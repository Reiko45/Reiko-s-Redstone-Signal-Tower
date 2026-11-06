package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.EditGroupPayload;
import com.reiko.signaltower.network.SetFrequencyPrivacyPayload;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The player's friend groups, opened from the Frequency Tuner's screen: a list of groups on the
 * left, and the selected one's name, members and the frequencies using it on the right. Save
 * creates or changes a group (every private frequency using it changes with it), Delete removes
 * it, and Use here puts it in the tuner screen's Friends box as "#name".
 */
public class GroupsScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".groups.";
    private static final int WIDTH = 360;
    private static final int LIST_WIDTH = 112;
    private static final int ROWS = 8;
    private static final int ROW_HEIGHT = 14;

    private final Screen parent;
    private final Consumer<String> onUse;
    private List<FrequencyClaims.GroupInfo> groups = List.of();
    /** The selected group's name, or null for a new one. */
    private @Nullable String selected;
    /** After Save, the group to select once the server's answer comes back. */
    private @Nullable String selectAfterSave;
    private boolean confirmDelete;
    private EditBox nameBox;
    private EditBox membersBox;
    private Button saveButton;
    private Button useButton;
    private Button deleteButton;
    private int left;
    private int top;
    private int listTop;
    private int panelX;

    /** @param onUse gets a group's name when Use here is clicked; the screen then goes back to {@code parent} */
    public GroupsScreen(Screen parent, Consumer<String> onUse) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.onUse = onUse;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = Math.max(4, height / 2 - 82);
        listTop = top + 18;
        panelX = left + LIST_WIDTH + 8;
        int panelWidth = WIDTH - LIST_WIDTH - 8;
        String keepName = nameBox != null ? nameBox.getValue() : null;
        String keepMembers = membersBox != null ? membersBox.getValue() : null;

        nameBox = new EditBox(font, panelX, listTop + 10, panelWidth, 18, Component.translatable(KEY + "name"));
        nameBox.setMaxLength(FrequencyClaims.MAX_GROUP_NAME);
        nameBox.setHint(Component.translatable(KEY + "name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(text -> updateButtons());
        addRenderableWidget(nameBox);
        membersBox = new EditBox(font, panelX, listTop + 44, panelWidth, 18, Component.translatable(KEY + "members"));
        membersBox.setMaxLength(SetFrequencyPrivacyPayload.MAX_FRIENDS_TEXT);
        membersBox.setHint(Component.translatable(KEY + "members_hint").withStyle(ChatFormatting.DARK_GRAY));
        membersBox.setResponder(text -> updateButtons());
        addRenderableWidget(membersBox);

        int buttonsY = listTop + ROWS * ROW_HEIGHT + 8;
        saveButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "save"), button -> save())
                .bounds(left, buttonsY, 84, 20).build());
        useButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "use"), button -> use())
                .bounds(left + 92, buttonsY, 84, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "delete"), button -> delete())
                .tooltip(Tooltip.create(Component.translatable(KEY + "delete_tooltip")))
                .bounds(left + 184, buttonsY, 84, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + 276, buttonsY, 84, 20).build());

        groups = FrequencyClaims.clientGroups();
        if (keepName != null) {
            nameBox.setValue(keepName);
            membersBox.setValue(keepMembers);
        } else if (!groups.isEmpty()) {
            select(groups.get(0).name());
        } else {
            select(null);
        }
        updateButtons();
    }

    private @Nullable FrequencyClaims.GroupInfo selectedGroup() {
        if (selected == null) {
            return null;
        }
        for (FrequencyClaims.GroupInfo group : groups) {
            if (group.name().equalsIgnoreCase(selected)) {
                return group;
            }
        }
        return null;
    }

    /** Shows {@code name}'s group in the boxes, or empty boxes for a new group (null). */
    private void select(@Nullable String name) {
        selected = name;
        confirmDelete = false;
        FrequencyClaims.GroupInfo group = selectedGroup();
        if (group == null) {
            selected = null;
        }
        nameBox.setValue(group == null ? "" : group.name());
        membersBox.setValue(group == null ? "" : String.join(", ", group.members()));
        if (group == null) {
            setFocused(nameBox);
        }
        updateButtons();
    }

    /** True if the boxes differ from the selected group (or a new group has something typed). */
    private boolean edited() {
        FrequencyClaims.GroupInfo group = selectedGroup();
        if (group == null) {
            return !nameBox.getValue().isBlank();
        }
        return !nameBox.getValue().strip().equals(group.name()) || !lower(SetFrequencyPrivacyPayload.parseNames(membersBox.getValue()))
                .equals(lower(group.members()));
    }

    private static Set<String> lower(List<String> names) {
        Set<String> set = new HashSet<>();
        names.forEach(name -> set.add(name.toLowerCase(Locale.ROOT)));
        return set;
    }

    private void updateButtons() {
        if (saveButton == null) {
            return;
        }
        boolean full = selected == null && groups.size() >= FrequencyClaims.MAX_GROUPS;
        saveButton.active = edited() && !nameBox.getValue().isBlank() && !full;
        boolean usable = selectedGroup() != null && !edited();
        useButton.active = usable;
        useButton.setTooltip(Tooltip.create(Component.translatable(usable ? KEY + "use_tooltip" : KEY + "use_unsaved")));
        deleteButton.active = selectedGroup() != null;
        deleteButton.setMessage(Component.translatable(confirmDelete ? KEY + "delete_confirm" : KEY + "delete")
                .withStyle(confirmDelete ? ChatFormatting.RED : ChatFormatting.WHITE));
    }

    private void save() {
        FrequencyClaims.GroupInfo group = selectedGroup();
        selectAfterSave = EditGroupPayload.cleanName(nameBox.getValue());
        PacketDistributor.sendToServer(new EditGroupPayload(group == null ? "" : group.name(), nameBox.getValue(),
                membersBox.getValue(), false));
    }

    private void use() {
        FrequencyClaims.GroupInfo group = selectedGroup();
        if (group != null && minecraft != null) {
            onUse.accept(group.name());
            minecraft.setScreen(parent);
        }
    }

    private void delete() {
        FrequencyClaims.GroupInfo group = selectedGroup();
        if (group == null) {
            return;
        }
        if (!confirmDelete) {
            confirmDelete = true; // click again to delete
            updateButtons();
            return;
        }
        PacketDistributor.sendToServer(new EditGroupPayload(group.name(), "", "", true));
        selectAfterSave = null;
        confirmDelete = false;
    }

    @Override
    public void tick() {
        super.tick();
        List<FrequencyClaims.GroupInfo> now = FrequencyClaims.clientGroups();
        if (now != groups) {
            // The server answered (or something else changed them): show the new list.
            groups = now;
            if (selectAfterSave != null) {
                select(selectAfterSave);
                selectAfterSave = null;
            } else if (selected != null && selectedGroup() == null) {
                select(groups.isEmpty() ? null : groups.get(0).name());
            } else if (!edited()) {
                select(selected);
            }
            updateButtons();
        }
    }

    /** The row (0 to ROWS-1) under the mouse in the group list, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + LIST_WIDTH || mouseY < listTop) {
            return -1;
        }
        int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        return row < ROWS && row <= groups.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0 && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            select(row < groups.size() ? groups.get(row).name() : null);
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

    /** The frequencies of this player's that use the group called {@code name}, from the client's copy of the claims. */
    private List<Object> usedBy(String name) {
        List<Object> labels = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) {
            return labels;
        }
        for (int frequency : new TreeSet<>(FrequencyClaims.clientFrequencies())) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(frequency);
            if (claim != null && claim.owner().equals(minecraft.player.getUUID()) && claim.usesGroup(name)) {
                labels.add(FrequencyNames.label(null, frequency));
            }
        }
        return labels;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS; i++) {
            int rowTop = listTop + i * ROW_HEIGHT;
            boolean isSelected = i < groups.size() ? groups.get(i).name().equalsIgnoreCase(String.valueOf(selected))
                    : i == groups.size() && selected == null;
            int background = isSelected ? 0x6040A040 : i == hovered ? 0x40FFFFFF : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + LIST_WIDTH, rowTop + ROW_HEIGHT - 1, background);
            if (i < groups.size()) {
                FrequencyClaims.GroupInfo group = groups.get(i);
                String text = font.plainSubstrByWidth(group.name(), LIST_WIDTH - 26);
                graphics.drawString(font, text, left + 4, rowTop + 3, 0x55FFFF);
                String count = Integer.toString(group.members().size());
                graphics.drawString(font, count, left + LIST_WIDTH - 4 - font.width(count), rowTop + 3, 0xA0A0A0);
            } else if (i == groups.size()) {
                boolean full = groups.size() >= FrequencyClaims.MAX_GROUPS;
                graphics.drawString(font, Component.translatable(full ? KEY + "full_row" : KEY + "new"), left + 4, rowTop + 3,
                        full ? 0x808080 : 0x80FF80);
            }
        }

        graphics.drawString(font, Component.translatable(KEY + "name"), panelX, listTop, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "members"), panelX, listTop + 34, 0xC0C0C0);
        int panelWidth = WIDTH - LIST_WIDTH - 8;
        int infoY = listTop + 68;
        FrequencyClaims.GroupInfo group = selectedGroup();
        if (group != null) {
            List<Object> used = usedBy(group.name());
            Component usedLine = used.isEmpty()
                    ? Component.translatable(KEY + "unused").withStyle(ChatFormatting.GRAY)
                    : Component.translatable(KEY + "used_by", joined(used)).withStyle(ChatFormatting.GRAY);
            graphics.drawWordWrap(font, usedLine, panelX, infoY, panelWidth, 0xFFFFFF);
        }
        Component status;
        if (selected == null && groups.size() >= FrequencyClaims.MAX_GROUPS) {
            status = Component.translatable(KEY + "full", FrequencyClaims.MAX_GROUPS).withStyle(ChatFormatting.RED);
        } else if (edited()) {
            status = Component.translatable(group == null ? KEY + "status_new" : KEY + "status_changed").withStyle(ChatFormatting.YELLOW);
        } else if (group != null) {
            status = group.members().size() == 1
                    ? Component.translatable(KEY + "status_count_one").withStyle(ChatFormatting.GRAY)
                    : Component.translatable(KEY + "status_count", group.members().size()).withStyle(ChatFormatting.GRAY);
        } else if (groups.isEmpty()) {
            status = Component.translatable(KEY + "none").withStyle(ChatFormatting.GRAY);
        } else {
            status = Component.empty();
        }
        graphics.drawWordWrap(font, status, panelX, infoY + 24, panelWidth, 0xFFFFFF);
    }

    /** "2026 (Front Gate), 4242 (Vault)" as one component. */
    private static Component joined(List<Object> labels) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            Object label = labels.get(i);
            out.append(label instanceof Component component ? component : Component.literal(String.valueOf(label)));
        }
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
