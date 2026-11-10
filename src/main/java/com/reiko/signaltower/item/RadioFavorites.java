package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Sneak + scroll and the 5 favorite frequencies, shared by the items with a single frequency: the
 * Frequency Tuner, the Remote Control and the Radio Pager. (The Multi-Button Remote uses sneak +
 * scroll to pick its button instead.) Favorites are stored in the {@code tuner_presets} component,
 * whose name dates from when only the tuner had them.
 */
public final class RadioFavorites {
    private RadioFavorites() {}

    /** True for the tuner, the Remote Control and the pager. */
    public static boolean isScrollable(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof FrequencyTunerItem || item instanceof RemoteControlItem || item instanceof PagerItem
                || item instanceof SignalMeterItem || item instanceof WalkieTalkieItem;
    }

    public static TunerPresets get(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.TUNER_PRESETS.get(), TunerPresets.EMPTY);
    }

    /** Stores the favorites, or removes the data entirely when every slot is empty. */
    public static void set(ItemStack stack, TunerPresets favorites) {
        if (favorites.hasAny()) {
            stack.set(ModRegistry.TUNER_PRESETS.get(), favorites);
        } else {
            stack.remove(ModRegistry.TUNER_PRESETS.get());
        }
    }

    /** Sets the item's frequency the way that item needs (a pager also forgets its last signal). */
    public static void setFrequency(ItemStack stack, int frequency) {
        if (stack.getItem() instanceof PagerItem) {
            PagerItem.setFrequency(stack, frequency);
        } else {
            FrequencyTunerItem.setFrequency(stack, frequency);
        }
    }

    /**
     * Sneak + scroll: steps the frequency up or down by 1 (stopping at the ends), or with
     * {@code favorites} (Ctrl held) jumps to the next favorite (scrolling down) or the previous one
     * (up). Returns the message to show, which names the item: "Remote Control: frequency 42".
     */
    public static Component scroll(ItemStack stack, boolean up, boolean favorites) {
        return scroll(null, stack, up, favorites);
    }

    /** {@link #scroll(ItemStack, boolean, boolean)}, with the frequency's name (if it has one) looked up in {@code level}. */
    public static Component scroll(@Nullable Level level, ItemStack stack, boolean up, boolean favorites) {
        int current = FrequencyTunerItem.getFrequency(stack);
        String key = "message." + SignalTower.MOD_ID + ".";
        if (favorites) {
            OptionalInt next = get(stack).next(current, up);
            if (next.isEmpty()) {
                return Component.translatable(key + "no_favorites").withStyle(ChatFormatting.YELLOW);
            }
            setFrequency(stack, next.getAsInt());
        } else {
            setFrequency(stack, Frequency.clamp((long) current + (up ? 1 : -1)));
        }
        int frequency = FrequencyTunerItem.getFrequency(stack);
        int favorite = get(stack).indexOf(frequency);
        return (favorite >= 0
                ? Component.translatable(key + "frequency_scrolled_favorite", stack.getHoverName(), FrequencyNames.label(level, frequency), favorite + 1)
                : Component.translatable(key + "frequency_scrolled", stack.getHoverName(), FrequencyNames.label(level, frequency))).withStyle(ChatFormatting.AQUA);
    }

    /** The "Favorites: 5, 42, 77" line (if there are any), for right under the frequency in a tooltip. */
    public static void addFavoritesLine(ItemStack stack, List<Component> tooltip) {
        TunerPresets favorites = get(stack);
        if (favorites.hasAny()) {
            String list = favorites.slots().stream().filter(f -> f != 0).map(String::valueOf).collect(Collectors.joining(", "));
            tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".favorites", list)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** The "Sneak + scroll..." hint line. */
    public static void addScrollHint(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".frequency_scroll").withStyle(ChatFormatting.GRAY));
    }
}
