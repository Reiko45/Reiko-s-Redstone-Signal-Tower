package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Altitude, reach and mast-column helpers. */
public final class RadioHelper {
    /** Hard limit for any mast-column scan, comfortably more than the tallest possible world. */
    private static final int SCAN_LIMIT = 4096;

    private RadioHelper() {}

    /** Blocks above (positive) or below (negative) sea level. */
    public static int heightAboveSeaLevel(Level level, BlockPos pos) {
        return pos.getY() - seaLevel(level);
    }

    /**
     * The dimension's real sea level. {@code Level.getSeaLevel()} is hard-coded to 63 in 1.21.1, so on
     * the server ask the world generator instead: 63 in the Overworld, 32 in the Nether, -63 in a
     * default superflat world.
     */
    public static int seaLevel(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getGenerator().getSeaLevel();
        }
        return level.getSeaLevel();
    }

    /** How far a transmitter at {@code pos} reaches, measured horizontally. */
    public static int reachAt(Level level, BlockPos pos) {
        return reachAt(level, pos, 1.0);
    }

    /** {@link #reachAt(Level, BlockPos)} times {@code multiplier} (before the cap). */
    public static int reachAt(Level level, BlockPos pos, double multiplier) {
        long height = Math.max(0, heightAboveSeaLevel(level, pos));
        double reach = (Config.BASE_REACH.get() + height * (double) Config.REACH_PER_BLOCK.get()) * multiplier;
        int max = Config.MAX_REACH.get();
        if (max > 0) {
            reach = Math.min(reach, max);
        }
        return (int) Math.min(Math.round(reach), Integer.MAX_VALUE);
    }

    /**
     * How far a tower top (transmitter, repeater, converter) in {@code state} at {@code pos} reaches:
     * by its height, times its antenna's multiplier (v0.45.0), capped by maxReach.
     */
    public static int towerReach(Level level, BlockPos pos, BlockState state) {
        return reachAt(level, pos, AntennaTier.of(state).multiplier());
    }

    /**
     * {@link #towerReach} in the weather where it stands now (v0.46.0): what the network goes by.
     * For status lines, Jade and the reach ring.
     */
    public static int towerReachNow(ServerLevel level, BlockPos pos, BlockState state) {
        return SignalNetwork.get(level).reachNow(level, pos, towerReach(level, pos, state));
    }

    /**
     * The strongest redstone signal feeding a transmitter: from the blocks around the transmitter
     * itself, or around any mast in the unbroken column directly beneath it.
     */
    public static int readTransmitterInput(Level level, BlockPos transmitterPos) {
        int best = level.getBestNeighborSignal(transmitterPos);
        BlockPos.MutableBlockPos cursor = transmitterPos.mutable().move(Direction.DOWN);
        for (int i = 0; i < SCAN_LIMIT && best < 15 && isMast(level, cursor); i++) {
            // v0.52.0: a Dish Rotator's redstone only turns the dish.
            if (!(level.getBlockState(cursor).getBlock() instanceof com.reiko.signaltower.block.DishRotatorBlock)) {
                best = Math.max(best, level.getBestNeighborSignal(cursor));
            }
            cursor.move(Direction.DOWN);
        }
        return best;
    }

    /** Number of masts in the unbroken column directly beneath {@code pos}. */
    public static int mastsBelow(BlockGetter level, BlockPos pos) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.DOWN);
        while (count < SCAN_LIMIT && isMast(level, cursor)) {
            count++;
            cursor.move(Direction.DOWN);
        }
        return count;
    }

    /**
     * Walks up the mast column starting at {@code start} and asks the transmitter on top, if there is
     * one, to re-read its input. Called whenever something changes next to or inside a mast column.
     */
    public static void refreshTransmitterAbove(Level level, BlockPos start) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        BlockPos.MutableBlockPos cursor = start.mutable();
        for (int i = 0; i < SCAN_LIMIT && isMast(level, cursor); i++) {
            cursor.move(Direction.UP);
        }
        if (level.getBlockEntity(cursor) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.refresh();
        } else if (level.getBlockEntity(cursor) instanceof com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity relay) {
            relay.refresh(); // v0.52.0: so a Dish Rotator under it turns its dish at once
        }
    }

    private static boolean isMast(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RadioMastBlock;
    }
}
