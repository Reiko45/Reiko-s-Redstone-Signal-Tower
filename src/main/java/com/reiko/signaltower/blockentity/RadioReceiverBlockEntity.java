package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioInvertedReceiverBlock;
import com.reiko.signaltower.block.RadioLogicReceiverBlock;
import com.reiko.signaltower.radio.Frequency;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Listens to the network. It re-scans only when something on its frequency changed (so changes
 * arrive within one tick), when its own frequency changes, or once a second as a fallback.
 * <p>
 * Shared by the Radio Receiver, the Compact Radio Receiver, the Radio Toggle Receiver and the Radio
 * Inverted Receiver and the Radio Logic Receiver. An inverted receiver outputs 15 while it hears
 * nothing and 0 while it hears anything (v0.39.0). A logic receiver (v0.40.0) also listens on a
 * second frequency and combines the two ({@link LogicMode}); it's tracked on both. A toggle
 * receiver doesn't pass the signal through: it flips between off and on (full strength) each time
 * its frequency turns on (goes from silent to any strength) where it stands. It starts listening
 * without flipping when it's placed, loaded or re-tuned, so an already-on frequency doesn't count
 * as a turn-on. Its on/off state is saved.
 * <p>
 * On someone else's encrypted frequency (one whose owner and members don't include whoever tuned
 * or placed it) it hears nothing. Becoming able to hear it, or losing that, isn't a turn-on either.
 */
