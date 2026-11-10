package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Door Sensor's eye on what touches it (v0.68.0): a door, trapdoor, fence gate or barrel (their
 * {@code open} state) or a chest (anyone looking in). Every {@link #CHECK_TICKS} it looks; while one
 * is open, and for {@link #HOLD_TICKS} after, it sends full strength on its frequency like a Compact
 * Radio Transmitter. The frequency log (and a Security Console) name the player nearest it when it
 * opened, if one is within {@link #WHO_DISTANCE}.
 */
public class DoorSensorBlockEntity extends RadioTransmitterBlockEntity {
    public static final int CHECK_TICKS = 4;
    public static final int HOLD_TICKS = 20;
    public static final double WHO_DISTANCE = 6.0;

    // Not saved: after a reload it looks again.
    private boolean open;
    private long openUntil;
    private boolean sending;
    private @Nullable UUID openerId;
    private String openerName = "";
    private BlockPos watched = BlockPos.ZERO;

    public DoorSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DOOR_SENSOR_BE.get(), pos, state);
    }

    /** True while it sends: something it touches is open (or was, less than {@link #HOLD_TICKS} ago). */
    public boolean isSending() {
        return sending;
    }

    /** True while something it touches is open. */
    public boolean isOpen() {
        return open;
    }

    /** The open thing it saw last (or the first it found), for its readout. */
    public BlockPos watched() {
        return watched;
    }

    public String getOpenerName() {
        return openerName;
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return SignalNetwork.Cause.sensor(worldPosition, openerId, openerName);
    }

    /** True if the block at {@code pos} is a door, trapdoor, fence gate or barrel standing open, or a chest someone has open. */
    public static boolean isOpen(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            return state.getValue(BlockStateProperties.OPEN);
        }
        return level.getBlockEntity(pos) instanceof ChestBlockEntity && ChestBlockEntity.getOpenCount(level, pos) > 0;
    }

    /** True if the block at {@code pos} is something it can watch. */
    public static boolean watchable(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).hasProperty(BlockStateProperties.OPEN) || level.getBlockEntity(pos) instanceof ChestBlockEntity;
    }

    /** Looks at what touches it once (at {@code gameTime}), naming the nearest of {@code players} if it just opened. Public for the GameTests. */
    public void look(ServerLevel level, long gameTime, Iterable<? extends Player> players) {
        boolean nowOpen = false;
        BlockPos seen = null;
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            if (isOpen(level, next)) {
                nowOpen = true;
                seen = next;
                break;
            }
            if (seen == null && watchable(level, next)) {
                seen = next;
            }
        }
        if (seen != null) {
            watched = seen.immutable();
        }
        if (nowOpen && !open) {
            Player nearest = null;
            double best = WHO_DISTANCE * WHO_DISTANCE;
            for (Player player : players) {
                double distance = player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
                if (!player.isSpectator() && player.level() == level && distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            if (!sending) {
                openerId = nearest == null ? null : nearest.getUUID();
                openerName = nearest == null ? "" : nearest.getGameProfile().getName();
            }
        }
        open = nowOpen;
        if (open) {
            openUntil = gameTime + HOLD_TICKS;
        }
        update(gameTime);
    }

    private void update(long gameTime) {
        boolean now = open || gameTime < openUntil;
        if (now != sending) {
            sending = now;
            refresh();
        }
    }

    public static void sensorTick(Level level, BlockPos pos, BlockState state, DoorSensorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level.getGameTime();
        if ((now + pos.hashCode()) % CHECK_TICKS == 0) {
            be.look(serverLevel, now, serverLevel.players());
        } else {
            be.update(now);
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, be);
    }
}
