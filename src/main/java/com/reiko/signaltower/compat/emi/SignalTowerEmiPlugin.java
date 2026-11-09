package com.reiko.signaltower.compat.emi;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.compat.RecipeViewerInfo;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.world.ModVillagers;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * v0.57.0: EMI support, loaded only by EMI. The same as the JEI plugin: an information page for every
 * item, and the Radio Operator's trades with the Radio Telegraph as their workstation.
 */
@EmiEntrypoint
public class SignalTowerEmiPlugin implements EmiPlugin {
    public static final EmiStack TELEGRAPH = EmiStack.of(ModRegistry.RADIO_TELEGRAPH_ITEM.get());
    public static final EmiRecipeCategory RADIO_OPERATOR = new EmiRecipeCategory(RecipeViewerInfo.RADIO_OPERATOR_TRADES, TELEGRAPH) {
        @Override
        public net.minecraft.network.chat.Component getName() {
            return net.minecraft.network.chat.Component.translatable(RecipeViewerInfo.TRADES_TITLE_KEY);
        }
    };

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(RADIO_OPERATOR);
        registry.addWorkstation(RADIO_OPERATOR, TELEGRAPH);
        List<ModVillagers.Trade> trades = RecipeViewerInfo.trades();
        for (int i = 0; i < trades.size(); i++) {
            registry.addRecipe(new RadioOperatorEmiRecipe(trades.get(i), i));
        }
        for (Item item : RecipeViewerInfo.items()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            registry.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(item)), List.of(RecipeViewerInfo.info(item)),
                    ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "/info/" + id.getPath())));
        }
    }
}
