package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.RecorderTape;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Radio Signal Recorder's tape and transport (v0.44.0). It's a full-size transmitter (reach by
 * height, relaying remote presses) that broadcasts a recording instead of the redstone around it:
 * <ul>
 *   <li><b>Recording:</b> after Record it waits for its "Records from" frequency to turn on where it
 *   stands, then keeps every change of what it hears there (0-15), to the tick, until Stop, 30
 *   seconds, or {@value #MAX_CHANGES} changes. The old recording is only replaced once the new one
 *   starts.</li>
 *   <li><b>Playing:</b> it sends the recording on its own frequency, from the start. <b>Once</b>: each
 *   time a redstone signal reaches it (or its masts), and with Play. <b>Loop</b>: over and over while
 *   redstone reaches it; Play plays it once.</li>
 * </ul>
 * A recording is kept as its length and its changes, each {@code tick << 4 | strength}, the first
 * at tick 0. The item keeps it (with the settings) when the block is broken.
 */
public class SignalRecorderBlockEntity extends RadioTransmitterBlockEntity {
    /** The longest recording: 30 seconds. */
    public static final int MAX_TICKS = RecorderTape.MAX_TICKS;
    /** The most changes a recording keeps. */
    public static final int MAX_CHANGES = RecorderTape.MAX_CHANGES;
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    /** What it's doing. Saved and sent by ordinal: only add at the end. */
    public enum Phase {
        IDLE, WAITING, RECORDING, PLAYING;

        public static Phase of(int ordinal) {
            Phase[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    private static final String LISTEN_TAG = "recorder_listen";
    private static final String LOOP_TAG = "recorder_loop";
    private static final String LENGTH_TAG = "recorder_length";
    private static final String CHANGES_TAG = "recorder_changes";
    private static final String PHASE_TAG = "recorder_phase";
    private static final String START_TAG = "recorder_start";
    private static final String TAKING_TAG = "recorder_taking";
    private static final String LAST_TAG = "recorder_last";

    /** The frequency it records (0: none set). */
    private int listenFrequency;
    private boolean loop;
    /** The recording's length in ticks (0: nothing recorded). */
    private int length;
    private int[] changes = new int[0];
    private Phase phase = Phase.IDLE;
    /** When the current phase began (game time). */
    private long phaseStart;
    /** The recording being made (while recording). */
    private final List<Integer> taking = new ArrayList<>();
    /** What it heard at the last tick while recording. */
    private int lastHeard;
    /** What it's broadcasting now (0-15). Not saved: worked out from the phase and the time. */
    private int output;
    /** Whether redstone reached it at the last look (not saved; see {@link #primed}). */
    private boolean powered;
    /** False until it has looked at its redstone once, so loading or placing it while powered doesn't count as a pulse. */
    private boolean primed;

    public SignalRecorderBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SIGNAL_RECORDER_BE.get(), pos, state);
    }

    // ---- What it has ----

    public int getListenFrequency() {
        return listenFrequency;
    }

    public boolean isLoop() {
        return loop;
    }

    public int getLength() {
        return length;
    }

    /** The recording's changes, {@code tick << 4 | strength} (a copy). */
    public int[] getChanges() {
        return changes.clone();
    }

    /** The recording being made, while recording (a copy). */
    public List<Integer> getTaking() {
        return List.copyOf(taking);
    }

    public Phase getPhase() {
        return phase;
    }

    /** Ticks since the current phase began (0 while idle). */
    public int elapsed(long now) {
        return phase == Phase.IDLE ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, now - phaseStart));
    }

    /** What it's broadcasting now. */
    public int getOutput() {
        return output;
    }

    /** True if a redstone signal reaches it, or a mast under it. */
    public boolean isPowered(Level level) {
        return RadioHelper.readTransmitterInput(level, worldPosition) > 0;
    }

    /** The strength a recording with {@code changes} sends at {@code tick}: the last change at or before it. */
    public static int strengthAt(int[] changes, int tick) {
        int strength = 0;
        for (int change : changes) {
            if (change >> 4 > tick) {
                break;
            }
            strength = change & 15;
        }
        return strength;
    }

    /** {@code changes} with only changes inside {@code length}, in order, at most {@value #MAX_CHANGES}. */
    public static int[] clean(List<Integer> changes, int length) {
        int[] kept = changes.stream().mapToInt(Integer::intValue)
                .filter(change -> change >= 0 && change >> 4 < length)
                .sorted()
                .limit(MAX_CHANGES)
                .toArray();
        return kept;
    }

    // ---- Settings ----

    /** Sets what it records from (0: none) and whether it loops. */
    public void setSettings(int listen, boolean loop) {
        this.listenFrequency = listen <= 0 ? 0 : Frequency.clamp(listen);
        this.loop = loop;
        setChanged();
        refresh();
    }

    // ---- Transport ----

    /** Record: waits for its frequency to turn on, then records. Stops anything playing. False if it has nothing to record from. */
    public boolean record(long now) {
        if (listenFrequency <= 0) {
            return false;
        }
        phase = Phase.WAITING;
        phaseStart = now;
        taking.clear();
        setChanged();
        refresh();
        return true;
    }

    /**
     * Stop: ends the recording (keeping what it got, up to now), stops waiting (keeping the old
     * recording) or stops playing. Returns the phase it was in.
     */
    public Phase stop(long now) {
        Phase was = phase;
        switch (phase) {
            case RECORDING -> finish((int) Math.min(MAX_TICKS, now - phaseStart));
            case WAITING, PLAYING -> phase = Phase.IDLE;
            default -> {
            }
        }
        setChanged();
        refresh();
        return was;
    }

    /** Play: plays the recording once from the start. False if there's nothing to play, or it's recording. */
    public boolean play(long now) {
        if (length <= 0 || phase == Phase.WAITING || phase == Phase.RECORDING) {
            return false;
        }
        phase = Phase.PLAYING;
        phaseStart = now;
        setChanged();
        refresh();
        return true;
    }

    /** Clears the recording (not while recording). False if there was nothing to clear. */
    public boolean clear() {
        if (length <= 0 || phase == Phase.WAITING || phase == Phase.RECORDING) {
            return false;
        }
        length = 0;
        changes = new int[0];
        phase = Phase.IDLE;
        setChanged();
        refresh();
        return true;
    }

    private void begin(long now, int heard) {
        phase = Phase.RECORDING;
        phaseStart = now;
        taking.clear();
        taking.add(heard); // tick 0
        lastHeard = heard;
        setChanged();
    }

    private void finish(int ticks) {
        changes = taking.stream().mapToInt(Integer::intValue).toArray();
        length = Math.max(1, Math.min(MAX_TICKS, ticks));
        taking.clear();
        phase = Phase.IDLE;
        setChanged();
    }

    /** What it hears on its "Records from" frequency where it stands, as whoever tuned it. */
    public int heard(ServerLevel level) {
        return listenFrequency <= 0 ? 0 : SignalNetwork.get(level).listen(level, listenFrequency, worldPosition, getTunedBy()).power();
    }

    /** Moves playing on to {@code now}: ends a pass (or starts the next one while looping), and starts a loop while powered. */
    private void advance(long now) {
        if (phase == Phase.PLAYING && now - phaseStart >= length) {
            if (loop && powered && length > 0) {
                phaseStart = now - Math.floorMod(now - phaseStart, (long) length);
            } else {
                phase = Phase.IDLE;
                setChanged();
            }
        }
        if (phase == Phase.IDLE && loop && powered && length > 0) {
            phase = Phase.PLAYING;
            phaseStart = now;
            setChanged();
        }
    }

    /** What it broadcasts at {@code now} in its current phase. */
    private int outputAt(long now) {
        return phase == Phase.PLAYING && length > 0 ? strengthAt(changes, (int) Math.min(length - 1, Math.max(0, now - phaseStart))) : 0;
    }

    /**
     * Looks at its redstone (a pulse plays it in Once mode; in Loop mode it plays while powered and
     * stops when the power goes), works out what it sends now, and tells the network.
     */
    @Override
    public void refresh() {
        if (level instanceof ServerLevel serverLevel) {
            long now = serverLevel.getGameTime();
            boolean in = isPowered(serverLevel);
            if (!primed) {
                primed = true;
                powered = in;
            } else if (in != powered) {
                powered = in;
                if (in && !loop && length > 0 && (phase == Phase.IDLE || phase == Phase.PLAYING)) {
                    phase = Phase.PLAYING; // a pulse: from the start
                    phaseStart = now;
                    setChanged();
                } else if (!in && loop && phase == Phase.PLAYING) {
                    phase = Phase.IDLE;
                    setChanged();
                }
            }
            advance(now);
            output = outputAt(now);
        }
        super.refresh();
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return SignalNetwork.Cause.recorder(worldPosition, getTunedBy());
    }

    // ---- Labels ----

    /** "5.2s, 6 changes" (or "nothing recorded"). */
    public static Component tapeText(int length, int changeCount) {
        if (length <= 0) {
            return Component.translatable(KEY + "recorder_empty");
        }
        return Component.translatable(KEY + "recorder_tape", seconds(length), changeCount);
    }

    /** "5.2" seconds, one decimal (no trailing ".0"). */
    public static String seconds(int ticks) {
        double value = Math.round(ticks / 2.0) / 10.0;
        return value == Math.floor(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    /** "Once on a redstone pulse" or "On a loop while powered". */
    public static Component modeText(boolean loop) {
        return Component.translatable(KEY + (loop ? "recorder_mode_loop" : "recorder_mode_once"));
    }

    // ---- Ticking ----

    public static void recorderTick(Level level, BlockPos pos, BlockState state, SignalRecorderBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level.getGameTime();
        if (!be.primed) {
            be.refresh();
        }
        switch (be.phase) {
            case WAITING -> {
                int heard = be.heard(serverLevel);
                if (heard > 0) {
                    be.begin(now, heard);
                }
            }
            case RECORDING -> {
                int at = (int) (now - be.phaseStart);
                if (at >= MAX_TICKS) {
                    be.finish(MAX_TICKS);
                } else {
                    int heard = be.heard(serverLevel);
                    if (heard != be.lastHeard) {
                        if (be.taking.size() >= MAX_CHANGES) {
                            be.finish(at);
                        } else {
                            be.taking.add(at << 4 | heard);
                            be.lastHeard = heard;
                            be.setChanged();
                        }
                    }
                }
            }
            default -> {
            }
        }
        be.advance(now);
        if (be.outputAt(now) != be.output) {
            be.refresh();
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, be);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(LISTEN_TAG, listenFrequency);
        tag.putBoolean(LOOP_TAG, loop);
        tag.putInt(LENGTH_TAG, length);
        tag.putIntArray(CHANGES_TAG, changes);
        tag.putByte(PHASE_TAG, (byte) phase.ordinal());
        tag.putLong(START_TAG, phaseStart);
        tag.putIntArray(TAKING_TAG, taking.stream().mapToInt(Integer::intValue).toArray());
        tag.putInt(LAST_TAG, lastHeard);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        listenFrequency = Math.max(0, Math.min(Frequency.MAX, tag.getInt(LISTEN_TAG)));
        loop = tag.getBoolean(LOOP_TAG);
        length = Math.max(0, Math.min(MAX_TICKS, tag.getInt(LENGTH_TAG)));
        changes = clean(Arrays.stream(tag.getIntArray(CHANGES_TAG)).boxed().toList(), length);
        phase = Phase.of(tag.getByte(PHASE_TAG));
        phaseStart = tag.getLong(START_TAG);
        taking.clear();
        Arrays.stream(tag.getIntArray(TAKING_TAG)).limit(MAX_CHANGES).forEach(taking::add);
        lastHeard = tag.getInt(LAST_TAG);
        if (phase == Phase.RECORDING && taking.isEmpty()) {
            phase = Phase.IDLE;
        }
    }

    // ---- Item <-> block: it keeps its recording and settings ----

    public RecorderTape tape() {
        return new RecorderTape(listenFrequency, loop, length, Arrays.stream(changes).boxed().toList());
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        RecorderTape tape = componentInput.get(ModRegistry.RECORDER_TAPE.get());
        if (tape != null) {
            listenFrequency = tape.listen() <= 0 ? 0 : Frequency.clamp(tape.listen());
            loop = tape.loop();
            length = Math.max(0, Math.min(MAX_TICKS, tape.length()));
            changes = clean(tape.changes(), length);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.RECORDER_TAPE.get(), tape());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(LISTEN_TAG);
        tag.remove(LOOP_TAG);
        tag.remove(LENGTH_TAG);
        tag.remove(CHANGES_TAG);
    }
}
