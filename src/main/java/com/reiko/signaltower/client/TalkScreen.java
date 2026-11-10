package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.WalkieTalkieItem;
import com.reiko.signaltower.network.WalkieTalkPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.WalkieTalkie;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * v0.64.0: the Walkie-Talkie's talk box: a line at the bottom of the screen, like chat, with the
 * radio's frequency above it. Enter says it (and closes); Escape closes. The game goes on behind it.
 */
public class TalkScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".talk.";
    private final InteractionHand hand;
    private EditBox input;

    public TalkScreen(InteractionHand hand) {
        super(Component.translatable(KEY + "title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        input = new EditBox(font, 4, height - 16, width - 8, 12, Component.translatable(KEY + "title"));
        input.setMaxLength(WalkieTalkie.MAX_LENGTH);
        input.setBordered(false);
        input.setHint(Component.translatable(KEY + "hint"));
        addRenderableWidget(input);
        setInitialFocus(input);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = input.getValue().strip();
            if (!text.isEmpty()) {
                PacketDistributor.sendToServer(new WalkieTalkPayload(hand == InteractionHand.OFF_HAND, text));
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(2, height - 18, width - 2, height - 4, minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
        Component label = stack.getItem() instanceof WalkieTalkieItem
                ? Component.translatable(KEY + "label", stack.getHoverName(), FrequencyNames.label(null, FrequencyTunerItem.getFrequency(stack)))
                : Component.translatable(KEY + "title");
        int labelWidth = font.width(label) + 8;
        graphics.fill(2, height - 31, 2 + labelWidth, height - 19, minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
        graphics.drawString(font, label, 6, height - 29, 0x55FF55);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No dimming or blur: like chat, the world stays in view.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
