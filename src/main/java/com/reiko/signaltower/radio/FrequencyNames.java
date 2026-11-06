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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Names for frequencies ("Front Gate" for 42), shared by everyone in the world and saved with it
 * (in the Overworld's data, but they count in every dimension). Set in the Frequency Tuner's
 * screen. The server sends every player the whole list when they join and each change as it
 * happens, so tooltips and screens on the client can show names too.
 * <p>
 * Messages use {@link #label}: the plain number for a frequency without a name (so nothing
 * changes until one is named), or "42 (Front Gate)".
 */
public final class FrequencyNames extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_frequency_names";
    private static final SavedData.Factory<FrequencyNames> FACTORY =
            new SavedData.Factory<>(FrequencyNames::new, FrequencyNames::load, null);

    /** The longest name, in characters. */
    public static final int MAX_LENGTH = 24;
    /** The most names one world keeps. */
    public static final int MAX_NAMES = 4096;

    /** The client's copy, filled in by the server's packets. Empty on a dedicated server. */
    private static final Map<Integer, String> CLIENT = new ConcurrentHashMap<>();

    private final Map<Integer, String> names = new HashMap<>();

    public static FrequencyNames get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ---- Looking names up ----

    /**
     * The name of {@code frequency}, or null. On the server (a {@link ServerLevel}) it's read from
     * the world; anywhere else (a client level, or no level, as in item tooltips) from the copy
     * the server sent.
     */
    public static @Nullable String nameOf(@Nullable Level level, int frequency) {
        if (level instanceof ServerLevel serverLevel) {
            return get(serverLevel.getServer()).names.get(frequency);
        }
        return CLIENT.get(frequency);
    }

    /**
     * For message arguments: the number itself if {@code frequency} has no name (exactly as
     * before names existed), otherwise "42 (Front Gate)" with the name in aqua.
     */
    public static Object label(@Nullable Level level, int frequency) {
        String name = nameOf(level, frequency);
        return name == null ? frequency : named(frequency, name);
    }

    /** "42 (Front Gate)". */
    public static Component named(int frequency, String name) {
        return Component.translatable("frequency." + SignalTower.MOD_ID + ".named", frequency,
                Component.literal(name).withStyle(ChatFormatting.AQUA));
    }

    // ---- Changing them (server) ----

    /**
     * Tidies a typed name: drops formatting codes and characters that can't be shown, trims
     * spaces, and cuts it to {@link #MAX_LENGTH}. Empty means "no name".
     */
    public static String clean(String raw) {
        String cleaned = StringUtil.filterText(raw == null ? "" : raw).strip();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH).strip() : cleaned;
    }

    /** The name stored for {@code frequency}, or null. */
    public @Nullable String get(int frequency) {
        return names.get(frequency);
    }

    /** Every name, for sending to a player who just joined. */
    public Map<Integer, String> all() {
        return Map.copyOf(names);
    }

    /**
     * Names {@code frequency} (an empty name removes it). Returns the stored name ("" if
     * removed), or null if it was refused because the world already has {@link #MAX_NAMES}.
     */
    public @Nullable String set(int frequency, String rawName) {
        int clamped = Frequency.clamp(frequency);
        String name = clean(rawName);
        if (name.isEmpty()) {
            if (names.remove(clamped) != null) {
                setDirty();
            }
            return "";
        }
        if (!names.containsKey(clamped) && names.size() >= MAX_NAMES) {
            return null;
        }
        if (!name.equals(names.put(clamped, name))) {
            setDirty();
        }
        return name;
    }

    // ---- The client's copy ----

    /** The server's whole list arrived (on joining). */
    public static void clientReplaceAll(Map<Integer, String> all) {
        CLIENT.clear();
        CLIENT.putAll(all);
    }

    /** One name changed ("" means it was removed). */
    public static void clientSet(int frequency, String name) {
        if (name.isEmpty()) {
            CLIENT.remove(frequency);
        } else {
            CLIENT.put(frequency, name);
        }
    }

    /** Left the world or server: forget its names. */
    public static void clientClear() {
        CLIENT.clear();
    }

    // ---- Saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag list = new CompoundTag();
        names.forEach((frequency, name) -> list.putString(Integer.toString(frequency), name));
        tag.put("names", list);
        return tag;
    }

    /** Reads what {@link #save} wrote. Public for the GameTests. */
    public static FrequencyNames load(CompoundTag tag, HolderLookup.Provider registries) {
        FrequencyNames result = new FrequencyNames();
        CompoundTag list = tag.getCompound("names");
        for (String key : list.getAllKeys()) {
            try {
                String name = clean(list.getString(key));
                if (!name.isEmpty() && result.names.size() < MAX_NAMES) {
                    result.names.put(Frequency.clamp(Long.parseLong(key)), name);
                }
            } catch (NumberFormatException ignored) {
                // not a frequency: skip it
            }
        }
        return result;
    }
}
