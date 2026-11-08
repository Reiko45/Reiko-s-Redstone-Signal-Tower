package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.ConfigureLogicPayload;
import com.reiko.signaltower.network.OpenLogicScreenPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalInt;

/**
 * A Radio Logic Receiver's screen (v0.40.0): its two frequencies (each with a directory button),
 * the mode, and what it hears and outputs right now (as it was when the screen opened). Tab moves
 * between the boxes; Enter or Done saves, Escape cancels.
 */
public class LogicScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".logic.";

    private final OpenLogicScreenPayload state;
    private EditBox firstBox;
    private EditBox secondBox;
    private Button doneButton;
    private RadioReceiverBlockEntity.LogicMode mode;
    private int top;
    /** Frequencies picked in the directory for the two boxes, used when this screen is shown again. */
    private OptionalInt pickedFirst = OptionalInt.empty();
    private OptionalInt pickedSecond = OptionalInt.empty();

    public LogicScreen(OpenLogicScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_logic_receiver"));
        this.state = state;
        this.mode = state.modeValue();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 78;
        String keepFirst = firstBox != null ? firstBox.getValue() : Integer.toString(state.first());
        String keepSecond = secondBox != null ? secondBox.getValue() : Integer.toString(state.second());
        if (pickedFirst.isPresent()) keepFirst = Integer.toString(pickedFirst.getAsInt());
        if (pickedSecond.isPresent()) keepSecond = Integer.toString(pickedSecond.getAsInt());
        pickedFirst = OptionalInt.empty();
        pickedSecond = OptionalInt.empty();
        firstBox = frequencyBox(centerX - 4, top + 34, keepFirst, KEY + "first");
        secondBox = frequencyBox(centerX - 4, top + 84, keepSecond, KEY + "second");
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(true))
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory_first")))
                .bounds(centerX + 102, top + 34, 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(false))
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory_second")))
                .bounds(centerX + 102, top + 84, 16, 20).build());
        addRenderableWidget(CycleButton.<RadioReceiverBlockEntity.LogicMode>builder(value -> Component.translatable(KEY + "mode_" + value.key()))
                .withValues(RadioReceiverBlockEntity.LogicMode.values())
                .withInitialValue(mode)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "mode_" + value.key() + "_tip")))
                .displayOnlyValue()
                .create(centerX - 50, top + 59, 100, 20, Component.translatable(KEY + "mode"), (button, value) -> mode = value));
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 138, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 138, 100, 20).build());
        setInitialFocus(firstBox);
        updateDoneButton();
    }

    /** Opens the frequency directory for the first box ({@code first}) or the second. */
    private void openDirectory(boolean first) {
        if (minecraft != null) {
            OptionalInt current = parse(first ? firstBox : secondBox);
            minecraft.setScreen(new DirectoryScreen(this, current.orElse(-1), frequency -> {
                if (first) {
                    pickedFirst = OptionalInt.of(frequency);
                } else {
                    pickedSecond = OptionalInt.of(frequency);
                }
            }, state.pos()));
        }
    }

    private EditBox frequencyBox(int x, int y, String value, String narration) {
        EditBox box = new EditBox(font, x, y, 104, 20, Component.translatable(narration));
        box.setMaxLength(Frequency.MAX_DIGITS);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(value);
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
            doneButton.active = parse(firstBox).isPresent() && parse(secondBox).isPresent();
        }
    }

    private void save() {
        OptionalInt first = parse(firstBox);
        OptionalInt second = parse(secondBox);
        if (first.isPresent() && second.isPresent()) {
            PacketDistributor.sendToServer(new ConfigureLogicPayload(state.pos(), first.getAsInt(), second.getAsInt(), mode.ordinal()));
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
        graphics.drawCenteredString(font, Component.translatable(KEY + "explain"), centerX, top + 14, 0xA0A0A0);
        Component first = Component.translatable(KEY + "first");
        Component second = Component.translatable(KEY + "second");
        graphics.drawString(font, first, centerX - 12 - font.width(first), top + 40, 0xFFFFFF);
        graphics.drawString(font, second, centerX - 12 - font.width(second), top + 90, 0xFFFFFF);
        drawName(graphics, firstBox, top + 40);
        drawName(graphics, secondBox, top + 90);
        graphics.drawCenteredString(font, hearingLine(), centerX, top + 112, 0xFFFFFF);
        graphics.drawCenteredString(font, outputLine(), centerX, top + 124, 0xFFFFFF);
    }

    /** "Hearing 15 on 2026 and nothing on 3001", or a red line if it can't hear one of them. */
    private Component hearingLine() {
        if ((state.quiet() & OpenLogicScreenPayload.DEAF_FIRST) != 0 || (state.quiet() & OpenLogicScreenPayload.DEAF_SECOND) != 0) {
            int deaf = (state.quiet() & OpenLogicScreenPayload.DEAF_FIRST) != 0 ? state.first() : state.second();
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(deaf);
            return Component.translatable(KEY + "deaf", FrequencyNames.label(null, deaf), claim != null ? claim.ownerName() : "?")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable(KEY + "hearing", strength(state.heardFirst()), FrequencyNames.label(null, state.first()),
                strength(state.heardSecond()), FrequencyNames.label(null, state.second())).withStyle(ChatFormatting.GRAY);
    }

    private static Component strength(int heard) {
        return heard > 0 ? Component.literal(Integer.toString(heard)).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "nothing");
    }

    private Component outputLine() {
        return state.output() > 0
                ? Component.translatable(KEY + "output", state.output()).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "output_off").withStyle(ChatFormatting.GRAY);
    }

    /** The typed frequency's name (if it has one) to the right of its box, cut to fit. */
    private void drawName(GuiGraphics graphics, EditBox box, int y) {
        OptionalInt frequency = parse(box);
        String name = frequency.isPresent() ? FrequencyNames.nameOf(null, frequency.getAsInt()) : null;
        int x = box.getX() + box.getWidth() + 24;
        if (name != null && width - x > 12) {
            graphics.drawString(font, font.plainSubstrByWidth(name, width - x - 4), x, y, 0x55FFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
