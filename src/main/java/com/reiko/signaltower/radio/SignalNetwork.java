package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
                        && !(level.getBlockState(area.origin()).getBlock() instanceof RadioTransmitterBlock)) {
                    continue;
                }
                return true;
            }
            return false;
        }
    }

    private final Map<BlockPos, Transmitter> transmitters = new HashMap<>();
    private final Map<Integer, Map<BlockPos, Transmitter>> byFrequency = new HashMap<>();
    private final Map<Integer, Long> revisions = new HashMap<>();
    private final Map<Integer, RemoteSignal> pulses = new HashMap<>();
    private final Map<Integer, RemoteSignal> latches = new HashMap<>();
    private final Map<BlockPos, Integer> receivers = new HashMap<>();
    private final Map<Integer, Set<BlockPos>> receiversByFrequency = new HashMap<>();

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

    public long revision(int frequency) {
        return revisions.getOrDefault(frequency, 0L);
    }

    private void bump(int frequency) {
        revisions.merge(frequency, 1L, Long::sum);
    }

    // ---- Remote Controls ----

    /**
     * Where a remote press from {@code origin} goes out: the player's own reach first, then, if
     * {@code relayThroughTowers}, every Radio Transmitter within that reach (horizontally, on any
     * frequency), each with its own reach. A tower only relays a press it heard directly; towers
     * don't pass it on to each other.
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
            nowOn = true;
        }
        bump(frequency);
        setDirty();
        return nowOn;
    }

    /** Ends pulses whose time is up. Called every server tick for each dimension. */
    public void tick(long gameTime) {
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

    /**
     * Finds the strongest signal on {@code frequency} that reaches {@code receiverPos}. A
     * transmitter reaches every receiver within its reach, measured horizontally, at any height.
     */
    public Reception listen(ServerLevel level, int frequency, BlockPos receiverPos) {
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
        return network;
    }
}
