package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.radio.DronePilots;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server, every tick while flying (v0.67.0): the pilot's controls (forward, left, up;
 * each -1 to 1) and where they look. Only for the drone they're flying.
 */
public record DroneInputPayload(float forward, float strafe, float vertical, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<DroneInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "drone_input"));
    public static final StreamCodec<ByteBuf, DroneInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DroneInputPayload::forward,
            ByteBufCodecs.FLOAT, DroneInputPayload::strafe,
            ByteBufCodecs.FLOAT, DroneInputPayload::vertical,
            ByteBufCodecs.FLOAT, DroneInputPayload::yaw,
            ByteBufCodecs.FLOAT, DroneInputPayload::pitch,
            DroneInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DroneInputPayload payload, IPayloadContext context) {
        RadioDroneEntity drone = DronePilots.droneOf(context.player());
        if (drone != null) {
            drone.steer(payload.forward(), payload.strafe(), payload.vertical(), payload.yaw(), payload.pitch());
        }
    }
}
