package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.HandOverFrequencyPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand Over (v0.34.0), opened from the audit of one of your private frequencies: pick one of its
 * friends or group members and click Hand over twice. They become its owner, and everyone else on
 * it, you included, stays on it as a friend (its groups' members as single friends, since groups
 * belong to their owner). Sends {@link HandOverFrequencyPayload} and goes back to the directory.
 */
public class HandOverScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".hand_over.";
    private static final int WIDTH = 240;
    private static final int ROWS = 8;
    private static final int ROW_HEIGHT = 14;

    private final Screen parent;
    private final Screen afterwards;
    private final int frequency;
    private List<String> members = List.of();
    private List<String> friends = List.of();
    private boolean usesGroups;
    private @Nullable String selected;
    private boolean confirming;
    private int scroll;
    private int left;
    private int top;
    private int listTop;
    private Button handOverButton;

    /** {@code afterwards}: where to go once it's handed over (the audit is no longer yours then). */
    public HandOverScreen(Screen parent, Screen afterwards, int frequency) {
        super(Component.translatable(KEY + "title", FrequencyNames.label(null, frequency)));
        this.parent = parent;
        this.afterwards = afterwards;
        this.frequency = frequency;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = Math.max(4, height / 2 - 105);
        listTop = top + 38;
        int buttonsY = listTop + ROWS * ROW_HEIGHT + 46;
        handOverButton = addRenderableWidget(Button.builder(Component.empty(), button -> handOver())
                .bounds(width / 2 - 104, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 + 4, buttonsY, 100, 20).build());
        refresh();
    }

    /** Reads the frequency's members from the client's copy of its claim (it can change while open). */
    private void refresh() {
        FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(frequency);
        List<String> now = claim == null ? List.of() : new ArrayList<>(claim.members());
        now.sort(String.CASE_INSENSITIVE_ORDER);
        if (!now.equals(members)) {
            members = now;
            scroll = Math.max(0, Math.min(scroll, members.size() - ROWS));
            if (selected != null && members.stream().noneMatch(selected::equalsIgnoreCase)) {
                selected = null;
                confirming = false;
            }
        }
        friends = claim == null ? List.of() : claim.friends();
        usesGroups = claim != null && !claim.groups().isEmpty();
        updateButton();
    }

    private boolean tooMany() {
        return members.size() > FrequencyClaims.MAX_FRIENDS;
    }

    private void updateButton() {
        if (handOverButton == null) {
            return;
        }
        handOverButton.active = selected != null && !tooMany();
        handOverButton.setMessage(confirming
                ? Component.translatable(KEY + "confirm").withStyle(ChatFormatting.RED)
                : Component.translatable(KEY + "button"));
    }

    private void handOver() {
        if (selected == null || tooMany()) {
            return;
        }
        if (!confirming) {
            confirming = true;
            updateButton();
            return;
        }
        PacketDistributor.sendToServer(new HandOverFrequencyPayload(frequency, selected));
        if (minecraft != null) {
            minecraft.setScreen(afterwards);
        }
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop) {
            return -1;
        }
        int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        return row < ROWS && scroll + row < members.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0) {
            String name = members.get(scroll + row);
            if (!name.equals(selected)) {
                selected = name;
                confirming = false;
            }
            updateButton();
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (members.size() > ROWS && scrollY != 0) {
            scroll = Math.max(0, Math.min(members.size() - ROWS, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY), centerX, top + 12, 0xFFFFFF);
        graphics.drawString(font, Component.translatable(KEY + "heading"), left + 4, listTop - 11, 0xC0C0C0);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS; i++) {
            int rowTop = listTop + i * ROW_HEIGHT;
            int index = scroll + i;
            boolean isSelected = index < members.size() && members.get(index).equals(selected);
            int background = isSelected ? 0x60FFC21A : i == hovered ? 0x40FFFFFF : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + WIDTH, rowTop + ROW_HEIGHT - 1, background);
            if (index >= members.size()) {
                continue;
            }
            String name = members.get(index);
            graphics.drawString(font, (isSelected ? "» " : "") + name, left + 4, rowTop + 3, isSelected ? 0xFFD54F : 0xFFFFFF);
            Component role = Component.translatable(KEY + (friends.contains(name) ? "role_friend" : "role_group"));
            graphics.drawString(font, role, left + WIDTH - 4 - font.width(role), rowTop + 3, 0x909090);
        }
        if (members.size() > ROWS) {
            int barHeight = Math.max(8, ROWS * ROW_HEIGHT * ROWS / members.size());
            int barTop = listTop + (ROWS * ROW_HEIGHT - barHeight) * scroll / (members.size() - ROWS);
            graphics.fill(left + WIDTH - 2, barTop, left + WIDTH, barTop + barHeight, 0xC0A0A0A0);
        }

        int below = listTop + ROWS * ROW_HEIGHT + 4;
        Component line = members.isEmpty()
                ? Component.translatable(KEY + "none").withStyle(ChatFormatting.GRAY)
                : tooMany()
                ? Component.translatable(KEY + "too_many", members.size(), FrequencyClaims.MAX_FRIENDS).withStyle(ChatFormatting.RED)
                : selected == null
                ? Component.translatable(KEY + "pick").withStyle(ChatFormatting.GRAY)
                : Component.translatable(KEY + "what", selected).withStyle(ChatFormatting.GOLD);
        for (var part : font.split(line, WIDTH + 80)) {
            graphics.drawCenteredString(font, part, centerX, below, 0xFFFFFF);
            below += 10;
        }
        if (usesGroups && !members.isEmpty() && !tooMany()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "groups").withStyle(ChatFormatting.DARK_GRAY),
                    centerX, below, 0xFFFFFF);
        }
    }
}
