package com.reiko.signaltower.item;

import com.mojang.serialization.Codec;
import com.reiko.signaltower.radio.Frequency;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The Frequency Tuner's favorite frequencies: always exactly {@link #COUNT} slots, where 0 means
 * an empty slot (real frequencies start at 1).
 */
public record TunerPresets(List<Integer> slots) {
    public static final int COUNT = 5;
    public static final TunerPresets EMPTY = new TunerPresets(List.of());

    public static final Codec<TunerPresets> CODEC = Codec.INT.listOf().xmap(TunerPresets::new, TunerPresets::slots);
    public static final StreamCodec<ByteBuf, TunerPresets> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(COUNT)).map(TunerPresets::new, TunerPresets::slots);

    /** Always keeps exactly COUNT slots, each 0 (empty) or a valid frequency, whatever the input. */
    public TunerPresets {
        List<Integer> fixed = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            Integer value = i < slots.size() ? slots.get(i) : null;
            fixed.add(value == null || value <= 0 ? 0 : Frequency.clamp(value));
        }
        slots = List.copyOf(fixed);
    }

    public boolean isEmpty(int index) {
        return slots.get(index) == 0;
    }

    /** The frequency in slot {@code index}, or 0 if it's empty. */
    public int get(int index) {
        return slots.get(index);
    }

    public boolean hasAny() {
        return slots.stream().anyMatch(value -> value != 0);
    }

    public TunerPresets with(int index, int frequency) {
        List<Integer> copy = new ArrayList<>(slots);
        copy.set(index, Frequency.clamp(frequency));
        return new TunerPresets(copy);
    }

    public TunerPresets cleared(int index) {
        List<Integer> copy = new ArrayList<>(slots);
        copy.set(index, 0);
        return new TunerPresets(copy);
    }

    /** The first slot holding {@code frequency}, or -1. */
    public int indexOf(int frequency) {
        return slots.indexOf(frequency);
    }

    /**
     * The favorite after (or, {@code backwards}, before) {@code current} in slot order, skipping
     * empty slots and wrapping around. If {@code current} isn't a favorite, it's the first (or
     * last) one. Empty if there are no favorites.
     */
    public OptionalInt next(int current, boolean backwards) {
        if (!hasAny()) {
            return OptionalInt.empty();
        }
        int start = indexOf(current);
        if (start < 0) {
            start = backwards ? 0 : COUNT - 1; // so the first step lands on the first (or last) slot
        }
        for (int step = 1; step <= COUNT; step++) {
            int index = Math.floorMod(start + (backwards ? -step : step), COUNT);
            if (!isEmpty(index)) {
                return OptionalInt.of(get(index));
            }
        }
        return OptionalInt.empty();
    }
}
