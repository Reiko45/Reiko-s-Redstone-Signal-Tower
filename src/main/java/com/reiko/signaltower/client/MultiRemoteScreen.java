package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.network.ConfigureMultiRemotePayload;
import com.reiko.signaltower.network.RemoteScanRequestPayload;
import com.reiko.signaltower.network.RemoteScanResultPayload;
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
public class MultiRemoteScreen extends Screen implements RemoteScanListener {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".multi_remote";
    private static final int COLUMNS_WIDTH = 348;
    /** Each row is a line of controls plus a line of locator text underneath. */
    private static final int ROW_HEIGHT = 32;

    private final InteractionHand hand;
    private RemoteButtons buttons;
    private final Button[] selectButtons = new Button[RemoteButtons.COUNT];
    private final Button[] modeButtons = new Button[RemoteButtons.COUNT];
    private final Button[] lengthButtons = new Button[RemoteButtons.COUNT];
    private final RemoteScanResultPayload.Entry[] scans = new RemoteScanResultPayload.Entry[RemoteButtons.COUNT];
    private int scanReach;
    private int scanTowers;
    private boolean scanned;
    private int scanTimer;

    public MultiRemoteScreen(InteractionHand hand, RemoteButtons buttons, Component title) {
        super(title);
        this.hand = hand;
        this.buttons = buttons;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 70;

        // Columns: [#] [name] [frequency] [mode] [pulse length] [Press], 348 wide in total.
        int left = centerX - COLUMNS_WIDTH / 2;
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            final int index = i;
            int y = top + i * ROW_HEIGHT;

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
                        scans[index] = null; // "checking" until the new frequency's answer arrives
                        scanTimer = Math.min(scanTimer, 5);
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

        int bottom = top + RemoteButtons.COUNT * ROW_HEIGHT + 2;
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
    public void tick() {
        super.tick();
        if (--scanTimer <= 0) {
            scanTimer = 20;
            java.util.List<Integer> frequencies = new java.util.ArrayList<>();
            for (int i = 0; i < RemoteButtons.COUNT; i++) {
                frequencies.add(buttons.button(i).frequency());
            }
            PacketDistributor.sendToServer(new RemoteScanRequestPayload(frequencies));
        }
    }

    @Override
    public void onRemoteScan(RemoteScanResultPayload result) {
        scanReach = result.reach();
        scanTowers = result.towers();
        scanned = true;
        for (int i = 0; i < RemoteButtons.COUNT && i < result.entries().size(); i++) {
            RemoteScanResultPayload.Entry entry = result.entries().get(i);
            if (entry.frequency() == buttons.button(i).frequency()) {
                scans[i] = entry;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = height / 2 - 70;
        graphics.drawCenteredString(font, title, centerX, top - 42, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".hint"), centerX, top - 28, 0xA0A0A0);
        // Column headings
        int left = centerX - COLUMNS_WIDTH / 2;
        int headingY = top - 11;
        graphics.drawString(font, Component.translatable(KEY + ".heading_name"), left + 30, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + ".heading_frequency"), left + 130, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + ".heading_mode"), left + 198, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + ".heading_length"), left + 254, headingY, 0xC0C0C0);
        // Locator: who is listening on each button's frequency
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            graphics.drawString(font, LocatorText.describe(scans[i], scanReach, scanTowers), left + 30, top + i * ROW_HEIGHT + 22, 0xFFFFFF);
        }
        int bottom = top + RemoteButtons.COUNT * ROW_HEIGHT + 2;
        graphics.drawCenteredString(font, LocatorText.reachLine(scanned, scanReach, scanTowers), centerX, bottom + 26, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
