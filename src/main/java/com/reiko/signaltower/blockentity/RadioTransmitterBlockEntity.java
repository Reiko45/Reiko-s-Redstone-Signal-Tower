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

    /** v0.50.0: the dimension its Ender Antenna sends into, if one was picked (null: the default). */
    private @Nullable net.minecraft.resources.ResourceKey<Level> linkTarget;

    /** The dimension its Ender Antenna sends into (the one picked, if the server still has it, else the default). */
    public @Nullable net.minecraft.resources.ResourceKey<Level> linkTarget(ServerLevel level) {
        return com.reiko.signaltower.radio.CrossDimension.valid(level, linkTarget);
    }

    /** Picks the dimension its Ender Antenna sends into (null: the default), and tells the network. */
    public void setLinkTarget(@Nullable net.minecraft.resources.ResourceKey<Level> target) {
        linkTarget = target;
        setChanged();
        refresh();
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkTarget != null) {
            tag.putString("link_target", linkTarget.location().toString());
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        net.minecraft.resources.ResourceLocation id = tag.contains("link_target")
                ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString("link_target")) : null;
        linkTarget = id == null ? null : net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
    }

    /** Re-reads input power, height and frequency, and tells the network. Safe to call any time. */
    public void refresh() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        com.reiko.signaltower.block.DishRotatorBlock.turnDish(serverLevel, worldPosition); // v0.52.0
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadioTransmitterBlock)) {
            return;
        }
        int power = inputOf(serverLevel, worldPosition, state);
        SignalNetwork.get(serverLevel).updateTransmitter(worldPosition, getFrequency(), power,
                reachOf(serverLevel, worldPosition, state), getTunedBy(), state.getBlock() instanceof CompactRadioTransmitterBlock,
                power > 0 ? turnOnCause() : null);
        SignalNetwork.get(serverLevel).setDish(worldPosition, com.reiko.signaltower.radio.AntennaTier.dishAim(state)); // v0.48.0
        SignalNetwork.get(serverLevel).setLink(worldPosition, // v0.50.0: an Ender Antenna sends into another dimension too
                com.reiko.signaltower.radio.AntennaTier.of(state) == com.reiko.signaltower.radio.AntennaTier.ENDER
                        && com.reiko.signaltower.radio.AntennaTier.sendsAcross(state) ? linkTarget(serverLevel) : null);
        com.reiko.signaltower.radio.PowerCost.check(serverLevel, worldPosition); // v0.49.0: a charged battery, or quiet
        // Its light goes out while lightning has it knocked out (v0.47.0) or it's out of power (v0.49.0);
        // the network keeps what it would send.
        boolean powered = power > 0 && !SignalNetwork.get(serverLevel).isDown(worldPosition);
        if (state.getValue(RadioTransmitterBlock.POWERED) != powered) {
            serverLevel.setBlock(worldPosition, state.setValue(RadioTransmitterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
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
        if (state.getBlock() instanceof com.reiko.signaltower.block.BundledTransmitterBlock) {
            // v0.54.0: its strongest color (it sends each on its own frequency).
            return level.getBlockEntity(pos) instanceof BundledTransmitterBlockEntity bundled ? bundled.strongest() : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioWeatherStationBlock) {
            // v0.53.0: its reading of the weather, time or moon where it stands (redstone only pauses it).
            return level.getBlockEntity(pos) instanceof WeatherStationBlockEntity station ? station.output(level) : 0;
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

    /**
     * How far a transmitter in {@code state} at {@code pos} reaches: by height, or a compact one's
     * fixed reach (config, capped by maxReach); a Radio Jammer (v0.46.0) its share of a transmitter's.
     */
    public static int reachOf(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CompactRadioTransmitterBlock) {
            int reach = Config.COMPACT_TRANSMITTER_REACH.get();
            int max = Config.MAX_REACH.get();
            return max > 0 ? Math.min(reach, max) : reach;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioJammerBlock) {
            return RadioJammerBlockEntity.jamReach(level, pos, state);
        }
        return RadioHelper.towerReach(level, pos, state);
    }

    /** {@link #reachOf} in the weather where it stands now (v0.46.0; compact ones don't mind it). For status lines, Jade and the reach ring. */
    public static int reachNow(ServerLevel level, BlockPos pos, BlockState state) {
        int reach = reachOf(level, pos, state);
        return state.getBlock() instanceof CompactRadioTransmitterBlock ? reach : SignalNetwork.get(level).reachNow(level, pos, reach);
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
