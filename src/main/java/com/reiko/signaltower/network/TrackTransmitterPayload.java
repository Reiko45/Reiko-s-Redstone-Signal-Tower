package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.SignalScannerItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent when a row in the Signal Scanner is clicked: start tracking the transmitter the scanner
 * points to for {@code frequency}, or with {@code stop}, stop tracking.
 */
public record TrackTransmitterPayload(boolean offhand, int frequency, boolean stop) implements CustomPacketPayload {
    public static final Type<TrackTransmitterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "track_transmitter"));

    public static final StreamCodec<ByteBuf, TrackTransmitterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TrackTransmitterPayload::offhand,
            ByteBufCodecs.VAR_INT, TrackTransmitterPayload::frequency,
            ByteBufCodecs.BOOL, TrackTransmitterPayload::stop,
            TrackTransmitterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(TrackTransmitterPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof SignalScannerItem) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Component message = payload.stop()
                ? SignalScannerItem.stopTracking(stack)
                : SignalScannerItem.track(level, player, stack, payload.frequency());
        player.displayClientMessage(message, true);
    }
}
