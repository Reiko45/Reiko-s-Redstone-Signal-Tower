package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.37.0): how far the sender at {@code pos} reaches, for the reach preview
 * ring; {@code reach} -1 if there's nothing to show. {@code kind}: {@link #TRANSMITTER},
 * {@link #REPEATER}, {@link #CONVERTER}, {@link #KEYPAD}, {@link #COMPACT_TRANSMITTER} or {@link #MOTION_SENSOR}; {@code frequency} is the one it sends on.
 * v0.48.0: a tower with a Dish Antenna reaches {@code dishReach} inside its beam; {@code dish} is
 * its aim (bits 0-3) and the beam's half-width in degrees (bits 4 up), or -1 if it has no dish.
 */
public record ReachPreviewPayload(BlockPos pos, int reach, int kind, int frequency, int dishReach, int dish) implements CustomPacketPayload {
    public static final int TRANSMITTER = 0;
    public static final int REPEATER = 1;
    public static final int CONVERTER = 2;
    public static final int KEYPAD = 3;
    /** A Compact Radio Transmitter (v0.38.0). */
    public static final int COMPACT_TRANSMITTER = 4;
    /** A Radio Motion Sensor (v0.39.0). */
    public static final int MOTION_SENSOR = 5;
    /** A Radio Timer Transmitter (v0.42.0). */
    public static final int TIMER_TRANSMITTER = 6;
    /** A Radio Signal Recorder (v0.44.0). */
    public static final int SIGNAL_RECORDER = 7;
    /** A Radio Jammer (v0.46.0): the ring is how far it jams. */
    public static final int JAMMER = 8;
    /** A Radio Weather Station (v0.53.0). */
    public static final int WEATHER_STATION = 9;
    /** A Bundled Radio Transmitter (v0.54.0). */
    public static final int BUNDLED_TRANSMITTER = 10;
    /** A Radio Jukebox (v0.60.0). */
    public static final int JUKEBOX = 11;

    public ReachPreviewPayload(BlockPos pos, int reach, int kind, int frequency) {
        this(pos, reach, kind, frequency, reach, -1);
    }

    public boolean hasDish() {
        return dish >= 0;
    }

    /** Where the dish points (0 north ... 15 north-north-west). */
    public int dishAim() {
        return dish & 15;
    }

    /** How many degrees either side of its aim the dish's beam reaches. */
    public int dishCone() {
        return dish >> 4;
    }

    public static final Type<ReachPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "reach_preview"));

    public static final StreamCodec<ByteBuf, ReachPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ReachPreviewPayload::pos,
            ByteBufCodecs.VAR_INT, ReachPreviewPayload::reach,
            ByteBufCodecs.VAR_INT, ReachPreviewPayload::kind,
            ByteBufCodecs.VAR_INT, ReachPreviewPayload::frequency,
            ByteBufCodecs.VAR_INT, ReachPreviewPayload::dishReach,
            ByteBufCodecs.VAR_INT, ReachPreviewPayload::dish,
            ReachPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(ReachPreviewPayload payload, IPayloadContext context) {
        ClientHooks.onReachPreview(payload);
    }
}
