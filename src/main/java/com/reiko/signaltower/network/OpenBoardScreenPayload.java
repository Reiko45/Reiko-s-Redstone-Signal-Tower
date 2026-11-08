package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.StatusBoardBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client (v0.43.0): open a Radio Status Board's screen, with its rows' frequencies (0:
 * empty), the strengths heard there and which rows are deaf (bit per row).
 */
public record OpenBoardScreenPayload(BlockPos pos, List<Integer> frequencies, List<Integer> strengths, int deaf)
        implements CustomPacketPayload {
    public static final Type<OpenBoardScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_board_screen"));

    public static final StreamCodec<ByteBuf, OpenBoardScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenBoardScreenPayload::pos,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(StatusBoardBlockEntity.ROWS)), OpenBoardScreenPayload::frequencies,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(StatusBoardBlockEntity.ROWS)), OpenBoardScreenPayload::strengths,
            ByteBufCodecs.VAR_INT, OpenBoardScreenPayload::deaf,
            OpenBoardScreenPayload::new);

    public static OpenBoardScreenPayload of(ServerLevel level, StatusBoardBlockEntity board) {
        List<Integer> strengths = new ArrayList<>();
        int deaf = 0;
        for (int i = 0; i < StatusBoardBlockEntity.ROWS; i++) {
            strengths.add(board.getStrength(i));
            if (board.isDeaf(i)) {
                deaf |= 1 << i;
            }
        }
        return new OpenBoardScreenPayload(board.getBlockPos(), board.frequencyList(), strengths, deaf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenBoardScreenPayload payload, IPayloadContext context) {
        ClientHooks.openBoardScreen(payload);
    }
}
