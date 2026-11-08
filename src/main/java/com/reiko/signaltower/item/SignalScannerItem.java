package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A handheld radio direction-finder. Right-click opens a live list of every frequency a receiver
 * where you stand would hear: its strength, and where the strongest signal comes from (distance
 * and direction for a transmitter; just "remote" for a remote press, so it never points at
 * players). Each row's Tune button sets that frequency on the radio item in your other hand.
 * Clicking a transmitter's row tracks it: while you hold the scanner, an arrow at the top of the
 * screen points to it. Click the row again to stop; breaking the transmitter stops it too.
 */
public class SignalScannerItem extends Item {
    public SignalScannerItem(Properties properties) {
        super(properties);
    }

    /** True for the items the scanner's Tune button can set: tuner, both remotes, pager. */
    public static boolean canTune(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof FrequencyTunerItem || item instanceof RemoteControlItem
                || item instanceof MultiRemoteItem || item instanceof PagerItem || item instanceof SignalMeterItem;
    }

    /** A Multi-Button Remote's buttons with the selected one re-tuned; its name, mode and color stay. */
    public static RemoteButtons retuneSelected(RemoteButtons buttons, int frequency) {
        int selected = buttons.selected();
        return buttons.withButton(selected, buttons.button(selected).withFrequency(frequency));
    }

    private static final String MESSAGE = "message." + SignalTower.MOD_ID + ".";

    public static @Nullable ScannerTarget getTarget(ItemStack stack) {
        return stack.get(ModRegistry.SCANNER_TARGET.get());
    }

    /**
     * Starts tracking the transmitter the scanner points to for {@code frequency} where the
     * player stands (the strongest one reaching them, the nearest among equals). Remote signals
     * can't be tracked, and nor can someone else's encrypted frequency. Returns the message to show.
     */
    public static Component track(ServerLevel level, Player player, ItemStack scanner, int frequency) {
        Optional<BlockPos> found = FrequencyClaims.mayHear(level, frequency, player.getUUID())
                ? SignalNetwork.get(level).strongestTransmitter(level, player.blockPosition(), frequency)
                : Optional.empty();
        if (found.isEmpty()) {
            return Component.translatable(MESSAGE + "scanner_cant_track", FrequencyNames.label(level, frequency)).withStyle(ChatFormatting.YELLOW);
        }
        scanner.set(ModRegistry.SCANNER_TARGET.get(), new ScannerTarget(frequency, found.get(), level.dimension()));
        return Component.translatable(MESSAGE + "scanner_tracking", FrequencyNames.label(level, frequency)).withStyle(ChatFormatting.GREEN);
    }

    public static Component stopTracking(ItemStack scanner) {
        scanner.remove(ModRegistry.SCANNER_TARGET.get());
        return Component.translatable(MESSAGE + "scanner_stopped").withStyle(ChatFormatting.GRAY);
    }

    /**
     * Drops the target if its transmitter (or repeater) is gone or was re-tuned to another frequency (only
     * checked in the target's own dimension). Returns the message to show, or null if it's fine.
     */
    public static @Nullable Component checkTarget(ServerLevel level, ItemStack scanner) {
        return checkTarget(level, scanner, null);
    }

    /**
     * {@link #checkTarget(ServerLevel, ItemStack)} for a scanner carried by {@code holder}: the
     * target is also dropped if its frequency is now someone else's encrypted one.
     */
    public static @Nullable Component checkTarget(ServerLevel level, ItemStack scanner, @Nullable UUID holder) {
        ScannerTarget target = getTarget(scanner);
        if (target == null) {
            return null;
        }
        if (!FrequencyClaims.mayHear(level, target.frequency(), holder)) {
            scanner.remove(ModRegistry.SCANNER_TARGET.get());
            return Component.translatable(MESSAGE + "scanner_lost_encrypted", FrequencyNames.label(level, target.frequency()))
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (!target.dimension().equals(level.dimension())) {
            return null;
        }
        boolean blockGone = level.isLoaded(target.pos())
                && !SignalNetwork.isTower(level.getBlockState(target.pos()).getBlock());
        OptionalInt frequency = SignalNetwork.get(level).towerFrequencyAt(target.pos());
        if (!blockGone && frequency.isPresent() && frequency.getAsInt() == target.frequency()) {
            return null;
        }
        scanner.remove(ModRegistry.SCANNER_TARGET.get());
        return Component.translatable(MESSAGE + "scanner_lost", FrequencyNames.label(level, target.frequency())).withStyle(ChatFormatting.YELLOW);
    }

    /** Once a second, makes sure the tracked transmitter is still there. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 20 == 0 && getTarget(stack) != null) {
            Component lost = checkTarget(serverLevel, stack, entity.getUUID());
            if (lost != null && entity instanceof Player player) {
                player.displayClientMessage(lost, true);
            }
        }
    }

    /** Re-equipping is for new items, not for the target changing. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
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
        ScannerTarget target = getTarget(stack);
        if (target != null) {
            tooltip.add(Component.translatable(prefix + "tracking", FrequencyNames.label(null, target.frequency())).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable(prefix + "use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "open").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "tune").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "track").withStyle(ChatFormatting.GRAY));
    }
}
