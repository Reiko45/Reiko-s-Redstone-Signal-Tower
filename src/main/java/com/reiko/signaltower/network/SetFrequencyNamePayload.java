package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the name typed in the Frequency Tuner's screen for a frequency ("" removes it). */
public record SetFrequencyNamePayload(int frequency, String name) implements CustomPacketPayload {
    public static final Type<SetFrequencyNamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "set_frequency_name"));

    public static final StreamCodec<ByteBuf, SetFrequencyNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFrequencyNamePayload::frequency,
            ByteBufCodecs.stringUtf8(FrequencyNames.MAX_LENGTH * 4), SetFrequencyNamePayload::name,
            SetFrequencyNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(SetFrequencyNamePayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Names the frequency if the player is holding a Frequency Tuner, tells every player on the
     * server, and tells this player what happened. Returns false if nothing changed. Public for
     * the GameTests.
     */
    public static boolean apply(Player player, SetFrequencyNamePayload payload) {
        MinecraftServer server = player.getServer();
        boolean holdingTuner = player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
        if (server == null || !holdingTuner) {
            return false;
        }
        int frequency = Frequency.clamp(payload.frequency());
        // A private frequency's name is up to its owner and their friends (and server operators).
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(frequency);
        if (claim != null && !claim.allows(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".private_refused_name",
                    claim.ownerName(), frequency).withStyle(ChatFormatting.RED), true);
            return false;
        }
        FrequencyNames names = FrequencyNames.get(server);
        String before = names.get(frequency);
        String stored = names.set(frequency, payload.name());
        String key = "message." + SignalTower.MOD_ID + ".";
        if (stored == null) {
            player.displayClientMessage(Component.translatable(key + "name_full", FrequencyNames.MAX_NAMES)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (stored.equals(before == null ? "" : before)) {
            return false;
        }
        PacketDistributor.sendToAllPlayers(new FrequencyNamePayload(frequency, stored));
        player.displayClientMessage(stored.isEmpty()
                ? Component.translatable(key + "name_removed", frequency).withStyle(ChatFormatting.GRAY)
                : Component.translatable(key + "name_set", frequency,
                        Component.literal(stored).withStyle(ChatFormatting.AQUA)), true);
        return true;
    }
}
