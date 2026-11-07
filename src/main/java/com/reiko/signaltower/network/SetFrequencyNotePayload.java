package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
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

/** Client to server: the note saved in the Frequency Tuner's Note screen for a frequency ("" removes it). */
public record SetFrequencyNotePayload(int frequency, String note) implements CustomPacketPayload {
    public static final Type<SetFrequencyNotePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "set_frequency_note"));

    public static final StreamCodec<ByteBuf, SetFrequencyNotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFrequencyNotePayload::frequency,
            ByteBufCodecs.stringUtf8(FrequencyNotes.MAX_LENGTH * 4), SetFrequencyNotePayload::note,
            SetFrequencyNotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(SetFrequencyNotePayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the frequency's note if the player is holding a Frequency Tuner (and, on a private
     * frequency, is its owner, a member or an operator, the same rule as for names), tells every
     * player on the server, and tells this player what happened. Returns false if nothing changed.
     * Public for the GameTests.
     */
    public static boolean apply(Player player, SetFrequencyNotePayload payload) {
        MinecraftServer server = player.getServer();
        boolean holdingTuner = player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
        if (server == null || !holdingTuner) {
            return false;
        }
        int frequency = Frequency.clamp(payload.frequency());
        String key = "message." + SignalTower.MOD_ID + ".";
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(frequency);
        if (claim != null && !claim.allows(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable(key + "private_refused_note",
                    claim.ownerName(), FrequencyNames.label(player.level(), frequency)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        FrequencyNotes notes = FrequencyNotes.get(server);
        String before = notes.get(frequency);
        String stored = notes.set(frequency, payload.note());
        if (stored == null) {
            player.displayClientMessage(Component.translatable(key + "note_full", FrequencyNotes.MAX_NOTES)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (stored.equals(before == null ? "" : before)) {
            return false;
        }
        PacketDistributor.sendToAllPlayers(new FrequencyNotePayload(frequency, stored));
        Object label = FrequencyNames.label(player.level(), frequency);
        player.displayClientMessage(stored.isEmpty()
                ? Component.translatable(key + "note_removed", label).withStyle(ChatFormatting.GRAY)
                : Component.translatable(key + "note_set", label,
                        Component.literal(stored).withStyle(ChatFormatting.ITALIC)), true);
        return true;
    }
}
