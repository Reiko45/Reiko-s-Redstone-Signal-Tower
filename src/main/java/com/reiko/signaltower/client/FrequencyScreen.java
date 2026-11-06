package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import net.minecraft.ChatFormatting;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.TunerPresets;
import com.reiko.signaltower.network.ConfigureFavoritesPayload;
import com.reiko.signaltower.network.ConfigurePagerPayload;
import com.reiko.signaltower.network.ConfigureRadioItemPayload;
import com.reiko.signaltower.network.RemoteScanRequestPayload;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import com.reiko.signaltower.radio.Frequency;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Type a frequency for the Frequency Tuner, Remote Control or Radio Pager (plus the remote's mode).
 * Enter or Done saves it, Escape cancels. All three get a row of favorites: click an empty one to
 * save the frequency above, click a saved one to use it, sneak-click to replace it, right-click to
 * clear it. Favorites are saved straight away, even if you then press Cancel.
 * <p>
 * Layout, from {@code top}: title, range hint, the frequency row; for the remote the locator and
 * reach lines; the favorites; the remote's Mode and Length buttons; Done and Cancel.
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
    /** The favorites (all three items have them). */
    private @Nullable TunerPresets presets;
    /** Pager only: its Sound and Chat log settings (null for the tuner and remote). */
    private @Nullable Boolean pagerSound;
    private boolean pagerChatLog;
    private final Button[] presetButtons = new Button[TunerPresets.COUNT];
    /** Where things go, worked out in init(). */
    private int top;
    private int inputY;
    private int favoritesY;

    public FrequencyScreen(InteractionHand hand, int frequency, Component title,
                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks) {
        super(title);
        this.hand = hand;
        this.initialFrequency = frequency;
        this.toggleMode = toggleMode;
        this.pulseTicks = pulseTicks == null ? PulseLength.DEFAULT_TICKS : pulseTicks;
    }

    /** The screen with a row of favorites (all three items use this). */
    public FrequencyScreen(InteractionHand hand, int frequency, Component title,
                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks, @Nullable TunerPresets presets) {
        this(hand, frequency, title, toggleMode, pulseTicks);
        this.presets = presets;
    }

    /** A Radio Pager's screen: like the tuner's, plus Sound and Chat log buttons. */
    public FrequencyScreen(InteractionHand hand, int frequency, Component title, TunerPresets presets,
                           boolean sound, boolean chatLog) {
        this(hand, frequency, title, null, null, presets);
        this.pagerSound = sound;
        this.pagerChatLog = chatLog;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        boolean remote = toggleMode != null;
        boolean optionRow = remote || pagerSound != null;
        int contentHeight = 34 + 20 + (remote ? 32 : 6) + (presets != null ? 37 : 0) + (optionRow ? 24 : 0) + 20;
        top = centerY - Math.max(44, contentHeight / 2);
        inputY = top + 34;
        int y = inputY + 20 + (remote ? 32 : 6);  // under the frequency row (and the remote's two locator lines)

        input = new EditBox(font, centerX - 50, inputY, 100, 20, Component.translatable(KEY + ".input"));
        input.setMaxLength(Frequency.MAX_DIGITS);
        input.setFilter(text -> text.chars().allMatch(Character::isDigit));
        input.setValue(Integer.toString(initialFrequency));
        input.setResponder(text -> {
            updateDoneButton();
            updatePresetButtons();
            scan = null;     // show "checking" until the new frequency's answer arrives
            scanTimer = 5;
        });
        addRenderableWidget(input);

        addRenderableWidget(nudgeButton(-100, centerX - 112, 32));
        addRenderableWidget(nudgeButton(-1, centerX - 76, 22));
        addRenderableWidget(nudgeButton(1, centerX + 54, 22));
        addRenderableWidget(nudgeButton(100, centerX + 80, 32));

        if (presets != null) {
            // Five favorites, with a "Favorites" heading (drawn in render()) above them
            favoritesY = y - 1;
            int rowX = centerX - (TunerPresets.COUNT * 64 - 4) / 2;
            for (int i = 0; i < TunerPresets.COUNT; i++) {
                int slot = i;
                presetButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> pressPreset(slot))
                        .bounds(rowX + i * 64, y + 10, 60, 20).build());
            }
            updatePresetButtons();
            y += 37;
        }
        int buttonsY = y;
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
        if (pagerSound != null) {
            addRenderableWidget(Button.builder(pagerSoundLabel(), button -> {
                pagerSound = !pagerSound;
                button.setMessage(pagerSoundLabel());
            }).bounds(centerX - 102, buttonsY, 100, 20).build());
            addRenderableWidget(Button.builder(pagerChatLabel(), button -> {
                pagerChatLog = !pagerChatLog;
                button.setMessage(pagerChatLabel());
            }).tooltip(Tooltip.create(Component.translatable(KEY + ".pager_chat_tooltip")))
                    .bounds(centerX + 2, buttonsY, 100, 20).build());
            buttonsY += 24;
        }
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(centerX - 102, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 2, buttonsY, 100, 20).build());

        setInitialFocus(input);
        updateDoneButton();
    }

    /** Click: use a saved favorite, or save the frequency above into an empty one. Sneak-click: replace. */
    private void pressPreset(int slot) {
        if (presets == null) {
            return;
        }
        if (!presets.isEmpty(slot) && !Screen.hasShiftDown()) {
            input.setValue(Integer.toString(presets.get(slot)));
            return;
        }
        parsed().ifPresent(frequency -> setPresets(presets.with(slot, frequency)));
    }

    private void setPresets(TunerPresets newPresets) {
        presets = newPresets;
        PacketDistributor.sendToServer(new ConfigureFavoritesPayload(hand == InteractionHand.OFF_HAND, newPresets));
        updatePresetButtons();
    }

    private void updatePresetButtons() {
        if (presets == null || presetButtons[0] == null) {
            return;
        }
        int current = parsed().orElse(-1);
        for (int i = 0; i < TunerPresets.COUNT; i++) {
            Button button = presetButtons[i];
            if (presets.isEmpty(i)) {
                button.setMessage(Component.literal("+").withStyle(ChatFormatting.GRAY));
                button.setTooltip(Tooltip.create(Component.translatable(KEY + ".favorite_empty", i + 1)));
            } else {
                int frequency = presets.get(i);
                Component label = Component.literal(Integer.toString(frequency));
                button.setMessage(frequency == current ? label.copy().withStyle(ChatFormatting.GREEN) : label);
                button.setTooltip(Tooltip.create(Component.translatable(KEY + ".favorite", i + 1, frequency)));
            }
        }
    }

    /** Right-click a saved favorite to clear it. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && presets != null) {
            for (int i = 0; i < TunerPresets.COUNT; i++) {
                if (presetButtons[i] != null && presetButtons[i].isMouseOver(mouseX, mouseY) && !presets.isEmpty(i)) {
                    presetButtons[i].playDownSound(minecraft.getSoundManager());
                    setPresets(presets.cleared(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
                .bounds(x, inputY, buttonWidth, 20).build();
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

    private Component pagerSoundLabel() {
        return CommonComponents.optionNameValue(Component.translatable(KEY + ".pager_sound"),
                CommonComponents.optionStatus(Boolean.TRUE.equals(pagerSound)));
    }

    private Component pagerChatLabel() {
        return CommonComponents.optionNameValue(Component.translatable(KEY + ".pager_chat"),
                CommonComponents.optionStatus(pagerChatLog));
    }

    private void save() {
        parsed().ifPresent(frequency -> {
            boolean offhand = hand == InteractionHand.OFF_HAND;
            if (pagerSound != null) {
                PacketDistributor.sendToServer(new ConfigurePagerPayload(offhand, frequency, pagerSound, pagerChatLog));
            } else {
                PacketDistributor.sendToServer(new ConfigureRadioItemPayload(
                        offhand, frequency, Boolean.TRUE.equals(toggleMode), pulseTicks));
            }
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
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".range", Frequency.MIN, Frequency.MAX),
                centerX, top + 16, 0xA0A0A0);
        if (toggleMode != null) {
            graphics.drawCenteredString(font, LocatorText.describe(scan, scanReach, scanTowers), centerX, inputY + 26, 0xFFFFFF);
            graphics.drawCenteredString(font, LocatorText.reachLine(scanned, scanReach, scanTowers), centerX, inputY + 38, 0xFFFFFF);
        }
        if (presets != null) {
            graphics.drawCenteredString(font, Component.translatable(KEY + ".favorites"), centerX, favoritesY, 0xA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
