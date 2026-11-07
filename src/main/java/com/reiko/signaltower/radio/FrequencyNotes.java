package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.FrequencyNotePayload;
import com.reiko.signaltower.network.FrequencyNotesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short notes on frequencies ("opens the barn door" for 2026), like {@link FrequencyNames}: shared
 * by everyone in the world, saved with it (in the Overworld's data, but they count in every
 * dimension), set in the Frequency Tuner's screen (its Note button), and sent to every player
 * when they join and whenever one changes, so the directory, Jade and item tooltips can show them.
 * <p>
 * A private frequency's note can be kept to its members (v0.32.0): then only its owner, their
 * friends, the members of groups it uses and server operators are sent it (see {@link #visibleTo});
 * everyone else's client never gets it. On a public frequency the switch has no effect.
 */
public final class FrequencyNotes extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_frequency_notes";
    private static final SavedData.Factory<FrequencyNotes> FACTORY =
            new SavedData.Factory<>(FrequencyNotes::new, FrequencyNotes::load, null);

    /** The longest note, in characters. */
    public static final int MAX_LENGTH = 64;
    /** The most notes one world keeps. */
    public static final int MAX_NOTES = 4096;

    /** The client's copy, filled in by the server's packets. Empty on a dedicated server. */
    private static final Map<Integer, String> CLIENT = new ConcurrentHashMap<>();
    /** The frequencies in the client's copy whose note is kept to members. */
    private static final Set<Integer> CLIENT_MEMBERS_ONLY = ConcurrentHashMap.newKeySet();

    private final Map<Integer, String> notes = new HashMap<>();
    private final Set<Integer> membersOnly = new HashSet<>();

    public static FrequencyNotes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ---- Looking notes up ----

    /**
     * The note on {@code frequency}, or null. On the server (a {@link ServerLevel}) it's read from
     * the world; anywhere else (a client level, or no level, as in item tooltips) from the copy
     * the server sent.
     */
    public static @Nullable String noteOf(@Nullable Level level, int frequency) {
        if (level instanceof ServerLevel serverLevel) {
            return get(serverLevel.getServer()).notes.get(frequency);
        }
        return CLIENT.get(frequency);
    }

    /** A note as a tooltip line: grey, in italics and quotation marks. */
    public static Component line(String note) {
        return line(note, false);
    }

    /** {@link #line(String)}, followed by "(members only)" if it's kept to the frequency's members. */
    public static Component line(String note, boolean membersOnly) {
        var line = Component.translatable("tooltip." + SignalTower.MOD_ID + ".note", note)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        if (membersOnly) {
            line.append(Component.translatable("tooltip." + SignalTower.MOD_ID + ".note_members_only")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    /** Adds the client's note on {@code frequency} (if it has one) to an item tooltip. */
    public static void addTooltipLine(int frequency, List<Component> tooltip) {
        String note = CLIENT.get(frequency);
        if (note != null) {
            tooltip.add(line(note, CLIENT_MEMBERS_ONLY.contains(frequency)));
        }
    }

    // ---- Changing them (server) ----

    /**
     * Tidies a typed note: drops formatting codes and characters that can't be shown, trims
     * spaces, and cuts it to {@link #MAX_LENGTH}. Empty means "no note".
     */
    public static String clean(String raw) {
        String cleaned = StringUtil.filterText(raw == null ? "" : raw).strip();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH).strip() : cleaned;
    }

    /** The note stored for {@code frequency}, or null. */
    public @Nullable String get(int frequency) {
        return notes.get(frequency);
    }

    /** True if {@code frequency}'s note is kept to the frequency's members. */
    public boolean isMembersOnly(int frequency) {
        return membersOnly.contains(frequency);
    }

    // ---- Who gets to see a note (server) ----

    /**
     * True if {@code player} may see {@code frequency}'s note: it has one, and it isn't kept to
     * members, or the frequency isn't private (any more), or they're its owner, a friend, a member
     * of a group it uses, or a server operator. {@code null} (no player) only sees open notes.
     */
    public static boolean visibleTo(MinecraftServer server, int frequency, @Nullable Player player) {
        FrequencyNotes notes = get(server);
        if (!notes.notes.containsKey(frequency)) {
            return false;
        }
        if (!notes.membersOnly.contains(frequency)) {
            return true;
        }
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(frequency);
        return claim == null || player != null && (claim.allows(player.getUUID()) || player.hasPermissions(2));
    }

    /** {@code frequency}'s note if {@code player} may see it, else null. */
    public static @Nullable String noteFor(MinecraftServer server, int frequency, @Nullable Player player) {
        return visibleTo(server, frequency, player) ? get(server).notes.get(frequency) : null;
    }

    /** What {@code player}'s client should know about {@code frequency}'s note ("" = none, or not for them). */
    public static FrequencyNotePayload payloadFor(MinecraftServer server, int frequency, Player player) {
        FrequencyNotes notes = get(server);
        boolean visible = visibleTo(server, frequency, player);
        return new FrequencyNotePayload(frequency, visible ? notes.notes.get(frequency) : "",
                visible && notes.membersOnly.contains(frequency));
    }

    /** Every note {@code player} may see, for when they join. */
    public static FrequencyNotesPayload allFor(MinecraftServer server, Player player) {
        FrequencyNotes notes = get(server);
        Map<Integer, String> visible = new HashMap<>();
        List<Integer> kept = new java.util.ArrayList<>();
        notes.notes.forEach((frequency, note) -> {
            if (visibleTo(server, frequency, player)) {
                visible.put(frequency, note);
                if (notes.membersOnly.contains(frequency)) {
                    kept.add(frequency);
                }
            }
        });
        return new FrequencyNotesPayload(visible, kept);
    }

    /**
     * Tells every online player about {@code frequency}'s note as they may see it: after the note
     * changes, and after the frequency's claim changes (members added or taken off, made public).
     */
    public static void sendToAll(MinecraftServer server, int frequency) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payloadFor(server, frequency, player));
        }
    }

    /** Lets everyone see {@code frequency}'s note again (its frequency was made public). */
    public void release(int frequency) {
        if (membersOnly.remove(Frequency.clamp(frequency))) {
            setDirty();
        }
    }

    /** Every note (members-only ones included), for the server's own use. */
    public Map<Integer, String> all() {
        return Map.copyOf(notes);
    }

    /**
     * Sets the note on {@code frequency} (an empty one removes it). Returns the stored note (""
     * if removed), or null if it was refused because the world already has {@link #MAX_NOTES}.
     */
    public @Nullable String set(int frequency, String rawNote) {
        return set(frequency, rawNote, false);
    }

    /**
     * {@link #set(int, String)}, kept to the frequency's members or not. Removing the note also
     * removes the switch.
     */
    public @Nullable String set(int frequency, String rawNote, boolean keepToMembers) {
        int clamped = Frequency.clamp(frequency);
        String note = clean(rawNote);
        if (note.isEmpty()) {
            if (notes.remove(clamped) != null) {
                setDirty();
            }
            if (membersOnly.remove(clamped)) {
                setDirty();
            }
            return "";
        }
        if (!notes.containsKey(clamped) && notes.size() >= MAX_NOTES) {
            return null;
        }
        if (!note.equals(notes.put(clamped, note))) {
            setDirty();
        }
        if (keepToMembers ? membersOnly.add(clamped) : membersOnly.remove(clamped)) {
            setDirty();
        }
        return note;
    }

    // ---- The client's copy ----

    /** Every frequency with a note in the client's copy. */
    public static Set<Integer> clientFrequencies() {
        return Set.copyOf(CLIENT.keySet());
    }

    /** The server's whole list arrived (on joining). */
    public static void clientReplaceAll(Map<Integer, String> all) {
        clientReplaceAll(all, List.of());
    }

    /** The server's whole list arrived (on joining), with the frequencies whose notes are kept to members. */
    public static void clientReplaceAll(Map<Integer, String> all, java.util.Collection<Integer> keptToMembers) {
        CLIENT.clear();
        CLIENT.putAll(all);
        CLIENT_MEMBERS_ONLY.clear();
        CLIENT_MEMBERS_ONLY.addAll(keptToMembers);
    }

    /** One note changed ("" means it was removed, or isn't for this player any more). */
    public static void clientSet(int frequency, String note) {
        clientSet(frequency, note, false);
    }

    /** {@link #clientSet(int, String)}, and whether it's kept to members. */
    public static void clientSet(int frequency, String note, boolean keptToMembers) {
        if (note.isEmpty()) {
            CLIENT.remove(frequency);
            CLIENT_MEMBERS_ONLY.remove(frequency);
        } else {
            CLIENT.put(frequency, note);
            if (keptToMembers) {
                CLIENT_MEMBERS_ONLY.add(frequency);
            } else {
                CLIENT_MEMBERS_ONLY.remove(frequency);
            }
        }
    }

    /** True if the client knows {@code frequency}'s note is kept to members. */
    public static boolean clientMembersOnly(int frequency) {
        return CLIENT_MEMBERS_ONLY.contains(frequency);
    }

    /** Left the world or server: forget its notes. */
    public static void clientClear() {
        CLIENT.clear();
        CLIENT_MEMBERS_ONLY.clear();
    }

    // ---- Saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag list = new CompoundTag();
        notes.forEach((frequency, note) -> list.putString(Integer.toString(frequency), note));
        tag.put("notes", list);
        if (!membersOnly.isEmpty()) {
            tag.putIntArray("members_only", membersOnly.stream().sorted().mapToInt(Integer::intValue).toArray());
        }
        return tag;
    }

    /** Reads what {@link #save} wrote (notes saved before v0.32.0 aren't kept to members). Public for the GameTests. */
    public static FrequencyNotes load(CompoundTag tag, HolderLookup.Provider registries) {
        FrequencyNotes result = new FrequencyNotes();
        CompoundTag list = tag.getCompound("notes");
        for (String key : list.getAllKeys()) {
            try {
                String note = clean(list.getString(key));
                if (!note.isEmpty() && result.notes.size() < MAX_NOTES) {
                    result.notes.put(Frequency.clamp(Long.parseLong(key)), note);
                }
            } catch (NumberFormatException ignored) {
                // not a frequency: skip it
            }
        }
        for (int frequency : tag.getIntArray("members_only")) {
            if (result.notes.containsKey(frequency)) {
                result.membersOnly.add(frequency);
            }
        }
        return result;
    }
}