public class RadioReceiverBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private static final String TOGGLED_TAG = "toggled_on";
    private static final String SECOND_TAG = "second_frequency";
    private static final String MODE_TAG = "logic_mode";

    /**
     * How a Radio Logic Receiver combines what it hears on its two frequencies (v0.40.0). Saved by
     * name, sent by ordinal: only add at the end.
     */
    public enum LogicMode {
        /** Both on: the weaker of the two. */
        AND,
        /** Either on: the stronger of the two. */
        OR,
        /** Exactly one on: that one's strength. */
        XOR;

        public static LogicMode of(int ordinal) {
            LogicMode[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        static LogicMode named(String name) {
            for (LogicMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return AND;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The output for strengths {@code a} and {@code b} (0 = silent). */
        public int combine(int a, int b) {
            return switch (this) {
                case AND -> Math.min(a, b);
                case OR -> Math.max(a, b);
                case XOR -> a > 0 && b > 0 ? 0 : Math.max(a, b);
            };
        }
    }

    private long seenRevision = -1;
    private int seenFrequency = -1;
    private int timer;
    /** Toggle receiver: on or off. Saved. */
    private boolean toggledOn;
    /** Toggle receiver: whether it heard its frequency at the last check, and whether that's been set yet. Not saved. */
    private boolean lastHeard;
    private boolean primed;
    /** Whether it was deaf (on someone else's encrypted frequency) at the last check, and who it was tracked as tuned by. Not saved. */
    private boolean wasDeaf;
    private @Nullable UUID trackedTuner;
    /** Logic receiver: its second frequency and mode (saved), and what it last checked on the second (not saved). */
    private int secondFrequency = Frequency.DEFAULT;
    private LogicMode logicMode = LogicMode.AND;
    private long seenRevisionB = -1;
    private int seenSecond = -1;
    /** Logic receiver: what it heard on each frequency at the last check (not saved; for the screen). */
    private int heardA;
    private int heardB;

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_RECEIVER_BE.get(), pos, state);
    }

    /** True if this is a Radio Toggle Receiver. */
    public boolean isToggle() {
        return getBlockState().getBlock() instanceof RadioToggleReceiverBlock;
    }

    /** True if this is a Radio Inverted Receiver. */
    public boolean isInverted() {
        return getBlockState().getBlock() instanceof RadioInvertedReceiverBlock;
    }

    /** True if this is a Radio Logic Receiver. */
    public boolean isLogic() {
        return getBlockState().getBlock() instanceof RadioLogicReceiverBlock;
    }

    /** A logic receiver's second frequency. */
    public int getSecondFrequency() {
        return secondFrequency;
    }

    public LogicMode getLogicMode() {
        return logicMode;
    }

    /** A logic receiver's two frequencies and mode at once (the first one through {@link #setFrequency}). */
    public void setLogic(int first, int second, LogicMode mode) {
        int clamped = Frequency.clamp(second);
        if (clamped != secondFrequency || mode != logicMode) {
            secondFrequency = clamped;
            logicMode = mode;
            seenRevisionB = -1;
            setChanged();
        }
        setFrequency(first);
    }

    /** What a logic receiver heard on its first and second frequency at its last check. */
    public int heardFirst() {
        return heardA;
    }

    public int heardSecond() {
        return heardB;
    }

    /**
     * The frequency this receiver can't hear because it's someone else's encrypted one (whoever tuned
     * or placed it isn't a member): its own, or a logic receiver's second; -1 if it hears both.
     */
    public int deafFrequency(Level level) {
        if (!FrequencyClaims.mayHear(level, getFrequency(), getTunedBy())) {
            return getFrequency();
        }
        return isLogic() && !FrequencyClaims.mayHear(level, secondFrequency, getTunedBy()) ? secondFrequency : -1;
    }

    /** A toggle receiver's state: true if it's switched on. Always false for the other receivers. */
    public boolean isToggledOn() {
        return toggledOn;
    }

    @Override
    protected void onFrequencyChanged() {
        seenRevision = -1;
        primed = false; // the new frequency's current state isn't a turn-on
    }

    @Override
    protected void onTunedByChanged() {
        seenRevision = -1; // check again at once: it may hear an encrypted frequency now, or not any more
    }

    /** Broken, or its chunk unloaded: stop counting it as a listener. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).untrackReceiver(worldPosition);
        }
    }

    /**
     * What to output for a heard strength: the same strength for a plain receiver; for a toggle
     * receiver, 15 or 0, flipping on each turn-on; for an inverted receiver, 15 while silent and 0
     * while hearing anything.
     */
    private int outputFor(int heard) {
        if (isInverted()) {
            return heard > 0 ? 0 : 15;
        }
        if (!isToggle()) {
            return heard;
        }
        boolean hearing = heard > 0;
        if (!primed) {
            primed = true;
        } else if (hearing && !lastHeard) {
            toggledOn = !toggledOn;
            setChanged();
        }
        lastHeard = hearing;
        return toggledOn ? 15 : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        int frequency = be.getFrequency();
        long revision = network.revision(frequency);
        boolean logic = be.isLogic();
        int second = logic ? be.secondFrequency : -1;
        long revisionB = logic ? network.revision(second) : 0;

        boolean changed = revision != be.seenRevision || frequency != be.seenFrequency
                || logic && (revisionB != be.seenRevisionB || second != be.seenSecond);
        if (!changed && be.timer-- > 0) {
            return;
        }
        be.timer = RECHECK_INTERVAL;
        be.seenRevision = revision;
        be.seenRevisionB = revisionB;
        UUID tunedBy = be.getTunedBy();
        if (frequency != be.seenFrequency || second != be.seenSecond || !Objects.equals(tunedBy, be.trackedTuner)) {
            network.trackReceiver(pos, frequency, tunedBy, second); // first tick after loading, or just re-tuned
            be.trackedTuner = tunedBy;
        }
        be.seenFrequency = frequency;
        be.seenSecond = second;

        boolean deaf = !FrequencyClaims.mayHear(serverLevel, frequency, tunedBy);
        if (deaf != be.wasDeaf) {
            be.wasDeaf = deaf;
            be.primed = false; // starting or stopping hearing an encrypted frequency isn't a turn-on
        }
        int heard = deaf ? 0 : network.listen(serverLevel, frequency, pos).power();
        int power;
        if (logic) {
            boolean deafB = !FrequencyClaims.mayHear(serverLevel, second, tunedBy);
            be.heardA = heard;
            be.heardB = deafB ? 0 : network.listen(serverLevel, second, pos).power();
            power = be.logicMode.combine(be.heardA, be.heardB);
        } else {
            power = be.outputFor(heard);
        }
        if (state.getValue(RadioReceiverBlock.POWER) != power) {
            // UPDATE_ALL also notifies neighbors, so wires and lamps next to us react right away.
            BlockState newState = state.setValue(RadioReceiverBlock.POWER, power);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            if (newState.getBlock() instanceof RadioReceiverBlock receiver) {
                receiver.afterOutputChanged(serverLevel, pos, newState);
            }
        }
    }

    // ---- Saving (the toggle receiver's state, the logic receiver's second frequency and mode) ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (toggledOn) {
            tag.putBoolean(TOGGLED_TAG, true);
        }
        if (isLogic()) {
            tag.putInt(SECOND_TAG, secondFrequency);
            tag.putString(MODE_TAG, logicMode.key());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        toggledOn = tag.getBoolean(TOGGLED_TAG);
        secondFrequency = tag.contains(SECOND_TAG) ? Frequency.clamp(tag.getInt(SECOND_TAG)) : Frequency.DEFAULT;
        logicMode = LogicMode.named(tag.getString(MODE_TAG));
        seenRevisionB = -1;
    }

    // ---- Item <-> block: a logic receiver keeps its second frequency and mode ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer second = componentInput.get(ModRegistry.SECOND_FREQUENCY.get());
        if (second != null) {
            secondFrequency = Frequency.clamp(second);
        }
        Integer mode = componentInput.get(ModRegistry.LOGIC_MODE.get());
        if (mode != null) {
            logicMode = LogicMode.of(mode);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (isLogic()) {
            components.set(ModRegistry.SECOND_FREQUENCY.get(), secondFrequency);
            components.set(ModRegistry.LOGIC_MODE.get(), logicMode.ordinal());
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(SECOND_TAG);
        tag.remove(MODE_TAG);
    }
}
