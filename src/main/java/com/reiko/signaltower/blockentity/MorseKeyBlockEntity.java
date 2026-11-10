package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.MorseKeyBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.IntruderAlerts;
import com.reiko.signaltower.radio.Morse;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.Telegraph;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Morse Key's block entity (v0.62.0). The key is down while a player holds right-click on it
 * (a click holds it {@link #RELEASE_TICKS}; the game repeats a held click every 4 ticks, and a
 * repeat that comes late, when the game lags, joins up with the press if the key was up no more
 * than {@link #MERGE_TICKS}) or while it has redstone. While down it holds its frequency on, like a remote pulse, over where a
 * telegraph there would reach (fixed at the first press of a message). It times each press and
 * gap; after {@link #END_TICKS} with the key up (or {@link Telegraph#MAX_LENGTH} characters) what
 * was keyed is decoded ({@link Morse#decode}) and goes out as a message, as a telegraph's does.
 * Nothing here is saved: a message being keyed is lost if the chunk unloads.
 */
public class MorseKeyBlockEntity extends RadioBlockEntity {
    /** How long one click holds the key down (it's down from the tick after: 6 ticks). */
    public static final int RELEASE_TICKS = 7;
    /** A hand press that comes this soon after the key went up carries on the press before it (a held click repeating late). */
    public static final int MERGE_TICKS = 1;
    /** How long the key has to be up for what was keyed to go out. */
    public static final int END_TICKS = 60;
    /** The most presses one message keeps. */
    public static final int MAX_PRESSES = 512;

    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private long heldUntil = -1;
    private boolean powered;
    private boolean down;
    private long downAt;
    private long upAt;
    private final List<Integer> presses = new ArrayList<>();
    private final List<Integer> gaps = new ArrayList<>();
    /** Whether the press going on (or last) is a player's, and whether any in this message was. */
    private boolean handPress;
    private boolean byHand;
    private @Nullable List<SignalNetwork.Coverage> coverage;
    private @Nullable UUID keyer;
    private String keyerName = "";
    private String lastSent = "";

    public MorseKeyBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MORSE_KEY_BE.get(), pos, state);
    }

    /**
     * {@code player} clicks it (or holds the click): the key goes down, or stays down, unless they
     * may not send on its frequency (someone else's private one), when the refusal is returned.
     */
    public @Nullable Component press(ServerLevel level, Player player) {
        Component refused = FrequencyClaims.refusal(level, getFrequency(), player.getUUID(), "private_refused_morse");
        if (refused != null) {
            if (!down && heldUntil < level.getGameTime()) {
                IntruderAlerts.report(level.getServer(), getFrequency(), player, IntruderAlerts.Kind.TELEGRAPH, getBlockState().getBlock());
            }
            return refused;
        }
        heldUntil = level.getGameTime() + RELEASE_TICKS;
        keyer = player.getUUID();
        keyerName = player.getGameProfile().getName();
        return null;
    }

    /** The redstone into it changed: on holds the key down (keyed as whoever tuned it). */
    public void redstone(boolean now) {
        if (now && !powered) {
            keyer = getTunedBy();
            keyerName = "";
        }
        powered = now;
    }

    public boolean isDown() {
        return down;
    }

    /** What was last sent from it ("" if nothing yet). */
    public String lastSent() {
        return lastSent;
    }

    /** What's been keyed so far in the message going on (decoded letters), and the dots and dashes of the letter being keyed. */
    public String keyedText(long now) {
        List<Integer> g = openGaps(now);
        return Morse.decode(presses, g, !down && !presses.isEmpty() && now - upAt >= 2 * Morse.dotLength(presses, gaps, byHand), byHand);
    }

    /** The dots and dashes of the letter still being keyed ("" between letters). */
    public String pendingCode(long now) {
        if (presses.isEmpty() || !down && now - upAt >= 2 * Morse.dotLength(presses, gaps, byHand)) {
            return "";
        }
        return Morse.pendingCode(presses, gaps, byHand);
    }

    private List<Integer> openGaps(long now) {
        if (down || presses.isEmpty()) {
            return gaps;
        }
        List<Integer> g = new ArrayList<>(gaps);
        g.add((int) Math.min(Integer.MAX_VALUE, now - upAt));
        return g;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MorseKeyBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel, level.getGameTime());
        }
    }

    private void tick(ServerLevel level, long now) {
        boolean shouldBeDown = now < heldUntil || powered;
        if (shouldBeDown && !down && !mayKey(level)) {
            shouldBeDown = false; // redstone on someone else's private frequency: nothing goes out
        }
        if (shouldBeDown && !down) {
            keyDown(level, now);
        } else if (!shouldBeDown && down) {
            keyUp(level, now);
        }
        SignalNetwork network = SignalNetwork.get(level);
        if (down && coverage != null) {
            network.keyMorse(getFrequency(), coverage, now, SignalNetwork.Cause.morse(worldPosition, keyer, keyerName));
        }
        if (!down && !presses.isEmpty() && (now - upAt >= END_TICKS || presses.size() >= MAX_PRESSES
                || Morse.decode(presses, gaps, true, byHand).length() >= Telegraph.MAX_LENGTH)) {
            finish(level);
        }
    }

    /** Redstone keying is as whoever tuned it; a player's press was checked already. */
    private boolean mayKey(ServerLevel level) {
        return !powered || FrequencyClaims.maySend(level, getFrequency(), getTunedBy());
    }

    private void keyDown(ServerLevel level, long now) {
        boolean hand = now < heldUntil && !powered;
        if (hand && handPress && !presses.isEmpty() && now - upAt <= MERGE_TICKS) {
            // A held click repeating late: the same press carries on.
            downAt = upAt - presses.remove(presses.size() - 1);
            down = true;
            setState(level, true);
            return;
        }
        handPress = hand;
        byHand |= hand;
        if (presses.isEmpty()) {
            coverage = Telegraph.coverage(level, worldPosition, getFrequency());
        } else {
            gaps.add((int) Math.min(Integer.MAX_VALUE, now - upAt));
        }
        down = true;
        downAt = now;
        setState(level, true);
        level.playSound(null, worldPosition, com.reiko.signaltower.registry.ModSounds.TELEGRAPH_KEY.get(), SoundSource.BLOCKS, 0.35F, 1.3F);
    }

    private void keyUp(ServerLevel level, long now) {
        presses.add((int) Math.max(1, Math.min(Integer.MAX_VALUE, now - downAt)));
        down = false;
        upAt = now;
        SignalNetwork.get(level).releaseMorse(getFrequency(), now);
        setState(level, false);
    }

    /** What was keyed goes out as a message (if it spells anything). */
    private void finish(ServerLevel level) {
        String text = Telegraph.clean(Morse.decode(presses, gaps, true, byHand));
        SignalTower.LOGGER.debug("Morse Key at {} on {}: presses {}, gaps {}{}: \"{}\"", worldPosition, getFrequency(), presses, gaps,
                byHand ? " (by hand)" : "", text);
        presses.clear();
        gaps.clear();
        byHand = false;
        List<SignalNetwork.Coverage> areas = coverage != null ? coverage : Telegraph.coverage(level, worldPosition, getFrequency());
        coverage = null;
        if (text.isEmpty()) {
            return;
        }
        lastSent = text;
        String name = keyerName.isEmpty() ? Component.translatable("block." + SignalTower.MOD_ID + ".morse_key").getString() : keyerName;
        int[] read = Telegraph.morseMessage(level, worldPosition, getFrequency(), areas, keyer, name, text);
        Component sent = Component.translatable(KEY + "morse_sent", text, Telegraph.readBy(read[0], read[1])).withStyle(ChatFormatting.GREEN);
        for (var player : level.players()) {
            if (keyer != null && player.getUUID().equals(keyer) && player.distanceToSqr(worldPosition.getCenter()) < 64 * 64) {
                player.displayClientMessage(sent, true);
            }
        }
        setChanged();
    }

    private void setState(ServerLevel level, boolean keyDown) {
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof MorseKeyBlock && state.getValue(MorseKeyBlock.DOWN) != keyDown) {
            level.setBlock(worldPosition, state.setValue(MorseKeyBlock.DOWN, keyDown), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (down && level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).releaseMorse(getFrequency(), serverLevel.getGameTime());
        }
    }

    @Override
    protected void onFrequencyChanged() {
        // A message half keyed on the old frequency is dropped.
        presses.clear();
        gaps.clear();
        byHand = false;
        coverage = null;
    }
}
