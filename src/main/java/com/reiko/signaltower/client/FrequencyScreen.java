package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.network.ConfigureRadioItemPayload;
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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Type a frequency for the Frequency Tuner or Remote Control (plus the remote's mode).
 * Enter or Done saves it, Escape cancels.
 */
public class FrequencyScreen extends Screen implements RemoteScanListener {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".tuner";

    private final InteractionHand hand;
    private final int initialFrequency;
    /** Null for the tuner, which has no mode. */
    private @Nullable Boolean toggleMode;
    private int pulseTicks;
    private @Nullable Button lengthButton;
    /** Locator (remotes only): the latest answer, and ticks until the next question. */
    private @Nullable RemoteScanResultPayload.Entry scan;
    private int scanReach;
    private int scanTowers;
    private boolean scanned;
    private int scanTimer;
    private EditBox input;
    private Button doneButton;

    public FrequencyScreen(InteractionHand hand, int frequency, Component title,
                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks) {
        super(title);
        this.hand = hand;
        this.initialFrequency = frequency;
        this.toggleMode = toggleMode;
        this.pulseTicks = pulseTicks == null ? PulseLength.DEFAULT_TICKS : pulseTicks;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        input = new EditBox(font, centerX - 50, centerY - 10, 100, 20, Component.translatable(KEY + ".input"));
        input.setMaxLength(Frequency.MAX_DIGITS);
        input.setFilter(text -> text.chars().allMatch(Character::isDigit));
        input.setValue(Integer.toString(initialFrequency));
        input.setResponder(text -> {
            updateDoneButton();
            scan = null;     // show "checking" until the new frequency's answer arrives
            scanTimer = 5;
        });
        addRenderableWidget(input);

        addRenderableWidget(nudgeButton(-100, centerX - 112, 32));
        addRenderableWidget(nudgeButton(-1, centerX - 76, 22));
        addRenderableWidget(nudgeButton(1, centerX + 54, 22));
        addRenderableWidget(nudgeButton(100, centerX + 80, 32));

        // The remote's mode button sits between the frequency row and Done/Cancel, under the locator and reach lines.
        int buttonsY = toggleMode != null ? centerY + 42 : centerY + 22;
        if (toggleMode != null) {
            addRenderableWidget(Button.builder(modeLabel(), button -> {
                toggleMode = !toggleMode;
                button.setMessage(modeLabel());
                updateLengthButton();
            }).bounds(centerX - 102, buttonsY, 100, 20).build());
            // Click for a longer pulse, sneak-click for a shorter one. Only used in pulse mode.
            lengthButton = addRenderableWidget(Button.builder(lengthLabel(), button -> {
                pulseTicks = PulseLength.next(pulseTicks, Screen.hasShiftDown());
                updateLengthButton();
            }).bounds(centerX + 2, buttonsY, 100, 20).build());
            updateLengthButton();
            buttonsY += 24;
        }
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(centerX - 102, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 2, buttonsY, 100, 20).build());

        setInitialFocus(input);
        updateDoneButton();
    }

    private Component modeLabel() {
        String mode = Boolean.TRUE.equals(toggleMode) ? "remote_mode_toggle" : "remote_mode_pulse";
        return Component.translatable("tooltip." + SignalTower.MOD_ID + ".remote_mode",
                Component.translatable("tooltip." + SignalTower.MOD_ID + "." + mode));
    }

    private Component lengthLabel() {
        return Component.translatable(KEY + ".pulse_length", PulseLength.format(pulseTicks));
    }

    private void updateLengthButton() {
        if (lengthButton != null) {
            lengthButton.setMessage(lengthLabel());
            lengthButton.active = !Boolean.TRUE.equals(toggleMode);
        }
    }

    private Button nudgeButton(int amount, int x, int buttonWidth) {
        String label = (amount > 0 ? "+" : "") + amount;
        return Button.builder(Component.literal(label), button -> nudge(amount))
                .bounds(x, height / 2 - 10, buttonWidth, 20).build();
    }

    private void nudge(int amount) {
        input.setValue(Integer.toString(Frequency.clamp((long) parsed().orElse(initialFrequency) + amount)));
    }

    private java.util.OptionalInt parsed() {
        String text = input.getValue();
        if (text.isEmpty()) {
            return java.util.OptionalInt.empty();
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX
                ? java.util.OptionalInt.of((int) value)
                : java.util.OptionalInt.empty();
    }

    private void updateDoneButton() {
        if (doneButton != null) {
            doneButton.active = parsed().isPresent();
        }
    }

    private void save() {
        parsed().ifPresent(frequency -> {
            PacketDistributor.sendToServer(new ConfigureRadioItemPayload(
                    hand == InteractionHand.OFF_HAND, frequency, Boolean.TRUE.equals(toggleMode), pulseTicks));
            onClose();
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (toggleMode != null && --scanTimer <= 0) {
            scanTimer = 20;
            PacketDistributor.sendToServer(new RemoteScanRequestPayload(
                    java.util.List.of(parsed().orElse(initialFrequency))));
        }
    }

    @Override
    public void onRemoteScan(RemoteScanResultPayload result) {
        if (!result.entries().isEmpty() && result.entries().get(0).frequency() == parsed().orElse(-1)) {
            scan = result.entries().get(0);
            scanReach = result.reach();
            scanTowers = result.towers();
            scanned = true;
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
        int centerY = height / 2;
        graphics.drawCenteredString(font, title, centerX, centerY - 44, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".range", Frequency.MIN, Frequency.MAX),
                centerX, centerY - 28, 0xA0A0A0);
        if (toggleMode != null) {
            graphics.drawCenteredString(font, LocatorText.describe(scan, scanReach, scanTowers), centerX, centerY + 16, 0xFFFFFF);
            graphics.drawCenteredString(font, LocatorText.reachLine(scanned, scanReach, scanTowers), centerX, centerY + 28, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
