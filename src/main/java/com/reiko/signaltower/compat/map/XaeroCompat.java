package com.reiko.signaltower.compat.map;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.MapWaypointsPayload;
import com.reiko.signaltower.network.MapWaypointsPayload.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;

/**
 * Xaero's Minimap (v0.61.0): {@link MapWaypoints} for the dimension the player is in, as its
 * "third-party" waypoints (not saved; Xaero's World Map shows them too). Only called when Xaero's
 * Minimap is loaded; checked once a second, and redone when the list, the dimension or Xaero's
 * world changes.
 */
public final class XaeroCompat {
    private static final ResourceLocation ORIGIN = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "radio_towers");

    private static @Nullable Object shownIn;
    private static int shownVersion = -1;
    private static @Nullable ResourceKey<Level> shownDimension;
    private static boolean failed;

    private XaeroCompat() {
    }

    static void update() {
        if (failed) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            shownIn = null;
            return;
        }
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
            if (world == null) {
                return;
            }
            ThirdPartyWaypoints ours = world.getContainer().getThirdPartyWaypointManager().get(ORIGIN);
            ResourceKey<Level> dimension = minecraft.level.dimension();
            if (ours == null || ours == shownIn && shownVersion == MapWaypoints.version() && dimension.equals(shownDimension)) {
                return;
            }
            ours.clear();
            int n = 0;
            for (Entry entry : MapWaypoints.current()) {
                if (!entry.dimension().equals(dimension.location())) {
                    continue;
                }
                Waypoint waypoint = new Waypoint(entry.pos().getX(), entry.pos().getY() + 1, entry.pos().getZ(),
                        MapWaypoints.name(entry), MapWaypoints.initials(entry), color(entry));
                ours.add("tower_" + n++, waypoint);
            }
            shownIn = ours;
            shownVersion = MapWaypoints.version();
            shownDimension = dimension;
        } catch (LinkageError | RuntimeException e) {
            failed = true; // a Xaero's Minimap this wasn't written for: leave it alone
            SignalTower.LOGGER.warn("Xaero's Minimap: couldn't show the radio towers", e);
        }
    }

    private static WaypointColor color(Entry entry) {
        return switch (entry.kind()) {
            case MapWaypointsPayload.TRANSMITTER -> WaypointColor.RED;
            case MapWaypointsPayload.RELAY -> WaypointColor.GOLD;
            case MapWaypointsPayload.JAMMER -> WaypointColor.PINK;
            case MapWaypointsPayload.OLD_TOWER -> WaypointColor.GRAY;
            case MapWaypointsPayload.VILLAGE_STATION -> WaypointColor.GREEN;
            default -> WaypointColor.AQUA;
        };
    }
}
