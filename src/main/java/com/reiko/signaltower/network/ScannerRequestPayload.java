package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Sent about once a second by an open Signal Scanner screen: "what can I hear here?" The server
 * answers with {@link ScannerResultPayload}.
 */
public record ScannerRequestPayload() implements CustomPacketPayload {
    public static final ScannerRequestPayload INSTANCE = new ScannerRequestPayload();
    public static final Type<ScannerRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "scanner_request"));
    public static final StreamCodec<ByteBuf, ScannerRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. Only answers players holding a scanner. */
    public static void handle(ScannerRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        boolean holding = player.getMainHandItem().getItem() instanceof SignalScannerItem
                || player.getOffhandItem().getItem() instanceof SignalScannerItem;
        if (!holding || !(player.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        List<SignalNetwork.HeardFrequency> heard = SignalNetwork.get(level).scanAt(level, player.blockPosition(), player.getUUID());
        PacketDistributor.sendToPlayer(serverPlayer, new ScannerResultPayload(heard.stream()
                .map(h -> new ScannerResultPayload.Entry(h.frequency(), h.power(), h.source().ordinal(),
                        h.transmitters(), h.distance(), h.direction()))
                .toList()));
    }
}
