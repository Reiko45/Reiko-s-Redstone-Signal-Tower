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

import java.util.ArrayList;
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
 * <p>
 * Guest codes (v0.38.0): up to 8 extra codes with a name, each working a set number of times or
 * until a real-time limit runs out. One that has run out is refused without counting as a wrong
 * code. The owner (if online) is told when one is used, and the frequency log names it.
 */
public class CodeKeypadBlockEntity extends RadioBlockEntity {
    public static final int MIN_CODE = 4;
    public static final int MAX_CODE = 8;
    /** Wrong codes in a row before a player is locked out. */
    public static final int MAX_MISSES = 3;
    /** How long a player is locked out: 30 seconds. */
    public static final int LOCK_TICKS = 600;

    private static final String KEY = "message." + SignalTower.MOD_ID + ".keypad_";

    /** Most guest codes on one keypad (v0.38.0). */
    public static final int MAX_GUEST_CODES = 8;
    /** Longest guest code name. */
    public static final int MAX_LABEL = 16;
    /** The use counts the Guest codes screen offers (-1: no limit). */
    public static final int[] GUEST_USES = {1, 3, 10, -1};
    /** The times it offers, in seconds (0: no limit). */
    public static final int[] GUEST_SECONDS = {3600, 86400, 604800, 0};

    /** What happened when someone typed a code, or the owner changed a setting. Sent by ordinal: only add at the end. */
    public enum Outcome {
        ACCEPTED, SILENT, WRONG, LOCKED, NO_CODE, SAVED, REFUSED, RUN_OUT
    }

    /**
     * A guest code (v0.38.0): an extra code with a name, that works {@code usesLeft} more times (-1:
     * no limit) until {@code untilMillis} (real time; 0: no limit). {@code used} and {@code lastUser}
     * say how it's been used.
     */
    public record GuestCode(String label, String code, int usesLeft, long untilMillis, int used, String lastUser) {
        public boolean expired(long nowMillis) {
            return untilMillis > 0 && nowMillis >= untilMillis;
        }

        public boolean usedUp() {
            return usesLeft == 0;
        }

        public boolean works(long nowMillis) {
            return !usedUp() && !expired(nowMillis);
        }

        /** Seconds left (rounded up), -1 for no time limit, 0 once expired. */
        public long secondsLeft(long nowMillis) {
            return untilMillis <= 0 ? -1 : Math.max(0, (untilMillis - nowMillis + 999) / 1000);
        }
    }

    public record Result(Outcome outcome, Component message, int lockedSeconds) {
    }

