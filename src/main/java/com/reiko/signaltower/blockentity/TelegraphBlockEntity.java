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
        }
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
        if (be.clicks > 0 && now % CLICK_GAP_TICKS == 0) {
            be.clicks--;
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.6F, be.clicks == 0 ? 1.2F : 1.8F);
        }
        if (state.getValue(RadioTelegraphBlock.POWERED) && now >= be.poweredUntil) {
            level.setBlock(pos, state.setValue(RadioTelegraphBlock.POWERED, false), Block.UPDATE_ALL);
            RadioTelegraphBlock.updateBelow(level, pos, state.getBlock());
        }
    }
}
