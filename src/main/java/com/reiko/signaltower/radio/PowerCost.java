package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.blockentity.RadioBatteryBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Power cost (v0.49.0). A full-size tower (transmitter of any kind, repeater, converter, jammer)
 * uses charge from the Radio Batteries next to it, or next to any mast in the unbroken column
 * beneath it, while it sends: {@link Config#CHARGE_PER_SECOND_PER_64_BLOCKS} a second for every 64
 * blocks of its reach. With no battery there, or only empty ones, it's out of power and sends
 * (relays, jams) nothing until one is charged. Compact transmitters, motion sensors, keypads and
 * remotes are free. Only towers in loaded chunks use charge; unloaded ones keep how they were.
 */
public final class PowerCost {
    /** How far up a mast column is searched for batteries (the same limit as for its redstone). */
    private static final int COLUMN_LIMIT = 4096;

    private PowerCost() {}

    private static @org.jetbrains.annotations.Nullable Boolean forTests;

    /** For the GameTests only: false switches power cost off whatever the config says; null leaves it to the config. */
    public static void powerForTests(@org.jetbrains.annotations.Nullable Boolean on) {
        forTests = on;
    }

    public static boolean enabled() {
        if (forTests != null) {
            return forTests;
        }
        var setting = Config.TOWERS_NEED_POWER;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    /** Charge a second a tower with a (stored, clear-weather) reach of {@code reach} uses while it sends. */
    public static double perSecond(int reach) {
        var setting = Config.CHARGE_PER_SECOND_PER_64_BLOCKS;
        double rate = Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
        return Math.max(0, reach) / 64.0 * rate;
    }

    public static int capacity() {
        var setting = Config.BATTERY_CAPACITY;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    public static int chargePerRedstone() {
        var setting = Config.CHARGE_PER_REDSTONE;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    public static int fePerCharge() {
        var setting = Config.FE_PER_CHARGE;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    /** The Radio Batteries feeding the tower whose top is at {@code top}: next to it, or next to a mast in the column beneath it. */
    public static List<RadioBatteryBlockEntity> batteries(Level level, BlockPos top) {
        Set<BlockPos> found = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = top.mutable();
        for (int i = 0; i < COLUMN_LIMIT; i++) {
            for (Direction side : Direction.values()) {
                BlockPos next = cursor.relative(side);
                // Never load a chunk to look (a tower at a chunk's edge, its neighbour not loaded).
                if (level.isLoaded(next) && level.getBlockEntity(next) instanceof RadioBatteryBlockEntity) {
                    found.add(next);
                }
            }
            cursor.move(Direction.DOWN);
            if (!(level.getBlockState(cursor).getBlock() instanceof RadioMastBlock)) {
                break;
            }
        }
        List<RadioBatteryBlockEntity> batteries = new ArrayList<>();
        for (BlockPos pos : found) {
            if (level.getBlockEntity(pos) instanceof RadioBatteryBlockEntity battery) {
                batteries.add(battery);
            }
        }
        return batteries;
    }

    /** The charge in {@code batteries} altogether. */
    public static long charge(List<RadioBatteryBlockEntity> batteries) {
        long total = 0;
        for (RadioBatteryBlockEntity battery : batteries) {
            total += battery.getCharge();
        }
        return total;
    }

    /** True if the block at {@code pos} is a tower that pays for sending (not a compact transmitter or motion sensor). */
    public static boolean pays(Level level, BlockPos pos) {
        return Lightning.isTower(level, pos);
    }

    /**
     * Right away (when a tower is placed, or its redstone or tuning changes): out of power if power
     * cost is on and it has no charged battery, else not. Nothing is used up here.
     */
    public static void check(ServerLevel level, BlockPos pos) {
        SignalNetwork network = SignalNetwork.get(level);
        if (!enabled() || !pays(level, pos) || level.getBlockState(pos).getBlock() instanceof CompactRadioTransmitterBlock) {
            network.setDrained(pos, false);
            return;
        }
        network.setDrained(pos, charge(batteries(level, pos)) <= 0);
    }

    /** Each level tick: once a second, every loaded tower that's sending pays for that second, and goes quiet if it can't. */
    public static void tick(ServerLevel level, SignalNetwork network) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        if (!enabled()) {
            for (BlockPos pos : network.drained()) {
                if (network.setDrained(pos, false)) {
                    refreshTower(level, pos);
                }
            }
            return;
        }
        long now = level.getGameTime();
        for (BlockPos pos : network.lightningTargets()) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            List<RadioBatteryBlockEntity> batteries = batteries(level, pos);
            long available = charge(batteries);
            boolean out = available <= 0;
            if (!out && network.wantsToSend(level, pos)) {
                double rate = perSecond(network.towerReach(pos));
                for (RadioBatteryBlockEntity battery : batteries) {
                    if (battery.getCharge() > 0) battery.noteUse(now, rate / batteries.size());
                }
                int due = network.owe(pos, rate);
                int paid = 0;
                for (RadioBatteryBlockEntity battery : batteries) {
                    if (paid >= due) break;
                    paid += battery.take(due - paid);
                }
                network.paid(pos, paid);
                out = paid < due;
            }
            if (network.setDrained(pos, out)) {
                refreshTower(level, pos);
            }
        }
    }

    /** Lets a tower's block entity catch up (its light, the network entry). */
    public static void refreshTower(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.refresh();
        } else if (level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity relay) {
            relay.refresh();
        }
    }

    /** "13h 20m", "25m", "40s": how long {@code seconds} is, roughly. */
    public static String durationLabel(double seconds) {
        long s = (long) Math.max(0, Math.floor(seconds));
        if (s >= 360000) return (s / 3600) + "h";
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        if (s >= 60) return (s / 60) + "m";
        return s + "s";
    }

    /** "45,000" */
    public static String number(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    /** "0.9", "12", "1.25": a charge rate. */
    public static String rateLabel(double rate) {
        if (rate >= 10) return String.valueOf(Math.round(rate));
        String text = String.format(java.util.Locale.ROOT, "%.2f", rate);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
