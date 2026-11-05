package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A pocket receiver. While it's anywhere in a player's inventory, it listens on its frequency where
 * the player is (inside a transmitter's reach, or a remote's), and the moment that frequency turns
 * on it beeps three times and shows a message. Its screen stays lit while the signal is on.
 * <ul>
 *   <li>Right-click: switch the sound on or off (messages still show).</li>
 *   <li>Sneak + right-click the air: change the frequency.</li>
 *   <li>Sneak + right-click a transmitter or receiver: copy its frequency.</li>
 * </ul>
 * Renaming it in an anvil changes the name its messages use ("Front Door: frequency 5 is ON").
 */
public class PagerItem extends Item {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    /** Total beeps per alert, and the ticks between them. */
    public static final int BEEPS = 3;
    private static final int BEEP_GAP_TICKS = 4;

    public PagerItem(Properties properties) {
        super(properties);
    }

    /** Sets the frequency and forgets the last signal, so the new frequency alerts if it's already on. */
    public static void setFrequency(ItemStack stack, int frequency) {
        FrequencyTunerItem.setFrequency(stack, frequency);
        stack.remove(ModRegistry.PAGER_SIGNAL.get());
        stack.remove(ModRegistry.PAGER_BEEPS.get());
    }

    /** The strength (0-15) the pager heard last time it checked. */
    public static int lastSignal(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.PAGER_SIGNAL.get(), 0);
    }

    public static boolean isSilent(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.PAGER_SILENT.get(), false);
    }

    public static void setSilent(ItemStack stack, boolean silent) {
        stack.set(ModRegistry.PAGER_SILENT.get(), silent);
    }

    /** "Radio Pager", or the name it was given in an anvil. */
    public static Component label(ItemStack stack) {
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        return custom != null ? custom : Component.translatable("item." + SignalTower.MOD_ID + ".pager");
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        checkSignal(serverLevel, entity, stack);
        // The rest of the alert's beeps, a few ticks apart.
        int beeps = stack.getOrDefault(ModRegistry.PAGER_BEEPS.get(), 0);
        if (beeps > 0 && serverLevel.getGameTime() % BEEP_GAP_TICKS == 0) {
            beep(entity);
            if (beeps > 1) {
                stack.set(ModRegistry.PAGER_BEEPS.get(), beeps - 1);
            } else {
                stack.remove(ModRegistry.PAGER_BEEPS.get());
            }
        }
    }

    /**
     * Listens on the pager's frequency where {@code holder} is. If the signal just turned on, starts
     * the beeps, shows the alert to the holder, and returns the alert. Otherwise returns null.
     */
    public static @Nullable Component checkSignal(ServerLevel level, Entity holder, ItemStack stack) {
        int frequency = FrequencyTunerItem.getFrequency(stack);
        int power = SignalNetwork.get(level).listen(level, frequency, holder.blockPosition()).power();
        int previous = lastSignal(stack);
        if (power == previous) {
            return null;
        }
        if (power > 0) {
            stack.set(ModRegistry.PAGER_SIGNAL.get(), power);
        } else {
            stack.remove(ModRegistry.PAGER_SIGNAL.get());
        }
        if (previous > 0 || power == 0) {
            return null; // only the moment it turns on is news
        }
        Component alert = Component.translatable(KEY + "pager_alert", label(stack), frequency, power)
                .withStyle(ChatFormatting.GOLD);
        if (holder instanceof Player player) {
            player.displayClientMessage(alert, true);
        }
        if (!isSilent(stack)) {
            beep(holder);
            stack.set(ModRegistry.PAGER_BEEPS.get(), BEEPS - 1);
        }
        return alert;
    }

    /** One beep, heard only by the pager's owner. */
    private static void beep(Entity holder) {
        if (holder instanceof ServerPlayer player) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8F, 1.8F);
        }
    }

    /**
     * How many pagers on {@code frequency} the player carries, if the player is inside
     * {@code coverage}. Used for a remote's "Heard by" count.
     */
    public static int pagersHeard(Player player, int frequency, List<SignalNetwork.Coverage> coverage) {
        if (!SignalNetwork.covers(coverage, player.blockPosition())) {
            return 0;
        }
        Container inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof PagerItem && FrequencyTunerItem.getFrequency(stack) == frequency) {
                count++;
            }
        }
        return count;
    }

    /** {@link #pagersHeard} for every player in the dimension. */
    public static int countPagers(ServerLevel level, int frequency, List<SignalNetwork.Coverage> coverage) {
        int count = 0;
        for (ServerPlayer player : level.players()) {
            count += pagersHeard(player, frequency, coverage);
        }
        return count;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            if (!level.isClientSide) {
                setFrequency(context.getItemInHand(), radio.getFrequency());
                player.displayClientMessage(Component.translatable(KEY + "frequency_copied_pager", radio.getFrequency()), true);
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
                ClientHooks.openFrequencyScreen(hand, FrequencyTunerItem.getFrequency(stack), stack.getHoverName(), null, null);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) {
            boolean silent = !isSilent(stack);
            setSilent(stack, silent);
            player.displayClientMessage(Component.translatable(KEY + (silent ? "pager_sound_off" : "pager_sound_on")), true);
            if (!silent) {
                beep(player);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** The signal and beep components change while it's held; don't replay the "pull out" animation for that. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyTunerItem.getFrequency(stack))
                .withStyle(ChatFormatting.AQUA));
        int signal = lastSignal(stack);
        tooltip.add(signal > 0
                ? Component.translatable(prefix + "pager_status_on", signal).withStyle(ChatFormatting.GREEN)
                : Component.translatable(prefix + "pager_status_quiet").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable(prefix + (isSilent(stack) ? "pager_silent" : "pager_beeping"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + "pager_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "pager_toggle").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "pager_configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "pager_copy").withStyle(ChatFormatting.GRAY));
    }
}
