package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Turns a locator result into text for the remote screens. */
public final class LocatorText {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".locator.";
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private LocatorText() {}

    /** "Your reach: 56 blocks, plus 2 towers relaying", or nothing until the first answer arrives. */
    public static Component reachLine(boolean scanned, int reach, int towers) {
        if (!scanned) {
            return Component.empty();
        }
        return towers == 0 ? Component.translatable(KEY + "reach", reach).withStyle(ChatFormatting.GRAY)
                : towers == 1 ? Component.translatable(KEY + "reach_one_tower", reach).withStyle(ChatFormatting.GRAY)
                : Component.translatable(KEY + "reach_towers", reach, towers).withStyle(ChatFormatting.GRAY);
    }

    public static Component describe(@Nullable RemoteScanResultPayload.Entry entry, int reach, int towers) {
        if (entry == null) {
            return Component.translatable(KEY + "checking").withStyle(ChatFormatting.DARK_GRAY);
        }
        Component where = entry.nearestDistance() <= 0 || entry.direction() < 0
                ? Component.translatable(KEY + "here")
                : Component.translatable(KEY + "distance", entry.nearestDistance(),
                        Component.translatable(KEY + "dir_" + DIRECTIONS[entry.direction() & 7]));
        if (entry.inRange() == 1) {
            return Component.translatable(KEY + "in_range_one", where).withStyle(ChatFormatting.GREEN);
        }
        if (entry.inRange() > 1) {
            return Component.translatable(KEY + "in_range_many", entry.inRange(), where).withStyle(ChatFormatting.GREEN);
        }
        if (entry.nearestDistance() >= 0) {
            return Component.translatable(KEY + "out_of_range", where).withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(KEY + (towers > 0 ? "none_towers" : "none"), reach * 2).withStyle(ChatFormatting.RED);
    }
}
