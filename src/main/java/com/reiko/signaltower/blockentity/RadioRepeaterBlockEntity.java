package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tells the network where this repeater is, its frequency and its reach (on its first tick, when
 * re-tuned, and once a second as a safety net), and keeps its light in step with what it's
 * repeating. The repeating itself is done by the network, so it goes on while the chunk is unloaded.
 */
public class RadioRepeaterBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;

    private long seenRevision = -1;
    private int timer;

    public RadioRepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_REPEATER_BE.get(), pos, state);
    }

    /** Re-reads frequency and height, tells the network, and updates the light. Safe to call any time. */
    public void refresh() {
        if (!(level instanceof ServerLevel serverLevel)
                || !(serverLevel.getBlockState(worldPosition).getBlock() instanceof RadioRepeaterBlock)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        network.updateRepeater(worldPosition, getFrequency(), RadioHelper.reachAt(serverLevel, worldPosition));
        seenRevision = network.revision(getFrequency());
        updateLight(serverLevel, network);
    }

    private void updateLight(ServerLevel level, SignalNetwork network) {
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadioRepeaterBlock)) {
            return;
        }
        boolean lit = network.repeaterPower(level, worldPosition) > 0;
        if (state.getValue(RadioRepeaterBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(RadioRepeaterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void onFrequencyChanged() {
        refresh();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioRepeaterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.timer-- <= 0) {
            be.timer = RECHECK_INTERVAL;
            be.refresh();
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        long revision = network.revision(be.getFrequency());
        if (revision != be.seenRevision) {
            be.seenRevision = revision;
            be.updateLight(serverLevel, network);
        }
    }
}
