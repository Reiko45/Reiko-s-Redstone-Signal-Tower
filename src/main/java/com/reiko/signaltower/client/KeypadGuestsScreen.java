package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.network.KeypadGuestCodesPayload;
import com.reiko.signaltower.network.KeypadGuestListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Code Keypad owner's Guest codes screen (v0.38.0): the keypad's guest codes (name, code, uses
 * left, time left), and a row to add one: name, code, **Uses** (1, 3, 10, any) and **Time** (1
 * hour, 1 day, 1 week, any), **Add**. Click a row, then **Remove** to end it. The list is asked for
 * again every two seconds; time left counts down in between.
 */
public class KeypadGuestsScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".keypad_guests.";
    private static final int WIDTH = 340;
    private static final int ROWS = CodeKeypadBlockEntity.MAX_GUEST_CODES;
    private static final int ROW_HEIGHT = 14;
    private static final int REFRESH_TICKS = 40;

    private final Screen parent;
    private final BlockPos pos;
    private List<KeypadGuestListPayload.Info> codes = new ArrayList<>();
    /** Client ticks since the list arrived, for counting time left down. */
    private int ticksSinceList;
    private int ticks;
    private @Nullable String selected;
    private Component notice = Component.empty();
    private int usesIndex;
    private int timeIndex = 1;
    private int left;
    private int top;
    private EditBox nameBox;
    private EditBox codeBox;
    private Button usesButton;
    private Button timeButton;
    private Button addButton;
    private Button removeButton;

    public KeypadGuestsScreen(Screen parent, BlockPos pos) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.pos = pos;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    protected void init() {
        left = width / 2 - WIDTH / 2;
        top = Math.max(4, height / 2 - 115);
        String keepName = nameBox != null ? nameBox.getValue() : "";
        String keepCode = codeBox != null ? codeBox.getValue() : "";
        int formY = top + 166;
        nameBox = addRenderableWidget(new EditBox(font, left, formY, 90, 20, Component.translatable(KEY + "name_hint")));
        nameBox.setMaxLength(CodeKeypadBlockEntity.MAX_LABEL);
        nameBox.setHint(Component.translatable(KEY + "name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(keepName);
        nameBox.setResponder(text -> updateButtons());
        codeBox = addRenderableWidget(new EditBox(font, left + 94, formY, 56, 20, Component.translatable(KEY + "code_hint")));
        codeBox.setMaxLength(CodeKeypadBlockEntity.MAX_CODE);
        codeBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        codeBox.setHint(Component.translatable(KEY + "code_hint").withStyle(ChatFormatting.DARK_GRAY));
        codeBox.setValue(keepCode);
        codeBox.setResponder(text -> updateButtons());
        usesButton = addRenderableWidget(Button.builder(usesLabel(), b -> {
            usesIndex = (usesIndex + (hasShiftDown() ? CodeKeypadBlockEntity.GUEST_USES.length - 1 : 1)) % CodeKeypadBlockEntity.GUEST_USES.length;
            b.setMessage(usesLabel());
        }).bounds(left + 154, formY, 60, 20).build());
        timeButton = addRenderableWidget(Button.builder(timeLabel(), b -> {
            timeIndex = (timeIndex + (hasShiftDown() ? CodeKeypadBlockEntity.GUEST_SECONDS.length - 1 : 1)) % CodeKeypadBlockEntity.GUEST_SECONDS.length;
            b.setMessage(timeLabel());
        }).bounds(left + 218, formY, 78, 20).build());
        addButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "add"), b -> add())
                .bounds(left + 300, formY, 40, 20).build());
        removeButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "remove"), b -> remove())
                .bounds(left, top + 206, 168, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(left + 172, top + 206, 168, 20).build());
        setInitialFocus(nameBox);
        updateButtons();
        if (codes.isEmpty()) {
            PacketDistributor.sendToServer(KeypadGuestCodesPayload.list(pos));
        }
    }

    private Component usesLabel() {
        int uses = CodeKeypadBlockEntity.GUEST_USES[usesIndex];
        return Component.translatable(KEY + "uses", uses < 0 ? Component.translatable(KEY + "any") : Component.literal(Integer.toString(uses)));
    }

    private Component timeLabel() {
        int seconds = CodeKeypadBlockEntity.GUEST_SECONDS[timeIndex];
        String duration = seconds >= 604800 ? "week" : seconds >= 86400 ? "day" : "hour";
        return Component.translatable(KEY + "time", seconds <= 0 ? Component.translatable(KEY + "any")
                : Component.translatable("duration." + SignalTower.MOD_ID + "." + duration));
    }

    private void add() {
        if (addButton.active) {
            PacketDistributor.sendToServer(new KeypadGuestCodesPayload(pos, KeypadGuestCodesPayload.ADD, nameBox.getValue(), codeBox.getValue(),
                    CodeKeypadBlockEntity.GUEST_USES[usesIndex], CodeKeypadBlockEntity.GUEST_SECONDS[timeIndex]));
        }
    }

    private void remove() {
        if (selected != null) {
            PacketDistributor.sendToServer(new KeypadGuestCodesPayload(pos, KeypadGuestCodesPayload.REMOVE, selected, "", 0, 0));
        }
    }

    private void updateButtons() {
        if (addButton != null) {
            addButton.active = !nameBox.getValue().trim().isEmpty() && CodeKeypadBlockEntity.validCode(codeBox.getValue());
        }
        if (removeButton != null) {
            removeButton.active = selected != null;
        }
    }

    /** The server's answer: the list, and what the last change said. A code that was just added clears the form. */
    public void onList(KeypadGuestListPayload payload) {
        int before = codes.size();
        codes = new ArrayList<>(payload.codes());
        ticksSinceList = 0;
        if (!payload.notice().getString().isEmpty()) {
            notice = payload.notice();
            if (codes.size() > before) {
                nameBox.setValue("");
                codeBox.setValue("");
            }
        }
        if (selected != null && codes.stream().noneMatch(c -> c.label().equals(selected))) {
            selected = null;
        }
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        ticksSinceList++;
        if (ticks % REFRESH_TICKS == 0) {
            PacketDistributor.sendToServer(KeypadGuestCodesPayload.list(pos));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0 && row < codes.size()) {
            String label = codes.get(row).label();
            selected = label.equals(selected) ? null : label;
            notice = selected == null ? Component.empty()
                    : Component.translatable(KEY + "selected", selected).withStyle(ChatFormatting.GRAY);
            updateButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowAt(double mouseX, double mouseY) {
        int listTop = top + 48;
        if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop || mouseY >= listTop + ROWS * ROW_HEIGHT) {
            return -1;
        }
        return (int) ((mouseY - listTop) / ROW_HEIGHT);
    }

    /** Time left now, counted down since the list arrived (-1: no limit). */
    private long secondsLeft(KeypadGuestListPayload.Info info) {
        if (info.secondsLeft() < 0) {
            return -1;
        }
        return Math.max(0, info.secondsLeft() - ticksSinceList / 20);
    }

    private Component usesCell(KeypadGuestListPayload.Info info) {
        return info.usesLeft() < 0 ? Component.translatable(KEY + "no_limit").withStyle(ChatFormatting.GRAY)
                : info.usesLeft() == 0 ? Component.translatable(KEY + "used_up").withStyle(ChatFormatting.RED)
                : Component.literal(Integer.toString(info.usesLeft())).withStyle(ChatFormatting.WHITE);
    }

    private Component timeCell(KeypadGuestListPayload.Info info) {
        long seconds = secondsLeft(info);
        if (seconds < 0) {
            return Component.translatable(KEY + "no_limit").withStyle(ChatFormatting.GRAY);
        }
        if (seconds == 0) {
            return Component.translatable(KEY + "expired").withStyle(ChatFormatting.RED);
        }
        long minutes = (seconds + 59) / 60;
        if (minutes >= 24 * 60) {
            return Component.translatable(KEY + "days", minutes / (24 * 60), (minutes / 60) % 24);
        }
        if (minutes >= 60) {
            return Component.translatable(KEY + "hours", minutes / 60, minutes % 60);
        }
        return Component.translatable(KEY + "minutes", minutes);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        List<FormattedCharSequence> subtitle = font.split(Component.translatable(KEY + "subtitle"), WIDTH);
        for (int i = 0; i < Math.min(2, subtitle.size()); i++) {
            graphics.drawCenteredString(font, subtitle.get(i), centerX, top + 12 + i * 10, 0xA0A0A0);
        }
        int headerY = top + 37;
        graphics.drawString(font, Component.translatable(KEY + "col_name"), left + 4, headerY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "col_code"), left + 120, headerY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "col_uses"), left + 184, headerY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "col_time"), left + 262, headerY, 0xC0C0C0);
        int listTop = top + 48;
        graphics.fill(left, listTop, left + WIDTH, listTop + ROWS * ROW_HEIGHT, 0x60000000);
        if (codes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + ROWS * ROW_HEIGHT / 2 - 4, 0xFFFFFF);
        }
        KeypadGuestListPayload.Info hovered = null;
        for (int i = 0; i < Math.min(ROWS, codes.size()); i++) {
            KeypadGuestListPayload.Info info = codes.get(i);
            int y = listTop + i * ROW_HEIGHT;
            boolean runOut = info.usesLeft() == 0 || secondsLeft(info) == 0;
            if (info.label().equals(selected)) {
                graphics.fill(left, y, left + WIDTH, y + ROW_HEIGHT, 0x60FFFFFF);
            } else if (i % 2 == 1) {
                graphics.fill(left, y, left + WIDTH, y + ROW_HEIGHT, 0x20FFFFFF);
            }
            int textY = y + 3;
            graphics.drawString(font, font.plainSubstrByWidth(info.label(), 112), left + 4, textY, runOut ? 0x808080 : 0xFFFFFF);
            graphics.drawString(font, info.code(), left + 120, textY, runOut ? 0x407070 : 0x55FFFF);
            graphics.drawString(font, usesCell(info), left + 184, textY, 0xFFFFFF);
            graphics.drawString(font, timeCell(info), left + 262, textY, 0xFFFFFF);
            if (rowAt(mouseX, mouseY) == i) {
                hovered = info;
            }
        }
        Component line = notice.getString().isEmpty() ? Component.translatable(KEY + "hint").withStyle(ChatFormatting.GRAY) : notice;
        graphics.drawCenteredString(font, font.plainSubstrByWidth(line.getString(), WIDTH + 40).equals(line.getString()) ? line
                : Component.literal(font.plainSubstrByWidth(line.getString(), WIDTH + 40)).withStyle(line.getStyle()), centerX, top + 192, 0xFFFFFF);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.used() == 0
                    ? Component.translatable(KEY + "tip_unused")
                    : Component.translatable(KEY + "tip_used", hovered.used(), hovered.lastUser()), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
