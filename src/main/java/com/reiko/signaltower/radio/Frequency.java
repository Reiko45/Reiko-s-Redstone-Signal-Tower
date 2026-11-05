package com.reiko.signaltower.radio;

/** Frequencies are whole numbers from {@link #MIN} to {@link #MAX}: nearly a billion channels. */
public final class Frequency {
    public static final int MIN = 1;
    public static final int MAX = 999_999_999;
    public static final int DEFAULT = 1;
    /** Digits needed to type {@link #MAX}. */
    public static final int MAX_DIGITS = 9;

    private Frequency() {}

    public static int clamp(long frequency) {
        return (int) Math.max(MIN, Math.min(MAX, frequency));
    }
}
