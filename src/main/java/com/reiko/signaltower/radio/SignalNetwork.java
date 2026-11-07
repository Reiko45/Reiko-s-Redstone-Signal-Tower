package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
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

    /** What a transmitter is currently broadcasting. */
    public record Transmitter(int frequency, int power, int reach, @Nullable UUID tunedBy) {
        public Transmitter(int frequency, int power, int reach) {
            this(frequency, power, reach, null);
        }
    }

    /** What a receiver can hear: the strongest signal, and how many transmitters reach it. */
    public record Reception(int power, int transmittersInReach) {}

    /**
     * One area a remote signal goes out from: the player's own position, or a Radio Transmitter
     * that relayed the press ({@code tower} is true), each with its own reach.
     */
    public record Coverage(BlockPos origin, int reach, boolean tower) {
        public Coverage {
            origin = origin.immutable();
        }

        /** Horizontal distance only, like transmitters. */
        public boolean reaches(BlockPos pos) {
            long dx = origin.getX() - pos.getX();
            long dz = origin.getZ() - pos.getZ();
            return dx * dx + dz * dz <= (long) reach * reach;
        }
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
    public record Repeater(int frequency, int output, int reach, @Nullable UUID tunedBy) {
        public Repeater(int frequency, int reach) {
            this(frequency, frequency, reach, null);
        }

        public Repeater(int frequency, int output, int reach) {
            this(frequency, output, reach, null);
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
    /** Recent times each frequency turned on, oldest first, for the pagers' catch-up. Saved. */
    private final Map<Integer, ArrayDeque<TurnOn>> history = new HashMap<>();
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
        BlockPos key = pos.immutable();
        Transmitter next = new Transmitter(frequency, power, reach, tunedBy);
        Transmitter previous = transmitters.put(key, next);
        if (next.equals(previous)) {
            return;
        }
        if (power > 0 && (previous == null || previous.power() == 0 || previous.frequency() != frequency)
                && !blocked(null, frequency, tunedBy)) {
            recordTurnOn(frequency, ownCoverage(key, reach), Cause.transmitter(key, tunedBy));
        }
        if (previous != null && previous.frequency() != frequency) {
            removeFromIndex(key, previous.frequency());
        }
        byFrequency.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, next);
        bump(frequency);
        setDirty();
    }

    public void removeTransmitter(BlockPos pos) {
        Transmitter previous = transmitters.remove(pos);
        if (previous != null) {
            removeFromIndex(pos, previous.frequency());
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
        BlockPos key = pos.immutable();
        Repeater next = new Repeater(frequency, output, reach, tunedBy);
        Repeater previous = repeaters.put(key, next);
        if (next.equals(previous)) {
            return;
        }
        if (previous != null) {
            removeRepeaterFromIndex(key, previous);
        }
        repeatersByInput.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, next);
        repeatersByOutput.computeIfAbsent(output, f -> new HashMap<>()).put(key, next);
        relayEpoch++;
        bump(frequency);
        bump(output);
        setDirty();
    }

    public void removeRepeater(BlockPos pos) {
        Repeater previous = repeaters.remove(pos);
        if (previous != null) {
            removeRepeaterFromIndex(pos, previous);
            setDirty();
        }
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
                best = tx.power();
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
     * unchanged.
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
        Set<BlockPos> silenced = new HashSet<>();
        for (BlockPos pos : positions) {
            Repeater repeater = repeaters.get(pos);
            if (blocked(level, repeater.output(), repeater.tunedBy()) || deaf(level, repeater.frequency(), repeater.tunedBy())) {
                // It sends on someone else's private frequency, or listens on someone else's encrypted one: it stays quiet.
                silenced.add(pos);
                power.put(pos, 0);
            } else {
                power.put(pos, listenDirect(level, repeater.frequency(), pos).power());
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
            Coverage area = new Coverage(next, from.reach(), true);
            for (BlockPos pos : listening.keySet()) {
                if (power.get(pos) < nextPower && !done.contains(pos) && !silenced.contains(pos) && area.reaches(pos)) {
                    power.put(pos, nextPower);
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
                if (reached.contains(pos) || !item.getValue().reaches(pos) || isStale(level, pos)
                        || blocked(level, repeater.output(), repeater.tunedBy())
                        || deaf(level, repeater.frequency(), repeater.tunedBy())) {
                    continue;
                }
                reached.add(pos);
                Coverage own = new Coverage(pos, repeater.reach(), true);
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
     * (horizontally, on any frequency), each with its own reach. A tower only relays a press it
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
            if (!own.reaches(pos)) {
                continue;
            }
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(pos);
                continue;
            }
            coverage.add(new Coverage(pos, entry.getValue().reach(), true));
        }
        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        List<BlockPos> staleRepeaters = null;
        for (Map.Entry<BlockPos, Repeater> entry : repeaters.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!own.reaches(pos)) {
                continue;
            }
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RadioRepeaterBlock)) {
                if (staleRepeaters == null) staleRepeaters = new ArrayList<>();
                staleRepeaters.add(pos);
                continue;
            }
            coverage.add(new Coverage(pos, entry.getValue().reach(), true));
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

    // ---- History, for the pagers' catch-up ----

    /** The most turn-ons remembered per frequency. */
    public static final int MAX_HISTORY = 32;

    /** What turned a frequency on (v0.35.0), for the frequency log. Saved by name, sent by ordinal: only add at the end. */
    public enum CauseKind {
        UNKNOWN, TRANSMITTER, REMOTE_PULSE, REMOTE_TOGGLE, KEYPAD;

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
    public record Cause(CauseKind kind, @Nullable BlockPos pos, @Nullable UUID who, String name) {
        public static final Cause UNKNOWN = new Cause(CauseKind.UNKNOWN, null, null, "");

        public static Cause transmitter(BlockPos pos, @Nullable UUID tunedBy) {
            return new Cause(CauseKind.TRANSMITTER, pos.immutable(), tunedBy, "");
        }

        public static Cause remote(boolean toggle, UUID player, String name) {
            return new Cause(toggle ? CauseKind.REMOTE_TOGGLE : CauseKind.REMOTE_PULSE, null, player, name);
        }

        /** The right code typed on a Code Keypad (v0.37.0): where the keypad is, and who typed it. */
        public static Cause keypad(BlockPos pos, UUID player, String name) {
            return new Cause(CauseKind.KEYPAD, pos.immutable(), player, name);
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

    /** Ends pulses whose time is up. Called every server tick for each dimension. */
    public void tick(long gameTime, long dayTime) {
        now = gameTime;
        nowDayTime = dayTime;
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
        BlockPos key = pos.immutable();
        if (tunedBy == null) {
            receiverTuners.remove(key);
        } else {
            receiverTuners.put(key, tunedBy);
        }
        Integer previous = receivers.put(key, frequency);
        if (previous != null && previous != frequency) {
            removeReceiverFromIndex(key, previous);
        }
        receiversByFrequency.computeIfAbsent(frequency, f -> new HashSet<>()).add(key);
    }

    /** A receiver was broken or its chunk unloaded. */
    public void untrackReceiver(BlockPos pos) {
        receiverTuners.remove(pos);
        Integer previous = receivers.remove(pos);
        if (previous != null) {
            removeReceiverFromIndex(pos, previous);
        }
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
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Transmitter> entry : transmitters.entrySet()) {
            BlockPos at = entry.getKey();
            Transmitter tx = entry.getValue();
            long distSq = horizontalDistSq(at, pos);
            if (distSq > (long) tx.reach() * tx.reach() || blocked(level, tx.frequency(), tx.tunedBy())) {
                continue;
            }
            if (level.isLoaded(at) && !(level.getBlockState(at).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(at);
                continue;
            }
            offer(best, tx.frequency(), new BestTransmitter(tx.power(), distSq, at, 1, Source.TRANSMITTER));
        }
        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        // Resolve first: that can forget repeaters whose block is gone. Each is listed under the frequency it sends on.
        Map<BlockPos, Integer> sending = resolve(level);
        for (Map.Entry<BlockPos, Repeater> entry : repeaters.entrySet()) {
            BlockPos at = entry.getKey();
            Repeater repeater = entry.getValue();
            long distSq = horizontalDistSq(at, pos);
            if (distSq > (long) repeater.reach() * repeater.reach() || blocked(level, repeater.output(), repeater.tunedBy())) {
                continue;
            }
            offer(best, repeater.output(), new BestTransmitter(sending.getOrDefault(at, 0), distSq, at, 1,
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
            if (remote != null) {
                heard.add(new HeardFrequency(frequency, 15, remote, count, -1, -1));
            } else {
                long dx = tx.at().getX() - pos.getX();
                long dz = tx.at().getZ() - pos.getZ();
                int distance = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(tx.distSq())));
                heard.add(new HeardFrequency(frequency, tx.power(), tx.source(),
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
        if (deaf(level, frequency, listener)) {
            return new Reception(0, 0);
        }
        Reception direct = listenDirect(level, frequency, receiverPos);
        if (!repeatersByOutput.containsKey(frequency)) {
            return direct;
        }
        Map<BlockPos, Integer> repeating = resolve(level);
        Map<BlockPos, Repeater> onFrequency = repeatersByOutput.get(frequency);
        if (onFrequency == null) { // resolving forgot the last ones (their blocks were gone)
            return direct;
        }
        int best = direct.power();
        int heard = direct.transmittersInReach();
        for (Map.Entry<BlockPos, Repeater> entry : onFrequency.entrySet()) {
            BlockPos pos = entry.getKey();
            Repeater repeater = entry.getValue();
            if (pos.equals(receiverPos) || horizontalDistSq(pos, receiverPos) > (long) repeater.reach() * repeater.reach()
                    || blocked(level, frequency, repeater.tunedBy())) {
                continue;
            }
            heard++;
            best = Math.max(best, repeating.getOrDefault(pos, 0));
        }
        return new Reception(best, heard);
    }

    /** What {@code receiverPos} hears straight from transmitters and remote presses, leaving repeaters out. */
    private Reception listenDirect(ServerLevel level, int frequency, BlockPos receiverPos) {
        int best = 0;
        int heard = 0;
        for (RemoteSignal remote : new RemoteSignal[] {pulses.get(frequency), latches.get(frequency)}) {
            if (remote != null && remote.reaches(level, receiverPos)) {
                best = 15;
                heard++;
            }
        }
        Map<BlockPos, Transmitter> onFrequency = byFrequency.get(frequency);
        if (onFrequency == null) {
            return new Reception(best, heard);
        }
        List<BlockPos> stale = null;

        for (Map.Entry<BlockPos, Transmitter> entry : onFrequency.entrySet()) {
            BlockPos pos = entry.getKey();
            Transmitter tx = entry.getValue();
            long dx = pos.getX() - receiverPos.getX();
            long dz = pos.getZ() - receiverPos.getZ();
            long reach = tx.reach();
            if (dx * dx + dz * dz > reach * reach || blocked(level, frequency, tx.tunedBy())) {
                continue;
            }
            // Self-repair: drop entries whose block is gone (for example removed by a world editor).
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(pos);
                continue;
            }
            heard++;
            best = Math.max(best, tx.power());
        }

        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        return new Reception(best, heard);
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
            repeaterList.add(entry);
        });
        tag.put("repeaters", repeaterList);

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
                    entry.put("cause", causeTag);
                }
                if (turnOn.from() != 0) {
                    entry.putInt("from", turnOn.from());
                }
                turnOns.add(entry);
            }
        });
        tag.put("turn_ons", turnOns);
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
                    entry.hasUUID("tuned_by") ? entry.getUUID("tuned_by") : null);
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
                    entry.hasUUID("tuned_by") ? entry.getUUID("tuned_by") : null);
            network.repeaters.put(pos, repeater);
            network.repeatersByInput.computeIfAbsent(repeater.frequency(), f -> new HashMap<>()).put(pos, repeater);
            network.repeatersByOutput.computeIfAbsent(repeater.output(), f -> new HashMap<>()).put(pos, repeater);
        }
        ListTag latchList = tag.getList("remote_latches", Tag.TAG_COMPOUND);
        for (int i = 0; i < latchList.size(); i++) {
            CompoundTag entry = latchList.getCompound(i);
            List<Coverage> coverage = new ArrayList<>();
            ListTag areas = entry.getList("coverage", Tag.TAG_COMPOUND);
            for (int j = 0; j < areas.size(); j++) {
                CompoundTag area = areas.getCompound(j);
                coverage.add(new Coverage(BlockPos.of(area.getLong("pos")), area.getInt("reach"), area.getBoolean("tower")));
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
                        causeTag.getString("name"));
            }
            recent.addLast(new TurnOn(entry.getLong("time"), entry.getLong("day_time"), coverage, cause, entry.getInt("from")));
            while (recent.size() > MAX_HISTORY) {
                recent.removeFirst();
            }
        }
        return network;
    }
}
