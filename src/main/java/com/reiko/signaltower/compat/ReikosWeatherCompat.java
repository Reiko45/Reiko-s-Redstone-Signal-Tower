package com.reiko.signaltower.compat;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Weather;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Reiko's Weather support (v0.53.0). Reiko's Weather ({@code reikos_weather}) replaces the vanilla
 * weather with 26 weathers, seasons and storm cells that drift across the map, so whether it rains
 * depends on where you are. When it's installed this mod reads that weather instead of vanilla's:
 * <ul>
 *   <li>a storm cell over a tower shortens its reach (rain or thunder, as the cell is), and towers
 *   outside the cell aren't affected (see {@link #stormOver});</li>
 *   <li>the Radio Weather Station reads the weather where it stands, and gets extra readings: the
 *   weather code, storm danger, wind, precipitation, temperature, season and the nearest storm,
 *   on the same scales as Reiko's Weather's own Weather Station and Weather Radar.</li>
 * </ul>
 * There's no compile-time dependency: everything goes through reflection, looked up once. If
 * Reiko's Weather isn't installed, or a newer version changed what this reads, it logs that once
 * and everything falls back to vanilla weather. Server side only.
 */
public final class ReikosWeatherCompat {
    public static final String MOD_ID = "reikos_weather";
    private static final String PKG = "com.reiko.weather.";

    /**
     * The weather Reiko's Weather says it is at one spot.
     *
     * @param id        the weather's id ("rain", "supercell")
     * @param code      its 0-15 code (the same as Reiko's Weather Station's Weather reading)
     * @param rain      whether it rains or snows (vanilla's rain flag)
     * @param thunder   whether it's a thunderstorm (vanilla's thunder flag)
     * @param severe    supercell, hurricane, blizzard, sandstorm, ice storm or derecho
     * @param rainAmount how hard it rains, 0 to 1
     * @param wind      how strong the wind is, 0 to 1
     */
    public record Local(String id, int code, boolean rain, boolean thunder, boolean severe, float rainAmount, float wind,
                        Object raw) {
        /** "Heavy Rain", in the reader's language (Reiko's Weather's own name for it). */
        public Component label() {
            return weatherName(id);
        }

        /** Storm danger, as Reiko's Weather Station reads it: 15 thunder or severe, 10 hail or heavy rain, 5 rain, else 0. */
        public int danger() {
            if (thunder || severe) {
                return 15;
            }
            if (id.equals("hail") || id.equals("heavy_rain")) {
                return 10;
            }
            return rain ? 5 : 0;
        }

        /** As {@link Weather}: thunder, rain or clear. */
        public Weather weather() {
            return thunder ? Weather.THUNDER : rain ? Weather.RAIN : Weather.CLEAR;
        }
    }

    /** The nearest storm cell: its weather and how far its edge is (0: overhead). */
    public record Storm(String id, int edge) {}

    /** Reiko's Weather's season: 1 spring, 2 summer, 3 autumn, 4 winter; and the day of it, from 1 (0 if not known: Serene Seasons can't always say). */
    public record Season(int number, String id, int day, int days) {}

    private static boolean checked;
    private static @Nullable Bridge bridge;
    /** For the GameTests: false = act as if it weren't installed; null = as it is. */
    private static @Nullable Boolean forced;

    private record Bridge(Method local, Method activeState, Method localType, Method localProfile,
                          Field profileRain, Field profileWind, Field typeRain, Field typeThunder, Method typeSevere,
                          Method typeCode, Method typeId, Field stateCells, Field stateWind, Field cellType, Field cellRadius,
                          Method cellWeight, Method cellDistance,
                          @Nullable Method seasonInfo, @Nullable Method infoSeason, @Nullable Method infoDay, @Nullable Method infoDays,
                          @Nullable Method seasonId, @Nullable Method ambient, @Nullable Method bloodMoon,
                          @Nullable Method compassFrom, @Nullable Method kmh) {}

    private ReikosWeatherCompat() {}

    /** True if Reiko's Weather is installed (whether or not this could read it). */
    public static boolean installed() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /** True if Reiko's Weather is installed and this can read it. */
    public static boolean available() {
        if (Boolean.FALSE.equals(forced)) {
            return false;
        }
        return bridge() != null;
    }

    /** For the GameTests: {@code false} acts as if it weren't installed, {@code null} goes back to normal. */
    public static void forTests(@Nullable Boolean value) {
        forced = value;
    }

    /** True if Reiko's Weather runs the weather in {@code level} now (the Overworld, with its weather on). */
    public static boolean runs(Level level) {
        return state(level) != null;
    }

    private static @Nullable Bridge bridge() {
        if (!checked) {
            checked = true;
            if (installed()) {
                try {
                    bridge = lookUp();
                    SignalTower.LOGGER.info("Reiko's Weather found: towers and the Radio Weather Station follow its weather");
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    SignalTower.LOGGER.warn("Reiko's Weather is installed but this version can't read it ({}); using vanilla weather", e.toString());
                }
            }
        }
        return bridge;
    }

