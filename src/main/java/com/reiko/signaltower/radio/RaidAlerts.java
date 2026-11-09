package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.jetbrains.annotations.Nullable;

/**
 * Raid alerts (v0.58.0): what a village radio station knows about a raid near it, and the messages
 * it telegraphs. The station itself ({@code RadioTransmitterBlockEntity#updateRaid}) keeps which
 * raid it's following and which wave it last announced.
 */
public final class RaidAlerts {
    /** How often a village station looks for a raid, in ticks. */
    public static final int CHECK_TICKS = 40;
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private RaidAlerts() {
    }

    public enum Status { ONGOING, VICTORY, LOSS }

    /** A raid as a station sees it: its id, the waves that have come so far and how many there will be. */
    public record Snapshot(int id, int wave, int waves, Status status) {
    }

    /** The raid within 96 blocks of {@code pos} (the game's own "raided" distance), or null if there's none. */
    public static @Nullable Snapshot near(ServerLevel level, BlockPos pos) {
        Raid raid = level.getRaidAt(pos);
        if (raid == null || raid.isStopped()) {
            return null;
        }
        // A bad omen above level 1 brings one more wave (the game's own rule).
        int waves = raid.getNumGroups(level.getDifficulty()) + (raid.getRaidOmenLevel() > 1 ? 1 : 0);
        Status status = raid.isVictory() ? Status.VICTORY : raid.isLoss() ? Status.LOSS : Status.ONGOING;
        return new Snapshot(raid.getId(), raid.getGroupsSpawned(), waves, status);
    }

    /** The name its messages are sent under: "Village Radio". */
    public static String senderName() {
        return Component.translatable("block." + SignalTower.MOD_ID + ".village_station.short").getString();
    }

    /** "RAID! Pillagers are coming to the village" */
    public static String startText() {
        return Telegraph.clean(Component.translatable(KEY + "raid_start").getString());
    }

    /** "Raid: wave 2 of 5" */
    public static String waveText(int wave, int waves) {
        return Telegraph.clean(Component.translatable(KEY + "raid_wave", wave, waves).getString());
    }

    /** "The raid is over: the village held" / "... the village fell" / "The raid has ended" */
    public static String endText(@Nullable Status status) {
        String key = status == Status.VICTORY ? "raid_victory" : status == Status.LOSS ? "raid_loss" : "raid_ended";
        return Telegraph.clean(Component.translatable(KEY + key).getString());
    }
}
