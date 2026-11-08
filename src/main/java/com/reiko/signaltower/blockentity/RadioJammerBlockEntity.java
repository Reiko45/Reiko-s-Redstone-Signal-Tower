package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.block.RadioJammerBlock;
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
 * A Radio Jammer (v0.46.0). It's built like a transmitter (tuned the same way, on masts, with an
 * antenna) but sends nothing: it tells the network how strongly it jams its frequency (the
 * redstone reaching it or its masts) and how far ({@link #jamReach}). Like a transmitter it checks
 * in when its redstone or frequency changes, and once a second.
 */
public class RadioJammerBlockEntity extends RadioTransmitterBlockEntity {
    public RadioJammerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_JAMMER_BE.get(), pos, state);
    }

    @Override
    public void refresh() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadioJammerBlock)) {
            return;
        }
        int power = RadioHelper.readTransmitterInput(serverLevel, worldPosition);
        boolean powered = power > 0 && !SignalNetwork.get(serverLevel).isKnockedOut(worldPosition);
        if (state.getValue(RadioTransmitterBlock.POWERED) != powered) {
            serverLevel.setBlock(worldPosition, state.setValue(RadioTransmitterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        SignalNetwork.get(serverLevel).updateJammer(worldPosition, getFrequency(), power,
                jamReach(serverLevel, worldPosition, state), getTunedBy());
        SignalNetwork.get(serverLevel).setDish(worldPosition, com.reiko.signaltower.radio.AntennaTier.dishAim(state));
    }

    /** How strongly it jams now: the redstone reaching it or its masts (0-15). */
    public int jamPower() {
        return level == null ? 0 : RadioHelper.readTransmitterInput(level, worldPosition);
    }

    /**
     * How far a jammer in {@code state} at {@code pos} reaches in clear weather: its share
     * ({@link Config#JAMMER_REACH_PERCENT}) of what a transmitter there, with the same antenna, would.
     */
    public static int jamReach(Level level, BlockPos pos, BlockState state) {
        var setting = Config.JAMMER_REACH_PERCENT;
        int percent = Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.round(RadioHelper.towerReach(level, pos, state) * percent / 100.0)));
    }
}
