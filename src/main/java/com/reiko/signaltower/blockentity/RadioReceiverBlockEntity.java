package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioInvertedReceiverBlock;
import com.reiko.signaltower.block.RadioLogicReceiverBlock;
import com.reiko.signaltower.block.RadioDelayReceiverBlock;
import com.reiko.signaltower.block.RadioCounterReceiverBlock;
import com.reiko.signaltower.block.RadioSpeakerBlock;
import com.reiko.signaltower.radio.SpeakerSound;
import com.reiko.signaltower.radio.Frequency;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;

/**
 * Listens to the network. It re-scans only when something on its frequency changed (so changes
 * arrive within one tick), when its own frequency changes, or once a second as a fallback.
 * <p>
 * Shared by the Radio Receiver, the Compact Radio Receiver, the Radio Toggle Receiver and the Radio
 * Inverted Receiver and the Radio Logic Receiver. An inverted receiver outputs 15 while it hears
 * nothing and 0 while it hears anything (v0.39.0). A logic receiver (v0.40.0) also listens on a
 * second frequency and combines the two ({@link LogicMode}); it's tracked on both. A delay receiver
 * (v0.41.0) outputs 15 while what it heard {@link #getDelayTicks() delay} ago was on, and stays on
 * for its {@link #getHoldTicks() hold} after that turns off; while a change is under way it checks
 * every tick. Its timings are saved, what's in progress isn't (it starts again from what it hears
 * when loaded or re-tuned). A counter receiver (v0.42.0) counts its frequency's turn-ons and
 * turns on when the count reaches its target: for a 1-second pulse (then it counts again), or until
 * its reset frequency turns on. It can forget the count after a while without a turn-on, and its
 * reset frequency also clears the count; it's tracked on both. Its count is saved. A speaker
 * (v0.43.0) outputs like a plain receiver and plays its sound when its frequency turns on (once, or
 * every second while it stays on). A toggle
 * receiver doesn't pass the signal through: it flips between off and on (full strength) each time
 * its frequency turns on (goes from silent to any strength) where it stands. It starts listening
 * without flipping when it's placed, loaded or re-tuned, so an already-on frequency doesn't count
 * as a turn-on. Its on/off state is saved.
 * <p>
 * On someone else's encrypted frequency (one whose owner and members don't include whoever tuned
 * or placed it) it hears nothing. Becoming able to hear it, or losing that, isn't a turn-on either.
 */
