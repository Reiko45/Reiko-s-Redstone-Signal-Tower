package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.ConfigureConverterPayload;
import com.reiko.signaltower.network.OpenConverterScreenPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalInt;
import org.jetbrains.annotations.Nullable;

/**
 * A Radio Converter's screen: the frequency it listens on and the one it sends on, plus what it's
 * converting right now (as it was when the screen opened). Tab moves between the boxes; Enter or
 * Done saves, Escape cancels. Since v0.41.0 it also has the signal gate: Pass, At least N or
 * Below N, with the threshold N (1-15).
 */
public class ConverterScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".converter.";

    private final OpenConverterScreenPayload state;
    private EditBox listenBox;
    private EditBox sendBox;
    private Button doneButton;
    private int top;
    /** Frequencies picked in the directory for the two boxes, used when this screen is shown again. */
    private OptionalInt pickedListen = OptionalInt.empty();
    private OptionalInt pickedSend = OptionalInt.empty();
    /** The signal gate being set (v0.41.0): 0 pass, 1 at least, 2 below; and its threshold. */
    private int gate;
    private int threshold;
    private @Nullable CycleButton<Integer> thresholdButton;

    public ConverterScreen(OpenConverterScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_converter"));
        this.state = state;
        this.gate = state.gate();
        this.threshold = Math.max(1, Math.min(15, state.threshold()));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 80;
        // init() runs again after the directory (or a window resize): keep what was typed, or use what was picked.
        String keepListen = listenBox != null ? listenBox.getValue() : Integer.toString(state.listen());
        String keepSend = sendBox != null ? sendBox.getValue() : Integer.toString(state.send());
        if (pickedListen.isPresent()) keepListen = Integer.toString(pickedListen.getAsInt());
        if (pickedSend.isPresent()) keepSend = Integer.toString(pickedSend.getAsInt());
        pickedListen = OptionalInt.empty();
        pickedSend = OptionalInt.empty();
        listenBox = frequencyBox(centerX - 4, top + 34, keepListen, KEY + "listens");
        sendBox = frequencyBox(centerX - 4, top + 60, keepSend, KEY + "sends");
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(true))
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory_listens")))
                .bounds(centerX + 102, top + 34, 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(false))
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory_sends")))
                .bounds(centerX + 102, top + 60, 16, 20).build());
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.translatable(KEY + "gate_" + value))
                .withValues(0, 1, 2)
                .withInitialValue(gate)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "gate_" + value + "_tip")))
                .create(centerX - 102, top + 86, 140, 20, Component.translatable(KEY + "gate"), (button, value) -> {
                    gate = value;
                    updateThresholdButton();
                }));
        thresholdButton = addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(Integer.toString(value)))
                .withValues(java.util.stream.IntStream.rangeClosed(1, 15).boxed().toList())
                .withInitialValue(threshold)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "threshold_tip")))
                .create(centerX + 42, top + 86, 60, 20, Component.translatable(KEY + "threshold"), (button, value) -> threshold = value));
        updateThresholdButton();
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 146, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 146, 100, 20).build());
        setInitialFocus(listenBox);
        updateDoneButton();
    }

    /** Opens the frequency directory for the Listens on box ({@code listen}) or the Sends on box. */
    private void openDirectory(boolean listen) {
        if (minecraft != null) {
            OptionalInt current = parse(listen ? listenBox : sendBox);
            minecraft.setScreen(new DirectoryScreen(this, current.orElse(-1), frequency -> {
                if (listen) {
                    pickedListen = OptionalInt.of(frequency);
                } else {
                    pickedSend = OptionalInt.of(frequency);
                }
            }, state.pos()));
        }
    }

    private EditBox frequencyBox(int x, int y, String value, String narration) {
        EditBox box = new EditBox(font, x, y, 104, 20, Component.translatable(narration));
        box.setMaxLength(Frequency.MAX_DIGITS);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        box.setResponder(text -> updateDoneButton());
        return addRenderableWidget(box);
    }

    private static OptionalInt parse(EditBox box) {
        String text = box.getValue();
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateThresholdButton() {
        if (thresholdButton != null) {
            thresholdButton.active = gate != 0;
        }
    }

    private void updateDoneButton() {
        if (doneButton != null) {
            doneButton.active = parse(listenBox).isPresent() && parse(sendBox).isPresent();
        }
    }

    private void save() {
        OptionalInt listen = parse(listenBox);
        OptionalInt send = parse(sendBox);
        if (listen.isPresent() && send.isPresent()) {
            PacketDistributor.sendToServer(new ConfigureConverterPayload(state.pos(), listen.getAsInt(), send.getAsInt(), gate, threshold));
            onClose();
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
        graphics.drawCenteredString(font, Component.translatable(KEY + "range", Frequency.MIN, Frequency.MAX),
                centerX, top + 14, 0xA0A0A0);
        Component listens = Component.translatable(KEY + "listens");
        Component sends = Component.translatable(KEY + "sends");
        graphics.drawString(font, listens, centerX - 12 - font.width(listens), top + 40, 0xFFFFFF);
        graphics.drawString(font, sends, centerX - 12 - font.width(sends), top + 66, 0xFFFFFF);
        drawName(graphics, listenBox, top + 40);
        drawName(graphics, sendBox, top + 66);
        FrequencyClaims.ClaimInfo listenClaim = FrequencyClaims.clientInfo(state.listen());
        Component status = state.silenced()
                ? Component.translatable(KEY + "silenced", FrequencyNames.label(null, state.send())).withStyle(ChatFormatting.RED)
                : state.deaf()
                ? Component.translatable(KEY + "deaf", FrequencyNames.label(null, state.listen()),
                        listenClaim != null ? listenClaim.ownerName() : "?").withStyle(ChatFormatting.RED)
                : previewing()
                ? preview()
                : state.gate() != 0
                ? (state.power() > 0
                    ? Component.translatable(KEY + "gate_sending", state.power(), FrequencyNames.label(null, state.send())).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "gate_closed", FrequencyNames.label(null, state.send())).withStyle(ChatFormatting.GRAY))
                : state.power() > 0
                ? Component.translatable(KEY + "converting", state.power(), FrequencyNames.label(null, state.listen()),
                        FrequencyNames.label(null, state.send())).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "quiet", FrequencyNames.label(null, state.listen())).withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, status, centerX, top + 112, 0xFFFFFF);
        Component reach = Component.translatable(KEY + "reach", state.reach());
        if (state.gate() != 0) {
            reach = Component.translatable(KEY + "reach_gate", state.reach(),
                    state.heard() > 0 ? Integer.toString(state.heard()) : Component.translatable(KEY + "heard_nothing"));
        }
        graphics.drawCenteredString(font, reach, centerX, top + 126, 0xA0A0A0);
    }

    /**
     * Whether the gate picked here differs from the converter's, while it still listens on the same
     * frequency: then the status says what it would send once saved.
     */
    private boolean previewing() {
        boolean changed = gate != state.gate() || gate != 0 && threshold != state.threshold();
        return changed && parse(listenBox).equals(OptionalInt.of(state.listen()));
    }

    /** What the converter would send with the gate picked here, from what it hears now. */
    private Component preview() {
        int power = com.reiko.signaltower.radio.SignalNetwork.Repeater.gateOutput(gate, threshold, state.heard());
        Object send = FrequencyNames.label(null, parse(sendBox).orElse(state.send()));
        return power > 0
                ? Component.translatable(KEY + "gate_preview", power, send).withStyle(ChatFormatting.YELLOW)
                : Component.translatable(KEY + "gate_preview_closed", send).withStyle(ChatFormatting.YELLOW);
    }

    /** The typed frequency's name (if it has one) to the right of its box, cut to fit. */
    private void drawName(GuiGraphics graphics, EditBox box, int y) {
        OptionalInt frequency = parse(box);
        String name = frequency.isPresent() ? FrequencyNames.nameOf(null, frequency.getAsInt()) : null;
        int x = box.getX() + box.getWidth() + 24; // after the directory button
        if (name != null && width - x > 12) {
            graphics.drawString(font, font.plainSubstrByWidth(name, width - x - 4), x, y, 0x55FFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
