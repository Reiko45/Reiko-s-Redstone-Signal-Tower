package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sneak + scroll with a Frequency Tuner: step its frequency up or down by 1, or with
 * {@code favorites} (Ctrl held) jump to the previous or next favorite.
 */
public record ScrollTunerPayload(boolean offhand, boolean up, boolean favorites) implements CustomPacketPayload {
    public static final Type<ScrollTunerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "scroll_tuner"));

    public static final StreamCodec<ByteBuf, ScrollTunerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ScrollTunerPayload::offhand,
            ByteBufCodecs.BOOL, ScrollTunerPayload::up,
            ByteBufCodecs.BOOL, ScrollTunerPayload::favorites,
            ScrollTunerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ScrollTunerPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof FrequencyTunerItem)) {
            return;
        }
        Component message = FrequencyTunerItem.scroll(stack, payload.up(), payload.favorites());
        player.displayClientMessage(message, true);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.35F, payload.up() ? 1.9F : 1.6F);
        }
    }
}
