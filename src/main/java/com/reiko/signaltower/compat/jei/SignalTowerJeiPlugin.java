package com.reiko.signaltower.compat.jei;

import com.reiko.signaltower.compat.RecipeViewerInfo;
import com.reiko.signaltower.registry.ModRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.57.0: JEI support, loaded only by JEI. An information page for every item, and the Radio
 * Operator's trades (with the Radio Telegraph, their workstation, as the catalyst). The crafting
 * recipes JEI finds by itself.
 */
@JeiPlugin
public class SignalTowerJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(com.reiko.signaltower.SignalTower.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RadioOperatorCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RadioOperatorCategory.TYPE, RecipeViewerInfo.trades());
        for (Item item : RecipeViewerInfo.items()) {
            registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK, RecipeViewerInfo.info(item));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModRegistry.RADIO_TELEGRAPH_ITEM.get()), RadioOperatorCategory.TYPE);
    }
}
