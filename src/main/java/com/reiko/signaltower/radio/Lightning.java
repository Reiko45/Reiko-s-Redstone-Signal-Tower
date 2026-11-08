package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lightning and towers (v0.47.0). While a thunderstorm rages where it stands, a tower open to
 * the sky (full-size transmitter, repeater, converter or jammer) gets struck now and then: on
 * average {@link Config#LIGHTNING_STRIKES_PER_HOUR} times an hour at 64 blocks above sea level,
 * fewer the lower it is. A struck tower crackles with sparks and goes quiet (sends, relays and jams
 * nothing) for {@link Config#LIGHTNING_OUTAGE_SECONDS}; a Lightning Rod within {@link #ROD_RANGE}
 * blocks of the tower's top takes the hit instead, and the tower stays on. The mod's own bolts are
 * visual only (no fire, no harm); bolts that land on a tower some other way (vanilla lightning, a
 * channeling trident, {@code /summon}) count too.
 */
public final class Lightning {
    /** Marks the mod's own bolts, so the join handler doesn't count them twice. */
    public static final String TAG = SignalTower.MOD_ID + "_strike";
    /** How far (in each direction) from a tower's top a Lightning Rod protects it. */
    public static final int ROD_RANGE = 2;

    private Lightning() {}

    /** Each level tick: ends outages, sparks knocked-out towers, and once a second in a thunderstorm maybe strikes a tower. */
    public static void tick(ServerLevel level, SignalNetwork network) {
        for (BlockPos pos : network.endOutages()) {
            refreshTower(level, pos);
        }
        long time = level.getGameTime();
        if (time % 4 == 0) {
            for (BlockPos pos : network.knockedOut()) {
                if (level.isLoaded(pos)) {
                    sparks(level, pos);
                }
            }
        }
        if (time % 20 != 0 || !level.isThundering() || strikesPerHour() <= 0) {
            return;
        }
        for (BlockPos top : network.lightningTargets()) {
            if (!level.isPositionEntityTicking(top) || network.isKnockedOut(top)) {
                continue;
            }
            double chance = chancePerSecond(level, top);
            if (chance > 0 && level.random.nextDouble() < chance && level.isRainingAt(top.above())) {
                strike(level, top);
            }
        }
    }

    /** The chance each second, in a thunderstorm, that lightning picks the tower at {@code top}: by its height above sea level. */
    public static double chancePerSecond(Level level, BlockPos top) {
        int height = RadioHelper.heightAboveSeaLevel(level, top);
        return height <= 0 ? 0 : strikesPerHour() / 3600.0 * height / 64.0;
    }

    /**
     * Lightning strikes the tower at {@code top}: a bolt comes down on it, or on a Lightning Rod
     * within {@link #ROD_RANGE} blocks, which takes the hit (and gives its redstone pulse). Returns
     * the rod that took it, or null if the tower was knocked out.
     */
    public static @Nullable BlockPos strike(ServerLevel level, BlockPos top) {
        BlockPos rod = rodNear(level, top);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf((rod != null ? rod : top).above()));
            bolt.setVisualOnly(true);
            bolt.addTag(TAG);
            level.addFreshEntity(bolt);
        }
        hit(level, top, rod);
        return rod;
    }

    /** A bolt joined {@code level} (from the join event): if it landed on a tower that isn't one of ours, that tower is hit. */
    public static void onBoltJoined(ServerLevel level, LightningBolt bolt) {
        if (bolt.getTags().contains(TAG)) {
            return;
        }
        BlockPos struck = BlockPos.containing(bolt.getX(), bolt.getY() - 1.0E-6, bolt.getZ());
        BlockPos top = isTower(level, struck) ? struck : isTower(level, struck.below()) ? struck.below() : null;
        if (top != null) {
            hit(level, top, rodNear(level, top));
        }
    }

    /** True if the block at {@code pos} is a tower lightning can knock out (not a compact transmitter or motion sensor). */
    public static boolean isTower(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof RadioTransmitterBlock && !(block instanceof CompactRadioTransmitterBlock)
                || block instanceof RadioRepeaterBlock;
    }

    /** The Lightning Rod that protects the tower at {@code top} (within {@link #ROD_RANGE} blocks; the highest, then the nearest), or null. */
    public static @Nullable BlockPos rodNear(Level level, BlockPos top) {
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = ROD_RANGE; dy >= -ROD_RANGE; dy--) {
            for (int dx = -ROD_RANGE; dx <= ROD_RANGE; dx++) {
                for (int dz = -ROD_RANGE; dz <= ROD_RANGE; dz++) {
                    cursor.setWithOffset(top, dx, dy, dz);
                    if (level.getBlockState(cursor).getBlock() instanceof LightningRodBlock) {
                        int dist = dx * dx + dz * dz;
                        if (best == null || cursor.getY() > best.getY() || cursor.getY() == best.getY() && dist < bestDist) {
                            best = cursor.immutable();
                            bestDist = dist;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void hit(ServerLevel level, BlockPos top, @Nullable BlockPos rod) {
        if (rod != null) {
            BlockState state = level.getBlockState(rod);
            if (state.getBlock() instanceof LightningRodBlock lightningRod) {
                lightningRod.onLightningStrike(state, level, rod);
            }
            return;
        }
        SignalNetwork.get(level).knockOut(top, level.getGameTime() + outageSeconds() * 20L);
        refreshTower(level, top);
        sparks(level, top);
        level.playSound(null, top, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /** Lets the tower's block entity catch up (its light, the network entry). */
    private static void refreshTower(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.refresh();
        } else if (level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity relay) {
            relay.refresh();
        }
    }

    private static void sparks(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 6, 0.35, 0.5, 0.35, 0.05);
    }

    public static double strikesPerHour() {
        var setting = Config.LIGHTNING_STRIKES_PER_HOUR;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    public static int outageSeconds() {
        var setting = Config.LIGHTNING_OUTAGE_SECONDS;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }
}
