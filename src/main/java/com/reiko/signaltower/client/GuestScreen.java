package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.GuestPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Guests (v0.36.0), opened from the audit of one of your private frequencies: who has a guest
 * pass on it and how long it has left. Type a player's name and pick 1 hour, 1 day or 1 week to
 * give one (or, with a guest picked, to renew theirs); Remove ends it early. A guest can send on
 * it (and hear it, if it's encrypted) until the pass runs out, real time.
 */
public class GuestScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".guests.";
    private static final int WIDTH = 280;
    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 14;

    private final Screen parent;
    private final int frequency;
    private List<FrequencyClaims.GuestInfo> guests = List.of();
    private @Nullable String selected;
    private @Nullable Component notice;
    private int noticeTicks;
    private int left;
    private int top;
    private int listTop;
    private EditBox nameBox;
    private final List<Button> durationButtons = new ArrayList<>();
    private Button removeButton;

    public GuestScreen(Screen parent, int frequency) {
        super(Component.translatable(KEY + "title", FrequencyNames.label(null, frequency)));
        this.parent = parent;
        this.frequency = frequency;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = Math.max(4, height / 2 - 100);
        listTop = top + 38;
        int inputY = listTop + ROWS * ROW_HEIGHT + 8;
        String typed = nameBox == null ? "" : nameBox.getValue();
        nameBox = addRenderableWidget(new EditBox(font, left, inputY, 118, 20, Component.translatable(KEY + "name")));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable(KEY + "name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(typed);
        nameBox.setResponder(text -> updateButtons());
        durationButtons.clear();
        int x = left + 122;
        for (int seconds : FrequencyClaims.GUEST_SECONDS) {
            durationButtons.add(addRenderableWidget(Button.builder(GuestPayload.duration(seconds), button -> give(seconds))
                    .tooltip(Tooltip.create(Component.translatable(KEY + "give_tooltip", GuestPayload.duration(seconds))))
                    .bounds(x, inputY, 50, 20).build()));
            x += 54;
        }
        int buttonsY = inputY + 40;
        removeButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "remove"), button -> remove())
                .bounds(width / 2 - 104, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 + 4, buttonsY, 100, 20).build());
        refresh();
    }

    /** The guests, from the client's copy of the claim (it changes as passes are given, renewed and run out). */
    private void refresh() {
        FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(frequency);
        List<FrequencyClaims.GuestInfo> now = claim == null ? List.of() : new ArrayList<>(claim.guests());
        now = new ArrayList<>(now);
        now.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
        guests = now;
        if (selected != null && guests.stream().noneMatch(guest -> guest.name().equalsIgnoreCase(selected))) {
            selected = null;
        }
        updateButtons();
    }

    private void updateButtons() {
        if (removeButton == null) {
            return;
        }
        boolean named = !nameBox.getValue().isBlank();
        durationButtons.forEach(button -> button.active = named);
        removeButton.active = selected != null;
    }

    private void give(int seconds) {
        String name = nameBox.getValue().strip();
        if (!name.isEmpty()) {
            PacketDistributor.sendToServer(new GuestPayload(frequency, name, seconds));
        }
    }

    private void remove() {
        if (selected != null) {
            PacketDistributor.sendToServer(new GuestPayload(frequency, selected, 0));
        }
    }

    /** The server's answer to the last change. */
    public void notice(Component message) {
        notice = message;
        noticeTicks = 160;
    }

    @Override
    public void tick() {
        super.tick();
        if (noticeTicks > 0 && --noticeTicks == 0) {
            notice = null;
        }
        refresh();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop) {
            return -1;
        }
        int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        return row < ROWS && row < guests.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0) {
            selected = guests.get(row).name();
            nameBox.setValue(selected);
            updateButtons();
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    /** "6 days left", "23 hours left", "40 minutes left", "30 seconds left". */
    static Component timeLeft(long untilMillis) {
        long seconds = Math.max(0, (untilMillis - System.currentTimeMillis()) / 1000);
        if (seconds >= 2 * 86400) {
            return Component.translatable(KEY + "left_days", seconds / 86400);
        }
        if (seconds >= 2 * 3600) {
            return Component.translatable(KEY + "left_hours", seconds / 3600);
        }
        if (seconds >= 120) {
            return Component.translatable(KEY + "left_minutes", seconds / 60);
        }
        return Component.translatable(KEY + "left_seconds", seconds);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY), centerX, top + 12, 0xFFFFFF);
        graphics.drawString(font, Component.translatable(KEY + "heading", guests.size(), FrequencyClaims.MAX_GUESTS),
                left + 4, listTop - 11, 0xC0C0C0);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS; i++) {
            int rowTop = listTop + i * ROW_HEIGHT;
            boolean isSelected = i < guests.size() && guests.get(i).name().equals(selected);
            int background = isSelected ? 0x60FFC21A : i == hovered ? 0x40FFFFFF : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + WIDTH, rowTop + ROW_HEIGHT - 1, background);
            if (i >= guests.size()) {
                continue;
            }
            FrequencyClaims.GuestInfo guest = guests.get(i);
            graphics.drawString(font, (isSelected ? "» " : "") + guest.name(), left + 4, rowTop + 3, isSelected ? 0xFFD54F : 0xFFFFFF);
            Component leftText = timeLeft(guest.untilMillis());
            graphics.drawString(font, leftText, left + WIDTH - 4 - font.width(leftText), rowTop + 3, 0xA0E0A0);
        }
        if (guests.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "none").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + ROW_HEIGHT + 3, 0xFFFFFF);
        }

        int noticeY = listTop + ROWS * ROW_HEIGHT + 8 + 24;
        Component line = notice != null ? notice
                : selected != null ? Component.translatable(KEY + "hint_selected", selected).withStyle(ChatFormatting.GRAY)
                : Component.translatable(KEY + "hint").withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, line, centerX, noticeY + 2, 0xFFFFFF);
    }
}
