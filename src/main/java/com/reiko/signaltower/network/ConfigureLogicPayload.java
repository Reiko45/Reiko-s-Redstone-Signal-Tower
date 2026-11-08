package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioLogicReceiverBlock;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.IntruderAlerts;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.40.0): Done was pressed in a Radio Logic Receiver's screen. */
public record ConfigureLogicPayload(BlockPos pos, int first, int second, int mode) implements CustomPacketPayload {
    public static final Type<ConfigureLogicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_logic"));

    public static final StreamCodec<ByteBuf, ConfigureLogicPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureLogicPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureLogicPayload::first,
            ByteBufCodecs.VAR_INT, ConfigureLogicPayload::second,
            ByteBufCodecs.VAR_INT, ConfigureLogicPayload::mode,
            ConfigureLogicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureLogicPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the logic receiver's frequencies and mode if the player can reach it (like clicking it),
     * makes them its tuner and tells them; on someone else's encrypted frequency it says it's deaf
     * there instead (and the owner gets an alert). It only listens, so any frequency may be set.
     * Returns the message, or null if they can't reach a logic receiver there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureLogicPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioLogicReceiverBlock)
                || !(player.level().getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return null;
        }
        RadioReceiverBlockEntity.LogicMode mode = RadioReceiverBlockEntity.LogicMode.of(payload.mode());
        rx.setTunedBy(player.getUUID());
        rx.setLogic(payload.first(), payload.second(), mode);
        int deaf = rx.deafFrequency(player.level());
        Component message;
        if (deaf > 0) {
            Component deafness = FrequencyClaims.deafness(player.level(), deaf, player.getUUID(), "encrypted_deaf");
            message = deafness != null ? deafness : Component.literal("?");
            IntruderAlerts.report(player.getServer(), deaf, player, IntruderAlerts.Kind.DEAF, player.level().getBlockState(pos).getBlock());
        } else {
            String key = "message." + SignalTower.MOD_ID + ".";
            message = Component.translatable(key + "logic_set", FrequencyNames.label(player.level(), rx.getFrequency()),
                    Component.translatable(key + "logic_" + mode.key()), FrequencyNames.label(player.level(), rx.getSecondFrequency()))
                    .withStyle(ChatFormatting.AQUA);
        }
        player.displayClientMessage(message, true);
        return message;
    }
}
