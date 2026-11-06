package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/** Server to every client: one frequency was made private, its friends changed, or it's public again (empty). */
public record FrequencyClaimPayload(int frequency, Optional<FrequencyClaims.ClaimInfo> claim) implements CustomPacketPayload {
    public static final Type<FrequencyClaimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_claim"));

    public static final StreamCodec<ByteBuf, FrequencyClaimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrequencyClaimPayload::frequency,
            ByteBufCodecs.optional(FrequencyClaims.ClaimInfo.STREAM_CODEC), FrequencyClaimPayload::claim,
            FrequencyClaimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyClaimPayload payload, IPayloadContext context) {
        FrequencyClaims.clientSet(payload.frequency(), payload.claim().orElse(null));
    }
}
