package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.RadioFavorites;
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
 * Sneak + scroll with a Frequency Tuner, Remote Control or Radio Pager: step its frequency up or
 * down by 1, or with {@code favorites} (Ctrl held) jump to the previous or next favorite.
 */
public record ScrollFrequencyPayload(boolean offhand, boolean up, boolean favorites) implements CustomPacketPayload {
    public static final Type<ScrollFrequencyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "scroll_frequency"));

    public static final StreamCodec<ByteBuf, ScrollFrequencyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ScrollFrequencyPayload::offhand,
            ByteBufCodecs.BOOL, ScrollFrequencyPayload::up,
            ByteBufCodecs.BOOL, ScrollFrequencyPayload::favorites,
            ScrollFrequencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ScrollFrequencyPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!RadioFavorites.isScrollable(stack)) {
            return;
        }
        Component message = RadioFavorites.scroll(player.level(), stack, payload.up(), payload.favorites());
        player.displayClientMessage(message, true);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.playNotifySound(com.reiko.signaltower.registry.ModSounds.TUNER_STEP.get(), SoundSource.PLAYERS, 0.5F, payload.up() ? 1.1F : 0.95F);
        }
    }
}
