package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioDelayReceiverBlock;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
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

/** Client to server (v0.41.0): Done was pressed in a Radio Delay Receiver's screen. */
public record ConfigureDelayPayload(BlockPos pos, int delay, int hold) implements CustomPacketPayload {
    public static final Type<ConfigureDelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_delay"));

    public static final StreamCodec<ByteBuf, ConfigureDelayPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDelayPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureDelayPayload::delay,
            ByteBufCodecs.VAR_INT, ConfigureDelayPayload::hold,
            ConfigureDelayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureDelayPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the delay receiver's delay and hold if the player can reach it (times it doesn't offer
     * become none), and tells them. Returns the message, or null if they can't reach one there.
     * Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureDelayPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioDelayReceiverBlock)
                || !(player.level().getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return null;
        }
        rx.setTiming(payload.delay(), payload.hold());
        Component message = Component.translatable("message." + SignalTower.MOD_ID + ".delay_set",
                RadioReceiverBlockEntity.timeLabel(rx.getDelayTicks()), RadioReceiverBlockEntity.timeLabel(rx.getHoldTicks()))
                .withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(message, true);
        return message;
    }
}
