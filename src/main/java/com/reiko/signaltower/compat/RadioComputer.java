package com.reiko.signaltower.compat;

import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.blockentity.TelegraphBlockEntity;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.Telegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * What a computer can do with a radio block (v0.58.0), without CC: Tweaked's classes: the
 * peripherals in {@code compat/cct} only turn Lua calls into these, and the GameTests use them
 * directly. Everything here but the listeners runs on the server thread.
 * <p>
 * A computer reads and sets the frequency of a transmitter, receiver or telegraph (a transmitter
 * can't be set onto a private frequency its tuner isn't allowed on); sets the strength a
 * transmitter sends (the higher of that and its redstone); reads what a receiver hears, on its own
 * frequency or any other, and everything it could hear; and sends and reads telegraph messages.
 * Computers attached to a block get "radio_signal" when what a receiver hears changes and
 * "radio_message" when a message reaches a telegraph.
 */
public final class RadioComputer {
    private RadioComputer() {
    }

    /** A computer (or several) attached to a radio block, told about what happens there. */
    public interface Listener {
        /** What a receiver hears on its frequency changed to {@code strength}. */
        void signal(int frequency, int strength);

        /** A message on {@code frequency} reached a telegraph: {@code text}, from {@code from}. */
        void message(int frequency, String text, String from);
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<Key, Set<Listener>> LISTENERS = new ConcurrentHashMap<>();

    private static Key key(Level level, BlockPos pos) {
        return new Key(level.dimension(), pos.immutable());
    }

    /** A computer is now attached to the radio block at {@code pos}. Any thread. */
    public static void attach(Level level, BlockPos pos, Listener listener) {
        LISTENERS.computeIfAbsent(key(level, pos), k -> new CopyOnWriteArraySet<>()).add(listener);
    }

