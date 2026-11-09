package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server to client (v0.61.0): the satellites in orbit over the player's dimension, so the client
 * can draw them crossing the night sky. Sent when they join, change dimension or respawn, and to
 * everyone in a dimension when a new one gets there.
 */
public record SatellitesPayload(ResourceLocation dimension, List<SignalNetwork.Satellite> satellites) implements CustomPacketPayload {
    public static final Type<SatellitesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "satellites"));

    private static final StreamCodec<ByteBuf, SignalNetwork.Satellite> SATELLITE_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SignalNetwork.Satellite::launchedAt,
            ByteBufCodecs.VAR_LONG, SignalNetwork.Satellite::seed,
            SignalNetwork.Satellite::new);

    public static final StreamCodec<ByteBuf, SatellitesPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SatellitesPayload::dimension,
            SATELLITE_CODEC.apply(ByteBufCodecs.list(SignalNetwork.MAX_SATELLITES)), SatellitesPayload::satellites,
            SatellitesPayload::new);

    public static SatellitesPayload of(ServerLevel level) {
        return new SatellitesPayload(level.dimension().location(), SignalNetwork.get(level).satellites());
    }

    /** Tells {@code player} about the satellites over the dimension they're in. */
    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, of(player.serverLevel()));
    }

    /** Tells everyone in {@code level} about its satellites. */
    public static void sendToDimension(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, of(level));
    }

    public static void handle(SatellitesPayload payload, IPayloadContext context) {
        ClientHooks.satellites(payload.dimension(), payload.satellites());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