    private static Bridge lookUp() throws ReflectiveOperationException {
        ClassLoader loader = ReikosWeatherCompat.class.getClassLoader();
        Class<?> manager = Class.forName(PKG + "weather.WeatherManager", true, loader);
        Class<?> local = Class.forName(PKG + "weather.LocalWeather", true, loader);
        Class<?> profile = Class.forName(PKG + "weather.WeatherProfile", true, loader);
        Class<?> type = Class.forName(PKG + "weather.WeatherType", true, loader);
        Class<?> state = Class.forName(PKG + "weather.WeatherState", true, loader);
        Class<?> cell = Class.forName(PKG + "weather.StormCell", true, loader);
        Method seasonInfo = null, infoSeason = null, infoDay = null, infoDays = null, seasonId = null;
        try {
            Class<?> info = Class.forName(PKG + "weather.Seasons$Info", true, loader);
            seasonInfo = manager.getMethod("seasonInfo");
            infoSeason = info.getMethod("season");
            infoDay = info.getMethod("dayOfSeason");
            infoDays = info.getMethod("daysPerSeason");
            seasonId = Class.forName(PKG + "weather.Season", true, loader).getMethod("id");
        } catch (ReflectiveOperationException e) {
            SignalTower.LOGGER.warn("Reiko's Weather: can't read its seasons ({})", e.toString());
        }
        Method ambient = optional(() -> Class.forName(PKG + "temperature.Temperature", true, loader)
                .getMethod("ambient", Level.class, BlockPos.class, local), "temperature");
        Method bloodMoon = optional(() -> Class.forName(PKG + "weather.SkyPhenomena", true, loader)
                .getMethod("bloodMoonNow", Level.class), "blood moons");
        Class<?> wind = optional(() -> Class.forName(PKG + "weather.Wind", true, loader), "wind");
        Method compassFrom = wind == null ? null : optional(() -> wind.getMethod("compassFrom", float.class), "wind");
        Method kmh = wind == null ? null : optional(() -> wind.getMethod("kmh", float.class), "wind");
        return new Bridge(manager.getMethod("local", Level.class, BlockPos.class), manager.getMethod("activeState", Level.class),
                local.getMethod("type"), local.getMethod("profile"),
                profile.getField("rain"), profile.getField("wind"), type.getField("vanillaRain"), type.getField("vanillaThunder"),
                type.getMethod("isSevere"), type.getMethod("signalCode"), type.getMethod("id"),
                state.getField("cells"), state.getField("windAngle"), cell.getField("type"), cell.getField("radius"),
                cell.getMethod("weightAt", double.class, double.class), cell.getMethod("distanceTo", double.class, double.class),
                seasonInfo, infoSeason, infoDay, infoDays, seasonId, ambient, bloodMoon, compassFrom, kmh);
    }

    private interface Lookup<T> {
        T get() throws ReflectiveOperationException;
    }

    private static <T> @Nullable T optional(Lookup<T> lookup, String what) {
        try {
            return lookup.get();
        } catch (ReflectiveOperationException | RuntimeException e) {
            SignalTower.LOGGER.warn("Reiko's Weather: can't read its {} ({})", what, e.toString());
            return null;
        }
    }

    /** Something this reads failed after all: stop using Reiko's Weather (logged once). */
    private static void broke(Throwable e) {
        if (bridge != null) {
            bridge = null;
            SignalTower.LOGGER.warn("Reiko's Weather stopped answering ({}); using vanilla weather from now on", e.toString());
        }
    }

