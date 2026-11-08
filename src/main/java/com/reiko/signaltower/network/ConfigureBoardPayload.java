package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.StatusBoardBlock;
import com.reiko.signaltower.blockentity.StatusBoardBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.IntruderAlerts;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Client to server (v0.43.0): Done was pressed in a Radio Status Board's screen (0: an empty row). */
public record ConfigureBoardPayload(BlockPos pos, List<Integer> frequencies) implements CustomPacketPayload {
    public static final Type<ConfigureBoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_board"));

    public static final StreamCodec<ByteBuf, ConfigureBoardPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureBoardPayload::pos,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(StatusBoardBlockEntity.ROWS)), ConfigureBoardPayload::frequencies,
            ConfigureBoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureBoardPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the board's rows if the player can reach it, making them the one it hears as (like the
     * logic receiver's screen); if a row is someone else's encrypted frequency it says so, and that
     * row shows "encrypted". Returns the message, or null if they can't reach a board there. Public
     * for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureBoardPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof StatusBoardBlock block)
                || !(player.level().getBlockEntity(pos) instanceof StatusBoardBlockEntity board)) {
            return null;
        }
        board.setTunedBy(player.getUUID());
        board.setFrequencies(payload.frequencies());
        for (int i = 0; i < StatusBoardBlockEntity.ROWS; i++) {
            int frequency = board.getFrequency(i);
            if (frequency > 0 && !FrequencyClaims.mayHear(player.level(), frequency, player.getUUID())) {
                Component deafness = FrequencyClaims.deafness(player.level(), frequency, player.getUUID(), "encrypted_deaf");
                IntruderAlerts.report(player.getServer(), frequency, player, IntruderAlerts.Kind.DEAF, block);
                if (deafness != null) {
                    player.displayClientMessage(deafness, true);
                    return deafness;
                }
            }
        }
        int rows = (int) board.frequencyList().stream().filter(f -> f > 0).count();
        Component message = Component.translatable("message." + SignalTower.MOD_ID + ".board_set", rows).withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(message, true);
        return message;
    }
}
