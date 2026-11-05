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
import java.util.List;
import java.util.Map;

/**
 * Every transmitter in one dimension, saved with the world.
 * <p>
 * Transmitters stay listed while their chunk is unloaded, so a receiver keeps getting the last
 * signal a far-away transmitter sent. Each frequency has a revision number that goes up whenever
 * anything on that frequency changes, so receivers only re-scan when they need to.
 */
public final class SignalNetwork extends SavedData {
    private static final String DATA_NAME = SignalTower.MOD_ID + "_network";

    private static final SavedData.Factory<SignalNetwork> FACTORY =
            new SavedData.Factory<>(SignalNetwork::new, SignalNetwork::load, null);

    /** What a transmitter is currently broadcasting. */
    public record Transmitter(int frequency, int power, int reach) {}

    /** What a receiver can hear: the strongest signal, and how many transmitters reach it. */
    public record Reception(int power, int transmittersInReach) {}

    private final Map<BlockPos, Transmitter> transmitters = new HashMap<>();
    private final Map<Integer, Map<BlockPos, Transmitter>> byFrequency = new HashMap<>();
    private final Map<Integer, Long> revisions = new HashMap<>();

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

    // ---- Receivers ----

    /**
     * Finds the strongest signal on {@code frequency} that reaches {@code receiverPos}. A
     * transmitter reaches every receiver within its reach, measured horizontally, at any height.
     */
    public Reception listen(ServerLevel level, int frequency, BlockPos receiverPos) {
        Map<BlockPos, Transmitter> onFrequency = byFrequency.get(frequency);
        if (onFrequency == null) {
            return new Reception(0, 0);
        }
        int best = 0;
        int heard = 0;
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
        return network;
    }
}
