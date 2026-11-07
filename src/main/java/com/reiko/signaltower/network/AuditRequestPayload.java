package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyAudit;
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
 * Client to server, about once a second while the audit screen is open: list the blocks on this
 * private frequency. Only its owner (or an operator) gets the list.
 */
public record AuditRequestPayload(int frequency) implements CustomPacketPayload {
    public static final Type<AuditRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "audit_request"));

    public static final StreamCodec<ByteBuf, AuditRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuditRequestPayload::frequency,
            AuditRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(AuditRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, answer(player, payload.frequency()));
        }
    }

    /** The answer for {@code player}: the list, or "not allowed" (no rows). Public for the GameTests. */
    public static AuditResultPayload answer(Player player, int rawFrequency) {
        int frequency = Frequency.clamp(rawFrequency);
        if (player.getServer() == null || !FrequencyAudit.mayAudit(player, frequency)) {
            return new AuditResultPayload(frequency, false, List.of());
        }
        return new AuditResultPayload(frequency, true,
                FrequencyAudit.audit(player.getServer(), frequency, player, AuditResultPayload.MAX_ENTRIES));
    }
}