    /**
     * A computer is no longer attached. When the last one goes, a transmitter stops sending what a
     * computer set (like a computer's redstone output when it shuts down). Any thread.
     */
    public static void detach(Level level, BlockPos pos, Listener listener) {
        Key key = key(level, pos);
        Set<Listener> set = LISTENERS.get(key);
        if (set == null) {
            return;
        }
        set.remove(listener);
        if (set.isEmpty()) {
            LISTENERS.remove(key, set);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    if (!watched(serverLevel, pos) && serverLevel.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity tx) {
                        tx.setComputerSignal(0);
                    }
                });
            }
        }
    }

    /** True if a computer is attached to the radio block at {@code pos}. */
    public static boolean watched(Level level, BlockPos pos) {
        Set<Listener> set = LISTENERS.get(key(level, pos));
        return set != null && !set.isEmpty();
    }

    /** Called by a receiver when what it hears changes. */
    public static void signalChanged(Level level, BlockPos pos, int frequency, int strength) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        Set<Listener> set = LISTENERS.get(key(level, pos));
        if (set != null) {
            set.forEach(listener -> listener.signal(frequency, strength));
        }
    }

    /** Called by a telegraph when a message it can read reaches it. */
    public static void messageArrived(Level level, BlockPos pos, int frequency, SignalNetwork.Message message) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        Set<Listener> set = LISTENERS.get(key(level, pos));
        if (set != null) {
            set.forEach(listener -> listener.message(frequency, message.text(), message.name()));
        }
    }

    // ---- Every radio block ----

    /** Its frequency's name, or null. */
    public static @Nullable String frequencyName(RadioBlockEntity be) {
        return be.getLevel() == null ? null : FrequencyNames.nameOf(be.getLevel(), be.getFrequency());
    }

    /**
     * Tunes it to {@code frequency}, as its tuner would. Returns why not (out of range, or a
     * transmitter onto a private frequency its tuner isn't allowed on), or null if it's done.
     */
    public static @Nullable String setFrequency(RadioBlockEntity be, long frequency) {
        if (frequency < Frequency.MIN || frequency > Frequency.MAX) {
            return Component.translatable("computer.reikos_signal_tower.frequency_range", Frequency.MIN, Frequency.MAX).getString();
        }
        int f = (int) frequency;
        if (be instanceof RadioTransmitterBlockEntity && be.getLevel() != null) {
            Component refused = FrequencyClaims.refusal(be.getLevel(), f, be.getTunedBy(), "private_refused_block");
            if (refused != null) {
                return refused.getString();
            }
        }
        be.setFrequency(f);
        return null;
    }

    // ---- Transmitters ----

    /** The strength it's sending now: 0 while it's silenced (someone else's private frequency), struck by lightning or out of power. */
    public static int sending(RadioTransmitterBlockEntity tx) {
        String status = status(tx);
        if (!status.equals("sending")) {
            return 0;
        }
        ServerLevel level = (ServerLevel) tx.getLevel();
        return RadioTransmitterBlockEntity.inputOf(level, tx.getBlockPos(), level.getBlockState(tx.getBlockPos()));
    }

    /** "sending", "quiet" (nothing to send), "silenced", "struck" (lightning) or "no_power". */
    public static String status(RadioTransmitterBlockEntity tx) {
        if (!(tx.getLevel() instanceof ServerLevel level)) {
            return "quiet";
        }
        BlockPos pos = tx.getBlockPos();
        SignalNetwork network = SignalNetwork.get(level);
        BlockState state = level.getBlockState(pos);
        if (RadioTransmitterBlockEntity.inputOf(level, pos, state) <= 0) {
            return "quiet";
        }
        if (network.isSilenced(level, pos)) {
            return "silenced";
        }
        if (network.isKnockedOut(pos)) {
            return "struck";
        }
        return network.isDown(pos) ? "no_power" : "sending";
    }

    /** How far it reaches now, in blocks (its height, antenna and the weather where it stands). */
    public static int reach(RadioTransmitterBlockEntity tx) {
        ServerLevel level = (ServerLevel) tx.getLevel();
        return RadioTransmitterBlockEntity.reachNow(level, tx.getBlockPos(), level.getBlockState(tx.getBlockPos()));
    }

    // ---- Receivers ----

    /** The redstone it puts out (for a toggle, inverted, logic... receiver, not the same as what it hears). */
    public static int output(RadioReceiverBlockEntity rx) {
        BlockState state = rx.getBlockState();
        return state.hasProperty(RadioReceiverBlock.POWER) ? state.getValue(RadioReceiverBlock.POWER) : 0;
    }

    /** The strength heard where the block stands on {@code frequency}, as its tuner hears it (0 on someone else's encrypted frequency). */
    public static int listen(RadioBlockEntity be, long frequency) {
        if (!(be.getLevel() instanceof ServerLevel level) || frequency < Frequency.MIN || frequency > Frequency.MAX) {
            return 0;
        }
        int f = (int) frequency;
        if (!FrequencyClaims.mayHear(level, f, be.getTunedBy())) {
            return 0;
        }
        return SignalNetwork.get(level).listen(level, f, be.getBlockPos(), be.getTunedBy()).power();
    }

    /** Everything it could hear where it stands (as a Signal Scanner would): frequency, strength and name, strongest first. */
    public static List<Map<String, Object>> scan(RadioBlockEntity be) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return list;
        }
        for (SignalNetwork.HeardFrequency heard : SignalNetwork.get(level).scanAt(level, be.getBlockPos(), be.getTunedBy())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("frequency", heard.frequency());
            row.put("strength", heard.power());
            String name = FrequencyNames.nameOf(level, heard.frequency());
            if (name != null) {
                row.put("name", name);
            }
            list.add(row);
        }
        return list;
    }

    // ---- Telegraphs ----

    /** Sends {@code text} as "Computer {@code id}". Returns why not (empty, too soon after the last, private), or null if it went out. */
    public static @Nullable String send(TelegraphBlockEntity telegraph, int computerId, String text) {
        if (!(telegraph.getLevel() instanceof ServerLevel level)) {
            return "not loaded";
        }
        String name = Component.translatable("computer.reikos_signal_tower.sender", computerId).getString();
        Telegraph.Result result = Telegraph.sendAs(level, telegraph.getBlockPos(), telegraph.getTunedBy(), name, text);
        if (result == null) {
            return "not loaded";
        }
        return result.sent() ? null : result.message().getString();
    }

    /**
     * The messages that reached it, oldest first (up to 16): text, from, and how many seconds ago.
     * On someone else's encrypted frequency the text is left out.
     */
    public static List<Map<String, Object>> messages(TelegraphBlockEntity telegraph) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(telegraph.getLevel() instanceof ServerLevel level)) {
            return list;
        }
        int frequency = telegraph.getFrequency();
        boolean deaf = !FrequencyClaims.mayHear(level, frequency, telegraph.getTunedBy());
        for (SignalNetwork.Message message : SignalNetwork.get(level).messagesAt(frequency, telegraph.getBlockPos())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("text", deaf ? "" : message.text());
            row.put("from", message.name());
            row.put("secondsAgo", Math.max(0, (level.getGameTime() - message.gameTime()) / 20));
            if (deaf) {
                row.put("encrypted", true);
            }
            list.add(row);
        }
        return list;
    }
}
