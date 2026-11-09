package com.reiko.signaltower.compat.map;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.MapWaypointsPayload.Entry;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * JourneyMap (v0.61.0): found and loaded by JourneyMap itself (its annotation), so this class is
 * only touched when JourneyMap is there. Puts {@link MapWaypoints} in a "Radio towers" group (not
 * saved: they come from the server each time), and draws each of the player's own towers' reach as
 * a faint circle in its color on the full map and the minimap.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapCompat implements IClientPlugin {
    /** Points round a reach circle. */
    private static final int CIRCLE_POINTS = 64;

    private IClientAPI api;
    private boolean mapping;
    private int shownVersion = -1;

    @Override
    public String getModId() {
        return SignalTower.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        ClientEventRegistry.MAPPING_EVENT.subscribe(SignalTower.MOD_ID, this::onMapping);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private void onMapping(MappingEvent event) {
        mapping = event.getStage() == MappingEvent.Stage.MAPPING_STARTED;
        shownVersion = -1; // JourneyMap starts a world afresh: add them all again
        if (!mapping) {
            clear();
        }
    }

    private void tick() {
        if (api == null || !mapping || shownVersion == MapWaypoints.version()) {
            return;
        }
        shownVersion = MapWaypoints.version();
        show(MapWaypoints.current());
    }

    private void clear() {
        try {
            api.removeAllWaypoints(SignalTower.MOD_ID);
            api.removeAll(SignalTower.MOD_ID);
        } catch (RuntimeException e) {
            SignalTower.LOGGER.debug("JourneyMap: couldn't clear the radio towers", e);
        }
    }

    private void show(List<Entry> entries) {
        clear();
        try {
            WaypointGroup group = api.getWaypointGroupByName(SignalTower.MOD_ID, MapWaypoints.GROUP);
            if (group == null) {
                group = WaypointFactory.createWaypointGroup(SignalTower.MOD_ID, MapWaypoints.GROUP);
                group.setPersistent(false);
                api.addWaypointGroup(group);
            }
            for (Entry entry : entries) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.dimension());
                Waypoint waypoint = WaypointFactory.createClientWaypoint(SignalTower.MOD_ID, entry.pos(), MapWaypoints.name(entry), dimension, false);
                waypoint.setColor(MapWaypoints.color(entry));
                waypoint.setIconColor(MapWaypoints.color(entry));
                group.addWaypoint(waypoint);
                api.addWaypoint(SignalTower.MOD_ID, waypoint);
                if (entry.own() && entry.reach() > 0) {
                    api.show(circle(entry, dimension));
                }
            }
        } catch (Exception e) {
            SignalTower.LOGGER.warn("JourneyMap: couldn't show the radio towers", e);
        }
    }

    /** A tower's reach, as a polygon close to a circle. */
    private static PolygonOverlay circle(Entry entry, ResourceKey<Level> dimension) {
        List<BlockPos> points = new ArrayList<>(CIRCLE_POINTS);
        BlockPos center = entry.pos();
        for (int i = 0; i < CIRCLE_POINTS; i++) {
            double angle = Math.PI * 2 * i / CIRCLE_POINTS;
            points.add(new BlockPos((int) Math.round(center.getX() + 0.5 + Math.cos(angle) * entry.reach()), center.getY(),
                    (int) Math.round(center.getZ() + 0.5 + Math.sin(angle) * entry.reach())));
        }
        int color = MapWaypoints.color(entry);
        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(color).setStrokeOpacity(0.8F).setStrokeWidth(2.0F)
                .setFillColor(color).setFillOpacity(0.08F);
        PolygonOverlay overlay = new PolygonOverlay(SignalTower.MOD_ID, dimension, shape, new MapPolygon(points));
        overlay.setOverlayGroupName(MapWaypoints.GROUP)
                .setTitle(MapWaypoints.name(entry))
                .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap, Context.UI.Webmap);
        return overlay;
    }
}
