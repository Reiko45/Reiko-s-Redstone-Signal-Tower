package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyAudit;
import com.reiko.signaltower.radio.FrequencyLog;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * The server's answer to {@link LogRequestPayload} (v0.35.0): a private frequency's last
 * turn-ons, newest first, and the game time now (for "3 minutes ago"); or {@code allowed = false}
 * if the player isn't its owner (or an operator).
 */
public record LogResultPayload(int frequency, boolean allowed, List<FrequencyLog.Entry> entries, long gameTime)
        implements CustomPacketPayload {
    public static final Type<LogResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "log_result"));

    private static final StreamCodec<ByteBuf, FrequencyLog.Entry> ENTRY_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, entry.kind().ordinal());
                ByteBufCodecs.stringUtf8(256).encode(buffer, entry.dimension());
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buffer, entry.pos());
                ByteBufCodecs.stringUtf8(64).encode(buffer, entry.who());
                ByteBufCodecs.VAR_INT.encode(buffer, entry.whoKind().ordinal());
                ByteBufCodecs.VAR_LONG.encode(buffer, entry.gameTime());
                ByteBufCodecs.VAR_LONG.encode(buffer, entry.dayTime());
                ByteBufCodecs.VAR_INT.encode(buffer, entry.from());
                ByteBufCodecs.stringUtf8(64).encode(buffer, entry.detail());
            },
            buffer -> new FrequencyLog.Entry(
                    SignalNetwork.CauseKind.of(ByteBufCodecs.VAR_INT.decode(buffer)),
                    ByteBufCodecs.stringUtf8(256).decode(buffer),
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buffer),
                    ByteBufCodecs.stringUtf8(64).decode(buffer),
                    FrequencyAudit.Who.of(ByteBufCodecs.VAR_INT.decode(buffer)),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.stringUtf8(64).decode(buffer)));

    public static final StreamCodec<ByteBuf, LogResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LogResultPayload::frequency,
            ByteBufCodecs.BOOL, LogResultPayload::allowed,
            ENTRY_CODEC.apply(ByteBufCodecs.list(FrequencyLog.MAX_ENTRIES)), LogResultPayload::entries,
            ByteBufCodecs.VAR_LONG, LogResultPayload::gameTime,
            LogResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(LogResultPayload payload, IPayloadContext context) {
        ClientHooks.onLogResult(payload);
    }
}
