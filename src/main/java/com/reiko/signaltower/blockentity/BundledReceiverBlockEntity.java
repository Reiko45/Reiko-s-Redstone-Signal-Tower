package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.BundledReceiverBlock;
import com.reiko.signaltower.radio.BundledCables;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * A Bundled Radio Receiver (v0.54.0): listens on its 16 frequencies when any of them changes (and
 * once a second anyway), and when what it hears changes it works its cable network out again.
 * Nothing extra is saved: it listens again after loading.
 */
public class BundledReceiverBlockEntity extends RadioBlockEntity {
    private int[] heard = new int[SignalNetwork.BUNDLE_SIZE];
    private final long[] revisions = new long[SignalNetwork.BUNDLE_SIZE];
    private boolean fresh = true;
    private int timer;

    public BundledReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BUNDLED_RECEIVER_BE.get(), pos, state);
    }

    /** What it hears on each color (a copy). */
    public int[] getHeard() {
        return heard.clone();
    }

    @Override
    protected void onFrequencyChanged() {
        fresh = true;
    }

    @Override
    protected void onTunedByChanged() {
        fresh = true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BundledReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        boolean listen = be.fresh || ++be.timer >= 20;
        for (int c = 0; c < SignalNetwork.BUNDLE_SIZE && !listen; c++) {
            listen = network.revision(be.getFrequency() + c) != be.revisions[c];
        }
        if (listen) {
            be.listen(serverLevel);
        }
    }

    /** Listens on all 16 again; if anything changed, its cables get it. Public for the GameTests. */
    public void listen(ServerLevel level) {
        fresh = false;
        timer = 0;
        SignalNetwork network = SignalNetwork.get(level);
        int[] now = new int[SignalNetwork.BUNDLE_SIZE];
        for (int c = 0; c < SignalNetwork.BUNDLE_SIZE; c++) {
            int frequency = getFrequency() + c;
            revisions[c] = network.revision(frequency);
            now[c] = frequency <= Frequency.MAX ? network.listen(level, frequency, worldPosition, getTunedBy()).power() : 0;
        }
        if (!Arrays.equals(now, heard)) {
            heard = now;
            BundledCables.update(level, worldPosition);
        }
        BlockState state = level.getBlockState(worldPosition);
        boolean lit = Arrays.stream(heard).anyMatch(power -> power > 0);
        if (state.getBlock() instanceof BundledReceiverBlock && state.getValue(BundledReceiverBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(BundledReceiverBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }
}
