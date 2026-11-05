package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.item.RemoteButtons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** The server's answer to {@link RemoteScanRequestPayload}: one entry per frequency asked about. */
public record RemoteScanResultPayload(int reach, List<Entry> entries) implements CustomPacketPayload {
    /** See {@link com.reiko.signaltower.radio.SignalNetwork.ReceiverScan}. */
    public record Entry(int frequency, int inRange, int nearestDistance, int direction, boolean nearestInRange) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::frequency,
                ByteBufCodecs.VAR_INT, Entry::inRange,
                ByteBufCodecs.INT, Entry::nearestDistance,
                ByteBufCodecs.INT, Entry::direction,
                ByteBufCodecs.BOOL, Entry::nearestInRange,
                Entry::new);
    }

    public static final Type<RemoteScanResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "remote_scan_result"));

    public static final StreamCodec<ByteBuf, RemoteScanResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RemoteScanResultPayload::reach,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(RemoteButtons.COUNT)), RemoteScanResultPayload::entries,
            RemoteScanResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client. Only ever called there, so the client-only hook class is safe to use. */
    public static void handle(RemoteScanResultPayload payload, IPayloadContext context) {
        ClientHooks.onRemoteScan(payload);
    }
}
