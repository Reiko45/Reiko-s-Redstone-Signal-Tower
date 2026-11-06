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
 * Client to server: the Frequency Tuner screen's privacy button (Public, Private or Encrypted) and
 * Friends box for a frequency. {@code friends} is the names typed, separated by commas or spaces;
 * {@code encrypted} only counts when {@code makePrivate} is true.
 */
public record SetFrequencyPrivacyPayload(int frequency, boolean makePrivate, String friends, boolean encrypted)
        implements CustomPacketPayload {
    /** The longest friends list the box takes. */
    public static final int MAX_FRIENDS_TEXT = 400;

    public static final Type<SetFrequencyPrivacyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "set_frequency_privacy"));

    public static final StreamCodec<ByteBuf, SetFrequencyPrivacyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFrequencyPrivacyPayload::frequency,
            ByteBufCodecs.BOOL, SetFrequencyPrivacyPayload::makePrivate,
            ByteBufCodecs.stringUtf8(MAX_FRIENDS_TEXT * 4), SetFrequencyPrivacyPayload::friends,
            ByteBufCodecs.BOOL, SetFrequencyPrivacyPayload::encrypted,
            SetFrequencyPrivacyPayload::new);

    /** Private (or public again), not encrypted. */
    public SetFrequencyPrivacyPayload(int frequency, boolean makePrivate, String friends) {
        this(frequency, makePrivate, friends, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(SetFrequencyPrivacyPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * The player names in a friends list as typed: split on commas and spaces, without repeats
     * (ignoring case). Group references ("#Base crew") are left out; see {@link #parseGroups}.
     */
    public static List<String> parseNames(String text) {
        List<String> names = new ArrayList<>();
        for (String part : text.split(",")) {
            if (part.strip().startsWith("#")) {
                continue;
            }
            for (String word : part.split("\\s+")) {
                String name = word.strip();
                if (!name.isEmpty() && names.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** The group names in a friends list as typed: each comma-separated part starting with "#", without repeats. */
    public static List<String> parseGroups(String text) {
        List<String> groups = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.strip();
            if (trimmed.startsWith("#")) {
                String name = trimmed.substring(1).strip();
                if (!name.isEmpty() && groups.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                    groups.add(name);
                }
            }
        }
        return groups;
    }

    /** Players found for a list of names: who they are, which names weren't found, and whether some didn't fit. */
    public record Resolved(Map<UUID, String> players, List<String> missing, boolean tooMany) {}

    /**
     * Finds players by name for {@code owner}'s friends or group: someone online now by that name,
     * or someone in {@code known} (already a friend or member, kept by UUID, so they needn't be
     * online). The owner's own name is skipped; at most {@code max} are kept.
     */
    public static Resolved resolve(MinecraftServer server, Player owner, List<String> names, Map<UUID, String> known, int max) {
        Map<UUID, String> players = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        boolean tooMany = false;
        for (String name : names) {
            if (name.equalsIgnoreCase(owner.getGameProfile().getName())) {
                continue; // the owner isn't their own friend
            }
            UUID uuid = null;
            String found = name;
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                uuid = online.getUUID();
                found = online.getGameProfile().getName();
            } else {
                for (Map.Entry<UUID, String> entry : known.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(name)) {
                        uuid = entry.getKey();
                        found = entry.getValue();
                    }
                }
            }
            if (uuid == null) {
                missing.add(name);
            } else if (!uuid.equals(owner.getUUID()) && !players.containsKey(uuid)) {
                if (players.size() >= max) {
                    tooMany = true;
                } else {
                    players.put(uuid, found);
                }
            }
        }
        return new Resolved(players, missing, tooMany);
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
        Resolved resolved = resolve(server, player, parseNames(payload.friends()),
                claim == null ? Map.of() : claim.friends(), FrequencyClaims.MAX_FRIENDS);
        Map<UUID, String> friends = resolved.players();
        List<String> missing = resolved.missing();
        boolean tooMany = resolved.tooMany();
        // Groups: the player's own groups, by name ("#Base crew").
        List<String> groups = new ArrayList<>();
        List<String> unknownGroups = new ArrayList<>();
        boolean tooManyGroups = false;
        for (String name : parseGroups(payload.friends())) {
            FrequencyClaims.Group group = claims.group(player.getUUID(), name);
            if (group == null) {
                unknownGroups.add(name);
            } else if (groups.size() >= FrequencyClaims.MAX_GROUPS_PER_CLAIM) {
                tooManyGroups = true;
            } else {
                groups.add(group.name());
            }
        }
        if (!unknownGroups.isEmpty()) {
            player.displayClientMessage(Component.translatable(key + "private_no_group",
                    String.join(", ", unknownGroups)).withStyle(ChatFormatting.YELLOW), false);
        }
        if (tooManyGroups) {
            player.displayClientMessage(Component.translatable(key + "private_too_many_groups",
                    FrequencyClaims.MAX_GROUPS_PER_CLAIM).withStyle(ChatFormatting.YELLOW), false);
        }

        if (claim == null) {
            // Don't let anyone silence other players' blocks by claiming a frequency they're using.
            int others = otherSenders(server, frequency, player.getUUID());
            if (others > 0) {
                tell(player, Component.translatable(key + "private_in_use", label, others).withStyle(ChatFormatting.RED));
                return false;
            }
        } else if (claim.friends().equals(friends) && claim.groups().equals(groups) && claim.encrypted() == payload.encrypted()) {
            reportFriends(player, key, missing, tooMany);
            return false;
        }

        FrequencyClaims.Claim stored = claims.put(frequency, player.getUUID(), player.getGameProfile().getName(), friends, groups,
                payload.encrypted());
        changed(server, frequency, stored);
        String kind = stored.encrypted() ? "encrypted_set" : "private_set";
        tell(player, stored.friends().isEmpty() && stored.groups().isEmpty()
                ? Component.translatable(key + kind, label).withStyle(ChatFormatting.GOLD)
                : Component.translatable(key + kind + "_friends", label,
                        FrequencyClaims.friendsText(stored.groups(), stored.friendNames())).withStyle(ChatFormatting.GOLD));
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
     * After a claim changed: every dimension works out again who may send on (and, if it's
     * encrypted, hear) the frequency, and every player is told ({@code claim} null: it's public now).
     */
    public static void changed(MinecraftServer server, int frequency, @Nullable FrequencyClaims.Claim claim) {
        for (ServerLevel level : server.getAllLevels()) {
            SignalNetwork.get(level).invalidate(frequency);
        }
        PacketDistributor.sendToAllPlayers(new FrequencyClaimPayload(frequency,
                Optional.ofNullable(claim).map(FrequencyClaims.ClaimInfo::of)));
    }
}
