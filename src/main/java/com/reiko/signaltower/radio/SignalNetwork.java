package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Every transmitter in one dimension, saved with the world.
 * <p>
 * Transmitters stay listed while their chunk is unloaded, so a receiver keeps getting the last
 * signal a far-away transmitter sent. Each frequency has a revision number that goes up whenever
 * anything on that frequency changes, so receivers only re-scan when they need to.
 * <p>
 * Remote Controls aren't blocks, so their signals are kept separately: a short <b>pulse</b> per
 * frequency (not saved, it only lasts a second) and a <b>latch</b> per frequency for toggle mode
 * (saved, stays on until any remote on that frequency switches it off). A remote signal covers the
 * player's own reach plus the reach of every Radio Transmitter that relayed it (see
 * {@link #remoteCoverage}).
 * <p>
 * Loaded receivers are also tracked (not saved; each one checks in on its first tick), so a remote
 * can tell its player how many receivers heard a press.
 * <p>
 * <b>Repeaters</b> (saved, like transmitters) listen for their frequency where they stand and send
 * the strongest signal they hear out again with their own reach. <b>Converters</b> are repeaters
 * that send on a different frequency. What each one sends is worked out from the real sources
 * outward (see {@link #resolve}), so they can pass a signal along a chain, and across frequencies,
 * but can never keep each other on in a loop.
 */
public final class SignalNetwork extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_network";

    private static final SavedData.Factory<SignalNetwork> FACTORY =
            new SavedData.Factory<>(SignalNetwork::new, SignalNetwork::load, null);

    /**
     * What a transmitter is currently broadcasting. {@code compact}: a Compact Radio Transmitter
     * (v0.38.0) or a Radio Motion Sensor (v0.39.0), which have a short fixed reach and don't relay
     * remote presses.
     */
    public record Transmitter(int frequency, int power, int reach, @Nullable UUID tunedBy, boolean compact) {
        public Transmitter(int frequency, int power, int reach) {
            this(frequency, power, reach, null, false);
        }

        public Transmitter(int frequency, int power, int reach, @Nullable UUID tunedBy) {
            this(frequency, power, reach, tunedBy, false);
        }
    }

    /**
     * What a receiver can hear: the strongest signal, and how many transmitters reach it.
     * {@code hills} (v0.46.0): how much of that signal's strength hills and other ground took off on
     * the way (0: none; it can be all of it). {@code jammed}: how strong a Radio Jammer is here on
     * the frequency (0: none), already taken off {@code power}. {@code lifted} (v0.47.0): the
     * signal heard only reaches because the listener stands higher than its tower. {@code faded}
     * (v0.48.0): how much it faded on the way out to the edge of its tower's reach.
     */
    public record Reception(int power, int transmittersInReach, int hills, int jammed, boolean lifted, int faded) {
        public Reception(int power, int transmittersInReach) {
            this(power, transmittersInReach, 0, 0, false, 0);
        }

        public Reception(int power, int transmittersInReach, int hills, int jammed) {
            this(power, transmittersInReach, hills, jammed, false, 0);
        }

        public Reception(int power, int transmittersInReach, int hills, int jammed, boolean lifted) {
            this(power, transmittersInReach, hills, jammed, lifted, 0);
        }
    }

    /**
     * A Radio Jammer (v0.46.0): while {@code power} > 0 it takes that much off whatever anything
     * inside its reach hears on {@code frequency} (minus hills between, like a signal). On a
     * private frequency only the owner's and members' jammers count.
     */
    public record Jammer(int frequency, int power, int reach, @Nullable UUID tunedBy) {}

    /**
     * One area a remote signal goes out from: the player's own position, or a Radio Transmitter
     * that relayed the press ({@code tower} is true), each with its own reach. A tower with a Dish
     * Antenna (v0.48.0) also reaches {@code far} inside its beam, aimed {@code aim} (-1: no dish).
     */
    public record Coverage(BlockPos origin, int reach, boolean tower, int far, int aim) {
        public Coverage {
            origin = origin.immutable();
        }

        public Coverage(BlockPos origin, int reach, boolean tower) {
            this(origin, reach, tower, reach, -1);
        }

        /** Horizontal distance only, like transmitters. */
        public boolean reaches(BlockPos pos) {
            long dx = pos.getX() - origin.getX();
            long dz = pos.getZ() - origin.getZ();
            long distSq = dx * dx + dz * dz;
            return distSq <= (long) reach * reach
                    || aim >= 0 && distSq <= (long) far * far && AntennaTier.inBeam(dx, dz, aim);
        }
    }

    /** What a tower at {@code pos} with a (clear-weather) reach of {@code reach} covers when it relays a press, dish included. */
    private Coverage towerCoverage(@Nullable ServerLevel level, BlockPos pos, int reach) {
        int near = reachNow(level, pos, reach);
        Integer aim = dishes.get(pos);
        return aim == null ? new Coverage(pos, near, true)
                : new Coverage(pos, near, true, reachNow(level, pos, Math.max(reach, AntennaTier.dishReach(reach))), aim);
    }

    /**
     * v0.53.0: where a message from the Radio Weather Station at {@code pos} can be read: its own
     * reach (in the weather there, dish included) and the repeaters on {@code frequency}.
     */
    public List<Coverage> stationCoverage(ServerLevel level, BlockPos pos, int frequency, int reach) {
        return withRepeaters(level, frequency, List.of(towerCoverage(level, pos, reach)));
    }

    /** The coverage of a remote used from {@code origin} with no towers: just the player's own reach. */
    public static List<Coverage> ownCoverage(BlockPos origin, int reach) {
        return List.of(new Coverage(origin, reach, false));
    }

    /** True if any area in {@code coverage} reaches {@code pos}. */
    public static boolean covers(List<Coverage> coverage, BlockPos pos) {
        for (Coverage area : coverage) {
            if (area.reaches(pos)) {
                return true;
            }
        }
        return false;
    }

    /** A signal from a Remote Control: full strength, over every area in {@code coverage}. */
    public record RemoteSignal(List<Coverage> coverage, long expiresAtGameTime) {
        public RemoteSignal {
            coverage = List.copyOf(coverage);
        }

        /**
         * True if one of the areas reaches {@code receiverPos}. A tower's area only counts while the
         * tower is still there, so breaking a relay tower cuts off what it was relaying (a tower in
         * an unloaded chunk is assumed to still be standing).
         */
        boolean reaches(ServerLevel level, BlockPos receiverPos) {
            for (Coverage area : coverage) {
                if (!area.reaches(receiverPos)) {
                    continue;
                }
                if (area.tower() && level.isLoaded(area.origin())
                        && !isTower(level.getBlockState(area.origin()).getBlock())) {
                    continue;
                }
                return true;
            }
            return false;
        }
    }

    /** A Radio Transmitter, Radio Repeater or Radio Converter: the blocks that broadcast and relay remote presses. */
    public static boolean isTower(Block block) {
        return block instanceof RadioTransmitterBlock || block instanceof RadioRepeaterBlock;
    }

    /**
     * A Radio Repeater or Radio Converter: it listens on {@code frequency} and sends what it hears
     * on {@code output} with its reach. A repeater sends on the frequency it listens on; a
     * converter on another one. What it sends is worked out by {@link #resolve}.
     */
    public record Repeater(int frequency, int output, int reach, @Nullable UUID tunedBy, int gate, int threshold) {
        /** A converter's signal gate (v0.41.0): none (it passes what it hears on). */
        public static final int GATE_NONE = 0;
        /** Sends what it hears when that's at least the threshold, else nothing. */
        public static final int GATE_AT_LEAST = 1;
        /** Sends 15 while what it hears is below the threshold (at 1: while it hears nothing), else nothing. */
        public static final int GATE_BELOW = 2;

        public Repeater(int frequency, int reach) {
            this(frequency, frequency, reach, null);
        }

        public Repeater(int frequency, int output, int reach) {
            this(frequency, output, reach, null);
        }

        public Repeater(int frequency, int output, int reach, @Nullable UUID tunedBy) {
            this(frequency, output, reach, tunedBy, GATE_NONE, 1);
        }

        /** True for a converter with a signal gate: what it sends is decided by its block entity, see {@link #setGateOutput}. */
        public boolean gated() {
            return gate != GATE_NONE;
        }

        /** What a gate with this mode and threshold sends when it hears {@code heard}. */
        public static int gateOutput(int gate, int threshold, int heard) {
            return switch (gate) {
                case GATE_AT_LEAST -> heard >= threshold ? heard : 0;
                case GATE_BELOW -> heard < threshold ? 15 : 0;
                default -> heard;
            };
        }

        /** True for a converter (sends on another frequency than it listens on). */
        public boolean converts() {
            return output != frequency;
        }
    }

    /** How long a resolved chain is trusted before it's worked out again, as a safety net. */
    private static final int RESOLVE_INTERVAL = 20;

    private final Map<BlockPos, Transmitter> transmitters = new HashMap<>();
    private final Map<Integer, Map<BlockPos, Transmitter>> byFrequency = new HashMap<>();
    private final Map<BlockPos, Repeater> repeaters = new HashMap<>();
    /** What each gated converter sends (v0.41.0), as its block entity last decided; absent = nothing. Saved. */
    private final Map<BlockPos, Integer> gateOutputs = new HashMap<>();
    /** Repeaters and converters by the frequency they listen on. */
    private final Map<Integer, Map<BlockPos, Repeater>> repeatersByInput = new HashMap<>();
    /** Repeaters and converters by the frequency they send on. */
    private final Map<Integer, Map<BlockPos, Repeater>> repeatersByOutput = new HashMap<>();
    /** Goes up whenever anything a repeater or converter could hear changes. Not saved. */
    private long relayEpoch;
    /** What each repeater and converter sends, worked out at {@link #resolvedEpoch}. Not saved. */
    private Map<BlockPos, Integer> resolvedPower = Map.of();
    private long resolvedEpoch = -1;
    private long resolvedAt;
    private final Map<Integer, Long> revisions = new HashMap<>();
    private final Map<Integer, RemoteSignal> pulses = new HashMap<>();
    private final Map<Integer, RemoteSignal> latches = new HashMap<>();
    private final Map<BlockPos, Integer> receivers = new HashMap<>();
    /** Who tuned or placed each tracked receiver (only those with a known tuner), for encrypted frequencies. */
    private final Map<BlockPos, UUID> receiverTuners = new HashMap<>();
    private final Map<Integer, Set<BlockPos>> receiversByFrequency = new HashMap<>();
    /** A Radio Logic Receiver's second frequency (v0.40.0): it's tracked on both. */
    private final Map<BlockPos, Integer> receiverSeconds = new HashMap<>();
    /** Recent times each frequency turned on, oldest first, for the pagers' catch-up. Saved. */
    private final Map<Integer, ArrayDeque<TurnOn>> history = new HashMap<>();
    private final Map<BlockPos, Jammer> jammers = new HashMap<>();
    private final Map<Integer, Map<BlockPos, Jammer>> jammersByFrequency = new HashMap<>();
    /** Whether it rains or snows where each tower stands (by biome; not saved). */
    private final Map<BlockPos, Boolean> wetSpots = new HashMap<>();
    /** The weather last seen in this dimension, to notice it changing (not saved). */
    private @Nullable Weather lastWeather;
    /** Weather the GameTests set for one network (null: the level's). */
    private @Nullable Weather forcedWeather;
    /**
     * v0.53.0, with Reiko's Weather: the storm cell over each tower (its weather, or null for none),
     * worked out again every {@link #STORM_CHECK_TICKS} (not saved).
     */
    private final Map<BlockPos, java.util.Optional<Weather>> storms = new HashMap<>();
    /** How often the storm cells over towers are looked at again (they drift a few blocks in that time). */
    public static final int STORM_CHECK_TICKS = 40;
    /** The game time and day time of the last tick, used to stamp {@link TurnOn}s. */
    private long now;
    private long nowDayTime;

    /** The dimension this network belongs to, for checking private frequencies where no level is passed in. Not saved. */
    private @Nullable ServerLevel world;

    public static SignalNetwork get(ServerLevel level) {
        SignalNetwork network = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        network.world = level;
        return network;
    }

    /** Ties a network made with {@code new} to {@code level}, so it checks private frequencies. For the GameTests. */
    public SignalNetwork attachTo(ServerLevel level) {
        world = level;
        return this;
    }

    // ---- Private frequencies ----

    /**
     * True if a block tuned or placed by {@code tunedBy} may not broadcast on {@code frequency},
     * because the frequency is private and {@code tunedBy} is neither its owner nor a friend.
     * Blocks with no recorded tuner are never blocked.
     */
    private boolean blocked(@Nullable ServerLevel level, int frequency, @Nullable UUID tunedBy) {
        if (tunedBy == null) {
            return false;
        }
        ServerLevel at = level != null ? level : world;
        return at != null && !FrequencyClaims.maySend(at, frequency, tunedBy);
    }

    /**
     * True if a receiver or converter tuned or placed by {@code listener} can't hear
     * {@code frequency}, because it's encrypted and {@code listener} is neither its owner nor a
     * member. Blocks with no recorded tuner always hear.
     */
    private boolean deaf(@Nullable ServerLevel level, int frequency, @Nullable UUID listener) {
        if (listener == null) {
            return false;
        }
        ServerLevel at = level != null ? level : world;
        return at != null && !FrequencyClaims.mayHear(at, frequency, listener);
    }

    /** True if the converter (or repeater) at {@code pos} hears nothing, because it listens on someone else's encrypted frequency. */
    public boolean isDeaf(ServerLevel level, BlockPos pos) {
        Repeater repeater = repeaters.get(pos);
        return repeater != null && deaf(level, repeater.frequency(), repeater.tunedBy());
    }

    /**
     * True if the transmitter, repeater or converter at {@code pos} is kept quiet because it sends
     * on someone else's private frequency.
     */
    public boolean isSilenced(ServerLevel level, BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        if (tx != null) {
            return blocked(level, tx.frequency(), tx.tunedBy());
        }
        Jammer jammer = jammers.get(pos);
        if (jammer != null) {
            return blocked(level, jammer.frequency(), jammer.tunedBy());
        }
        Repeater repeater = repeaters.get(pos);
        return repeater != null && blocked(level, repeater.output(), repeater.tunedBy());
    }

    /**
     * How many transmitters, repeaters and converters send on {@code frequency} that were tuned
     * or placed by someone {@code allowed} says no to. Blocks with no recorded tuner don't count
     * (they'd keep working anyway).
     */
    public int countOtherSenders(int frequency, java.util.function.Predicate<UUID> allowed) {
        int count = 0;
        for (Transmitter tx : byFrequency.getOrDefault(frequency, Map.of()).values()) {
            if (tx.tunedBy() != null && !allowed.test(tx.tunedBy())) count++;
        }
        for (Repeater repeater : repeatersByOutput.getOrDefault(frequency, Map.of()).values()) {
            if (repeater.tunedBy() != null && !allowed.test(repeater.tunedBy())) count++;
        }
        for (Jammer jammer : jammersByFrequency.getOrDefault(frequency, Map.of()).values()) {
            if (jammer.tunedBy() != null && !allowed.test(jammer.tunedBy())) count++;
        }
        return count;
    }

    /**
     * A frequency was made private or public, or its friends changed: who may send on it is
     * different now, so everything on it (and down the line from it) works it out again.
     */
    public void invalidate(int frequency) {
        relayEpoch++;
        bump(frequency);
    }

    // ---- Transmitters ----

    public void updateTransmitter(BlockPos pos, int frequency, int power, int reach) {
        updateTransmitter(pos, frequency, power, reach, null);
    }

    /**
     * What the transmitter at {@code pos} is sending. {@code tunedBy} is the player who last tuned
     * or placed it (null if unknown), which decides whether it may broadcast on a private frequency.
     */
    public void updateTransmitter(BlockPos pos, int frequency, int power, int reach, @Nullable UUID tunedBy) {
        updateTransmitter(pos, frequency, power, reach, tunedBy, false);
    }

    /** {@link #updateTransmitter(BlockPos, int, int, int, UUID)} for a compact transmitter ({@code compact}) or a normal one. */
    public void updateTransmitter(BlockPos pos, int frequency, int power, int reach, @Nullable UUID tunedBy, boolean compact) {
        updateTransmitter(pos, frequency, power, reach, tunedBy, compact, null);
    }

    /**
     * {@link #updateTransmitter(BlockPos, int, int, int, UUID, boolean)}, naming what turned it on
     * for the frequency log if this turns it on ({@code cause}; null: the transmitter and its tuner).
     * A Radio Motion Sensor (v0.39.0) names who it saw.
     */
    public void updateTransmitter(BlockPos pos, int frequency, int power, int reach, @Nullable UUID tunedBy, boolean compact,
                                  @Nullable Cause cause) {
        BlockPos key = pos.immutable();
        Bundle bundle = bundles.remove(key);
        if (bundle != null) {
            unindexBundle(key, bundle); // v0.54.0: a plain transmitter took a bundled one's place
        }
        Transmitter next = new Transmitter(frequency, power, reach, tunedBy, compact);
        Transmitter previous = transmitters.put(key, next);
        if (next.equals(previous) && bundle == null) {
            return;
        }
        if (power > 0 && (previous == null || previous.power() == 0 || previous.frequency() != frequency)
                && !blocked(null, frequency, tunedBy)) {
            recordTurnOn(frequency, ownCoverage(key, reach), cause != null ? cause : Cause.transmitter(key, tunedBy));
        }
        if (previous != null && previous.frequency() != frequency) {
            removeFromIndex(key, previous.frequency());
        }
        byFrequency.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, next);
        bump(frequency);
        setDirty();
    }

    public void removeTransmitter(BlockPos pos) {
        dishes.remove(pos);
        if (uplinks.remove(pos)) setDirty(); // v0.61.0
        forgetPower(pos);
        Transmitter linked = transmitters.get(pos);
        ResourceKey<Level> target = links.remove(pos);
        if (target != null && linked != null) {
            setDirty();
            bumpLinked(target, linked.frequency());
        }
        Transmitter previous = transmitters.remove(pos);
        if (previous != null) {
            removeFromIndex(pos, previous.frequency());
            setDirty();
        }
        setMusic(pos, null); // v0.60.0: a Radio Jukebox's song goes with it
        Bundle bundle = bundles.remove(pos);
        if (bundle != null) {
            unindexBundle(pos, bundle); // v0.54.0
            setDirty();
        }
        forgetStorms(pos);
    }

    /** v0.54.0: a tower went: its storm damage and ice go with it. */
    private void forgetStorms(BlockPos pos) {
        if (damaged.remove(pos) != null | iced.remove(pos) != null) {
            setDirty();
        }
    }

    private void removeFromIndex(BlockPos pos, int frequency) {
        Map<BlockPos, Transmitter> onFrequency = byFrequency.get(frequency);
        if (onFrequency != null) {
            onFrequency.remove(pos);
            if (onFrequency.isEmpty()) {
                byFrequency.remove(frequency);
            }
        }
        bump(frequency);
    }

    // ---- Bundles (v0.54.0) ----

    /** How many colors a bundle carries: white, orange, ... black, as dyes are numbered. */
    public static final int BUNDLE_SIZE = 16;

    /**
     * What a Bundled Radio Transmitter sends: color {@code c} on frequency {@code base + c}, at
     * {@code channels[c]}. Saved, so a bundle tower in an unloaded chunk keeps sending all 16.
     */
    public record Bundle(int base, int[] channels) {
        public int channel(int frequency) {
            int c = frequency - base;
            return c >= 0 && c < BUNDLE_SIZE ? channels[c] : 0;
        }

        public boolean covers(int frequency) {
            return frequency >= base && frequency < base + BUNDLE_SIZE && frequency <= Frequency.MAX;
        }
    }

    private final Map<BlockPos, Bundle> bundles = new HashMap<>();

    /** The bundle the transmitter at {@code pos} sends, or null if it isn't a bundled one. */
    public @Nullable Bundle bundleAt(BlockPos pos) {
        return bundles.get(pos);
    }

    /**
     * v0.54.0: a Bundled Radio Transmitter checks in. It's one tower (its reach, power cost,
     * lightning, dish and Ender Antenna as usual, "sending" while any color is on), and 16
     * transmitters for listening: color c is heard on {@code base + c} at {@code channels[c]}, so a
     * plain receiver on base + 3 picks out light blue, and repeaters and converters pass colors on.
     */
    public void updateBundle(BlockPos pos, int base, int[] channels, int reach, @Nullable UUID tunedBy) {
        BlockPos key = pos.immutable();
        int max = 0;
        for (int c = 0; c < BUNDLE_SIZE; c++) {
            max = Math.max(max, channels[c]);
        }
        Transmitter main = new Transmitter(base, max, reach, tunedBy, false);
        Transmitter previousMain = transmitters.put(key, main);
        Bundle previous = bundles.get(key);
        if (previous != null && previous.base() != base) {
            unindexBundle(key, previous);
            previous = null;
        } else if (previous == null && previousMain != null) {
            removeFromIndex(key, previousMain.frequency()); // it was a plain transmitter here
        }
        boolean changed = !main.equals(previousMain);
        for (int c = 0; c < BUNDLE_SIZE; c++) {
            int frequency = base + c;
            if (frequency > Frequency.MAX) {
                break;
            }
            Transmitter entry = new Transmitter(frequency, channels[c], reach, tunedBy, false);
            Transmitter was = byFrequency.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, entry);
            if (!entry.equals(was)) {
                if (channels[c] > 0 && (was == null || was.power() == 0) && !blocked(null, frequency, tunedBy)) {
                    recordTurnOn(frequency, ownCoverage(key, reach), Cause.transmitter(key, tunedBy));
                }
                bump(frequency);
                changed = true;
            }
        }
        bundles.put(key, new Bundle(base, channels.clone()));
        if (changed) {
            setDirty();
        }
    }

    /** Takes a bundle's 16 frequencies out of the index. */
    private void unindexBundle(BlockPos pos, Bundle bundle) {
        for (int c = 0; c < BUNDLE_SIZE && bundle.base() + c <= Frequency.MAX; c++) {
            removeFromIndex(pos, bundle.base() + c);
        }
    }

    /** Puts a loaded bundle's 16 frequencies in the index (the main entry is the base's). */
    private void indexBundle(BlockPos pos, Bundle bundle, Transmitter main) {
        for (int c = 0; c < BUNDLE_SIZE && bundle.base() + c <= Frequency.MAX; c++) {
            byFrequency.computeIfAbsent(bundle.base() + c, f -> new HashMap<>()).put(pos,
                    new Transmitter(bundle.base() + c, bundle.channels()[c], main.reach(), main.tunedBy(), false));
        }
    }

    // ---- Repeaters and converters ----

    /** A repeater was placed, re-tuned, or checks in (once a second, and after its chunk loads). */
    public void updateRepeater(BlockPos pos, int frequency, int reach) {
        updateRepeater(pos, frequency, frequency, reach);
    }

    /** A repeater ({@code output == frequency}) or converter was placed, re-tuned, or checks in. */
    public void updateRepeater(BlockPos pos, int frequency, int output, int reach) {
        updateRepeater(pos, frequency, output, reach, null);
    }

    /** {@link #updateRepeater(BlockPos, int, int, int)}, with the player who last tuned or placed it. */
    public void updateRepeater(BlockPos pos, int frequency, int output, int reach, @Nullable UUID tunedBy) {
        updateRepeater(pos, frequency, output, reach, tunedBy, Repeater.GATE_NONE, 1);
    }

    /** {@link #updateRepeater(BlockPos, int, int, int, UUID)} for a converter with a signal gate (v0.41.0). */
    public void updateRepeater(BlockPos pos, int frequency, int output, int reach, @Nullable UUID tunedBy, int gate, int threshold) {
        BlockPos key = pos.immutable();
        Repeater next = new Repeater(frequency, output, reach, tunedBy, gate, threshold);
        Repeater previous = repeaters.put(key, next);
        if (next.equals(previous)) {
            return;
        }
        if (previous != null) {
            removeRepeaterFromIndex(key, previous);
        }
        if (!next.gated() || previous == null || previous.output() != output) {
            gateOutputs.remove(key); // a new gate (or a new output frequency) starts quiet until its block entity decides
        }
        repeatersByInput.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, next);
        repeatersByOutput.computeIfAbsent(output, f -> new HashMap<>()).put(key, next);
        relayEpoch++;
        bump(frequency);
        bump(output);
        setDirty();
    }

    public void removeRepeater(BlockPos pos) {
        dishes.remove(pos);
        forgetPower(pos);
        Repeater previous = repeaters.remove(pos);
        gateOutputs.remove(pos);
        if (previous != null) {
            removeRepeaterFromIndex(pos, previous);
            setDirty();
        }
        forgetStorms(pos);
    }

    private void removeRepeaterFromIndex(BlockPos pos, Repeater repeater) {
        for (var index : List.of(Map.entry(repeatersByInput, repeater.frequency()), Map.entry(repeatersByOutput, repeater.output()))) {
            Map<BlockPos, Repeater> onFrequency = index.getKey().get(index.getValue());
            if (onFrequency != null) {
                onFrequency.remove(pos);
                if (onFrequency.isEmpty()) {
                    index.getKey().remove(index.getValue());
                }
            }
        }
        relayEpoch++;
        bump(repeater.output()); // first, while it can't ripple through this repeater any more
        bump(repeater.frequency());
    }

    /**
     * A gated converter's output, as decided by its block entity (v0.41.0): {@code power} goes out on
     * its output frequency like a transmitter's, over its reach. A change bumps that frequency, and a
     * turn-on is remembered for the log and pagers (as the gate's). Returns true if it changed.
     */
    public boolean setGateOutput(BlockPos pos, int power) {
        Repeater repeater = repeaters.get(pos);
        if (repeater == null || !repeater.gated()) {
            return false;
        }
        BlockPos key = pos.immutable();
        int previous = gateOutputs.getOrDefault(key, 0);
        if (previous == power) {
            return false;
        }
        if (power > 0) {
            gateOutputs.put(key, power);
        } else {
            gateOutputs.remove(key);
        }
        relayEpoch++;
        if (previous == 0 && !blocked(null, repeater.output(), repeater.tunedBy())) {
            recordTurnOn(repeater.output(), ownCoverage(key, repeater.reach()), Cause.gate(key, repeater.tunedBy()));
        }
        bump(repeater.output());
        setDirty();
        return true;
    }

    /** What the gated converter at {@code pos} sends right now, as last decided (0 if nothing or none). */
    public int gateOutput(BlockPos pos) {
        return gateOutputs.getOrDefault(pos, 0);
    }

    /** What a gated converter at {@code pos} hears on the frequency it listens on (0 if it's deaf there, or there's none). */
    public int gateInput(ServerLevel level, BlockPos pos) {
        Repeater repeater = repeaters.get(pos);
        if (repeater == null || deaf(level, repeater.frequency(), repeater.tunedBy())) {
            return 0;
        }
        return listen(level, repeater.frequency(), pos, null, false).power(); // a converter hears it unfaded
    }

    /** The frequency the repeater or converter at {@code pos} listens on, or empty if there's none in the network there. */
    public OptionalInt repeaterFrequencyAt(BlockPos pos) {
        Repeater repeater = repeaters.get(pos);
        return repeater == null ? OptionalInt.empty() : OptionalInt.of(repeater.frequency());
    }

    /** The repeater or converter at {@code pos}, or null. */
    public @Nullable Repeater repeaterAt(BlockPos pos) {
        return repeaters.get(pos);
    }

    /**
     * The frequency the transmitter, repeater or converter at {@code pos} sends on (for a
     * converter, its output), or empty if there's none.
     */
    public OptionalInt towerFrequencyAt(BlockPos pos) {
        OptionalInt transmitter = transmitterFrequencyAt(pos);
        if (transmitter.isPresent()) {
            return transmitter;
        }
        Jammer jammer = jammers.get(pos);
        if (jammer != null) {
            return OptionalInt.of(jammer.frequency());
        }
        Repeater repeater = repeaters.get(pos);
        return repeater == null ? OptionalInt.empty() : OptionalInt.of(repeater.output());
    }

    /**
     * The strongest signal sent on {@code frequency} anywhere in this dimension right now, 0 if it's
     * quiet: transmitters (leaving out ones silenced on a private frequency), repeaters and
     * converters sending on it, and remote presses (15). For the frequency directory.
     */
    public int strongestAnywhere(ServerLevel level, int frequency) {
        int best = pulses.containsKey(frequency) || latches.containsKey(frequency) ? 15 : 0;
        for (Transmitter tx : byFrequency.getOrDefault(frequency, Map.of()).values()) {
            if (tx.power() > best && !blocked(level, frequency, tx.tunedBy())) {
                best = tx.power(); // (one knocked out by lightning still counts: it's back in seconds)
            }
        }
        Map<BlockPos, Repeater> relays = repeatersByOutput.get(frequency);
        if (relays != null && best < 15) {
            Map<BlockPos, Integer> sending = resolve(level); // silenced relays are already 0 here
            for (BlockPos pos : relays.keySet()) {
                best = Math.max(best, sending.getOrDefault(pos, 0));
            }
        }
        return best;
    }

    /** The signal the repeater or converter at {@code pos} is sending out right now (0 if it hears nothing, or there's none). */
    public int repeaterPower(ServerLevel level, BlockPos pos) {
        return repeaters.containsKey(pos) ? resolve(level).getOrDefault(pos, 0) : 0;
    }

    /**
     * Works out what every repeater and converter sends, by position. First each one gets what it
     * hears straight from transmitters and remote presses on the frequency it listens on; then,
     * strongest first, each one that hears something passes it on to the repeaters and converters
     * inside its reach that listen on the frequency it sends on. Because it only spreads outward
     * from real sources, repeaters and converters can't keep each other on in a loop (not even a
     * converter loop like 42 to 100 and back) once the source goes quiet. The strength is passed on
     * unchanged, less what hills between take off and what a jammer where the next one stands
     * takes off (v0.46.0).
     * <p>
     * One answer covers the whole dimension, kept until anything a repeater or converter could
     * hear changes ({@link #relayEpoch}), and re-worked once a second anyway, for changes nothing
     * reports (a relay tower of a remote press being broken).
     */
    private Map<BlockPos, Integer> resolve(ServerLevel level) {
        if (repeaters.isEmpty()) {
            return Map.of();
        }
        if (resolvedEpoch == relayEpoch && now >= resolvedAt && now - resolvedAt < RESOLVE_INTERVAL) {
            return resolvedPower;
        }
        dropStaleRepeaters(level);
        List<BlockPos> positions = new ArrayList<>(repeaters.keySet());
        Map<BlockPos, Integer> power = new HashMap<>();
        Map<BlockPos, Integer> jamHere = new HashMap<>();
        Set<BlockPos> silenced = new HashSet<>();
        for (BlockPos pos : positions) {
            Repeater repeater = repeaters.get(pos);
            if (blocked(level, repeater.output(), repeater.tunedBy()) || deaf(level, repeater.frequency(), repeater.tunedBy())
                    || isDown(pos)) {
                // It sends on someone else's private frequency, or listens on someone else's encrypted one, or
                // lightning knocked it out (v0.47.0): it stays quiet.
                silenced.add(pos);
                power.put(pos, 0);
            } else if (repeater.gated()) {
                // A gate (v0.41.0) sends what its block entity decided, like a transmitter: it's a source
                // here, and nothing spreads into it (that would make an inverting loop impossible to settle).
                silenced.add(pos);
                power.put(pos, gateOutputs.getOrDefault(pos, 0));
            } else {
                int jam = jamming(level, repeater.frequency(), pos);
                if (jam > 0) {
                    jamHere.put(pos, jam);
                }
                power.put(pos, Math.max(0, listenDirect(level, repeater.frequency(), pos, false).power() - jam));
            }
        }
        Set<BlockPos> done = new HashSet<>();
        while (true) {
            BlockPos next = null;
            int nextPower = 0;
            for (BlockPos pos : positions) {
                int p = power.get(pos);
                if (p > nextPower && !done.contains(pos)) {
                    next = pos;
                    nextPower = p;
                }
            }
            if (next == null) {
                break;
            }
            done.add(next);
            Repeater from = repeaters.get(next);
            Map<BlockPos, Repeater> listening = repeatersByInput.get(from.output());
            if (listening == null) {
                continue;
            }
            for (BlockPos pos : listening.keySet()) {
                if (power.get(pos) < nextPower && !done.contains(pos) && !silenced.contains(pos)
                        && horizontalDistSq(next, pos) <= square(linkReach(level, next, from.reach(), pos, ear(level, pos)))) {
                    // v0.46.0: minus a jammer where it stands, then minus the hills between.
                    int got = nextPower - jamHere.getOrDefault(pos, 0);
                    if (got > power.get(pos)) {
                        got -= hillLoss(level, next, pos);
                        if (got > power.get(pos)) {
                            power.put(pos, got);
                        }
                    }
                }
            }
        }
        resolvedPower = Map.copyOf(power);
        resolvedEpoch = relayEpoch;
        resolvedAt = now;
        return resolvedPower;
    }

    /** Self-repair: forgets repeaters and converters whose block is gone (for example removed by a world editor). */
    private void dropStaleRepeaters(ServerLevel level) {
        List<BlockPos> stale = null;
        for (BlockPos pos : repeaters.keySet()) {
            if (isStale(level, pos)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(pos);
            }
        }
        if (stale != null) {
            stale.forEach(this::removeRepeater);
        }
    }

    /** True if {@code pos} is loaded and holds neither a transmitter nor a repeater (or converter) any more. */
    private static boolean isStale(@Nullable ServerLevel level, BlockPos pos) {
        return level != null && level.isLoaded(pos) && !isTower(level.getBlockState(pos).getBlock());
    }

    // ---- For the frequency audit (read-only copies) ----

    /** Every transmitter on {@code frequency} whose block is still there (or not loaded), by position. */
    public Map<BlockPos, Transmitter> transmittersOn(ServerLevel level, int frequency) {
        Map<BlockPos, Transmitter> result = new java.util.TreeMap<>();
        byFrequency.getOrDefault(frequency, Map.of()).forEach((pos, tx) -> {
            if (!isStale(level, pos)) {
                result.put(pos, tx);
            }
        });
        return result;
    }

    /** Every repeater or converter that listens on or sends on {@code frequency}, by position. */
    public Map<BlockPos, Repeater> relaysOn(ServerLevel level, int frequency) {
        Map<BlockPos, Repeater> result = new java.util.TreeMap<>();
        for (Map<Integer, Map<BlockPos, Repeater>> index : List.of(repeatersByInput, repeatersByOutput)) {
            index.getOrDefault(frequency, Map.of()).forEach((pos, relay) -> {
                if (!isStale(level, pos)) {
                    result.put(pos, relay);
                }
            });
        }
        return result;
    }

    /** One loaded receiver: where it is and who tuned or placed it (null: no recorded tuner). */
    public record TrackedReceiver(BlockPos pos, @Nullable UUID tunedBy) {}

    /** Every loaded receiver on {@code frequency} (receivers in unloaded chunks aren't known). */
    public List<TrackedReceiver> receiversOn(int frequency) {
        List<TrackedReceiver> result = new ArrayList<>();
        for (BlockPos pos : new java.util.TreeSet<>(receiversByFrequency.getOrDefault(frequency, Set.of()))) {
            result.add(new TrackedReceiver(pos, receiverTuners.get(pos)));
        }
        return result;
    }

    /**
     * Where a signal on {@code frequency} over {@code coverage} gets to, by frequency: the
     * coverage itself (first), plus the area of every repeater and converter it reaches, then of
     * those that one reaches, and so on, each under the frequency it sends on. Used to count who
     * hears a remote press (on every frequency it ends up on) and to remember where a turn-on
     * reached. With a {@code level}, repeaters whose block is gone are skipped.
     */
    public Carried carried(@Nullable ServerLevel level, int frequency, List<Coverage> coverage) {
        Map<Integer, List<Coverage>> areas = new java.util.LinkedHashMap<>();
        areas.put(frequency, new ArrayList<>(coverage));
        if (repeaters.isEmpty()) {
            return new Carried(areas, 0, 0);
        }
        Set<BlockPos> reached = new HashSet<>();
        int repeaterCount = 0;
        int converterCount = 0;
        ArrayDeque<Map.Entry<Integer, Coverage>> queue = new ArrayDeque<>();
        coverage.forEach(area -> queue.add(Map.entry(frequency, area)));
        while (!queue.isEmpty()) {
            Map.Entry<Integer, Coverage> item = queue.poll();
            Map<BlockPos, Repeater> listening = repeatersByInput.get(item.getKey());
            if (listening == null) {
                continue;
            }
            for (Map.Entry<BlockPos, Repeater> entry : listening.entrySet()) {
                BlockPos pos = entry.getKey();
                Repeater repeater = entry.getValue();
                if (reached.contains(pos) || !item.getValue().reaches(pos) || isStale(level, pos) || repeater.gated()
                        || blocked(level, repeater.output(), repeater.tunedBy())
                        || deaf(level, repeater.frequency(), repeater.tunedBy())) {
                    continue;
                }
                reached.add(pos);
                Coverage own = towerCoverage(level, pos, repeater.reach());
                List<Coverage> list = areas.computeIfAbsent(repeater.output(), f -> new ArrayList<>());
                if (!list.contains(own)) { // a repeater is already there if it relayed the press as a tower
                    list.add(own);
                    if (repeater.converts()) converterCount++;
                    else repeaterCount++;
                }
                queue.add(Map.entry(repeater.output(), own));
            }
        }
        return new Carried(areas, repeaterCount, converterCount);
    }

    /**
     * What {@link #carried} found: the areas reached on each frequency (the starting frequency
     * first), and how many repeaters and converters were added.
     */
    public record Carried(Map<Integer, List<Coverage>> areas, int repeaters, int converters) {
        /** The areas reached on {@code frequency}, or an empty list. */
        public List<Coverage> on(int frequency) {
            return areas.getOrDefault(frequency, List.of());
        }
    }

    /**
     * {@code coverage} plus the area of every repeater on {@code frequency} the signal gets to
     * through it: repeaters inside the coverage, then repeaters inside those, and so on. (A
     * converter's areas go under the frequency it sends on, so they're not in this list; see
     * {@link #carried}.) Unchanged when nothing on that frequency is reached.
     */
    public List<Coverage> withRepeaters(@Nullable ServerLevel level, int frequency, List<Coverage> coverage) {
        if (!repeatersByInput.containsKey(frequency)) {
            return coverage;
        }
        return carried(level, frequency, coverage).on(frequency);
    }

    public long revision(int frequency) {
        return revisions.getOrDefault(frequency, 0L);
    }

    /**
     * Something on {@code frequency} changed. If repeaters or converters listen on it, what they
     * send may change too, so the frequencies converters send on are bumped as well (and so on
     * down the line), so receivers there re-scan straight away.
     */
    private void bump(int frequency) {
        bump(frequency, null);
    }

    private void bump(int frequency, @Nullable Set<Integer> seen) {
        revisions.merge(frequency, 1L, Long::sum);
        propagateLinks(frequency); // v0.50.0: and in the dimensions its Ender Antennas send into
        Map<BlockPos, Repeater> listening = repeatersByInput.get(frequency);
        if (listening == null) {
            return;
        }
        relayEpoch++;
        for (Repeater repeater : listening.values()) {
            if (repeater.converts()) {
                if (seen == null) {
                    seen = new HashSet<>();
                    seen.add(frequency);
                }
                if (seen.add(repeater.output())) {
                    bump(repeater.output(), seen);
                }
            }
        }
    }

    // ---- Remote Controls ----

    /**
     * Where a remote press from {@code origin} goes out: the player's own reach first, then, if
     * {@code relayThroughTowers}, every Radio Transmitter and Radio Repeater within that reach
     * (horizontally, on any frequency), each with its own reach. Compact transmitters aren't towers
     * and don't relay. A tower only relays a press it
     * heard directly; towers don't pass it on to each other. (Repeaters on the press's own
     * frequency do pass it on, through {@link #resolve}.)
     */
    public List<Coverage> remoteCoverage(ServerLevel level, BlockPos origin, int reach, boolean relayThroughTowers) {
        List<Coverage> coverage = new ArrayList<>();
        Coverage own = new Coverage(origin, reach, false);
        coverage.add(own);
        if (!relayThroughTowers) {
            return coverage;
        }
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Transmitter> entry : transmitters.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!own.reaches(pos) || entry.getValue().compact() || isDown(pos)) {
                continue;
            }
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(pos);
                continue;
            }
            coverage.add(towerCoverage(level, pos, entry.getValue().reach()));
        }
        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        List<BlockPos> staleRepeaters = null;
        for (Map.Entry<BlockPos, Repeater> entry : repeaters.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!own.reaches(pos) || isDown(pos)) {
                continue;
            }
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RadioRepeaterBlock)) {
                if (staleRepeaters == null) staleRepeaters = new ArrayList<>();
                staleRepeaters.add(pos);
                continue;
            }
            coverage.add(towerCoverage(level, pos, entry.getValue().reach()));
        }
        if (staleRepeaters != null) {
            staleRepeaters.forEach(this::removeRepeater);
        }
        // Nearest towers first, so messages and saves come out in a stable order.
        coverage.subList(1, coverage.size()).sort(Comparator
                .comparingLong((Coverage c) -> horizontalDistSq(c.origin(), origin))
                .thenComparingLong(c -> c.origin().asLong()));
        return coverage;
    }

    private static long horizontalDistSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Sends a full-strength signal on {@code frequency} over {@code coverage} until
     * {@code untilGameTime}. Pressing again while a pulse is still on adds the new areas to it.
     */
    public void pulse(int frequency, List<Coverage> coverage, long untilGameTime) {
        pulse(frequency, coverage, untilGameTime, Cause.UNKNOWN);
    }

    /** {@link #pulse(int, List, long)}, remembering who pressed for the frequency log. */
    public void pulse(int frequency, List<Coverage> coverage, long untilGameTime, Cause cause) {
        RemoteSignal previous = pulses.get(frequency);
        long until = untilGameTime;
        List<Coverage> areas = coverage;
        if (previous != null) {
            until = Math.max(previous.expiresAtGameTime(), untilGameTime);
            LinkedHashSet<Coverage> merged = new LinkedHashSet<>(previous.coverage());
            merged.addAll(coverage);
            areas = new ArrayList<>(merged);
        }
        pulses.put(frequency, new RemoteSignal(areas, until));
        recordTurnOn(frequency, coverage, cause);
        bump(frequency);
    }

    /**
     * Toggle mode: switches {@code frequency} on (over {@code coverage}) or off. Returns true if it
     * is now on. The areas are fixed when it's switched on; moving afterwards doesn't change them.
     */
    public boolean toggleLatch(int frequency, List<Coverage> coverage) {
        return toggleLatch(frequency, coverage, Cause.UNKNOWN);
    }

    /** {@link #toggleLatch(int, List)}, remembering who switched it on for the frequency log. */
    public boolean toggleLatch(int frequency, List<Coverage> coverage, Cause cause) {
        boolean nowOn;
        if (latches.remove(frequency) != null) {
            nowOn = false;
        } else {
            latches.put(frequency, new RemoteSignal(coverage, Long.MAX_VALUE));
            recordTurnOn(frequency, coverage, cause);
            nowOn = true;
        }
        bump(frequency);
        setDirty();
        return nowOn;
    }

    // ---- Morse (v0.62.0) ----

    /** How long after its last key-down a frequency still counts as carrying Morse (pagers don't alert, the log isn't added to). */
    public static final int MORSE_QUIET_TICKS = 100;
    /** Until when each frequency counts as carrying Morse (not saved). */
    private final Map<Integer, Long> morseUntil = new HashMap<>();

    /**
     * A Morse Key (or a telegraph keying a message) holds {@code frequency} on over {@code coverage}
     * at {@code gameTime}, for a few ticks; called every tick while the key is down. Like a remote
     * pulse, receivers in the areas hear 15. Only the first key-down of a message goes in the
     * frequency log, and pagers don't alert for any of them.
     */
    public void keyMorse(int frequency, List<Coverage> coverage, long gameTime, Cause cause) {
        boolean fresh = !isMorse(frequency, gameTime);
        morseUntil.put(frequency, gameTime + MORSE_QUIET_TICKS);
        RemoteSignal previous = pulses.get(frequency);
        if (previous != null && previous.coverage().containsAll(coverage)) {
            if (previous.expiresAtGameTime() < gameTime + 3) {
                pulses.put(frequency, new RemoteSignal(previous.coverage(), gameTime + 3)); // still down: no change to hear
            }
            return;
        }
        List<Coverage> areas = coverage;
        if (previous != null) {
            LinkedHashSet<Coverage> merged = new LinkedHashSet<>(previous.coverage());
            merged.addAll(coverage);
            areas = new ArrayList<>(merged);
        }
        pulses.put(frequency, new RemoteSignal(areas, Math.max(gameTime + 3, previous == null ? 0 : previous.expiresAtGameTime())));
        if (fresh) {
            recordTurnOn(frequency, coverage, cause);
        }
        bump(frequency);
    }

    /** The key went up: {@code frequency}'s Morse signal ends now. */
    public void releaseMorse(int frequency, long gameTime) {
        morseUntil.put(frequency, gameTime + MORSE_QUIET_TICKS);
        if (pulses.remove(frequency) != null) {
            bump(frequency);
        }
    }

    /** True if Morse went out on {@code frequency} in the last {@link #MORSE_QUIET_TICKS} (as of {@code gameTime}). */
    public boolean isMorse(int frequency, long gameTime) {
        Long until = morseUntil.get(frequency);
        return until != null && gameTime <= until;
    }

    // ---- Music broadcasts (v0.60.0) ----

    /**
     * A song a Radio Jukebox is broadcasting on {@code frequency}: from {@code start} (game time),
     * {@code length} ticks a play (the song and the gap after it), over and over while {@code loop}.
     * Kept here (and saved) rather than only in the jukebox, so a jukebox whose chunk isn't loaded
     * keeps playing to the speakers that hear it, and stops when its song ends.
     */
    public record Music(int frequency, ResourceKey<net.minecraft.world.item.JukeboxSong> song, long start, int length, boolean loop) {
        /** True if it's playing at {@code now}. */
        public boolean playingAt(long now) {
            return now >= start && (loop || now - start < length);
        }

        /** When the play going on at {@code now} began: {@link #start}, or the latest repeat while looping. */
        public long startAt(long now) {
            return loop && length > 0 && now > start ? start + (now - start) / length * length : start;
        }
    }

    /** What a speaker at a spot hears played: the broadcast, the jukebox it comes from, and when this play began. */
    public record MusicHeard(BlockPos from, Music music, long start) {
    }

    /** The song each Radio Jukebox is broadcasting, by its position. Saved. */
    private final Map<BlockPos, Music> music = new HashMap<>();
    /** Where each broadcast reaches (its tower and the repeaters on its frequency), and when that was worked out. Not saved. */
    private final Map<BlockPos, CachedCoverage> musicCoverage = new HashMap<>();
    /** How long a broadcast's coverage is trusted before it's worked out again. */
    private static final int MUSIC_COVERAGE_TICKS = 20;

    private record CachedCoverage(long at, int frequency, int reach, List<Coverage> coverage) {
    }

    /**
     * What the Radio Jukebox at {@code pos} broadcasts (null: nothing). Speakers on the frequency
     * look again at once when it changes.
     */
    public void setMusic(BlockPos pos, @Nullable Music next) {
        BlockPos key = pos.immutable();
        Music previous = next == null ? music.remove(key) : music.put(key, next);
        if (java.util.Objects.equals(previous, next)) {
            return;
        }
        musicCoverage.remove(key);
        if (previous != null) {
            bump(previous.frequency());
        }
        if (next != null && (previous == null || previous.frequency() != next.frequency())) {
            bump(next.frequency());
        } else if (next != null) {
            revisions.merge(next.frequency(), 1L, Long::sum);
        }
        setDirty();
    }

    /** What the transmitter at {@code pos} sends, as last told (null: none). */
    public @Nullable Transmitter transmitterAt(BlockPos pos) {
        return transmitters.get(pos);
    }

    /** What the Radio Jukebox at {@code pos} is broadcasting, or null. */
    public @Nullable Music musicAt(BlockPos pos) {
        return music.get(pos);
    }

    /**
     * The song played at {@code pos} on {@code frequency}: from a Radio Jukebox on it that's playing
     * and sending (on the air, not out of power or kept quiet on someone else's private frequency),
     * whose reach (or a repeater's on the frequency) covers {@code pos}; the nearest if several do.
     * Null if none. (Whether a speaker there hears the frequency at all is up to it.)
     */
    public @Nullable MusicHeard musicHeard(ServerLevel level, int frequency, BlockPos pos) {
        if (music.isEmpty()) {
            return null;
        }
        long gameTime = level.getGameTime();
        MusicHeard best = null;
        long bestDistSq = Long.MAX_VALUE;
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Music> entry : music.entrySet()) {
            BlockPos from = entry.getKey();
            Music song = entry.getValue();
            if (song.frequency() != frequency || !song.playingAt(gameTime)) {
                continue;
            }
            if (level.isLoaded(from) && !(level.getBlockState(from).getBlock() instanceof com.reiko.signaltower.block.RadioJukeboxBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(from);
                continue;
            }
            Transmitter tx = transmitters.get(from);
            if (tx == null || tx.frequency() != frequency || tx.power() <= 0 || isDown(from) || blocked(level, frequency, tx.tunedBy())) {
                continue;
            }
            if (isUplinked(from)) {
                long distSq = horizontalDistSq(from, pos); // v0.61.0: through a satellite, everywhere
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = new MusicHeard(from, song, song.startAt(gameTime));
                }
                continue;
            }
            CachedCoverage cached = musicCoverage.get(from);
            if (cached == null || gameTime - cached.at() >= MUSIC_COVERAGE_TICKS || gameTime < cached.at()
                    || cached.frequency() != frequency || cached.reach() != tx.reach()) {
                cached = new CachedCoverage(gameTime, frequency, tx.reach(), stationCoverage(level, from, frequency, tx.reach()));
                musicCoverage.put(from, cached);
            }
            if (!covers(cached.coverage(), pos)) {
                continue;
            }
            long distSq = horizontalDistSq(from, pos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new MusicHeard(from, song, song.startAt(gameTime));
            }
        }
        if (stale != null) {
            stale.forEach(at -> setMusic(at, null));
        }
        return best;
    }

    /**
     * Each tick: a song that has ended (and doesn't repeat) is taken off, and its jukebox goes off
     * the air, even if its chunk isn't loaded; a repeating one starting again tells the speakers.
     */
    private void tickMusic(long gameTime) {
        if (music.isEmpty()) {
            return;
        }
        List<BlockPos> ended = null;
        for (Map.Entry<BlockPos, Music> entry : music.entrySet()) {
            Music song = entry.getValue();
            if (song.length() <= 0 || gameTime <= song.start()) {
                continue;
            }
            if (!song.loop() && gameTime - song.start() >= song.length()) {
                if (ended == null) ended = new ArrayList<>();
                ended.add(entry.getKey());
            } else if (song.loop() && (gameTime - song.start()) % song.length() == 0) {
                revisions.merge(song.frequency(), 1L, Long::sum); // it starts again: speakers start it again
            }
        }
        if (ended != null) {
            for (BlockPos pos : ended) {
                setMusic(pos, null);
                Transmitter tx = transmitters.get(pos);
                if (tx != null && tx.power() > 0) {
                    updateTransmitter(pos, tx.frequency(), 0, tx.reach(), tx.tunedBy(), tx.compact());
                }
            }
        }
    }

    // ---- History, for the pagers' catch-up ----

    /** The most turn-ons remembered per frequency. */
    public static final int MAX_HISTORY = 32;

    /** What turned a frequency on (v0.35.0), for the frequency log. Saved by name, sent by ordinal: only add at the end. */
    public enum CauseKind {
        UNKNOWN, TRANSMITTER, REMOTE_PULSE, REMOTE_TOGGLE, KEYPAD, SENSOR, GATE, TIMER, RECORDER,
        /** v0.51.0: a Radio Telegraph message (in the log only; it doesn't turn the frequency on). */
        TELEGRAPH,
        /** v0.53.0: a Radio Weather Station's reading. */
        WEATHER_STATION,
        /** v0.60.0: a Radio Jukebox playing a song (the detail is the song). */
        JUKEBOX,
        /** v0.62.0: a Morse message starting, from a Morse Key (keyed by a player, or redstone) or a telegraph keying it. */
        MORSE,
        /** v0.64.0: something said into a Walkie-Talkie (in the log only, like a telegraph message; no position). */
        WALKIE;

        public static CauseKind of(int ordinal) {
            CauseKind[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        static CauseKind named(String name) {
            for (CauseKind kind : values()) {
                if (kind.name().equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * What turned a frequency on: a transmitter (where it is and who tuned it, null if nobody is
     * recorded) or a remote press (whose, by UUID and name; never where they stood, so the log
     * can't be used to find players). {@link #UNKNOWN} for turn-ons saved before v0.35.0.
     */
    public record Cause(CauseKind kind, @Nullable BlockPos pos, @Nullable UUID who, String name, String detail) {
        public static final Cause UNKNOWN = new Cause(CauseKind.UNKNOWN, null, null, "");

        /** No detail. ({@code detail}, v0.38.0: the keypad guest code used, or "".) */
        public Cause(CauseKind kind, @Nullable BlockPos pos, @Nullable UUID who, String name) {
            this(kind, pos, who, name, "");
        }

        public static Cause transmitter(BlockPos pos, @Nullable UUID tunedBy) {
            return new Cause(CauseKind.TRANSMITTER, pos.immutable(), tunedBy, "");
        }

        public static Cause remote(boolean toggle, UUID player, String name) {
            return new Cause(toggle ? CauseKind.REMOTE_TOGGLE : CauseKind.REMOTE_PULSE, null, player, name);
        }

        /** The right code typed on a Code Keypad (v0.37.0): where the keypad is, and who typed it. */
        public static Cause keypad(BlockPos pos, UUID player, String name) {
            return keypad(pos, player, name, "");
        }

        /** {@link #keypad(BlockPos, UUID, String)} with the guest code's name (v0.38.0), or "" for the keypad's own code. */
        public static Cause keypad(BlockPos pos, UUID player, String name, String guestCode) {
            return new Cause(CauseKind.KEYPAD, pos.immutable(), player, name, guestCode == null ? "" : guestCode);
        }

        /** A converter's signal gate opened (v0.41.0): where the converter is, and who tuned it. */
        public static Cause gate(BlockPos pos, @Nullable UUID tunedBy) {
            return new Cause(CauseKind.GATE, pos.immutable(), tunedBy, "");
        }

        /** A Radio Timer Transmitter's clock (v0.42.0): where it is and who tuned it. */
        public static Cause timer(BlockPos pos, @Nullable UUID tunedBy) {
            return new Cause(CauseKind.TIMER, pos.immutable(), tunedBy, "");
        }

        /** A Radio Weather Station's reading (v0.53.0): where it is and who tuned it. */
        public static Cause station(BlockPos pos, @Nullable UUID tunedBy) {
            return new Cause(CauseKind.WEATHER_STATION, pos.immutable(), tunedBy, "");
        }

        /** A Radio Jukebox playing {@code song} (v0.60.0): where it is and who tuned it. */
        /** v0.62.0: Morse keyed at {@code pos} (a Morse Key or a telegraph) by {@code player} ({@code name}); null for redstone. */
        public static Cause morse(BlockPos pos, @Nullable UUID player, String name) {
            return new Cause(CauseKind.MORSE, pos.immutable(), player, name == null ? "" : name);
        }

        public static Cause jukebox(BlockPos pos, @Nullable UUID tunedBy, String song) {
            return new Cause(CauseKind.JUKEBOX, pos.immutable(), tunedBy, "", song == null ? "" : song);
        }

        /** A Radio Signal Recorder playing its recording (v0.44.0): where it is and who tuned it. */
        public static Cause recorder(BlockPos pos, @Nullable UUID tunedBy) {
            return new Cause(CauseKind.RECORDER, pos.immutable(), tunedBy, "");
        }

        /** A Radio Motion Sensor saw someone (v0.39.0): where the sensor is, and who it saw first. */
        public static Cause sensor(BlockPos pos, @Nullable UUID player, String name) {
            return new Cause(CauseKind.SENSOR, pos.immutable(), player, name == null ? "" : name);
        }
    }

    /**
     * One time a frequency turned on: when, where it could be heard, what did it, and the
     * frequency it started on ({@code from}; another one if a converter carried it over, 0 if
     * not known).
     */
    public record TurnOn(long gameTime, long dayTime, List<Coverage> coverage, Cause cause, int from) {
        public TurnOn {
            coverage = List.copyOf(coverage);
        }

        public TurnOn(long gameTime, long dayTime, List<Coverage> coverage) {
            this(gameTime, dayTime, coverage, Cause.UNKNOWN, 0);
        }
    }

    /**
     * Remembers a turn-on, over {@code coverage} plus every repeater it reaches. Converters it
     * reaches pass it on, so it's also remembered on the frequencies they send on.
     */
    private void recordTurnOn(int frequency, List<Coverage> coverage, Cause cause) {
        carried(null, frequency, coverage).areas().forEach((onFrequency, areas) -> {
            ArrayDeque<TurnOn> list = history.computeIfAbsent(onFrequency, f -> new ArrayDeque<>());
            list.addLast(new TurnOn(now, nowDayTime, areas, cause, frequency));
            while (list.size() > MAX_HISTORY) {
                list.removeFirst();
            }
        });
        setDirty();
    }

    /**
     * The times {@code frequency} turned on after {@code sinceGameTime} where a receiver at
     * {@code pos} would have heard it, oldest first. At most {@link #MAX_HISTORY}.
     */
    /** Every remembered turn-on of {@code frequency} in this dimension, oldest first (for the log). */
    public List<TurnOn> history(int frequency) {
        ArrayDeque<TurnOn> list = history.get(frequency);
        return list == null ? List.of() : List.copyOf(list);
    }

    public List<TurnOn> turnOnsSince(int frequency, long sinceGameTime, BlockPos pos) {
        ArrayDeque<TurnOn> list = history.get(frequency);
        if (list == null) {
            return List.of();
        }
        List<TurnOn> heard = new ArrayList<>();
        for (TurnOn turnOn : list) {
            if (turnOn.gameTime() > sinceGameTime && covers(turnOn.coverage(), pos)) {
                heard.add(turnOn);
            }
        }
        return heard;
    }

    // ---- Telegraph messages (v0.51.0) ----

    /** The most telegraph messages remembered per frequency. */
    public static final int MAX_MESSAGES = 16;

    /**
     * One message sent from a Radio Telegraph: when, where it could be read (the telegraph's own
     * reach, towers that relayed it, repeaters on its frequency), the telegraph it came from, who
     * sent it, and the text.
     */
    public record Message(long gameTime, long dayTime, List<Coverage> coverage, BlockPos from, @Nullable UUID who, String name, String text,
                          boolean handheld) {
        public Message {
            coverage = List.copyOf(coverage);
            from = from.immutable();
        }

        public Message(long gameTime, long dayTime, List<Coverage> coverage, BlockPos from, @Nullable UUID who, String name, String text) {
            this(gameTime, dayTime, coverage, from, who, name, text, false);
        }
    }

    private final Map<Integer, ArrayDeque<Message>> messages = new HashMap<>();
    /** Loaded telegraphs by frequency, with who tuned them (not saved; they check in like receivers). */
    private final Map<Integer, Map<BlockPos, TrackedReceiver>> telegraphs = new HashMap<>();
    private final Map<BlockPos, Integer> telegraphFrequencies = new HashMap<>();

    /** Remembers a message sent on {@code frequency} over {@code coverage}, and returns it. */
    public Message recordMessage(int frequency, List<Coverage> coverage, BlockPos from, @Nullable UUID who, String name, String text) {
        return recordMessage(frequency, coverage, from, who, name, text, false);
    }

    /**
     * v0.64.0: {@link #recordMessage(int, List, BlockPos, UUID, String, String)}, said into a
     * Walkie-Talkie when {@code handheld}: {@code from} is where the player stood, which the log doesn't show.
     */
    public Message recordMessage(int frequency, List<Coverage> coverage, BlockPos from, @Nullable UUID who, String name, String text,
                                 boolean handheld) {
        Message message = new Message(now, nowDayTime, coverage, from, who, name, text, handheld);
        ArrayDeque<Message> list = messages.computeIfAbsent(frequency, f -> new ArrayDeque<>());
        list.addLast(message);
        while (list.size() > MAX_MESSAGES) {
            list.removeFirst();
        }
        bump(frequency); // so Status Boards on it look again at once
        setDirty();
        return message;
    }

    /** Every remembered message on {@code frequency} in this dimension, oldest first. */
    public List<Message> messages(int frequency) {
        ArrayDeque<Message> list = messages.get(frequency);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** The messages on {@code frequency} that could be read at {@code pos}, oldest first. */
    public List<Message> messagesAt(int frequency, BlockPos pos) {
        List<Message> heard = new ArrayList<>();
        for (Message message : messages(frequency)) {
            if (covers(message.coverage(), pos)) {
                heard.add(message);
            }
        }
        return heard;
    }

    /** The newest message on {@code frequency} readable at {@code pos} sent after {@code sinceGameTime}, or null. */
    public @Nullable Message lastMessageAt(int frequency, BlockPos pos, long sinceGameTime) {
        List<Message> heard = messagesAt(frequency, pos);
        Message last = heard.isEmpty() ? null : heard.get(heard.size() - 1);
        return last != null && last.gameTime() > sinceGameTime ? last : null;
    }

    /** A loaded telegraph says which frequency it's on (first tick, re-tune, new tuner). */
    public void trackTelegraph(BlockPos pos, int frequency, @Nullable UUID tunedBy) {
        BlockPos key = pos.immutable();
        Integer previous = telegraphFrequencies.put(key, frequency);
        if (previous != null && previous != frequency) {
            Map<BlockPos, TrackedReceiver> old = telegraphs.get(previous);
            if (old != null) {
                old.remove(key);
                if (old.isEmpty()) telegraphs.remove(previous);
            }
        }
        telegraphs.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, new TrackedReceiver(key, tunedBy));
    }

    /** A telegraph was broken or its chunk unloaded. */
    public void untrackTelegraph(BlockPos pos) {
        Integer previous = telegraphFrequencies.remove(pos);
        if (previous != null) {
            Map<BlockPos, TrackedReceiver> old = telegraphs.get(previous);
            if (old != null) {
                old.remove(pos);
                if (old.isEmpty()) telegraphs.remove(previous);
            }
        }
    }

    /** The loaded telegraphs on {@code frequency}. */
    public List<TrackedReceiver> telegraphsOn(int frequency) {
        Map<BlockPos, TrackedReceiver> on = telegraphs.get(frequency);
        return on == null ? List.of() : List.copyOf(on.values());
    }

    /** Ends pulses whose time is up. Called every server tick for each dimension. */
    public void tick(long gameTime, long dayTime) {
        now = gameTime;
        nowDayTime = dayTime;
        tickMusic(gameTime); // v0.60.0
        if (pulses.isEmpty()) {
            return;
        }
        var it = pulses.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (gameTime >= entry.getValue().expiresAtGameTime()) {
                it.remove();
                bump(entry.getKey());
            }
        }
    }

    // ---- Receiver tracking (for the remotes' signal light) ----

    /** A loaded receiver says which frequency it's on. Called on its first tick and when re-tuned. */
    public void trackReceiver(BlockPos pos, int frequency) {
        trackReceiver(pos, frequency, null);
    }

    /** {@link #trackReceiver(BlockPos, int)}, with who tuned or placed it (it isn't counted on an encrypted frequency it can't hear). */
    public void trackReceiver(BlockPos pos, int frequency, @Nullable UUID tunedBy) {
        trackReceiver(pos, frequency, tunedBy, -1);
    }

    /**
     * {@link #trackReceiver(BlockPos, int, UUID)} for a receiver that also listens on {@code second}
     * (a Radio Logic Receiver, v0.40.0; -1 for none): it counts as a receiver on both.
     */
    public void trackReceiver(BlockPos pos, int frequency, @Nullable UUID tunedBy, int second) {
        BlockPos key = pos.immutable();
        if (tunedBy == null) {
            receiverTuners.remove(key);
        } else {
            receiverTuners.put(key, tunedBy);
        }
        Integer previous = receivers.put(key, frequency);
        Integer previousSecond = second > 0 ? receiverSeconds.put(key, second) : receiverSeconds.remove(key);
        if (previous != null && previous != frequency && previous != second) {
            removeReceiverFromIndex(key, previous);
        }
        if (previousSecond != null && previousSecond != frequency && previousSecond != second) {
            removeReceiverFromIndex(key, previousSecond);
        }
        receiversByFrequency.computeIfAbsent(frequency, f -> new HashSet<>()).add(key);
        if (second > 0) {
            receiversByFrequency.computeIfAbsent(second, f -> new HashSet<>()).add(key);
        }
    }

    /** A receiver was broken or its chunk unloaded. */
    public void untrackReceiver(BlockPos pos) {
        receiverTuners.remove(pos);
        Integer previous = receivers.remove(pos);
        if (previous != null) {
            removeReceiverFromIndex(pos, previous);
        }
        Integer previousSecond = receiverSeconds.remove(pos);
        if (previousSecond != null) {
            removeReceiverFromIndex(pos, previousSecond);
        }
    }

    /**
     * For the Signal Meter (v0.40.0): how many blocks {@code pos} is inside the reach of the sender
     * on {@code frequency} whose reach goes farthest past it (0 = right at the edge), or -1 if no
     * sender reaches it. Transmitters count whether they're on or off (so receivers can be placed
     * before switching on); ones kept quiet on someone else's private frequency, and relays that
     * are silenced or deaf, don't. Measured like the reach ring.
     */
    public int edgeMargin(ServerLevel level, int frequency, BlockPos pos) {
        int best = -1;
        Map<BlockPos, Transmitter> onFrequency = byFrequency.get(frequency);
        if (onFrequency != null) {
            for (Map.Entry<BlockPos, Transmitter> entry : onFrequency.entrySet()) {
                if (!blocked(level, frequency, entry.getValue().tunedBy())) {
                    Transmitter tx = entry.getValue();
                    int reach = tx.compact() ? reachNow(level, entry.getKey(), tx)
                            : (int) Math.min(Integer.MAX_VALUE, linkReach(level, entry.getKey(), tx.reach(), pos, ear(level, pos)));
                    best = Math.max(best, margin(entry.getKey(), reach, pos));
                }
            }
        }
        for (Phantom phantom : phantoms(level, frequency)) {
            best = Math.max(best, margin(phantom.pos(), phantom.reach(), pos));
        }
        for (Map.Entry<BlockPos, Repeater> entry : repeaters.entrySet()) {
            if (entry.getValue().output() == frequency && !isSilenced(level, entry.getKey()) && !isDeaf(level, entry.getKey())) {
                best = Math.max(best, margin(entry.getKey(),
                        (int) Math.min(Integer.MAX_VALUE, linkReach(level, entry.getKey(), entry.getValue().reach(), pos, ear(level, pos))), pos));
            }
        }
        return best;
    }

    private static int margin(BlockPos origin, int reach, BlockPos pos) {
        long dx = pos.getX() - origin.getX();
        long dz = pos.getZ() - origin.getZ();
        long distSq = dx * dx + dz * dz;
        if (distSq > (long) reach * reach) {
            return -1;
        }
        return Math.max(0, reach - (int) Math.floor(Math.sqrt((double) distSq)));
    }

    private void removeReceiverFromIndex(BlockPos pos, int frequency) {
        Set<BlockPos> onFrequency = receiversByFrequency.get(frequency);
        if (onFrequency != null) {
            onFrequency.remove(pos);
            if (onFrequency.isEmpty()) {
                receiversByFrequency.remove(frequency);
            }
        }
    }

    /** How many loaded receivers on {@code frequency} are within {@code reach} (horizontally) of {@code origin}. */
    public int countReceivers(int frequency, BlockPos origin, int reach) {
        return countReceivers(frequency, ownCoverage(origin, reach));
    }

    /** How many loaded receivers on {@code frequency} any area of {@code coverage} reaches. */
    public int countReceivers(int frequency, List<Coverage> coverage) {
        Set<BlockPos> onFrequency = receiversByFrequency.get(frequency);
        if (onFrequency == null) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : onFrequency) {
            if (covers(coverage, pos) && !deaf(null, frequency, receiverTuners.get(pos))) {
                count++;
            }
        }
        return count;
    }

    /**
     * What the remotes' locator shows for one frequency: how many loaded receivers are within
     * {@code reach} of {@code origin}, and where the nearest one is. The nearest one is only
     * reported if it's within twice the reach, so the locator can't find receivers far away.
     * With relay towers (see the 4-argument version), receivers a tower reaches count as in range
     * and can be pointed to too, however far away they are.
     *
     * @param nearestDistance horizontal distance in blocks, or -1 if none is near enough
     * @param direction 0-7 for N, NE, E, SE, S, SW, W, NW, or -1 if right here or none
     */
    public record ReceiverScan(int inRange, int nearestDistance, int direction, boolean nearestInRange) {}

    public ReceiverScan scanReceivers(int frequency, BlockPos origin, int reach) {
        return scanReceivers(frequency, origin, reach, ownCoverage(origin, reach));
    }

    /** The locator with relay towers: {@code coverage} as returned by {@link #remoteCoverage}. */
    public ReceiverScan scanReceivers(int frequency, BlockPos origin, int reach, List<Coverage> coverage) {
        Set<BlockPos> onFrequency = receiversByFrequency.get(frequency);
        if (onFrequency == null) {
            return new ReceiverScan(0, -1, -1, false);
        }
        long hintSq = 4L * reach * reach;
        int inRange = 0;
        long bestSq = Long.MAX_VALUE;
        long bestDx = 0;
        long bestDz = 0;
        boolean bestInRange = false;
        for (BlockPos pos : onFrequency) {
            if (deaf(null, frequency, receiverTuners.get(pos))) {
                continue; // it can't hear this encrypted frequency, so it isn't listening
            }
            long dx = pos.getX() - origin.getX();
            long dz = pos.getZ() - origin.getZ();
            long distSq = dx * dx + dz * dz;
            boolean covered = covers(coverage, pos);
            if (covered) {
                inRange++;
            }
            if ((covered || distSq <= hintSq) && distSq < bestSq) {
                bestSq = distSq;
                bestDx = dx;
                bestDz = dz;
                bestInRange = covered;
            }
        }
        if (bestSq == Long.MAX_VALUE) {
            return new ReceiverScan(inRange, -1, -1, false);
        }
        int distance = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(bestSq)));
        return new ReceiverScan(inRange, distance, compassDirection(bestDx, bestDz), bestInRange);
    }

    /** 0-7 for N, NE, E, SE, S, SW, W, NW (north is -Z), or -1 when there's no offset. */
    public static int compassDirection(long dx, long dz) {
        if (dx == 0 && dz == 0) {
            return -1;
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, clockwise
        return Math.floorMod((int) Math.round(degrees / 45.0), 8);
    }

    // ---- Receivers ----

    /** Where the strongest signal on a frequency comes from, for the Signal Scanner. (Sent by ordinal: only add at the end.) */
    public enum Source { TRANSMITTER, REMOTE_PULSE, REMOTE_TOGGLE, REPEATER, CONVERTER }

    /**
     * One frequency the Signal Scanner hears. {@code transmitters} is how many transmitters,
     * repeaters and converters send on it here. For a {@link Source#TRANSMITTER},
     * {@link Source#REPEATER} or {@link Source#CONVERTER},
     * {@code distance} and {@code direction} point to the strongest one (the nearest, if several
     * are equally strong); for a remote signal they're -1, so the scanner never points at players.
     */
    public record HeardFrequency(int frequency, int power, Source source, int transmitters, int distance, int direction) {}

    /** The most frequencies one scan reports. */
    public static final int MAX_SCAN_RESULTS = 64;

    /**
     * Per frequency, the strongest transmitter or repeater reaching a spot (the nearest among
     * equally strong ones, a transmitter before a repeater), and how many reach it.
     */
    private record BestTransmitter(int power, long distSq, BlockPos at, int count, Source source) {
        boolean beats(BestTransmitter other) {
            if (power != other.power) return power > other.power;
            if (distSq != other.distSq) return distSq < other.distSq;
            return source == Source.TRANSMITTER && other.source != Source.TRANSMITTER;
        }
    }

    private static void offer(Map<Integer, BestTransmitter> best, int frequency, BestTransmitter candidate) {
        BestTransmitter old = best.get(frequency);
        if (old == null) {
            best.put(frequency, candidate);
        } else {
            BestTransmitter winner = candidate.beats(old) ? candidate : old;
            best.put(frequency, new BestTransmitter(winner.power(), winner.distSq(), winner.at(), old.count() + 1, winner.source()));
        }
    }

    private Map<Integer, BestTransmitter> bestTransmitters(ServerLevel level, BlockPos pos) {
        Map<Integer, BestTransmitter> best = new HashMap<>();
        int ear = ear(level, pos);
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Transmitter> entry : transmitters.entrySet()) {
            BlockPos at = entry.getKey();
            Transmitter tx = entry.getValue();
            long distSq = horizontalDistSq(at, pos);
            long reach = tx.compact() ? reachNow(level, at, tx) : linkReach(level, at, tx.reach(), pos, ear);
            if (distSq > reach * reach || blocked(level, tx.frequency(), tx.tunedBy())) {
                continue;
            }
            if (level.isLoaded(at) && !(level.getBlockState(at).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(at);
                continue;
            }
            Bundle bundle = bundles.get(at);
            if (bundle != null) {
                // v0.54.0: each color on its own frequency.
                for (int c = 0; c < BUNDLE_SIZE && bundle.base() + c <= Frequency.MAX; c++) {
                    int sent = bundle.channels()[c];
                    int heard = sent > 0 && !isDown(at) && !blocked(level, bundle.base() + c, tx.tunedBy())
                            ? Math.max(0, fade(sent, fadeFraction(distSq, reach)) - hillLoss(level, at, pos)) : 0;
                    offer(best, bundle.base() + c, new BestTransmitter(heard, distSq, at, 1, Source.TRANSMITTER));
                }
                continue;
            }
            int power = tx.power() > 0 && !isDown(at)
                    ? Math.max(0, (tx.compact() ? tx.power() : fade(tx.power(), fadeFraction(distSq, reach))) - hillLoss(level, at, pos)) : 0;
            offer(best, tx.frequency(), new BestTransmitter(power, distSq, at, 1, Source.TRANSMITTER));
        }
        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        for (Phantom phantom : phantoms(level, -1)) {
            // v0.50.0: from another dimension, listed where it arrives here.
            long distSq = horizontalDistSq(phantom.pos(), pos);
            if (distSq <= (long) phantom.reach() * phantom.reach()) {
                offer(best, phantom.frequency(), new BestTransmitter(fade(phantom.power(), fadeFraction(distSq, phantom.reach())),
                        distSq, phantom.pos(), 1, Source.TRANSMITTER));
            }
        }
        // Resolve first: that can forget repeaters whose block is gone. Each is listed under the frequency it sends on.
        Map<BlockPos, Integer> sending = resolve(level);
        for (Map.Entry<BlockPos, Repeater> entry : repeaters.entrySet()) {
            BlockPos at = entry.getKey();
            Repeater repeater = entry.getValue();
            long distSq = horizontalDistSq(at, pos);
            long reach = linkReach(level, at, repeater.reach(), pos, ear);
            if (distSq > reach * reach || blocked(level, repeater.output(), repeater.tunedBy())) {
                continue;
            }
            int sends = sending.getOrDefault(at, 0);
            offer(best, repeater.output(), new BestTransmitter(sends > 0 ? Math.max(0, fade(sends, fadeFraction(distSq, reach)) - hillLoss(level, at, pos)) : 0, distSq, at, 1,
                    repeater.converts() ? Source.CONVERTER : Source.REPEATER));
        }
        return best;
    }

    /**
     * The transmitter or repeater the Signal Scanner points to for {@code frequency} at
     * {@code pos}: the strongest one whose reach covers it, the nearest among equally strong ones.
     * Empty if none does.
     */
    public Optional<BlockPos> strongestTransmitter(ServerLevel level, BlockPos pos, int frequency) {
        BestTransmitter best = bestTransmitters(level, pos).get(frequency);
        return best == null ? Optional.empty() : Optional.of(best.at());
    }

    /** The frequency of the transmitter at {@code pos}, or empty if there's none in the network there. */
    public OptionalInt transmitterFrequencyAt(BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        return tx == null ? OptionalInt.empty() : OptionalInt.of(tx.frequency());
    }

    /**
     * Everything a receiver at {@code pos} could hear: every frequency with a transmitter or
     * repeater whose reach covers it (including silent ones, with power 0) or a remote signal
     * covering it. Strongest first, then lowest frequency, at most {@link #MAX_SCAN_RESULTS}.
     */
    public List<HeardFrequency> scanAt(ServerLevel level, BlockPos pos) {
        return scanAt(level, pos, null);
    }

    /**
     * {@link #scanAt(ServerLevel, BlockPos)} for a scanner carried by {@code viewer}: encrypted
     * frequencies they aren't a member of are left out ({@code null}: none are).
     */
    public List<HeardFrequency> scanAt(ServerLevel level, BlockPos pos, @Nullable UUID viewer) {
        Map<Integer, BestTransmitter> best = bestTransmitters(level, pos);

        Map<Integer, Source> remotes = new HashMap<>();
        pulses.forEach((frequency, pulse) -> {
            if (pulse.reaches(level, pos)) remotes.put(frequency, Source.REMOTE_PULSE);
        });
        latches.forEach((frequency, latch) -> {
            if (latch.reaches(level, pos)) remotes.put(frequency, Source.REMOTE_TOGGLE);
        });

        Set<Integer> frequencies = new HashSet<>(best.keySet());
        frequencies.addAll(remotes.keySet());
        frequencies.removeIf(frequency -> deaf(level, frequency, viewer));
        List<HeardFrequency> heard = new ArrayList<>();
        for (int frequency : frequencies) {
            BestTransmitter tx = best.get(frequency);
            int count = tx == null ? 0 : tx.count();
            Source remote = remotes.get(frequency);
            int jam = jamming(level, frequency, pos); // v0.46.0
            if (remote != null) {
                heard.add(new HeardFrequency(frequency, Math.max(0, 15 - jam), remote, count, -1, -1));
            } else {
                long dx = tx.at().getX() - pos.getX();
                long dz = tx.at().getZ() - pos.getZ();
                int distance = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(tx.distSq())));
                heard.add(new HeardFrequency(frequency, Math.max(0, tx.power() - jam), tx.source(),
                        count, distance, distance == 0 ? -1 : compassDirection(dx, dz)));
            }
        }
        heard.sort(Comparator.comparingInt(HeardFrequency::power).reversed()
                .thenComparingInt(HeardFrequency::frequency));
        return heard.size() > MAX_SCAN_RESULTS ? new ArrayList<>(heard.subList(0, MAX_SCAN_RESULTS)) : heard;
    }

    /**
     * Finds the strongest signal on {@code frequency} that reaches {@code receiverPos}. A
     * transmitter, repeater or converter reaches every receiver within its reach, measured
     * horizontally, at any height. A converter is heard on the frequency it sends on.
     * {@code transmittersInReach} counts repeaters and converters too.
     */
    public Reception listen(ServerLevel level, int frequency, BlockPos receiverPos) {
        return listen(level, frequency, receiverPos, null);
    }

    /**
     * {@link #listen(ServerLevel, int, BlockPos)} for a receiver tuned (or placed) by
     * {@code listener}, or a pager carried by them: nothing at all (no signal, no towers) on
     * someone else's encrypted frequency. {@code null} listens like anyone could.
     */
    public Reception listen(ServerLevel level, int frequency, BlockPos receiverPos, @Nullable UUID listener) {
        return listen(level, frequency, receiverPos, listener, true);
    }

    /**
     * {@link #listen(ServerLevel, int, BlockPos, UUID)}; {@code fades} false for a repeater or
     * converter listening, which hears signals unfaded and sends them on at full strength (v0.48.0).
     */
    private Reception listen(ServerLevel level, int frequency, BlockPos receiverPos, @Nullable UUID listener, boolean fades) {
        if (deaf(level, frequency, listener)) {
            return new Reception(0, 0);
        }
        Reception heard = listenRelayed(level, frequency, receiverPos, fades);
        // v0.46.0: a Radio Jammer here takes its strength off what's heard.
        int jam = jamming(level, frequency, receiverPos);
        if (jam <= 0) {
            return heard;
        }
        return new Reception(Math.max(0, heard.power() - jam), heard.transmittersInReach(), heard.hills(), jam, heard.lifted(), heard.faded());
    }

    /** {@link #listen} before deafness and jammers: transmitters, remote presses, repeaters and converters. */
    private Reception listenRelayed(ServerLevel level, int frequency, BlockPos receiverPos, boolean fades) {
        Reception direct = listenDirect(level, frequency, receiverPos, fades);
        if (!repeatersByOutput.containsKey(frequency)) {
            return direct;
        }
        Map<BlockPos, Integer> repeating = resolve(level);
        Map<BlockPos, Repeater> onFrequency = repeatersByOutput.get(frequency);
        if (onFrequency == null) { // resolving forgot the last ones (their blocks were gone)
            return direct;
        }
        Best best = new Best(direct);
        int heard = direct.transmittersInReach();
        int ear = ear(level, receiverPos);
        for (Map.Entry<BlockPos, Repeater> entry : onFrequency.entrySet()) {
            BlockPos pos = entry.getKey();
            Repeater repeater = entry.getValue();
            long distSq = horizontalDistSq(pos, receiverPos);
            long own = reachNow(level, pos, repeater.reach());
            long reach = linkReach(level, pos, repeater.reach(), receiverPos, ear);
            if (pos.equals(receiverPos) || distSq > reach * reach || blocked(level, frequency, repeater.tunedBy())) {
                continue;
            }
            heard++;
            best.offer(level, repeating.getOrDefault(pos, 0), pos, receiverPos, distSq > own * own,
                    fades ? fadeFraction(distSq, reach) : -1);
        }
        return best.reception(heard);
    }

    /**
     * The strongest signal so far after hills (v0.46.0), and what it was before them. Among equally
     * strong ones, the one that started stronger wins, so a signal hills block completely still
     * shows how much they took.
     */
    private final class Best {
        int power;
        int raw;
        /** What was left of {@code raw} after fading, before hills (v0.48.0). */
        int faded;
        /** The winner only reaches because the listener is higher than its tower (v0.47.0). */
        boolean lifted;

        Best() {}

        Best(Reception start) {
            power = start.power();
            faded = start.power() + start.hills();
            raw = faded + start.faded();
            lifted = start.lifted();
        }

        /**
         * A signal of {@code sent} from {@code from}, heard at {@code at}; {@code viaHeight}: only in
         * reach thanks to the listener's height; {@code fadeAt}: how far out in its reach the listener
         * is (0-1), or -1 if it doesn't fade.
         */
        void offer(ServerLevel level, int sent, BlockPos from, BlockPos at, boolean viaHeight, double fadeAt) {
            if (sent < power || sent == power && (sent < raw || !lifted || viaHeight)) {
                return; // can't beat it (fading and hills only take away), so don't measure them
            }
            int left = fadeAt < 0 ? sent : fade(sent, fadeAt);
            if (left < power) {
                return;
            }
            int after = Math.max(0, left - hillLoss(level, from, at));
            if (after > power || after == power && (sent > raw || sent == raw && lifted && !viaHeight)) {
                power = after;
                raw = sent;
                faded = left;
                lifted = viaHeight;
            }
        }

        /** A signal of {@code sent} from another dimension: it fades ({@code fadeAt} 0-1, or -1), but hills don't block it. */
        void offerFar(int sent, double fadeAt) {
            int left = fadeAt < 0 ? sent : fade(sent, fadeAt);
            if (left > power || left == power && (sent > raw || lifted)) {
                power = left;
                raw = sent;
                faded = left;
                lifted = false;
            }
        }

        void offerClear(int sent) {
            if (sent > power || sent == power && (sent > raw || lifted)) {
                power = sent;
                raw = sent;
                faded = sent;
                lifted = false;
            }
        }

        Reception reception(int heard) {
            return new Reception(power, heard, faded - power, 0, lifted, raw - faded);
        }
    }

    // ---- Signal fade (v0.48.0) ----

    /** How far out in a reach of {@code reach} a listener {@code distSq} (squared, horizontally) away is: 0 at the tower, 1 at the edge. */
    private static double fadeFraction(long distSq, long reach) {
        return reach <= 0 ? 1.0 : Math.min(1.0, Math.sqrt((double) distSq) / reach);
    }

    /**
     * What's left of a signal of {@code sent} heard {@code fraction} of the way out to the edge of
     * its tower's reach: all of it up to {@code signalFadeStartPercent}, then less and less, down to
     * {@code (15 - signalFadeAtEdge) / 15} of it at the edge; never below 1 (inside reach a signal
     * is always heard).
     */
    public static int fade(int sent, double fraction) {
        if (sent <= 0 || Boolean.FALSE.equals(fadeForTests)) {
            return sent;
        }
        int start = fadeSetting(com.reiko.signaltower.Config.SIGNAL_FADE_START_PERCENT);
        int atEdge = fadeSetting(com.reiko.signaltower.Config.SIGNAL_FADE_AT_EDGE);
        double from = start / 100.0;
        if (start >= 100 || atEdge <= 0 || fraction <= from) {
            return sent;
        }
        double t = Math.min(1.0, (fraction - from) / (1.0 - from));
        return Math.max(1, (int) Math.round(sent * (1.0 - t * atEdge / 15.0)));
    }

    private static @Nullable Boolean fadeForTests;

    /**
     * For the GameTests only: false turns fading off whatever the config says (most tests check
     * other things, at exact strengths); null leaves it to the config.
     */
    public static void fadeForTests(@Nullable Boolean on) {
        fadeForTests = on;
    }

    private static int fadeSetting(net.neoforged.neoforge.common.ModConfigSpec.IntValue setting) {
        return com.reiko.signaltower.Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    /** What {@code receiverPos} hears straight from transmitters and remote presses, leaving repeaters out. */
    private Reception listenDirect(ServerLevel level, int frequency, BlockPos receiverPos, boolean fades) {
        Best best = new Best();
        int heard = 0;
        for (RemoteSignal remote : new RemoteSignal[] {pulses.get(frequency), latches.get(frequency)}) {
            if (remote != null && remote.reaches(level, receiverPos)) {
                best.offerClear(15); // remote presses don't mind hills
                heard++;
            }
        }
        for (Phantom phantom : phantoms(level, frequency)) {
            // v0.50.0: a transmitter in another dimension sending into this one, from where it arrives here (no hills).
            long distSq = horizontalDistSq(phantom.pos(), receiverPos);
            long reach = phantom.reach();
            if (distSq <= reach * reach) {
                heard++;
                best.offerFar(phantom.power(), fades ? fadeFraction(distSq, reach) : -1);
            }
        }
        Map<BlockPos, Transmitter> onFrequency = byFrequency.get(frequency);
        if (onFrequency == null) {
            return best.reception(heard);
        }
        List<BlockPos> stale = null;
        int ear = ear(level, receiverPos);

        for (Map.Entry<BlockPos, Transmitter> entry : onFrequency.entrySet()) {
            BlockPos pos = entry.getKey();
            Transmitter tx = entry.getValue();
            long dx = pos.getX() - receiverPos.getX();
            long dz = pos.getZ() - receiverPos.getZ();
            long distSq = dx * dx + dz * dz;
            long own = reachNow(level, pos, tx);
            long reach = tx.compact() ? own : linkReach(level, pos, tx.reach(), receiverPos, ear);
            if (distSq > reach * reach || blocked(level, frequency, tx.tunedBy())) {
                continue;
            }
            // Self-repair: drop entries whose block is gone (for example removed by a world editor).
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(pos);
                continue;
            }
            heard++;
            if (isUplinked(pos)) {
                best.offerFar(isDown(pos) ? 0 : tx.power(), -1); // v0.61.0: from the satellite: full strength, no hills
                continue;
            }
            // v0.48.0: it fades toward the edge, unless a relay is listening (or it's a compact one).
            best.offer(level, isDown(pos) ? 0 : tx.power(), pos, receiverPos, distSq > own * own,
                    fades && !tx.compact() ? fadeFraction(distSq, reach) : -1);
        }

        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        return best.reception(heard);
    }

    // ---- Radio Jammers (v0.46.0) ----

    /** What the Radio Jammer at {@code pos} does now: placed, re-tuned, its redstone or reach changed, or it checks in. */
    public void updateJammer(BlockPos pos, int frequency, int power, int reach, @Nullable UUID tunedBy) {
        BlockPos key = pos.immutable();
        Jammer next = new Jammer(frequency, power, reach, tunedBy);
        Jammer previous = jammers.put(key, next);
        if (next.equals(previous)) {
            return;
        }
        if (previous != null && previous.frequency() != frequency) {
            removeJammerFromIndex(key, previous.frequency());
        }
        jammersByFrequency.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, next);
        relayEpoch++; // repeaters and converters inside it may hear less
        bump(frequency);
        setDirty();
    }

    public void removeJammer(BlockPos pos) {
        dishes.remove(pos);
        forgetPower(pos);
        Jammer previous = jammers.remove(pos);
        if (previous != null) {
            removeJammerFromIndex(pos, previous.frequency());
            setDirty();
        }
        forgetStorms(pos);
    }

    private void removeJammerFromIndex(BlockPos pos, int frequency) {
        Map<BlockPos, Jammer> onFrequency = jammersByFrequency.get(frequency);
        if (onFrequency != null) {
            onFrequency.remove(pos);
            if (onFrequency.isEmpty()) {
                jammersByFrequency.remove(frequency);
            }
        }
        relayEpoch++;
        bump(frequency);
    }

    /** The Radio Jammer at {@code pos}, or null. */
    public @Nullable Jammer jammerAt(BlockPos pos) {
        return jammers.get(pos);
    }

    /** Every Radio Jammer on {@code frequency} whose block is still there (or not loaded), by position. For the audit. */
    public Map<BlockPos, Jammer> jammersOn(ServerLevel level, int frequency) {
        Map<BlockPos, Jammer> result = new java.util.TreeMap<>();
        jammersByFrequency.getOrDefault(frequency, Map.of()).forEach((pos, jammer) -> {
            if (!staleJammer(level, pos)) {
                result.put(pos, jammer);
            }
        });
        return result;
    }

    /**
     * How strongly {@code frequency} is jammed at {@code pos}: the strongest running jammer on it
     * whose reach (in the weather where it stands) covers it, minus the hills between; 0 if none.
     * Jammers kept quiet on someone else's private frequency don't count.
     */
    public int jamming(@Nullable ServerLevel level, int frequency, BlockPos pos) {
        Map<BlockPos, Jammer> onFrequency = jammersByFrequency.get(frequency);
        if (onFrequency == null) {
            return 0;
        }
        int best = 0;
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Jammer> entry : onFrequency.entrySet()) {
            BlockPos at = entry.getKey();
            Jammer jammer = entry.getValue();
            if (jammer.power() <= best || isDown(at)) {
                continue;
            }
            long reach = reachNow(level, at, dishReachToward(at, jammer.reach(), pos));
            long distSq = horizontalDistSq(at, pos);
            if (distSq > reach * reach || blocked(level, frequency, jammer.tunedBy())) {
                continue;
            }
            if (staleJammer(level, at)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(at);
                continue;
            }
            best = Math.max(best, fade(jammer.power(), fadeFraction(distSq, reach)) - hillLoss(level, at, pos));
        }
        if (stale != null) {
            stale.forEach(this::removeJammer);
        }
        return best;
    }

    /** True if {@code pos} is loaded and holds no Radio Jammer any more. */
    private boolean staleJammer(@Nullable ServerLevel level, BlockPos pos) {
        ServerLevel at = level != null ? level : world;
        return at != null && at.isLoaded(pos) && !(at.getBlockState(pos).getBlock() instanceof com.reiko.signaltower.block.RadioJammerBlock);
    }

    // ---- Receiver height and lightning (v0.47.0) ----

    /** When lightning knocked out a tower, by position: the game time it comes back (not saved: it only lasts seconds). */
    private final Map<BlockPos, Long> outages = new HashMap<>();

    /**
     * How far something listening at {@code pos} could hear a tower lower down (v0.47.0): the reach
     * a transmitter would have there ({@link RadioHelper#reachAt}, so by its height above sea level,
     * capped by maxReach), times {@code receiverHeightPercent}. 0 when that's switched off.
     */
    public int ear(@Nullable ServerLevel level, BlockPos pos) {
        ServerLevel at = level != null ? level : world;
        var setting = com.reiko.signaltower.Config.RECEIVER_HEIGHT_PERCENT;
        int percent = com.reiko.signaltower.Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
        if (percent <= 0 || at == null) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(RadioHelper.reachAt(at, pos) * percent / 100.0));
    }

    /**
     * How far a tower at {@code towerPos} (stored, clear-weather reach {@code reach}) reaches the
     * listener at {@code listener} with {@code ear}: its own reach, or, if the listener stands
     * higher than the tower, as far as a transmitter at the listener's height would reach, whichever
     * is farther; in the weather where the tower stands. (Not for compact transmitters.)
     */
    public long linkReach(@Nullable ServerLevel level, BlockPos towerPos, int reach, BlockPos listener, int ear) {
        if (isUplinked(towerPos)) {
            return UPLINK_REACH; // v0.61.0: through a satellite, the whole dimension
        }
        int base = dishReachToward(towerPos, reach, listener);
        return reachNow(level, towerPos, listener.getY() > towerPos.getY() ? Math.max(base, ear) : base);
    }

    // ---- Dish Antennas (v0.48.0) ----

    /** Where each tower with a Dish Antenna points, by position (its block state's aim; saved, for unloaded towers). */
    private final Map<BlockPos, Integer> dishes = new HashMap<>();

    /** The tower at {@code pos} has a dish aimed {@code aim} (0-15), or none ({@code aim} < 0). Called by its block entity. */
    public void setDish(BlockPos pos, int aim) {
        Integer previous = aim < 0 ? dishes.remove(pos) : dishes.put(pos.immutable(), aim & 15);
        if (previous == null ? aim >= 0 : previous != aim) {
            relayEpoch++;
            bumpTower(pos);
            setDirty();
        }
    }

    /** Where the dish on the tower at {@code pos} points, or null if it has none. */
    public @Nullable Integer dishAim(BlockPos pos) {
        return dishes.get(pos);
    }

    /** A tower's (clear-weather) reach toward {@code target}: times the dish multiplier if it has a dish aimed that way. */
    public int dishReachToward(BlockPos towerPos, int reach, BlockPos target) {
        Integer aim = dishes.get(towerPos);
        if (aim == null || !AntennaTier.inBeam(target.getX() - towerPos.getX(), target.getZ() - towerPos.getZ(), aim)) {
            return reach;
        }
        return Math.max(reach, AntennaTier.dishReach(reach));
    }

    private static long square(long value) {
        return value * value;
    }

    /** Lightning struck the tower at {@code pos} (v0.47.0): it sends (and relays, and jams) nothing until {@code untilGameTime}. */
    public void knockOut(BlockPos pos, long untilGameTime) {
        BlockPos key = pos.immutable();
        outages.merge(key, untilGameTime, Math::max);
        relayEpoch++;
        bumpTower(key);
    }

    /** True while lightning has the tower at {@code pos} knocked out. */
    public boolean isKnockedOut(BlockPos pos) {
        Long until = outages.get(pos);
        return until != null && now < until;
    }

    /** Ticks left until the tower at {@code pos} is back (0 if it isn't knocked out). */
    public long outageTicks(BlockPos pos) {
        Long until = outages.get(pos);
        return until == null ? 0 : Math.max(0, until - now);
    }

    /** Forgets outages that are over, bumping their frequencies; returns where they were (to refresh their blocks). */
    public List<BlockPos> endOutages() {
        if (outages.isEmpty()) {
            return List.of();
        }
        List<BlockPos> ended = new ArrayList<>();
        outages.entrySet().removeIf(entry -> {
            if (now >= entry.getValue()) {
                ended.add(entry.getKey());
                return true;
            }
            return false;
        });
        if (!ended.isEmpty()) {
            relayEpoch++;
            ended.forEach(this::bumpTower);
        }
        return ended;
    }

    /** Positions that are knocked out right now. */
    public Set<BlockPos> knockedOut() {
        Set<BlockPos> result = new HashSet<>();
        outages.forEach((pos, until) -> {
            if (now < until) result.add(pos);
        });
        return result;
    }

    /** Every tower lightning can strike (v0.47.0): full-size transmitters, repeaters, converters and jammers. */
    public List<BlockPos> lightningTargets() {
        List<BlockPos> result = new ArrayList<>();
        transmitters.forEach((pos, tx) -> {
            if (!tx.compact()) result.add(pos);
        });
        result.addAll(repeaters.keySet());
        result.addAll(jammers.keySet());
        return result;
    }

    // ---- Cross-dimension links (v0.50.0) ----

    /** Transmitters with an Ender Antenna, by position: the dimension each also sends into (saved). */
    private final Map<BlockPos, ResourceKey<Level>> links = new HashMap<>();
    /** True while telling other dimensions' networks about a change, so they don't tell this one back. */
    private static boolean linking;

    /** A transmitter in another dimension sending into this one, placed where it arrives here. */
    public record Phantom(BlockPos pos, int frequency, int power, int reach, ResourceKey<Level> from, BlockPos source) {}

    /** The transmitter at {@code pos} also sends into {@code target}, or into no other dimension ({@code null}). Called by its block entity. */
    public void setLink(BlockPos pos, @Nullable ResourceKey<Level> target) {
        ResourceKey<Level> previous = target == null ? links.remove(pos) : links.put(pos.immutable(), target);
        if (!java.util.Objects.equals(previous, target)) {
            Transmitter tx = transmitters.get(pos);
            if (tx != null) {
                Bundle bundle = bundles.get(pos);
                for (int c = 0; c < (bundle != null ? BUNDLE_SIZE : 1) && tx.frequency() + c <= Frequency.MAX; c++) {
                    bumpLinked(previous, tx.frequency() + c);
                    bumpLinked(target, tx.frequency() + c);
                }
            }
            setDirty();
        }
    }

    /** The dimension the transmitter at {@code pos} also sends into, or null. */
    public @Nullable ResourceKey<Level> linkTarget(BlockPos pos) {
        return links.get(pos);
    }

    /** Tells the networks of the dimensions this one's Ender Antennas send into on {@code frequency} that it changed. */
    private void propagateLinks(int frequency) {
        if (linking || links.isEmpty() || world == null) {
            return;
        }
        Set<ResourceKey<Level>> targets = new HashSet<>();
        links.forEach((pos, target) -> {
            Transmitter tx = transmitters.get(pos);
            if (tx != null && tx.frequency() == frequency) targets.add(target);
        });
        for (ResourceKey<Level> target : targets) {
            bumpLinked(target, frequency);
        }
    }

    private void bumpLinked(@Nullable ResourceKey<Level> target, int frequency) {
        if (target == null || world == null || linking) {
            return;
        }
        ServerLevel there = world.getServer().getLevel(target);
        if (there == null || there == world) {
            return;
        }
        linking = true;
        try {
            SignalNetwork network = SignalNetwork.get(there);
            network.relayEpoch++;
            network.bump(frequency);
        } finally {
            linking = false;
        }
    }

    /**
     * Transmitters in other dimensions sending into this one on {@code frequency} (any frequency if
     * it's negative), where they arrive here: their strength (0 while out of power or struck), and
     * their reach in the weather where they stand.
     */
    public List<Phantom> phantoms(@Nullable ServerLevel level, int frequency) {
        ServerLevel here = level != null ? level : world;
        if (here == null) {
            return List.of();
        }
        List<Phantom> result = null;
        for (ServerLevel other : here.getServer().getAllLevels()) {
            if (other == here) {
                continue;
            }
            SignalNetwork network = SignalNetwork.get(other);
            if (network.links.isEmpty()) {
                continue;
            }
            for (Map.Entry<BlockPos, ResourceKey<Level>> link : network.links.entrySet()) {
                if (link.getValue() != here.dimension()) {
                    continue;
                }
                Transmitter tx = network.transmitters.get(link.getKey());
                Bundle bundle = network.bundles.get(link.getKey());
                if (tx != null && bundle != null && !tx.compact()) {
                    // v0.54.0: a bundle sends every color across.
                    for (int c = 0; c < BUNDLE_SIZE && bundle.base() + c <= Frequency.MAX; c++) {
                        int f = bundle.base() + c;
                        if (frequency >= 0 && f != frequency || network.blocked(other, f, tx.tunedBy())) {
                            continue;
                        }
                        if (result == null) result = new ArrayList<>();
                        result.add(new Phantom(CrossDimension.arrival(other, here, link.getKey()), f,
                                network.isDown(link.getKey()) ? 0 : bundle.channels()[c], network.reachNow(other, link.getKey(), tx.reach()),
                                other.dimension(), link.getKey()));
                    }
                    continue;
                }
                if (tx == null || tx.compact() || frequency >= 0 && tx.frequency() != frequency
                        || network.blocked(other, tx.frequency(), tx.tunedBy())) {
                    continue;
                }
                if (result == null) result = new ArrayList<>();
                result.add(new Phantom(CrossDimension.arrival(other, here, link.getKey()), tx.frequency(),
                        network.isDown(link.getKey()) ? 0 : tx.power(), network.reachNow(other, link.getKey(), tx.reach()),
                        other.dimension(), link.getKey()));
            }
        }
        return result == null ? List.of() : result;
    }

    // ---- Satellites and uplinks (v0.61.0) ----

    /** A satellite in orbit over this dimension: the game time it went up, and the seed its path across the sky comes from. */
    public record Satellite(long launchedAt, long seed) {}

    /** The most satellites one dimension keeps (more launches still work: the oldest falls out of orbit). */
    public static final int MAX_SATELLITES = 64;
    /** How far a tower reaches through a satellite: the whole dimension. */
    public static final long UPLINK_REACH = 60_000_000L;

    /** Satellites in orbit over this dimension, oldest first (saved). */
    private final List<Satellite> satellites = new ArrayList<>();
    /** Transmitters with a Satellite Dish under open sky (saved, so unloaded ones stay uplinked). */
    private final Set<BlockPos> uplinks = new HashSet<>();

    /** The transmitter at {@code pos} has a Satellite Dish with open sky above it, or not. Called by its block entity. */
    public void setUplink(BlockPos pos, boolean dishUnderSky) {
        boolean changed = dishUnderSky ? uplinks.add(pos.immutable()) : uplinks.remove(pos);
        if (changed) {
            musicCoverage.remove(pos);
            relayEpoch++;
            bumpTower(pos);
            setDirty();
        }
    }

    /** True if the transmitter at {@code pos} has a Satellite Dish under open sky (whether or not a satellite is up). */
    public boolean hasUplinkDish(BlockPos pos) {
        return uplinks.contains(pos);
    }

    /** True if the transmitter at {@code pos} is heard across the whole dimension now: its dish sees the sky and a satellite is up. */
    public boolean isUplinked(BlockPos pos) {
        return !satellites.isEmpty() && uplinks.contains(pos);
    }

    public List<Satellite> satellites() {
        return List.copyOf(satellites);
    }

    /** A satellite reaches orbit over this dimension (from a Launch Pad); uplinked towers come on through it if it's the first. */
    public void addSatellite(long gameTime, long seed) {
        satellites.add(new Satellite(gameTime, seed));
        while (satellites.size() > MAX_SATELLITES) {
            satellites.remove(0);
        }
        if (satellites.size() == 1) {
            relayEpoch++;
            musicCoverage.clear();
            List.copyOf(uplinks).forEach(this::bumpTower);
        }
        setDirty();
        if (world != null) {
            com.reiko.signaltower.network.SatellitesPayload.sendToDimension(world);
        }
    }

    /** For the GameTests: no satellites over this dimension. */
    public void clearSatellitesForTests() {
        if (!satellites.isEmpty()) {
            satellites.clear();
            relayEpoch++;
            musicCoverage.clear();
            List.copyOf(uplinks).forEach(this::bumpTower);
            setDirty();
        }
    }

    // ---- Power cost (v0.49.0) ----

    /** Towers out of power (no charged Radio Battery when last checked; saved, so unloaded ones stay quiet). */
    private final Set<BlockPos> drained = new HashSet<>();
    /** Charge each tower owes, below 1 (it pays in whole units; not saved). */
    private final Map<BlockPos, Double> owed = new HashMap<>();

    /** True if the tower at {@code pos} sends (relays, jams) nothing right now: lightning, no power, or (v0.54.0) storm damage. */
    public boolean isDown(BlockPos pos) {
        return isKnockedOut(pos) || isDrained(pos) || damaged.containsKey(pos);
    }

    // ---- Storm damage, ice and guy wires (v0.54.0) ----

    /** Towers blown down by a storm, by their top, with what did it ("derecho", "tornado"). Saved. */
    private final Map<BlockPos, String> damaged = new HashMap<>();
    /** Towers whose antenna is iced up, by their top, with how many ticks of thaw they've had. Saved. */
    private final Map<BlockPos, Integer> iced = new HashMap<>();
    /** Guy Wire Anchors, with the foot of the mast column each one braces. Saved. */
    private final Map<BlockPos, BlockPos> anchors = new HashMap<>();

    public @Nullable String damageCause(BlockPos top) {
        return damaged.get(top);
    }

    public Set<BlockPos> damagedTowers() {
        return Set.copyOf(damaged.keySet());
    }

    /** A storm blew the tower whose top is at {@code top} down: it's quiet until {@link #repair}. */
    public void damage(BlockPos top, String cause) {
        if (damaged.put(top.immutable(), cause) == null) {
            relayEpoch++;
            bumpTower(top);
        }
        setDirty();
    }

    /** Puts a storm-damaged tower right again; false if it wasn't damaged. */
    public boolean repair(BlockPos top) {
        if (damaged.remove(top) == null) {
            return false;
        }
        relayEpoch++;
        bumpTower(top);
        setDirty();
        return true;
    }

    public boolean isIced(BlockPos top) {
        return iced.containsKey(top);
    }

    /** Ticks of thaw an iced tower has had (above freezing, after its ice storm). */
    public int thawed(BlockPos top) {
        return iced.getOrDefault(top, 0);
    }

    public Set<BlockPos> icedTowers() {
        return Set.copyOf(iced.keySet());
    }

    /** An ice storm iced up the tower's antenna (again: it starts thawing from scratch). */
    public void ice(BlockPos top) {
        Integer was = iced.put(top.immutable(), 0);
        if (was == null) {
            relayEpoch++;
            bumpTower(top);
        }
        if (was == null || was != 0) {
            setDirty();
        }
    }

    /** {@code ticks} more of thaw; the ice is gone after {@code needed}. True if that thawed it. */
    public boolean thaw(BlockPos top, int ticks, int needed) {
        Integer was = iced.get(top);
        if (was == null) {
            return false;
        }
        if (was + ticks >= needed) {
            unice(top);
            return true;
        }
        iced.put(top, was + ticks);
        setDirty();
        return false;
    }

    /** The ice is gone. */
    public void unice(BlockPos top) {
        if (iced.remove(top) != null) {
            relayEpoch++;
            bumpTower(top);
            setDirty();
        }
    }

    /** A Guy Wire Anchor at {@code anchor} braces the mast column whose foot is {@code base} (null: none). */
    public void setAnchor(BlockPos anchor, @Nullable BlockPos base) {
        BlockPos was = base == null ? anchors.remove(anchor) : anchors.put(anchor.immutable(), base.immutable());
        if (!java.util.Objects.equals(was, base)) {
            setDirty();
        }
    }

    /** How many Guy Wire Anchors brace the mast column whose foot is {@code base}. */
    public int bracesOf(BlockPos base) {
        int count = 0;
        for (BlockPos braced : anchors.values()) {
            if (braced.equals(base)) count++;
        }
        return count;
    }

    /** Every full-size tower top: transmitters (not compact), repeaters, converters, jammers. */
    public List<BlockPos> towerTops() {
        return lightningTargets();
    }

    /** Who tuned the tower at {@code pos} (null if nobody's known). */
    public @Nullable UUID tunerOf(BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        if (tx != null) return tx.tunedBy();
        Repeater repeater = repeaters.get(pos);
        if (repeater != null) return repeater.tunedBy();
        Jammer jammer = jammers.get(pos);
        return jammer != null ? jammer.tunedBy() : null;
    }

    /** True if the tower at {@code pos} is out of power (and power cost is on). */
    public boolean isDrained(BlockPos pos) {
        return !drained.isEmpty() && drained.contains(pos) && PowerCost.enabled();
    }

    /** Marks the tower at {@code pos} out of power or not; true if that changed (then everything listening re-checks). */
    public boolean setDrained(BlockPos pos, boolean out) {
        boolean changed = out ? drained.add(pos.immutable()) : drained.remove(pos);
        if (changed) {
            if (!out) owed.remove(pos);
            relayEpoch++;
            bumpTower(pos);
            setDirty();
        }
        return changed;
    }

    /** Positions marked out of power (whether or not power cost is on now). */
    public Set<BlockPos> drained() {
        return Set.copyOf(drained);
    }

    /** The tower at {@code pos} used {@code amount} more charge; returns how many whole units it now owes (to pay with {@link #paid}). */
    public int owe(BlockPos pos, double amount) {
        double total = owed.merge(pos.immutable(), amount, Double::sum);
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(total));
    }

    /** The tower at {@code pos} paid {@code units} of what it owes. */
    public void paid(BlockPos pos, int units) {
        owed.computeIfPresent(pos, (key, total) -> total - units);
    }

    private void forgetPower(BlockPos pos) {
        owed.remove(pos);
        if (drained.remove(pos)) {
            setDirty();
        }
    }

    /** The stored (clear-weather, antenna included) reach of the tower at {@code pos}, or 0 if there's none. */
    public int towerReach(BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        if (tx != null) return tx.reach();
        Repeater repeater = repeaters.get(pos);
        if (repeater != null) return repeater.reach();
        Jammer jammer = jammers.get(pos);
        return jammer != null ? jammer.reach() : 0;
    }

    /**
     * True if the tower at {@code pos} would send something now if it had power: a transmitter with
     * redstone, a jammer with redstone, a repeater or converter hearing something (a gate: its gate
     * sending). That's when it uses charge.
     */
    public boolean wantsToSend(ServerLevel level, BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        if (tx != null) return !tx.compact() && tx.power() > 0;
        Jammer jammer = jammers.get(pos);
        if (jammer != null) return jammer.power() > 0;
        Repeater repeater = repeaters.get(pos);
        if (repeater == null) return false;
        if (repeater.gated()) return gateOutputs.getOrDefault(pos, 0) > 0;
        if (!isDown(pos)) return repeaterPower(level, pos) > 0;
        return listen(level, repeater.frequency(), pos, null, false).power() > 0;
    }

    /**
     * Towers on {@code frequency} (v0.49.0, for the Coverage Map's markers): transmitters sending on
     * it (0), repeaters and converters sending on it (1), and jammers jamming it (2).
     */
    public List<Map.Entry<BlockPos, Integer>> towersOn(int frequency) {
        List<Map.Entry<BlockPos, Integer>> result = new ArrayList<>();
        transmitters.forEach((pos, tx) -> {
            Bundle bundle = bundles.get(pos);
            if (bundle != null ? bundle.covers(frequency) : tx.frequency() == frequency) result.add(Map.entry(pos, 0));
        });
        repeaters.forEach((pos, repeater) -> {
            if (repeater.output() == frequency) result.add(Map.entry(pos, 1));
        });
        jammers.forEach((pos, jammer) -> {
            if (jammer.frequency() == frequency) result.add(Map.entry(pos, 2));
        });
        for (Phantom phantom : phantoms(world, frequency)) {
            result.add(Map.entry(phantom.pos(), 3)); // v0.50.0: arriving from another dimension
        }
        return result;
    }

    /** A full-size tower someone tuned (v0.61.0, for map waypoints): 0 transmitter, 1 repeater or converter, 2 jammer; reach -1 if uplinked. */
    public record OwnTower(BlockPos pos, int kind, int frequency, int reach) {}

    /** The full-size towers {@code tuner} tuned (placed), for their map's waypoints (v0.61.0). */
    public List<OwnTower> towersTunedBy(UUID tuner) {
        List<OwnTower> result = new ArrayList<>();
        transmitters.forEach((pos, tx) -> {
            if (!tx.compact() && tuner.equals(tx.tunedBy())) {
                result.add(new OwnTower(pos, 0, tx.frequency(), isUplinked(pos) ? -1 : tx.reach()));
            }
        });
        repeaters.forEach((pos, repeater) -> {
            if (tuner.equals(repeater.tunedBy())) result.add(new OwnTower(pos, 1, repeater.output(), repeater.reach()));
        });
        jammers.forEach((pos, jammer) -> {
            if (tuner.equals(jammer.tunedBy())) result.add(new OwnTower(pos, 2, jammer.frequency(), jammer.reach()));
        });
        result.sort(Comparator.comparingLong(tower -> tower.pos().asLong()));
        return result;
    }

    /** The frequency the transmitter at {@code pos} sends on, if one is known there (v0.61.0). */
    public OptionalInt transmitterFrequency(BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        return tx == null ? OptionalInt.empty() : OptionalInt.of(tx.frequency());
    }

    /** Bumps the frequency the tower at {@code pos} sends (or jams) on. */
    private void bumpTower(BlockPos pos) {
        Transmitter tx = transmitters.get(pos);
        if (tx != null) bump(tx.frequency());
        Bundle bundle = bundles.get(pos);
        if (bundle != null) {
            for (int c = 1; c < BUNDLE_SIZE && bundle.base() + c <= Frequency.MAX; c++) bump(bundle.base() + c);
        }
        Repeater repeater = repeaters.get(pos);
        if (repeater != null) bump(repeater.output());
        Jammer jammer = jammers.get(pos);
        if (jammer != null) bump(jammer.frequency());
    }

    // ---- Weather and terrain (v0.46.0) ----

    /** Ground this close (horizontally) to either end of a line doesn't count: a tower's own base, the building round a receiver. */
    public static final int TERRAIN_SKIP = 8;
    /** The most points checked along one line. */
    private static final int TERRAIN_SAMPLES = 64;

    /** For the GameTests: this network behaves as if the weather were {@code weather} (null: the level's again). */
    public void forceWeather(@Nullable Weather weather) {
        forcedWeather = weather;
        relayEpoch++;
        bumpEverything();
    }

    /** The weather in this dimension's sky now. */
    public Weather weather(@Nullable ServerLevel level) {
        if (forcedWeather != null) {
            return forcedWeather;
        }
        ServerLevel at = level != null ? level : world;
        return at == null ? Weather.CLEAR : Weather.of(at);
    }

    /**
     * The weather a tower at {@code pos} is in: the dimension's, or clear where it never rains (by
     * biome). v0.53.0: with Reiko's Weather, a storm cell over it brings the cell's weather instead
     * (rain or thunder, or clear for a dry one), and the world's weather everywhere else.
     */
    public Weather weatherAt(@Nullable ServerLevel level, BlockPos pos) {
        Weather now = weather(level);
        ServerLevel at = level != null ? level : world;
        if (at == null) {
            return Weather.CLEAR;
        }
        if (forcedWeather == null) {
            Weather storm = stormAt(at, pos);
            if (storm != null) {
                now = storm;
            }
        }
        if (now == Weather.CLEAR) {
            return Weather.CLEAR;
        }
        Boolean wet = wetSpots.get(pos);
        if (wet == null) {
            // Biomes are known without loading the chunk; they hardly ever change, so remember them.
            wet = Weather.wetBiome(at.getBiome(pos).value(), pos);
            if (wetSpots.size() >= 8192) {
                wetSpots.clear();
            }
            wetSpots.put(pos.immutable(), wet);
        }
        return wet ? now : Weather.CLEAR;
    }

    /** {@code reach} of a tower (not a compact transmitter) at {@code pos}, in the weather where it stands now. */
    public int reachNow(@Nullable ServerLevel level, BlockPos pos, int reach) {
        int now = weatherAt(level, pos).apply(reach);
        if (!iced.isEmpty() && iced.containsKey(pos)) {
            now = now * (100 - com.reiko.signaltower.Config.ICE_REACH_LOSS_PERCENT.get()) / 100; // v0.54.0: an iced-up antenna
        }
        return now;
    }

    private int reachNow(@Nullable ServerLevel level, BlockPos pos, Transmitter tx) {
        return tx.compact() ? tx.reach() : reachNow(level, pos, tx.reach());
    }

    /** Checks once a tick whether the weather changed; if it did, everything on every frequency is worked out again. */
    public void watchWeather(ServerLevel level) {
        Weather now = weather(level);
        if (now != lastWeather) {
            boolean first = lastWeather == null;
            lastWeather = now;
            if (!first) {
                relayEpoch++;
                bumpEverything();
            }
        }
        if (level.getGameTime() % STORM_CHECK_TICKS == 0) {
            watchStorms(level);
            StormDamage.check(level, this); // v0.54.0
        }
    }

    /** v0.53.0: the storm cell over {@code pos} (Reiko's Weather), as last worked out; null for none. */
    private @Nullable Weather stormAt(ServerLevel level, BlockPos pos) {
        if (!com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
            return null;
        }
        java.util.Optional<Weather> known = storms.get(pos);
        if (known == null) {
            known = java.util.Optional.ofNullable(com.reiko.signaltower.compat.ReikosWeatherCompat.stormOver(level, pos));
            if (storms.size() >= 8192) {
                storms.clear();
            }
            storms.put(pos.immutable(), known);
        }
        return known.orElse(null);
    }

    /**
     * v0.53.0: looks again at the storm cells over every tower (Reiko's Weather moves them across
     * the map); where one has come or gone, that tower's frequency is worked out again.
     */
    private void watchStorms(ServerLevel level) {
        if (storms.isEmpty() && !com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
            return;
        }
        storms.clear();
        if (!com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
            relayEpoch++;
            bumpEverything(); // it was switched off (the GameTests): back to the world's weather
            return;
        }
        Set<BlockPos> towers = new HashSet<>(transmitters.keySet());
        towers.addAll(repeaters.keySet());
        towers.addAll(jammers.keySet());
        Map<BlockPos, java.util.Optional<Weather>> before = new HashMap<>(lastStorms);
        lastStorms.clear();
        boolean changed = false;
        for (BlockPos pos : towers) {
            java.util.Optional<Weather> now = java.util.Optional.ofNullable(com.reiko.signaltower.compat.ReikosWeatherCompat.stormOver(level, pos));
            storms.put(pos, now);
            if (now.isPresent()) {
                lastStorms.put(pos, now);
            }
            if (!now.equals(before.getOrDefault(pos, java.util.Optional.empty()))) {
                changed = true;
                bumpTower(pos);
            }
        }
        if (changed) {
            relayEpoch++; // relayed presses and repeaters go by reach too
        }
    }

    /** v0.53.0: the towers a storm cell was over at the last look, to notice it moving on. */
    private final Map<BlockPos, java.util.Optional<Weather>> lastStorms = new HashMap<>();

    private void bumpEverything() {
        Set<Integer> all = new HashSet<>(byFrequency.keySet());
        all.addAll(repeatersByInput.keySet());
        all.addAll(repeatersByOutput.keySet());
        all.addAll(jammersByFrequency.keySet());
        for (int frequency : all) {
            revisions.merge(frequency, 1L, Long::sum);
        }
    }

    /**
     * How much strength the ground between a tower at {@code from} and a listener at {@code to}
     * takes off (0-15): the straight line runs from the top of the tower to the middle of the
     * listener's block, and every {@link com.reiko.signaltower.Config#TERRAIN_BLOCKS_PER_STRENGTH}
     * blocks of it (measured horizontally) that run through solid ground take 1 off. "Ground" is a
     * block that stops movement, or a liquid; leaves don't count (trees don't block it), nor does air
     * (a cave it runs through, or a roof above it). Up to {@link #TERRAIN_SAMPLES} points along the line
     * are checked. The first and last {@link #TERRAIN_SKIP} blocks don't count; chunks that aren't
     * loaded count as clear (nothing is loaded to check).
     */
    public int hillLoss(@Nullable ServerLevel level, BlockPos from, BlockPos to) {
        if (isUplinked(from)) {
            return 0; // v0.61.0: it comes down from the sky
        }
        int per = terrainBlocksPerStrength();
        ServerLevel at = level != null ? level : world;
        if (per <= 0 || at == null) {
            return 0;
        }
        double ax = from.getX() + 0.5, ay = from.getY() + 1.0, az = from.getZ() + 0.5;
        double bx = to.getX() + 0.5, by = to.getY() + 0.5, bz = to.getZ() + 0.5;
        double dx = bx - ax, dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        double span = length - 2.0 * TERRAIN_SKIP;
        if (span <= 0) {
            return 0;
        }
        int samples = (int) Math.min(TERRAIN_SAMPLES, Math.ceil(span));
        double step = span / samples;
        var chunks = at.getChunkSource();
        int under = 0;
        int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
        LevelChunk chunk = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double t = (TERRAIN_SKIP + (i + 0.5) * step) / length;
            int x = Mth.floor(ax + dx * t);
            int z = Mth.floor(az + dz * t);
            if (x >> 4 != lastChunkX || z >> 4 != lastChunkZ) {
                lastChunkX = x >> 4;
                lastChunkZ = z >> 4;
                chunk = chunks.getChunkNow(lastChunkX, lastChunkZ);
            }
            if (chunk == null) {
                continue;
            }
            double y = ay + (by - ay) * t;
            // Above the highest solid block it's clear for sure; below it, look at the block itself
            // (a roof or an overhang above the line isn't in the way, a cave it runs through isn't either).
            if (y < chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1
                    && isGround(chunk.getBlockState(cursor.set(x, Mth.floor(y), z)))) {
                under++;
            }
        }
        return (int) Math.min(15, Math.floor(under * step / per + 1e-9));
    }

    /** Solid ground for {@link #hillLoss}: blocks that stop movement, and liquids; not leaves (like the MOTION_BLOCKING_NO_LEAVES height map). */
    private static boolean isGround(net.minecraft.world.level.block.state.BlockState state) {
        return (state.blocksMotion() || !state.getFluidState().isEmpty()) && !state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

        private static int terrainBlocksPerStrength() {
        var setting = com.reiko.signaltower.Config.TERRAIN_BLOCKS_PER_STRENGTH;
        return com.reiko.signaltower.Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    // ---- Saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        transmitters.forEach((pos, tx) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("frequency", tx.frequency());
            entry.putInt("power", tx.power());
            entry.putInt("reach", tx.reach());
            if (tx.tunedBy() != null) {
                entry.putUUID("tuned_by", tx.tunedBy());
            }
            if (tx.compact()) {
                entry.putBoolean("compact", true);
            }
            list.add(entry);
        });
        tag.put("transmitters", list);

        ListTag repeaterList = new ListTag();
        repeaters.forEach((pos, repeater) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("frequency", repeater.frequency());
            if (repeater.converts()) {
                entry.putInt("output", repeater.output()); // left out for repeaters, as saved by 0.20.0
            }
            entry.putInt("reach", repeater.reach());
            if (repeater.tunedBy() != null) {
                entry.putUUID("tuned_by", repeater.tunedBy());
            }
            if (repeater.gated()) {
                entry.putInt("gate", repeater.gate());
                entry.putInt("threshold", repeater.threshold());
                entry.putInt("gate_output", gateOutputs.getOrDefault(pos, 0));
            }
            repeaterList.add(entry);
        });
        tag.put("repeaters", repeaterList);

        ListTag jammerList = new ListTag();
        jammers.forEach((pos, jammer) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("frequency", jammer.frequency());
            entry.putInt("power", jammer.power());
            entry.putInt("reach", jammer.reach());
            if (jammer.tunedBy() != null) {
                entry.putUUID("tuned_by", jammer.tunedBy());
            }
            jammerList.add(entry);
        });
        tag.put("jammers", jammerList);

        ListTag dishList = new ListTag();
        dishes.forEach((pos, aim) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("aim", aim);
            dishList.add(entry);
        });
        tag.put("dishes", dishList);

        if (!drained.isEmpty()) {
            tag.putLongArray("drained", drained.stream().mapToLong(BlockPos::asLong).toArray());
        }
        if (!damaged.isEmpty()) {
            ListTag damagedList = new ListTag(); // v0.54.0
            damaged.forEach((pos, cause) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putString("cause", cause);
                damagedList.add(entry);
            });
            tag.put("storm_damaged", damagedList);
        }
        if (!iced.isEmpty()) {
            ListTag icedList = new ListTag();
            iced.forEach((pos, thawed) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putInt("thawed", thawed);
                icedList.add(entry);
            });
            tag.put("iced", icedList);
        }
        if (!anchors.isEmpty()) {
            ListTag anchorList = new ListTag();
            anchors.forEach((pos, base) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putLong("base", base.asLong());
                anchorList.add(entry);
            });
            tag.put("guy_anchors", anchorList);
        }
        if (!bundles.isEmpty()) {
            ListTag bundleList = new ListTag(); // v0.54.0
            bundles.forEach((pos, bundle) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putInt("base", bundle.base());
                entry.putIntArray("channels", bundle.channels());
                bundleList.add(entry);
            });
            tag.put("bundles", bundleList);
        }
        if (!music.isEmpty()) {
            ListTag musicList = new ListTag(); // v0.60.0
            music.forEach((pos, song) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putInt("frequency", song.frequency());
                entry.putString("song", song.song().location().toString());
                entry.putLong("start", song.start());
                entry.putInt("length", song.length());
                entry.putBoolean("loop", song.loop());
                musicList.add(entry);
            });
            tag.put("music", musicList);
        }
        if (!satellites.isEmpty()) { // v0.61.0
            ListTag satelliteList = new ListTag();
            for (Satellite satellite : satellites) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("launched", satellite.launchedAt());
                entry.putLong("seed", satellite.seed());
                satelliteList.add(entry);
            }
            tag.put("satellites", satelliteList);
        }
        if (!uplinks.isEmpty()) {
            tag.putLongArray("uplinks", uplinks.stream().mapToLong(BlockPos::asLong).toArray());
        }
        if (!links.isEmpty()) {
            ListTag linkList = new ListTag();
            links.forEach((pos, target) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.putString("dimension", target.location().toString());
                linkList.add(entry);
            });
            tag.put("links", linkList);
        }

        ListTag latchList = new ListTag();
        latches.forEach((frequency, latch) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("frequency", frequency);
            // "pos" and "reach" are the player's own area, as saved by versions before 0.11.0, so
            // an older version can still read the latch (without its relay towers).
            Coverage own = latch.coverage().get(0);
            entry.putLong("pos", own.origin().asLong());
            entry.putInt("reach", own.reach());
            ListTag areas = new ListTag();
            for (Coverage area : latch.coverage()) {
                CompoundTag areaTag = new CompoundTag();
                areaTag.putLong("pos", area.origin().asLong());
                areaTag.putInt("reach", area.reach());
                areaTag.putBoolean("tower", area.tower());
                if (area.aim() >= 0) {
                    areaTag.putInt("far", area.far());
                    areaTag.putInt("aim", area.aim());
                }
                areas.add(areaTag);
            }
            entry.put("coverage", areas);
            latchList.add(entry);
        });
        tag.put("remote_latches", latchList);

        ListTag turnOns = new ListTag();
        history.forEach((frequency, recent) -> {
            for (TurnOn turnOn : recent) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("frequency", frequency);
                entry.putLong("time", turnOn.gameTime());
                entry.putLong("day_time", turnOn.dayTime());
                ListTag areas = new ListTag();
                for (Coverage area : turnOn.coverage()) {
                    CompoundTag areaTag = new CompoundTag();
                    areaTag.putLong("pos", area.origin().asLong());
                    areaTag.putInt("reach", area.reach());
                    areas.add(areaTag);
                }
                entry.put("coverage", areas);
                Cause cause = turnOn.cause();
                if (cause.kind() != CauseKind.UNKNOWN) {
                    CompoundTag causeTag = new CompoundTag();
                    causeTag.putString("kind", cause.kind().name().toLowerCase(java.util.Locale.ROOT));
                    if (cause.pos() != null) {
                        causeTag.putLong("pos", cause.pos().asLong());
                    }
                    if (cause.who() != null) {
                        causeTag.putUUID("who", cause.who());
                    }
                    if (!cause.name().isEmpty()) {
                        causeTag.putString("name", cause.name());
                    }
                    if (!cause.detail().isEmpty()) {
                        causeTag.putString("detail", cause.detail());
                    }
                    entry.put("cause", causeTag);
                }
                if (turnOn.from() != 0) {
                    entry.putInt("from", turnOn.from());
                }
                turnOns.add(entry);
            }
        });
        tag.put("turn_ons", turnOns);
        ListTag messageList = new ListTag(); // v0.51.0
        messages.forEach((frequency, recent) -> {
            for (Message message : recent) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("frequency", frequency);
                entry.putLong("time", message.gameTime());
                entry.putLong("day_time", message.dayTime());
                entry.putLong("from", message.from().asLong());
                if (message.who() != null) {
                    entry.putUUID("who", message.who());
                }
                entry.putString("name", message.name());
                entry.putString("text", message.text());
                if (message.handheld()) {
                    entry.putBoolean("handheld", true); // v0.64.0
                }
                ListTag areas = new ListTag();
                for (Coverage area : message.coverage()) {
                    CompoundTag areaTag = new CompoundTag();
                    areaTag.putLong("pos", area.origin().asLong());
                    areaTag.putInt("reach", area.reach());
                    areaTag.putBoolean("tower", area.tower());
                    if (area.aim() >= 0) {
                        areaTag.putInt("far", area.far());
                        areaTag.putInt("aim", area.aim());
                    }
                    areas.add(areaTag);
                }
                entry.put("coverage", areas);
                messageList.add(entry);
            }
        });
        tag.put("messages", messageList);
        return tag;
    }

    /** Reads what {@link #save} wrote. Public so the GameTests can check old saves still load. */
    public static SignalNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        SignalNetwork network = new SignalNetwork();
        ListTag list = tag.getList("transmitters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            Transmitter tx = new Transmitter(
                    Frequency.clamp(entry.getInt("frequency")),
                    entry.getInt("power"),
                    entry.getInt("reach"),
                    entry.hasUUID("tuned_by") ? entry.getUUID("tuned_by") : null,
                    entry.getBoolean("compact"));
            network.transmitters.put(pos, tx);
            network.byFrequency.computeIfAbsent(tx.frequency(), f -> new HashMap<>()).put(pos, tx);
        }
        ListTag repeaterList = tag.getList("repeaters", Tag.TAG_COMPOUND);
        for (int i = 0; i < repeaterList.size(); i++) {
            CompoundTag entry = repeaterList.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            int frequency = Frequency.clamp(entry.getInt("frequency"));
            int output = entry.contains("output") ? Frequency.clamp(entry.getInt("output")) : frequency;
            Repeater repeater = new Repeater(frequency, output, entry.getInt("reach"),
                    entry.hasUUID("tuned_by") ? entry.getUUID("tuned_by") : null,
                    Math.max(0, Math.min(2, entry.getInt("gate"))), Math.max(1, Math.min(15, entry.contains("threshold") ? entry.getInt("threshold") : 1)));
            network.repeaters.put(pos, repeater);
            if (repeater.gated() && entry.getInt("gate_output") > 0) {
                network.gateOutputs.put(pos, Math.min(15, entry.getInt("gate_output")));
            }
            network.repeatersByInput.computeIfAbsent(repeater.frequency(), f -> new HashMap<>()).put(pos, repeater);
            network.repeatersByOutput.computeIfAbsent(repeater.output(), f -> new HashMap<>()).put(pos, repeater);
        }
        ListTag jammerList = tag.getList("jammers", Tag.TAG_COMPOUND);
        for (int i = 0; i < jammerList.size(); i++) {
            CompoundTag entry = jammerList.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            Jammer jammer = new Jammer(Frequency.clamp(entry.getInt("frequency")), Math.max(0, Math.min(15, entry.getInt("power"))),
                    entry.getInt("reach"), entry.hasUUID("tuned_by") ? entry.getUUID("tuned_by") : null);
            network.jammers.put(pos, jammer);
            network.jammersByFrequency.computeIfAbsent(jammer.frequency(), f -> new HashMap<>()).put(pos, jammer);
        }
        for (long packed : tag.getLongArray("drained")) {
            network.drained.add(BlockPos.of(packed));
        }
        ListTag damagedList = tag.getList("storm_damaged", Tag.TAG_COMPOUND);
        for (int i = 0; i < damagedList.size(); i++) {
            CompoundTag entry = damagedList.getCompound(i);
            network.damaged.put(BlockPos.of(entry.getLong("pos")), entry.getString("cause"));
        }
        ListTag icedList = tag.getList("iced", Tag.TAG_COMPOUND);
        for (int i = 0; i < icedList.size(); i++) {
            CompoundTag entry = icedList.getCompound(i);
            network.iced.put(BlockPos.of(entry.getLong("pos")), Math.max(0, entry.getInt("thawed")));
        }
        ListTag anchorList = tag.getList("guy_anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchorList.size(); i++) {
            CompoundTag entry = anchorList.getCompound(i);
            network.anchors.put(BlockPos.of(entry.getLong("pos")), BlockPos.of(entry.getLong("base")));
        }
        ListTag bundleList = tag.getList("bundles", Tag.TAG_COMPOUND);
        for (int i = 0; i < bundleList.size(); i++) {
            CompoundTag entry = bundleList.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            int[] saved = entry.getIntArray("channels");
            Transmitter main = network.transmitters.get(pos);
            if (main == null || saved.length != BUNDLE_SIZE) {
                continue;
            }
            int[] channels = new int[BUNDLE_SIZE];
            for (int c = 0; c < BUNDLE_SIZE; c++) channels[c] = Math.max(0, Math.min(15, saved[c]));
            Bundle bundle = new Bundle(main.frequency(), channels);
            network.bundles.put(pos, bundle);
            network.indexBundle(pos, bundle, main);
        }
        ListTag musicList = tag.getList("music", Tag.TAG_COMPOUND);
        for (int i = 0; i < musicList.size(); i++) {
            CompoundTag entry = musicList.getCompound(i);
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(entry.getString("song"));
            if (id != null && entry.getInt("length") > 0) {
                network.music.put(BlockPos.of(entry.getLong("pos")), new Music(entry.getInt("frequency"),
                        ResourceKey.create(net.minecraft.core.registries.Registries.JUKEBOX_SONG, id), entry.getLong("start"),
                        entry.getInt("length"), entry.getBoolean("loop")));
            }
        }
        ListTag satelliteList = tag.getList("satellites", Tag.TAG_COMPOUND);
        for (int i = 0; i < satelliteList.size(); i++) {
            CompoundTag entry = satelliteList.getCompound(i);
            network.satellites.add(new Satellite(entry.getLong("launched"), entry.getLong("seed")));
        }
        for (long packed : tag.getLongArray("uplinks")) {
            network.uplinks.add(BlockPos.of(packed));
        }
        ListTag linkList = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < linkList.size(); i++) {
            CompoundTag entry = linkList.getCompound(i);
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(entry.getString("dimension"));
            if (id != null) {
                network.links.put(BlockPos.of(entry.getLong("pos")),
                        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
            }
        }
        ListTag dishList = tag.getList("dishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < dishList.size(); i++) {
            CompoundTag entry = dishList.getCompound(i);
            network.dishes.put(BlockPos.of(entry.getLong("pos")), entry.getInt("aim") & 15);
        }
        ListTag latchList = tag.getList("remote_latches", Tag.TAG_COMPOUND);
        for (int i = 0; i < latchList.size(); i++) {
            CompoundTag entry = latchList.getCompound(i);
            List<Coverage> coverage = new ArrayList<>();
            ListTag areas = entry.getList("coverage", Tag.TAG_COMPOUND);
            for (int j = 0; j < areas.size(); j++) {
                CompoundTag area = areas.getCompound(j);
                coverage.add(area.contains("aim")
                        ? new Coverage(BlockPos.of(area.getLong("pos")), area.getInt("reach"), area.getBoolean("tower"), area.getInt("far"), area.getInt("aim"))
                        : new Coverage(BlockPos.of(area.getLong("pos")), area.getInt("reach"), area.getBoolean("tower")));
            }
            if (coverage.isEmpty()) { // saved before 0.11.0: just the player's own area
                coverage.add(new Coverage(BlockPos.of(entry.getLong("pos")), entry.getInt("reach"), false));
            }
            network.latches.put(Frequency.clamp(entry.getInt("frequency")), new RemoteSignal(coverage, Long.MAX_VALUE));
        }
        ListTag turnOns = tag.getList("turn_ons", Tag.TAG_COMPOUND);
        for (int i = 0; i < turnOns.size(); i++) {
            CompoundTag entry = turnOns.getCompound(i);
            List<Coverage> coverage = new ArrayList<>();
            ListTag areas = entry.getList("coverage", Tag.TAG_COMPOUND);
            for (int j = 0; j < areas.size(); j++) {
                CompoundTag area = areas.getCompound(j);
                coverage.add(new Coverage(BlockPos.of(area.getLong("pos")), area.getInt("reach"), false));
            }
            ArrayDeque<TurnOn> recent = network.history.computeIfAbsent(Frequency.clamp(entry.getInt("frequency")), f -> new ArrayDeque<>());
            Cause cause = Cause.UNKNOWN; // turn-ons saved before v0.35.0 have none
            if (entry.contains("cause", Tag.TAG_COMPOUND)) {
                CompoundTag causeTag = entry.getCompound("cause");
                cause = new Cause(CauseKind.named(causeTag.getString("kind")),
                        causeTag.contains("pos") ? BlockPos.of(causeTag.getLong("pos")) : null,
                        causeTag.hasUUID("who") ? causeTag.getUUID("who") : null,
                        causeTag.getString("name"), causeTag.getString("detail"));
            }
            recent.addLast(new TurnOn(entry.getLong("time"), entry.getLong("day_time"), coverage, cause, entry.getInt("from")));
            while (recent.size() > MAX_HISTORY) {
                recent.removeFirst();
            }
        }
        ListTag messageList = tag.getList("messages", Tag.TAG_COMPOUND);
        for (int i = 0; i < messageList.size(); i++) {
            CompoundTag entry = messageList.getCompound(i);
            List<Coverage> coverage = new ArrayList<>();
            ListTag areas = entry.getList("coverage", Tag.TAG_COMPOUND);
            for (int j = 0; j < areas.size(); j++) {
                CompoundTag area = areas.getCompound(j);
                coverage.add(area.contains("aim")
                        ? new Coverage(BlockPos.of(area.getLong("pos")), area.getInt("reach"), area.getBoolean("tower"), area.getInt("far"), area.getInt("aim"))
                        : new Coverage(BlockPos.of(area.getLong("pos")), area.getInt("reach"), area.getBoolean("tower")));
            }
            ArrayDeque<Message> recent = network.messages.computeIfAbsent(Frequency.clamp(entry.getInt("frequency")), f -> new ArrayDeque<>());
            recent.addLast(new Message(entry.getLong("time"), entry.getLong("day_time"), coverage, BlockPos.of(entry.getLong("from")),
                    entry.hasUUID("who") ? entry.getUUID("who") : null, entry.getString("name"), entry.getString("text"),
                    entry.getBoolean("handheld")));
            while (recent.size() > MAX_MESSAGES) {
                recent.removeFirst();
            }
        }
        return network;
    }
}
