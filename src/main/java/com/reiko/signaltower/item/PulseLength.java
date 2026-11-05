package com.reiko.signaltower.item;

import java.math.BigDecimal;

/** How long a remote's pulse lasts, in game ticks (20 ticks = 1 second). */
public final class PulseLength {
    public static final int MIN_TICKS = 2;
    public static final int MAX_TICKS = 200;
    /** 1 second, the same as a stone button. */
    public static final int DEFAULT_TICKS = 20;

    /** The choices offered in the screens: 0.1s, 0.25s, 0.5s, 1s, 1.5s, 2s, 3s, 5s, 10s. */
    private static final int[] PRESETS = {2, 5, 10, 20, 30, 40, 60, 100, 200};

    private PulseLength() {}

    public static int clamp(int ticks) {
        return Math.max(MIN_TICKS, Math.min(MAX_TICKS, ticks));
    }

    /** The next preset up (or down when {@code backwards}), wrapping around at the ends. */
    public static int next(int ticks, boolean backwards) {
        if (backwards) {
            for (int i = PRESETS.length - 1; i >= 0; i--) {
                if (PRESETS[i] < ticks) return PRESETS[i];
            }
            return PRESETS[PRESETS.length - 1];
        }
        for (int preset : PRESETS) {
            if (preset > ticks) return preset;
        }
        return PRESETS[0];
    }

    /** "0.25s", "1s", "10s". Exact, because every whole number of ticks is a terminating decimal of seconds. */
    public static String format(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString() + "s";
    }
}
