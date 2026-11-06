package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.ConfigureConverterPayload;
import com.reiko.signaltower.network.OpenConverterScreenPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalInt;

/**
 * A Radio Converter's screen: the frequency it listens on and the one it sends on, plus what it's
 * converting right now (as it was when the screen opened). Tab moves between the boxes; Enter or
 * Done saves, Escape cancels.
 */
public class ConverterScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".converter.";

    private final OpenConverterScreenPayload state;
    private EditBox listenBox;
    private EditBox sendBox;
    private Button doneButton;
    private int top;

    public ConverterScreen(OpenConverterScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_converter"));
        this.state = state;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 66;
        listenBox = frequencyBox(centerX - 4, top + 34, state.listen(), KEY + "listens");
        sendBox = frequencyBox(centerX - 4, top + 60, state.send(), KEY + "sends");
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 122, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 122, 100, 20).build());
        setInitialFocus(listenBox);
        updateDoneButton();
    }

    private EditBox frequencyBox(int x, int y, int value, String narration) {
        EditBox box = new EditBox(font, x, y, 104, 20, Component.translatable(narration));
        box.setMaxLength(Frequency.MAX_DIGITS);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(Integer.toString(value));
        box.setResponder(text -> updateDoneButton());
        return addRenderableWidget(box);
    }

    private static OptionalInt parse(EditBox box) {
        String text = box.getValue();
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateDoneButton() {
        if (doneButton != null) {
            doneButton.active = parse(listenBox).isPresent() && parse(sendBox).isPresent();
        }
    }

    private void save() {
        OptionalInt listen = parse(listenBox);
        OptionalInt send = parse(sendBox);
        if (listen.isPresent() && send.isPresent()) {
            PacketDistributor.sendToServer(new ConfigureConverterPayload(state.pos(), listen.getAsInt(), send.getAsInt()));
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "range", Frequency.MIN, Frequency.MAX),
                centerX, top + 14, 0xA0A0A0);
        Component listens = Component.translatable(KEY + "listens");
        Component sends = Component.translatable(KEY + "sends");
        graphics.drawString(font, listens, centerX - 12 - font.width(listens), top + 40, 0xFFFFFF);
        graphics.drawString(font, sends, centerX - 12 - font.width(sends), top + 66, 0xFFFFFF);
        drawName(graphics, listenBox, top + 40);
        drawName(graphics, sendBox, top + 66);
        Component status = state.silenced()
                ? Component.translatable(KEY + "silenced", FrequencyNames.label(null, state.send())).withStyle(ChatFormatting.RED)
                : state.power() > 0
                ? Component.translatable(KEY + "converting", state.power(), FrequencyNames.label(null, state.listen()),
                        FrequencyNames.label(null, state.send())).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "quiet", FrequencyNames.label(null, state.listen())).withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, status, centerX, top + 90, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "reach", state.reach()), centerX, top + 104, 0xA0A0A0);
    }

    /** The typed frequency's name (if it has one) to the right of its box, cut to fit. */
    private void drawName(GuiGraphics graphics, EditBox box, int y) {
        OptionalInt frequency = parse(box);
        String name = frequency.isPresent() ? FrequencyNames.nameOf(null, frequency.getAsInt()) : null;
        int x = box.getX() + box.getWidth() + 6;
        if (name != null && width - x > 12) {
            graphics.drawString(font, font.plainSubstrByWidth(name, width - x - 4), x, y, 0x55FFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
