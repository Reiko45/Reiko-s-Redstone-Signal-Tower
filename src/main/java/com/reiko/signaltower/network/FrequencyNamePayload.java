package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to every client: one frequency's name changed ("" means it was removed). */
public record FrequencyNamePayload(int frequency, String name) implements CustomPacketPayload {
    public static final Type<FrequencyNamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_name"));

    public static final StreamCodec<ByteBuf, FrequencyNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrequencyNamePayload::frequency,
            ByteBufCodecs.stringUtf8(FrequencyNames.MAX_LENGTH * 4), FrequencyNamePayload::name,
            FrequencyNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyNamePayload payload, IPayloadContext context) {
        FrequencyNames.clientSet(payload.frequency(), payload.name());
    }
}
