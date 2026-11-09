package com.reiko.signaltower.compat;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.Telegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A radio that moves (v0.59.0): a CC: Tweaked turtle or pocket computer with a Radio Pager fitted.
 * What it can do, without CC: Tweaked's classes (the peripheral in {@code compat/cct} calls these,
 * and the GameTests use them directly): hear a frequency where it is, scan, send a pulse like a
 * Remote Control (its reach by its height, relayed by towers), and send and read telegraph messages.
 * Devices with a computer attached are listed here, so telegraph messages reach them as they reach
 * pagers ({@link #deliver}).
 */
public final class MobileRadio {
    private MobileRadio() {
    }

    /** A turtle or pocket computer carrying a radio. */
    public interface Device {
        /** Where it is now (null if it isn't in a loaded world). */
        @Nullable ServerLevel level();

        BlockPos position();

        int frequency();

        /** Whose it is (the turtle's owner, the pocket computer's holder): decides private and encrypted frequencies. */
        @Nullable UUID owner();

        /** A telegraph message on its frequency reached it. */
        void message(int frequency, String text, String from);
    }

    private static final Set<Device> DEVICES = ConcurrentHashMap.newKeySet();

    public static void add(Device device) {
        DEVICES.add(device);
    }

    public static void remove(Device device) {
        DEVICES.remove(device);
    }

    public static boolean listed(Device device) {
        return DEVICES.contains(device);
    }

    /**
     * Called for every telegraph message (from {@link Telegraph}): hands it to the listed devices on
     * {@code frequency} inside {@code coverage} that may read it (not the one that sent it). Returns how many.
     */
    public static int deliver(ServerLevel level, int frequency, List<SignalNetwork.Coverage> coverage, SignalNetwork.Message message) {
        if (DEVICES.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Device device : DEVICES) {
            if (device.level() != level || device.frequency() != frequency) {
                continue;
            }
            BlockPos pos = device.position();
            if (pos.equals(message.from()) || !SignalNetwork.covers(coverage, pos) || !FrequencyClaims.mayHear(level, frequency, device.owner())) {
                continue;
            }
            device.message(frequency, message.text(), message.name());
            count++;
        }
        return count;
    }

    /** The strength heard at {@code pos} on {@code frequency} by {@code owner} (0 on someone else's encrypted frequency, or out of range). */
    public static int heard(ServerLevel level, BlockPos pos, long frequency, @Nullable UUID owner) {
        if (frequency < Frequency.MIN || frequency > Frequency.MAX || !FrequencyClaims.mayHear(level, (int) frequency, owner)) {
            return 0;
        }
        return SignalNetwork.get(level).listen(level, (int) frequency, pos, owner).power();
    }

    /** Everything that could be heard at {@code pos}: frequency, strength and name, strongest first. */
    public static List<Map<String, Object>> scan(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        return RadioComputer.scanAt(level, pos, owner);
    }

    /** How far it reaches from {@code pos}: by its height, like a player with a remote. */
    public static int reach(ServerLevel level, BlockPos pos) {
        return RadioHelper.reachAt(level, pos);
    }

    /** What a pulse did: whether it went out, how many receivers and pagers heard it, or why not. */
    public record Pulse(boolean sent, int heardBy, String refusal) {
    }

    /**
     * Sends a full-strength pulse of {@code ticks} (clamped to 0.1-10 s) on {@code frequency} from
     * {@code pos}, as a Remote Control would there: its reach by height, relayed by the towers in it
     * and carried on by repeaters. Refused on a private frequency {@code owner} isn't allowed on.
     */
    public static Pulse pulse(ServerLevel level, BlockPos pos, long frequency, @Nullable UUID owner, String name, int ticks) {
        if (frequency < Frequency.MIN || frequency > Frequency.MAX) {
            return new Pulse(false, 0, Component.translatable("computer.reikos_signal_tower.frequency_range", Frequency.MIN, Frequency.MAX).getString());
        }
        int f = (int) frequency;
        Component refused = FrequencyClaims.refusal(level, f, owner, "private_refused_send");
        if (refused != null) {
            return new Pulse(false, 0, refused.getString());
        }
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> coverage = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos), Config.TOWERS_RELAY_REMOTES.get());
        SignalNetwork.Carried carried = network.carried(level, f, coverage);
        network.pulse(f, coverage, level.getGameTime() + PulseLength.clamp(ticks), SignalNetwork.Cause.remote(false, owner, name));
        return new Pulse(true, RemoteControlItem.heardBy(level, network, carried), "");
    }

    /**
     * Sends {@code raw} as a telegraph message from {@code pos} on {@code frequency}, signed
     * {@code name}: read by pagers, telegraphs, Status Boards and other devices in its reach (and
     * the towers', and repeaters'). Returns why not (empty, private), or null if it went out.
     */
    public static @Nullable String send(ServerLevel level, BlockPos pos, long frequency, @Nullable UUID owner, String name, String raw) {
        if (frequency < Frequency.MIN || frequency > Frequency.MAX) {
            return Component.translatable("computer.reikos_signal_tower.frequency_range", Frequency.MIN, Frequency.MAX).getString();
        }
        int f = (int) frequency;
        String text = Telegraph.clean(raw);
        if (text.isEmpty()) {
            return Component.translatable("message.reikos_signal_tower.telegraph_empty").getString();
        }
        Component refused = FrequencyClaims.refusal(level, f, owner, "private_refused_telegraph");
        if (refused != null) {
            return refused.getString();
        }
        Telegraph.broadcast(level, pos, f, Telegraph.coverage(level, pos, f), name, text);
        return null;
    }

    /** The messages on {@code frequency} that could be read at {@code pos}, oldest first (text left out on someone else's encrypted frequency). */
    public static List<Map<String, Object>> messages(ServerLevel level, BlockPos pos, int frequency, @Nullable UUID owner) {
        return RadioComputer.messagesAt(level, pos, frequency, owner);
    }

    /** For the tests: how many devices are listed. */
    public static int count() {
        return DEVICES.size();
    }
}
