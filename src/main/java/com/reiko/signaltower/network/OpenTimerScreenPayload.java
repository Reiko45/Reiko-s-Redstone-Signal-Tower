package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
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
 * Server to client (v0.42.0): open a Radio Timer Transmitter's screen, with its frequency and
 * schedule (daily or not, interval and pulse in ticks, on and off hours), whether it's sending,
 * paused by redstone or silenced (someone else's private frequency), its interval's next change
 * ({@code changeIn} ticks: positive = turns on then, negative = off, 0 = a daily timer) and the
 * time of day (0-23999).
 */
public record OpenTimerScreenPayload(BlockPos pos, int frequency, boolean daily, int interval, int pulse, int onHour, int offHour,
                                     boolean sending, boolean paused, boolean silenced, int changeIn, int dayTime)
        implements CustomPacketPayload {
    public static final Type<OpenTimerScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_timer_screen"));

    public static final StreamCodec<ByteBuf, OpenTimerScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.frequency());
                ByteBufCodecs.BOOL.encode(buffer, payload.daily());
                for (int value : new int[]{payload.interval(), payload.pulse(), payload.onHour(), payload.offHour()}) {
                    ByteBufCodecs.VAR_INT.encode(buffer, value);
                }
                ByteBufCodecs.BOOL.encode(buffer, payload.sending());
                ByteBufCodecs.BOOL.encode(buffer, payload.paused());
                ByteBufCodecs.BOOL.encode(buffer, payload.silenced());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.changeIn());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.dayTime());
            },
            buffer -> new OpenTimerScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    public static OpenTimerScreenPayload of(ServerLevel level, TimerTransmitterBlockEntity timer) {
        BlockPos pos = timer.getBlockPos();
        boolean silenced = SignalNetwork.get(level).isSilenced(level, pos);
        return new OpenTimerScreenPayload(pos, timer.getFrequency(), timer.isDaily(), timer.getInterval(), timer.getPulse(),
                timer.getOnHour(), timer.getOffHour(), timer.output(level) > 0 && !silenced, timer.isPaused(level), silenced,
                (int) Math.max(-100_000, Math.min(100_000, timer.changeIn(level))), (int) Math.floorMod(level.getDayTime(), 24000L));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenTimerScreenPayload payload, IPayloadContext context) {
        ClientHooks.openTimerScreen(payload);
    }
}
