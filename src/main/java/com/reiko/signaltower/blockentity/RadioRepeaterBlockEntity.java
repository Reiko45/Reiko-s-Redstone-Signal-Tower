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
 * <p>
 * A converter can have a signal gate (v0.41.0, {@link #getGate()}): "at least N" sends what it hears
 * only when that's N or more; "below N" sends 15 while what it hears is under N (at 1, a NOT gate).
 * A gated converter's output isn't worked out by the network's relay chain: this block entity
 * checks what it hears whenever that frequency changes (and once a second), and tells the network
 * ({@link SignalNetwork#setGateOutput}), at most once every {@link #GATE_MIN_TICKS} ticks, so a gate
 * feeding itself blinks like a redstone clock instead of locking up.
 */
public class RadioRepeaterBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private static final String OUTPUT_TAG = "output_frequency";
    private static final String GATE_TAG = "gate";
    private static final String THRESHOLD_TAG = "gate_threshold";
    /** The fewest ticks between two changes of a gate's output. */
    public static final int GATE_MIN_TICKS = 2;

    private int outputFrequency = Frequency.DEFAULT;
    private long seenRevision = -1;
    private int timer;
    /** Converter: its signal gate ({@link SignalNetwork.Repeater#GATE_NONE}, AT_LEAST or BELOW) and threshold (1-15). Saved. */
    private int gate = SignalNetwork.Repeater.GATE_NONE;
    private int threshold = 1;
    /** Gate: the revision of the input frequency at the last check, when the output last changed, and whether a change waits. Not saved. */
    private long seenInputRevision = -1;
    private long gateChangedAt = Long.MIN_VALUE / 2;
    private boolean gatePending;

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

    public int getGate() {
        return isConverter() ? gate : SignalNetwork.Repeater.GATE_NONE;
    }

    public int getThreshold() {
        return threshold;
    }

    /** Sets a converter's signal gate (v0.41.0); a threshold outside 1-15 is clamped. Then tells the network. */
    public void setGate(int newGate, int newThreshold) {
        int g = Math.max(SignalNetwork.Repeater.GATE_NONE, Math.min(SignalNetwork.Repeater.GATE_BELOW, newGate));
        int t = Math.max(1, Math.min(15, newThreshold));
        if (g != gate || t != threshold) {
            gate = g;
            threshold = t;
            seenInputRevision = -1;
            gatePending = true;
            setChanged();
            refresh();
        }
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
        com.reiko.signaltower.block.DishRotatorBlock.turnDish(serverLevel, worldPosition); // v0.52.0
        SignalNetwork network = SignalNetwork.get(serverLevel);
        network.updateRepeater(worldPosition, getFrequency(), getSendFrequency(), RadioHelper.towerReach(serverLevel, worldPosition, serverLevel.getBlockState(worldPosition)),
                getTunedBy(), getGate(), threshold);
        network.setDish(worldPosition, com.reiko.signaltower.radio.AntennaTier.dishAim(serverLevel.getBlockState(worldPosition))); // v0.48.0
        com.reiko.signaltower.radio.PowerCost.check(serverLevel, worldPosition); // v0.49.0
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

    @Override
    protected void onTunedByChanged() {
        refresh();
    }

    /**
     * A gated converter: if what it hears changed (or a change is waiting), works out what to send
     * and tells the network, unless its output changed less than {@link #GATE_MIN_TICKS} ago.
     */
    private void tickGate(ServerLevel level, SignalNetwork network, boolean force) {
        long revision = network.revision(getFrequency());
        if (!force && !gatePending && revision == seenInputRevision) {
            return;
        }
        seenInputRevision = revision;
        int heard = network.gateInput(level, worldPosition);
        int wanted = SignalNetwork.Repeater.gateOutput(gate, threshold, heard);
        int current = network.gateOutput(worldPosition);
        if (wanted == current) {
            gatePending = false;
            return;
        }
        long now = level.getGameTime();
        if (now - gateChangedAt < GATE_MIN_TICKS) {
            gatePending = true; // try again next tick
            return;
        }
        gatePending = false;
        gateChangedAt = now;
        network.setGateOutput(worldPosition, wanted);
    }

    /** What a gated converter hears on its input frequency right now (for its screen and Jade). */
    public int gateHeard(ServerLevel level) {
        return SignalNetwork.get(level).gateInput(level, worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioRepeaterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean gated = be.getGate() != SignalNetwork.Repeater.GATE_NONE;
        if (be.timer-- <= 0) {
            be.timer = RECHECK_INTERVAL;
            be.refresh();
            if (gated) {
                be.tickGate(serverLevel, SignalNetwork.get(serverLevel), true);
            }
            return;
        }
        if (gated) {
            be.tickGate(serverLevel, SignalNetwork.get(serverLevel), false);
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
            if (gate != SignalNetwork.Repeater.GATE_NONE) {
                tag.putInt(GATE_TAG, gate);
                tag.putInt(THRESHOLD_TAG, threshold);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputFrequency = tag.contains(OUTPUT_TAG) ? Frequency.clamp(tag.getInt(OUTPUT_TAG)) : Frequency.DEFAULT;
        gate = Math.max(SignalNetwork.Repeater.GATE_NONE, Math.min(SignalNetwork.Repeater.GATE_BELOW, tag.getInt(GATE_TAG)));
        threshold = tag.contains(THRESHOLD_TAG) ? Math.max(1, Math.min(15, tag.getInt(THRESHOLD_TAG))) : 1;
        seenInputRevision = -1;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.OUTPUT_FREQUENCY.get());
        if (stored != null) {
            outputFrequency = Frequency.clamp(stored);
        }
        Integer storedGate = componentInput.get(ModRegistry.CONVERTER_GATE.get());
        if (storedGate != null) {
            gate = Math.max(0, Math.min(2, storedGate / 16));
            threshold = Math.max(1, Math.min(15, storedGate % 16));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (isConverter()) {
            components.set(ModRegistry.OUTPUT_FREQUENCY.get(), outputFrequency);
            if (gate != SignalNetwork.Repeater.GATE_NONE) {
                components.set(ModRegistry.CONVERTER_GATE.get(), gate * 16 + threshold);
            }
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(OUTPUT_TAG);
        tag.remove(GATE_TAG);
        tag.remove(THRESHOLD_TAG);
    }
}
