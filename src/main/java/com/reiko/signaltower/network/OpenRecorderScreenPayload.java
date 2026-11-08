package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.SignalRecorderBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.44.0): open a Radio Signal Recorder's screen, with the frequency it plays
 * on, the one it records from (0: none), whether it loops, and what it's doing.
 */
public record OpenRecorderScreenPayload(int frequency, int listen, boolean loop, RecorderStatus status) implements CustomPacketPayload {
    public static final Type<OpenRecorderScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_recorder_screen"));

    public static final StreamCodec<ByteBuf, OpenRecorderScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenRecorderScreenPayload::frequency,
            ByteBufCodecs.VAR_INT, OpenRecorderScreenPayload::listen,
            ByteBufCodecs.BOOL, OpenRecorderScreenPayload::loop,
            RecorderStatus.STREAM_CODEC, OpenRecorderScreenPayload::status,
            OpenRecorderScreenPayload::new);

    public static OpenRecorderScreenPayload of(ServerLevel level, SignalRecorderBlockEntity recorder) {
        return new OpenRecorderScreenPayload(recorder.getFrequency(), recorder.getListenFrequency(), recorder.isLoop(),
                RecorderStatus.of(level, recorder));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenRecorderScreenPayload payload, IPayloadContext context) {
        ClientHooks.openRecorderScreen(payload);
    }
}
