package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client (v0.66.0): open a TV Camera's settings, with its frequency, settings, name, reach and how many monitors show it. */
public record OpenTvCameraPayload(BlockPos pos, int frequency, int settings, String name, int reach, int watchers)
        implements CustomPacketPayload {
    public static final Type<OpenTvCameraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_tv_camera"));

    public static final StreamCodec<ByteBuf, OpenTvCameraPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenTvCameraPayload::pos,
            ByteBufCodecs.VAR_INT, OpenTvCameraPayload::frequency,
            ByteBufCodecs.VAR_INT, OpenTvCameraPayload::settings,
            ByteBufCodecs.stringUtf8(TvCameraBlockEntity.NAME_LENGTH * 4), OpenTvCameraPayload::name,
            ByteBufCodecs.VAR_INT, OpenTvCameraPayload::reach,
            ByteBufCodecs.VAR_INT, OpenTvCameraPayload::watchers,
            OpenTvCameraPayload::new);

    public static OpenTvCameraPayload of(ServerLevel level, TvCameraBlockEntity camera) {
        camera.publish(level);
        camera.count(level);
        return new OpenTvCameraPayload(camera.getBlockPos(), camera.getFrequency(), camera.getSettings(), camera.getName(),
                camera.getReach(), camera.getWatchers());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenTvCameraPayload payload, IPayloadContext context) {
        ClientHooks.openTvCamera(payload);
    }
}