    private @Nullable UUID owner;
    private String ownerName = "";
    private String code = "";
    private int pulseTicks = PulseLength.DEFAULT_TICKS;
    private final List<GuestCode> guestCodes = new ArrayList<>();
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
        if (!newCode.isEmpty() && guestCodeFor(newCode) != null) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "guest_code_taken").withStyle(ChatFormatting.RED), 0);
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
        return enter(level, player, digits, now, System.currentTimeMillis());
    }

    /** {@link #enter(ServerLevel, Player, String, long)} at real time {@code nowMillis} (for guest codes' time limits). */
    public Result enter(ServerLevel level, Player player, String digits, long now, long nowMillis) {
        UUID who = player.getUUID();
        int locked = lockedSeconds(who, now);
        if (locked > 0) {
            buzz(level, 0.4F);
            return new Result(Outcome.LOCKED, Component.translatable(KEY + "locked", locked).withStyle(ChatFormatting.RED), locked);
        }
        lockedUntil.remove(who);
        // An empty code never matches (a keypad with only guest codes has code "").
        boolean mainMatch = hasCode() && code.equals(digits);
        GuestCode guest = mainMatch ? null : guestCodeFor(digits);
        if (!hasCode() && guestCodes.isEmpty()) {
            buzz(level, 0.8F);
            return new Result(Outcome.NO_CODE, Component.translatable(KEY + "no_code", ownerLabel()).withStyle(ChatFormatting.GRAY), 0);
        }
        if (guest != null) {
            if (!guest.works(nowMillis)) {
                // A guest code that has run out: say so, without counting it as a wrong code.
                buzz(level, 0.7F);
                return new Result(Outcome.RUN_OUT, Component.translatable(KEY + (guest.usedUp() ? "guest_used_up" : "guest_expired"))
                        .withStyle(ChatFormatting.RED), 0);
            }
        }
        if (!mainMatch && guest == null) {
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
        if (guest != null) {
            guest = useGuestCode(guest, player.getGameProfile().getName());
            tellOwnerGuestUsed(level, player, guest);
        }
        return new Result(Outcome.ACCEPTED, press(level, player, now, guest != null ? guest.label() : ""), 0);
    }

    // ---- Guest codes (v0.38.0) ----

    public List<GuestCode> guestCodes() {
        return List.copyOf(guestCodes);
    }

    private @Nullable GuestCode guestCodeFor(String digits) {
        for (GuestCode guest : guestCodes) {
            if (guest.code().equals(digits)) {
                return guest;
            }
        }
        return null;
    }

    /** Counts a use of {@code guest} by {@code name}; returns it as it is now. */
    private GuestCode useGuestCode(GuestCode guest, String name) {
        GuestCode next = new GuestCode(guest.label(), guest.code(), guest.usesLeft() > 0 ? guest.usesLeft() - 1 : guest.usesLeft(),
                guest.untilMillis(), guest.used() + 1, name);
        guestCodes.set(guestCodes.indexOf(guest), next);
        setChanged();
        return next;
    }

    /** The owner, if online and not the one who typed it, is told which guest code was used and by whom. */
    private void tellOwnerGuestUsed(ServerLevel level, Player player, GuestCode guest) {
        if (owner == null || owner.equals(player.getUUID())) {
            return;
        }
        net.minecraft.server.level.ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null) {
            return;
        }
        BlockPos pos = getBlockPos();
        Component left = guest.usesLeft() < 0 ? Component.translatable(KEY + "guest_left_unlimited")
                : guest.usesLeft() == 0 ? Component.translatable(KEY + "guest_left_none")
                : guest.usesLeft() == 1 ? Component.translatable(KEY + "guest_left_one")
                : Component.translatable(KEY + "guest_left_many", guest.usesLeft());
        ownerPlayer.displayClientMessage(Component.translatable(KEY + "guest_used",
                Component.translatable(getBlockState().getBlock().getDescriptionId()),
                pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), player.getGameProfile().getName(), guest.label(), left)
                .withStyle(ChatFormatting.AQUA), false);
    }

    /** A guest code's name, tidied (formatting codes dropped, trimmed, cut to {@link #MAX_LABEL}). */
    public static String cleanLabel(String label) {
        String clean = net.minecraft.util.StringUtil.filterText(label == null ? "" : label).trim();
        return clean.length() > MAX_LABEL ? clean.substring(0, MAX_LABEL).trim() : clean;
    }

    /**
     * The owner adds a guest code {@code label} / {@code newCode} that works {@code uses} times (-1:
     * no limit) for {@code seconds} (0: no limit). Refused for anyone else, a bad code, one already
     * on the keypad (the main code too), a name already used, or a ninth.
     */
    public Result addGuestCode(Player player, String label, String newCode, int uses, int seconds, long nowMillis) {
        if (!mayConfigure(player)) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "not_yours", ownerLabel()).withStyle(ChatFormatting.RED), 0);
        }
        String name = cleanLabel(label);
        if (name.isEmpty()) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "guest_bad_label").withStyle(ChatFormatting.RED), 0);
        }
        if (!validCode(newCode)) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "bad_code", MIN_CODE, MAX_CODE).withStyle(ChatFormatting.RED), 0);
        }
        if (newCode.equals(code) || guestCodeFor(newCode) != null) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "guest_code_taken").withStyle(ChatFormatting.RED), 0);
        }
        if (guestNamed(name) != null) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "guest_label_taken", name).withStyle(ChatFormatting.RED), 0);
        }
        if (guestCodes.size() >= MAX_GUEST_CODES) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "guest_full", MAX_GUEST_CODES).withStyle(ChatFormatting.RED), 0);
        }
        int usesLeft = uses < 0 ? -1 : Math.max(1, uses);
        long until = seconds > 0 ? nowMillis + seconds * 1000L : 0;
        guestCodes.add(new GuestCode(name, newCode, usesLeft, until, 0, ""));
        if (owner == null) {
            setOwner(player.getUUID(), player.getGameProfile().getName());
        }
        setChanged();
        return new Result(Outcome.SAVED, Component.translatable(KEY + "guest_added", name, usesText(usesLeft), timeText(seconds))
                .withStyle(ChatFormatting.GREEN), 0);
    }

    /** The owner removes the guest code called {@code label} (ignoring case). */
    public Result removeGuestCode(Player player, String label) {
        if (!mayConfigure(player)) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "not_yours", ownerLabel()).withStyle(ChatFormatting.RED), 0);
        }
        GuestCode guest = guestNamed(cleanLabel(label));
        if (guest == null) {
            return new Result(Outcome.REFUSED, Component.translatable(KEY + "guest_unknown", cleanLabel(label)).withStyle(ChatFormatting.RED), 0);
        }
        guestCodes.remove(guest);
        setChanged();
        return new Result(Outcome.SAVED, Component.translatable(KEY + "guest_removed", guest.label()).withStyle(ChatFormatting.GREEN), 0);
    }

    private @Nullable GuestCode guestNamed(String label) {
        for (GuestCode guest : guestCodes) {
            if (guest.label().equalsIgnoreCase(label)) {
                return guest;
            }
        }
        return null;
    }

    /** "3 uses", "1 use", "any number of uses". */
    public static Component usesText(int uses) {
        return uses < 0 ? Component.translatable(KEY + "guest_uses_unlimited")
                : uses == 1 ? Component.translatable(KEY + "guest_uses_one")
                : Component.translatable(KEY + "guest_uses_many", uses);
    }

    /** "for 1 day", "no time limit". */
    public static Component timeText(int seconds) {
        if (seconds <= 0) {
            return Component.translatable(KEY + "guest_no_time");
        }
        String duration = seconds >= 604800 ? "week" : seconds >= 86400 ? "day" : "hour";
        return Component.translatable(KEY + "guest_for", Component.translatable("duration." + SignalTower.MOD_ID + "." + duration));
    }

    /** Sends the pulse from here and powers the attached block; returns the "Code accepted: ..." line. {@code label}: the guest code used, or "". */
    private Component press(ServerLevel level, Player player, long now, String label) {
        BlockPos pos = getBlockPos();
        int frequency = getFrequency();
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> coverage = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos),
                Config.TOWERS_RELAY_REMOTES.get());
        SignalNetwork.Carried carried = network.carried(level, frequency, coverage);
        network.pulse(frequency, coverage, now + pulseTicks,
                SignalNetwork.Cause.keypad(pos, player.getUUID(), player.getGameProfile().getName(), label));
        if (getBlockState().getBlock() instanceof CodeKeypadBlock keypad) {
            keypad.press(getBlockState(), level, pos, pulseTicks);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F, 2.0F);
        // Short, so it fits above the hotbar with "Heard by ..." after it (Jade shows the keypad's reach).
        Component sent = (label.isEmpty()
                ? Component.translatable(KEY + "accepted", PulseLength.format(pulseTicks), FrequencyNames.label(level, frequency))
                : Component.translatable(KEY + "guest_accepted", label, PulseLength.format(pulseTicks), FrequencyNames.label(level, frequency)))
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
        if (!guestCodes.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (GuestCode guest : guestCodes) {
                CompoundTag entry = new CompoundTag();
                entry.putString("label", guest.label());
                entry.putString("code", guest.code());
                entry.putInt("uses_left", guest.usesLeft());
                entry.putLong("until", guest.untilMillis());
                entry.putInt("used", guest.used());
                entry.putString("last_user", guest.lastUser());
                list.add(entry);
            }
            tag.put("guest_codes", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        String saved = tag.getString("code");
        code = validCode(saved) ? saved : "";
        pulseTicks = tag.contains("pulse_ticks") ? PulseLength.clamp(tag.getInt("pulse_ticks")) : PulseLength.DEFAULT_TICKS;
        guestCodes.clear();
        net.minecraft.nbt.ListTag list = tag.getList("guest_codes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && guestCodes.size() < MAX_GUEST_CODES; i++) {
            CompoundTag entry = list.getCompound(i);
            String guestCode = entry.getString("code");
            String label = cleanLabel(entry.getString("label"));
            if (validCode(guestCode) && !label.isEmpty()) {
                guestCodes.add(new GuestCode(label, guestCode, entry.getInt("uses_left"), entry.getLong("until"),
                        entry.getInt("used"), entry.getString("last_user")));
            }
        }
    }
}
