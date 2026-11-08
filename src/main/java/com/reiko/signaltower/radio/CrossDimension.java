package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-dimension links (v0.50.0). A Radio Transmitter with an Ender Antenna also sends into one
 * other dimension, from "the same spot" there: its x and z scaled the way portals scale them (so
 * the Nether at 1/8 of the Overworld's, and back; the End and other dimensions 1:1), its height
 * kept. It's heard there as if a transmitter stood at that spot, with the tower's own reach (in
 * the weather where the tower stands) and strength; hills don't block it. To start with it sends
 * into the Nether from the Overworld and into the Overworld from anywhere else; another Ender
 * Antenna moves it on to the next dimension.
 */
public final class CrossDimension {
    private CrossDimension() {}

    /** Where a tower at {@code pos} in {@code from} arrives in {@code to}. */
    public static BlockPos arrival(Level from, Level to, BlockPos pos) {
        return arrival(from.dimensionType(), to, pos);
    }

    public static BlockPos arrival(DimensionType from, Level to, BlockPos pos) {
        double scale = DimensionType.getTeleportationScale(from, to.dimensionType());
        int y = Mth.clamp(pos.getY(), to.getMinBuildHeight(), to.getMaxBuildHeight() - 1);
        return new BlockPos(Mth.floor(pos.getX() * scale), y, Mth.floor(pos.getZ() * scale));
    }

    /** The dimension a newly fitted Ender Antenna in {@code level} sends into: the Nether from the Overworld, else the Overworld. */
    public static @Nullable ResourceKey<Level> defaultTarget(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ResourceKey<Level> wanted = level.dimension() == Level.OVERWORLD ? Level.NETHER : Level.OVERWORLD;
        if (server.getLevel(wanted) != null) {
            return wanted;
        }
        List<ResourceKey<Level>> others = others(level);
        return others.isEmpty() ? null : others.get(0);
    }

    /** The dimension after {@code current} (in the server's order, skipping {@code level}'s own), for turning an Ender Antenna on. */
    public static @Nullable ResourceKey<Level> next(ServerLevel level, @Nullable ResourceKey<Level> current) {
        List<ResourceKey<Level>> others = others(level);
        if (others.isEmpty()) {
            return null;
        }
        int index = others.indexOf(current);
        return others.get((index + 1) % others.size());
    }

    /** {@code target} if it's another dimension the server has, else the default for {@code level}. */
    public static @Nullable ResourceKey<Level> valid(ServerLevel level, @Nullable ResourceKey<Level> target) {
        if (target != null && target != level.dimension() && level.getServer().getLevel(target) != null) {
            return target;
        }
        return defaultTarget(level);
    }

    private static List<ResourceKey<Level>> others(ServerLevel level) {
        List<ResourceKey<Level>> result = new ArrayList<>();
        for (ResourceKey<Level> key : level.getServer().levelKeys()) {
            if (key != level.dimension()) {
                result.add(key);
            }
        }
        return result;
    }

    /** "the Nether", "the Overworld", "the End", or a modded dimension's id. */
    public static Component name(ResourceKey<Level> key) {
        String id = key.location().toString();
        return switch (id) {
            case "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end" ->
                    Component.translatable("dimension." + SignalTower.MOD_ID + "." + key.location().getPath());
            default -> Component.literal(id);
        };
    }

    /** "62, -60, 11" */
    public static String coords(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
