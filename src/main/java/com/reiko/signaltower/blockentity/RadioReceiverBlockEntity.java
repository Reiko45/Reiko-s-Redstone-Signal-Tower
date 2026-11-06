package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Listens to the network. It re-scans only when something on its frequency changed (so changes
 * arrive within one tick), when its own frequency changes, or once a second as a fallback.
 * <p>
 * Shared by the Radio Receiver, the Compact Radio Receiver and the Radio Toggle Receiver. A toggle
 * receiver doesn't pass the signal through: it flips between off and on (full strength) each time
 * its frequency turns on (goes from silent to any strength) where it stands. It starts listening
 * without flipping when it's placed, loaded or re-tuned, so an already-on frequency doesn't count
 * as a turn-on. Its on/off state is saved.
 */
public class RadioReceiverBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private static final String TOGGLED_TAG = "toggled_on";

    private long seenRevision = -1;
    private int seenFrequency = -1;
    private int timer;
    /** Toggle receiver: on or off. Saved. */
    private boolean toggledOn;
    /** Toggle receiver: whether it heard its frequency at the last check, and whether that's been set yet. Not saved. */
    private boolean lastHeard;
    private boolean primed;

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_RECEIVER_BE.get(), pos, state);
    }

    /** True if this is a Radio Toggle Receiver. */
    public boolean isToggle() {
        return getBlockState().getBlock() instanceof RadioToggleReceiverBlock;
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
     * receiver, 15 or 0, flipping on each turn-on.
     */
    private int outputFor(int heard) {
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

        boolean changed = revision != be.seenRevision || frequency != be.seenFrequency;
        if (!changed && be.timer-- > 0) {
            return;
        }
        be.timer = RECHECK_INTERVAL;
        be.seenRevision = revision;
        if (frequency != be.seenFrequency) {
            network.trackReceiver(pos, frequency); // first tick after loading, or just re-tuned
        }
        be.seenFrequency = frequency;

        int power = be.outputFor(network.listen(serverLevel, frequency, pos).power());
        if (state.getValue(RadioReceiverBlock.POWER) != power) {
            // UPDATE_ALL also notifies neighbors, so wires and lamps next to us react right away.
            BlockState newState = state.setValue(RadioReceiverBlock.POWER, power);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            if (newState.getBlock() instanceof RadioReceiverBlock receiver) {
                receiver.afterOutputChanged(serverLevel, pos, newState);
            }
        }
    }

    // ---- Saving (the toggle receiver's state) ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (toggledOn) {
            tag.putBoolean(TOGGLED_TAG, true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        toggledOn = tag.getBoolean(TOGGLED_TAG);
    }
}
