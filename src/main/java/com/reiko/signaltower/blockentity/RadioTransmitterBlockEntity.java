package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tells the network what this transmitter is sending. It updates the moment its input or frequency
 * changes, and also re-checks once a second (and right after its chunk loads) as a safety net for
 * changes no block update reports, such as world edits or config changes.
 */
public class RadioTransmitterBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private int timer;

    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_TRANSMITTER_BE.get(), pos, state);
    }

    /** Re-reads input power, height and frequency, and tells the network. Safe to call any time. */
    public void refresh() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadioTransmitterBlock)) {
            return;
        }
        int power = RadioHelper.readTransmitterInput(serverLevel, worldPosition);
        boolean powered = power > 0;
        if (state.getValue(RadioTransmitterBlock.POWERED) != powered) {
            serverLevel.setBlock(worldPosition, state.setValue(RadioTransmitterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        SignalNetwork.get(serverLevel).updateTransmitter(worldPosition, getFrequency(), power,
                RadioHelper.reachAt(serverLevel, worldPosition), getTunedBy());
    }

    @Override
    protected void onFrequencyChanged() {
        refresh();
    }

    @Override
    protected void onTunedByChanged() {
        refresh();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioTransmitterBlockEntity be) {
        if (be.timer-- > 0) {
            return;
        }
        be.timer = RECHECK_INTERVAL;
        be.refresh();
    }
}
