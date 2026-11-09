package com.reiko.signaltower.compat.emi;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.compat.RecipeViewerInfo;
import com.reiko.signaltower.world.ModVillagers;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** v0.57.0: one of the Radio Operator's trades in EMI. */
public class RadioOperatorEmiRecipe implements EmiRecipe {
    private final ModVillagers.Trade trade;
    private final ResourceLocation id;
    private final List<EmiIngredient> inputs = new ArrayList<>();
    private final List<EmiStack> outputs;

    public RadioOperatorEmiRecipe(ModVillagers.Trade trade, int index) {
        this.trade = trade;
        this.id = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "/radio_operator/" + index);
        inputs.add(EmiStack.of(trade.costA()));
        if (!trade.costB().isEmpty()) {
            inputs.add(EmiStack.of(trade.costB()));
        }
        outputs = List.of(EmiStack.of(trade.result()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return SignalTowerEmiPlugin.RADIO_OPERATOR;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return 160;
    }

    @Override
    public int getDisplayHeight() {
        return 24;
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(EmiStack.of(trade.costA()), 0, 3);
        widgets.addSlot(trade.costB().isEmpty() ? EmiStack.EMPTY : EmiStack.of(trade.costB()), 20, 3);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 44, 4);
        widgets.addSlot(outputs.get(0), 72, 3).recipeContext(this);
        widgets.addText(RecipeViewerInfo.levelName(trade.level()), 96, 8, 0x404040, false);
    }
}
