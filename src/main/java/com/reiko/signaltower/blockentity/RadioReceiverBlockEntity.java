package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Listens to the network. It re-scans only when something on its frequency changed (so changes
 * arrive within one tick), when its own frequency changes, or once a second as a fallback.
 */
public class RadioReceiverBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;

    private long seenRevision = -1;
    private int seenFrequency = -1;
    private int timer;

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_RECEIVER_BE.get(), pos, state);
    }

    @Override
    protected void onFrequencyChanged() {
        seenRevision = -1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        int frequency = be.getFrequency();
        long revision = network.revision(frequency);

        boolean changed = revision != be.seenRevision || frequency != be.seenFrequency;
        if (!changed && be.timer-- > 0) {
            return;
        }
        be.timer = RECHECK_INTERVAL;
        be.seenRevision = revision;
        be.seenFrequency = frequency;

        int power = network.listen(serverLevel, frequency, pos).power();
        if (state.getValue(RadioReceiverBlock.POWER) != power) {
            // UPDATE_ALL also notifies neighbors, so wires and lamps next to us react right away.
            BlockState newState = state.setValue(RadioReceiverBlock.POWER, power);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            if (newState.getBlock() instanceof RadioReceiverBlock receiver) {
                receiver.afterOutputChanged(serverLevel, pos, newState);
            }
        }
    }
}
