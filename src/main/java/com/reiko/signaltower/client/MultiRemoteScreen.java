package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
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
 * right-click presses, type its frequency, switch its mode, or press it right now. Pressing also
 * saves the settings. Done saves and closes, Cancel throws away unsaved edits.
 */
public class MultiRemoteScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".multi_remote";

    private final InteractionHand hand;
    private RemoteButtons buttons;
    private final Button[] selectButtons = new Button[RemoteButtons.COUNT];
    private final Button[] modeButtons = new Button[RemoteButtons.COUNT];

    public MultiRemoteScreen(InteractionHand hand, RemoteButtons buttons, Component title) {
        super(title);
        this.hand = hand;
        this.buttons = buttons;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 46;

        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            final int index = i;
            int y = top + i * 24;

            selectButtons[i] = addRenderableWidget(Button.builder(selectLabel(i), b -> {
                buttons = buttons.withSelected(index);
                refreshLabels();
            }).bounds(centerX - 132, y, 28, 20).build());

            EditBox box = new EditBox(font, centerX - 100, y, 80, 20, Component.translatable(KEY + ".frequency", i + 1));
            box.setMaxLength(Frequency.MAX_DIGITS);
            box.setFilter(text -> text.chars().allMatch(Character::isDigit));
            box.setValue(Integer.toString(buttons.button(i).frequency()));
            box.setResponder(text -> {
                if (!text.isEmpty()) {
                    long value = Long.parseLong(text);
                    if (value >= Frequency.MIN && value <= Frequency.MAX) {
                        RemoteButtons.Button old = buttons.button(index);
                        buttons = buttons.withButton(index, new RemoteButtons.Button((int) value, old.toggle()));
                    }
                }
            });
            addRenderableWidget(box);

            modeButtons[i] = addRenderableWidget(Button.builder(MultiRemoteItem.modeName(buttons.button(i).toggle()), b -> {
                RemoteButtons.Button old = buttons.button(index);
                buttons = buttons.withButton(index, new RemoteButtons.Button(old.frequency(), !old.toggle()));
                refreshLabels();
            }).bounds(centerX - 16, y, 64, 20).build());

            addRenderableWidget(Button.builder(Component.translatable(KEY + ".press"), b -> send(index))
                    .bounds(centerX + 52, y, 80, 20).build());
        }

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
            selectButtons[i].setMessage(selectLabel(i));
            modeButtons[i].setMessage(MultiRemoteItem.modeName(buttons.button(i).toggle()));
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
        graphics.drawCenteredString(font, title, centerX, top - 30, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".hint"), centerX, top - 16, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
