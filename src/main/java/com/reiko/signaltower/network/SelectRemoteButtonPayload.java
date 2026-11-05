package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when the player sneak-scrolls while holding a Multi-Button Remote: move the selection by one. */
public record SelectRemoteButtonPayload(boolean offhand, boolean forward) implements CustomPacketPayload {
    public static final Type<SelectRemoteButtonPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "select_remote_button"));

    public static final StreamCodec<ByteBuf, SelectRemoteButtonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SelectRemoteButtonPayload::offhand,
            ByteBufCodecs.BOOL, SelectRemoteButtonPayload::forward,
            SelectRemoteButtonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectRemoteButtonPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof MultiRemoteItem) {
            MultiRemoteItem.cycleSelection(player, stack, payload.forward() ? 1 : -1);
        }
    }
}
