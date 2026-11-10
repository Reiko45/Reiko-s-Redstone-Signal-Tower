package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

/**
 * The sounds a Radio Speaker can play (v0.43.0): vanilla ones, and since v0.59.0 three of the
 * mod's own (pager, static, siren); all have subtitles and follow the Blocks volume slider. The doorbell plays two notes ("ding-dong"), a major third apart. Saved by
 * {@link #ordinal()}, so new ones go at the end.
 */
public enum SpeakerSound {
    BELL("bell", () -> SoundEvents.BELL_BLOCK),
    CHIME("chime", () -> SoundEvents.NOTE_BLOCK_CHIME.value()),
    DING("ding", () -> SoundEvents.NOTE_BLOCK_BELL.value()),
    DOORBELL("doorbell", () -> SoundEvents.NOTE_BLOCK_BELL.value()),
    PLING("pling", () -> SoundEvents.NOTE_BLOCK_PLING.value()),
    BASS("bass", () -> SoundEvents.NOTE_BLOCK_BASS.value()),
    BEEP("beep", () -> SoundEvents.NOTE_BLOCK_BIT.value()),
    // v0.59.0: some of the mod's own
    PAGER("pager", () -> com.reiko.signaltower.registry.ModSounds.PAGER_BEEP.get()),
    STATIC("static", () -> com.reiko.signaltower.registry.ModSounds.TUNER_TUNE.get()),
    SIREN("siren", () -> com.reiko.signaltower.registry.ModSounds.STATION_SIREN.get()),
    /** v0.62.0: a steady tone for as long as the frequency is on (played by the client), for Morse. */
    TONE("tone", () -> com.reiko.signaltower.registry.ModSounds.SPEAKER_TONE.get());

    /** The note names from F♯ up, like a note block's. */
    private static final String[] NOTES = {"F♯", "G", "G♯", "A", "A♯", "B", "C", "C♯", "D", "D♯", "E", "F"};
    /** How far apart the doorbell's two notes are, in ticks. */
    public static final int DOORBELL_GAP = 6;
    public static final int DEFAULT_PITCH = 12;

    private final String key;
    private final Supplier<SoundEvent> sound;

    SpeakerSound(String key, Supplier<SoundEvent> sound) {
        this.key = key;
        this.sound = sound;
    }

    public SoundEvent sound() {
        return sound.get();
    }

    /** True for a sound that lasts while the frequency is on (the Tone), rather than ringing when it turns on. */
    public boolean whileOn() {
        return this == TONE;
    }

    public Component label() {
        return Component.translatable("message." + SignalTower.MOD_ID + ".speaker_sound_" + key);
    }

    public static SpeakerSound byId(int id) {
        SpeakerSound[] values = values();
        return id >= 0 && id < values.length ? values[id] : BELL;
    }

    /** A note block's pitch for note 0-24 (12 is the sound's own pitch). */
    public static float pitch(int note) {
        return (float) Math.pow(2.0, (validNote(note) - 12) / 12.0);
    }

    public static int validNote(int note) {
        return Math.max(0, Math.min(24, note));
    }

    /** "F♯4" for note 12, "F♯3" for 0, "C5" for 18. */
    public static Component noteLabel(int note) {
        int n = validNote(note);
        return Component.literal(NOTES[n % 12] + (3 + (n + 6) / 12));
    }

    /** How loud it plays: quiet and normal are heard within 16 blocks, loud within 48. Saved by ordinal. */
    public enum Volume {
        QUIET("quiet", 0.4F),
        NORMAL("normal", 1.0F),
        LOUD("loud", 3.0F);

        private final String key;
        private final float volume;

        Volume(String key, float volume) {
            this.key = key;
            this.volume = volume;
        }

        public float volume() {
            return volume;
        }

        public Component label() {
            return Component.translatable("message." + SignalTower.MOD_ID + ".speaker_volume_" + key);
        }

        public static Volume byId(int id) {
            Volume[] values = values();
            return id >= 0 && id < values.length ? values[id] : NORMAL;
        }
    }
}
