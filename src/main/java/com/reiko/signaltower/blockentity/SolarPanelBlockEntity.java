package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Solar Panel's workings (v0.50.0): once a second it charges the Radio Battery under it by how
 * much sunlight it gets, worked out like a Daylight Detector's (0 at night, 15 at noon in clear
 * weather, less in rain or thunder or with a roof over it): {@link Config#SOLAR_CHARGE_PER_SECOND}
 * a second at 15. Charge comes in whole units; the rest is kept for the next second (not saved).
 */
public class SolarPanelBlockEntity extends BlockEntity {
    private double owed;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SOLAR_PANEL_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity panel) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        RadioBatteryBlockEntity battery = batteryUnder(level, pos);
        double rate = rateFor(daylight(level, pos));
        if (battery != null && rate > 0) {
            panel.collect(battery, rate);
            battery.noteGain(level.getGameTime(), rate);
        }
    }

    /** Puts {@code rate} into {@code battery}: the whole units now, the rest next time. Returns how much went in. */
    public int collect(RadioBatteryBlockEntity battery, double rate) {
        owed += rate;
        int whole = (int) Math.floor(owed);
        if (whole <= 0) {
            return 0;
        }
        owed -= whole;
        return battery.add(whole); // a full battery wastes it
    }

    public static @Nullable RadioBatteryBlockEntity batteryUnder(Level level, BlockPos pos) {
        return level.getBlockEntity(pos.below()) instanceof RadioBatteryBlockEntity battery ? battery : null;
    }

    /** How much sunlight a panel at {@code pos} gets, 0-15: a Daylight Detector's reading there. */
    public static int daylight(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0;
        }
        int light = level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken();
        float angle = level.getSunAngle(1.0F);
        if (light > 0) {
            float toward = angle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            angle += (toward - angle) * 0.2F;
            light = Math.round(light * Mth.cos(angle));
        }
        return Mth.clamp(light, 0, 15);
    }

    /** Charge a second at {@code daylight} (0-15). */
    public static double rateFor(int daylight) {
        var setting = Config.SOLAR_CHARGE_PER_SECOND;
        double full = Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
        return Math.max(0, Math.min(15, daylight)) / 15.0 * full;
    }
}