    private static @Nullable Object state(Level level) {
        Bridge b = available() ? bridge : null;
        if (b == null || !(level instanceof ServerLevel)) {
            return null;
        }
        try {
            return b.activeState().invoke(null, level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broke(e);
            return null;
        }
    }

    // ---- What it says ----

    /** The weather at {@code pos}: the world's, or a storm cell's passing over; null if Reiko's Weather doesn't run there. */
    public static @Nullable Local local(Level level, BlockPos pos) {
        Bridge b = available() ? bridge : null;
        if (b == null || !(level instanceof ServerLevel)) {
            return null;
        }
        try {
            Object local = b.local().invoke(null, level, pos);
            if (local == null) {
                return null;
            }
            Object type = b.localType().invoke(local);
            Object profile = b.localProfile().invoke(local);
            return new Local((String) b.typeId().invoke(type), (int) b.typeCode().invoke(type), b.typeRain().getBoolean(type),
                    b.typeThunder().getBoolean(type), (boolean) b.typeSevere().invoke(type),
                    b.profileRain().getFloat(profile), b.profileWind().getFloat(profile), local);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broke(e);
            return null;
        }
    }

    /**
     * The weather a storm cell brings to {@code pos}, if one is over it (its core: where Reiko's
     * Weather says it's the cell's weather): {@link Weather#THUNDER}, {@link Weather#RAIN}, or
     * {@link Weather#CLEAR} for a dry one (a sandstorm). Null if no cell is over it (then it's the
     * world's weather, which Reiko's Weather keeps vanilla's rain and thunder in step with), or if
     * Reiko's Weather doesn't run in {@code level}. Only arithmetic: no chunks are loaded.
     */
    public static @Nullable Weather stormOver(Level level, BlockPos pos) {
        Object state = state(level);
        if (state == null) {
            return null;
        }
        Bridge b = bridge;
        try {
            Object best = null;
            float bestWeight = 0.0F;
            for (Object cell : (List<?>) b.stateCells().get(state)) {
                Object type = b.cellType().get(cell);
                if ("dust_haze".equals(b.typeId().invoke(type))) {
                    continue; // high up: the weather underneath it stays (Reiko's Weather does the same)
                }
                float weight = (float) b.cellWeight().invoke(cell, pos.getX() + 0.5, pos.getZ() + 0.5);
                if (weight > bestWeight) {
                    best = type;
                    bestWeight = weight;
                }
            }
            if (best == null || bestWeight < 0.5F) {
                return null;
            }
            return b.typeThunder().getBoolean(best) ? Weather.THUNDER : b.typeRain().getBoolean(best) ? Weather.RAIN : Weather.CLEAR;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broke(e);
            return null;
        }
    }

    /** The storm cell whose edge is nearest {@code pos} (0: it's overhead), or null if there are none. */
    public static @Nullable Storm nearestStorm(Level level, BlockPos pos) {
        Object state = state(level);
        if (state == null) {
            return null;
        }
        Bridge b = bridge;
        try {
            Storm nearest = null;
            for (Object cell : (List<?>) b.stateCells().get(state)) {
                double distance = (double) b.cellDistance().invoke(cell, pos.getX() + 0.5, pos.getZ() + 0.5);
                int edge = (int) Math.round(Math.max(0, distance - b.cellRadius().getFloat(cell)));
                if (nearest == null || edge < nearest.edge()) {
                    nearest = new Storm((String) b.typeId().invoke(b.cellType().get(cell)), edge);
                }
            }
            return nearest;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broke(e);
            return null;
        }
    }

    /** The air temperature at {@code pos} in °C (biome, season, height, time of day, weather, shelter), or NaN. */
    public static float temperature(Level level, BlockPos pos, @Nullable Local local) {
        Bridge b = available() ? bridge : null;
        if (b == null || b.ambient() == null) {
            return Float.NaN;
        }
        try {
            return (float) b.ambient().invoke(null, level, pos, local == null ? null : local.raw());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Float.NaN;
        }
    }

    /** The season now, or null without seasons (they're off, or not readable). */
    public static @Nullable Season season() {
        Bridge b = available() ? bridge : null;
        if (b == null || b.seasonInfo() == null) {
            return null;
        }
        try {
            Object info = b.seasonInfo().invoke(null);
            Object season = info == null ? null : b.infoSeason().invoke(info);
            if (season == null) {
                return null;
            }
            String id = (String) b.seasonId().invoke(season);
            int day = (int) b.infoDay().invoke(info); // counted from 0 there; -1 if not known
            return new Season(((Enum<?>) season).ordinal() + 1, id, Math.max(0, day + 1), (int) b.infoDays().invoke(info));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** True while a Blood Moon is up (from dusk to dawn). */
    public static boolean bloodMoon(Level level) {
        Bridge b = available() ? bridge : null;
        if (b == null || b.bloodMoon() == null) {
            return false;
        }
        try {
            return (boolean) b.bloodMoon().invoke(null, level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** "28 km/h from the north-east"-style parts: km/h and the compass point it blows from ("ne"); null if not readable. */
    public static @Nullable Component windText(Level level, Local local) {
        Bridge b = available() ? bridge : null;
        Object state = state(level);
        if (b == null || state == null || b.kmh() == null || b.compassFrom() == null) {
            return null;
        }
        try {
            int kmh = (int) b.kmh().invoke(null, local.wind());
            String from = (String) b.compassFrom().invoke(null, b.stateWind().getFloat(state));
            return Component.translatable("message." + SignalTower.MOD_ID + ".station_wind", kmh,
                    Component.translatable("compass." + SignalTower.MOD_ID + "." + from.toLowerCase(Locale.ROOT)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Reiko's Weather's name for a weather ("Supercell"); its own language files have it. */
    public static Component weatherName(String id) {
        return Component.translatable("weather." + MOD_ID + "." + id);
    }

    /** Reiko's Weather's name for a season ("Winter"). */
    public static Component seasonName(String id) {
        return Component.translatable("season." + MOD_ID + "." + id);
    }
}
