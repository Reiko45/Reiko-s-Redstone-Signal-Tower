package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.block.RadioMastBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * v0.55.0: masts can be climbed like ladders. A player touching one (a mast, a Dish Rotator, any
 * mast segment) who walks into it goes up; letting go they slide down slowly, and sneaking they hold
 * still. Works on both sides: the client moves its own player, the server keeps the fall from
 * counting.
 */
public final class MastClimbing {
    /** How fast you go up, in blocks a tick: a ladder's speed (0.2 less a tick of gravity and drag). */
    public static final double CLIMB_SPEED = 0.12;
    /** The fastest you slide down, as on a ladder. */
    public static final double SLIDE_SPEED = 0.15;
    /** How close a mast has to be to hold on to. */
    private static final double REACH = 0.06;

    private MastClimbing() {}

    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        apply(event.getEntity());
    }

    /** True if a mast segment touches {@code box} (from the side, between its feet and its head). */
    public static boolean touchingMast(BlockGetter level, AABB box) {
        AABB probe = box.inflate(REACH, 0, REACH);
        int minX = Mth.floor(probe.minX), maxX = Mth.floor(probe.maxX);
        int minY = Mth.floor(probe.minY), maxY = Mth.floor(probe.maxY - 1.0E-7);
        int minZ = Mth.floor(probe.minZ), maxZ = Mth.floor(probe.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof RadioMastBlock) {
                        for (AABB part : state.getShape(level, pos).toAabbs()) {
                            if (part.move(pos).intersects(probe)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Climbs, slides or holds {@code player} on a mast it touches, as a ladder would. Returns true if
     * it did anything.
     */
    public static boolean apply(Player player) {
        if (!enabled() || player.isSpectator() || player.getAbilities().flying || player.isFallFlying()
                || player.isPassenger() || player.onClimbable()) {
            return false;
        }
        if (player.onGround() && !player.horizontalCollision) {
            return false; // standing beside it: nothing to do
        }
        if (!touchingMast(player.level(), player.getBoundingBox())) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        double y = Math.max(motion.y, -SLIDE_SPEED);
        if (player.horizontalCollision) {
            y = CLIMB_SPEED; // walking into it: up
        } else if (player.isShiftKeyDown() && y < 0) {
            y = 0; // sneaking: hold on
        }
        player.setDeltaMovement(Mth.clamp(motion.x, -SLIDE_SPEED, SLIDE_SPEED), y, Mth.clamp(motion.z, -SLIDE_SPEED, SLIDE_SPEED));
        player.resetFallDistance();
        return true;
    }

    private static boolean enabled() {
        try {
            return Config.CLIMBABLE_MASTS.get();
        } catch (IllegalStateException notLoaded) {
            return true; // a client not on a server yet: the default
        }
    }
}
