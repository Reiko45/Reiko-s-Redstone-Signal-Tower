package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioJammerBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.compat.ReikosWeatherCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Storm damage and ice (v0.54.0), with Reiko's Weather. Every {@link SignalNetwork#STORM_CHECK_TICKS}
 * it looks at every loaded full-size tower:
 * <ul>
 *   <li>a <b>tornado</b> touching it, or a <b>hurricane</b> or <b>derecho</b> over it (each look, a
 *   {@code stormDamageChancePercent} chance), blows a <b>tall</b> tower down unless it's
 *   <b>braced</b>: tall is {@code stormDamageMinMasts} masts or more under the top, braced is
 *   {@code guyWiresToBrace} Guy Wire Anchors on its mast. A blown-down tower is quiet (light off)
 *   until someone right-clicks it, or any of its masts, with an Iron Ingot;</li>
 *   <li>an <b>ice storm</b> over it ices its antenna up: {@code iceReachLossPercent} less reach until
 *   it thaws, which takes {@code iceThawSeconds} above freezing after the storm has gone.</li>
 * </ul>
 * Without Reiko's Weather no storm does this (and any ice thaws at once); damage stays until repaired.
 */
public final class StormDamage {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    /** How tall a tower is and how braced: what decides whether a storm can blow it down. */
    public record Assessment(int masts, @Nullable BlockPos base, int braces, boolean tall, boolean braced) {
        public boolean atRisk() {
            return tall && !braced;
        }
    }

    /** For the GameTests: the blow-down chance to use instead of the config's (null: the config's). */
    private static @Nullable Integer chanceForTests;

    private StormDamage() {}

    public static void chanceForTests(@Nullable Integer percent) {
        chanceForTests = percent;
    }

    /** True if {@code pos} holds a full-size tower top: transmitter (not compact), repeater, converter or jammer. */
    public static boolean isTowerTop(BlockGetter level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof RadioTransmitterBlock && !(block instanceof CompactRadioTransmitterBlock)
                || block instanceof RadioRepeaterBlock || block instanceof RadioJammerBlock;
    }

    /** The top of the tower whose mast (or top) is at {@code pos}, or null. */
    public static @Nullable BlockPos topOf(BlockGetter level, BlockPos pos) {
        BlockPos at = pos;
        for (int i = 0; i < 1024 && level.getBlockState(at).getBlock() instanceof RadioMastBlock; i++) {
            at = at.above();
        }
        return isTowerTop(level, at) ? at.immutable() : null;
    }

    /** How many masts are under the top at {@code top}, its column's foot, and the anchors on it. */
    public static Assessment assess(BlockGetter level, SignalNetwork network, BlockPos top) {
        int masts = RadioHelper.mastsBelow(level, top);
        BlockPos base = masts > 0 ? top.below(masts) : null;
        int braces = base != null ? network.bracesOf(base) : 0;
        return new Assessment(masts, base, braces, masts >= Config.STORM_DAMAGE_MIN_MASTS.get(),
                braces >= Config.GUY_WIRES_TO_BRACE.get());
    }

