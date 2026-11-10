package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.radio.DronePilots;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server (v0.67.0): the pilot pressed Grab: the drone they fly grabs or drops a stack. */
public record DroneGrabPayload() implements CustomPacketPayload {
    public static final Type<DroneGrabPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "drone_grab"));
    public static final StreamCodec<ByteBuf, DroneGrabPayload> STREAM_CODEC = StreamCodec.unit(new DroneGrabPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DroneGrabPayload payload, IPayloadContext context) {
        RadioDroneEntity drone = DronePilots.droneOf(context.player());
        if (drone != null && drone.level() instanceof ServerLevel level) {
            context.player().displayClientMessage(drone.grabOrDrop(level), true);
        }
    }
}
