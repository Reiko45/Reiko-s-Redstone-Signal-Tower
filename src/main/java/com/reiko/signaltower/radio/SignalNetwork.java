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
import java.util.HashMap;
import java.util.HashSet;
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
 * (saved, stays on until any remote on that frequency switches it off).
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

    /** A signal from a Remote Control: full strength, sent from where the player pressed it. */
    public record RemoteSignal(BlockPos origin, int reach, long expiresAtGameTime) {
        boolean reaches(BlockPos receiverPos) {
            long dx = origin.getX() - receiverPos.getX();
            long dz = origin.getZ() - receiverPos.getZ();
            return dx * dx + dz * dz <= (long) reach * reach;
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

    /** Sends a full-strength signal on {@code frequency} until {@code untilGameTime}. */
    public void pulse(int frequency, BlockPos origin, int reach, long untilGameTime) {
        RemoteSignal previous = pulses.get(frequency);
        long until = previous == null ? untilGameTime : Math.max(previous.expiresAtGameTime(), untilGameTime);
        pulses.put(frequency, new RemoteSignal(origin.immutable(), reach, until));
        bump(frequency);
    }

    /** Toggle mode: switches {@code frequency} on (from {@code origin}) or off. Returns true if it is now on. */
    public boolean toggleLatch(int frequency, BlockPos origin, int reach) {
        boolean nowOn;
        if (latches.remove(frequency) != null) {
            nowOn = false;
        } else {
            latches.put(frequency, new RemoteSignal(origin.immutable(), reach, Long.MAX_VALUE));
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
        Set<BlockPos> onFrequency = receiversByFrequency.get(frequency);
        if (onFrequency == null) {
            return 0;
        }
        RemoteSignal area = new RemoteSignal(origin, reach, 0);
        int count = 0;
        for (BlockPos pos : onFrequency) {
            if (area.reaches(pos)) {
                count++;
            }
        }
        return count;
    }

    /**
     * What the remotes' locator shows for one frequency: how many loaded receivers are within
     * {@code reach} of {@code origin}, and where the nearest one is. The nearest one is only
     * reported if it's within twice the reach, so the locator can't find receivers far away.
     *
     * @param nearestDistance horizontal distance in blocks, or -1 if none is near enough
     * @param direction 0-7 for N, NE, E, SE, S, SW, W, NW, or -1 if right here or none
     */
    public record ReceiverScan(int inRange, int nearestDistance, int direction, boolean nearestInRange) {}

    public ReceiverScan scanReceivers(int frequency, BlockPos origin, int reach) {
        Set<BlockPos> onFrequency = receiversByFrequency.get(frequency);
        if (onFrequency == null) {
            return new ReceiverScan(0, -1, -1, false);
        }
        long reachSq = (long) reach * reach;
        long hintSq = 4L * reachSq;
        int inRange = 0;
        long bestSq = Long.MAX_VALUE;
        long bestDx = 0;
        long bestDz = 0;
        for (BlockPos pos : onFrequency) {
            long dx = pos.getX() - origin.getX();
            long dz = pos.getZ() - origin.getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq <= reachSq) {
                inRange++;
            }
            if (distSq <= hintSq && distSq < bestSq) {
                bestSq = distSq;
                bestDx = dx;
                bestDz = dz;
            }
        }
        if (bestSq == Long.MAX_VALUE) {
            return new ReceiverScan(inRange, -1, -1, false);
        }
        int distance = (int) Math.round(Math.sqrt(bestSq));
        return new ReceiverScan(inRange, distance, compassDirection(bestDx, bestDz), bestSq <= reachSq);
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
            if (remote != null && remote.reaches(receiverPos)) {
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
            entry.putLong("pos", latch.origin().asLong());
            entry.putInt("reach", latch.reach());
            latchList.add(entry);
        });
        tag.put("remote_latches", latchList);
        return tag;
    }

    private static SignalNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
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
            network.latches.put(Frequency.clamp(entry.getInt("frequency")),
                    new RemoteSignal(BlockPos.of(entry.getLong("pos")), entry.getInt("reach"), Long.MAX_VALUE));
        }
        return network;
    }
}
