package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The antenna on a tower top (v0.45.0): none, or an Iron, Gold, Diamond or Netherite Antenna, each
 * multiplying the tower's reach (by 1.5, 2, 3 and 4 with the default config). It's a block state
 * property ({@link #PROPERTY}, "antenna") of the full-size transmitters (plain, timer, recorder),
 * repeaters and converters, so the client draws it and the item keeps it. Saved by name: keep the names.
 */
public enum AntennaTier implements StringRepresentable {
    NONE("none"),
    IRON("iron"),
    GOLD("gold"),
    DIAMOND("diamond"),
    NETHERITE("netherite");

    public static final EnumProperty<AntennaTier> PROPERTY = EnumProperty.create("antenna", AntennaTier.class);

    private final String name;

    AntennaTier(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** How many times its tower's reach this antenna gives (from the server config; 1 for none). */
    public double multiplier() {
        return switch (this) {
            case NONE -> 1.0;
            case IRON -> value(Config.IRON_ANTENNA_REACH);
            case GOLD -> value(Config.GOLD_ANTENNA_REACH);
            case DIAMOND -> value(Config.DIAMOND_ANTENNA_REACH);
            case NETHERITE -> value(Config.NETHERITE_ANTENNA_REACH);
        };
    }

    /** The config's value, or its default before the config is loaded (an item tooltip in the main menu). */
    private static double value(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue setting) {
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    /** "×1.5", "×3". */
    public String multiplierText() {
        double value = multiplier();
        String number = value == Math.floor(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
        return "×" + number;
    }

    /** "Diamond Antenna". */
    public Component label() {
        return Component.translatable("item." + SignalTower.MOD_ID + "." + name + "_antenna");
    }

    /** The antenna item for this tier (none for {@link #NONE}). */
    public @Nullable Item item() {
        return switch (this) {
            case NONE -> null;
            case IRON -> ModRegistry.IRON_ANTENNA.get();
            case GOLD -> ModRegistry.GOLD_ANTENNA.get();
            case DIAMOND -> ModRegistry.DIAMOND_ANTENNA.get();
            case NETHERITE -> ModRegistry.NETHERITE_ANTENNA.get();
        };
    }

    /** The antenna on a block in {@code state}: none for blocks that can't have one. */
    public static AntennaTier of(BlockState state) {
        return state.hasProperty(PROPERTY) ? state.getValue(PROPERTY) : NONE;
    }

    /** True if a block in {@code state} takes an antenna (a full-size tower top). */
    public static boolean fits(BlockState state) {
        return state.hasProperty(PROPERTY);
    }

    /** The antenna a tower top's item carries (in its block state component), or none. */
    public static AntennaTier of(ItemStack stack) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        AntennaTier tier = properties == null ? null : properties.get(PROPERTY);
        return tier == null ? NONE : tier;
    }

    /** Adds "Antenna: Diamond Antenna (×3 reach)" to a tower top item's tooltip, if it has one. */
    public static void addTooltipLine(ItemStack stack, List<Component> tooltip) {
        AntennaTier tier = of(stack);
        if (tier != NONE) {
            tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".antenna", tier.label(), tier.multiplierText())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
