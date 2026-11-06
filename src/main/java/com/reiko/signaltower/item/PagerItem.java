package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyNames;
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
 * Alerts also go in chat with the in-game day and time ("[Day 12, 06:30] Front Door: ..."), so
 * you can scroll back to see what went off; the pager's screen switches that off.
 * <p>
 * Catch-up: when its owner logs out, each pager notes the time; at the next login it says in chat
 * how many times its frequency turned on where the player is standing while they were away.
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

    /** True if alerts also go in chat (the default). */
    public static boolean isChatLog(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.PAGER_CHAT_LOG.get(), true);
    }

    public static void setChatLog(ItemStack stack, boolean chatLog) {
        if (chatLog) {
            stack.remove(ModRegistry.PAGER_CHAT_LOG.get());
        } else {
            stack.set(ModRegistry.PAGER_CHAT_LOG.get(), false);
        }
    }

    /**
     * The chat version of an alert: "[Day 12, 06:30] " in front, from the world's day time.
     * Days start at midnight here (the game's own day count starts at 6:00), so 23:59 is followed
     * by the next day's 00:00.
     */
    public static Component chatLine(long dayTime, Component alert) {
        Object[] when = dayAndTime(dayTime);
        return Component.translatable(KEY + "pager_log", when[0], when[1]).withStyle(ChatFormatting.GRAY)
                .append(alert.copy());
    }

    /** {day number, "HH:MM"} for a world day time, with days starting at midnight (day time 0 is 06:00). */
    public static Object[] dayAndTime(long dayTime) {
        long sinceMidnight = dayTime + 6000;
        long day = Math.floorDiv(sinceMidnight, 24000L) + 1;
        long ticks = Math.floorMod(sinceMidnight, 24000L);
        String time = String.format(java.util.Locale.ROOT, "%02d:%02d", ticks / 1000, ticks % 1000 * 60 / 1000);
        return new Object[]{day, time};
    }

    // ---- Catch-up ----

    /** Every pager in the player's inventory (main slots, armor, off-hand). */
    private static List<ItemStack> pagersOf(Player player) {
        List<ItemStack> pagers = new java.util.ArrayList<>();
        Container inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof PagerItem) {
                pagers.add(stack);
            }
        }
        return pagers;
    }

    /** At logout: each pager notes the time, so the next login can say what it missed. */
    public static void markAway(Player player, long gameTime) {
        for (ItemStack pager : pagersOf(player)) {
            pager.set(ModRegistry.PAGER_AWAY_SINCE.get(), gameTime);
        }
    }

    /**
     * At login: for each pager that noted a logout time, how many times its frequency turned on
     * where the player is standing since then, and when the last one was. Pagers that heard
     * nothing say nothing, unless none of them heard anything; then one line says so (except for
     * the owner of a singleplayer world, which doesn't run while they're away). The lines are
     * shown in chat and returned.
     */
    public static List<Component> catchUp(ServerLevel level, Player player, boolean worldRanWhileAway) {
        SignalNetwork network = SignalNetwork.get(level);
        List<Component> lines = new java.util.ArrayList<>();
        int pagersBack = 0;
        for (ItemStack pager : pagersOf(player)) {
            Long since = pager.get(ModRegistry.PAGER_AWAY_SINCE.get());
            if (since == null) {
                continue;
            }
            pager.remove(ModRegistry.PAGER_AWAY_SINCE.get());
            pagersBack++;
            int frequency = FrequencyTunerItem.getFrequency(pager);
            List<SignalNetwork.TurnOn> heard = network.turnOnsSince(frequency, since, player.blockPosition());
            if (heard.isEmpty()) {
                continue;
            }
            Object[] when = dayAndTime(heard.get(heard.size() - 1).dayTime());
            int times = heard.size();
            lines.add((times == 1
                    ? Component.translatable(KEY + "pager_catchup_once", label(pager), FrequencyNames.label(level, frequency), when[0], when[1])
                    : Component.translatable(KEY + (times >= SignalNetwork.MAX_HISTORY ? "pager_catchup_lots" : "pager_catchup_many"),
                            label(pager), FrequencyNames.label(level, frequency), times, when[0], when[1]))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (lines.isEmpty() && pagersBack > 0 && worldRanWhileAway) {
            lines.add((pagersBack == 1
                    ? Component.translatable(KEY + "pager_catchup_none_one")
                    : Component.translatable(KEY + "pager_catchup_none_many", pagersBack)).withStyle(ChatFormatting.GRAY));
        }
        for (Component line : lines) {
            player.displayClientMessage(line, false);
        }
        return lines;
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
        Component alert = Component.translatable(KEY + "pager_alert", label(stack), FrequencyNames.label(level, frequency), power)
                .withStyle(ChatFormatting.GOLD);
        if (holder instanceof Player player) {
            player.displayClientMessage(alert, true);
            if (isChatLog(stack)) {
                player.displayClientMessage(chatLine(level.getDayTime(), alert), false);
            }
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
                player.displayClientMessage(Component.translatable(KEY + "frequency_copied_pager", FrequencyNames.label(level, radio.getFrequency())), true);
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
                ClientHooks.openPagerScreen(hand, FrequencyTunerItem.getFrequency(stack), stack.getHoverName(),
                        RadioFavorites.get(stack), !isSilent(stack), isChatLog(stack));
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
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, FrequencyTunerItem.getFrequency(stack)))
                .withStyle(ChatFormatting.AQUA));
        RadioFavorites.addFavoritesLine(stack, tooltip);
        int signal = lastSignal(stack);
        tooltip.add(signal > 0
                ? Component.translatable(prefix + "pager_status_on", signal).withStyle(ChatFormatting.GREEN)
                : Component.translatable(prefix + "pager_status_quiet").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable(prefix + (isSilent(stack) ? "pager_silent" : "pager_beeping"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + (isChatLog(stack) ? "pager_chat_on" : "pager_chat_off"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(prefix + "pager_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "pager_toggle").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "pager_configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "pager_copy").withStyle(ChatFormatting.GRAY));
        RadioFavorites.addScrollHint(tooltip);
    }
}
