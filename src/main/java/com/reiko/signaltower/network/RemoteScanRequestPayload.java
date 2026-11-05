package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent about once a second by an open remote screen: "who's listening on these frequencies near
 * me?" The server answers with {@link RemoteScanResultPayload}. Nothing is transmitted.
 */
public record RemoteScanRequestPayload(List<Integer> frequencies) implements CustomPacketPayload {
    public static final Type<RemoteScanRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "remote_scan_request"));

    public static final StreamCodec<ByteBuf, RemoteScanRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(RemoteButtons.COUNT)), RemoteScanRequestPayload::frequencies,
            RemoteScanRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. Only answers players holding a remote. */
    public static void handle(RemoteScanRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        boolean holdingRemote = player.getMainHandItem().getItem() instanceof RemoteControlItem
                || player.getMainHandItem().getItem() instanceof MultiRemoteItem
                || player.getOffhandItem().getItem() instanceof RemoteControlItem
                || player.getOffhandItem().getItem() instanceof MultiRemoteItem;
        if (!holdingRemote || !(player.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        List<SignalNetwork.Coverage> coverage = RemoteControlItem.coverageFor(level, player);
        BlockPos origin = coverage.get(0).origin();
        int reach = coverage.get(0).reach();
        SignalNetwork network = SignalNetwork.get(level);
        List<RemoteScanResultPayload.Entry> results = new ArrayList<>();
        for (int frequency : payload.frequencies()) {
            int clamped = Frequency.clamp(frequency);
            SignalNetwork.ReceiverScan scan = network.scanReceivers(clamped, origin, reach, coverage);
            results.add(new RemoteScanResultPayload.Entry(clamped, scan.inRange(), scan.nearestDistance(),
                    scan.direction(), scan.nearestInRange()));
        }
        PacketDistributor.sendToPlayer(serverPlayer, new RemoteScanResultPayload(reach, coverage.size() - 1, results));
    }
}
