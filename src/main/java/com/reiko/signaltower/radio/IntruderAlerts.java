package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.PagerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intruder alerts (v0.36.0): when someone who isn't on a private frequency tries to use it, its
 * owner is told in chat: a refused remote press, a sender they tried to tune onto it (refused), a
 * block they placed on it that stays silent, or one they set to it that can't hear it (encrypted).
 * Like the audit, an alert never says where the intruder or their blocks are. Since v0.37.0 a Code
 * Keypad's owner is also told when someone types three wrong codes in a row on it (with where the
 * keypad is, since it's theirs). One alert per intruder, frequency and kind a minute.
 * <p>
 * An owner who's offline gets them at their next login (the last {@link #MAX_STORED}, with the
 * in-game day and time, and how many there were). Saved in the Overworld's data.
 */
public final class IntruderAlerts extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_intruder_alerts";
    private static final SavedData.Factory<IntruderAlerts> FACTORY =
            new SavedData.Factory<>(IntruderAlerts::new, IntruderAlerts::load, null);
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    /** The most alerts kept for an owner who's away (older ones are only counted). */
    public static final int MAX_STORED = 16;
    /** How many of those are shown at login. */
    public static final int SHOWN_AT_LOGIN = 5;
    /** The same intruder, frequency and kind only alerts once in this many ticks (a minute). */
    public static final int QUIET_TICKS = 1200;

    /** What the intruder tried. Saved by name. KEYPAD (v0.37.0): three wrong codes in a row on the owner's Code Keypad. */
    public enum Kind {
        REMOTE, TUNE, SILENT, DEAF, KEYPAD, TELEGRAPH
    }

    /**
     * One alert: what, on which frequency, by whom, with which block (its description id, or ""),
     * and when (day time). {@code at} is only set for a keypad alert: where the owner's own keypad is.
     */
    public record Alert(Kind kind, int frequency, String intruder, String block, long dayTime, @Nullable BlockPos at) {
        public Alert(Kind kind, int frequency, String intruder, String block, long dayTime) {
            this(kind, frequency, intruder, block, dayTime, null);
        }
    }

    private final Map<UUID, ArrayDeque<Alert>> missed = new HashMap<>();
    private final Map<UUID, Integer> missedCount = new HashMap<>();
    /** When each intruder/frequency/kind last alerted (server ticks; not saved). */
    private final Map<String, Integer> lastAlert = new HashMap<>();

    public static IntruderAlerts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * {@code intruder} tried {@code kind} on {@code frequency} (with {@code block}, if a block).
     * Nothing happens if it's public or they're on it; otherwise its owner is told now, or at their
     * next login.
     */
    public static void report(@Nullable MinecraftServer server, int frequency, Player intruder, Kind kind, @Nullable Block block) {
        if (server == null) {
            return;
        }
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(Frequency.clamp(frequency));
        if (claim == null || claim.allows(intruder.getUUID())) {
            return;
        }
        send(server, claim.owner(), new Alert(kind, Frequency.clamp(frequency), intruder.getGameProfile().getName(),
                block == null ? "" : block.getDescriptionId(), server.overworld().getDayTime()), intruder);
    }

    /**
     * {@code intruder} typed three wrong codes in a row on {@code owner}'s Code Keypad at {@code at}
     * (v0.37.0), on any frequency, private or not. The keypad is the owner's own, so the alert says where it is.
     */
    public static void reportKeypad(@Nullable MinecraftServer server, UUID owner, int frequency, Player intruder, Block block, BlockPos at) {
        if (server == null || owner.equals(intruder.getUUID())) {
            return;
        }
        send(server, owner, new Alert(Kind.KEYPAD, Frequency.clamp(frequency), intruder.getGameProfile().getName(),
                block.getDescriptionId(), server.overworld().getDayTime(), at.immutable()), intruder);
    }

    /** Tells {@code owner} now, or keeps it for their next login; once a minute per intruder, frequency and kind. */
    private static void send(MinecraftServer server, UUID ownerId, Alert alert, Player intruder) {
        IntruderAlerts alerts = get(server);
        String key = ownerId + "|" + alert.frequency() + "|" + intruder.getUUID() + "|" + alert.kind();
        Integer last = alerts.lastAlert.get(key);
        int now = server.getTickCount();
        if (last != null && now - last < QUIET_TICKS) {
            return;
        }
        alerts.lastAlert.put(key, now);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.displayClientMessage(line(server.overworld(), alert, false), false);
        } else {
            alerts.store(ownerId, alert);
        }
    }

    private void store(UUID owner, Alert alert) {
        ArrayDeque<Alert> list = missed.computeIfAbsent(owner, uuid -> new ArrayDeque<>());
        list.addLast(alert);
        while (list.size() > MAX_STORED) {
            list.removeFirst();
        }
        missedCount.merge(owner, 1, Integer::sum);
        setDirty();
    }

    /** The chat line for {@code alert}: "Radio alert: Alex pressed a remote on 2026 (Front Gate), but it's private: refused". */
    public static Component line(@Nullable Level level, Alert alert, boolean withTime) {
        MutableComponent line = Component.empty();
        if (withTime) {
            Object[] when = PagerItem.dayAndTime(alert.dayTime());
            line.append(Component.translatable("message." + SignalTower.MOD_ID + ".pager_log", when[0], when[1]).withStyle(ChatFormatting.GRAY));
        }
        line.append(Component.translatable(KEY + "intruder_prefix").withStyle(ChatFormatting.GOLD));
        Component block = alert.block().isEmpty() ? Component.empty() : Component.translatable(alert.block());
        String where = alert.at() == null ? "" : alert.at().getX() + ", " + alert.at().getY() + ", " + alert.at().getZ();
        line.append(Component.translatable(KEY + "intruder_" + alert.kind().name().toLowerCase(java.util.Locale.ROOT),
                alert.intruder(), block, FrequencyNames.label(level, alert.frequency()), where).withStyle(ChatFormatting.RED));
        return line;
    }

    /** At login: the alerts missed while away, if any (the last few, and how many in all). Then they're forgotten. */
    public static List<Component> missedLines(MinecraftServer server, UUID owner) {
        IntruderAlerts alerts = get(server);
        ArrayDeque<Alert> list = alerts.missed.remove(owner);
        Integer count = alerts.missedCount.remove(owner);
        List<Component> lines = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return lines;
        }
        alerts.setDirty();
        int total = count == null ? list.size() : count;
        lines.add(Component.translatable(KEY + (total == 1 ? "intruder_missed_one" : "intruder_missed"), total).withStyle(ChatFormatting.GOLD));
        List<Alert> recent = new ArrayList<>(list);
        int from = Math.max(0, recent.size() - SHOWN_AT_LOGIN);
        for (Alert alert : recent.subList(from, recent.size())) {
            lines.add(line(server.overworld(), alert, true));
        }
        if (total > recent.size() - from) {
            lines.add(Component.translatable(KEY + "intruder_missed_more", total - (recent.size() - from)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** The alerts kept for {@code owner} (for the GameTests). */
    public List<Alert> missedFor(UUID owner) {
        ArrayDeque<Alert> list = missed.get(owner);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Forgets when everyone last alerted (for the GameTests). */
    public void resetQuiet() {
        lastAlert.clear();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        missed.forEach((owner, list) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", owner);
            entry.putInt("count", missedCount.getOrDefault(owner, list.size()));
            ListTag alertList = new ListTag();
            for (Alert alert : list) {
                CompoundTag alertTag = new CompoundTag();
                alertTag.putString("kind", alert.kind().name().toLowerCase(java.util.Locale.ROOT));
                alertTag.putInt("frequency", alert.frequency());
                alertTag.putString("intruder", alert.intruder());
                alertTag.putString("block", alert.block());
                alertTag.putLong("day_time", alert.dayTime());
                if (alert.at() != null) {
                    alertTag.putLong("at", alert.at().asLong());
                }
                alertList.add(alertTag);
            }
            entry.put("alerts", alertList);
            owners.add(entry);
        });
        tag.put("missed", owners);
        return tag;
    }

    /** Reads what {@link #save} wrote. Public for the GameTests. */
    public static IntruderAlerts load(CompoundTag tag, HolderLookup.Provider registries) {
        IntruderAlerts result = new IntruderAlerts();
        ListTag owners = tag.getList("missed", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag entry = owners.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            UUID owner = entry.getUUID("owner");
            ArrayDeque<Alert> list = new ArrayDeque<>();
            ListTag alertList = entry.getList("alerts", Tag.TAG_COMPOUND);
            for (int j = 0; j < alertList.size(); j++) {
                CompoundTag alertTag = alertList.getCompound(j);
                Kind kind;
                try {
                    kind = Kind.valueOf(alertTag.getString("kind").toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                    continue;
                }
                list.addLast(new Alert(kind, alertTag.getInt("frequency"), alertTag.getString("intruder"),
                        alertTag.getString("block"), alertTag.getLong("day_time"),
                        alertTag.contains("at") ? BlockPos.of(alertTag.getLong("at")) : null));
            }
            if (!list.isEmpty()) {
                result.missed.put(owner, list);
                result.missedCount.put(owner, Math.max(list.size(), entry.getInt("count")));
            }
        }
        return result;
    }
}
