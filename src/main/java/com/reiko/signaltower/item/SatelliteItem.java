package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.LaunchPadBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Satellite (v0.61.0): put it on a Launch Pad with eight Firework Rockets and pulse the pad, and it
 * goes up to orbit over the dimension. Once one is up, Satellite Dishes there under open sky are heard
 * everywhere in that dimension.
 */
public class SatelliteItem extends Item {
    public SatelliteItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".satellite_help", LaunchPadBlockEntity.FIREWORKS_NEEDED)
                .withStyle(ChatFormatting.GRAY));
    }
}
