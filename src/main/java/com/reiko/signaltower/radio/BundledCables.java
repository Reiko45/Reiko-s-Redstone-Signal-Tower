package com.reiko.signaltower.radio;

import com.reiko.signaltower.block.BundledCableBlock;
import com.reiko.signaltower.block.BundledReceiverBlock;
import com.reiko.signaltower.block.BundledTransmitterBlock;
import com.reiko.signaltower.block.CableTapBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.blockentity.BundledReceiverBlockEntity;
import com.reiko.signaltower.blockentity.BundledTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bundled cable networks (v0.54.0). Bundled Cables that touch make one network carrying 16 colors
 * (white, orange, ... black, as dyes are numbered), each 0-15. What's on a color is the strongest
 * of what's put on it: by an input Cable Tap of that color (the redstone it gets) or a Bundled
 * Radio Receiver (what it hears on that color's frequency). Output Cable Taps give their color out
 * as redstone; a Bundled Radio Transmitter (touching a cable itself or through any mast of its
 * tower) sends all 16. Nothing is lost along a cable. A network is worked out again, from scratch,
 * whenever something on it changes; nothing is stored in the cables.
 */
public final class BundledCables {
    /** The most cables one network looks through. */
    public static final int MAX_CABLES = 4096;

    /** A network: what each color carries, the devices on it, and how many cables it has. */
    public record Net(int[] channels, List<BlockPos> devices, int cables) {}

    private BundledCables() {}

    /** True if a cable connects to {@code neighbor}, which is {@code direction} from the cable. */
    public static boolean connects(BlockState neighbor, Direction direction) {
        if (neighbor.getBlock() instanceof BundledCableBlock || neighbor.getBlock() instanceof BundledTransmitterBlock
                || neighbor.getBlock() instanceof BundledReceiverBlock || neighbor.getBlock() instanceof RadioMastBlock) {
            return true;
        }
        return neighbor.getBlock() instanceof CableTapBlock && neighbor.getValue(CableTapBlock.FACING) == direction.getOpposite();
    }

    /** The Bundled Radio Transmitter on top of the mast column {@code mast} is in, or null. */
    public static BlockPos towerTopAbove(Level level, BlockPos mast) {
        BlockPos at = mast;
        for (int i = 0; i < 512 && level.getBlockState(at).getBlock() instanceof RadioMastBlock; i++) {
            at = at.above();
        }
        return level.getBlockState(at).getBlock() instanceof BundledTransmitterBlock ? at : null;
    }

    /** The network that {@code start} (a cable or a device) is on. A device on no cable is a network of its own. */
    public static Net scan(Level level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> devices = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockState first = level.getBlockState(start);
        if (first.getBlock() instanceof BundledCableBlock) {
            cables.add(start.immutable());
            queue.add(start.immutable());
        } else {
            devices.add(start.immutable());
            for (BlockPos touching : touching(level, start, first)) {
                BlockPos cable = touching.immutable();
                if (cables.add(cable)) {
                    queue.add(cable);
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos cable = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = cable.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof BundledCableBlock) {
                    if (cables.size() < MAX_CABLES && cables.add(next.immutable())) {
                        queue.add(next.immutable());
                    }
                } else if (connects(state, direction)) {
                    if (state.getBlock() instanceof RadioMastBlock) {
                        BlockPos top = towerTopAbove(level, next);
                        if (top != null) {
                            devices.add(top.immutable());
                        }
                    } else {
                        devices.add(next.immutable());
                    }
                }
            }
        }
        int[] channels = new int[SignalNetwork.BUNDLE_SIZE];
        for (BlockPos device : devices) {
            BlockState state = level.getBlockState(device);
            if (state.getBlock() instanceof CableTapBlock && !state.getValue(CableTapBlock.OUTPUT)) {
                int color = state.getValue(CableTapBlock.COLOR).getId();
                channels[color] = Math.max(channels[color], state.getValue(CableTapBlock.POWER));
            } else if (level.getBlockEntity(device) instanceof BundledReceiverBlockEntity receiver) {
                int[] heard = receiver.getHeard();
                for (int c = 0; c < channels.length; c++) {
                    channels[c] = Math.max(channels[c], heard[c]);
                }
            }
        }
        return new Net(channels, new ArrayList<>(devices), cables.size());
    }

    /**
     * The cables a device at {@code pos} feeds or reads: a tap's on its face; a bundled receiver's on
     * every side; a Bundled Radio Transmitter's beside it or beside any mast of its tower.
     */
    private static List<BlockPos> touching(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> result = new ArrayList<>();
        if (state.getBlock() instanceof CableTapBlock) {
            BlockPos cable = pos.relative(state.getValue(CableTapBlock.FACING));
            if (level.getBlockState(cable).getBlock() instanceof BundledCableBlock) {
                result.add(cable);
            }
            return result;
        }
        List<BlockPos> column = new ArrayList<>();
        column.add(pos);
        if (state.getBlock() instanceof BundledTransmitterBlock) {
            BlockPos at = pos.below();
            for (int i = 0; i < 512 && level.getBlockState(at).getBlock() instanceof RadioMastBlock; i++) {
                column.add(at);
                at = at.below();
            }
        }
        for (BlockPos at : column) {
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof BundledCableBlock) {
                    result.add(next);
                }
            }
        }
        return result;
    }

    /**
     * Works out the network at {@code start} again and hands what it carries to everything on it
     * that reads it: output taps change their redstone, Bundled Radio Transmitters what they send.
     * Server side; called whenever something on it may have changed.
     */
    public static Net update(Level level, BlockPos start) {
        Net net = scan(level, start);
        for (BlockPos device : net.devices()) {
            BlockState state = level.getBlockState(device);
            if (state.getBlock() instanceof CableTapBlock tap && state.getValue(CableTapBlock.OUTPUT)) {
                tap.setOutput(level, device, state, net.channels()[state.getValue(CableTapBlock.COLOR).getId()]);
            } else if (level.getBlockEntity(device) instanceof BundledTransmitterBlockEntity transmitter) {
                transmitter.takeChannels(net.channels());
            }
        }
        return net;
    }

    /** Updates every network that touches {@code pos} (after something there was placed or broken). */
    public static void updateAround(Level level, BlockPos pos) {
        Set<BlockPos> done = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            BlockState state = level.getBlockState(next);
            if (state.getBlock() instanceof BundledCableBlock && !done.contains(next)) {
                Net net = update(level, next);
                done.add(next.immutable());
                done.addAll(net.devices());
            } else if (state.getBlock() instanceof CableTapBlock || state.getBlock() instanceof BundledTransmitterBlock
                    || state.getBlock() instanceof BundledReceiverBlock) {
                if (done.add(next.immutable())) {
                    update(level, next);
                }
            } else if (state.getBlock() instanceof RadioMastBlock) {
                BlockPos top = towerTopAbove(level, next);
                if (top != null && done.add(top.immutable())) {
                    update(level, top);
                }
            }
        }
    }

    /** "white 15, red 8": the colors that are on (or "none"). */
    public static String describe(int[] channels) {
        StringBuilder text = new StringBuilder();
        for (int c = 0; c < channels.length; c++) {
            if (channels[c] > 0) {
                if (!text.isEmpty()) text.append(", ");
                text.append(DyeColor.byId(c).getName().replace('_', ' ')).append(' ').append(channels[c]);
            }
        }
        return text.toString();
    }
}
