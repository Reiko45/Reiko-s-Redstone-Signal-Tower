package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CodeKeypadBlock;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.IntruderAlerts;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Code Keypad (v0.37.0): typing its code sends a pulse on its frequency from where it is (like a
 * remote pressed there, relayed by towers in its reach), and powers the block it's on for the same
 * time, like a button.
 * <p>
 * Whoever places it owns it: only they (or an operator) set the code and pulse length, and re-tune
 * it. The code stays on the server; it's never sent to a client, and isn't kept on the item when
 * the keypad is broken. Three wrong codes in a row lock a player out for 30 seconds and tell the
 * owner (an intruder alert, now or at their next login).
 * <p>
 * Like a transmitter, it only sends on someone else's private frequency if its tuner (normally its
 * owner) is a member; otherwise the right code does nothing.
 */
public class CodeKeypadBlockEntity extends RadioBlockEntity {
    public static final int MIN_CODE = 4;
    public static final int MAX_CODE = 8;
    /** Wrong codes in a row before a player is locked out. */
    public static final int MAX_MISSES = 3;
    /** How long a player is locked out: 30 seconds. */
    public static final int LOCK_TICKS = 600;

    private static final String KEY = "message." + SignalTower.MOD_ID + ".keypad_";

    /** What happened when someone typed a code, or the owner changed a setting. Sent by ordinal: only add at the end. */
    public enum Outcome {
        ACCEPTED, SILENT, WRONG, LOCKED, NO_CODE, SAVED, REFUSED
    }

    public record Result(Outcome outcome, Component message, int lockedSeconds) {
    }

    private @Nullable UUID owner;
    private String ownerName = "";
    private String code = "";
    private int pulseTicks = PulseLength.DEFAULT_TICKS;
    /** Wrong codes in a row, per player (not saved). */
    private final Map<UUID, Integer> misses = new HashMap<>();
    /** Until when (game time) each locked-out player is locked out (not saved). */
    private final Map<UUID, Long> lockedUntil = new HashMap<>();

