package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** Server to one client, when they join: every private frequency in the world, with its owner and friends. */
public record FrequencyClaimsPayload(Map<Integer, FrequencyClaims.ClaimInfo> claims) implements CustomPacketPayload {
    public static final Type<FrequencyClaimsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_claims"));

    public static final StreamCodec<ByteBuf, FrequencyClaimsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, FrequencyClaims.ClaimInfo.STREAM_CODEC,
                    FrequencyClaims.MAX_CLAIMS), FrequencyClaimsPayload::claims,
            FrequencyClaimsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyClaimsPayload payload, IPayloadContext context) {
        FrequencyClaims.clientReplaceAll(payload.claims());
    }
}
