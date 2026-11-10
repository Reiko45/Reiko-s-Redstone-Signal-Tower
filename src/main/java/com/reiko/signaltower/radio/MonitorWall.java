package com.reiko.signaltower.radio;

import com.reiko.signaltower.block.TvMonitorBlock;
import com.reiko.signaltower.blockentity.TvMonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Video walls (v0.66.0): TV Monitors side by side (and above one another) on one wall, facing the
 * same way and tuned to the same frequency, join into one big screen if together they make a full
 * rectangle of at most {@link #MAX_WIDTH} by {@link #MAX_HEIGHT}. The top-left one (as you look at
 * it) is the wall's lead: it picks the camera, keeps the channels and draws the whole picture. Any
 * other shape, or a bigger one, leaves each monitor on its own.
 */
public final class MonitorWall {
    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 5;
    private static final int MAX_BLOCKS = MAX_WIDTH * MAX_HEIGHT;

    /** A wall: its lead (top left), its size in blocks, and where one monitor is in it (column from the left, row from the top). */
    public record Wall(BlockPos lead, int width, int height, int column, int row) {
        public static Wall single(BlockPos pos) {
            return new Wall(pos.immutable(), 1, 1, 0, 0);
        }

        public boolean isLead() {
            return column == 0 && row == 0;
        }

        public int size() {
            return width * height;
        }
    }

    private MonitorWall() {
    }

    /** "Right" as you look at a monitor facing {@code facing}. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The wall the monitor at {@code pos} belongs to (just itself if the monitors around it don't make a rectangle). */
    public static Wall find(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TvMonitorBlock) || !(level.getBlockEntity(pos) instanceof TvMonitorBlockEntity start)) {
            return Wall.single(pos);
        }
        Direction facing = state.getValue(TvMonitorBlock.FACING);
        int frequency = start.getFrequency();
        Direction right = right(facing);
        Direction[] around = {right, right.getOpposite(), Direction.UP, Direction.DOWN};
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(pos.immutable());
        queue.add(pos.immutable());
        int minColumn = 0, maxColumn = 0, minRow = 0, maxRow = 0;
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction direction : around) {
                BlockPos next = at.relative(direction);
                if (found.contains(next) || !joins(level, next, facing, frequency)) {
                    continue;
                }
                if (found.size() >= MAX_BLOCKS) {
                    return Wall.single(pos); // too big to be one screen
                }
                found.add(next);
                queue.add(next);
                int column = column(pos, next, right);
                int row = pos.getY() - next.getY();
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
            }
        }
        int width = maxColumn - minColumn + 1;
        int height = maxRow - minRow + 1;
        if (width > MAX_WIDTH || height > MAX_HEIGHT || found.size() != width * height) {
            return Wall.single(pos);
        }
        BlockPos lead = pos.relative(right, minColumn).below(minRow);
        return new Wall(lead.immutable(), width, height, -minColumn, -minRow);
    }

    /** Every monitor in {@code wall} (facing {@code facing}), the lead first, row by row. */
    public static List<BlockPos> members(Wall wall, Direction facing) {
        List<BlockPos> members = new ArrayList<>(wall.size());
        Direction right = right(facing);
        for (int row = 0; row < wall.height(); row++) {
            for (int column = 0; column < wall.width(); column++) {
                members.add(wall.lead().relative(right, column).below(row));
            }
        }
        return members;
    }

    private static boolean joins(BlockGetter level, BlockPos pos, Direction facing, int frequency) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof TvMonitorBlock && state.getValue(TvMonitorBlock.FACING) == facing
                && level.getBlockEntity(pos) instanceof TvMonitorBlockEntity monitor && monitor.getFrequency() == frequency;
    }

    /** How many blocks to the right of {@code origin} {@code pos} is. */
    private static int column(BlockPos origin, BlockPos pos, Direction right) {
        return (pos.getX() - origin.getX()) * right.getStepX() + (pos.getZ() - origin.getZ()) * right.getStepZ();
    }
}
