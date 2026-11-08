package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Radio Timer Transmitter's clock (v0.42.0). It broadcasts full strength on its frequency, like
 * a transmitter with a lever on it, but on a schedule:
 * <ul>
 *   <li><b>Interval:</b> a pulse every {@link #getInterval() interval} (1 second to 20 minutes),
 *   lasting its {@link #getPulse() pulse length} (at most half the interval). The first pulse goes
 *   out when it's placed or set, and it keeps that rhythm (by game time) through reloads.</li>
 *   <li><b>Daily:</b> on from one in-game hour to another (by the world's time of day, so 18:00 to
 *   06:00 is every night). On and off at the same hour means never on.</li>
 * </ul>
 * A redstone signal into it (or into the masts under it) pauses it: it sends nothing while powered.
 * It's a full-size transmitter otherwise, with reach by height and relaying remote presses.
 */
public class TimerTransmitterBlockEntity extends RadioTransmitterBlockEntity {
    /** The intervals it offers, in ticks: 1, 2, 3, 5, 10, 15 and 30 seconds, and 1, 2, 5, 10 and 20 minutes. */
    public static final int[] INTERVALS = {20, 40, 60, 100, 200, 300, 600, 1200, 2400, 6000, 12000, 24000};
    public static final int DEFAULT_INTERVAL = 200;
    public static final int DEFAULT_PULSE = PulseLength.DEFAULT_TICKS;
    public static final int DEFAULT_ON_HOUR = 18;
    public static final int DEFAULT_OFF_HOUR = 6;

    private static final String DAILY_TAG = "timer_daily";
    private static final String INTERVAL_TAG = "timer_interval";
    private static final String PULSE_TAG = "timer_pulse";
    private static final String ON_TAG = "timer_on_hour";
    private static final String OFF_TAG = "timer_off_hour";
    private static final String START_TAG = "timer_start";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private boolean daily;
    private int interval = DEFAULT_INTERVAL;
    private int pulse = DEFAULT_PULSE;
    private int onHour = DEFAULT_ON_HOUR;
    private int offHour = DEFAULT_OFF_HOUR;
    /** The game time its interval counts from (-1: not started yet; set on its first tick). Saved. */
    private long start = -1;
    /** Whether its clock was on at the last tick (not saved). */
    private boolean lastOn;

    public TimerTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TIMER_TRANSMITTER_BE.get(), pos, state);
    }

    // ---- Settings ----

    public boolean isDaily() {
        return daily;
    }

    public int getInterval() {
        return interval;
    }

    /** The pulse length picked (see {@link #effectivePulse()} for what it sends). */
    public int getPulse() {
        return pulse;
    }

    public int getOnHour() {
        return onHour;
    }

    public int getOffHour() {
        return offHour;
    }

    /** The pulse it sends: its pulse length, but at most half its interval. */
    public int effectivePulse() {
        return effectivePulse(interval, pulse);
    }

    public static int effectivePulse(int interval, int pulse) {
        return Math.max(1, Math.min(pulse, interval / 2));
    }

    /** {@code ticks} if it's one of {@link #INTERVALS}, else the default. */
    public static int validInterval(int ticks) {
        for (int allowed : INTERVALS) {
            if (allowed == ticks) {
                return ticks;
            }
        }
        return DEFAULT_INTERVAL;
    }

    /** {@code ticks} if it's one of the pulse length presets, else the default (1 second). */
    public static int validPulse(int ticks) {
        return PulseLength.isPreset(ticks) ? ticks : DEFAULT_PULSE;
    }

    public static int validHour(int hour) {
        return Math.floorMod(hour, 24);
    }

    /**
     * Sets its schedule (values it doesn't offer become the defaults) and starts its interval over
     * at {@code now}, so the first pulse goes out at once.
     */
    public void setTiming(boolean daily, int interval, int pulse, int onHour, int offHour, long now) {
        this.daily = daily;
        this.interval = validInterval(interval);
        this.pulse = validPulse(pulse);
        this.onHour = validHour(onHour);
        this.offHour = validHour(offHour);
        this.start = now;
        setChanged();
        if (level != null) {
            lastOn = clockOn(level);
        }
        refresh();
    }

    // ---- The clock ----

    /** The in-game hour (0-23) at day time {@code dayTime}: 0 is 06:00 in the morning, 18,000 is midnight. */
    public static int hourOf(long dayTime) {
        return (int) (Math.floorMod(dayTime + 6000, 24000L) / 1000);
    }

    /** True if a daily timer from {@code on} to {@code off} is on at {@code dayTime}. */
    public static boolean dailyOn(long dayTime, int on, int off) {
        int hour = hourOf(dayTime);
        if (on == off) {
            return false;
        }
        return on < off ? hour >= on && hour < off : hour >= on || hour < off;
    }

    /** True while its clock says on (whether or not redstone pauses it). */
    public boolean clockOn(Level level) {
        if (daily) {
            return dailyOn(level.getDayTime(), onHour, offHour);
        }
        long begun = start < 0 ? level.getGameTime() : start;
        return Math.floorMod(level.getGameTime() - begun, (long) interval) < effectivePulse();
    }

    /** True while a redstone signal reaches it (or a mast under it), which pauses it. */
    public boolean isPaused(Level level) {
        return RadioHelper.readTransmitterInput(level, worldPosition) > 0;
    }

    /** What it broadcasts now: 15 while its clock is on and it isn't paused, else 0. */
    public int output(Level level) {
        return clockOn(level) && !isPaused(level) ? 15 : 0;
    }

    /**
     * An interval timer's next change, in ticks: positive if its clock turns on then, negative if
     * it turns off then. 0 for a daily timer (it goes by the time of day; see its hours).
     */
    public long changeIn(Level level) {
        if (daily) {
            return 0;
        }
        long begun = start < 0 ? level.getGameTime() : start;
        long phase = Math.floorMod(level.getGameTime() - begun, (long) interval);
        int length = effectivePulse();
        return phase < length ? -(length - phase) : interval - phase;
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return SignalNetwork.Cause.timer(worldPosition, getTunedBy());
    }

    // ---- Labels ----

    /** "18:00". */
    public static Component hourLabel(int hour) {
        return Component.literal(String.format(java.util.Locale.ROOT, "%02d:00", validHour(hour)));
    }

    /** "10s", "1.5s", "2 min". */
    public static Component durationLabel(int ticks) {
        if (ticks >= 1200 && ticks % 1200 == 0) {
            return Component.translatable(KEY + "time_minutes", ticks / 1200);
        }
        return Component.translatable(KEY + "time_seconds", PulseLength.format(ticks).replace("s", ""));
    }

    /** "every 10s, 1s pulse" or "on 18:00 to 06:00". */
    public static Component scheduleText(boolean daily, int interval, int pulse, int onHour, int offHour) {
        if (daily) {
            return Component.translatable(KEY + "timer_daily", hourLabel(onHour), hourLabel(offHour));
        }
        return Component.translatable(KEY + "timer_interval", durationLabel(validInterval(interval)),
                durationLabel(effectivePulse(validInterval(interval), validPulse(pulse))));
    }

    public Component scheduleText() {
        return scheduleText(daily, interval, pulse, onHour, offHour);
    }

    // ---- Ticking ----

    public static void timerTick(Level level, BlockPos pos, BlockState state, TimerTransmitterBlockEntity be) {
        if (be.start < 0) {
            be.start = level.getGameTime(); // placed: the first pulse goes out now
            be.setChanged();
        }
        boolean on = be.clockOn(level);
        if (on != be.lastOn) {
            be.lastOn = on;
            be.refresh();
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, be);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(DAILY_TAG, daily);
        tag.putInt(INTERVAL_TAG, interval);
        tag.putInt(PULSE_TAG, pulse);
        tag.putInt(ON_TAG, onHour);
        tag.putInt(OFF_TAG, offHour);
        tag.putLong(START_TAG, start);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        daily = tag.getBoolean(DAILY_TAG);
        interval = tag.contains(INTERVAL_TAG) ? validInterval(tag.getInt(INTERVAL_TAG)) : DEFAULT_INTERVAL;
        pulse = tag.contains(PULSE_TAG) ? validPulse(tag.getInt(PULSE_TAG)) : DEFAULT_PULSE;
        onHour = tag.contains(ON_TAG) ? validHour(tag.getInt(ON_TAG)) : DEFAULT_ON_HOUR;
        offHour = tag.contains(OFF_TAG) ? validHour(tag.getInt(OFF_TAG)) : DEFAULT_OFF_HOUR;
        start = tag.contains(START_TAG) ? tag.getLong(START_TAG) : -1;
    }

    // ---- Item <-> block: it keeps its schedule (not where its interval is) ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer mode = componentInput.get(ModRegistry.TIMER_MODE.get());
        if (mode != null) {
            daily = mode == 1;
        }
        Integer storedInterval = componentInput.get(ModRegistry.TIMER_INTERVAL.get());
        if (storedInterval != null) {
            interval = validInterval(storedInterval);
        }
        Integer storedPulse = componentInput.get(ModRegistry.TIMER_PULSE.get());
        if (storedPulse != null) {
            pulse = validPulse(storedPulse);
        }
        Integer hours = componentInput.get(ModRegistry.TIMER_HOURS.get());
        if (hours != null) {
            onHour = validHour(hours / 24);
            offHour = validHour(hours % 24);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.TIMER_MODE.get(), daily ? 1 : 0);
        components.set(ModRegistry.TIMER_INTERVAL.get(), interval);
        components.set(ModRegistry.TIMER_PULSE.get(), pulse);
        components.set(ModRegistry.TIMER_HOURS.get(), onHour * 24 + offHour);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(DAILY_TAG);
        tag.remove(INTERVAL_TAG);
        tag.remove(PULSE_TAG);
        tag.remove(ON_TAG);
        tag.remove(OFF_TAG);
    }
}
