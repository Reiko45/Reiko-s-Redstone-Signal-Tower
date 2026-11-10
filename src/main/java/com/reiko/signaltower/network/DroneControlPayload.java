package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client (v0.67.0): you're flying the drone {@code entityId} now, or ({@code on} false) not any more. */
public record DroneControlPayload(int entityId, boolean on) implements CustomPacketPayload {
    public static final Type<DroneControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "drone_control"));
    public static final StreamCodec<ByteBuf, DroneControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DroneControlPayload::entityId,
            ByteBufCodecs.BOOL, DroneControlPayload::on,
            DroneControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DroneControlPayload payload, IPayloadContext context) {
        ClientHooks.droneControl(payload);
    }
}
