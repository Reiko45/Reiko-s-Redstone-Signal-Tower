package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioTelegraphBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A Radio Telegraph's block entity (v0.51.0): its frequency and tuner (who it reads as, on an
 * encrypted frequency). While loaded it checks in with the network, so a message sent near it
 * makes it click three times and give a 1-second redstone pulse. The messages themselves are kept
 * by the network ({@link SignalNetwork#messagesAt}), not here.
 */
public class TelegraphBlockEntity extends RadioBlockEntity {
    /** How long it stays lit (and powered) after a message arrives. */
    public static final int PULSE_TICKS = 20;
    private static final int CLICK_GAP_TICKS = 3;

    private long lastSent = Long.MIN_VALUE;
    /** v0.62.0: whether it sends messages as Morse (its screen's Morse button). Saved. */
    private boolean morse;
    /** v0.62.0: the Morse message it's keying out now, or null. Not saved. */
    private @Nullable Keying keying;

    /** A message being keyed: its timings, where it goes, who sent it, and how far along it is. */
    private static final class Keying {
        final java.util.List<com.reiko.signaltower.radio.Morse.Element> elements;
        final java.util.List<SignalNetwork.Coverage> coverage;
        final int frequency;
        final @Nullable UUID who;
        final String name;
        final String text;
        int index;
        int left;

        Keying(java.util.List<com.reiko.signaltower.radio.Morse.Element> elements, java.util.List<SignalNetwork.Coverage> coverage,
               int frequency, @Nullable UUID who, String name, String text) {
            this.elements = elements;
            this.coverage = coverage;
            this.frequency = frequency;
            this.who = who;
            this.name = name;
            this.text = text;
            this.left = elements.isEmpty() ? 0 : elements.get(0).ticks();
        }
    }
    private long poweredUntil;
    private int clicks;
    private int trackedFrequency = -1;
    private @Nullable UUID trackedTuner;

    public TelegraphBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TELEGRAPH_BE.get(), pos, state);
    }

    /** True if it sent a message less than {@link com.reiko.signaltower.radio.Telegraph#COOLDOWN_TICKS} ago. */
    public boolean coolingDown(long gameTime) {
        return lastSent != Long.MIN_VALUE && gameTime - lastSent < com.reiko.signaltower.radio.Telegraph.COOLDOWN_TICKS && gameTime >= lastSent;
    }

    public void markSent(long gameTime) {
        lastSent = gameTime;
    }

    /** v0.62.0: true if it sends messages as Morse. */
    public boolean isMorse() {
        return morse;
    }

    public void setMorse(boolean morse) {
        if (this.morse != morse) {
            this.morse = morse;
            setChanged();
        }
    }

    /** True while it's keying a message out in Morse. */
    public boolean isKeying() {
        return keying != null;
    }

    /**
     * v0.62.0: starts keying {@code text} (already {@link com.reiko.signaltower.radio.Morse#sendable})
     * in Morse on its frequency over {@code coverage}; when it's done, the text goes out as a message
     * from {@code who} ({@code name}). Returns how many ticks it takes.
     */
    public int startKeying(java.util.List<SignalNetwork.Coverage> coverage, @Nullable UUID who, String name, String text) {
        var elements = com.reiko.signaltower.radio.Morse.encode(text, com.reiko.signaltower.radio.Morse.TELEGRAPH_UNIT);
        keying = new Keying(elements, coverage, getFrequency(), who, name, text);
        return com.reiko.signaltower.radio.Morse.length(elements);
    }

    /** One tick of keying: the key down or up for this element, and the message out at the end. */
    private void keyTick(ServerLevel level, long now) {
        Keying k = keying;
        if (k == null) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(level);
        if (k.index < k.elements.size()) {
            var element = k.elements.get(k.index);
            if (element.on()) {
                if (k.left == element.ticks()) {
                    level.playSound(null, worldPosition, com.reiko.signaltower.registry.ModSounds.TELEGRAPH_KEY.get(), SoundSource.BLOCKS, 0.25F, 1.4F);
                }
                network.keyMorse(k.frequency, k.coverage, now, SignalNetwork.Cause.morse(worldPosition, k.who, k.name));
            }
            if (--k.left <= 0) {
                if (element.on()) {
                    network.releaseMorse(k.frequency, now);
                }
                k.index++;
                if (k.index < k.elements.size()) {
                    k.left = k.elements.get(k.index).ticks();
                }
            }
            return;
        }
        keying = null;
        markSent(now);
        com.reiko.signaltower.radio.Telegraph.morseMessage(level, worldPosition, k.frequency, k.coverage, k.who, k.name, k.text);
    }

    /**
     * A message on its frequency reached it: click and pulse, unless it can't read it (someone
     * else's encrypted frequency). Returns true if it could read it.
     */
    public boolean receive(ServerLevel level, SignalNetwork.Message message) {
        if (!FrequencyClaims.mayHear(level, getFrequency(), getTunedBy())) {
            return false;
        }
        poweredUntil = level.getGameTime() + PULSE_TICKS;
        clicks = 3;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RadioTelegraphBlock && !state.getValue(RadioTelegraphBlock.POWERED)) {
            level.setBlock(worldPosition, state.setValue(RadioTelegraphBlock.POWERED, true), Block.UPDATE_ALL);
            RadioTelegraphBlock.updateBelow(level, worldPosition, state.getBlock());
        }
        com.reiko.signaltower.compat.RadioComputer.messageArrived(level, worldPosition, getFrequency(), message); // v0.58.0
        return true;
    }

    @Override
    protected void onFrequencyChanged() {
        // Checked in again on its next tick.
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).untrackTelegraph(worldPosition);
            if (keying != null) {
                SignalNetwork.get(serverLevel).releaseMorse(keying.frequency, serverLevel.getGameTime()); // a message half keyed is lost
                keying = null;
            }
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (morse) {
            tag.putBoolean("telegraph_morse", true);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        morse = tag.getBoolean("telegraph_morse");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TelegraphBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.getFrequency() != be.trackedFrequency || !Objects.equals(be.getTunedBy(), be.trackedTuner)) {
            SignalNetwork.get(serverLevel).trackTelegraph(pos, be.getFrequency(), be.getTunedBy());
            be.trackedFrequency = be.getFrequency();
            be.trackedTuner = be.getTunedBy();
        }
        long now = level.getGameTime();
        be.keyTick(serverLevel, now); // v0.62.0
        if (be.clicks > 0 && now % CLICK_GAP_TICKS == 0) {
            be.clicks--;
            level.playSound(null, pos, com.reiko.signaltower.registry.ModSounds.TELEGRAPH_SOUNDER.get(), SoundSource.BLOCKS, 0.6F, be.clicks == 0 ? 0.9F : 1.05F);
        }
        if (state.getValue(RadioTelegraphBlock.POWERED) && now >= be.poweredUntil) {
            level.setBlock(pos, state.setValue(RadioTelegraphBlock.POWERED, false), Block.UPDATE_ALL);
            RadioTelegraphBlock.updateBelow(level, pos, state.getBlock());
        }
    }
}
