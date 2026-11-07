package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.36.0): the answer to something done in a screen that stays open (the
 * Guests screen), shown in that screen; above the hotbar if it has been closed.
 */
public record ScreenNoticePayload(Component message) implements CustomPacketPayload {
    public static final Type<ScreenNoticePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "screen_notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, ScreenNoticePayload::message,
            ScreenNoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(ScreenNoticePayload payload, IPayloadContext context) {
        ClientHooks.onScreenNotice(payload.message());
    }
}
