package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.41.0): open a Radio Delay Receiver's screen, with its frequency, delay and
 * hold (ticks), what it hears and outputs right now, its next change ({@code changeIn} ticks:
 * positive = turns on then, negative = off, 0 = none) and whether it's deaf (someone else's
 * encrypted frequency).
 */
public record OpenDelayScreenPayload(BlockPos pos, int frequency, int delay, int hold, int heard, int output,
                                     int changeIn, boolean deaf) implements CustomPacketPayload {
    public static final Type<OpenDelayScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_delay_screen"));

    public static final StreamCodec<ByteBuf, OpenDelayScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                for (int value : new int[]{payload.frequency(), payload.delay(), payload.hold(), payload.heard(), payload.output(),
                        payload.changeIn()}) {
                    ByteBufCodecs.VAR_INT.encode(buffer, value);
                }
                ByteBufCodecs.BOOL.encode(buffer, payload.deaf());
            },
            buffer -> new OpenDelayScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer)));

    public static OpenDelayScreenPayload of(ServerLevel level, RadioReceiverBlockEntity rx) {
        boolean deaf = !FrequencyClaims.mayHear(level, rx.getFrequency(), rx.getTunedBy());
        int heard = deaf ? 0 : SignalNetwork.get(level).listen(level, rx.getFrequency(), rx.getBlockPos()).power();
        long change = rx.changeIn(level.getGameTime());
        return new OpenDelayScreenPayload(rx.getBlockPos(), rx.getFrequency(), rx.getDelayTicks(), rx.getHoldTicks(), heard,
                rx.getBlockState().getValue(com.reiko.signaltower.block.RadioReceiverBlock.POWER),
                (int) Math.max(-100_000, Math.min(100_000, change)), deaf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenDelayScreenPayload payload, IPayloadContext context) {
        ClientHooks.openDelayScreen(payload);
    }
}
