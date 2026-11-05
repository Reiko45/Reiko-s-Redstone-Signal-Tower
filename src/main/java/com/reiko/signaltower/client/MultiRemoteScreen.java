package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.network.ConfigureMultiRemotePayload;
import com.reiko.signaltower.radio.Frequency;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Multi-Button Remote's keypad. Each row is one button: click its number to make it the one
 * right-click presses, give it a name, type its frequency, switch its mode, set its pulse length
 * (click for longer, sneak-click for shorter), or press it right now. Pressing also saves the settings. Done saves
 * and closes, Cancel throws away unsaved edits.
 */
public class MultiRemoteScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".multi_remote";
    private static final int COLUMNS_WIDTH = 348;

    private final InteractionHand hand;
    private RemoteButtons buttons;
    private final Button[] selectButtons = new Button[RemoteButtons.COUNT];
    private final Button[] modeButtons = new Button[RemoteButtons.COUNT];
    private final Button[] lengthButtons = new Button[RemoteButtons.COUNT];

    public MultiRemoteScreen(InteractionHand hand, RemoteButtons buttons, Component title) {
        super(title);
        this.hand = hand;
        this.buttons = buttons;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 46;

        // Columns: [#] [name] [frequency] [mode] [pulse length] [Press], 348 wide in total.
        int left = centerX - COLUMNS_WIDTH / 2;
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            final int index = i;
            int y = top + i * 24;

            selectButtons[i] = addRenderableWidget(Button.builder(selectLabel(i), b -> {
                buttons = buttons.withSelected(index);
                refreshLabels();
            }).bounds(left, y, 24, 20).build());

            EditBox nameBox = new EditBox(font, left + 28, y, 96, 20, Component.translatable(KEY + ".name", i + 1));
            nameBox.setMaxLength(RemoteButtons.MAX_NAME_LENGTH);
            nameBox.setValue(buttons.button(i).name());
            nameBox.setHint(buttons.button(i).withName("").label(i).copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            nameBox.setResponder(text -> buttons = buttons.withButton(index, buttons.button(index).withName(text)));
            addRenderableWidget(nameBox);

            EditBox box = new EditBox(font, left + 128, y, 64, 20, Component.translatable(KEY + ".frequency", i + 1));
            box.setMaxLength(Frequency.MAX_DIGITS);
            box.setFilter(text -> text.chars().allMatch(Character::isDigit));
            box.setValue(Integer.toString(buttons.button(i).frequency()));
            box.setResponder(text -> {
                if (!text.isEmpty()) {
                    long value = Long.parseLong(text);
                    if (value >= Frequency.MIN && value <= Frequency.MAX) {
                        buttons = buttons.withButton(index, buttons.button(index).withFrequency((int) value));
                    }
                }
            });
            addRenderableWidget(box);

            modeButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                RemoteButtons.Button old = buttons.button(index);
                buttons = buttons.withButton(index, old.withToggle(!old.toggle()));
                refreshLabels();
            }).bounds(left + 196, y, 52, 20).build());

            lengthButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                RemoteButtons.Button old = buttons.button(index);
                buttons = buttons.withButton(index, old.withPulseTicks(PulseLength.next(old.pulseTicks(), Screen.hasShiftDown())));
                refreshLabels();
            }).bounds(left + 252, y, 44, 20).build());

            addRenderableWidget(Button.builder(Component.translatable(KEY + ".press"), b -> send(index))
                    .bounds(left + 300, y, 48, 20).build());
        }
        refreshLabels();

        int bottom = top + RemoteButtons.COUNT * 24 + 6;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            send(-1);
            onClose();
        }).bounds(centerX - 102, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, bottom, 100, 20).build());
    }

    private Component selectLabel(int index) {
        String number = Integer.toString(index + 1);
        return Component.literal(index == buttons.selected() ? "[" + number + "]" : number);
    }

    private void refreshLabels() {
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            RemoteButtons.Button button = buttons.button(i);
            selectButtons[i].setMessage(selectLabel(i));
            modeButtons[i].setMessage(MultiRemoteItem.modeName(button.toggle()));
            lengthButtons[i].setMessage(Component.literal(PulseLength.format(button.pulseTicks())));
            lengthButtons[i].active = !button.toggle(); // length only matters for pulses
        }
    }

    /** Saves the settings on the server, and presses button {@code pressIndex} if it's 0-3. */
    private void send(int pressIndex) {
        PacketDistributor.sendToServer(new ConfigureMultiRemotePayload(hand == InteractionHand.OFF_HAND, buttons, pressIndex));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = height / 2 - 46;
        graphics.drawCenteredString(font, title, centerX, top - 42, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".hint"), centerX, top - 28, 0xA0A0A0);
        // Column headings
        int left = centerX - COLUMNS_WIDTH / 2;
        int headingY = top - 11;
        graphics.drawString(font, Component.translatable(KEY + ".heading_name"), left + 30, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + ".heading_frequency"), left + 130, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + ".heading_mode"), left + 198, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + ".heading_length"), left + 254, headingY, 0xC0C0C0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
