package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.entity.RadioDroneEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, four times a second while flying (v0.67.0): the drone's state for the pilot's
 * panel, even when it's too far for the pilot's game to have it: where it is, its charge, how far
 * out in the pilot's reach it is, and its state (bits 0-2), whether it carries something (8),
 * relays (16) or has its beacon on (32).
 */
public record DroneStatusPayload(int entityId, BlockPos pos, int charge, float signal, int flags) implements CustomPacketPayload {
    public static final Type<DroneStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "drone_status"));
    public static final StreamCodec<ByteBuf, DroneStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DroneStatusPayload::entityId,
            BlockPos.STREAM_CODEC, DroneStatusPayload::pos,
            ByteBufCodecs.VAR_INT, DroneStatusPayload::charge,
            ByteBufCodecs.FLOAT, DroneStatusPayload::signal,
            ByteBufCodecs.VAR_INT, DroneStatusPayload::flags,
            DroneStatusPayload::new);

    public static DroneStatusPayload of(RadioDroneEntity drone) {
        int flags = drone.getState().ordinal() | (drone.getCarried().isEmpty() ? 0 : 8) | (drone.isRelaying() ? 16 : 0)
                | (drone.has(RadioDroneEntity.BEACON) ? 32 : 0);
        return new DroneStatusPayload(drone.getId(), drone.blockPosition(), drone.getCharge(), (float) drone.getSignal(), flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DroneStatusPayload payload, IPayloadContext context) {
        ClientHooks.droneStatus(payload);
    }
}
