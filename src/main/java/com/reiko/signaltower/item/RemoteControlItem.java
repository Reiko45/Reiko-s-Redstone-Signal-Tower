package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
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
 * pressing it from up high reaches farther.
 */
public class RemoteControlItem extends Item {
    /** How long a pulse lasts: 20 ticks, the same as a stone button. */
    public static final int PULSE_TICKS = 20;
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
                        stack.getHoverName(), isToggleMode(stack));
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
        int frequency = FrequencyTunerItem.getFrequency(stack);
        BlockPos origin = player.blockPosition();
        int reach = RadioHelper.reachAt(level, origin);
        SignalNetwork network = SignalNetwork.get(level);
        String key = "message." + SignalTower.MOD_ID + ".remote_";
        Component message;
        if (isToggleMode(stack)) {
            boolean on = network.toggleLatch(frequency, origin, reach);
            message = on
                    ? Component.translatable(key + "on", frequency, reach).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(key + "off", frequency).withStyle(ChatFormatting.RED);
            level.playSound(null, origin, on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.PLAYERS, 0.6F, on ? 1.3F : 1.0F);
        } else {
            network.pulse(frequency, origin, reach, level.getGameTime() + PULSE_TICKS);
            message = Component.translatable(key + "pulse", frequency, reach);
            level.playSound(null, origin, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.3F);
        }
        player.displayClientMessage(message, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyTunerItem.getFrequency(stack))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + "remote_mode",
                        Component.translatable(prefix + (isToggleMode(stack) ? "remote_mode_toggle" : "remote_mode_pulse")))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + "remote_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "remote_configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "remote_copy").withStyle(ChatFormatting.GRAY));
    }
}
