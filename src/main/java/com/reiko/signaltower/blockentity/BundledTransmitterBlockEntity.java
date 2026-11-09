package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.radio.BundledCables;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * A Bundled Radio Transmitter (v0.54.0): reads the 16 colors on the cables touching it or its
 * tower's masts, and sends color c on its frequency + c ({@link SignalNetwork#updateBundle}). The
 * cable network hands it new colors when they change ({@link #takeChannels}); it also reads them
 * again once a second, like every transmitter looks at its redstone.
 */
public class BundledTransmitterBlockEntity extends RadioTransmitterBlockEntity {
    private int[] channels = new int[SignalNetwork.BUNDLE_SIZE];

    public BundledTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BUNDLED_TRANSMITTER_BE.get(), pos, state);
    }

    /** What it sends now on each color (a copy). */
    public int[] getChannels() {
        return channels.clone();
    }

    /** The strongest color: how much it's sending, as one strength. */
    public int strongest() {
        return Arrays.stream(channels).max().orElse(0);
    }

    /** The cable network has new colors for it. */
    public void takeChannels(int[] next) {
        if (!Arrays.equals(channels, next)) {
            channels = next.clone();
            send();
        }
    }

    @Override
    public void refresh() {
        if (level instanceof ServerLevel serverLevel && serverLevel.getBlockState(worldPosition).getBlock() instanceof RadioTransmitterBlock) {
            channels = BundledCables.scan(serverLevel, worldPosition).channels();
        }
        send();
    }

    /** Sends its colors, and keeps the rest of the tower (dish, Ender Antenna, battery, light) as a transmitter does. */
    private void send() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        com.reiko.signaltower.block.DishRotatorBlock.turnDish(serverLevel, worldPosition);
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadioTransmitterBlock)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        network.updateBundle(worldPosition, getFrequency(), channels, reachOf(serverLevel, worldPosition, state), getTunedBy());
        network.setDish(worldPosition, com.reiko.signaltower.radio.AntennaTier.dishAim(state));
        network.setLink(worldPosition, com.reiko.signaltower.radio.AntennaTier.of(state) == com.reiko.signaltower.radio.AntennaTier.ENDER
                && com.reiko.signaltower.radio.AntennaTier.sendsAcross(state) ? linkTarget(serverLevel) : null);
        network.setUplink(worldPosition, com.reiko.signaltower.radio.AntennaTier.uplinks(serverLevel, worldPosition, state)); // v0.61.0
        com.reiko.signaltower.radio.PowerCost.check(serverLevel, worldPosition);
        boolean powered = strongest() > 0 && !network.isDown(worldPosition);
        if (state.getValue(RadioTransmitterBlock.POWERED) != powered) {
            serverLevel.setBlock(worldPosition, state.setValue(RadioTransmitterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }
}
