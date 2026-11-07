package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short notes on frequencies ("opens the barn door" for 2026), like {@link FrequencyNames}: shared
 * by everyone in the world, saved with it (in the Overworld's data, but they count in every
 * dimension), set in the Frequency Tuner's screen (its Note button), and sent to every player
 * when they join and whenever one changes, so the directory, Jade and item tooltips can show them.
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

    private final Map<Integer, String> notes = new HashMap<>();

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
        return Component.translatable("tooltip." + SignalTower.MOD_ID + ".note", note)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    /** Adds the client's note on {@code frequency} (if it has one) to an item tooltip. */
    public static void addTooltipLine(int frequency, List<Component> tooltip) {
        String note = CLIENT.get(frequency);
        if (note != null) {
            tooltip.add(line(note));
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

    /** Every note, for sending to a player who just joined. */
    public Map<Integer, String> all() {
        return Map.copyOf(notes);
    }

    /**
     * Sets the note on {@code frequency} (an empty one removes it). Returns the stored note (""
     * if removed), or null if it was refused because the world already has {@link #MAX_NOTES}.
     */
    public @Nullable String set(int frequency, String rawNote) {
        int clamped = Frequency.clamp(frequency);
        String note = clean(rawNote);
        if (note.isEmpty()) {
            if (notes.remove(clamped) != null) {
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
        return note;
    }

    // ---- The client's copy ----

    /** Every frequency with a note in the client's copy. */
    public static Set<Integer> clientFrequencies() {
        return Set.copyOf(CLIENT.keySet());
    }

    /** The server's whole list arrived (on joining). */
    public static void clientReplaceAll(Map<Integer, String> all) {
        CLIENT.clear();
        CLIENT.putAll(all);
    }

    /** One note changed ("" means it was removed). */
    public static void clientSet(int frequency, String note) {
        if (note.isEmpty()) {
            CLIENT.remove(frequency);
        } else {
            CLIENT.put(frequency, note);
        }
    }

    /** Left the world or server: forget its notes. */
    public static void clientClear() {
        CLIENT.clear();
    }

    // ---- Saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag list = new CompoundTag();
        notes.forEach((frequency, note) -> list.putString(Integer.toString(frequency), note));
        tag.put("notes", list);
        return tag;
    }

    /** Reads what {@link #save} wrote. Public for the GameTests. */
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
        return result;
    }
}
