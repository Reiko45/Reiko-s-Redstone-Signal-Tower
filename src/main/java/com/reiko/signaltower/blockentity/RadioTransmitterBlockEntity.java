package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioMotionSensorBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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

    /** For blocks that send like a compact transmitter but keep more state (the Radio Motion Sensor). */
    protected RadioTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** What the frequency log names if this turns its frequency on; null: this transmitter and its tuner. */
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return null;
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
        int power = inputOf(serverLevel, worldPosition, state);
        boolean powered = power > 0;
        if (state.getValue(RadioTransmitterBlock.POWERED) != powered) {
            serverLevel.setBlock(worldPosition, state.setValue(RadioTransmitterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        SignalNetwork.get(serverLevel).updateTransmitter(worldPosition, getFrequency(), power,
                reachOf(serverLevel, worldPosition, state), getTunedBy(), state.getBlock() instanceof CompactRadioTransmitterBlock,
                power > 0 ? turnOnCause() : null);
    }

    /**
     * The redstone signal a transmitter in {@code state} at {@code pos} broadcasts: a normal one also
     * reads its mast column; a compact one (v0.38.0) only what's around it, like a button's block;
     * a Radio Motion Sensor (v0.39.0) full strength while it sees someone, whatever redstone is around.
     */
    public static int inputOf(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioTimerTransmitterBlock) {
            // v0.42.0: full strength while its clock is on, unless redstone pauses it.
            return level.getBlockEntity(pos) instanceof TimerTransmitterBlockEntity timer ? timer.output(level) : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioSignalRecorderBlock) {
            // v0.44.0: what its recording sends now (redstone only starts and stops it).
            return level.getBlockEntity(pos) instanceof SignalRecorderBlockEntity recorder ? recorder.getOutput() : 0;
        }
        if (state.getBlock() instanceof RadioMotionSensorBlock) {
            return level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor && sensor.isSensing() ? 15 : 0;
        }
        return state.getBlock() instanceof CompactRadioTransmitterBlock
                ? level.getBestNeighborSignal(pos)
                : RadioHelper.readTransmitterInput(level, pos);
    }

    /** How far a transmitter in {@code state} at {@code pos} reaches: by height, or a compact one's fixed reach (config, capped by maxReach). */
    public static int reachOf(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CompactRadioTransmitterBlock) {
            int reach = Config.COMPACT_TRANSMITTER_REACH.get();
            int max = Config.MAX_REACH.get();
            return max > 0 ? Math.min(reach, max) : reach;
        }
        return RadioHelper.reachAt(level, pos);
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
