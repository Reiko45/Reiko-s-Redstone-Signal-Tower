package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyAudit;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * The server's answer to {@link AuditRequestPayload}: the blocks on a private frequency, or
 * {@code allowed = false} if the player isn't its owner (or an operator).
 */
public record AuditResultPayload(int frequency, boolean allowed, List<FrequencyAudit.Entry> entries) implements CustomPacketPayload {
    /** The most rows sent. */
    public static final int MAX_ENTRIES = 512;

    public static final Type<AuditResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "audit_result"));

    private static final StreamCodec<ByteBuf, FrequencyAudit.Entry> ENTRY_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, entry.kind().ordinal());
                ByteBufCodecs.stringUtf8(256).encode(buffer, entry.dimension());
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buffer, entry.pos());
                ByteBufCodecs.stringUtf8(64).encode(buffer, entry.tuner());
                ByteBufCodecs.VAR_INT.encode(buffer, entry.who().ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, entry.status().ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, entry.count());
                ByteBufCodecs.VAR_INT.encode(buffer, entry.power());
            },
            buffer -> new FrequencyAudit.Entry(
                    FrequencyAudit.Kind.of(ByteBufCodecs.VAR_INT.decode(buffer)),
                    ByteBufCodecs.stringUtf8(256).decode(buffer),
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buffer),
                    ByteBufCodecs.stringUtf8(64).decode(buffer),
                    FrequencyAudit.Who.of(ByteBufCodecs.VAR_INT.decode(buffer)),
                    FrequencyAudit.Status.of(ByteBufCodecs.VAR_INT.decode(buffer)),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    public static final StreamCodec<ByteBuf, AuditResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuditResultPayload::frequency,
            ByteBufCodecs.BOOL, AuditResultPayload::allowed,
            ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), AuditResultPayload::entries,
            AuditResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(AuditResultPayload payload, IPayloadContext context) {
        ClientHooks.onAuditResult(payload);
    }
}
