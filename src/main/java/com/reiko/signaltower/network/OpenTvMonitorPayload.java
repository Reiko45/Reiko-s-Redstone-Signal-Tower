package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvMonitorBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.List;

/**
 * Server to client (v0.66.0): open a TV Monitor's settings (its wall's lead's): its frequency
 * (channel 1), the extra channels, which channel it's on, its flags, the wall's size (width × 10 +
 * height) and its status line.
 */
public record OpenTvMonitorPayload(BlockPos pos, int frequency, List<Integer> channels, int channel, int flags, int wallSize)
        implements CustomPacketPayload {
    public static final Type<OpenTvMonitorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_tv_monitor"));

    public static final StreamCodec<ByteBuf, OpenTvMonitorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenTvMonitorPayload::pos,
            ByteBufCodecs.VAR_INT, OpenTvMonitorPayload::frequency,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(TvMonitorBlockEntity.EXTRA_CHANNELS)), OpenTvMonitorPayload::channels,
            ByteBufCodecs.VAR_INT, OpenTvMonitorPayload::channel,
            ByteBufCodecs.VAR_INT, OpenTvMonitorPayload::flags,
            ByteBufCodecs.VAR_INT, OpenTvMonitorPayload::wallSize,
            OpenTvMonitorPayload::new);

    public static OpenTvMonitorPayload of(ServerLevel level, TvMonitorBlockEntity monitor) {
        monitor.update(level);
        return new OpenTvMonitorPayload(monitor.getBlockPos(), monitor.getFrequency(),
                Arrays.stream(monitor.extraChannels()).boxed().toList(), monitor.channelIndex(), monitor.getFlags(),
                monitor.wall().width() * 10 + monitor.wall().height());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenTvMonitorPayload payload, IPayloadContext context) {
        ClientHooks.openTvMonitor(payload);
    }
}
