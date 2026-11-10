package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.item.WalkieTalkieItem;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Walkie-Talkie (v0.64.0): a two-way handheld radio. A line said into one held in hand goes out
 * like a Remote Control press from where the player stands (their reach by height, every tower in
 * it passing it on, repeaters on the frequency) and becomes a message on the frequency, so pagers,
 * telegraphs, Status Boards, Ticker Tape Printers and the frequency log get it like a telegraph's
 * (the log never shows where the player stood). Everyone carrying a Walkie-Talkie on the frequency
 * where it reaches hears it in chat, and so they hear telegraph messages and Morse on it too. Far
 * out, near the edge of the area that carries it to them, it comes through broken up
 * ({@link #garble}). Only the owner and members talk on a private frequency; on an encrypted one,
 * others hear that someone spoke, but "(encrypted)".
 */
public final class WalkieTalkie {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    /** The longest line, in characters (a message's). */
    public static final int MAX_LENGTH = Telegraph.MAX_LENGTH;
    /** Ticks between lines from one player. */
    public static final int COOLDOWN_TICKS = 20;
    /** Up to this fraction of the way to the edge it's clear; past it, more and more is lost. */
    public static final double CLEAR_FRACTION = 0.7;
    /** At the very edge, the chance that a word is cut off. */
    public static final double EDGE_LOSS = 0.6;

    private static final Map<UUID, Long> LAST_SAID = new HashMap<>();

    private WalkieTalkie() {
    }

    /** What talking did: the action-bar line for the speaker, whether it went out, and who heard it. */
    public record Result(Component message, boolean sent, int walkies, int pagers, int telegraphs) {
    }

    /** The Walkie-Talkie in {@code player}'s hand ({@code offhand} first if asked, else either), or null. */
    public static @Nullable ItemStack held(Player player, boolean offhand) {
        ItemStack stack = player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof WalkieTalkieItem) {
            return stack;
        }
        ItemStack other = player.getItemInHand(offhand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        return other.getItem() instanceof WalkieTalkieItem ? other : null;
    }

    /** {@code player} says {@code raw} into the Walkie-Talkie in their hand. Null if they don't hold one. */
    public static @Nullable Result talk(Player player, boolean offhand, String raw) {
        ItemStack walkie = held(player, offhand);
        if (walkie == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        int frequency = FrequencyTunerItem.getFrequency(walkie);
        String text = Telegraph.clean(raw);
        if (text.isEmpty()) {
            return new Result(Component.translatable(KEY + "walkie_empty").withStyle(ChatFormatting.GRAY), false, 0, 0, 0);
        }
        long now = level.getGameTime();
        Long last = LAST_SAID.get(player.getUUID());
        if (last != null && now >= last && now - last < COOLDOWN_TICKS) {
            return new Result(Component.translatable(KEY + "walkie_too_fast").withStyle(ChatFormatting.GOLD), false, 0, 0, 0);
        }
        Component refused = FrequencyClaims.refusal(level, frequency, player.getUUID(), "private_refused_walkie");
        if (refused != null) {
            IntruderAlerts.report(level.getServer(), frequency, player, IntruderAlerts.Kind.WALKIE, null);
            return new Result(refused, false, 0, 0, 0);
        }
        LAST_SAID.put(player.getUUID(), now);
        BlockPos pos = player.blockPosition();
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos),
                com.reiko.signaltower.Config.TOWERS_RELAY_REMOTES.get());
        List<SignalNetwork.Coverage> coverage = network.withRepeaters(level, frequency, own);
        String name = player.getGameProfile().getName();
        SignalNetwork.Message message = network.recordMessage(frequency, coverage, pos, player.getUUID(), name, text, true);
        int[] read = Telegraph.deliverMessage(level, pos, frequency, coverage, message);
        // The speaker's own line in chat (clear: it's what they said), and a squelch nearby.
        player.displayClientMessage(line(level, frequency, 4, name, text, true), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WALKIE_SQUELCH.get(), SoundSource.PLAYERS, 0.4F, 1.15F);
        Component reach = RemoteControlItem.describeReach(own.get(0).reach(), own.size() - 1, coverage.size() - own.size());
        Component sent = Component.translatable(KEY + "walkie_sent", FrequencyNames.label(level, frequency), reach).withStyle(ChatFormatting.GREEN);
        return new Result(Component.translatable(KEY + "remote_result", sent, heardBy(read[2], read[0], read[1])), true, read[2], read[0], read[1]);
    }

    /** "Heard by 2 walkie-talkies, 1 pager and 1 telegraph", or that nobody heard it. */
    public static Component heardBy(int walkies, int pagers, int telegraphs) {
        List<Component> parts = new ArrayList<>();
        if (walkies > 0) parts.add(Component.translatable(KEY + (walkies == 1 ? "walkie_one" : "walkie_many"), walkies));
        if (pagers > 0) parts.add(Component.translatable(KEY + (pagers == 1 ? "telegraph_one_pager" : "telegraph_pagers"), pagers));
        if (telegraphs > 0) parts.add(Component.translatable(KEY + (telegraphs == 1 ? "telegraph_one_telegraph" : "telegraph_telegraphs"), telegraphs));
        if (parts.isEmpty()) {
            return Component.translatable(KEY + "walkie_heard_none").withStyle(ChatFormatting.RED);
        }
        MutableComponent list = parts.get(0).copy();
        for (int i = 1; i < parts.size(); i++) {
            list = Component.translatable(KEY + (i == parts.size() - 1 ? "telegraph_and" : "walkie_comma"), list, parts.get(i));
        }
        return Component.translatable(KEY + "walkie_heard", list).withStyle(ChatFormatting.GREEN);
    }

    /**
     * A message on {@code frequency} reached everyone carrying a Walkie-Talkie on it where it can be
     * heard (but not whoever said it): a line in chat, broken up the nearer they are to the edge,
     * and a squelch from their radio (unless all of theirs on it are silent). Returns how many
     * walkie-talkies heard it.
     */
    public static int deliver(ServerLevel level, int frequency, SignalNetwork.Message message) {
        int heard = 0;
        for (ServerPlayer player : level.players()) {
            heard += deliverTo(level, player, frequency, message);
        }
        return heard;
    }

    /** {@link #deliver} for one player: how many of their Walkie-Talkies heard it (0 if none, or out of reach, or they said it). */
    public static int deliverTo(ServerLevel level, Player player, int frequency, SignalNetwork.Message message) {
        if (player.getUUID().equals(message.who()) && message.handheld()) {
            return 0;
        }
        int count = 0;
        boolean loud = false;
        for (ItemStack stack : walkiesOf(player)) {
            if (FrequencyTunerItem.getFrequency(stack) == frequency) {
                count++;
                loud |= !PagerItem.isSilent(stack);
            }
        }
        if (count == 0) {
            return 0;
        }
        double fraction = edgeFraction(message.coverage(), player.blockPosition());
        if (fraction > 1.0) {
            return 0;
        }
        String text = !FrequencyClaims.mayHear(level, frequency, player.getUUID())
                ? Component.translatable(KEY + "printer_encrypted").getString()
                : garble(message.text(), fraction, message.gameTime() * 31 + player.getUUID().hashCode());
        player.displayClientMessage(line(level, frequency, bars(fraction), message.name(), text, false), false);
        if (loud) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WALKIE_SQUELCH.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
        }
        return count;
    }

    /** "[2026] ▮▮▮▯ Dev: Meet at the gate" (the bars gray; "(you)" after the name for your own line). */
    public static Component line(ServerLevel level, int frequency, int bars, String name, String text, boolean own) {
        String signal = "▮".repeat(bars) + "▯".repeat(Math.max(0, 4 - bars));
        Component who = own ? Component.translatable(KEY + "walkie_you", name) : Component.literal(name);
        return Component.translatable(KEY + "walkie_line", FrequencyNames.label(level, frequency),
                Component.literal(signal).withStyle(bars >= 3 ? ChatFormatting.GREEN : bars == 2 ? ChatFormatting.GOLD : ChatFormatting.RED),
                Component.empty().append(who).withStyle(ChatFormatting.YELLOW), Component.literal(text).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * How far out {@code pos} is in the best area of {@code coverage} that reaches it: 0 at its
     * middle, 1 at its edge, more than 1 if none reaches it.
     */
    public static double edgeFraction(List<SignalNetwork.Coverage> coverage, BlockPos pos) {
        double best = Double.MAX_VALUE;
        for (SignalNetwork.Coverage area : coverage) {
            if (!area.reaches(pos)) {
                continue;
            }
            double dx = pos.getX() - area.origin().getX();
            double dz = pos.getZ() - area.origin().getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            int reach = distance <= area.reach() ? area.reach() : area.far(); // past its reach it's in a dish's beam
            best = Math.min(best, reach <= 0 ? 0 : distance / reach);
        }
        return best;
    }

    /** Signal bars, 1 to 4, for how far out the listener is. */
    public static int bars(double fraction) {
        return fraction <= 0.5 ? 4 : fraction <= CLEAR_FRACTION ? 3 : fraction <= 0.85 ? 2 : 1;
    }

    /**
     * {@code text} as heard {@code fraction} of the way to the edge: clear up to
     * {@link #CLEAR_FRACTION}; past that, each word is cut off ("g...") with a chance growing to
     * {@link #EDGE_LOSS} at the edge, and words cut in a row run together. The same {@code seed}
     * breaks it up the same way.
     */
    public static String garble(String text, double fraction, long seed) {
        if (fraction <= CLEAR_FRACTION) {
            return text;
        }
        double loss = Math.min(1.0, (fraction - CLEAR_FRACTION) / (1.0 - CLEAR_FRACTION)) * EDGE_LOSS;
        Random random = new Random(seed);
        StringBuilder out = new StringBuilder();
        boolean lastCut = false;
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (random.nextDouble() < loss) {
                if (!lastCut) {
                    int keep = word.length() > 1 ? random.nextInt(word.length()) : 0;
                    if (!out.isEmpty()) out.append(' ');
                    out.append(word, 0, keep).append("...");
                }
                lastCut = true;
            } else {
                if (!out.isEmpty()) out.append(' ');
                out.append(word);
                lastCut = false;
            }
        }
        return out.toString();
    }

    /** Every Walkie-Talkie in {@code player}'s inventory (hands included). */
    public static List<ItemStack> walkiesOf(Player player) {
        List<ItemStack> out = new ArrayList<>();
        Container inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof WalkieTalkieItem) {
                out.add(stack);
            }
        }
        return out;
    }

    /** For the GameTests: forget when everyone last spoke. */
    public static void resetForTests() {
        LAST_SAID.clear();
    }
}
