package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Turns a locator result into one line of text for the remote screens. */
public final class LocatorText {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".locator.";
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private LocatorText() {}

    public static Component describe(@Nullable RemoteScanResultPayload.Entry entry, int reach) {
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
            return Component.translatable(KEY + "out_of_range", where, reach).withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(KEY + "none", reach * 2).withStyle(ChatFormatting.RED);
    }
}
