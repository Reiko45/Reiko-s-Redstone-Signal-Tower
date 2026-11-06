package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** Server to one client, when they join: every frequency name in the world. */
public record FrequencyNamesPayload(Map<Integer, String> names) implements CustomPacketPayload {
    public static final Type<FrequencyNamesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_names"));

    public static final StreamCodec<ByteBuf, FrequencyNamesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.stringUtf8(FrequencyNames.MAX_LENGTH * 4),
                    FrequencyNames.MAX_NAMES), FrequencyNamesPayload::names,
            FrequencyNamesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyNamesPayload payload, IPayloadContext context) {
        FrequencyNames.clientReplaceAll(payload.names());
    }
}
