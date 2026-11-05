package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
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
 * A Remote Control with four buttons, each on its own frequency and mode.
 * <ul>
 *   <li>Right-click: press the selected button.</li>
 *   <li>Sneak + scroll: choose the selected button.</li>
 *   <li>Sneak + right-click the air: open the keypad to press any button and set each one up.</li>
 *   <li>Sneak + right-click a transmitter or receiver: copy its frequency into the selected button.</li>
 * </ul>
 */
public class MultiRemoteItem extends Item {
    private static final int COOLDOWN_TICKS = 10;
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public MultiRemoteItem(Properties properties) {
        super(properties);
    }

    public static RemoteButtons getButtons(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.REMOTE_BUTTONS.get(), RemoteButtons.DEFAULT);
    }

    public static void setButtons(ItemStack stack, RemoteButtons buttons) {
        stack.set(ModRegistry.REMOTE_BUTTONS.get(), buttons);
    }

    /** Presses button {@code index} (0-3) from where the player is standing. */
    public static void pressButton(ServerLevel level, Player player, ItemStack stack, int index) {
        RemoteButtons.Button button = getButtons(stack).button(index);
        Component result = RemoteControlItem.sendSignal(level, player, button.frequency(), button.toggle(), button.pulseTicks());
        player.displayClientMessage(Component.translatable(KEY + "remote_button", index + 1, result), true);
    }

    /** Moves the selection by {@code delta} buttons (wrapping around) and says which one is picked. */
    public static void cycleSelection(Player player, ItemStack stack, int delta) {
        RemoteButtons buttons = getButtons(stack);
        RemoteButtons updated = buttons.withSelected(buttons.selected() + delta);
        setButtons(stack, updated);
        player.displayClientMessage(describeSelected(updated), true);
    }

    private static Component describeSelected(RemoteButtons buttons) {
        RemoteButtons.Button button = buttons.selectedButton();
        return Component.translatable(KEY + "remote_button_selected", buttons.selected() + 1, button.frequency(),
                RemoteControlItem.describeMode(button.toggle(), button.pulseTicks())).withStyle(ChatFormatting.AQUA);
    }

    public static Component modeName(boolean toggle) {
        return Component.translatable("tooltip." + SignalTower.MOD_ID + "." + (toggle ? "remote_mode_toggle" : "remote_mode_pulse"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            if (!level.isClientSide) {
                ItemStack stack = context.getItemInHand();
                RemoteButtons buttons = getButtons(stack);
                RemoteButtons.Button old = buttons.selectedButton();
                setButtons(stack, buttons.withButton(buttons.selected(), old.withFrequency(radio.getFrequency())));
                player.displayClientMessage(Component.translatable(KEY + "frequency_copied_button",
                        radio.getFrequency(), buttons.selected() + 1), true);
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
                ClientHooks.openMultiRemoteScreen(hand, getButtons(stack), stack.getHoverName());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level instanceof ServerLevel serverLevel) {
            pressButton(serverLevel, player, stack, getButtons(stack).selected());
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RemoteButtons buttons = getButtons(stack);
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            RemoteButtons.Button button = buttons.button(i);
            boolean selected = i == buttons.selected();
            tooltip.add(Component.translatable(prefix + (selected ? "remote_button_line_selected" : "remote_button_line"),
                    i + 1, button.frequency(), RemoteControlItem.describeMode(button.toggle(), button.pulseTicks()))
                    .withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable(prefix + "multi_remote_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "multi_remote_select").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "multi_remote_configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "multi_remote_copy").withStyle(ChatFormatting.GRAY));
    }
}
