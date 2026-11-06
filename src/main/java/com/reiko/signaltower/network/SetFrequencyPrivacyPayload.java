package com.reiko.signaltower.network;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Client to server: the Frequency Tuner screen's Private button and Friends box for a frequency.
 * {@code friends} is the names typed, separated by commas or spaces.
 */
public record SetFrequencyPrivacyPayload(int frequency, boolean makePrivate, String friends) implements CustomPacketPayload {
    /** The longest friends list the box takes. */
    public static final int MAX_FRIENDS_TEXT = 400;

    public static final Type<SetFrequencyPrivacyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "set_frequency_privacy"));

    public static final StreamCodec<ByteBuf, SetFrequencyPrivacyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFrequencyPrivacyPayload::frequency,
            ByteBufCodecs.BOOL, SetFrequencyPrivacyPayload::makePrivate,
            ByteBufCodecs.stringUtf8(MAX_FRIENDS_TEXT * 4), SetFrequencyPrivacyPayload::friends,
            SetFrequencyPrivacyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(SetFrequencyPrivacyPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /** The names in a friends list as typed: split on commas and spaces, without repeats (ignoring case). */
    public static List<String> parseNames(String text) {
        List<String> names = new ArrayList<>();
        for (String part : text.split("[,\\s]+")) {
            String name = part.strip();
            if (!name.isEmpty() && names.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Makes the frequency private (or changes its friends), or public again, if the player is
     * holding a Frequency Tuner and is allowed to: only the owner can change a private frequency,
     * though server operators can make any frequency public. Tells every player on the server,
     * and tells this player what happened. Returns false if nothing changed. Public for the
     * GameTests.
     */
    public static boolean apply(Player player, SetFrequencyPrivacyPayload payload) {
        MinecraftServer server = player.getServer();
        boolean holdingTuner = player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
        if (server == null || !holdingTuner) {
            return false;
        }
        int frequency = Frequency.clamp(payload.frequency());
        FrequencyClaims claims = FrequencyClaims.get(server);
        FrequencyClaims.Claim claim = claims.get(frequency);
        String key = "message." + SignalTower.MOD_ID + ".";
        Object label = FrequencyNames.label(player.level(), frequency);
        boolean owner = claim != null && claim.owner().equals(player.getUUID());

        if (!payload.makePrivate()) {
            if (claim == null) {
                return false;
            }
            if (!owner && !player.hasPermissions(2)) {
                tell(player, Component.translatable(key + "private_not_yours", label, claim.ownerName()).withStyle(ChatFormatting.RED));
                return false;
            }
            claims.remove(frequency);
            changed(server, frequency, null);
            tell(player, Component.translatable(key + "private_removed", label).withStyle(ChatFormatting.GRAY));
            return true;
        }

        if (claim != null && !owner) {
            tell(player, Component.translatable(key + "private_not_yours", label, claim.ownerName()).withStyle(ChatFormatting.RED));
            return false;
        }
        if (claim == null) {
            int limit = Config.PRIVATE_FREQUENCIES_PER_PLAYER.get();
            if (limit <= 0) {
                tell(player, Component.translatable(key + "private_disabled").withStyle(ChatFormatting.RED));
                return false;
            }
            if (claims.countOwnedBy(player.getUUID()) >= limit) {
                tell(player, Component.translatable(key + "private_limit", limit).withStyle(ChatFormatting.RED));
                return false;
            }
            if (claims.size() >= FrequencyClaims.MAX_CLAIMS) {
                tell(player, Component.translatable(key + "private_full", FrequencyClaims.MAX_CLAIMS).withStyle(ChatFormatting.RED));
                return false;
            }
        }

        // Friends: players online now by that name, or friends it already has (kept by UUID, so they needn't be online).
        Map<UUID, String> friends = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        boolean tooMany = false;
        for (String name : parseNames(payload.friends())) {
            if (name.equalsIgnoreCase(player.getGameProfile().getName())) {
                continue; // the owner isn't their own friend
            }
            UUID uuid = null;
            String found = name;
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                uuid = online.getUUID();
                found = online.getGameProfile().getName();
            } else if (claim != null) {
                for (Map.Entry<UUID, String> friend : claim.friends().entrySet()) {
                    if (friend.getValue().equalsIgnoreCase(name)) {
                        uuid = friend.getKey();
                        found = friend.getValue();
                    }
                }
            }
            if (uuid == null) {
                missing.add(name);
            } else if (!uuid.equals(player.getUUID()) && !friends.containsKey(uuid)) {
                if (friends.size() >= FrequencyClaims.MAX_FRIENDS) {
                    tooMany = true;
                } else {
                    friends.put(uuid, found);
                }
            }
        }

        if (claim == null) {
            // Don't let anyone silence other players' blocks by claiming a frequency they're using.
            int others = otherSenders(server, frequency, player.getUUID());
            if (others > 0) {
                tell(player, Component.translatable(key + "private_in_use", label, others).withStyle(ChatFormatting.RED));
                return false;
            }
        } else if (claim.friends().equals(friends)) {
            reportFriends(player, key, missing, tooMany);
            return false;
        }

        FrequencyClaims.Claim stored = claims.put(frequency, player.getUUID(), player.getGameProfile().getName(), friends);
        changed(server, frequency, stored);
        tell(player, stored.friends().isEmpty()
                ? Component.translatable(key + "private_set", label).withStyle(ChatFormatting.GOLD)
                : Component.translatable(key + "private_set_friends", label,
                        String.join(", ", stored.friendNames())).withStyle(ChatFormatting.GOLD));
        reportFriends(player, key, missing, tooMany);
        return true;
    }

    /**
     * How many transmitters, repeaters and converters, in every dimension, send on
     * {@code frequency} that someone other than {@code player} tuned or placed. A new claim needs
     * none. The friends being added count too: otherwise listing someone as a friend and then
     * taking them off would get round it. Public for the GameTests.
     */
    public static int otherSenders(MinecraftServer server, int frequency, UUID player) {
        int others = 0;
        for (ServerLevel level : server.getAllLevels()) {
            others += SignalNetwork.get(level).countOtherSenders(frequency, player::equals);
        }
        return others;
    }

    /** In chat, so it stays after the action-bar message: names that weren't added, and why. */
    private static void reportFriends(Player player, String key, List<String> missing, boolean tooMany) {
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable(key + "private_friends_missing",
                    String.join(", ", missing)).withStyle(ChatFormatting.YELLOW), false);
        }
        if (tooMany) {
            player.displayClientMessage(Component.translatable(key + "private_friends_too_many",
                    FrequencyClaims.MAX_FRIENDS).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * After a claim changed: every dimension works out again who may send on the frequency, and
     * every player is told ({@code claim} null: it's public now).
     */
    public static void changed(MinecraftServer server, int frequency, @Nullable FrequencyClaims.Claim claim) {
        for (ServerLevel level : server.getAllLevels()) {
            SignalNetwork.get(level).invalidate(frequency);
        }
        PacketDistributor.sendToAllPlayers(new FrequencyClaimPayload(frequency,
                Optional.ofNullable(claim).map(FrequencyClaims.ClaimInfo::of)));
    }
}
