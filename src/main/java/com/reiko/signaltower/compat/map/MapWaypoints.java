package com.reiko.signaltower.compat.map;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.ScannerTarget;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.network.MapWaypointsPayload;
import com.reiko.signaltower.network.MapWaypointsPayload.Entry;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Map waypoints (v0.61.0), the client's half, with no map mod's classes: what the server sent
 * ({@link com.reiko.signaltower.radio.MapTowers}), plus the tower the Signal Scanner in the player's
 * inventory tracks, merged into one list with a {@link #version()} that goes up when it changes.
 * JourneyMap's plugin ({@code JourneyMapCompat}) and Xaero's ({@code XaeroCompat}) show it.
 */
public final class MapWaypoints {
    /** All the mod's waypoints go in this group (JourneyMap) or set (Xaero). */
    public static final String GROUP = "Radio towers";

    private static List<Entry> fromServer = List.of();
    private static @Nullable Entry tracked;
    private static List<Entry> current = List.of();
    private static int version;
    private static int timer;

    private MapWaypoints() {
    }

    /** True if a map mod this works with is loaded (so the client bothers looking at scanners). */
    public static boolean anyMap() {
        return ModList.get().isLoaded("journeymap") || ModList.get().isLoaded("xaerominimap");
    }

    public static void setFromServer(List<Entry> entries) {
        fromServer = List.copyOf(entries);
        rebuild();
    }

    /** Once a second: the scanner's target, if it changed. Then Xaero's map gets the list if it needs it. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++timer < 20) {
            return;
        }
        timer = 0;
        LocalPlayer player = Minecraft.getInstance().player;
        Entry now = player == null ? null : trackedBy(player);
        if (!Objects.equals(now, tracked)) {
            tracked = now;
            rebuild();
        }
        if (ModList.get().isLoaded("xaerominimap")) {
            XaeroCompat.update();
        }
    }

    /** The first Signal Scanner with a target in the player's hands or inventory: its target. */
    private static @Nullable Entry trackedBy(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getMainHandItem());
        stacks.add(player.getOffhandItem());
        stacks.addAll(player.getInventory().items);
        for (ItemStack stack : stacks) {
            if (stack.is(ModRegistry.SIGNAL_SCANNER.get())) {
                ScannerTarget target = SignalScannerItem.getTarget(stack);
                if (target != null) {
                    return new Entry(target.dimension().location(), target.pos(), MapWaypointsPayload.TRACKED, target.frequency(), 0);
                }
            }
        }
        return null;
    }

    private static void rebuild() {
        List<Entry> next = new ArrayList<>(fromServer);
        if (tracked != null) {
            next.add(tracked);
        }
        if (!next.equals(current)) {
            current = List.copyOf(next);
            version++;
        }
    }

    public static List<Entry> current() {
        return current;
    }

    public static int version() {
        return version;
    }

    /** Logged out: nothing to show. */
    public static void clear() {
        fromServer = List.of();
        tracked = null;
        current = List.of();
        version++;
    }

    /** The waypoint's name: "Radio Transmitter 7000", "Old Radio Tower 123456", "Tracked: 7000". */
    public static String name(Entry entry) {
        String key = switch (entry.kind()) {
            case MapWaypointsPayload.TRANSMITTER -> "transmitter";
            case MapWaypointsPayload.RELAY -> "relay";
            case MapWaypointsPayload.JAMMER -> "jammer";
            case MapWaypointsPayload.OLD_TOWER -> "old_tower";
            case MapWaypointsPayload.VILLAGE_STATION -> "village_station";
            default -> "tracked";
        };
        String named = com.reiko.signaltower.radio.FrequencyNames.nameOf(null, entry.frequency());
        String frequency = named == null || named.isEmpty() ? Integer.toString(entry.frequency())
                : entry.frequency() + " (" + named + ")";
        return Component.translatable("map." + SignalTower.MOD_ID + "." + key, frequency).getString();
    }

    /** Short letters for maps that show initials (Xaero). */
    public static String initials(Entry entry) {
        return switch (entry.kind()) {
            case MapWaypointsPayload.TRANSMITTER -> "TX";
            case MapWaypointsPayload.RELAY -> "RP";
            case MapWaypointsPayload.JAMMER -> "JM";
            case MapWaypointsPayload.OLD_TOWER -> "OT";
            case MapWaypointsPayload.VILLAGE_STATION -> "VS";
            default -> "SC";
        };
    }

    /** Its color (RGB): red transmitters, amber relays, magenta jammers, grey old towers, green stations, aqua tracked. */
    public static int color(Entry entry) {
        return switch (entry.kind()) {
            case MapWaypointsPayload.TRANSMITTER -> 0xEC2C1E;
            case MapWaypointsPayload.RELAY -> 0xFFB228;
            case MapWaypointsPayload.JAMMER -> 0xFF50C8;
            case MapWaypointsPayload.OLD_TOWER -> 0xA0A0A0;
            case MapWaypointsPayload.VILLAGE_STATION -> 0x55FF55;
            default -> 0x2CDEE8;
        };
    }
}
