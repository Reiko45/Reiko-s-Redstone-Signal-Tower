package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.SetFrequencyNotePayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A frequency's note, opened with the Note button in the Frequency Tuner's screen: one box (up to
 * {@link FrequencyNotes#MAX_LENGTH} characters), Save and Back. Save sends it at once (an empty
 * box removes the note); everyone on the server sees it in the directory, Jade and item tooltips.
 * On someone else's private frequency the box is read-only for non-members.
 * <p>
 * On a private frequency, the "Who sees it" switch (v0.32.0) keeps the note to its members: only
 * the owner, friends, group members and server operators get it.
 */
public class NoteScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".note.";
    private static final int BOX_WIDTH = 260;

    private final Screen parent;
    private final int frequency;
    /** Null if the player may change it; otherwise the private frequency's owner, for the line saying why not. */
    private final @Nullable String lockedBy;
    /** True if the frequency is private, so its note can be kept to members. */
    private final boolean privateFrequency;
    private EditBox box;
    private Button saveButton;
    private Button whoButton;
    private boolean membersOnly;
    private boolean whoEdited;
    private int top;

    public NoteScreen(Screen parent, int frequency, @Nullable String lockedBy) {
        this(parent, frequency, lockedBy, false);
    }

    public NoteScreen(Screen parent, int frequency, @Nullable String lockedBy, boolean privateFrequency) {
        super(Component.translatable(KEY + "title", FrequencyNames.label(null, frequency)));
        this.parent = parent;
        this.frequency = frequency;
        this.lockedBy = lockedBy;
        this.privateFrequency = privateFrequency;
    }

    @Override
    protected void init() {
        top = Math.max(4, height / 2 - 50);
        String keep = box != null ? box.getValue() : null;
        box = new EditBox(font, (width - BOX_WIDTH) / 2, top + 22, BOX_WIDTH, 20, Component.translatable(KEY + "box"));
        box.setMaxLength(FrequencyNotes.MAX_LENGTH);
        box.setHint(Component.translatable(KEY + "hint").withStyle(ChatFormatting.DARK_GRAY));
        String current = FrequencyNotes.noteOf(null, frequency);
        box.setValue(keep != null ? keep : current == null ? "" : current);
        box.moveCursorToStart(false);
        box.setEditable(lockedBy == null);
        box.setResponder(text -> updateSave());
        addRenderableWidget(box);
        if (lockedBy == null) {
            setInitialFocus(box);
        }

        if (!whoEdited) {
            membersOnly = privateFrequency && FrequencyNotes.clientMembersOnly(frequency);
        }
        whoButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            membersOnly = !membersOnly;
            whoEdited = true;
            updateSave();
        }).tooltip(Tooltip.create(Component.translatable(privateFrequency ? KEY + "who_tooltip" : KEY + "who_tooltip_public")))
                .bounds(width / 2 - 104, top + 70, 208, 20).build());

        int buttonsY = top + 98;
        saveButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "save"), button -> save())
                .bounds(width / 2 - 104, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 + 4, buttonsY, 100, 20).build());
        updateSave();
    }

    /** True if the box (or the switch) differs from the note the client knows. */
    private boolean edited() {
        String current = FrequencyNotes.noteOf(null, frequency);
        String typed = FrequencyNotes.clean(box.getValue());
        return !typed.equals(current == null ? "" : current)
                || !typed.isEmpty() && membersOnly != FrequencyNotes.clientMembersOnly(frequency);
    }

    private void updateSave() {
        saveButton.active = lockedBy == null && edited();
        whoButton.active = lockedBy == null && privateFrequency;
        whoButton.setMessage(Component.translatable(KEY + "who",
                Component.translatable(KEY + (membersOnly ? "who_members" : "who_everyone"))
                        .withStyle(membersOnly ? ChatFormatting.GOLD : ChatFormatting.WHITE)));
    }

    private void save() {
        if (lockedBy == null && edited()) {
            PacketDistributor.sendToServer(new SetFrequencyNotePayload(frequency, box.getValue(), privateFrequency && membersOnly));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && saveButton.active) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        Component line = lockedBy != null
                ? Component.translatable(KEY + "locked", lockedBy).withStyle(ChatFormatting.RED)
                : membersOnly && privateFrequency
                ? Component.translatable(KEY + "shared_members", FrequencyNotes.MAX_LENGTH).withStyle(ChatFormatting.GOLD)
                : Component.translatable(KEY + "shared", FrequencyNotes.MAX_LENGTH).withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, line, width / 2, top + 52, 0xFFFFFF);
    }
}
