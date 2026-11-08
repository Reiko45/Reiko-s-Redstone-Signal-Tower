package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The Aviation Beacon's rules (v0.44.0): when it's lit, how it blinks, its color, and which tower
 * it belongs to. Everything here works from what both sides know (block states, the world's
 * time and weather), so the client can draw the blinking without the server telling it. Every
 * beacon blinks by the world's game time, so all of them blink together, like real tower lights.
 */
public final class AviationLight {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".beacon_";
    /** How far up (or down) a mast column is followed to find the tower a beacon is on. */
    private static final int MAST_SCAN_LIMIT = 512;

    /** When the beacon is lit. Saved in the block state by name; keep the names. */
    public enum When implements StringRepresentable {
        /** At night, in rain and storms, and always in the Nether and the End. */
        NIGHT("night"),
        ALWAYS("always"),
        /** While the tower it's on is broadcasting: it shows from afar whether the tower is on. */
        SENDING("sending");

        private final String name;

        When(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Component label() {
            return Component.translatable(KEY + "when_" + name);
        }
    }

    /** How the beacon blinks while it's lit. */
    public enum Pattern implements StringRepresentable {
        /** One second on, one second off (30 times a minute). */
        BLINK("blink", 40, 20),
        /** A short flash every 1.5 seconds (40 times a minute), like a strobe. */
        FLASH("flash", 30, 3),
        /** On without blinking. */
        STEADY("steady", 1, 1);

        private final String name;
        private final int period;
        private final int onTicks;

        Pattern(String name, int period, int onTicks) {
            this.name = name;
            this.period = period;
            this.onTicks = onTicks;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** True if the light is on at {@code gameTime} in this pattern (every beacon by the same clock). */
        public boolean onAt(long gameTime) {
            return Math.floorMod(gameTime, (long) period) < onTicks;
        }

        public Component label() {
            return Component.translatable(KEY + "pattern_" + name);
        }
    }

    /** The light's color. */
    public enum LightColor implements StringRepresentable {
        RED("red", 0xFF2A1A),
        WHITE("white", 0xFFF4E8);

        private final String name;
        private final int rgb;

        LightColor(String name, int rgb) {
            this.name = name;
            this.rgb = rgb;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The glow's color, 0xRRGGBB. */
        public int rgb() {
            return rgb;
        }

        public Component label() {
            return Component.translatable(KEY + "color_" + name);
        }
    }

    private AviationLight() {}

    /** True from dusk to dawn: the hours you can sleep in a bed (day time 12,542 to 23,459). */
    public static boolean isNightTime(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= 12542 && time < 23460;
    }

    /** True if it's dark enough for a beacon set to "At night": night, rain or a storm, or a dimension with no day. */
    public static boolean isDark(Level level) {
        return level.dimensionType().hasFixedTime() || isNightTime(level.getDayTime()) || level.isRaining();
    }

    /**
     * True if a beacon set to {@code when} and {@code pattern} is lit at {@code gameTime}, given
     * whether it's {@code dark} and whether the tower it's on is {@code sending}.
     */
    public static boolean isLit(When when, Pattern pattern, long gameTime, boolean dark, boolean sending) {
        boolean active = switch (when) {
            case ALWAYS -> true;
            case NIGHT -> dark;
            case SENDING -> sending;
        };
        return active && pattern.onAt(gameTime);
    }

    /** "Blinks red at night", "Flashes white all the time", "Glows red while its tower sends". */
    public static Component describe(When when, Pattern pattern, LightColor color) {
        return Component.translatable(KEY + "text_" + pattern.getSerializedName(), color.label(), when.label());
    }

    // ---- The tower it's on ----

    /** True for the top of a tower: a full-size transmitter (any kind), repeater or converter. */
    public static boolean isTowerTop(BlockState state) {
        return state.getBlock() instanceof RadioTransmitterBlock && !(state.getBlock() instanceof CompactRadioTransmitterBlock)
                || state.getBlock() instanceof RadioRepeaterBlock;
    }

    /** True if the tower top in {@code state} is broadcasting (its warning light is on). */
    public static boolean isSending(BlockState state) {
        if (state.getBlock() instanceof RadioTransmitterBlock) {
            return state.getValue(RadioTransmitterBlock.POWERED);
        }
        return state.getBlock() instanceof RadioRepeaterBlock && state.getValue(RadioRepeaterBlock.LIT);
    }

    /**
     * The tower a beacon at {@code pos} is on, if any: the tower top it's attached to, or the one
     * on top of the mast column it's attached to (on the mast's side, on top or underneath). Null
     * if it isn't on a tower.
     */
    public static @Nullable BlockPos towerOf(BlockGetter level, BlockPos pos, Direction toSupport) {
        BlockPos support = pos.relative(toSupport);
        BlockState state = level.getBlockState(support);
        if (isTowerTop(state)) {
            return support;
        }
        if (!(state.getBlock() instanceof RadioMastBlock)) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = support.mutable();
        for (int i = 0; i < MAST_SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof RadioMastBlock; i++) {
            cursor.move(Direction.UP);
        }
        return isTowerTop(level.getBlockState(cursor)) ? cursor.immutable() : null;
    }

    /** The translation key part for a tower top's name: "radio_transmitter", "radio_repeater", ... */
    public static String towerName(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
    }
}
