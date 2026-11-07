package com.reiko.signaltower.client;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Beams of light the player put over blocks from the frequency audit (v0.33.0): clicking a row
 * that shows a position lights it up for a minute, and clicking it again turns it off. Only this
 * client knows about them, and only positions the audit already gave it can be lit, so they show
 * nothing the player couldn't already see. Cleared on leaving the world.
 * <p>
 * Plain logic with no client-only classes, so the GameTests can check it; {@link AuditBeamRenderer}
 * draws them.
 */
public final class AuditBeams {
    /** How long a beam stays on: a minute. */
    public static final int DURATION_TICKS = 1200;
    /** At most this many at once; lighting another drops the one with the least time left. */
    public static final int MAX_BEAMS = 8;

    /** A beam over {@code pos} in {@code dimension} ("minecraft:overworld"), with ticks left. */
    public record Beam(String dimension, BlockPos pos, int ticksLeft) {
        boolean at(String otherDimension, BlockPos otherPos) {
            return dimension.equals(otherDimension) && pos.equals(otherPos);
        }
    }

    private static final List<Beam> BEAMS = new ArrayList<>();

    private AuditBeams() {
    }

    /** Turns the beam over {@code pos} on (for a minute) or off. True if it's on now. */
    public static synchronized boolean toggle(String dimension, BlockPos pos) {
        if (BEAMS.removeIf(beam -> beam.at(dimension, pos))) {
            return false;
        }
        while (BEAMS.size() >= MAX_BEAMS) {
            Beam soonest = BEAMS.get(0);
            for (Beam beam : BEAMS) {
                if (beam.ticksLeft() < soonest.ticksLeft()) {
                    soonest = beam;
                }
            }
            BEAMS.remove(soonest);
        }
        BEAMS.add(new Beam(dimension, pos.immutable(), DURATION_TICKS));
        return true;
    }

    /** Seconds left on the beam over {@code pos} (rounded up), or -1 if there isn't one. */
    public static synchronized int secondsLeft(String dimension, BlockPos pos) {
        for (Beam beam : BEAMS) {
            if (beam.at(dimension, pos)) {
                return (beam.ticksLeft() + 19) / 20;
            }
        }
        return -1;
    }

    public static boolean isOn(String dimension, BlockPos pos) {
        return secondsLeft(dimension, pos) >= 0;
    }

    /** The beams in {@code dimension}, for drawing. */
    public static synchronized List<Beam> in(String dimension) {
        List<Beam> result = new ArrayList<>();
        for (Beam beam : BEAMS) {
            if (beam.dimension().equals(dimension)) {
                result.add(beam);
            }
        }
        return result;
    }

    public static synchronized int count() {
        return BEAMS.size();
    }

    /** One client tick (not while the game is paused): every beam burns down, and spent ones go. */
    public static synchronized void tick() {
        BEAMS.replaceAll(beam -> new Beam(beam.dimension(), beam.pos(), beam.ticksLeft() - 1));
        BEAMS.removeIf(beam -> beam.ticksLeft() <= 0);
    }

    /** Leaving the world. */
    public static synchronized void clear() {
        BEAMS.clear();
    }
}
