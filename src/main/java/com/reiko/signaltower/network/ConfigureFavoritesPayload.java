package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.RadioFavorites;
import com.reiko.signaltower.item.TunerPresets;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by the frequency screen (tuner, Remote Control, pager) whenever a favorite is saved or cleared. */
public record ConfigureFavoritesPayload(boolean offhand, TunerPresets presets) implements CustomPacketPayload {
    public static final Type<ConfigureFavoritesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_favorites"));

    public static final StreamCodec<ByteBuf, ConfigureFavoritesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConfigureFavoritesPayload::offhand,
            TunerPresets.STREAM_CODEC, ConfigureFavoritesPayload::presets,
            ConfigureFavoritesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureFavoritesPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (RadioFavorites.isScrollable(stack)) {
            RadioFavorites.set(stack, payload.presets());
        }
    }
}
