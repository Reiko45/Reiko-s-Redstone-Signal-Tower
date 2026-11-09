package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Guy Wire Anchor (v0.54.0): which mast column it braces (by the column's foot), found when it's
 * placed and again if that column goes; told to the network (so towers in unloaded chunks stay
 * braced) and to clients (to draw the wire).
 */
public class GuyAnchorBlockEntity extends BlockEntity {
    /** How far out (horizontally) an anchor looks for a mast, and how far up or down. */
    public static final int REACH = 12;
    public static final int REACH_UP = 6;
    private static final String BASE_TAG = "base";

    private @Nullable BlockPos base;
    private boolean registered;
    private int timer;
    // client: how tall the column is, looked at now and then for the wire
    private int clientHeight = -1;
    private long clientHeightAt;

    public GuyAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GUY_ANCHOR_BE.get(), pos, state);
    }

    /** The foot of the mast column it braces, or null. */
    public @Nullable BlockPos getBase() {
        return base;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GuyAnchorBlockEntity anchor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!anchor.registered) {
            anchor.registered = true;
            if (anchor.base == null || !isFoot(level, anchor.base)) {
                anchor.findMast(serverLevel);
            } else {
                SignalNetwork.get(serverLevel).setAnchor(pos, anchor.base);
            }
        }
        if (++anchor.timer >= (anchor.base == null ? 400 : 100)) {
            anchor.timer = 0;
            if (anchor.base == null || !isFoot(level, anchor.base)) {
                anchor.findMast(serverLevel);
            }
        }
    }

    /** True if {@code pos} is the bottom mast of a column. */
    public static boolean isFoot(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RadioMastBlock && !(level.getBlockState(pos.below()).getBlock() instanceof RadioMastBlock);
    }

    /** How many blocks tall the column standing on {@code base} is: its masts and the top on them. */
    public static int columnHeight(BlockGetter level, BlockPos base) {
        int height = 0;
        BlockPos.MutableBlockPos at = base.mutable();
        while (height < 1024 && level.getBlockState(at).getBlock() instanceof RadioMastBlock) {
            height++;
            at.move(0, 1, 0);
        }
        return com.reiko.signaltower.radio.StormDamage.isTowerTop(level, at) ? height + 1 : height;
    }

    /** Looks for the nearest mast column 2 to {@link #REACH} blocks out and braces it (or none). */
    public void findMast(ServerLevel level) {
        BlockPos best = null;
        long bestDist = Long.MAX_VALUE;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                long dist = (long) dx * dx + (long) dz * dz;
                if (dist < 4 || dist > (long) REACH * REACH || dist >= bestDist) {
                    continue;
                }
                for (int dy = -REACH_UP; dy <= REACH_UP; dy++) {
                    at.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (level.isLoaded(at) && isFoot(level, at)) {
                        best = at.immutable();
                        bestDist = dist;
                        break;
                    }
                }
            }
        }
        if (!java.util.Objects.equals(best, base)) {
            base = best;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        SignalNetwork.get(level).setAnchor(worldPosition, base);
    }

    /** Client: the column's height, looked at once a second. */
    public int clientHeight() {
        if (level != null && base != null && (clientHeight < 0 || level.getGameTime() - clientHeightAt >= 20)) {
            clientHeight = columnHeight(level, base);
            clientHeightAt = level.getGameTime();
        }
        return Math.max(0, clientHeight);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (base != null) {
            tag.putLong(BASE_TAG, base.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        base = tag.contains(BASE_TAG) ? BlockPos.of(tag.getLong(BASE_TAG)) : null;
        clientHeight = -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
