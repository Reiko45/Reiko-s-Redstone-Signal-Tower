package com.reiko.signaltower.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The frequency log (v0.35.0): the last turn-ons of a private frequency, in every dimension,
 * newest first, with what did each one (a transmitter, a remote pulse or toggle) and who. Only
 * its owner and operators see it ({@link FrequencyAudit#mayAudit}).
 * <p>
 * Like the audit, it shows where a transmitter is only if it's the owner's, a member's or one
 * with no recorded tuner; another player's is named but not located. Remote presses never have a
 * position (where someone stood isn't kept). A Code Keypad's turn-on (v0.37.0) shows the keypad and
 * who typed the code; a Radio Motion Sensor's (v0.39.0) shows the sensor and who it saw.
 */
public final class FrequencyLog {
    /** The most rows shown: as many as one dimension remembers per frequency. */
    public static final int MAX_ENTRIES = SignalNetwork.MAX_HISTORY;

    private FrequencyLog() {
    }

    /**
     * One turn-on. {@code who} is a player name, or "" when nobody is recorded; {@code whoKind}
     * says how they relate to the frequency. {@code from} is the frequency it started on when a
     * converter carried it over (0 if it started on this one or isn't known).
     */
    public record Entry(SignalNetwork.CauseKind kind, String dimension, Optional<BlockPos> pos, String who,
                        FrequencyAudit.Who whoKind, long gameTime, long dayTime, int from, String detail) {
        /** No detail. ({@code detail}, v0.38.0: the keypad guest code used, or "".) */
        public Entry(SignalNetwork.CauseKind kind, String dimension, Optional<BlockPos> pos, String who,
                     FrequencyAudit.Who whoKind, long gameTime, long dayTime, int from) {
            this(kind, dimension, pos, who, whoKind, gameTime, dayTime, from, "");
        }
    }

    /** {@code frequency}'s turn-ons, newest first, at most {@link #MAX_ENTRIES}. Empty if it isn't private. */
    public static List<Entry> log(MinecraftServer server, int frequency) {
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(frequency);
        List<Entry> entries = new ArrayList<>();
        if (claim == null) {
            return entries;
        }
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            List<SignalNetwork.TurnOn> history = new ArrayList<>(SignalNetwork.get(level).history(frequency));
            java.util.Collections.reverse(history); // newest first, so equal times keep that order below
            for (SignalNetwork.TurnOn turnOn : history) {
                entries.add(entry(server, claim, frequency, dimension, turnOn));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::gameTime).reversed());
        return entries.size() > MAX_ENTRIES ? new ArrayList<>(entries.subList(0, MAX_ENTRIES)) : entries;
    }

    private static Entry entry(MinecraftServer server, FrequencyClaims.Claim claim, int frequency, String dimension,
                               SignalNetwork.TurnOn turnOn) {
        SignalNetwork.Cause cause = turnOn.cause();
        UUID player = cause.who();
        FrequencyAudit.Who whoKind = player == null ? FrequencyAudit.Who.UNKNOWN
                : player.equals(claim.owner()) ? FrequencyAudit.Who.OWNER
                : claim.allows(player) ? FrequencyAudit.Who.MEMBER
                : FrequencyAudit.Who.OTHER;
        boolean remote = cause.kind() == SignalNetwork.CauseKind.REMOTE_PULSE || cause.kind() == SignalNetwork.CauseKind.REMOTE_TOGGLE
                || cause.kind() == SignalNetwork.CauseKind.KEYPAD || cause.kind() == SignalNetwork.CauseKind.SENSOR;
        String who = player == null ? ""
                : remote && !cause.name().isEmpty() && whoKind == FrequencyAudit.Who.OTHER ? cause.name()
                : FrequencyAudit.nameOf(server, claim, player);
        // A keypad's (or motion sensor's) position is shown whoever typed the code (or was seen): on a
        // private frequency only a member's keypad or sensor sends.
        Optional<BlockPos> pos = cause.pos() == null ? Optional.empty()
                : cause.kind() == SignalNetwork.CauseKind.KEYPAD || cause.kind() == SignalNetwork.CauseKind.SENSOR ? Optional.of(cause.pos())
                : (cause.kind() == SignalNetwork.CauseKind.TRANSMITTER || cause.kind() == SignalNetwork.CauseKind.GATE
                        || cause.kind() == SignalNetwork.CauseKind.TIMER)
                    && whoKind != FrequencyAudit.Who.OTHER ? Optional.of(cause.pos())
                : Optional.empty();
        int from = turnOn.from() != 0 && turnOn.from() != frequency ? turnOn.from() : 0;
        return new Entry(cause.kind(), dimension, pos, who, whoKind, turnOn.gameTime(), turnOn.dayTime(), from, cause.detail());
    }
}
