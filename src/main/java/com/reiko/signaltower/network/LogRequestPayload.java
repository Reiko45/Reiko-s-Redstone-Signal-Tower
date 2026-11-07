package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyAudit;
import com.reiko.signaltower.radio.FrequencyLog;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client to server (v0.35.0), about once a second while the log screen is open: this private
 * frequency's last turn-ons. Only its owner (or an operator) gets them, as for the audit.
 */
public record LogRequestPayload(int frequency) implements CustomPacketPayload {
    public static final Type<LogRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "log_request"));

    public static final StreamCodec<ByteBuf, LogRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LogRequestPayload::frequency,
            LogRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(LogRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, answer(player, payload.frequency()));
        }
    }

    /** The answer for {@code player}: the log, or "not allowed" (no rows). Public for the GameTests. */
    public static LogResultPayload answer(Player player, int rawFrequency) {
        int frequency = Frequency.clamp(rawFrequency);
        if (player.getServer() == null || !FrequencyAudit.mayAudit(player, frequency)) {
            return new LogResultPayload(frequency, false, List.of(), 0L);
        }
        return new LogResultPayload(frequency, true, FrequencyLog.log(player.getServer(), frequency),
                player.level().getGameTime());
    }
}
