package com.reiko.signaltower.item;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A handheld transmitter, like a garage door opener.
 * <ul>
 *   <li>Right-click: press the button.
 *       <b>Pulse</b> mode sends a full-strength signal for one second, like a stone button.
 *       <b>Toggle</b> mode switches the frequency on, and the next press switches it off.</li>
 *   <li>Sneak + right-click the air: change the frequency and mode.</li>
 *   <li>Sneak + right-click a transmitter or receiver: copy its frequency.</li>
 * </ul>
 * Reach uses the same rule as a Radio Transmitter, measured from where the player is standing, so
 * pressing it from up high reaches farther. Every Radio Transmitter within that reach also relays
 * the press with its own reach (unless the server config turns that off), so a tall tower nearby
 * carries the remote as far as the tower reaches.
 */
public class RemoteControlItem extends Item {
    /** Default pulse length: 20 ticks, the same as a stone button. */
    public static final int PULSE_TICKS = PulseLength.DEFAULT_TICKS;
    /** Stops held right-click from flickering a toggle on and off. */
    private static final int COOLDOWN_TICKS = 10;

    public RemoteControlItem(Properties properties) {
        super(properties);
    }

    public static boolean isToggleMode(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.REMOTE_TOGGLE_MODE.get(), false);
    }

    public static void setToggleMode(ItemStack stack, boolean toggle) {
        stack.set(ModRegistry.REMOTE_TOGGLE_MODE.get(), toggle);
    }

    public static int getPulseTicks(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.REMOTE_PULSE_TICKS.get(), PulseLength.DEFAULT_TICKS);
    }

    public static void setPulseTicks(ItemStack stack, int ticks) {
        stack.set(ModRegistry.REMOTE_PULSE_TICKS.get(), PulseLength.clamp(ticks));
    }

    /** "Toggle", or "Pulse 0.5s". */
    public static Component describeMode(boolean toggle, int pulseTicks) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        return toggle
                ? Component.translatable(prefix + "remote_mode_toggle")
                : Component.translatable(prefix + "remote_mode_pulse_length", PulseLength.format(pulseTicks));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // Sneak-clicking a radio block copies its frequency; anything else counts as pressing the button.
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            if (!level.isClientSide) {
                FrequencyTunerItem.setFrequency(context.getItemInHand(), radio.getFrequency());
                player.displayClientMessage(Component.translatable(
                        "message." + SignalTower.MOD_ID + ".frequency_copied_remote", radio.getFrequency()), true);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                ClientHooks.openFrequencyScreen(hand, FrequencyTunerItem.getFrequency(stack),
                        stack.getHoverName(), isToggleMode(stack), getPulseTicks(stack), RadioFavorites.get(stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level instanceof ServerLevel serverLevel) {
            press(serverLevel, player, stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Presses the button: sends a pulse or flips the toggle, from where the player is. */
    public static void press(ServerLevel level, Player player, ItemStack stack) {
        player.displayClientMessage(
                sendSignal(level, player, stack, FrequencyTunerItem.getFrequency(stack), isToggleMode(stack), getPulseTicks(stack)), true);
    }

    /**
     * Where a remote used by {@code player} right now reaches: the player's own reach, plus every
     * Radio Transmitter in that reach if the server config lets towers relay remotes.
     */
    public static List<SignalNetwork.Coverage> coverageFor(ServerLevel level, Player player) {
        BlockPos origin = player.blockPosition();
        return SignalNetwork.get(level).remoteCoverage(level, origin, RadioHelper.reachAt(level, origin),
                Config.TOWERS_RELAY_REMOTES.get());
    }

    /** "32 blocks", "32 blocks + 1 tower" or "32 blocks + 3 towers". */
    public static Component describeReach(int reach, int towers) {
        return describeReach(reach, towers, 0);
    }

    /** {@link #describeReach(int, int)}, then " + 1 repeater" or " + 2 repeaters" for repeaters passing it on further. */
    public static Component describeReach(int reach, int towers, int repeaters) {
        return describeReach(reach, towers, repeaters, 0);
    }

    /** {@link #describeReach(int, int, int)}, then " + 1 converter" or " + 2 converters" for converters passing it on to other frequencies. */
    public static Component describeReach(int reach, int towers, int repeaters, int converters) {
        String key = "message." + SignalTower.MOD_ID + ".reach_";
        Component base = towers == 0 ? Component.translatable(key + "blocks", reach)
                : towers == 1 ? Component.translatable(key + "one_tower", reach)
                : Component.translatable(key + "towers", reach, towers);
        Component withRepeaters = repeaters == 0 ? base
                : repeaters == 1 ? Component.translatable(key + "plus_one_repeater", base)
                : Component.translatable(key + "plus_repeaters", base, repeaters);
        return converters == 0 ? withRepeaters
                : converters == 1 ? Component.translatable(key + "plus_one_converter", withRepeaters)
                : Component.translatable(key + "plus_converters", withRepeaters, converters);
    }

    /**
     * Sends a remote signal on {@code frequency} from where the player is standing (and from every
     * tower relaying it, and every repeater on that frequency passing it on), plays the click, lights the remote's status light (green if a receiver
     * heard it, red if not), and returns a short description of what happened. Shared with the
     * Multi-Button Remote.
     */
    public static Component sendSignal(ServerLevel level, Player player, ItemStack remote, int frequency,
                                       boolean toggle, int pulseTicks) {
        return sendSignal(level, player, remote, frequency, toggle, pulseTicks, coverageFor(level, player));
    }

    /** {@link #sendSignal} with the coverage already worked out. The first area is the player's own. */
    public static Component sendSignal(ServerLevel level, Player player, ItemStack remote, int frequency,
                                       boolean toggle, int pulseTicks, List<SignalNetwork.Coverage> coverage) {
        BlockPos origin = coverage.get(0).origin();
        // Only the owner and their friends can send on a private frequency.
        Component refused = FrequencyClaims.refusal(level, frequency, player.getUUID(), "private_refused_send");
        if (refused != null) {
            com.reiko.signaltower.radio.IntruderAlerts.report(level.getServer(), frequency, player,
                    com.reiko.signaltower.radio.IntruderAlerts.Kind.REMOTE, null);
            level.playSound(null, origin, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.6F, 0.7F);
            remote.set(ModRegistry.REMOTE_LIGHT.get(), new RemoteLight(false, level.getGameTime() + RemoteLight.SHOW_TICKS));
            return refused;
        }
        SignalNetwork network = SignalNetwork.get(level);
        // Repeaters pass the press on further, and converters onto other frequencies; count who hears it there too.
        SignalNetwork.Carried carried = network.carried(level, frequency, coverage);
        Component reach = describeReach(coverage.get(0).reach(), coverage.size() - 1, carried.repeaters(), carried.converters());
        String key = "message." + SignalTower.MOD_ID + ".remote_";
        Component result;
        if (toggle) {
            boolean on = network.toggleLatch(frequency, coverage,
                    SignalNetwork.Cause.remote(true, player.getUUID(), player.getGameProfile().getName()));
            level.playSound(null, origin, on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.PLAYERS, 0.6F, on ? 1.3F : 1.0F);
            result = on
                    ? Component.translatable(key + "on", FrequencyNames.label(level, frequency), reach).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(key + "off", FrequencyNames.label(level, frequency)).withStyle(ChatFormatting.RED);
        } else {
            int ticks = PulseLength.clamp(pulseTicks);
            network.pulse(frequency, coverage, level.getGameTime() + ticks,
                    SignalNetwork.Cause.remote(false, player.getUUID(), player.getGameProfile().getName()));
            level.playSound(null, origin, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.3F);
            result = Component.translatable(key + "pulse", PulseLength.format(ticks), FrequencyNames.label(level, frequency), reach);
        }

        // Pagers in players' pockets count as receivers too.
        int heardBy = 0;
        for (var entry : carried.areas().entrySet()) {
            heardBy += network.countReceivers(entry.getKey(), entry.getValue())
                    + PagerItem.countPagers(level, entry.getKey(), entry.getValue());
        }
        remote.set(ModRegistry.REMOTE_LIGHT.get(), new RemoteLight(heardBy > 0, level.getGameTime() + RemoteLight.SHOW_TICKS));
        Component heard = heardBy == 0
                ? Component.translatable(key + "not_heard").withStyle(ChatFormatting.RED)
                : heardBy == 1
                    ? Component.translatable(key + "heard_one").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(key + "heard_many", heardBy).withStyle(ChatFormatting.GREEN);
        return Component.translatable(key + "result", result, heard);
    }

    /** Lighting the status light changes the item; don't replay the "pull out" animation for that. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, FrequencyTunerItem.getFrequency(stack)))
                .withStyle(ChatFormatting.AQUA));
        FrequencyNotes.addTooltipLine(FrequencyTunerItem.getFrequency(stack), tooltip);
        RadioFavorites.addFavoritesLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + "remote_mode", describeMode(isToggleMode(stack), getPulseTicks(stack)))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + "remote_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "remote_configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "remote_copy").withStyle(ChatFormatting.GRAY));
        RadioFavorites.addScrollHint(tooltip);
    }
}
