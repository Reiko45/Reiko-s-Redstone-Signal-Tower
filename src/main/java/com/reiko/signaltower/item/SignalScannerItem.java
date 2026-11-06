package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A handheld radio direction-finder. Right-click opens a live list of every frequency a receiver
 * where you stand would hear: its strength, and where the strongest signal comes from (distance
 * and direction for a transmitter; just "remote" for a remote press, so it never points at
 * players). Each row's Tune button sets that frequency on the radio item in your other hand.
 */
public class SignalScannerItem extends Item {
    public SignalScannerItem(Properties properties) {
        super(properties);
    }

    /** True for the items the scanner's Tune button can set: tuner, both remotes, pager. */
    public static boolean canTune(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof FrequencyTunerItem || item instanceof RemoteControlItem
                || item instanceof MultiRemoteItem || item instanceof PagerItem;
    }

    /** A Multi-Button Remote's buttons with the selected one re-tuned; its name, mode and color stay. */
    public static RemoteButtons retuneSelected(RemoteButtons buttons, int frequency) {
        int selected = buttons.selected();
        return buttons.withButton(selected, buttons.button(selected).withFrequency(frequency));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientHooks.openScannerScreen(hand, stack.getHoverName());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".scanner_";
        tooltip.add(Component.translatable(prefix + "use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "open").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "tune").withStyle(ChatFormatting.GRAY));
    }
}
