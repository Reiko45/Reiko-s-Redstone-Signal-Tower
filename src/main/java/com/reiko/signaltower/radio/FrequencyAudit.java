package com.reiko.signaltower.radio;

import com.reiko.signaltower.block.CompactRadioReceiverBlock;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The frequency audit (v0.31.0): every block on one private frequency, in every dimension, for its
 * owner (or a server operator). Transmitters, repeaters and converters sending on it, converters
 * listening on it, and loaded receivers tuned to it, each with who tuned it and whether it works,
 * is kept quiet (silent) or can't hear it (deaf).
 * <p>
 * Blocks tuned by the owner or a member, and old ones with no recorded tuner, come with their
 * position, so they can be found. Other players' blocks are only counted (per player, kind and
 * status), so the audit can't be used to find someone's base.
 */
public final class FrequencyAudit {
    private FrequencyAudit() {
    }

    /** What a row is. Sent by ordinal: add new ones at the end. */
    public enum Kind {
        TRANSMITTER, REPEATER, CONVERTER_SENDS, CONVERTER_LISTENS, RECEIVER, TOGGLE_RECEIVER, COMPACT_RECEIVER, COMPACT_TRANSMITTER, INVERTED_RECEIVER, MOTION_SENSOR, LOGIC_RECEIVER, DELAY_RECEIVER, TIMER_TRANSMITTER, COUNTER_RECEIVER;

