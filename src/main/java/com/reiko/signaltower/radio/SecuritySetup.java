package com.reiko.signaltower.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Security Console's settings (v0.68.0), kept on its item too: up to {@link #ZONES} zones (a name,
 * a frequency, 0 for none, and a {@link Mode}), the alarm frequency (0 for none), and flags: the
 * entry/exit delay ({@link #DELAYS} index, bits 0–3) and {@link #NIGHT} (arm at dusk, disarm at dawn).
 */
public record SecuritySetup(List<String> names, List<Integer> frequencies, List<Integer> modes, int alarmFrequency, int flags) {
    public static final int ZONES = 8;
    public static final int NAME_LENGTH = 16;
    /** The delays to pick from, in seconds: after arming, and after a Delayed zone trips. */
    public static final int[] DELAYS = {5, 10, 20, 30};
    public static final int DEFAULT_DELAY = 1;
    public static final int DELAY_MASK = 15;
    public static final int NIGHT = 16;

    /** What a zone does when its frequency turns on. Sent and saved by ordinal: only add at the end. */
    public enum Mode {
        /** While armed: the alarm, at once. */
        INSTANT,
        /** While armed: the entry delay, then the alarm unless it's disarmed. */
        DELAYED,
        /** Always: the alarm, armed or not. */
        ALWAYS;

        public static Mode of(int ordinal) {
            Mode[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final SecuritySetup EMPTY = new SecuritySetup(List.of(), List.of(), List.of(), 0, DEFAULT_DELAY).normalized();

    public static final Codec<SecuritySetup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(SecuritySetup::names),
            Codec.INT.listOf().optionalFieldOf("frequencies", List.of()).forGetter(SecuritySetup::frequencies),
            Codec.INT.listOf().optionalFieldOf("modes", List.of()).forGetter(SecuritySetup::modes),
            Codec.INT.optionalFieldOf("alarm", 0).forGetter(SecuritySetup::alarmFrequency),
            Codec.INT.optionalFieldOf("flags", DEFAULT_DELAY).forGetter(SecuritySetup::flags)
    ).apply(instance, SecuritySetup::new));

    public static final StreamCodec<ByteBuf, SecuritySetup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(ZONES)), SecuritySetup::names,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(ZONES)), SecuritySetup::frequencies,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(ZONES)), SecuritySetup::modes,
            ByteBufCodecs.VAR_INT, SecuritySetup::alarmFrequency,
            ByteBufCodecs.VAR_INT, SecuritySetup::flags,
            (names, frequencies, modes, alarm, flags) -> new SecuritySetup(names, frequencies, modes, alarm, flags).normalized());

    /** Exactly {@link #ZONES} of each, names trimmed and cut short, frequencies in range (or 0), known modes and flags. */
    public SecuritySetup normalized() {
        List<String> n = new ArrayList<>(ZONES);
        List<Integer> f = new ArrayList<>(ZONES);
        List<Integer> m = new ArrayList<>(ZONES);
        for (int i = 0; i < ZONES; i++) {
            String name = i < names.size() && names.get(i) != null ? names.get(i).strip() : "";
            n.add(name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name);
            int frequency = i < frequencies.size() && frequencies.get(i) != null ? frequencies.get(i) : 0;
            f.add(frequency <= 0 ? 0 : Frequency.clamp(frequency));
            m.add(Mode.of(i < modes.size() && modes.get(i) != null ? modes.get(i) : 0).ordinal());
        }
        int delay = flags & DELAY_MASK;
        int cleanFlags = (delay < DELAYS.length ? delay : DEFAULT_DELAY) | flags & NIGHT;
        return new SecuritySetup(List.copyOf(n), List.copyOf(f), List.copyOf(m),
                alarmFrequency <= 0 ? 0 : Frequency.clamp(alarmFrequency), cleanFlags);
    }

    public String name(int zone) {
        return names.get(zone);
    }

    public int frequency(int zone) {
        return frequencies.get(zone);
    }

    public Mode mode(int zone) {
        return Mode.of(modes.get(zone));
    }

    /** The zone's name, or "Zone 3" if it has none. */
    public String label(int zone) {
        String name = name(zone);
        return name.isEmpty() ? "Zone " + (zone + 1) : name;
    }

    public int delayIndex() {
        return flags & DELAY_MASK;
    }

    public int delaySeconds() {
        return DELAYS[Math.min(DELAYS.length - 1, delayIndex())];
    }

    public boolean night() {
        return (flags & NIGHT) != 0;
    }

    /** How many zones have a frequency. */
    public int zonesInUse() {
        int count = 0;
        for (int frequency : frequencies) {
            if (frequency > 0) {
                count++;
            }
        }
        return count;
    }

    /** True if it's the default (nothing set but the delay), so the item needn't keep it. */
    public boolean isEmpty() {
        return equals(EMPTY);
    }
}
