package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.compat.ReikosWeatherCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a Radio Weather Station (v0.53.0) broadcasts: one reading of the conditions where it stands,
 * as a strength. Three readings work anywhere; the rest need Reiko's Weather, and use the same
 * scales as its own Weather Station and Weather Radar, so a table for one works for the other.
 * Saved by name: only add at the end.
 */
public enum StationReading {
    /** Clear 0, rain or snow 8, thunderstorm 15 (as towers' reach goes by: a storm cell over it counts). */
    WEATHER(false),
    /** The time of day: 0 at 06:00, 1 more every 1.5 hours, 4 at noon, 8 at 18:00, 12 at midnight. */
    TIME(false),
    /** The moon: 1 full moon ... 5 new moon ... 8 waxing gibbous; 15 during a Blood Moon (Reiko's Weather). */
    MOON(false),
    /** Reiko's Weather's 0-15 weather code. */
    CONDITIONS(true),
    /** Storm danger: 15 thunder or severe weather, 10 hail or heavy rain, 5 rain, else 0. */
    STORM(true),
    /** Wind strength, 0 calm to 15. */
    WIND(true),
    /** How hard it rains or snows, 0 to 15. */
    PRECIPITATION(true),
    /** The air temperature: 0 at −10 °C or colder, 1 more every 3 °C, 15 at 35 °C or hotter. */
    TEMPERATURE(true),
    /** The season: 1 spring, 2 summer, 3 autumn, 4 winter (0 without seasons). */
    SEASON(true),
    /** The nearest storm: 15 overhead, 1 less every 100 blocks to its edge (like Reiko's Weather Radar). */
    STORM_NEAR(true);

    private static final String KEY = "station." + SignalTower.MOD_ID + ".";

    /** True if this reading needs Reiko's Weather. */
    public final boolean needsReikosWeather;

    StationReading(boolean needsReikosWeather) {
        this.needsReikosWeather = needsReikosWeather;
    }

