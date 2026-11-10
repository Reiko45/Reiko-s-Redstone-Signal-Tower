package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * The Walkie-Talkie (v0.64.0): a two-way handheld radio ({@link com.reiko.signaltower.radio.WalkieTalkie}).
 * <ul>
 *   <li>Right-click, or the Talk key (V) while holding it: a box to say something on its frequency.</li>
 *   <li>Sneak + right-click the air: its frequency, favorites and Sound.</li>
 *   <li>Sneak + right-click a radio block: copy its frequency.</li>
 * </ul>
 * Anywhere in the inventory it hears its frequency (in chat). Sneak + scroll changes the frequency.
 */
public class WalkieTalkieItem extends Item {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public WalkieTalkieItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            if (!level.isClientSide) {
                FrequencyTunerItem.setFrequency(context.getItemInHand(), radio.getFrequency());
                player.displayClientMessage(Component.translatable(KEY + "frequency_copied_walkie",
                        FrequencyNames.label(level, radio.getFrequency())), true);
                level.playSound(null, pos, com.reiko.signaltower.registry.ModSounds.TUNER_TUNE.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (player.isSecondaryUseActive()) {
                ClientHooks.openWalkieScreen(hand, FrequencyTunerItem.getFrequency(stack), stack.getHoverName(),
                        RadioFavorites.get(stack), !PagerItem.isSilent(stack));
            } else {
                ClientHooks.openTalkScreen(hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        int frequency = FrequencyTunerItem.getFrequency(stack);
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
        FrequencyNotes.addTooltipLine(frequency, tooltip);
        RadioFavorites.addFavoritesLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + (PagerItem.isSilent(stack) ? "walkie_silent" : "walkie_sound"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + "walkie_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "walkie_configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "walkie_copy").withStyle(ChatFormatting.GRAY));
        RadioFavorites.addScrollHint(tooltip);
    }
}
