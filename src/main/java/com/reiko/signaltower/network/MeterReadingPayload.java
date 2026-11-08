package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.item.SignalMeterItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.40.0): a held Signal Meter's reading, five times a second. {@code margin}
 * is the blocks left to the edge of reach (-1: out of every tower's reach); {@code deaf}: someone
 * else's encrypted frequency, so nothing else is sent. {@code loss} (v0.46.0): what hills took off
 * (bits 0-3) and how strongly it's jammed (bits 4-7); v0.48.0: how much it faded (bits 8-11).
 */
public record MeterReadingPayload(int frequency, int strength, int towers, int margin, boolean deaf, int loss) implements CustomPacketPayload {
    public static final Type<MeterReadingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "meter_reading"));

    public static final StreamCodec<ByteBuf, MeterReadingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeterReadingPayload::frequency,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::strength,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::towers,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::margin,
            ByteBufCodecs.BOOL, MeterReadingPayload::deaf,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::loss,
            MeterReadingPayload::new);

    public static MeterReadingPayload of(SignalMeterItem.Reading reading) {
        return new MeterReadingPayload(reading.frequency(), reading.strength(), reading.towers(), reading.margin(), reading.deaf(),
                Math.min(15, reading.hills()) | Math.min(15, reading.jammed()) << 4 | Math.min(15, reading.faded()) << 8);
    }

    /** How much the signal faded here, far out in its tower's reach (v0.48.0). */
    public int faded() {
        return loss >> 8 & 15;
    }

    /** What hills took off the signal here (v0.46.0). */
    public int hills() {
        return loss & 15;
    }

    /** How strongly the frequency is jammed here (v0.46.0). */
    public int jammed() {
        return loss >> 4 & 15;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(MeterReadingPayload payload, IPayloadContext context) {
        ClientHooks.onMeterReading(payload);
    }
}
