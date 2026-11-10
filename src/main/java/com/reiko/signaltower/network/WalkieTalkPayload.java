package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.WalkieTalkie;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** v0.64.0: sent from the talk box: what the player says into the Walkie-Talkie in that hand. */
public record WalkieTalkPayload(boolean offhand, String text) implements CustomPacketPayload {
    public static final Type<WalkieTalkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "walkie_talk"));

    public static final StreamCodec<ByteBuf, WalkieTalkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WalkieTalkPayload::offhand,
            ByteBufCodecs.stringUtf8(256), WalkieTalkPayload::text,
            WalkieTalkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread: says it, and tells the player how it went (above the hotbar). */
    public static void handle(WalkieTalkPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            WalkieTalkie.Result result = WalkieTalkie.talk(player, payload.offhand(), payload.text());
            if (result != null) {
                player.displayClientMessage(result.message(), true);
            }
        }
    }
}
