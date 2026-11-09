package com.reiko.signaltower.compat;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.world.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * v0.57.0: what the JEI and EMI plugins show, kept here without either of them so it works (and is
 * tested) the same for both. Every item in the mod gets an information page saying how it's used
 * ({@code info.reikos_signal_tower.<item>}), and the Radio Operator's trades get a page of their own.
 * JEI and EMI already show the crafting recipes by themselves.
 */
public final class RecipeViewerInfo {
    /** The trades page's title (and its id in JEI and EMI). */
    public static final ResourceLocation RADIO_OPERATOR_TRADES = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "radio_operator_trades");
    public static final String TRADES_TITLE_KEY = "recipe_viewer." + SignalTower.MOD_ID + ".radio_operator_trades";
    public static final String INFO_PREFIX = "info." + SignalTower.MOD_ID + ".";

    private RecipeViewerInfo() {
    }

    /** Every item the mod adds, in registry order. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (SignalTower.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                items.add(item);
            }
        }
        return items;
    }

    public static String infoKey(Item item) {
        return INFO_PREFIX + BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    /** The information page for {@code item}. */
    public static Component info(Item item) {
        return Component.translatable(infoKey(item));
    }

    /** "Novice" ... "Master", vanilla's names for the levels. */
    public static Component levelName(int level) {
        return Component.translatable("merchant.level." + level);
    }

    /** The Radio Operator's trades, Novice first. */
    public static List<ModVillagers.Trade> trades() {
        return ModVillagers.trades();
    }
}
