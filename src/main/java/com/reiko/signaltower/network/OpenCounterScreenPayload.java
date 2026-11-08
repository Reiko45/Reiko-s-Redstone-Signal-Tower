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
 * Server to client (v0.42.0): open a Radio Counter Receiver's screen, with its frequency, target,
 * whether it stays on, its forget time (ticks), its reset frequency (0: none), the count, whether
 * it's on, what it hears on each frequency, how long until it forgets (ticks, 0: it won't) and
 * which frequencies it's deaf on ({@link #DEAF_FREQUENCY} / {@link #DEAF_RESET} bits).
 */
public record OpenCounterScreenPayload(BlockPos pos, int frequency, int target, boolean latch, int forget, int reset,
                                       int count, boolean on, int heard, int heardReset, int forgetIn, int deaf)
        implements CustomPacketPayload {
    public static final int DEAF_FREQUENCY = 1;
    public static final int DEAF_RESET = 2;

    public static final Type<OpenCounterScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_counter_screen"));

    public static final StreamCodec<ByteBuf, OpenCounterScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.frequency());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.target());
                ByteBufCodecs.BOOL.encode(buffer, payload.latch());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.forget());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.reset());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.count());
                ByteBufCodecs.BOOL.encode(buffer, payload.on());
                for (int value : new int[]{payload.heard(), payload.heardReset(), payload.forgetIn(), payload.deaf()}) {
                    ByteBufCodecs.VAR_INT.encode(buffer, value);
                }
            },
            buffer -> new OpenCounterScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    public static OpenCounterScreenPayload of(ServerLevel level, RadioReceiverBlockEntity rx) {
        BlockPos pos = rx.getBlockPos();
        SignalNetwork network = SignalNetwork.get(level);
        boolean deaf = !FrequencyClaims.mayHear(level, rx.getFrequency(), rx.getTunedBy());
        int reset = rx.getResetFrequency();
        boolean deafReset = reset > 0 && !FrequencyClaims.mayHear(level, reset, rx.getTunedBy());
        return new OpenCounterScreenPayload(pos, rx.getFrequency(), rx.getCounterTarget(), rx.isCounterLatch(), rx.getForgetTicks(),
                reset, rx.getCount(), rx.isCounterOn(), deaf ? 0 : network.listen(level, rx.getFrequency(), pos).power(),
                reset > 0 && !deafReset ? network.listen(level, reset, pos).power() : 0,
                (int) Math.min(100_000, rx.forgetIn(level.getGameTime())), (deaf ? DEAF_FREQUENCY : 0) | (deafReset ? DEAF_RESET : 0));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenCounterScreenPayload payload, IPayloadContext context) {
        ClientHooks.openCounterScreen(payload);
    }
}
