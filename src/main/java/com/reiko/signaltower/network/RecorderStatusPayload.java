package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioSignalRecorderBlock;
import com.reiko.signaltower.blockentity.SignalRecorderBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server to client (v0.44.0): what a Radio Signal Recorder is doing now, for its open screen. Sent
 * when the screen asks ({@link Request}, twice a second) and after each button in it.
 */
public record RecorderStatusPayload(RecorderStatus status) implements CustomPacketPayload {
    public static final Type<RecorderStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "recorder_status"));

    public static final StreamCodec<ByteBuf, RecorderStatusPayload> STREAM_CODEC =
            RecorderStatus.STREAM_CODEC.map(RecorderStatusPayload::new, RecorderStatusPayload::status);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(RecorderStatusPayload payload, IPayloadContext context) {
        ClientHooks.onRecorderStatus(payload.status());
    }

    /** The status for a player within reach of the recorder at {@code pos}, or null. Public for the GameTests. */
    public static @Nullable RecorderStatus answer(Player player, BlockPos pos) {
        if (player.level() instanceof ServerLevel level && player.canInteractWithBlock(pos, 1.0)
                && level.getBlockState(pos).getBlock() instanceof RadioSignalRecorderBlock
                && level.getBlockEntity(pos) instanceof SignalRecorderBlockEntity recorder) {
            return RecorderStatus.of(level, recorder);
        }
        return null;
    }

    /** Client to server: the recorder screen asks for the recorder's status. */
    public record Request(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "recorder_status_request"));

        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = BlockPos.STREAM_CODEC.map(Request::new, Request::pos);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /** Runs on the server's main thread. */
        public static void handle(Request request, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                RecorderStatus status = answer(player, request.pos());
                if (status != null) {
                    PacketDistributor.sendToPlayer(player, new RecorderStatusPayload(status));
                }
            }
        }
    }
}
