package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioCounterReceiverBlock;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
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

/**
 * Client to server (v0.42.0): Done or Reset count was pressed in a Radio Counter Receiver's screen.
 * {@code reset} is its reset frequency (0: none); {@code clear} sets the count back to 0.
 */
public record ConfigureCounterPayload(BlockPos pos, int target, boolean latch, int forget, int reset, boolean clear)
        implements CustomPacketPayload {
    public static final Type<ConfigureCounterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_counter"));

    public static final StreamCodec<ByteBuf, ConfigureCounterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureCounterPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureCounterPayload::target,
            ByteBufCodecs.BOOL, ConfigureCounterPayload::latch,
            ByteBufCodecs.VAR_INT, ConfigureCounterPayload::forget,
            ByteBufCodecs.VAR_INT, ConfigureCounterPayload::reset,
            ByteBufCodecs.BOOL, ConfigureCounterPayload::clear,
            ConfigureCounterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureCounterPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the counter receiver's settings (changing any starts the count over) and, with
     * {@code clear}, sets its count back to 0, if the player can reach it (like clicking it). A new
     * reset frequency makes them its tuner, as the logic receiver's screen does; if it then can't
     * hear one of its frequencies (someone else's encrypted one) it says so and the owner gets an
     * alert. Returns the message, or null if they can't reach one there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureCounterPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioCounterReceiverBlock)
                || !(player.level().getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return null;
        }
        String key = "message." + SignalTower.MOD_ID + ".";
        int reset = payload.reset() <= 0 ? 0 : com.reiko.signaltower.radio.Frequency.clamp(payload.reset());
        if (reset != rx.getResetFrequency()) {
            rx.setTunedBy(player.getUUID());
        }
        boolean changed = rx.getCounterTarget() != RadioReceiverBlockEntity.validCount(payload.target())
                || rx.isCounterLatch() != payload.latch()
                || rx.getForgetTicks() != RadioReceiverBlockEntity.validTime(payload.forget())
                || rx.getResetFrequency() != reset;
        rx.setCounter(payload.target(), payload.latch(), payload.forget(), reset);
        if (payload.clear()) {
            rx.resetCount();
        }
        Component message;
        int deaf = rx.deafFrequency(player.level());
        if (deaf > 0) {
            Component deafness = FrequencyClaims.deafness(player.level(), deaf, player.getUUID(), "encrypted_deaf");
            message = deafness != null ? deafness : Component.literal("?");
            IntruderAlerts.report(player.getServer(), deaf, player, IntruderAlerts.Kind.DEAF, player.level().getBlockState(pos).getBlock());
        } else if (payload.clear() && !changed) {
            message = Component.translatable(key + "counter_cleared").withStyle(ChatFormatting.AQUA);
        } else {
            message = Component.translatable(key + "counter_set",
                    RadioReceiverBlockEntity.counterText(rx.getCounterTarget(), rx.isCounterLatch(), rx.getForgetTicks()))
                    .withStyle(ChatFormatting.AQUA);
        }
        player.displayClientMessage(message, true);
        return message;
    }
}
