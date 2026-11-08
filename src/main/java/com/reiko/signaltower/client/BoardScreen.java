package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.StatusBoardBlockEntity;
import com.reiko.signaltower.network.ConfigureBoardPayload;
import com.reiko.signaltower.network.OpenBoardScreenPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A Radio Status Board's screen (v0.43.0): four frequency boxes (empty: an empty row), each with
 * "..." for the directory and, to its right, what that row shows now (name and strength, "off" or
 * "encrypted"). Done or Enter saves (and makes you the one it hears as), Escape cancels.
 */
public class BoardScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".board.";

    private final OpenBoardScreenPayload state;
    private final EditBox[] boxes = new EditBox[StatusBoardBlockEntity.ROWS];
    private Button doneButton;
    private int pickedRow = -1;
    private int pickedFrequency;
    private int top;

    public BoardScreen(OpenBoardScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_status_board"));
        this.state = state;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 78;
        String[] keep = new String[StatusBoardBlockEntity.ROWS];
        for (int i = 0; i < keep.length; i++) {
            int saved = i < state.frequencies().size() ? state.frequencies().get(i) : 0;
            keep[i] = boxes[i] != null ? boxes[i].getValue() : saved > 0 ? Integer.toString(saved) : "";
        }
        if (pickedRow >= 0) {
            keep[pickedRow] = Integer.toString(pickedFrequency);
            pickedRow = -1;
        }
        for (int i = 0; i < boxes.length; i++) {
            int row = i;
            EditBox box = new EditBox(font, centerX - 100, top + 32 + i * 24, 104, 20, Component.translatable(KEY + "row", i + 1));
            box.setMaxLength(Frequency.MAX_DIGITS);
            box.setFilter(text -> text.chars().allMatch(Character::isDigit));
            box.setValue(keep[i]);
            box.setHint(Component.translatable(KEY + "empty").withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(text -> updateDone());
            boxes[i] = addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(row))
                    .tooltip(Tooltip.create(Component.translatable(KEY + "directory")))
                    .bounds(centerX + 6, top + 32 + i * 24, 16, 20).build());
        }
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 134, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 134, 100, 20).build());
        setInitialFocus(boxes[0]);
        updateDone();
    }

    private void openDirectory(int row) {
        if (minecraft != null) {
            OptionalInt current = parse(boxes[row]);
            minecraft.setScreen(new DirectoryScreen(this, current.orElse(-1), frequency -> {
                pickedRow = row;
                pickedFrequency = frequency;
            }, state.pos()));
        }
    }

    /** The frequency typed in a box: 0 if it's empty, empty optional if it isn't a frequency. */
    private static OptionalInt parse(EditBox box) {
        String text = box.getValue();
        if (text.isEmpty()) {
            return OptionalInt.of(0);
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateDone() {
        if (doneButton == null) {
            return;
        }
        boolean valid = true;
        for (EditBox box : boxes) {
            valid &= box != null && parse(box).isPresent();
        }
        doneButton.active = valid;
    }

    private void save() {
        List<Integer> rows = new ArrayList<>();
        for (EditBox box : boxes) {
            OptionalInt value = parse(box);
            if (value.isEmpty()) {
                return;
            }
            rows.add(value.getAsInt());
        }
        PacketDistributor.sendToServer(new ConfigureBoardPayload(state.pos(), rows));
        onClose();
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
        for (int i = 0; i < boxes.length; i++) {
            int y = top + 38 + i * 24;
            Component label = Component.translatable(KEY + "row", i + 1);
            graphics.drawString(font, label, centerX - 108 - font.width(label), y, 0xFFFFFF);
            OptionalInt typed = parse(boxes[i]);
            int saved = i < state.frequencies().size() ? state.frequencies().get(i) : 0;
            int x = centerX + 28;
            if (typed.isPresent() && typed.getAsInt() > 0) {
                String name = FrequencyNames.nameOf(null, typed.getAsInt());
                Component now;
                if (typed.getAsInt() != saved) {
                    now = Component.translatable(KEY + "not_saved").withStyle(ChatFormatting.DARK_GRAY);
                } else if ((state.deaf() >> i & 1) != 0) {
                    now = Component.translatable(KEY + "encrypted").withStyle(ChatFormatting.RED);
                } else {
                    int strength = i < state.strengths().size() ? state.strengths().get(i) : 0;
                    now = strength > 0 ? Component.literal(Integer.toString(strength)).withStyle(ChatFormatting.GREEN)
                            : Component.translatable(KEY + "off").withStyle(ChatFormatting.GRAY);
                }
                Component line = name != null ? Component.literal(font.plainSubstrByWidth(name, Math.max(0, width - x - 60)) + ": ")
                        .withStyle(ChatFormatting.AQUA).append(now) : now;
                graphics.drawString(font, line, x, y, 0xFFFFFF);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
