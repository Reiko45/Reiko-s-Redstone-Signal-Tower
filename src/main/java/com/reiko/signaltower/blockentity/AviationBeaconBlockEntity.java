package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.AviationBeaconBlock;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An Aviation Beacon's block entity (v0.44.0). It keeps nothing: the beacon's settings are in its
 * block state. It's there so the client can draw the light ({@code AviationBeaconRenderer}), and
 * it remembers, on the client, whether the beacon should be on at all (dark enough, or its tower
 * sending), working that out again every few ticks rather than every frame, since following a
 * mast column up to its tower isn't free. The blinking itself is worked out every frame.
 */
public class AviationBeaconBlockEntity extends BlockEntity {
    /** How often (in ticks) the client works out again whether it should be on. */
    private static final int RECHECK_TICKS = 5;

    private long checkedAt;
    /** True until it has worked out whether it should be on, and again after its settings change. */
    private boolean stale = true;
    private boolean active;

    public AviationBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.AVIATION_BEACON_BE.get(), pos, state);
    }

    /** Whether its light is on now (in its blink, by the world's game time). */
    public boolean isLitNow() {
        Level level = getLevel();
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof AviationBeaconBlock)) {
            return false;
        }
        long now = level.getGameTime();
        if (stale || now - checkedAt >= RECHECK_TICKS || now < checkedAt) {
            stale = false;
            checkedAt = now;
            active = AviationBeaconBlock.isActive(level, getBlockPos(), state);
        }
        return active && state.getValue(AviationBeaconBlock.PATTERN).onAt(now);
    }

    /** Forgets what it worked out, so the next frame works it out again (its settings changed). */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        stale = true;
    }
}