    /** A reading: the strength it broadcasts and what it means ("Rain", "18:00", "12 °C"). */
    public record Value(int strength, Component label) {
        public Value {
            strength = Mth.clamp(strength, 0, 15);
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Weather", "Time of day". */
    public Component label() {
        return Component.translatable(KEY + "mode." + key());
    }

    /** "clear 0, rain 8, thunder 15": what the strengths mean. */
    public Component scale() {
        return Component.translatable(KEY + "scale." + key());
    }

    /** True if it can be picked here: it works anywhere, or Reiko's Weather is installed. */
    public boolean offered() {
        return !needsReikosWeather || ReikosWeatherCompat.available();
    }

    /** The readings that can be picked here, in order. */
    public static List<StationReading> offeredHere() {
        List<StationReading> list = new ArrayList<>();
        for (StationReading reading : values()) {
            if (reading.offered()) {
                list.add(reading);
            }
        }
        return list;
    }

    public static StationReading named(@Nullable String name) {
        for (StationReading reading : values()) {
            if (reading.name().equalsIgnoreCase(name)) {
                return reading;
            }
        }
        return WEATHER;
    }

    public static StationReading of(int ordinal) {
        StationReading[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    // ---- Reading ----

    /** What this reads at {@code pos} now. */
    public Value read(ServerLevel level, BlockPos pos) {
        if (!offered()) {
            return new Value(0, Component.translatable(KEY + "needs_reikos_weather").withStyle(ChatFormatting.RED));
        }
        return switch (this) {
            case WEATHER -> weather(level, pos);
            case TIME -> time(level.getDayTime());
            case MOON -> moon(level);
            case STORM_NEAR -> stormNear(level, pos);
            case SEASON -> season();
            default -> reikos(level, pos);
        };
    }

    /** The weather where it stands, as towers' reach goes by it: clear 0, rain 8, thunder 15. */
    private static Value weather(ServerLevel level, BlockPos pos) {
        Weather weather = SignalNetwork.get(level).weatherAt(level, pos);
        int strength = weather == Weather.THUNDER ? 15 : weather == Weather.RAIN ? 8 : 0;
        return new Value(strength, weatherLabel(level, pos, weather));
    }

    /** "Rain", or Reiko's Weather's own name for it ("Heavy Rain", "Sandstorm") when it agrees on rain and thunder. */
    public static Component weatherLabel(ServerLevel level, BlockPos pos, Weather weather) {
        ReikosWeatherCompat.Local local = ReikosWeatherCompat.local(level, pos);
        if (local != null && local.weather() == weather) {
            return local.label();
        }
        return weather.label();
    }

    /**
     * The weather's name for announcements, and an id that only changes when it does ("rain",
     * "supercell"); Reiko's Weather's when it agrees on rain and thunder.
     */
    public static String[] weatherNow(ServerLevel level, BlockPos pos) {
        Weather weather = SignalNetwork.get(level).weatherAt(level, pos);
        ReikosWeatherCompat.Local local = ReikosWeatherCompat.local(level, pos);
        if (local != null && local.weather() == weather) {
            return new String[]{local.id(), local.label().getString(), local.severe() ? "severe" : ""};
        }
        return new String[]{weather.key(), weather.label().getString(), ""};
    }

    /** The time of day: 0 at 06:00, 1 more every 1,500 ticks (1.5 hours). */
    public static int timeStrength(long dayTime) {
        return (int) (Math.floorMod(dayTime, 24000L) / 1500L);
    }

    /** "18:30". */
    public static String clock(long dayTime) {
        long clock = Math.floorMod(dayTime + 6000, 24000L);
        return String.format(Locale.ROOT, "%02d:%02d", clock / 1000, clock % 1000 * 60 / 1000);
    }

    private static Value time(long dayTime) {
        return new Value(timeStrength(dayTime), Component.literal(clock(dayTime)));
    }

    private static Value moon(ServerLevel level) {
        if (ReikosWeatherCompat.bloodMoon(level)) {
            return new Value(15, Component.translatable(KEY + "blood_moon").withStyle(ChatFormatting.DARK_RED));
        }
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return new Value(0, Component.translatable(KEY + "no_sky"));
        }
        int phase = level.getMoonPhase();
        return new Value(phase + 1, moonName(phase));
    }

    /** "Full moon" (phase 0) ... "Waxing gibbous" (phase 7). */
    public static Component moonName(int phase) {
        return Component.translatable(KEY + "moon." + Math.floorMod(phase, 8));
    }

    private static Value season() {
        ReikosWeatherCompat.Season season = ReikosWeatherCompat.season();
        if (season == null) {
            return new Value(0, Component.translatable(KEY + "no_seasons"));
        }
        Component name = ReikosWeatherCompat.seasonName(season.id());
        return new Value(season.number(), season.day() > 0 && season.days() > 0
                ? Component.translatable(KEY + "season_day", name, season.day(), season.days()) : name);
    }

    /** 15 overhead, 1 less every 100 blocks to the storm's edge (rounded, as Reiko's Weather Radar). */
    public static int stormStrength(int edge) {
        return Mth.clamp((int) Math.round(15 - edge / 100.0), 0, 15);
    }

    private static Value stormNear(ServerLevel level, BlockPos pos) {
        ReikosWeatherCompat.Storm storm = ReikosWeatherCompat.nearestStorm(level, pos);
        if (storm == null) {
            return new Value(0, Component.translatable(KEY + (ReikosWeatherCompat.runs(level) ? "no_storms" : "not_here")));
        }
        Component name = ReikosWeatherCompat.weatherName(storm.id());
        return new Value(stormStrength(storm.edge()), storm.edge() == 0 ? Component.translatable(KEY + "storm_overhead", name)
                : Component.translatable(KEY + "storm_away", name, storm.edge()));
    }

    /** °C to the temperature reading: 0 at −10 °C or colder, 1 more every 3 °C, 15 at 35 °C or hotter. */
    public static int temperatureStrength(float celsius) {
        return Float.isNaN(celsius) ? 0 : Mth.clamp(Math.round((celsius + 10.0F) / 3.0F), 0, 15);
    }

    private Value reikos(ServerLevel level, BlockPos pos) {
        ReikosWeatherCompat.Local local = ReikosWeatherCompat.local(level, pos);
        if (local == null) {
            return new Value(0, Component.translatable(KEY + "not_here"));
        }
        return switch (this) {
            case CONDITIONS -> new Value(local.code(), local.label());
            case STORM -> new Value(local.danger(), local.label());
            case WIND -> {
                Component text = ReikosWeatherCompat.windText(level, local);
                yield new Value(Math.round(local.wind() * 15.0F), text != null ? text
                        : Component.translatable(KEY + "percent", local.label(), Math.round(local.wind() * 100)));
            }
            case PRECIPITATION -> new Value(Math.round(local.rainAmount() * 15.0F),
                    Component.translatable(KEY + "percent", local.label(), Math.round(local.rainAmount() * 100)));
            case TEMPERATURE -> {
                float celsius = ReikosWeatherCompat.temperature(level, pos, local);
                yield Float.isNaN(celsius) ? new Value(0, Component.translatable(KEY + "not_here"))
                        : new Value(temperatureStrength(celsius), Component.translatable(KEY + "degrees",
                                Math.round(celsius), Math.round(celsius * 9.0F / 5.0F + 32.0F)));
            }
            default -> new Value(0, Component.empty());
        };
    }
}
