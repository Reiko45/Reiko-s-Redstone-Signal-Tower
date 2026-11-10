package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioBlock;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.IntruderAlerts;
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
import org.jetbrains.annotations.Nullable;

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
                level.playSound(null, pos, com.reiko.signaltower.registry.ModSounds.TUNER_TUNE.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            } else {
                int frequency = getFrequency(stack);
                Component refused = refusal(level, radio, frequency, player);
                if (radio instanceof CodeKeypadBlockEntity keypad && player != null && !keypad.mayConfigure(player)) {
                    // Only a Code Keypad's owner (or an operator) re-tunes it (v0.37.0).
                    message = Component.translatable("message." + SignalTower.MOD_ID + ".keypad_not_yours_tune",
                            keypad.getOwnerName().isEmpty() ? "?" : keypad.getOwnerName()).withStyle(ChatFormatting.RED);
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
                } else if (radio instanceof com.reiko.signaltower.blockentity.MotionSensorBlockEntity sensor && player != null
                        && !sensor.mayConfigure(player)) {
                    // Likewise a Radio Motion Sensor's (v0.39.0).
                    message = Component.translatable("message." + SignalTower.MOD_ID + ".sensor_not_yours_tune",
                            sensor.ownerLabel()).withStyle(ChatFormatting.RED);
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
                } else if (refused != null) {
                    // Someone else's private frequency: this block would send on it, so it's not tuned.
                    message = refused;
                    if (player != null) {
                        IntruderAlerts.report(level.getServer(), sendsOn(radio, frequency), player, IntruderAlerts.Kind.TUNE,
                                level.getBlockState(pos).getBlock());
                    }
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
                } else {
                    radio.setTunedBy(player == null ? null : player.getUUID());
                    radio.setFrequency(frequency);
                    // Tuned, but on someone else's encrypted frequency a receiver or converter won't hear anything: say so.
                    Component deaf = level instanceof ServerLevel serverLevel ? RadioBlock.deafNotice(serverLevel, pos) : null;
                    message = deaf != null ? deaf
                            : Component.translatable("message." + SignalTower.MOD_ID + ".frequency_set", FrequencyNames.label(level, radio.getFrequency()));
                    if (deaf != null && player != null) {
                        IntruderAlerts.report(level.getServer(), radio.getFrequency(), player, IntruderAlerts.Kind.DEAF,
                                level.getBlockState(pos).getBlock());
                    }
                    level.playSound(null, pos, com.reiko.signaltower.registry.ModSounds.TUNER_TUNE.get(), SoundSource.BLOCKS, 0.7F, 1.15F);
                }
            }
            if (player != null) {
                player.displayClientMessage(message, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Why {@code player} may not tune {@code radio} to {@code frequency}, or null if they may. A
     * transmitter, repeater or Code Keypad would send on that frequency, and a converter on its output
     * frequency, so they're refused if that's someone else's private frequency. Receivers only
     * listen, so they can always be tuned.
     */
    private static @Nullable Component refusal(Level level, RadioBlockEntity radio, int frequency, @Nullable Player player) {
        int sends = sendsOn(radio, frequency);
        if (sends < 0) {
            return null;
        }
        return FrequencyClaims.refusal(level, sends, player == null ? null : player.getUUID(),
                radio instanceof com.reiko.signaltower.blockentity.RadioJammerBlockEntity ? "private_refused_jammer" : "private_refused_block");
    }

    /** The frequency {@code radio} would send on if tuned to {@code frequency} (a converter: its output), or -1 for a receiver. */
    private static int sendsOn(RadioBlockEntity radio, int frequency) {
        if (radio instanceof RadioTransmitterBlockEntity || radio instanceof CodeKeypadBlockEntity
                || radio instanceof com.reiko.signaltower.blockentity.TvCameraBlockEntity) { // v0.66.0: a camera sends its picture
            return frequency;
        }
        if (radio instanceof RadioRepeaterBlockEntity relay) {
            return relay.isConverter() ? relay.getOutputFrequency() : frequency;
        }
        return -1;
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
        FrequencyNotes.addTooltipLine(getFrequency(stack), tooltip);
        RadioFavorites.addFavoritesLine(stack, tooltip);
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_use_air").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_use_block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_copy").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tuner_ring").withStyle(ChatFormatting.GRAY));
        RadioFavorites.addScrollHint(tooltip);

    }
}