    /** The look every 2 seconds. */
    public static void check(ServerLevel level, SignalNetwork network) {
        boolean reikos = Config.STORM_DAMAGE.get() && ReikosWeatherCompat.available() && ReikosWeatherCompat.runs(level);
        for (BlockPos top : network.icedTowers()) {
            if (!reikos) {
                network.unice(top);
                continue;
            }
            if (!level.isLoaded(top)) {
                continue; // stays frozen while nobody's near
            }
            if (!isTowerTop(level, top)) {
                network.unice(top);
                continue;
            }
            if ("ice_storm".equals(ReikosWeatherCompat.typeAt(level, top))) {
                network.ice(top);
            } else {
                float celsius = ReikosWeatherCompat.temperature(level, top, ReikosWeatherCompat.local(level, top));
                if (celsius > 0 && network.thaw(top, SignalNetwork.STORM_CHECK_TICKS, Config.ICE_THAW_SECONDS.get() * 20)) {
                    level.playSound(null, top, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.4F, 1.6F);
                    continue;
                }
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, top.getX() + 0.5, top.getY() + 1.2, top.getZ() + 0.5, 4, 0.4, 0.6, 0.4, 0.01);
        }
        for (BlockPos top : network.damagedTowers()) {
            if (level.isLoaded(top)) {
                if (!isTowerTop(level, top)) {
                    network.repair(top); // it's gone
                } else {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY() + 1.1, top.getZ() + 0.5, 2, 0.2, 0.3, 0.2, 0.01);
                }
            }
        }
        if (!reikos) {
            return;
        }
        for (BlockPos top : network.towerTops()) {
            if (!level.isLoaded(top) || network.damageCause(top) != null || !isTowerTop(level, top)) {
                continue;
            }
            String type = ReikosWeatherCompat.typeAt(level, top);
            if ("ice_storm".equals(type) && !network.isIced(top)) {
                network.ice(top);
                level.playSound(null, top, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F);
            }
            boolean tornado = ReikosWeatherCompat.tornadoHits(level, top);
            boolean gale = "hurricane".equals(type) || "derecho".equals(type);
            if (!tornado && !gale) {
                continue;
            }
            Assessment assessment = assess(level, network, top);
            if (assessment.atRisk()
                    && (tornado || level.random.nextInt(100) < (chanceForTests != null ? chanceForTests : Config.STORM_DAMAGE_CHANCE_PERCENT.get()))) {
                blowDown(level, network, top, tornado ? "tornado" : type);
            } else if (assessment.tall() && assessment.braced()) {
                // v0.56.0: Weathered the Storm, for its tuner and whoever's watching
                com.reiko.signaltower.advancement.TowerAdvancements.fire(level, top,
                        com.reiko.signaltower.advancement.TowerAdvancements.STORM_SURVIVED, assessment.masts(),
                        network.tunerOf(top), com.reiko.signaltower.advancement.TowerAdvancements.STORM_RADIUS);
            }
        }
    }

    /** A storm ({@code cause}: "tornado", "derecho", "hurricane") blows the tower down. Public for the GameTests. */
    public static void blowDown(ServerLevel level, SignalNetwork network, BlockPos top, String cause) {
        network.damage(top, cause);
        level.playSound(null, top, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 1, 0, 0, 0, 0);
        UUID tuner = network.tunerOf(top);
        ServerPlayer owner = tuner == null ? null : level.getServer().getPlayerList().getPlayer(tuner);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(KEY + "storm_damaged_owner", causeName(cause),
                    level.getBlockState(top).getBlock().getName(), top.getX(), top.getY(), top.getZ()).withStyle(ChatFormatting.RED));
        }
    }

    /** "a tornado", "a derecho". */
    public static Component causeName(String cause) {
        return Component.translatable(KEY + "storm_cause." + cause);
    }

    /**
     * An Iron Ingot used on a blown-down tower's top or any of its masts puts it right (using up the
     * ingot unless in creative). Null if it's not an ingot or there's nothing to repair there.
     */
    public static @Nullable ItemInteractionResult repairWith(ItemStack stack, Level level, BlockPos clicked, Player player) {
        if (!stack.is(Items.IRON_INGOT)) {
            return null;
        }
        BlockPos top = topOf(level, clicked);
        if (top == null) {
            return null;
        }
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork network = SignalNetwork.get(serverLevel);
            if (network.damageCause(top) == null) {
                return null;
            }
            network.repair(top);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            serverLevel.playSound(null, top, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
            player.displayClientMessage(Component.translatable(KEY + "storm_repaired", level.getBlockState(top).getBlock().getName())
                    .withStyle(ChatFormatting.GREEN), true);
            // the tower's own refresh turns its light back on
            if (serverLevel.getBlockEntity(top) instanceof com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity tx) {
                tx.refresh();
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
