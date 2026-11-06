package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * Sets which frequency a transmitter or receiver uses.
 * <ul>
 *   <li>Right-click the air: opens a screen to type a frequency.</li>
 *   <li>Right-click a transmitter or receiver: tunes it to the tuner's frequency.</li>
 *   <li>Sneak + right-click a transmitter or receiver: copies its frequency into the tuner.</li>
 * </ul>
 */
public class FrequencyTunerItem extends Item {
    public FrequencyTunerItem(Properties properties) {
        super(properties);
    }

    public static int getFrequency(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.FREQUENCY.get(), Frequency.DEFAULT);
    }

    public static void setFrequency(ItemStack stack, int frequency) {
        stack.set(ModRegistry.FREQUENCY.get(), Frequency.clamp(frequency));
    }

    /** The frequency changes while it's held when you scroll; don't replay the "pull out" animation for that. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Player player = context.getPlayer();
            ItemStack stack = context.getItemInHand();
            Component message;
            if (player != null && player.isSecondaryUseActive()) {
                setFrequency(stack, radio.getFrequency());
                message = Component.translatable("message." + SignalTower.MOD_ID + ".frequency_copied", FrequencyNames.label(level, radio.getFrequency()));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            } else {
                radio.setFrequency(getFrequency(stack));
                message = Component.translatable("message." + SignalTower.MOD_ID + ".frequency_set", FrequencyNames.label(level, radio.getFrequency()));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.6F);
            }
            if (player != null) {
                player.displayClientMessage(message, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // Only runs on the client, so the screen class is never loaded on a dedicated server.
            ClientHooks.openFrequencyScreen(hand, getFrequency(stack), stack.getHoverName(), null, null, RadioFavorites.get(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".frequency", FrequencyNames.label(null, getFrequency(stack)))
                .withStyle(ChatFormatting.AQUA));
        RadioFavorites.addFavoritesLine(stack, tooltip);
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_use_air").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_use_block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_copy").withStyle(ChatFormatting.GRAY));
        RadioFavorites.addScrollHint(tooltip);

    }
}
