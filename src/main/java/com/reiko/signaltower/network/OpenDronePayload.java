package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.entity.RadioDroneEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client (v0.67.0): open a Radio Drone's settings: its frequency, settings, name and charge. */
public record OpenDronePayload(int entityId, int frequency, int settings, String name, int charge) implements CustomPacketPayload {
    public static final Type<OpenDronePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_drone"));
    public static final StreamCodec<ByteBuf, OpenDronePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDronePayload::entityId,
            ByteBufCodecs.VAR_INT, OpenDronePayload::frequency,
            ByteBufCodecs.VAR_INT, OpenDronePayload::settings,
            ByteBufCodecs.stringUtf8(96), OpenDronePayload::name,
            ByteBufCodecs.VAR_INT, OpenDronePayload::charge,
            OpenDronePayload::new);

    public static OpenDronePayload of(RadioDroneEntity drone) {
        return new OpenDronePayload(drone.getId(), drone.getFrequency(), drone.getSettings(), drone.droneName(), drone.getCharge());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDronePayload payload, IPayloadContext context) {
        ClientHooks.openDrone(payload);
    }
}
