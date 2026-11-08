package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/**
 * Weather and reach (v0.46.0): while it rains or snows where a tower (transmitter, repeater,
 * converter, jammer) stands, its reach is shorter by {@link Config#RAIN_REACH_LOSS} percent, and by
 * {@link Config#THUNDER_REACH_LOSS} during a thunderstorm. Towers in biomes where it never rains
 * (deserts, savannas, badlands, the Nether, the End) aren't affected. The network applies it when it
 * measures reach, so it works for towers in unloaded chunks too, and the stored reach stays the
 * dry one.
 */
public enum Weather {
    CLEAR, RAIN, THUNDER;

    /** The weather in {@code level}'s sky now; always clear in a dimension without a sky. */
    public static Weather of(Level level) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return CLEAR;
        }
        return level.isThundering() ? THUNDER : level.isRaining() ? RAIN : CLEAR;
    }

    /** True if it rains or snows in {@code biome} at {@code pos} when the weather turns. */
    public static boolean wetBiome(Biome biome, BlockPos pos) {
        return biome.getPrecipitationAt(pos) != Biome.Precipitation.NONE;
    }

    /** Percent of reach lost in this weather (from the config; 0 when clear). */
    public int loss() {
        return switch (this) {
            case CLEAR -> 0;
            case RAIN -> value(Config.RAIN_REACH_LOSS);
            case THUNDER -> value(Config.THUNDER_REACH_LOSS);
        };
    }

    /** {@code reach} in this weather, rounded. */
    public int apply(int reach) {
        int loss = loss();
        if (loss <= 0 || reach <= 0) {
            return reach;
        }
        return (int) Math.round(reach * (100 - loss) / 100.0);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Rain" / "Thunderstorm" (for Jade and status lines). */
    public Component label() {
        return Component.translatable("weather." + SignalTower.MOD_ID + "." + key());
    }

    public static Weather of(int ordinal) {
        Weather[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    /** Tooltips can run before the server config loads: then the default. */
    private static int value(ModConfigSpec.IntValue setting) {
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }
}