    public CodeKeypadBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CODE_KEYPAD_BE.get(), pos, state);
    }

    @Override
    protected void onFrequencyChanged() {
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(@Nullable UUID player, String name) {
        owner = player;
        ownerName = name == null ? "" : name;
        setChanged();
    }

    public boolean hasCode() {
        return !code.isEmpty();
    }

    public int getPulseTicks() {
        return pulseTicks;
    }

    /** The owner, an operator, or anyone if nobody owns it (set by a command). */
    public boolean mayConfigure(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** True if {@code code} is 4 to 8 digits. */
    public static boolean validCode(String code) {
        return code != null && code.length() >= MIN_CODE && code.length() <= MAX_CODE
                && code.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** Seconds {@code player} is still locked out for at {@code gameTime} (0 if they aren't). */
    public int lockedSeconds(UUID player, long gameTime) {
        Long until = lockedUntil.get(player);
        if (until == null || until <= gameTime) {
            return 0;
        }
        return (int) ((until - gameTime + 19) / 20);
    }

    /**
     * The owner sets a new code ({@code newCode}; empty keeps the old one) and the pulse length.
     * Refused for anyone else, and for a code that isn't 4 to 8 digits.
     */
    public Result configure(Player player, String newCode, int newPulseTicks) {
        if (!mayConfigure(player)) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "not_yours", ownerLabel()).withStyle(ChatFormatting.RED), 0);
        }
        if (!newCode.isEmpty() && !validCode(newCode)) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "bad_code", MIN_CODE, MAX_CODE).withStyle(ChatFormatting.RED), 0);
        }
        if (owner == null) {
            setOwner(player.getUUID(), player.getGameProfile().getName());
        }
        int ticks = PulseLength.clamp(newPulseTicks);
        boolean pulseChanged = ticks != pulseTicks;
        if (!newCode.isEmpty()) {
            code = newCode;
            misses.clear();
            lockedUntil.clear();
        }
        pulseTicks = ticks;
        setChanged();
        // Never repeat the code itself, only how long it is.
        Component message = !newCode.isEmpty()
                ? Component.translatable(KEY + "code_saved", newCode.length(), PulseLength.format(ticks))
                : pulseChanged ? Component.translatable(KEY + "pulse_saved", PulseLength.format(ticks))
                : Component.translatable(KEY + "nothing_changed");
        return new Result(Outcome.SAVED, message.copy().withStyle(ChatFormatting.GREEN), 0);
    }

    /**
     * {@code player} typed {@code digits} at game time {@code now}. The right code sends the pulse
     * (unless the keypad is kept quiet on someone else's private frequency); a wrong one counts
     * towards the lockout.
     */
    public Result enter(ServerLevel level, Player player, String digits, long now) {
        UUID who = player.getUUID();
        int locked = lockedSeconds(who, now);
        if (locked > 0) {
            buzz(level, 0.4F);
            return new Result(Outcome.LOCKED, Component.translatable(KEY + "locked", locked).withStyle(ChatFormatting.RED), locked);
        }
        lockedUntil.remove(who);
        if (!hasCode()) {
            buzz(level, 0.8F);
            return new Result(Outcome.NO_CODE, Component.translatable(KEY + "no_code", ownerLabel()).withStyle(ChatFormatting.GRAY), 0);
        }
        if (!code.equals(digits)) {
            int count = misses.merge(who, 1, Integer::sum);
            buzz(level, 0.5F);
            if (count >= MAX_MISSES) {
                misses.remove(who);
                lockedUntil.put(who, now + LOCK_TICKS);
                if (owner != null && !owner.equals(who)) {
                    IntruderAlerts.reportKeypad(level.getServer(), owner, getFrequency(), player, getBlockState().getBlock(), getBlockPos());
                }
                return new Result(Outcome.LOCKED, Component.translatable(KEY + "wrong_locked", LOCK_TICKS / 20).withStyle(ChatFormatting.RED),
                        LOCK_TICKS / 20);
            }
            return new Result(Outcome.WRONG, Component.translatable(KEY + "wrong", count, MAX_MISSES).withStyle(ChatFormatting.RED), 0);
        }
        misses.remove(who);
        int frequency = getFrequency();
        // Like a transmitter: on someone else's private frequency, only a member's keypad sends.
        if (!FrequencyClaims.maySend(level, frequency, getTunedBy())) {
            buzz(level, 0.6F);
            Component silent = FrequencyClaims.refusal(level, frequency, null, "private_silenced");
            return new Result(Outcome.SILENT, silent != null ? silent
                    : Component.translatable(KEY + "silent").withStyle(ChatFormatting.RED), 0);
        }
        return new Result(Outcome.ACCEPTED, press(level, player, now), 0);
    }

    /** Sends the pulse from here and powers the attached block; returns the "Code accepted: ..." line. */
    private Component press(ServerLevel level, Player player, long now) {
        BlockPos pos = getBlockPos();
        int frequency = getFrequency();
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> coverage = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos),
                Config.TOWERS_RELAY_REMOTES.get());
        SignalNetwork.Carried carried = network.carried(level, frequency, coverage);
        network.pulse(frequency, coverage, now + pulseTicks,
                SignalNetwork.Cause.keypad(pos, player.getUUID(), player.getGameProfile().getName()));
        if (getBlockState().getBlock() instanceof CodeKeypadBlock keypad) {
            keypad.press(getBlockState(), level, pos, pulseTicks);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F, 2.0F);
        // Short, so it fits above the hotbar with "Heard by ..." after it (Jade shows the keypad's reach).
        Component sent = Component.translatable(KEY + "accepted", PulseLength.format(pulseTicks), FrequencyNames.label(level, frequency))
                .withStyle(ChatFormatting.GREEN);
        return Component.translatable("message." + SignalTower.MOD_ID + ".remote_result", sent,
                RemoteControlItem.heardLine(RemoteControlItem.heardBy(level, network, carried)));
    }

    private void buzz(ServerLevel level, float pitch) {
        level.playSound(null, getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.9F, pitch);
    }

    private Component ownerLabel() {
        return Component.literal(ownerName.isEmpty() ? "?" : ownerName);
    }

    // ---- Saving (the code stays on the server: block entities aren't synced, and it isn't put on the item) ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        if (!ownerName.isEmpty()) {
            tag.putString("owner_name", ownerName);
        }
        if (!code.isEmpty()) {
            tag.putString("code", code);
        }
        tag.putInt("pulse_ticks", pulseTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        String saved = tag.getString("code");
        code = validCode(saved) ? saved : "";
        pulseTicks = tag.contains("pulse_ticks") ? PulseLength.clamp(tag.getInt("pulse_ticks")) : PulseLength.DEFAULT_TICKS;
    }
}
