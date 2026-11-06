package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
public final class FrequencyClaims extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_frequency_claims";
    private static final SavedData.Factory<FrequencyClaims> FACTORY =
            new SavedData.Factory<>(FrequencyClaims::new, FrequencyClaims::load, null);

    /** The most friends one private frequency can have. */
    public static final int MAX_FRIENDS = 16;
    /** The most private frequencies one world can have (so the list sent to joining players stays small). */
    public static final int MAX_CLAIMS = 4096;

    /** One private frequency: its owner and their friends (by UUID, with the names they had when added). */
    public record Claim(UUID owner, String ownerName, Map<UUID, String> friends) {
        public Claim {
            friends = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(friends)); // keeps the order they were added in
        }

        /** True for the owner and their friends. */
        public boolean allows(@Nullable UUID player) {
            return player != null && (player.equals(owner) || friends.containsKey(player));
        }

        /** The friends' names, in the order they were added. */
        public List<String> friendNames() {
            return List.copyOf(friends.values());
        }
    }

    /** What the client is told about a claim. */
    public record ClaimInfo(UUID owner, String ownerName, List<String> friends) {
        public static final StreamCodec<ByteBuf, ClaimInfo> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ClaimInfo::owner,
                ByteBufCodecs.stringUtf8(64), ClaimInfo::ownerName,
                ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_FRIENDS)), ClaimInfo::friends,
                ClaimInfo::new);

        public static ClaimInfo of(Claim claim) {
            return new ClaimInfo(claim.owner(), claim.ownerName(), claim.friendNames());
        }
    }

    /** The client's copy, filled in by the server's packets. Empty on a dedicated server. */
    private static final Map<Integer, ClaimInfo> CLIENT = new ConcurrentHashMap<>();

    private final Map<Integer, Claim> claims = new HashMap<>();

    public static FrequencyClaims get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ---- Checks (server) ----

    /** The claim on {@code frequency}, or null if it's public. */
    public @Nullable Claim get(int frequency) {
        return claims.get(frequency);
    }

    /** True if the frequency is public, or {@code player} is its owner or a friend. */
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

    // ---- Changes (server) ----

    /** Makes {@code frequency} private to {@code owner}, keeping {@code friends}. Replaces any claim on it. */
    public Claim put(int frequency, UUID owner, String ownerName, Map<UUID, String> friends) {
        Map<UUID, String> limited = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> friend : friends.entrySet()) {
            if (limited.size() < MAX_FRIENDS && !friend.getKey().equals(owner)) {
                limited.put(friend.getKey(), friend.getValue());
            }
        }
        Claim claim = new Claim(owner, ownerName, limited);
        claims.put(Frequency.clamp(frequency), claim);
        setDirty();
        return claim;
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
    public static java.util.Set<Integer> clientFrequencies() {
        return java.util.Set.copyOf(CLIENT.keySet());
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

    public static void clientClear() {
        CLIENT.clear();
    }

    // ---- Saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        claims.forEach((frequency, claim) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("frequency", frequency);
            entry.putUUID("owner", claim.owner());
            entry.putString("owner_name", claim.ownerName());
            ListTag friends = new ListTag();
            claim.friends().forEach((uuid, name) -> {
                CompoundTag friend = new CompoundTag();
                friend.putUUID("uuid", uuid);
                friend.putString("name", name);
                friends.add(friend);
            });
            entry.put("friends", friends);
            list.add(entry);
        });
        tag.put("claims", list);
        return tag;
    }

    /** Reads what {@link #save} wrote. Public for the GameTests. */
    public static FrequencyClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        FrequencyClaims result = new FrequencyClaims();
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Map<UUID, String> friends = new LinkedHashMap<>();
            ListTag friendList = entry.getList("friends", Tag.TAG_COMPOUND);
            for (int j = 0; j < friendList.size(); j++) {
                CompoundTag friend = friendList.getCompound(j);
                if (friend.hasUUID("uuid")) {
                    friends.put(friend.getUUID("uuid"), friend.getString("name"));
                }
            }
            result.put(entry.getInt("frequency"), entry.getUUID("owner"), entry.getString("owner_name"), friends);
        }
        result.setDirty(false);
        return result;
    }
}
