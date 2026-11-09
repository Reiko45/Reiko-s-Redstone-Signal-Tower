package com.reiko.signaltower.compat.jei;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.compat.RecipeViewerInfo;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.world.ModVillagers;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;

/** v0.57.0: the Radio Operator's trades in JEI: what they take, what they give, and at which level. */
public class RadioOperatorCategory implements IRecipeCategory<ModVillagers.Trade> {
    public static final RecipeType<ModVillagers.Trade> TYPE = RecipeType.create(SignalTower.MOD_ID, "radio_operator_trades", ModVillagers.Trade.class);

    private final IDrawable icon;

    public RadioOperatorCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModRegistry.RADIO_TELEGRAPH_ITEM.get());
    }

    @Override
    public RecipeType<ModVillagers.Trade> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(RecipeViewerInfo.TRADES_TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 24;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ModVillagers.Trade trade, IFocusGroup focuses) {
        builder.addInputSlot(1, 4).setStandardSlotBackground().addItemStack(trade.costA());
        var second = builder.addInputSlot(21, 4).setStandardSlotBackground();
        if (!trade.costB().isEmpty()) {
            second.addItemStack(trade.costB());
        }
        builder.addOutputSlot(73, 4).setStandardSlotBackground().addItemStack(trade.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ModVillagers.Trade trade, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(44, 4);
        builder.addText(java.util.List.of(RecipeViewerInfo.levelName(trade.level())), 64, 10).setPosition(96, 8).setColor(0xFF404040);
    }
}
