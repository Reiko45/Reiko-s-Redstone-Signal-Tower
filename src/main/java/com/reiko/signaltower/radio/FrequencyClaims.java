package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Private frequencies: who owns which frequency, and who their friends are. Saved with the world
 * (in the Overworld's data; a claim counts in every dimension) and sent to every player, so the
 * tuner's screen can show it.
 * <p>
 * On a private frequency only the owner and their friends can send: their remote presses count,
 * and transmitters, repeaters and converters only broadcast on it if one of them tuned or placed
 * the block ({@link #maySend}). Anyone can still listen. Blocks tuned before private frequencies
 * existed have no recorded tuner and keep working.
 * <p>
 * Friends can be listed one by one, or as <b>friend groups</b> (v0.27.0): a player's named lists
 * of players ("Base crew") that any of their private frequencies can use. A claim keeps the names
 * of the groups it uses, so changing a group changes who can send on every frequency using it.
 * <p>
 * An <b>encrypted</b> private frequency (v0.29.0) is also closed for listening: only receivers,
 * toggle receivers and converters tuned or placed by a member, and pagers and scanners carried by
 * one, hear it ({@link #mayHear}). Everyone else's stay deaf to it.
 */
public final class FrequencyClaims extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_frequency_claims";
    private static final SavedData.Factory<FrequencyClaims> FACTORY =
            new SavedData.Factory<>(FrequencyClaims::new, FrequencyClaims::load, null);

    /** The most friends one private frequency can have, listed one by one. */
    public static final int MAX_FRIENDS = 16;
    /** The most private frequencies one world can have (so the list sent to joining players stays small). */
    public static final int MAX_CLAIMS = 4096;
    /** The most friend groups one player can have. */
    public static final int MAX_GROUPS = 16;
    /** The most players in one group. */
    public static final int MAX_GROUP_MEMBERS = 32;
    /** The most groups one private frequency can use. */
    public static final int MAX_GROUPS_PER_CLAIM = 4;
    /** The longest group name. */
    public static final int MAX_GROUP_NAME = 24;
    /** The most names a claim can let send, besides its owner. */
    public static final int MAX_MEMBERS = MAX_FRIENDS + MAX_GROUPS_PER_CLAIM * MAX_GROUP_MEMBERS;
    /** The most guests one private frequency can have at once (v0.36.0). */
    public static final int MAX_GUESTS = 8;
    /** How long a guest pass can be: an hour, a day or a week (in seconds, real time). */
    public static final List<Integer> GUEST_SECONDS = List.of(3600, 86400, 604800);

    /**
     * A guest on a private frequency (v0.36.0): who (by name, as when added) and until when, in
     * real time (milliseconds since 1970, the server's clock), so a pass keeps running while the
     * server is off. Until then they're a member like a friend; afterwards they drop off.
     */
    public record Guest(String name, long untilMillis) {
    }

    /** A player's friend group: its name and its members (by UUID, with the names they had when added). */
    public record Group(String name, Map<UUID, String> members) {
        public Group {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members)); // keeps the order they were added in
        }

        public List<String> memberNames() {
            return List.copyOf(members.values());
        }
    }

    /**
     * One private frequency: its owner, the friends listed one by one, the owner's groups it uses
     * (by name), everyone in those groups ({@code groupMembers}, worked out from the groups and
     * kept up to date when they change), and whether only members can hear it ({@code encrypted}).
     */
    public record Claim(UUID owner, String ownerName, Map<UUID, String> friends, List<String> groups,
                        Map<UUID, String> groupMembers, boolean encrypted, Map<UUID, Guest> guests) {
        public Claim {
            friends = Collections.unmodifiableMap(new LinkedHashMap<>(friends)); // keeps the order they were added in
            groups = List.copyOf(groups);
            groupMembers = Collections.unmodifiableMap(new LinkedHashMap<>(groupMembers));
            guests = Collections.unmodifiableMap(new LinkedHashMap<>(guests));
        }

        public Claim(UUID owner, String ownerName, Map<UUID, String> friends, List<String> groups,
                     Map<UUID, String> groupMembers, boolean encrypted) {
            this(owner, ownerName, friends, groups, groupMembers, encrypted, Map.of());
        }

        public Claim(UUID owner, String ownerName, Map<UUID, String> friends, List<String> groups,
                     Map<UUID, String> groupMembers) {
            this(owner, ownerName, friends, groups, groupMembers, false);
        }

        public Claim(UUID owner, String ownerName, Map<UUID, String> friends) {
            this(owner, ownerName, friends, List.of(), Map.of());
        }

        /** True for the owner, their friends, the members of the groups it uses and its guests. */
        public boolean allows(@Nullable UUID player) {
            return player != null && (player.equals(owner) || friends.containsKey(player) || groupMembers.containsKey(player)
                    || guests.containsKey(player));
        }

        /** True if a receiver tuned by {@code listener} hears it: always unless it's encrypted and they aren't a member. */
        public boolean hears(@Nullable UUID listener) {
            return !encrypted || allows(listener);
        }

        /** This claim with other friends, groups and members, keeping its owner, encryption and guests. */
        Claim with(Map<UUID, String> friends, List<String> groups, Map<UUID, String> groupMembers) {
            return new Claim(owner, ownerName, friends, groups, groupMembers, encrypted, guests);
        }

        /** This claim with other guests (never its owner, a friend or a group member). */
        public Claim withGuests(Map<UUID, Guest> newGuests) {
            Map<UUID, Guest> kept = new LinkedHashMap<>();
            newGuests.forEach((uuid, guest) -> {
                if (!uuid.equals(owner) && !friends.containsKey(uuid) && !groupMembers.containsKey(uuid) && kept.size() < MAX_GUESTS) {
                    kept.put(uuid, guest);
                }
            });
            return new Claim(owner, ownerName, friends, groups, groupMembers, encrypted, kept);
        }

        /** The guest called {@code name} (ignoring case), or null. */
        public @Nullable Map.Entry<UUID, Guest> guestNamed(String name) {
            for (Map.Entry<UUID, Guest> guest : guests.entrySet()) {
                if (guest.getValue().name().equalsIgnoreCase(name.strip())) {
                    return guest;
                }
            }
            return null;
        }

        /** The friends listed one by one, in the order they were added. */
        public List<String> friendNames() {
            return List.copyOf(friends.values());
        }

        /** Everyone besides the owner who can send on it, by name, without repeats. */
        public List<String> memberNames() {
            Map<UUID, String> all = new LinkedHashMap<>(friends);
            groupMembers.forEach((uuid, name) -> {
                if (!uuid.equals(owner)) all.putIfAbsent(uuid, name);
            });
            return List.copyOf(all.values());
        }
    }

    /**
     * What every client is told about a claim: the owner, the friends listed one by one, the groups
     * it uses, everyone who can send (friends and group members together), and whether only they
     * can hear it.
     */
    public record ClaimInfo(UUID owner, String ownerName, List<String> friends, List<String> groups, List<String> members,
                            boolean encrypted, List<GuestInfo> guests) {
        // Seven fields: more than StreamCodec.composite takes in 1.21.1, so written out by hand.
        public static final StreamCodec<ByteBuf, ClaimInfo> STREAM_CODEC = StreamCodec.of(
                (buffer, info) -> {
                    UUIDUtil.STREAM_CODEC.encode(buffer, info.owner());
                    ByteBufCodecs.stringUtf8(64).encode(buffer, info.ownerName());
                    ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_FRIENDS)).encode(buffer, info.friends());
                    ByteBufCodecs.stringUtf8(MAX_GROUP_NAME * 4).apply(ByteBufCodecs.list(MAX_GROUPS_PER_CLAIM)).encode(buffer, info.groups());
                    ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_MEMBERS)).encode(buffer, info.members());
                    ByteBufCodecs.BOOL.encode(buffer, info.encrypted());
                    GuestInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_GUESTS)).encode(buffer, info.guests());
                },
                buffer -> new ClaimInfo(
                        UUIDUtil.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.stringUtf8(64).decode(buffer),
                        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_FRIENDS)).decode(buffer),
                        ByteBufCodecs.stringUtf8(MAX_GROUP_NAME * 4).apply(ByteBufCodecs.list(MAX_GROUPS_PER_CLAIM)).decode(buffer),
                        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_MEMBERS)).decode(buffer),
                        ByteBufCodecs.BOOL.decode(buffer),
                        GuestInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_GUESTS)).decode(buffer)));

        public ClaimInfo(UUID owner, String ownerName, List<String> friends, List<String> groups, List<String> members,
                         boolean encrypted) {
            this(owner, ownerName, friends, groups, members, encrypted, List.of());
        }

        public ClaimInfo(UUID owner, String ownerName, List<String> friends, List<String> groups, List<String> members) {
            this(owner, ownerName, friends, groups, members, false);
        }

        public ClaimInfo(UUID owner, String ownerName, List<String> friends) {
            this(owner, ownerName, friends, List.of(), friends);
        }

        public static ClaimInfo of(Claim claim) {
            return new ClaimInfo(claim.owner(), claim.ownerName(), claim.friendNames(), claim.groups(), claim.memberNames(),
                    claim.encrypted(), claim.guests().values().stream().map(g -> new GuestInfo(g.name(), g.untilMillis())).toList());
        }

        /** True if the player with this UUID and name is the owner or can send on it (a guest too). */
        public boolean isMember(UUID player, String name) {
            return owner.equals(player) || hasMember(name);
        }

        /** True if {@code name} (ignoring case) can send on it besides the owner: a friend, a group member or a guest. */
        public boolean hasMember(String name) {
            return members.stream().anyMatch(member -> member.equalsIgnoreCase(name)) || guest(name) != null;
        }

        /** The guest called {@code name} (ignoring case), or null. */
        public @Nullable GuestInfo guest(String name) {
            return guests.stream().filter(guest -> guest.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        }

        /** True if it uses the group called {@code name} (ignoring case). */
        public boolean usesGroup(String name) {
            return groups.stream().anyMatch(group -> group.equalsIgnoreCase(name));
        }
    }

    /** What every client is told about a guest: their name and when their pass runs out (the server's clock, in ms). */
    public record GuestInfo(String name, long untilMillis) {
        public static final StreamCodec<ByteBuf, GuestInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), GuestInfo::name,
                ByteBufCodecs.VAR_LONG, GuestInfo::untilMillis,
                GuestInfo::new);
    }

    /** What a player's own client is told about one of their groups. */
    public record GroupInfo(String name, List<String> members) {
        public static final StreamCodec<ByteBuf, GroupInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_GROUP_NAME * 4), GroupInfo::name,
                ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_GROUP_MEMBERS)), GroupInfo::members,
                GroupInfo::new);

        public static GroupInfo of(Group group) {
            return new GroupInfo(group.name(), group.memberNames());
        }
    }

    /** The client's copy of every claim, filled in by the server's packets. Empty on a dedicated server. */
    private static final Map<Integer, ClaimInfo> CLIENT = new ConcurrentHashMap<>();
    /** The client's copy of its own player's groups. */
    private static volatile List<GroupInfo> clientGroups = List.of();

    private final Map<Integer, Claim> claims = new HashMap<>();
    /** Each player's groups, by lower-case name, in the order they were made. */
    private final Map<UUID, Map<String, Group>> groups = new HashMap<>();

    public static FrequencyClaims get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static String key(String groupName) {
        return groupName.toLowerCase(Locale.ROOT);
    }

    // ---- Checks (server) ----

    /** The claim on {@code frequency}, or null if it's public. */
    public @Nullable Claim get(int frequency) {
        return claims.get(frequency);
    }

    /** True if the frequency is public, or {@code player} is its owner, a friend or in one of its groups. */
    public boolean allows(int frequency, @Nullable UUID player) {
        Claim claim = claims.get(frequency);
        return claim == null || claim.allows(player);
    }

    /**
     * True if a block tuned (or placed) by {@code tunedBy} may broadcast on {@code frequency} in
     * {@code level}'s world: always on a public frequency, and on a private one if {@code tunedBy}
     * is the owner or a friend. A block with no recorded tuner ({@code null}: tuned before private
     * frequencies existed, or set by a command) may broadcast.
     */
    public static boolean maySend(Level level, int frequency, @Nullable UUID tunedBy) {
        if (tunedBy == null || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        Claim claim = get(serverLevel.getServer()).claims.get(frequency);
        return claim == null || claim.allows(tunedBy);
    }

    /**
     * True if a receiver, toggle receiver or converter tuned (or placed) by {@code listener}, or a
     * pager or scanner carried by {@code listener}, hears {@code frequency} in {@code level}'s
     * world: always, unless the frequency is encrypted and {@code listener} is neither its owner
     * nor a member. A block with no recorded tuner ({@code null}) hears, like it may send.
     */
    public static boolean mayHear(Level level, int frequency, @Nullable UUID listener) {
        if (listener == null || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        Claim claim = get(serverLevel.getServer()).claims.get(frequency);
        return claim == null || claim.hears(listener);
    }

    /**
     * A red "{@code key}" message if {@code listener} can't hear {@code frequency} (it's someone
     * else's encrypted frequency), else null. The message gets the frequency's label and the
     * owner's name.
     */
    public static @Nullable Component deafness(Level level, int frequency, @Nullable UUID listener, String key) {
        if (listener == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Claim claim = get(serverLevel.getServer()).claims.get(frequency);
        if (claim == null || claim.hears(listener)) {
            return null;
        }
        return Component.translatable("message." + SignalTower.MOD_ID + "." + key,
                FrequencyNames.label(level, frequency), claim.ownerName()).withStyle(ChatFormatting.RED);
    }

    /**
     * A red "{@code key}" message if {@code player} may not send on {@code frequency} (it's someone
     * else's private frequency), else null. The message gets the frequency's label and the owner's
     * name.
     */
    public static @Nullable Component refusal(Level level, int frequency, @Nullable UUID player, String key) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Claim claim = get(serverLevel.getServer()).claims.get(frequency);
        if (claim == null || claim.allows(player)) {
            return null;
        }
        return Component.translatable("message." + SignalTower.MOD_ID + "." + key,
                FrequencyNames.label(level, frequency), claim.ownerName()).withStyle(ChatFormatting.RED);
    }

    /** How many private frequencies there are. */
    public int size() {
        return claims.size();
    }

    /** How many frequencies {@code player} owns. */
    public int countOwnedBy(UUID player) {
        int count = 0;
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(player)) count++;
        }
        return count;
    }

    // ---- Groups (server) ----

    /** {@code owner}'s groups, in the order they were made. */
    public List<Group> groupsOf(UUID owner) {
        return List.copyOf(groups.getOrDefault(owner, Map.of()).values());
    }

    /** {@code owner}'s group called {@code name} (ignoring case), or null. */
    public @Nullable Group group(UUID owner, String name) {
        return groups.getOrDefault(owner, Map.of()).get(key(name));
    }

    /** The frequencies (owned by {@code owner}) using their group called {@code name}. */
    public Set<Integer> frequenciesUsing(UUID owner, String name) {
        Set<Integer> using = new TreeSet<>();
        claims.forEach((frequency, claim) -> {
            if (claim.owner().equals(owner) && claim.groups().stream().anyMatch(g -> g.equalsIgnoreCase(name))) {
                using.add(frequency);
            }
        });
        return using;
    }

    /**
     * Saves {@code owner}'s group: a new one ({@code oldName} null), or a changed one, possibly
     * renamed (the frequencies using it follow the new name). Members are capped at
     * {@link #MAX_GROUP_MEMBERS}, without the owner. Returns the frequencies whose senders may have
     * changed.
     */
    public Set<Integer> putGroup(UUID owner, @Nullable String oldName, String name, Map<UUID, String> members) {
        Map<UUID, String> limited = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> member : members.entrySet()) {
            if (limited.size() < MAX_GROUP_MEMBERS && !member.getKey().equals(owner)) {
                limited.put(member.getKey(), member.getValue());
            }
        }
        Map<String, Group> mine = groups.computeIfAbsent(owner, uuid -> new LinkedHashMap<>());
        Set<Integer> affected = oldName == null ? new TreeSet<>() : frequenciesUsing(owner, oldName);
        if (oldName != null && !key(oldName).equals(key(name))) {
            // Renamed: keep its place in the order, and point the frequencies using it at the new name.
            Map<String, Group> reordered = new LinkedHashMap<>();
            mine.forEach((k, g) -> reordered.put(k.equals(key(oldName)) ? key(name) : k, g));
            mine.clear();
            mine.putAll(reordered);
            for (int frequency : affected) {
                Claim claim = claims.get(frequency);
                List<String> renamed = new ArrayList<>();
                for (String g : claim.groups()) {
                    renamed.add(g.equalsIgnoreCase(oldName) ? name : g);
                }
                claims.put(frequency, claim.with(claim.friends(), renamed, Map.of()));
            }
        }
        mine.put(key(name), new Group(name, limited));
        affected.addAll(frequenciesUsing(owner, name));
        for (int frequency : affected) {
            claims.put(frequency, withMembers(claims.get(frequency)));
        }
        setDirty();
        return affected;
    }

    /** Deletes {@code owner}'s group; the frequencies using it drop it. Returns those frequencies. */
    public Set<Integer> removeGroup(UUID owner, String name) {
        Map<String, Group> mine = groups.get(owner);
        if (mine == null || mine.remove(key(name)) == null) {
            return Set.of();
        }
        if (mine.isEmpty()) {
            groups.remove(owner);
        }
        Set<Integer> affected = frequenciesUsing(owner, name);
        for (int frequency : affected) {
            Claim claim = claims.get(frequency);
            List<String> left = claim.groups().stream().filter(g -> !g.equalsIgnoreCase(name)).toList();
            claims.put(frequency, withMembers(claim.with(claim.friends(), left, Map.of())));
        }
        setDirty();
        return affected;
    }

    /** {@code claim} with its group names matched to the owner's groups (missing ones dropped) and their members filled in. */
    private Claim withMembers(Claim claim) {
        List<String> names = new ArrayList<>();
        Map<UUID, String> members = new LinkedHashMap<>();
        for (String name : claim.groups()) {
            Group group = group(claim.owner(), name);
            if (group != null && names.size() < MAX_GROUPS_PER_CLAIM && names.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
                names.add(group.name());
                group.members().forEach(members::putIfAbsent);
            }
        }
        members.remove(claim.owner());
        return claim.with(claim.friends(), names, members);
    }

    /** {@code owner}'s groups as their client is told about them. */
    public List<GroupInfo> groupInfo(UUID owner) {
        return groupsOf(owner).stream().map(GroupInfo::of).toList();
    }

    // ---- Changes (server) ----

    /** Makes {@code frequency} private to {@code owner}, keeping {@code friends}. Replaces any claim on it. */
    public Claim put(int frequency, UUID owner, String ownerName, Map<UUID, String> friends) {
        return put(frequency, owner, ownerName, friends, List.of());
    }

    /** {@link #put(int, UUID, String, Map, List, boolean)}, not encrypted. */
    public Claim put(int frequency, UUID owner, String ownerName, Map<UUID, String> friends, List<String> groupNames) {
        return put(frequency, owner, ownerName, friends, groupNames, false);
    }

    /**
     * Makes {@code frequency} private to {@code owner}, keeping {@code friends} and using the
     * owner's groups named in {@code groupNames} (ones they don't have are left out), and
     * {@code encrypted} (only members hear it) or not. Replaces any claim on it.
     */
    public Claim put(int frequency, UUID owner, String ownerName, Map<UUID, String> friends, List<String> groupNames,
                     boolean encrypted) {
        Map<UUID, String> limited = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> friend : friends.entrySet()) {
            if (limited.size() < MAX_FRIENDS && !friend.getKey().equals(owner)) {
                limited.put(friend.getKey(), friend.getValue());
            }
        }
        Claim existing = claims.get(Frequency.clamp(frequency));
        Claim claim = withMembers(new Claim(owner, ownerName, limited, groupNames, Map.of(), encrypted));
        if (existing != null && existing.owner().equals(owner) && !existing.guests().isEmpty()) {
            claim = claim.withGuests(existing.guests()); // editing friends or encryption keeps the guests (not ones who became friends)
        }
        claims.put(Frequency.clamp(frequency), claim);
        setDirty();
        return claim;
    }

    // ---- Guests (v0.36.0) ----

    /**
     * Makes {@code player} a guest on {@code frequency} until {@code untilMillis} (renewing them if
     * they already are). Returns the claim, or null if it isn't private, they're its owner, a friend
     * or a group member, or it already has {@link #MAX_GUESTS} other guests.
     */
    public @Nullable Claim putGuest(int frequency, UUID player, String name, long untilMillis) {
        Claim claim = get(frequency);
        if (claim == null || player.equals(claim.owner()) || claim.friends().containsKey(player)
                || claim.groupMembers().containsKey(player)
                || !claim.guests().containsKey(player) && claim.guests().size() >= MAX_GUESTS) {
            return null;
        }
        Map<UUID, Guest> guests = new LinkedHashMap<>(claim.guests());
        guests.put(player, new Guest(name, untilMillis));
        Claim updated = claim.withGuests(guests);
        claims.put(Frequency.clamp(frequency), updated);
        setDirty();
        return updated;
    }

    /** Takes {@code player}'s guest pass on {@code frequency} away. Returns the claim, or null if they weren't a guest. */
    public @Nullable Claim removeGuest(int frequency, UUID player) {
        Claim claim = get(frequency);
        if (claim == null || !claim.guests().containsKey(player)) {
            return null;
        }
        Map<UUID, Guest> guests = new LinkedHashMap<>(claim.guests());
        guests.remove(player);
        Claim updated = claim.withGuests(guests);
        claims.put(Frequency.clamp(frequency), updated);
        setDirty();
        return updated;
    }

    /** A guest whose pass ran out: on which frequency, who, and the claim as it is now. */
    public record Expired(int frequency, UUID player, String name, Claim claim) {
    }

    /** Takes every guest whose pass ran out by {@code nowMillis} off, and returns them. */
    public List<Expired> expireGuests(long nowMillis) {
        List<Expired> expired = new ArrayList<>();
        for (Map.Entry<Integer, Claim> entry : new ArrayList<>(claims.entrySet())) {
            Claim claim = entry.getValue();
            if (claim.guests().isEmpty()) {
                continue;
            }
            Map<UUID, Guest> left = new LinkedHashMap<>();
            List<Map.Entry<UUID, Guest>> gone = new ArrayList<>();
            claim.guests().forEach((uuid, guest) -> {
                if (guest.untilMillis() <= nowMillis) {
                    gone.add(Map.entry(uuid, guest));
                } else {
                    left.put(uuid, guest);
                }
            });
            if (!gone.isEmpty()) {
                Claim updated = claim.withGuests(left);
                claims.put(entry.getKey(), updated);
                setDirty();
                gone.forEach(guest -> expired.add(new Expired(entry.getKey(), guest.getKey(), guest.getValue().name(), updated)));
            }
        }
        return expired;
    }

    // ---- Handing a frequency over (v0.34.0) ----

    /**
     * The member of {@code claim} called {@code name} (ignoring case): one of its friends or a
     * member of a group it uses, never its owner. Null if there's none.
     */
    public static @Nullable Map.Entry<UUID, String> memberNamed(Claim claim, String name) {
        for (Map<UUID, String> members : List.of(claim.friends(), claim.groupMembers())) {
            for (Map.Entry<UUID, String> member : members.entrySet()) {
                if (!member.getKey().equals(claim.owner()) && member.getValue().equalsIgnoreCase(name.strip())) {
                    return Map.entry(member.getKey(), member.getValue());
                }
            }
        }
        return null;
    }

    /**
     * Who would be friends if {@code claim} went to {@code newOwner}: the old owner first, then its
     * friends, then its groups' members (groups belong to their owner, so they become single
     * friends), without the new owner. Its size is the claim's member count.
     */
    public static Map<UUID, String> friendsAfterHandOver(Claim claim, UUID newOwner) {
        Map<UUID, String> friends = new LinkedHashMap<>();
        friends.put(claim.owner(), claim.ownerName());
        claim.friends().forEach(friends::putIfAbsent);
        claim.groupMembers().forEach(friends::putIfAbsent);
        friends.remove(newOwner);
        return friends;
    }

    /**
     * Gives {@code frequency} to {@code newOwner} (who should be one of its members): everyone else
     * on it stays on it as a friend, and it stays encrypted if it was. Null if it isn't private or
     * would have more than {@link #MAX_FRIENDS} friends.
     */
    public @Nullable Claim handOver(int frequency, UUID newOwner, String newOwnerName) {
        Claim claim = get(frequency);
        if (claim == null) {
            return null;
        }
        Map<UUID, String> friends = friendsAfterHandOver(claim, newOwner);
        if (friends.size() > MAX_FRIENDS) {
            return null;
        }
        Claim handed = put(frequency, newOwner, newOwnerName, friends, List.of(), claim.encrypted());
        if (!claim.guests().isEmpty()) { // guests keep their passes (the new owner, if one, no longer needs it)
            handed = handed.withGuests(claim.guests());
            claims.put(Frequency.clamp(frequency), handed);
        }
        return handed;
    }

    /** Makes {@code frequency} public again. Returns true if it was private. */
    public boolean remove(int frequency) {
        if (claims.remove(frequency) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Every claim, for sending to a player who just joined. */
    public Map<Integer, ClaimInfo> allInfo() {
        Map<Integer, ClaimInfo> all = new HashMap<>();
        claims.forEach((frequency, claim) -> all.put(frequency, ClaimInfo.of(claim)));
        return all;
    }

    // ---- The client's copy ----

    /** What the client knows about {@code frequency}'s claim, or null if it's public. */
    public static @Nullable ClaimInfo clientInfo(int frequency) {
        return CLIENT.get(frequency);
    }

    /** Every frequency in the client's copy. */
    public static Set<Integer> clientFrequencies() {
        return Set.copyOf(CLIENT.keySet());
    }

    public static void clientReplaceAll(Map<Integer, ClaimInfo> all) {
        CLIENT.clear();
        CLIENT.putAll(all);
    }

    /** One claim changed; {@code info} null means the frequency is public again. */
    public static void clientSet(int frequency, @Nullable ClaimInfo info) {
        if (info == null) {
            CLIENT.remove(frequency);
        } else {
            CLIENT.put(frequency, info);
        }
    }

    /** This client's own player's groups. */
    public static List<GroupInfo> clientGroups() {
        return clientGroups;
    }

    public static void clientSetGroups(List<GroupInfo> groups) {
        clientGroups = List.copyOf(groups);
    }

    public static void clientClear() {
        CLIENT.clear();
        clientGroups = List.of();
    }

    // ---- Saving ----

    private static ListTag savePlayers(Map<UUID, String> players) {
        ListTag list = new ListTag();
        players.forEach((uuid, name) -> {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", uuid);
            player.putString("name", name);
            list.add(player);
        });
        return list;
    }

    private static Map<UUID, String> loadPlayers(ListTag list) {
        Map<UUID, String> players = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag player = list.getCompound(i);
            if (player.hasUUID("uuid")) {
                players.put(player.getUUID("uuid"), player.getString("name"));
            }
        }
        return players;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        claims.forEach((frequency, claim) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("frequency", frequency);
            entry.putUUID("owner", claim.owner());
            entry.putString("owner_name", claim.ownerName());
            entry.put("friends", savePlayers(claim.friends()));
            if (!claim.groups().isEmpty()) {
                ListTag names = new ListTag();
                claim.groups().forEach(name -> names.add(StringTag.valueOf(name)));
                entry.put("groups", names);
            }
            if (claim.encrypted()) {
                entry.putBoolean("encrypted", true);
            }
            if (!claim.guests().isEmpty()) {
                ListTag guestList = new ListTag();
                claim.guests().forEach((uuid, guest) -> {
                    CompoundTag guestTag = new CompoundTag();
                    guestTag.putUUID("uuid", uuid);
                    guestTag.putString("name", guest.name());
                    guestTag.putLong("until", guest.untilMillis());
                    guestList.add(guestTag);
                });
                entry.put("guests", guestList);
            }
            list.add(entry);
        });
        tag.put("claims", list);
        ListTag groupList = new ListTag();
        groups.forEach((owner, mine) -> mine.values().forEach(group -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", owner);
            entry.putString("name", group.name());
            entry.put("members", savePlayers(group.members()));
            groupList.add(entry);
        }));
        tag.put("groups", groupList);
        return tag;
    }

    /** Reads what {@link #save} wrote (claims saved before v0.27.0 have no groups, before v0.29.0 none are encrypted). Public for the GameTests. */
    public static FrequencyClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        FrequencyClaims result = new FrequencyClaims();
        ListTag groupList = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            CompoundTag entry = groupList.getCompound(i);
            String name = entry.getString("name");
            if (entry.hasUUID("owner") && !name.isEmpty()) {
                result.groups.computeIfAbsent(entry.getUUID("owner"), uuid -> new LinkedHashMap<>())
                        .put(key(name), new Group(name, loadPlayers(entry.getList("members", Tag.TAG_COMPOUND))));
            }
        }
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            List<String> groupNames = new ArrayList<>();
            ListTag names = entry.getList("groups", Tag.TAG_STRING);
            for (int j = 0; j < names.size(); j++) {
                groupNames.add(names.getString(j));
            }
            Claim claim = result.put(entry.getInt("frequency"), entry.getUUID("owner"), entry.getString("owner_name"),
                    loadPlayers(entry.getList("friends", Tag.TAG_COMPOUND)), groupNames, entry.getBoolean("encrypted"));
            ListTag guestList = entry.getList("guests", Tag.TAG_COMPOUND); // none before v0.36.0
            if (!guestList.isEmpty()) {
                Map<UUID, Guest> guests = new LinkedHashMap<>();
                for (int j = 0; j < guestList.size(); j++) {
                    CompoundTag guestTag = guestList.getCompound(j);
                    if (guestTag.hasUUID("uuid")) {
                        guests.put(guestTag.getUUID("uuid"), new Guest(guestTag.getString("name"), guestTag.getLong("until")));
                    }
                }
                result.claims.put(Frequency.clamp(entry.getInt("frequency")), claim.withGuests(guests));
            }
        }
        result.setDirty(false);
        return result;
    }

    /** Names in a friends or members list as typed, joined back up for a text box: groups first, as "#name". */
    public static String friendsText(List<String> groupNames, List<String> names) {
        Set<String> parts = new LinkedHashSet<>();
        groupNames.forEach(name -> parts.add("#" + name));
        parts.addAll(names);
        return String.join(", ", parts);
    }
}
