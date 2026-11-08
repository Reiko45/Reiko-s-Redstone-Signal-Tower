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
 * else's encrypted frequency, so nothing else is sent.
 */
public record MeterReadingPayload(int frequency, int strength, int towers, int margin, boolean deaf) implements CustomPacketPayload {
    public static final Type<MeterReadingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "meter_reading"));

    public static final StreamCodec<ByteBuf, MeterReadingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeterReadingPayload::frequency,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::strength,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::towers,
            ByteBufCodecs.VAR_INT, MeterReadingPayload::margin,
            ByteBufCodecs.BOOL, MeterReadingPayload::deaf,
            MeterReadingPayload::new);

    public static MeterReadingPayload of(SignalMeterItem.Reading reading) {
        return new MeterReadingPayload(reading.frequency(), reading.strength(), reading.towers(), reading.margin(), reading.deaf());
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