        public static Kind of(int ordinal) {
            Kind[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    /** Whose block it is, as seen from the frequency's owner. Sent by ordinal. */
    public enum Who {
        OWNER, MEMBER, UNKNOWN, OTHER;

        public static Who of(int ordinal) {
            Who[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    /** Whether it works on the frequency. Sent by ordinal. */
    public enum Status {
        WORKING, SILENT, DEAF;

        public static Status of(int ordinal) {
            Status[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    /**
     * One row. {@code dimension} and {@code pos} are empty for other players' blocks, which are
     * counted ({@code count}) instead; {@code power} is what it sends or outputs (-1 for a count).
     * {@code tuner} is a player name, or "" for no recorded tuner.
     */
    public record Entry(Kind kind, String dimension, Optional<BlockPos> pos, String tuner, Who who, Status status,
                        int count, int power) {
    }

    /** True if {@code player} may audit {@code frequency}: it's private, and theirs (or they're an operator). */
    public static boolean mayAudit(Player player, int frequency) {
        MinecraftServer server = player.getServer();
        FrequencyClaims.Claim claim = server == null ? null : FrequencyClaims.get(server).get(frequency);
        return claim != null && (claim.owner().equals(player.getUUID()) || player.hasPermissions(2));
    }

    /**
     * Every block on {@code frequency} in every dimension, sorted: the owner's first, then
     * members', then ones with no recorded tuner (each nearest to {@code viewer} first), then other
     * players' counts. Empty if it isn't private. At most {@code limit} rows.
     */
    public static List<Entry> audit(MinecraftServer server, int frequency, @Nullable Player viewer, int limit) {
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(frequency);
        List<Entry> placed = new ArrayList<>();
        Map<String, int[]> others = new LinkedHashMap<>(); // "tuner|kind|status" -> count
        Map<String, Entry> otherKeys = new LinkedHashMap<>();
        if (claim == null) {
            return placed;
        }
        for (ServerLevel level : server.getAllLevels()) {
            SignalNetwork network = SignalNetwork.get(level);
            String dimension = level.dimension().location().toString();
            List<Entry> found = new ArrayList<>();

            network.transmittersOn(level, frequency).forEach((pos, tx) -> found.add(row(server, claim, !tx.compact()
                    ? (level.getBlockState(pos).getBlock() instanceof com.reiko.signaltower.block.RadioTimerTransmitterBlock ? Kind.TIMER_TRANSMITTER : Kind.TRANSMITTER)
                    : level.getBlockState(pos).getBlock() instanceof com.reiko.signaltower.block.RadioMotionSensorBlock ? Kind.MOTION_SENSOR
                    : Kind.COMPACT_TRANSMITTER,
                    dimension, pos, tx.tunedBy(), network.isSilenced(level, pos) ? Status.SILENT : Status.WORKING, tx.power())));

            network.relaysOn(level, frequency).forEach((pos, relay) -> {
                boolean converter = level.isLoaded(pos)
                        ? level.getBlockState(pos).getBlock() instanceof RadioConverterBlock
                        : relay.output() != relay.frequency();
                Kind kind;
                Status status;
                if (relay.output() == frequency) {
                    kind = converter ? Kind.CONVERTER_SENDS : Kind.REPEATER;
                    status = network.isSilenced(level, pos) ? Status.SILENT
                            : network.isDeaf(level, pos) ? Status.DEAF : Status.WORKING;
                } else {
                    kind = Kind.CONVERTER_LISTENS;
                    status = network.isDeaf(level, pos) ? Status.DEAF : Status.WORKING;
                }
                found.add(row(server, claim, kind, dimension, pos, relay.tunedBy(), status, network.repeaterPower(level, pos)));
            });

            for (SignalNetwork.TrackedReceiver receiver : network.receiversOn(frequency)) {
                BlockState state = level.getBlockState(receiver.pos());
                Block block = state.getBlock();
                if (!(block instanceof RadioReceiverBlock)) {
                    continue;
                }
                // The tracked tuner is the one the receiver checked in with; the block entity knows for sure.
                UUID tunedBy = level.getBlockEntity(receiver.pos()) instanceof RadioBlockEntity radio
                        ? radio.getTunedBy() : receiver.tunedBy();
                Kind kind = block instanceof RadioToggleReceiverBlock ? Kind.TOGGLE_RECEIVER
                        : block instanceof com.reiko.signaltower.block.RadioInvertedReceiverBlock ? Kind.INVERTED_RECEIVER
                        : block instanceof com.reiko.signaltower.block.RadioLogicReceiverBlock ? Kind.LOGIC_RECEIVER
                        : block instanceof com.reiko.signaltower.block.RadioDelayReceiverBlock ? Kind.DELAY_RECEIVER
                        : block instanceof com.reiko.signaltower.block.RadioCounterReceiverBlock ? Kind.COUNTER_RECEIVER
                        : block instanceof CompactRadioReceiverBlock ? Kind.COMPACT_RECEIVER : Kind.RECEIVER;
                Status status = FrequencyClaims.mayHear(level, frequency, tunedBy) ? Status.WORKING : Status.DEAF;
                found.add(row(server, claim, kind, dimension, receiver.pos(), tunedBy, status,
                        state.getValue(RadioReceiverBlock.POWER)));
            }

            for (Entry entry : found) {
                if (entry.who() == Who.OTHER) {
                    String key = entry.tuner() + "|" + entry.kind() + "|" + entry.status();
                    others.computeIfAbsent(key, k -> new int[1])[0]++;
                    otherKeys.putIfAbsent(key, entry);
                } else {
                    placed.add(entry);
                }
            }
        }

        String viewerDimension = viewer == null ? "" : viewer.level().dimension().location().toString();
        BlockPos viewerPos = viewer == null ? BlockPos.ZERO : viewer.blockPosition();
        placed.sort(Comparator.comparing(Entry::who)
                .thenComparing(entry -> !entry.dimension().equals(viewerDimension))
                .thenComparingDouble(entry -> entry.pos().map(pos -> pos.distSqr(viewerPos)).orElse(0.0)));

        List<Entry> rows = new ArrayList<>();
        for (Entry entry : placed) {
            if (rows.size() >= limit) {
                return rows;
            }
            rows.add(entry);
        }
        for (Map.Entry<String, int[]> group : others.entrySet()) {
            if (rows.size() >= limit) {
                break;
            }
            Entry sample = otherKeys.get(group.getKey());
            rows.add(new Entry(sample.kind(), "", Optional.empty(), sample.tuner(), Who.OTHER, sample.status(), group.getValue()[0], -1));
        }
        return rows;
    }

    /** One block's row; another player's block gets no position (it's counted later). */
    private static Entry row(MinecraftServer server, FrequencyClaims.Claim claim, Kind kind, String dimension, BlockPos pos,
                             @Nullable UUID tunedBy, Status status, int power) {
        Who who = tunedBy == null ? Who.UNKNOWN
                : tunedBy.equals(claim.owner()) ? Who.OWNER
                : claim.allows(tunedBy) ? Who.MEMBER : Who.OTHER;
        String tuner = tunedBy == null ? "" : nameOf(server, claim, tunedBy);
        return who == Who.OTHER
                ? new Entry(kind, "", Optional.empty(), tuner, who, status, 1, -1)
                : new Entry(kind, dimension, Optional.of(pos.immutable()), tuner, who, status, 1, power);
    }

    /**
     * A player's name from a UUID: the claim's own records (owner, friends, group members), an
     * online player, or the server's profile cache (no network lookup); else the start of the UUID.
     */
    public static String nameOf(MinecraftServer server, FrequencyClaims.Claim claim, UUID player) {
        if (player.equals(claim.owner())) {
            return claim.ownerName();
        }
        String known = claim.friends().get(player);
        if (known == null) {
            known = claim.groupMembers().get(player);
        }
        if (known != null) {
            return known;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(player);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return player.toString().substring(0, 8);
    }
}
