package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.RemoteButtons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the Multi-Button Remote keypad: save these button settings, and optionally press one
 * ({@code pressIndex} 0-3, or -1 to just save).
 */
public record ConfigureMultiRemotePayload(boolean offhand, RemoteButtons buttons, int pressIndex) implements CustomPacketPayload {
    public static final Type<ConfigureMultiRemotePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_multi_remote"));

    public static final StreamCodec<ByteBuf, ConfigureMultiRemotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConfigureMultiRemotePayload::offhand,
            RemoteButtons.STREAM_CODEC, ConfigureMultiRemotePayload::buttons,
            ByteBufCodecs.VAR_INT, ConfigureMultiRemotePayload::pressIndex,
            ConfigureMultiRemotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. RemoteButtons already clamps frequencies and the selection. */
    public static void handle(ConfigureMultiRemotePayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof MultiRemoteItem)) {
            return;
        }
        MultiRemoteItem.setButtons(stack, payload.buttons());
        int index = payload.pressIndex();
        if (index >= 0 && index < RemoteButtons.COUNT && player.level() instanceof ServerLevel level
                && !player.getCooldowns().isOnCooldown(stack.getItem())) {
            MultiRemoteItem.pressButton(level, player, stack, index);
            player.getCooldowns().addCooldown(stack.getItem(), 4);
        }
    }
}
