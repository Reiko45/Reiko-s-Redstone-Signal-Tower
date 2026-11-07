package com.reiko.signaltower.network;

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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

/**
 * Client to server (v0.36.0), from the Guests screen: make {@code name} a guest on the owner's
 * private frequency for {@code seconds} (an hour, a day or a week; giving it again renews it), or
 * take their pass back ({@code seconds} 0). A guest can send on it (and hear it, if it's
 * encrypted) until the pass runs out; then they drop off, and their blocks go silent again.
 */
public record GuestPayload(int frequency, String name, int seconds) implements CustomPacketPayload {
    public static final Type<GuestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "guest"));

    public static final StreamCodec<ByteBuf, GuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuestPayload::frequency,
            ByteBufCodecs.stringUtf8(64), GuestPayload::name,
            ByteBufCodecs.VAR_INT, GuestPayload::seconds,
            GuestPayload::new);

    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(GuestPayload payload, IPayloadContext context) {
        apply(context.player(), payload, System.currentTimeMillis());
    }

    /** "1 hour", "1 day" or "1 week". */
    public static Component duration(int seconds) {
        return Component.translatable(KEY + "guest_for_" + seconds);
    }

    /**
     * Adds, renews or removes the guest if {@code player} owns the frequency, and tells them (in
     * the open screen) and the guest (in chat). Returns false if refused. Public for the GameTests.
     */
    public static boolean apply(Player player, GuestPayload payload, long nowMillis) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        int frequency = Frequency.clamp(payload.frequency());
        FrequencyClaims claims = FrequencyClaims.get(server);
        FrequencyClaims.Claim claim = claims.get(frequency);
        Object label = FrequencyNames.label(player.level(), frequency);
        String name = payload.name().strip();
        if (claim == null) {
            notice(player, Component.translatable(KEY + "guest_public", label).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!claim.owner().equals(player.getUUID())) {
            notice(player, Component.translatable(KEY + "guest_not_owner", label, claim.ownerName()).withStyle(ChatFormatting.RED));
            return false;
        }
        Map.Entry<UUID, FrequencyClaims.Guest> existing = claim.guestNamed(name);

        if (payload.seconds() == 0) {
            if (existing == null) {
                notice(player, Component.translatable(KEY + "guest_not_guest", name, label).withStyle(ChatFormatting.RED));
                return false;
            }
            FrequencyClaims.Claim updated = claims.removeGuest(frequency, existing.getKey());
            SetFrequencyPrivacyPayload.changed(server, frequency, updated);
            notice(player, Component.translatable(KEY + "guest_removed", existing.getValue().name(), label).withStyle(ChatFormatting.GRAY));
            tellOnline(server, existing.getKey(), Component.translatable(KEY + "guest_taken_back", player.getGameProfile().getName(), label)
                    .withStyle(ChatFormatting.YELLOW));
            return true;
        }

        if (!FrequencyClaims.GUEST_SECONDS.contains(payload.seconds())) {
            return false;
        }
        UUID guestId;
        String guestName;
        if (existing != null) {
            guestId = existing.getKey();
            guestName = existing.getValue().name();
        } else {
            if (name.isEmpty()) {
                notice(player, Component.translatable(KEY + "guest_no_name").withStyle(ChatFormatting.RED));
                return false;
            }
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online == null) {
                notice(player, Component.translatable(KEY + "guest_offline", name).withStyle(ChatFormatting.RED));
                return false;
            }
            guestId = online.getUUID();
            guestName = online.getGameProfile().getName();
            if (claim.allows(guestId)) {
                notice(player, Component.translatable(KEY + "guest_already_member", guestName, label).withStyle(ChatFormatting.RED));
                return false;
            }
            if (claim.guests().size() >= FrequencyClaims.MAX_GUESTS) {
                notice(player, Component.translatable(KEY + "guest_full", label, FrequencyClaims.MAX_GUESTS).withStyle(ChatFormatting.RED));
                return false;
            }
        }
        FrequencyClaims.Claim updated = claims.putGuest(frequency, guestId, guestName, nowMillis + payload.seconds() * 1000L);
        if (updated == null) {
            return false;
        }
        SetFrequencyPrivacyPayload.changed(server, frequency, updated);
        Component howLong = duration(payload.seconds());
        notice(player, Component.translatable(KEY + (existing != null ? "guest_renewed" : "guest_added"), guestName, label, howLong)
                .withStyle(ChatFormatting.GOLD));
        tellOnline(server, guestId, Component.translatable(KEY + "guest_welcome", player.getGameProfile().getName(), label, howLong)
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    /**
     * Takes every guest whose pass ran out off (about once a second, from the server tick): their
     * blocks go silent again, and the owner and the guest are told if they're online.
     */
    public static void expire(MinecraftServer server, long nowMillis) {
        FrequencyClaims claims = FrequencyClaims.get(server);
        for (FrequencyClaims.Expired expired : claims.expireGuests(nowMillis)) {
            SetFrequencyPrivacyPayload.changed(server, expired.frequency(), expired.claim());
            Object label = FrequencyNames.label(server.overworld(), expired.frequency());
            tellOnline(server, expired.claim().owner(), Component.translatable(KEY + "guest_ran_out_owner", expired.name(), label)
                    .withStyle(ChatFormatting.GRAY));
            tellOnline(server, expired.player(), Component.translatable(KEY + "guest_ran_out", label).withStyle(ChatFormatting.YELLOW));
        }
    }

    /** To the screen the player has open (the action bar if it's closed; a test player's action bar). */
    private static void notice(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            PacketDistributor.sendToPlayer(serverPlayer, new ScreenNoticePayload(message));
        } else {
            player.displayClientMessage(message, true);
        }
    }

    private static void tellOnline(MinecraftServer server, UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.displayClientMessage(message, false);
        }
    }
}