public class RadioReceiverBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private static final String TOGGLED_TAG = "toggled_on";
    private static final String SECOND_TAG = "second_frequency";
    private static final String MODE_TAG = "logic_mode";
    private static final String DELAY_TAG = "delay_ticks";
    private static final String HOLD_TAG = "hold_ticks";
    private static final String TARGET_TAG = "counter_target";
    private static final String LATCH_TAG = "counter_latch";
    private static final String FORGET_TAG = "counter_forget";
    private static final String RESET_TAG = "reset_frequency";
    private static final String COUNT_TAG = "count";
    private static final String COUNTER_ON_TAG = "counter_on";
    private static final String LAST_COUNT_TAG = "counter_last";
    private static final String UNTIL_TAG = "counter_until";
    /** The counts a counter receiver can wait for: 1 to 16, 20, 24, 32, 48 and 64. */
    public static final int[] COUNTS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 20, 24, 32, 48, 64};
    public static final int DEFAULT_COUNT = 3;
    private static final String SPEAKER_TAG = "speaker";
    /** How often a speaker set to repeat plays while its frequency stays on, in ticks. */
    public static final int SPEAKER_REPEAT_TICKS = 20;
    /** How long a counter receiver's pulse lasts when it reaches its count (and doesn't stay on). */
    public static final int COUNTER_PULSE_TICKS = 20;
    /** The delays and holds a delay receiver offers, in ticks: none, 0.5, 1, 2, 3, 5, 10, 30 and 60 seconds. */
    public static final int[] TIMES = {0, 10, 20, 40, 60, 100, 200, 600, 1200};
    /** The most changes a delay receiver keeps waiting (more are merged into the last one). */
    private static final int MAX_PENDING = 256;

    /**
     * How a Radio Logic Receiver combines what it hears on its two frequencies (v0.40.0). Saved by
     * name, sent by ordinal: only add at the end.
     */
    public enum LogicMode {
        /** Both on: the weaker of the two. */
        AND,
        /** Either on: the stronger of the two. */
        OR,
        /** Exactly one on: that one's strength. */
        XOR;

        public static LogicMode of(int ordinal) {
            LogicMode[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        static LogicMode named(String name) {
            for (LogicMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return AND;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The output for strengths {@code a} and {@code b} (0 = silent). */
        public int combine(int a, int b) {
            return switch (this) {
                case AND -> Math.min(a, b);
                case OR -> Math.max(a, b);
                case XOR -> a > 0 && b > 0 ? 0 : Math.max(a, b);
            };
        }
    }

    private long seenRevision = -1;
    private int seenFrequency = -1;
    private int timer;
    /** Toggle receiver: on or off. Saved. */
    private boolean toggledOn;
    /** Toggle receiver: whether it heard its frequency at the last check, and whether that's been set yet. Not saved. */
    private boolean lastHeard;
    private boolean primed;
    /** Whether it was deaf (on someone else's encrypted frequency) at the last check, and who it was tracked as tuned by. Not saved. */
    private boolean wasDeaf;
    private @Nullable UUID trackedTuner;
    /** Logic receiver: its second frequency and mode (saved), and what it last checked on the second (not saved). */
    private int secondFrequency = Frequency.DEFAULT;
    private LogicMode logicMode = LogicMode.AND;
    private long seenRevisionB = -1;
    private int seenSecond = -1;
    /** Delay receiver: its delay and hold in ticks (saved). */
    private int delayTicks = 20;
    private int holdTicks;
    /** Delay receiver, not saved: the changes of what it hears still on their way, the last one queued, the delayed state, and its output. */
    private final ArrayDeque<long[]> pending = new ArrayDeque<>();
    private boolean lastQueued;
    private boolean delayedOn;
    private long delayedOffSince;
    private boolean delayOutputOn;
    /** Counter receiver: its target, whether it stays on, when it forgets and its reset frequency (0: none). Saved. */
    private int counterTarget = DEFAULT_COUNT;
    private boolean counterLatch;
    private int forgetTicks;
    private int resetFrequency;
    /** Counter receiver: the count, whether it's on, when it last counted and when its pulse ends (game times). Saved. */
    private int count;
    private boolean counterOn;
    private long lastCountAt;
    private long counterOnUntil;
    /** Speaker: its sound, note (0-24), volume and whether it repeats while on. Saved (packed, see {@link #packSpeaker}). */
    private SpeakerSound speakerSound = SpeakerSound.BELL;
    private int speakerNote = SpeakerSound.DEFAULT_PITCH;
    private SpeakerSound.Volume speakerVolume = SpeakerSound.Volume.NORMAL;
    private boolean speakerRepeat;
    /** Speaker: when it plays again while on, when the doorbell's second note is due (-1: none), and how many notes it has played (not saved; for the GameTests). */
    private long nextRingAt;
    private long secondNoteAt = -1;
    private int notesPlayed;
    /** Counter receiver: whether it heard its reset frequency at the last check (not saved). */
    private boolean lastReset;
    /** Logic receiver: what it heard on each frequency at the last check (not saved; for the screen). A counter's second is its reset frequency. */
    private int heardA;
    private int heardB;
    /** v0.58.0: what it heard last (not saved): computers get an event when it changes. */
    private int computerHeard = -1;

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_RECEIVER_BE.get(), pos, state);
    }

    /** True if this is a Radio Toggle Receiver. */
    public boolean isToggle() {
        return getBlockState().getBlock() instanceof RadioToggleReceiverBlock;
    }

    /** True if this is a Radio Inverted Receiver. */
    public boolean isInverted() {
        return getBlockState().getBlock() instanceof RadioInvertedReceiverBlock;
    }

    /** True if this is a Radio Delay Receiver. */
    public boolean isDelay() {
        return getBlockState().getBlock() instanceof RadioDelayReceiverBlock;
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public int getHoldTicks() {
        return holdTicks;
    }

    /** {@code ticks} if it's one of {@link #TIMES}, else 0. */
    public static int validTime(int ticks) {
        for (int time : TIMES) {
            if (time == ticks) {
                return ticks;
            }
        }
        return 0;
    }

    /** A delay receiver's delay and hold (each one of {@link #TIMES}; others become 0). What's under way starts over. */
    public void setTiming(int delay, int hold) {
        int d = validTime(delay);
        int h = validTime(hold);
        if (d != delayTicks || h != holdTicks) {
            delayTicks = d;
            holdTicks = h;
            primed = false;
            setChanged();
        }
    }

    /** "none", "0.5s", "2s", "60s". */
    public static net.minecraft.network.chat.Component timeLabel(int ticks) {
        if (ticks <= 0) {
            return net.minecraft.network.chat.Component.translatable("message." + com.reiko.signaltower.SignalTower.MOD_ID + ".time_none");
        }
        String seconds = ticks % 20 == 0 ? Integer.toString(ticks / 20) : Double.toString(ticks / 20.0);
        return net.minecraft.network.chat.Component.translatable("message." + com.reiko.signaltower.SignalTower.MOD_ID + ".time_seconds", seconds);
    }

    /** A time still to wait, in seconds to a tenth, rounded up ("2.4s" for 47 ticks). */
    public static net.minecraft.network.chat.Component countdownLabel(long ticks) {
        long tenths = Math.max(1, (ticks + 1) / 2);
        String seconds = tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
        return net.minecraft.network.chat.Component.translatable("message." + com.reiko.signaltower.SignalTower.MOD_ID + ".time_seconds", seconds);
    }

    /** True while a delay receiver has a change under way (it then checks every tick). */
    private boolean timing() {
        return isDelay() && (!pending.isEmpty() || delayOutputOn && !delayedOn);
    }

    /**
     * A delay receiver's next change, in ticks: positive if it turns on then, negative if it turns
     * off then, 0 if none is under way.
     */
    public long changeIn(long now) {
        if (!isDelay()) {
            return 0;
        }
        if (delayOutputOn && !delayedOn) {
            return -Math.max(1, delayedOffSince + holdTicks - now);
        }
        for (long[] change : pending) {
            boolean on = change[1] != 0;
            if (on && !delayOutputOn) {
                return Math.max(1, change[0] - now);
            }
            if (!on && delayOutputOn) {
                return -Math.max(1, change[0] + holdTicks - now);
            }
        }
        return 0;
    }

    /** A delay receiver's output: on (15) or off. */
    public boolean isDelayOn() {
        return delayOutputOn;
    }

    /** What a delay receiver outputs at {@code now} for a heard strength (see the class comment). */
    private int delayOutput(long now, int heard) {
        boolean on = heard > 0;
        if (!primed) {
            // Placed, loaded or re-tuned: start from what it hears now, with nothing under way.
            primed = true;
            pending.clear();
            lastQueued = on;
            delayedOn = on;
            delayOutputOn = on;
            delayedOffSince = now;
            return on ? 15 : 0;
        }
        if (on != lastQueued) {
            lastQueued = on;
            if (delayTicks == 0) {
                setDelayed(on, now);
            } else if (pending.size() >= MAX_PENDING) {
                pending.peekLast()[1] = on ? 1 : 0; // too many flickers: the last one still lands on the right state
            } else {
                pending.addLast(new long[]{now + delayTicks, on ? 1 : 0});
            }
        }
        while (!pending.isEmpty() && pending.peekFirst()[0] <= now) {
            setDelayed(pending.pollFirst()[1] != 0, now);
        }
        if (delayedOn) {
            delayOutputOn = true;
        } else if (delayOutputOn && now - delayedOffSince >= holdTicks) {
            delayOutputOn = false;
        }
        return delayOutputOn ? 15 : 0;
    }

    private void setDelayed(boolean on, long now) {
        if (on != delayedOn) {
            delayedOn = on;
            if (!on) {
                delayedOffSince = now;
            }
        }
    }

    // ---- Speaker (v0.43.0) ----

    /** True if this is a Radio Speaker. */
    public boolean isSpeaker() {
        return getBlockState().getBlock() instanceof RadioSpeakerBlock;
    }

    public SpeakerSound getSpeakerSound() {
        return speakerSound;
    }

    public int getSpeakerNote() {
        return speakerNote;
    }

    public SpeakerSound.Volume getSpeakerVolume() {
        return speakerVolume;
    }

    public boolean isSpeakerRepeat() {
        return speakerRepeat;
    }

    /** How many notes it has played since it was loaded (the doorbell plays two per ring). */
    public int getNotesPlayed() {
        return notesPlayed;
    }

    public void setSpeaker(SpeakerSound sound, int note, SpeakerSound.Volume volume, boolean repeat) {
        speakerSound = sound;
        speakerNote = SpeakerSound.validNote(note);
        speakerVolume = volume;
        speakerRepeat = repeat;
        setChanged();
    }

    /** Its settings in one number: sound + 16 × volume + 64 × repeat + 128 × note (the item's {@code speaker} component). */
    public static int packSpeaker(SpeakerSound sound, int note, SpeakerSound.Volume volume, boolean repeat) {
        return sound.ordinal() | volume.ordinal() << 4 | (repeat ? 1 : 0) << 6 | SpeakerSound.validNote(note) << 7;
    }

    public int packedSpeaker() {
        return packSpeaker(speakerSound, speakerNote, speakerVolume, speakerRepeat);
    }

    private void unpackSpeaker(int packed) {
        speakerSound = SpeakerSound.byId(packed & 15);
        speakerVolume = SpeakerSound.Volume.byId(packed >> 4 & 3);
        speakerRepeat = (packed >> 6 & 1) == 1;
        speakerNote = SpeakerSound.validNote(packed >> 7);
    }

    /** "Bell, F♯4, normal, once" for its settings. */
    public static net.minecraft.network.chat.Component speakerText(int packed) {
        return net.minecraft.network.chat.Component.translatable("message." + com.reiko.signaltower.SignalTower.MOD_ID
                        + (((packed >> 6) & 1) == 1 ? ".speaker_text_repeat" : ".speaker_text_once"),
                SpeakerSound.byId(packed & 15).label(), SpeakerSound.noteLabel(packed >> 7), SpeakerSound.Volume.byId(packed >> 4 & 3).label());
    }

    /** Plays a note of its sound at its note (the doorbell's first note a major third up), with a note particle. */
    private void playNote(ServerLevel level, boolean high) {
        float pitch = SpeakerSound.pitch(speakerNote) * (high ? 1.26F : 1.0F);
        level.playSound(null, worldPosition, speakerSound.sound(), net.minecraft.sounds.SoundSource.BLOCKS,
                speakerVolume.volume(), pitch);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                worldPosition.getZ() + 0.5, 0, speakerNote / 24.0, 0, 0, 1);
        notesPlayed++;
    }

    /** Starts a ring: one note, or the doorbell's first with its second due a moment later. */
    private void ring(ServerLevel level, long now) {
        playNote(level, speakerSound == SpeakerSound.DOORBELL);
        if (speakerSound == SpeakerSound.DOORBELL) {
            secondNoteAt = now + SpeakerSound.DOORBELL_GAP;
        }
        nextRingAt = now + SPEAKER_REPEAT_TICKS;
    }

    /** A speaker's sound for this check: a ring when its frequency turns on, again every second while on if it repeats, and the doorbell's second note. */
    private void speakerTick(ServerLevel level, long now, int heard) {
        boolean hearing = heard > 0;
        if (!primed) {
            // Placed, loaded or re-tuned: whatever is on now isn't a turn-on.
            primed = true;
        } else if (hearing && !lastHeard) {
            ring(level, now);
        } else if (hearing && speakerRepeat && now >= nextRingAt) {
            ring(level, now);
        }
        lastHeard = hearing;
        if (secondNoteAt >= 0 && now >= secondNoteAt) {
            secondNoteAt = -1;
            playNote(level, false);
        }
    }

    /** True while a speaker has a note to come (repeating while on, or the doorbell's second), so it checks every tick. */
    private boolean speakerTiming() {
        return isSpeaker() && (secondNoteAt >= 0 || speakerRepeat && lastHeard);
    }

    // ---- Counter receiver (v0.42.0) ----

    /** True if this is a Radio Counter Receiver. */
    public boolean isCounter() {
        return getBlockState().getBlock() instanceof RadioCounterReceiverBlock;
    }

    public int getCount() {
        return count;
    }

    public int getCounterTarget() {
        return counterTarget;
    }

    /** True if it stays on when it reaches its count (until reset), false if it pulses and counts again. */
    public boolean isCounterLatch() {
        return counterLatch;
    }

    public int getForgetTicks() {
        return forgetTicks;
    }

    /** Its reset frequency, 0 if none. */
    public int getResetFrequency() {
        return resetFrequency;
    }

    public boolean isCounterOn() {
        return counterOn;
    }

    /** {@code target} if it's one of {@link #COUNTS}, else the default (3). */
    public static int validCount(int target) {
        for (int allowed : COUNTS) {
            if (allowed == target) {
                return target;
            }
        }
        return DEFAULT_COUNT;
    }

    /**
     * A counter receiver's settings (values it doesn't offer become the defaults; a reset frequency
     * below 1 means none). Changing any of them starts the count over, switched off.
     */
    public void setCounter(int target, boolean latch, int forget, int reset) {
        int t = validCount(target);
        int f = validTime(forget);
        int r = reset <= 0 ? 0 : Frequency.clamp(reset);
        if (t != counterTarget || latch != counterLatch || f != forgetTicks || r != resetFrequency) {
            counterTarget = t;
            counterLatch = latch;
            forgetTicks = f;
            resetFrequency = r;
            seenRevisionB = -1;
            primed = false;
            resetCount();
        }
    }

    /** Back to 0, switched off (it checks again on its next tick). */
    public void resetCount() {
        count = 0;
        counterOn = false;
        seenRevision = -1;
        setChanged();
    }

    /** Ticks until a counter receiver forgets its count, or 0 if it won't (nothing counted, no forget time, or on until reset). */
    public long forgetIn(long now) {
        if (!isCounter() || count == 0 || forgetTicks == 0 || counterLatch && counterOn) {
            return 0;
        }
        return Math.max(1, lastCountAt + forgetTicks - now);
    }

    /** True while a counter receiver has something to time (a pulse to end, a count to forget), so it checks every tick. */
    private boolean counterTiming() {
        return isCounter() && (counterOn && !counterLatch || count > 0 && forgetTicks > 0 && !(counterLatch && counterOn));
    }

    /** What a counter receiver outputs at {@code now}, hearing {@code heard} and {@code reset} on its two frequencies. */
    private int counterOutput(long now, int heard, int reset) {
        boolean hearing = heard > 0;
        boolean resetting = reset > 0;
        if (!primed) {
            // Placed, loaded or re-tuned: whatever is on now isn't a turn-on.
            primed = true;
        } else {
            if (resetting && !lastReset) {
                resetCount();
            }
            if (hearing && !lastHeard) {
                countTurnOn(now);
            }
        }
        lastHeard = hearing;
        lastReset = resetting;
        if (count > 0 && forgetTicks > 0 && !(counterLatch && counterOn) && now - lastCountAt >= forgetTicks) {
            count = 0;
            setChanged();
        }
        if (counterOn && !counterLatch && now >= counterOnUntil) {
            counterOn = false;
            setChanged();
        }
        return counterOn ? 15 : 0;
    }

    private void countTurnOn(long now) {
        if (counterLatch && counterOn) {
            return; // reached and staying on: it waits for a reset
        }
        if (count > 0 && forgetTicks > 0 && now - lastCountAt >= forgetTicks) {
            count = 0;
        }
        count++;
        lastCountAt = now;
        if (count >= counterTarget) {
            counterOn = true;
            if (counterLatch) {
                count = counterTarget;
            } else {
                count = 0;
                counterOnUntil = now + COUNTER_PULSE_TICKS;
            }
        }
        setChanged();
    }

    /** "on at 3 turn-ons, then a 1s pulse" (and "; forgets after 2s"). */
    public static net.minecraft.network.chat.Component counterText(int target, boolean latch, int forget) {
        String key = "message." + com.reiko.signaltower.SignalTower.MOD_ID + ".";
        net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.translatable(
                key + (latch ? "counter_text_stay" : "counter_text_pulse"), validCount(target));
        if (validTime(forget) > 0) {
            text.append(net.minecraft.network.chat.Component.translatable(key + "counter_text_forget", timeLabel(validTime(forget))));
        }
        return text;
    }

    /** True if this is a Radio Logic Receiver. */
    public boolean isLogic() {
        return getBlockState().getBlock() instanceof RadioLogicReceiverBlock;
    }

    /** A logic receiver's second frequency. */
    public int getSecondFrequency() {
        return secondFrequency;
    }

    public LogicMode getLogicMode() {
        return logicMode;
    }

    /** A logic receiver's two frequencies and mode at once (the first one through {@link #setFrequency}). */
    public void setLogic(int first, int second, LogicMode mode) {
        int clamped = Frequency.clamp(second);
        if (clamped != secondFrequency || mode != logicMode) {
            secondFrequency = clamped;
            logicMode = mode;
            seenRevisionB = -1;
            setChanged();
        }
        setFrequency(first);
    }

    /** What a logic receiver heard on its first and second frequency at its last check. */
    public int heardFirst() {
        return heardA;
    }

    public int heardSecond() {
        return heardB;
    }

    /**
     * The frequency this receiver can't hear because it's someone else's encrypted one (whoever tuned
     * or placed it isn't a member): its own, or a logic receiver's second; -1 if it hears both.
     */
    public int deafFrequency(Level level) {
        if (!FrequencyClaims.mayHear(level, getFrequency(), getTunedBy())) {
            return getFrequency();
        }
        if (isCounter()) {
            return resetFrequency > 0 && !FrequencyClaims.mayHear(level, resetFrequency, getTunedBy()) ? resetFrequency : -1;
        }
        return isLogic() && !FrequencyClaims.mayHear(level, secondFrequency, getTunedBy()) ? secondFrequency : -1;
    }

    /** A toggle receiver's state: true if it's switched on. Always false for the other receivers. */
    public boolean isToggledOn() {
        return toggledOn;
    }

    @Override
    protected void onFrequencyChanged() {
        seenRevision = -1;
        primed = false; // the new frequency's current state isn't a turn-on
    }

    @Override
    protected void onTunedByChanged() {
        seenRevision = -1; // check again at once: it may hear an encrypted frequency now, or not any more
    }

    /** Broken, or its chunk unloaded: stop counting it as a listener. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).untrackReceiver(worldPosition);
        }
    }

    /**
     * What to output for a heard strength: the same strength for a plain receiver; for a toggle
     * receiver, 15 or 0, flipping on each turn-on; for an inverted receiver, 15 while silent and 0
     * while hearing anything.
     */
    private int outputFor(int heard) {
        if (isInverted()) {
            return heard > 0 ? 0 : 15;
        }
        if (!isToggle()) {
            return heard;
        }
        boolean hearing = heard > 0;
        if (!primed) {
            primed = true;
        } else if (hearing && !lastHeard) {
            toggledOn = !toggledOn;
            setChanged();
        }
        lastHeard = hearing;
        return toggledOn ? 15 : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        int frequency = be.getFrequency();
        long revision = network.revision(frequency);
        boolean logic = be.isLogic();
        boolean counter = be.isCounter();
        // A logic receiver's second frequency, or a counter's reset frequency (v0.42.0): -1 if none.
        int second = logic ? be.secondFrequency : counter && be.resetFrequency > 0 ? be.resetFrequency : -1;
        long revisionB = second > 0 ? network.revision(second) : 0;

        boolean changed = revision != be.seenRevision || frequency != be.seenFrequency
                || second != be.seenSecond || second > 0 && revisionB != be.seenRevisionB
                || be.timing() || be.isDelay() && !be.primed || be.counterTiming() || be.speakerTiming();
        if (!changed && be.timer-- > 0) {
            return;
        }
        be.timer = RECHECK_INTERVAL;
        be.seenRevision = revision;
        be.seenRevisionB = revisionB;
        UUID tunedBy = be.getTunedBy();
        if (frequency != be.seenFrequency || second != be.seenSecond || !Objects.equals(tunedBy, be.trackedTuner)) {
            network.trackReceiver(pos, frequency, tunedBy, second); // first tick after loading, or just re-tuned
            be.trackedTuner = tunedBy;
        }
        be.seenFrequency = frequency;
        be.seenSecond = second;

        boolean deaf = !FrequencyClaims.mayHear(serverLevel, frequency, tunedBy);
        if (deaf != be.wasDeaf) {
            be.wasDeaf = deaf;
            be.primed = false; // starting or stopping hearing an encrypted frequency isn't a turn-on
        }
        int heard = deaf ? 0 : network.listen(serverLevel, frequency, pos).power();
        if (heard != be.computerHeard) {
            be.computerHeard = heard; // v0.58.0: a "radio_signal" event for computers on it
            com.reiko.signaltower.compat.RadioComputer.signalChanged(serverLevel, pos, frequency, heard);
        }
        int power;
        if (logic) {
            boolean deafB = !FrequencyClaims.mayHear(serverLevel, second, tunedBy);
            be.heardA = heard;
            be.heardB = deafB ? 0 : network.listen(serverLevel, second, pos).power();
            power = be.logicMode.combine(be.heardA, be.heardB);
        } else if (counter) {
            boolean deafB = second > 0 && !FrequencyClaims.mayHear(serverLevel, second, tunedBy);
            be.heardA = heard;
            be.heardB = second > 0 && !deafB ? network.listen(serverLevel, second, pos).power() : 0;
            power = be.counterOutput(serverLevel.getGameTime(), heard, be.heardB);
        } else if (be.isDelay()) {
            power = be.delayOutput(serverLevel.getGameTime(), heard);
        } else if (be.isSpeaker()) {
            be.heardA = heard;
            be.speakerTick(serverLevel, serverLevel.getGameTime(), heard);
            power = heard;
        } else {
            power = be.outputFor(heard);
        }
        if (state.getValue(RadioReceiverBlock.POWER) != power) {
            // UPDATE_ALL also notifies neighbors, so wires and lamps next to us react right away.
            BlockState newState = state.setValue(RadioReceiverBlock.POWER, power);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            if (newState.getBlock() instanceof RadioReceiverBlock receiver) {
                receiver.afterOutputChanged(serverLevel, pos, newState);
            }
        }
    }

    // ---- Saving (the toggle receiver's state, the logic receiver's second frequency and mode) ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (toggledOn) {
            tag.putBoolean(TOGGLED_TAG, true);
        }
        if (isLogic()) {
            tag.putInt(SECOND_TAG, secondFrequency);
            tag.putString(MODE_TAG, logicMode.key());
        }
        if (isDelay()) {
            tag.putInt(DELAY_TAG, delayTicks);
            tag.putInt(HOLD_TAG, holdTicks);
        }
        if (isSpeaker()) {
            tag.putInt(SPEAKER_TAG, packedSpeaker());
        }
        if (isCounter()) {
            tag.putInt(TARGET_TAG, counterTarget);
            tag.putBoolean(LATCH_TAG, counterLatch);
            tag.putInt(FORGET_TAG, forgetTicks);
            tag.putInt(RESET_TAG, resetFrequency);
            tag.putInt(COUNT_TAG, count);
            tag.putBoolean(COUNTER_ON_TAG, counterOn);
            tag.putLong(LAST_COUNT_TAG, lastCountAt);
            tag.putLong(UNTIL_TAG, counterOnUntil);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        toggledOn = tag.getBoolean(TOGGLED_TAG);
        secondFrequency = tag.contains(SECOND_TAG) ? Frequency.clamp(tag.getInt(SECOND_TAG)) : Frequency.DEFAULT;
        logicMode = LogicMode.named(tag.getString(MODE_TAG));
        seenRevisionB = -1;
        delayTicks = tag.contains(DELAY_TAG) ? validTime(tag.getInt(DELAY_TAG)) : 20;
        holdTicks = validTime(tag.getInt(HOLD_TAG));
        counterTarget = tag.contains(TARGET_TAG) ? validCount(tag.getInt(TARGET_TAG)) : DEFAULT_COUNT;
        counterLatch = tag.getBoolean(LATCH_TAG);
        forgetTicks = validTime(tag.getInt(FORGET_TAG));
        int reset = tag.getInt(RESET_TAG);
        resetFrequency = reset <= 0 ? 0 : Frequency.clamp(reset);
        count = Math.max(0, Math.min(counterTarget, tag.getInt(COUNT_TAG)));
        counterOn = tag.getBoolean(COUNTER_ON_TAG);
        lastCountAt = tag.getLong(LAST_COUNT_TAG);
        counterOnUntil = tag.getLong(UNTIL_TAG);
        if (tag.contains(SPEAKER_TAG)) {
            unpackSpeaker(tag.getInt(SPEAKER_TAG));
        }
        primed = false;
    }

    // ---- Item <-> block: a logic receiver keeps its second frequency and mode ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer second = componentInput.get(ModRegistry.SECOND_FREQUENCY.get());
        if (second != null) {
            secondFrequency = Frequency.clamp(second);
        }
        Integer mode = componentInput.get(ModRegistry.LOGIC_MODE.get());
        if (mode != null) {
            logicMode = LogicMode.of(mode);
        }
        Integer delay = componentInput.get(ModRegistry.DELAY_TICKS.get());
        if (delay != null) {
            delayTicks = validTime(delay);
        }
        Integer hold = componentInput.get(ModRegistry.HOLD_TICKS.get());
        if (hold != null) {
            holdTicks = validTime(hold);
        }
        Integer speaker = componentInput.get(ModRegistry.SPEAKER.get());
        if (speaker != null) {
            unpackSpeaker(speaker);
        }
        Integer target = componentInput.get(ModRegistry.COUNTER_TARGET.get());
        if (target != null) {
            counterTarget = validCount(target);
        }
        Integer latch = componentInput.get(ModRegistry.COUNTER_LATCH.get());
        if (latch != null) {
            counterLatch = latch == 1;
        }
        Integer forget = componentInput.get(ModRegistry.COUNTER_FORGET.get());
        if (forget != null) {
            forgetTicks = validTime(forget);
        }
        Integer reset = componentInput.get(ModRegistry.RESET_FREQUENCY.get());
        if (reset != null) {
            resetFrequency = reset <= 0 ? 0 : Frequency.clamp(reset);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (isLogic()) {
            components.set(ModRegistry.SECOND_FREQUENCY.get(), secondFrequency);
            components.set(ModRegistry.LOGIC_MODE.get(), logicMode.ordinal());
        }
        if (isDelay()) {
            components.set(ModRegistry.DELAY_TICKS.get(), delayTicks);
            components.set(ModRegistry.HOLD_TICKS.get(), holdTicks);
        }
        if (isSpeaker()) {
            components.set(ModRegistry.SPEAKER.get(), packedSpeaker());
        }
        if (isCounter()) {
            components.set(ModRegistry.COUNTER_TARGET.get(), counterTarget);
            components.set(ModRegistry.COUNTER_LATCH.get(), counterLatch ? 1 : 0);
            components.set(ModRegistry.COUNTER_FORGET.get(), forgetTicks);
            components.set(ModRegistry.RESET_FREQUENCY.get(), resetFrequency);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(SECOND_TAG);
        tag.remove(MODE_TAG);
        tag.remove(DELAY_TAG);
        tag.remove(HOLD_TAG);
        tag.remove(SPEAKER_TAG);
        tag.remove(TARGET_TAG);
        tag.remove(LATCH_TAG);
        tag.remove(FORGET_TAG);
        tag.remove(RESET_TAG);
    }
}
