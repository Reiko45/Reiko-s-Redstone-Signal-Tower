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
 * the strongest signal they hear out again with their own reach. What each one repeats is worked
 * out from the real sources outward (see {@link #resolve}), so repeaters can pass a signal along a
 * chain but can never keep each other on in a loop.
 */
public final class SignalNetwork extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_network";

    private static final SavedData.Factory<SignalNetwork> FACTORY =
            new SavedData.Factory<>(SignalNetwork::new, SignalNetwork::load, null);

    /** What a transmitter is currently broadcasting. */
    public record Transmitter(int frequency, int power, int reach) {}

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

    /** A Radio Transmitter or Radio Repeater: the blocks that broadcast and relay remote presses. */
    public static boolean isTower(Block block) {
        return block instanceof RadioTransmitterBlock || block instanceof RadioRepeaterBlock;
    }

    /** A Radio Repeater's frequency and reach. What it repeats is worked out by {@link #resolve}. */
    public record Repeater(int frequency, int reach) {}

    /** What every repeater on one frequency is repeating, worked out at one revision. Not saved. */
    private record Resolved(long revision, long computedAt, Map<BlockPos, Integer> power) {}

    /** How long a resolved chain is trusted before it's worked out again, as a safety net. */
    private static final int RESOLVE_INTERVAL = 20;

    private final Map<BlockPos, Transmitter> transmitters = new HashMap<>();
    private final Map<Integer, Map<BlockPos, Transmitter>> byFrequency = new HashMap<>();
    private final Map<BlockPos, Repeater> repeaters = new HashMap<>();
    private final Map<Integer, Map<BlockPos, Repeater>> repeatersByFrequency = new HashMap<>();
    private final Map<Integer, Resolved> resolved = new HashMap<>();
    private final Map<Integer, Long> revisions = new HashMap<>();
    private final Map<Integer, RemoteSignal> pulses = new HashMap<>();
    private final Map<Integer, RemoteSignal> latches = new HashMap<>();
    private final Map<BlockPos, Integer> receivers = new HashMap<>();
    private final Map<Integer, Set<BlockPos>> receiversByFrequency = new HashMap<>();
    /** Recent times each frequency turned on, oldest first, for the pagers' catch-up. Saved. */
    private final Map<Integer, ArrayDeque<TurnOn>> history = new HashMap<>();
    /** The game time and day time of the last tick, used to stamp {@link TurnOn}s. */
    private long now;
    private long nowDayTime;

    public static SignalNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ---- Transmitters ----

    public void updateTransmitter(BlockPos pos, int frequency, int power, int reach) {
        BlockPos key = pos.immutable();
        Transmitter next = new Transmitter(frequency, power, reach);
        Transmitter previous = transmitters.put(key, next);
        if (next.equals(previous)) {
            return;
        }
        if (power > 0 && (previous == null || previous.power() == 0 || previous.frequency() != frequency)) {
            recordTurnOn(frequency, ownCoverage(key, reach));
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

    // ---- Repeaters ----

    /** A repeater was placed, re-tuned, or checks in (once a second, and after its chunk loads). */
    public void updateRepeater(BlockPos pos, int frequency, int reach) {
        BlockPos key = pos.immutable();
        Repeater next = new Repeater(frequency, reach);
        Repeater previous = repeaters.put(key, next);
        if (next.equals(previous)) {
            return;
        }
        if (previous != null && previous.frequency() != frequency) {
            removeRepeaterFromIndex(key, previous.frequency());
        }
        repeatersByFrequency.computeIfAbsent(frequency, f -> new HashMap<>()).put(key, next);
        bump(frequency);
        setDirty();
    }

    public void removeRepeater(BlockPos pos) {
        Repeater previous = repeaters.remove(pos);
        if (previous != null) {
            removeRepeaterFromIndex(pos, previous.frequency());
            setDirty();
        }
    }

    private void removeRepeaterFromIndex(BlockPos pos, int frequency) {
        Map<BlockPos, Repeater> onFrequency = repeatersByFrequency.get(frequency);
        if (onFrequency != null) {
            onFrequency.remove(pos);
            if (onFrequency.isEmpty()) {
                repeatersByFrequency.remove(frequency);
            }
        }
        bump(frequency);
    }

    /** The frequency of the repeater at {@code pos}, or empty if there's none in the network there. */
    public OptionalInt repeaterFrequencyAt(BlockPos pos) {
        Repeater repeater = repeaters.get(pos);
        return repeater == null ? OptionalInt.empty() : OptionalInt.of(repeater.frequency());
    }

    /** The frequency of the transmitter or repeater at {@code pos}, or empty if there's neither. */
    public OptionalInt towerFrequencyAt(BlockPos pos) {
        OptionalInt transmitter = transmitterFrequencyAt(pos);
        return transmitter.isPresent() ? transmitter : repeaterFrequencyAt(pos);
    }

    /** The signal the repeater at {@code pos} is sending out right now (0 if it hears nothing, or there's none). */
    public int repeaterPower(ServerLevel level, BlockPos pos) {
        Repeater repeater = repeaters.get(pos);
        return repeater == null ? 0 : resolve(level, repeater.frequency()).getOrDefault(pos, 0);
    }

    /**
     * Works out what every repeater on {@code frequency} is sending, by position. First each one
     * gets what it hears straight from transmitters and remote presses; then, strongest first, each
     * repeater that hears something passes it on to the repeaters in its reach. Because it only
     * spreads outward from real sources, two repeaters in each other's reach can't keep each other
     * on once the source goes quiet. The strength is passed on unchanged.
     * <p>
     * The answer is kept until something on the frequency changes (its revision goes up), and
     * re-worked once a second anyway, for changes the revision doesn't see (a relay tower of a
     * remote press being broken).
     */
    private Map<BlockPos, Integer> resolve(ServerLevel level, int frequency) {
        if (!repeatersByFrequency.containsKey(frequency)) {
            return Map.of();
        }
        Resolved cached = resolved.get(frequency);
        if (cached != null && cached.revision() == revision(frequency)
                && now >= cached.computedAt() && now - cached.computedAt() < RESOLVE_INTERVAL) {
            return cached.power();
        }
        dropStaleRepeaters(level, frequency);
        Map<BlockPos, Repeater> onFrequency = repeatersByFrequency.get(frequency);
        if (onFrequency == null) {
            resolved.remove(frequency);
            return Map.of();
        }
        List<BlockPos> positions = new ArrayList<>(onFrequency.keySet());
        Map<BlockPos, Integer> power = new HashMap<>();
        for (BlockPos pos : positions) {
            power.put(pos, listenDirect(level, frequency, pos).power());
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
            Coverage area = new Coverage(next, onFrequency.get(next).reach(), true);
            for (BlockPos pos : positions) {
                if (power.get(pos) < nextPower && !done.contains(pos) && area.reaches(pos)) {
                    power.put(pos, nextPower);
                }
            }
        }
        Map<BlockPos, Integer> result = Map.copyOf(power);
        resolved.put(frequency, new Resolved(revision(frequency), now, result));
        return result;
    }

    /** Self-repair: forgets repeaters on {@code frequency} whose block is gone (for example removed by a world editor). */
    private void dropStaleRepeaters(ServerLevel level, int frequency) {
        Map<BlockPos, Repeater> onFrequency = repeatersByFrequency.get(frequency);
        if (onFrequency == null) {
            return;
        }
        List<BlockPos> stale = null;
        for (BlockPos pos : onFrequency.keySet()) {
            if (isStale(level, pos)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(pos);
            }
        }
        if (stale != null) {
            stale.forEach(this::removeRepeater);
        }
    }

    /** True if {@code pos} is loaded and holds neither a transmitter nor a repeater any more. */
    private static boolean isStale(@Nullable ServerLevel level, BlockPos pos) {
        return level != null && level.isLoaded(pos) && !isTower(level.getBlockState(pos).getBlock());
    }

    /**
     * {@code coverage} plus the area of every repeater on {@code frequency} the signal gets to
     * through it: repeaters inside the coverage, then repeaters inside those, and so on. Used to
     * count who hears a remote press and to remember where a turn-on reached. With a
     * {@code level}, repeaters whose block is gone are skipped.
     */
    public List<Coverage> withRepeaters(@Nullable ServerLevel level, int frequency, List<Coverage> coverage) {
        Map<BlockPos, Repeater> onFrequency = repeatersByFrequency.get(frequency);
        if (onFrequency == null) {
            return coverage;
        }
        List<Coverage> result = new ArrayList<>(coverage);
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<Coverage> queue = new ArrayDeque<>(coverage);
        while (!queue.isEmpty()) {
            Coverage area = queue.poll();
            for (Map.Entry<BlockPos, Repeater> entry : onFrequency.entrySet()) {
                BlockPos pos = entry.getKey();
                if (reached.contains(pos) || !area.reaches(pos) || isStale(level, pos)) {
                    continue;
                }
                reached.add(pos);
                Coverage own = new Coverage(pos, entry.getValue().reach(), true);
                if (!result.contains(own)) { // already there if it relayed the press as a tower
                    result.add(own);
                }
                queue.add(own);
            }
        }
        return result;
    }

    public long revision(int frequency) {
        return revisions.getOrDefault(frequency, 0L);
    }

    private void bump(int frequency) {
        revisions.merge(frequency, 1L, Long::sum);
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
        recordTurnOn(frequency, coverage);
        bump(frequency);
    }

    /**
     * Toggle mode: switches {@code frequency} on (over {@code coverage}) or off. Returns true if it
     * is now on. The areas are fixed when it's switched on; moving afterwards doesn't change them.
     */
    public boolean toggleLatch(int frequency, List<Coverage> coverage) {
        boolean nowOn;
        if (latches.remove(frequency) != null) {
            nowOn = false;
        } else {
            latches.put(frequency, new RemoteSignal(coverage, Long.MAX_VALUE));
            recordTurnOn(frequency, coverage);
            nowOn = true;
        }
        bump(frequency);
        setDirty();
        return nowOn;
    }

    // ---- History, for the pagers' catch-up ----

    /** The most turn-ons remembered per frequency. */
    public static final int MAX_HISTORY = 32;

    /** One time a frequency turned on: when, and where it could be heard. */
    public record TurnOn(long gameTime, long dayTime, List<Coverage> coverage) {
        public TurnOn {
            coverage = List.copyOf(coverage);
        }
    }

    /** Remembers a turn-on, over {@code coverage} plus every repeater on the frequency it reaches. */
    private void recordTurnOn(int frequency, List<Coverage> coverage) {
        ArrayDeque<TurnOn> list = history.computeIfAbsent(frequency, f -> new ArrayDeque<>());
        list.addLast(new TurnOn(now, nowDayTime, withRepeaters(null, frequency, coverage)));
        while (list.size() > MAX_HISTORY) {
            list.removeFirst();
        }
        setDirty();
    }

    /**
     * The times {@code frequency} turned on after {@code sinceGameTime} where a receiver at
     * {@code pos} would have heard it, oldest first. At most {@link #MAX_HISTORY}.
     */
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
        BlockPos key = pos.immutable();
        Integer previous = receivers.put(key, frequency);
        if (previous != null && previous != frequency) {
            removeReceiverFromIndex(key, previous);
        }
        receiversByFrequency.computeIfAbsent(frequency, f -> new HashSet<>()).add(key);
    }

    /** A receiver was broken or its chunk unloaded. */
    public void untrackReceiver(BlockPos pos) {
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
            if (covers(coverage, pos)) {
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
    public enum Source { TRANSMITTER, REMOTE_PULSE, REMOTE_TOGGLE, REPEATER }

    /**
     * One frequency the Signal Scanner hears. {@code transmitters} is how many transmitters and
     * repeaters on it reach this spot. For a {@link Source#TRANSMITTER} or {@link Source#REPEATER},
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
    private record BestTransmitter(int power, long distSq, BlockPos at, int count, boolean repeater) {
        boolean beats(BestTransmitter other) {
            if (power != other.power) return power > other.power;
            if (distSq != other.distSq) return distSq < other.distSq;
            return !repeater && other.repeater;
        }
    }

    private static void offer(Map<Integer, BestTransmitter> best, int frequency, BestTransmitter candidate) {
        BestTransmitter old = best.get(frequency);
        if (old == null) {
            best.put(frequency, candidate);
        } else {
            BestTransmitter winner = candidate.beats(old) ? candidate : old;
            best.put(frequency, new BestTransmitter(winner.power(), winner.distSq(), winner.at(), old.count() + 1, winner.repeater()));
        }
    }

    private Map<Integer, BestTransmitter> bestTransmitters(ServerLevel level, BlockPos pos) {
        Map<Integer, BestTransmitter> best = new HashMap<>();
        List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, Transmitter> entry : transmitters.entrySet()) {
            BlockPos at = entry.getKey();
            Transmitter tx = entry.getValue();
            long distSq = horizontalDistSq(at, pos);
            if (distSq > (long) tx.reach() * tx.reach()) {
                continue;
            }
            if (level.isLoaded(at) && !(level.getBlockState(at).getBlock() instanceof RadioTransmitterBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(at);
                continue;
            }
            offer(best, tx.frequency(), new BestTransmitter(tx.power(), distSq, at, 1, false));
        }
        if (stale != null) {
            stale.forEach(this::removeTransmitter);
        }
        // Copy first: working out a chain can forget repeaters whose block is gone.
        for (BlockPos at : new ArrayList<>(repeaters.keySet())) {
            Repeater repeater = repeaters.get(at);
            if (repeater == null) {
                continue;
            }
            long distSq = horizontalDistSq(at, pos);
            if (distSq > (long) repeater.reach() * repeater.reach()) {
                continue;
            }
            int power = resolve(level, repeater.frequency()).getOrDefault(at, 0);
            if (repeaters.containsKey(at)) {
                offer(best, repeater.frequency(), new BestTransmitter(power, distSq, at, 1, true));
            }
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
                heard.add(new HeardFrequency(frequency, tx.power(), tx.repeater() ? Source.REPEATER : Source.TRANSMITTER,
                        count, distance, distance == 0 ? -1 : compassDirection(dx, dz)));
            }
        }
        heard.sort(Comparator.comparingInt(HeardFrequency::power).reversed()
                .thenComparingInt(HeardFrequency::frequency));
        return heard.size() > MAX_SCAN_RESULTS ? new ArrayList<>(heard.subList(0, MAX_SCAN_RESULTS)) : heard;
    }

    /**
     * Finds the strongest signal on {@code frequency} that reaches {@code receiverPos}. A
     * transmitter or repeater reaches every receiver within its reach, measured horizontally, at
     * any height. {@code transmittersInReach} counts repeaters too.
     */
    public Reception listen(ServerLevel level, int frequency, BlockPos receiverPos) {
        Reception direct = listenDirect(level, frequency, receiverPos);
        if (!repeatersByFrequency.containsKey(frequency)) {
            return direct;
        }
        Map<BlockPos, Integer> repeating = resolve(level, frequency);
        Map<BlockPos, Repeater> onFrequency = repeatersByFrequency.get(frequency);
        if (onFrequency == null) { // resolving forgot the last ones (their blocks were gone)
            return direct;
        }
        int best = direct.power();
        int heard = direct.transmittersInReach();
        for (Map.Entry<BlockPos, Repeater> entry : onFrequency.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.equals(receiverPos) || horizontalDistSq(pos, receiverPos) > (long) entry.getValue().reach() * entry.getValue().reach()) {
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
            if (dx * dx + dz * dz > reach * reach) {
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
            list.add(entry);
        });
        tag.put("transmitters", list);

        ListTag repeaterList = new ListTag();
        repeaters.forEach((pos, repeater) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("frequency", repeater.frequency());
            entry.putInt("reach", repeater.reach());
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
                    entry.getInt("reach"));
            network.transmitters.put(pos, tx);
            network.byFrequency.computeIfAbsent(tx.frequency(), f -> new HashMap<>()).put(pos, tx);
        }
        ListTag repeaterList = tag.getList("repeaters", Tag.TAG_COMPOUND);
        for (int i = 0; i < repeaterList.size(); i++) {
            CompoundTag entry = repeaterList.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            Repeater repeater = new Repeater(Frequency.clamp(entry.getInt("frequency")), entry.getInt("reach"));
            network.repeaters.put(pos, repeater);
            network.repeatersByFrequency.computeIfAbsent(repeater.frequency(), f -> new HashMap<>()).put(pos, repeater);
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
            recent.addLast(new TurnOn(entry.getLong("time"), entry.getLong("day_time"), coverage));
            while (recent.size() > MAX_HISTORY) {
                recent.removeFirst();
            }
        }
        return network;
    }
}
