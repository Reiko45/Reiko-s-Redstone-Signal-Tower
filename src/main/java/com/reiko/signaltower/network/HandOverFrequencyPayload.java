package com.reiko.signaltower.network;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

/**
 * Client to server (v0.34.0): the Hand Over screen gives one of the player's private frequencies to
 * {@code newOwner}, one of its friends or group members (by name). They become its owner; everyone
 * else on it, the old owner included, stays on it as a friend, so no block goes silent.
 */
public record HandOverFrequencyPayload(int frequency, String newOwner) implements CustomPacketPayload {
    public static final Type<HandOverFrequencyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "hand_over_frequency"));

    public static final StreamCodec<ByteBuf, HandOverFrequencyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HandOverFrequencyPayload::frequency,
            ByteBufCodecs.stringUtf8(64), HandOverFrequencyPayload::newOwner,
            HandOverFrequencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(HandOverFrequencyPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Hands the frequency over if {@code player} owns it and the new owner is one of its members
     * who may own one more, and tells both of them. Returns false if it was refused. Public for
     * the GameTests.
     */
    public static boolean apply(Player player, HandOverFrequencyPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        int frequency = Frequency.clamp(payload.frequency());
        FrequencyClaims claims = FrequencyClaims.get(server);
        FrequencyClaims.Claim claim = claims.get(frequency);
        String key = "message." + SignalTower.MOD_ID + ".";
        Object label = FrequencyNames.label(player.level(), frequency);
        if (claim == null) {
            refuse(player, Component.translatable(key + "hand_over_public", label));
            return false;
        }
        if (!claim.owner().equals(player.getUUID())) {
            refuse(player, Component.translatable(key + "hand_over_not_owner", label, claim.ownerName()));
            return false;
        }
        Map.Entry<UUID, String> newOwner = FrequencyClaims.memberNamed(claim, payload.newOwner());
        if (newOwner == null) {
            refuse(player, Component.translatable(key + "hand_over_not_member", payload.newOwner().strip(), label));
            return false;
        }
        int limit = Config.PRIVATE_FREQUENCIES_PER_PLAYER.get();
        if (limit > 0 && claims.countOwnedBy(newOwner.getKey()) >= limit) {
            refuse(player, Component.translatable(key + "hand_over_limit", newOwner.getValue(), limit));
            return false;
        }
        int members = FrequencyClaims.friendsAfterHandOver(claim, newOwner.getKey()).size();
        if (members > FrequencyClaims.MAX_FRIENDS) {
            refuse(player, Component.translatable(key + "hand_over_too_many", label, members, FrequencyClaims.MAX_FRIENDS));
            return false;
        }
        FrequencyClaims.Claim handed = claims.handOver(frequency, newOwner.getKey(), newOwner.getValue());
        if (handed == null) {
            return false;
        }
        SetFrequencyPrivacyPayload.changed(server, frequency, handed);
        // In chat (not above the hotbar): the Hand Over screen goes back to the directory, which would hide it.
        player.displayClientMessage(Component.translatable(key + "hand_over_done", label, newOwner.getValue())
                .withStyle(ChatFormatting.GOLD), false);
        ServerPlayer receiver = server.getPlayerList().getPlayer(newOwner.getKey());
        if (receiver != null) {
            receiver.displayClientMessage(Component.translatable(key + "hand_over_received", player.getGameProfile().getName(), label)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        return true;
    }

    private static void refuse(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), false);
    }
}
