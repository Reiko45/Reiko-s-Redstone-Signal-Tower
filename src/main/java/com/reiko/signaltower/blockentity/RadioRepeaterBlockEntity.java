package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared by the Radio Repeater and the Radio Converter. Tells the network where it is, the
 * frequency it listens on ({@link #getFrequency()}), the one it sends on, and its reach (on its
 * first tick, when re-tuned, and once a second as a safety net), and keeps its light in step with
 * what it's sending. The sending itself is done by the network, so it goes on while the chunk is
 * unloaded.
 * <p>
 * A repeater always sends on the frequency it listens on. A converter sends on
 * {@link #getOutputFrequency()}, which is saved with it and kept on the item when it's broken.
 */
public class RadioRepeaterBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private static final String OUTPUT_TAG = "output_frequency";

    private int outputFrequency = Frequency.DEFAULT;
    private long seenRevision = -1;
    private int timer;

    public RadioRepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_REPEATER_BE.get(), pos, state);
    }

    /** True if this is a Radio Converter (it sends on its own output frequency). */
    public boolean isConverter() {
        return getBlockState().getBlock() instanceof RadioConverterBlock;
    }

    /** The converter's output frequency, as stored (a repeater ignores it). */
    public int getOutputFrequency() {
        return outputFrequency;
    }

    /** The frequency this sends on: the output frequency for a converter, its own frequency for a repeater. */
    public int getSendFrequency() {
        return isConverter() ? outputFrequency : getFrequency();
    }

    /** Sets both frequencies at once (the converter's screen), then tells the network. */
    public void setFrequencies(int listen, int send) {
        int clampedSend = Frequency.clamp(send);
        boolean outputChanged = clampedSend != outputFrequency;
        outputFrequency = clampedSend;
        if (outputChanged) {
            setChanged();
        }
        if (listen != getFrequency()) {
            setFrequency(listen); // refreshes
        } else if (outputChanged) {
            refresh();
        }
    }

    /** Re-reads frequencies and height, tells the network, and updates the light. Safe to call any time. */
    public void refresh() {
        if (!(level instanceof ServerLevel serverLevel)
                || !(serverLevel.getBlockState(worldPosition).getBlock() instanceof RadioRepeaterBlock)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        network.updateRepeater(worldPosition, getFrequency(), getSendFrequency(), RadioHelper.reachAt(serverLevel, worldPosition));
        seenRevision = network.revision(getSendFrequency());
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
        // What it sends changes with what it hears; converters bump the frequency they send on
        // whenever the one they listen on changes, so watching that one is enough.
        SignalNetwork network = SignalNetwork.get(serverLevel);
        long revision = network.revision(be.getSendFrequency());
        if (revision != be.seenRevision) {
            be.seenRevision = revision;
            be.updateLight(serverLevel, network);
        }
    }

    // ---- Saving, and keeping the output frequency on the item ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (isConverter()) {
            tag.putInt(OUTPUT_TAG, outputFrequency);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputFrequency = tag.contains(OUTPUT_TAG) ? Frequency.clamp(tag.getInt(OUTPUT_TAG)) : Frequency.DEFAULT;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.OUTPUT_FREQUENCY.get());
        if (stored != null) {
            outputFrequency = Frequency.clamp(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (isConverter()) {
            components.set(ModRegistry.OUTPUT_FREQUENCY.get(), outputFrequency);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(OUTPUT_TAG);
    }
}
