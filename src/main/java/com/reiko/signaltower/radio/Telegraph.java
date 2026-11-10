package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TelegraphBlockEntity;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteControlItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Radio Telegraph (v0.51.0): a desk where you type a short message onto a frequency. It goes
 * out like a remote press from where the telegraph stands (its reach by height, relayed by every
 * tower within it, carried on by repeaters on that frequency), and is read by:
 * <ul>
 *   <li>pagers on the frequency in the pockets of players inside it (in chat, with a beep);</li>
 *   <li>other telegraphs on the frequency inside it (in their screen; loaded ones also click and
 *       give a 1-second redstone pulse);</li>
 *   <li>Status Boards showing the frequency (for 5 minutes, under the row).</li>
 * </ul>
 * Each dimension remembers the last {@link SignalNetwork#MAX_MESSAGES} per frequency, so a telegraph
 * shows what reached it even while it was unloaded, and a private frequency's log lists them. Only
 * the owner and members send on a private frequency; on an encrypted one only members' telegraphs
 * and pagers can read the text. Messages don't turn the frequency on (no redstone strength).
 */
public final class Telegraph {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    /** The longest message, in characters. */
    public static final int MAX_LENGTH = 64;
    /** Ticks between messages from one telegraph. */
    public static final int COOLDOWN_TICKS = 40;
    /** How many messages a telegraph's screen shows. */
    public static final int FEED_LINES = 6;
    /** How long a Status Board shows the last message under a row. */
    public static final long BOARD_TICKS = 6000;

    private Telegraph() {
    }

    /** What sending did: the message for the sender, whether it went out, and who could read it. */
    public record Result(Component message, boolean sent, int pagers, int telegraphs) {
    }

    /** One line of a telegraph's screen: how long ago, when (day time), who sent it, and the text ("" if it can't be read). */
    public record Line(long secondsAgo, long dayTime, String who, String text, boolean encrypted) {
    }

    /** The text as it's sent: characters chat doesn't allow dropped, spaces trimmed, at most {@link #MAX_LENGTH}. */
    public static String clean(String text) {
        String filtered = StringUtil.filterText(text == null ? "" : text).strip();
        return filtered.length() > MAX_LENGTH ? filtered.substring(0, MAX_LENGTH).strip() : filtered;
    }

    /** Where a message from a telegraph at {@code pos} on {@code frequency} can be read. The first area is its own. */
    public static List<SignalNetwork.Coverage> coverage(ServerLevel level, BlockPos pos, int frequency) {
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos), Config.TOWERS_RELAY_REMOTES.get());
        return network.withRepeaters(level, frequency, own);
    }

    /** "56 blocks + 2 towers": how far a telegraph at {@code pos} reaches now. */
    public static Component reachText(ServerLevel level, BlockPos pos, int frequency) {
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos), Config.TOWERS_RELAY_REMOTES.get());
        int repeaters = network.withRepeaters(level, frequency, own).size() - own.size();
        return RemoteControlItem.describeReach(own.get(0).reach(), own.size() - 1, repeaters);
    }

    /**
     * Sends {@code raw} from the telegraph at {@code pos} for {@code sender}, on its frequency.
     * Returns what happened (also played as a click), or null if there's no telegraph there.
     */
    public static @Nullable Result send(ServerLevel level, BlockPos pos, Player sender, String raw) {
        return send(level, pos, sender.getUUID(), sender.getGameProfile().getName(), sender, raw);
    }

    /**
     * v0.58.0: sends {@code raw} from the telegraph at {@code pos} for a computer: {@code who} (the
     * telegraph's tuner) decides whether it may send on a private frequency, {@code name} ("Computer 3")
     * is shown as the sender. Null if there's no telegraph there.
     */
    public static @Nullable Result sendAs(ServerLevel level, BlockPos pos, @Nullable UUID who, String name, String raw) {
        return send(level, pos, who, name, null, raw);
    }

    private static @Nullable Result send(ServerLevel level, BlockPos pos, @Nullable UUID who, String name, @Nullable Player sender, String raw) {
        if (!(level.getBlockEntity(pos) instanceof TelegraphBlockEntity telegraph)) {
            return null;
        }
        int frequency = telegraph.getFrequency();
        String text = clean(raw);
        if (text.isEmpty()) {
            return new Result(Component.translatable(KEY + "telegraph_empty").withStyle(ChatFormatting.GRAY), false, 0, 0);
        }
        long now = level.getGameTime();
        if (telegraph.isKeying()) {
            return new Result(Component.translatable(KEY + "telegraph_keying").withStyle(ChatFormatting.GOLD), false, 0, 0); // v0.62.0
        }
        if (telegraph.coolingDown(now)) {
            return new Result(Component.translatable(KEY + "telegraph_too_fast").withStyle(ChatFormatting.GOLD), false, 0, 0);
        }
        Component refused = FrequencyClaims.refusal(level, frequency, who, "private_refused_telegraph");
        if (refused != null) {
            if (sender != null) {
                IntruderAlerts.report(level.getServer(), frequency, sender, IntruderAlerts.Kind.TELEGRAPH, level.getBlockState(pos).getBlock());
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6F, 0.7F);
            return new Result(refused, false, 0, 0);
        }
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos), Config.TOWERS_RELAY_REMOTES.get());
        List<SignalNetwork.Coverage> coverage = network.withRepeaters(level, frequency, own);
        if (telegraph.isMorse()) {
            // v0.62.0: keyed out in Morse first; the text goes out when it's done.
            String keyed = Morse.sendable(text);
            if (keyed.isEmpty()) {
                return new Result(Component.translatable(KEY + "telegraph_morse_nothing").withStyle(ChatFormatting.GOLD), false, 0, 0);
            }
            int ticks = telegraph.startKeying(coverage, who, name, keyed);
            level.playSound(null, pos, com.reiko.signaltower.registry.ModSounds.TELEGRAPH_KEY.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
            Component reach = RemoteControlItem.describeReach(own.get(0).reach(), own.size() - 1, coverage.size() - own.size());
            return new Result(Component.translatable(KEY + "telegraph_keying_started", FrequencyNames.label(level, frequency), reach,
                    (ticks + 19) / 20).withStyle(ChatFormatting.GREEN), true, 0, 0);
        }
        SignalNetwork.Message message = network.recordMessage(frequency, coverage, pos, who, name, text);
        telegraph.markSent(now);
        level.playSound(null, pos, com.reiko.signaltower.registry.ModSounds.TELEGRAPH_KEY.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        int[] read = deliver(level, pos, frequency, coverage, message);
        int pagers = read[0];
        int telegraphs = read[1];
        Component reach = RemoteControlItem.describeReach(own.get(0).reach(), own.size() - 1, coverage.size() - own.size());
        Component sent = Component.translatable(KEY + "telegraph_sent", FrequencyNames.label(level, frequency), reach)
                .withStyle(ChatFormatting.GREEN);
        return new Result(Component.translatable(KEY + "remote_result", sent, readBy(pagers, telegraphs)), true, pagers, telegraphs);
    }

    /** Hands {@code message} to the pagers and (other) telegraphs on {@code frequency} in {@code coverage}: how many of each read it. */
    private static int[] deliver(ServerLevel level, BlockPos pos, int frequency, List<SignalNetwork.Coverage> coverage,
                                 SignalNetwork.Message message) {
        int pagers = 0;
        for (ServerPlayer player : level.players()) {
            pagers += PagerItem.deliverMessage(level, player, frequency, message);
        }
        int telegraphs = 0;
        for (SignalNetwork.TrackedReceiver other : SignalNetwork.get(level).telegraphsOn(frequency)) {
            if (other.pos().equals(pos) || !SignalNetwork.covers(coverage, other.pos()) || !level.isLoaded(other.pos())) {
                continue;
            }
            if (level.getBlockEntity(other.pos()) instanceof TelegraphBlockEntity there && there.getFrequency() == frequency
                    && there.receive(level, message)) {
                telegraphs++;
            }
        }
        com.reiko.signaltower.compat.MobileRadio.deliver(level, frequency, coverage, message); // v0.59.0: radio turtles and pocket computers
        return new int[]{pagers, telegraphs};
    }

    /**
     * v0.53.0: sends {@code text} as a message from something that isn't a player (a Radio Weather
     * Station at {@code pos}, named {@code name}), over {@code coverage}: remembered for telegraphs'
     * feeds and Status Boards, and handed to pagers and telegraphs in it, as {@link #send} does.
     */
    public static SignalNetwork.Message broadcast(ServerLevel level, BlockPos pos, int frequency, List<SignalNetwork.Coverage> coverage,
                                                  String name, String text) {
        SignalNetwork.Message message = SignalNetwork.get(level).recordMessage(frequency, coverage, pos, null, name, text);
        deliver(level, pos, frequency, coverage, message);
        return message;
    }

    /**
     * v0.62.0: a message that came in Morse (from a Morse Key, or a telegraph keying it) from
     * {@code pos}: kept and handed out over {@code coverage} like a telegraph's, sent by {@code who}
     * ({@code name}). Returns {pagers, telegraphs} that read it.
     */
    public static int[] morseMessage(ServerLevel level, BlockPos pos, int frequency, List<SignalNetwork.Coverage> coverage,
                                     @Nullable UUID who, String name, String text) {
        SignalNetwork.Message message = SignalNetwork.get(level).recordMessage(frequency, coverage, pos, who, name, clean(text));
        return deliver(level, pos, frequency, coverage, message);
    }

    /** "Read by 2 pagers and 1 telegraph", or that nothing was in range. */
    public static Component readBy(int pagers, int telegraphs) {
        if (pagers + telegraphs == 0) {
            return Component.translatable(KEY + "telegraph_read_none").withStyle(ChatFormatting.RED);
        }
        Component pagerText = Component.translatable(KEY + (pagers == 1 ? "telegraph_one_pager" : "telegraph_pagers"), pagers);
        Component telegraphText = Component.translatable(KEY + (telegraphs == 1 ? "telegraph_one_telegraph" : "telegraph_telegraphs"), telegraphs);
        Component both = pagers == 0 ? telegraphText : telegraphs == 0 ? pagerText
                : Component.translatable(KEY + "telegraph_and", pagerText, telegraphText);
        return Component.translatable(KEY + "telegraph_read", both).withStyle(ChatFormatting.GREEN);
    }

    /**
     * The newest {@link #FEED_LINES} messages on {@code frequency} that could be read at {@code pos},
     * oldest first, as seen by {@code listener} (the telegraph's tuner): on someone else's encrypted
     * frequency the text is left out.
     */
    public static List<Line> feed(ServerLevel level, BlockPos pos, int frequency, @Nullable UUID listener) {
        boolean deaf = !FrequencyClaims.mayHear(level, frequency, listener);
        List<SignalNetwork.Message> heard = SignalNetwork.get(level).messagesAt(frequency, pos);
        List<Line> lines = new ArrayList<>();
        for (int i = Math.max(0, heard.size() - FEED_LINES); i < heard.size(); i++) {
            SignalNetwork.Message message = heard.get(i);
            lines.add(new Line(Math.max(0, (level.getGameTime() - message.gameTime()) / 20), message.dayTime(),
                    message.name(), deaf ? "" : message.text(), deaf));
        }
        return lines;
    }

    /** "just now", "40 seconds ago", "3 minutes ago", "2 hours ago", "5 days ago" (the log's words). */
    public static Component ago(long seconds) {
        String key = "screen." + SignalTower.MOD_ID + ".log.";
        if (seconds < 5) {
            return Component.translatable(key + "ago_now");
        }
        if (seconds < 120) {
            return Component.translatable(key + "ago_seconds", seconds);
        }
        long minutes = seconds / 60;
        if (minutes < 120) {
            return Component.translatable(key + "ago_minutes", minutes);
        }
        long hours = minutes / 60;
        return hours < 48 ? Component.translatable(key + "ago_hours", hours) : Component.translatable(key + "ago_days", hours / 24);
    }
}
