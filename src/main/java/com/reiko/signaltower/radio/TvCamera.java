package com.reiko.signaltower.radio;

import com.reiko.signaltower.block.TvCameraBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * TV cameras (v0.66.0): their settings and where they look. A TV Camera stands on a floor (a
 * tripod), hangs on a wall (a bracket) or from a ceiling, and looks the way it faces, tilted by its
 * Tilt setting (Auto: level on a floor, 15 degrees down on a wall, 30 on a ceiling), through a wide,
 * normal or zoom lens; with Pan it sweeps {@link #PAN_DEGREES} each way, once every
 * {@link #PAN_TICKS}, by game time (so every client sees it point the same way). Night vision makes
 * its picture green and bright in the dark. Its picture reaches monitors like a walkie-talkie's
 * voice: by its height, carried by towers and repeaters, snowy past {@link #CLEAR_FRACTION} of the
 * way to the edge. The picture is drawn on each player's own client ({@code client.CameraFeeds}).
 */
public final class TvCamera {
    /** Tilt settings in degrees (down is positive); index {@link #TILT_AUTO} picks one by how it's mounted. */
    public static final int[] TILTS = {-15, 0, 15, 30, 45, 60};
    public static final int TILT_AUTO = 7;
    /** Vertical fields of view: wide, normal, zoom. */
    public static final float[] LENSES = {95.0F, 70.0F, 35.0F};
    public static final float PAN_DEGREES = 40.0F;
    public static final int PAN_TICKS = 400;
    /** The picture is clear this far (of the way to the edge of its reach) and snowier out to the edge. */
    public static final double CLEAR_FRACTION = 0.7;
    /** How far a camera sees (blocks). */
    public static final double VIEW_DISTANCE = 64.0;

    // ---- Settings (one int, kept on the item) ----
    public static final int TILT_MASK = 7;
    public static final int LENS_SHIFT = 3;
    public static final int LENS_MASK = 3 << LENS_SHIFT;
    public static final int PAN = 1 << 5;
    public static final int NIGHT_VISION = 1 << 6;
    public static final int DEFAULT = TILT_AUTO | (1 << LENS_SHIFT);
    public static final int MASK = 127;

    private TvCamera() {
    }

    /** {@code settings} with a tilt and a lens that exist. */
    public static int settings(int settings) {
        int tilt = settings & TILT_MASK;
        if (tilt != TILT_AUTO && tilt >= TILTS.length) {
            tilt = TILT_AUTO;
        }
        int lens = Math.min(LENSES.length - 1, (settings & LENS_MASK) >> LENS_SHIFT);
        return (settings & (PAN | NIGHT_VISION)) | tilt | lens << LENS_SHIFT;
    }

    public static int lens(int settings) {
        return Math.min(LENSES.length - 1, (settings & LENS_MASK) >> LENS_SHIFT);
    }

    /** The tilt in degrees (down is positive) for a camera mounted {@code face} with {@code settings}. */
    public static int tiltDegrees(AttachFace face, int settings) {
        int tilt = settings & TILT_MASK;
        if (tilt < TILTS.length) {
            return TILTS[tilt];
        }
        return switch (face) {
            case FLOOR -> 0;
            case WALL -> 15;
            case CEILING -> 30;
        };
    }

    /** Where a camera's eye is, which way it looks (Minecraft's yaw and pitch, degrees) and its vertical field of view. */
    public record Pose(Vec3 eye, float yaw, float pitch, float fov) {
        public Vec3 forward() {
            return Vec3.directionFromRotation(pitch, yaw);
        }
    }

    /**
     * Where the camera at {@code pos} ({@code state}, {@code settings}) looks at {@code gameTime} +
     * {@code partialTick}: its head turns on a pivot (higher on a tripod), and the eye is the lens, a
     * little in front of the pivot.
     */
    public static Pose pose(BlockPos pos, BlockState state, int settings, long gameTime, float partialTick) {
        AttachFace face = state.getValue(TvCameraBlock.FACE);
        Direction facing = state.getValue(TvCameraBlock.FACING);
        float yaw = facing.toYRot() + panOffset(settings, gameTime, partialTick);
        float pitch = tiltDegrees(face, settings);
        Vec3 pivot = Vec3.atLowerCornerOf(pos).add(pivot(face, facing));
        Vec3 eye = pivot.add(Vec3.directionFromRotation(pitch, yaw).scale(0.36));
        return new Pose(eye, yaw, pitch, LENSES[lens(settings)]);
    }

    /** How far a panning camera has turned from its facing (degrees), or 0. */
    public static float panOffset(int settings, long gameTime, float partialTick) {
        if ((settings & PAN) == 0) {
            return 0.0F;
        }
        double t = (Math.floorMod(gameTime, PAN_TICKS) + partialTick) / PAN_TICKS;
        return (float) (Math.sin(t * Math.PI * 2) * PAN_DEGREES);
    }

    /** The head's pivot inside the block (block units): a tripod's head stands high, a wall bracket's sticks out. */
    public static Vec3 pivot(AttachFace face, Direction facing) {
        return switch (face) {
            case FLOOR -> new Vec3(0.5, 0.72, 0.5);
            case CEILING -> new Vec3(0.5, 0.44, 0.5);
            case WALL -> new Vec3(0.5 - facing.getStepX() * 0.06, 0.55, 0.5 - facing.getStepZ() * 0.06);
        };
    }

    /** How much snow on the picture at {@code fraction} of the way to the edge of the camera's reach: 0 to 0.85. */
    public static float snow(double fraction) {
        if (fraction <= CLEAR_FRACTION) {
            return 0.0F;
        }
        return (float) Math.min(0.85, (fraction - CLEAR_FRACTION) / (1.0 - CLEAR_FRACTION) * 0.85);
    }
}
