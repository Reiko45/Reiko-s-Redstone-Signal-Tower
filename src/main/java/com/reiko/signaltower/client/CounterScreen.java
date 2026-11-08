package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.ConfigureCounterPayload;
import com.reiko.signaltower.network.OpenCounterScreenPayload;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.OptionalInt;

/**
 * A Radio Counter Receiver's screen (v0.42.0): Turn on at (1-16, 20, 24, 32, 48, 64), Then (a
 * 1-second pulse and count again, or stay on until reset), Forget after (none to 60 seconds without
 * a turn-on) and a Reset by frequency (empty: none) with the directory. Below, the count and what
 * it hears, and when it forgets the count, counting down. Done or Enter saves, Reset count also
 * sets the count back to 0, Escape cancels.
 */
public class CounterScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".counter.";

    private final OpenCounterScreenPayload state;
    private int target;
    private boolean latch;
    private int forget;
    private EditBox resetBox;
    private Button doneButton;
    private Button clearButton;
    private OptionalInt pickedReset = OptionalInt.empty();
    private int top;
    private final long openedAt;

    public CounterScreen(OpenCounterScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_counter_receiver"));
        this.state = state;
        this.target = RadioReceiverBlockEntity.validCount(state.target());
        this.latch = state.latch();
        this.forget = RadioReceiverBlockEntity.validTime(state.forget());
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        this.openedAt = level != null ? level.getGameTime() : 0;
    }

    private long elapsed() {
        ClientLevel level = minecraft != null ? minecraft.level : null;
        return level != null ? Math.max(0, level.getGameTime() - openedAt) : 0;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 90;
        String keep = resetBox != null ? resetBox.getValue() : state.reset() > 0 ? Integer.toString(state.reset()) : "";
        if (pickedReset.isPresent()) {
            keep = Integer.toString(pickedReset.getAsInt());
            pickedReset = OptionalInt.empty();
        }
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(Integer.toString(value)))
                .withValues(Arrays.stream(RadioReceiverBlockEntity.COUNTS).boxed().toList())
                .withInitialValue(target)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "target_tip")))
                .create(centerX - 100, top + 30, 200, 20, Component.translatable(KEY + "target"), (button, value) -> target = value));
        addRenderableWidget(CycleButton.<Boolean>builder(value -> Component.translatable(KEY + (value ? "latch_stay" : "latch_pulse")))
                .withValues(false, true)
                .withInitialValue(latch)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + (value ? "latch_stay_tip" : "latch_pulse_tip"))))
                .create(centerX - 100, top + 54, 200, 20, Component.translatable(KEY + "latch"), (button, value) -> latch = value));
        addRenderableWidget(CycleButton.<Integer>builder(RadioReceiverBlockEntity::timeLabel)
                .withValues(Arrays.stream(RadioReceiverBlockEntity.TIMES).boxed().toList())
                .withInitialValue(forget)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "forget_tip")))
                .create(centerX - 100, top + 78, 200, 20, Component.translatable(KEY + "forget"), (button, value) -> forget = value));
        resetBox = new EditBox(font, centerX - 4, top + 104, 104, 20, Component.translatable(KEY + "reset"));
        resetBox.setMaxLength(Frequency.MAX_DIGITS);
        resetBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        resetBox.setValue(keep);
        resetBox.setHint(Component.translatable(KEY + "reset_none").withStyle(ChatFormatting.DARK_GRAY));
        resetBox.setTooltip(Tooltip.create(Component.translatable(KEY + "reset_tip")));
        resetBox.setResponder(text -> updateButtons());
        addRenderableWidget(resetBox);
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory())
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory")))
                .bounds(centerX + 102, top + 104, 16, 20).build());
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save(false))
                .bounds(centerX - 152, top + 162, 100, 20).build());
        clearButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "clear"), b -> save(true))
                .tooltip(Tooltip.create(Component.translatable(KEY + "clear_tip")))
                .bounds(centerX - 50, top + 162, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 52, top + 162, 100, 20).build());
        updateButtons();
    }

    private void openDirectory() {
        if (minecraft != null) {
            OptionalInt current = parse(resetBox);
            minecraft.setScreen(new DirectoryScreen(this, current.orElse(-1), frequency -> pickedReset = OptionalInt.of(frequency), state.pos()));
        }
    }

    /**
     * The reset frequency typed: empty for none (0), else the number if it's a frequency;
     * empty optional if what's typed isn't one.
     */
    private static OptionalInt parse(EditBox box) {
        String text = box.getValue();
        if (text.isEmpty()) {
            return OptionalInt.of(0);
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateButtons() {
        boolean valid = resetBox != null && parse(resetBox).isPresent();
        if (doneButton != null) {
            doneButton.active = valid;
        }
        if (clearButton != null) {
            clearButton.active = valid;
        }
    }

    private void save(boolean clear) {
        OptionalInt reset = parse(resetBox);
        if (reset.isPresent()) {
            PacketDistributor.sendToServer(new ConfigureCounterPayload(state.pos(), target, latch, forget, reset.getAsInt(), clear));
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "frequency", FrequencyNames.label(null, state.frequency())),
                centerX, top + 14, 0xA0A0A0);
        Component label = Component.translatable(KEY + "reset");
        graphics.drawString(font, label, centerX - 12 - font.width(label), top + 110, 0xFFFFFF);
        OptionalInt typed = parse(resetBox);
        String name = typed.isPresent() && typed.getAsInt() > 0 ? FrequencyNames.nameOf(null, typed.getAsInt()) : null;
        int x = resetBox.getX() + resetBox.getWidth() + 24;
        if (name != null && width - x > 12) {
            graphics.drawString(font, font.plainSubstrByWidth(name, width - x - 4), x, top + 110, 0x55FFFF);
        }
        graphics.drawCenteredString(font, statusLine(), centerX, top + 132, 0xFFFFFF);
        long left = state.forgetIn() - elapsed();
        if (state.forgetIn() > 0 && left > 0 && state.deaf() == 0) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "forgets_in", RadioReceiverBlockEntity.countdownLabel(left))
                    .withStyle(ChatFormatting.YELLOW), centerX, top + 144, 0xFFFFFF);
        }
    }

    /** "Count: 2 of 3 | hearing 15", "Reached 3: on", or a red line if it can't hear one of its frequencies. */
    private Component statusLine() {
        if (state.deaf() != 0) {
            int deaf = (state.deaf() & OpenCounterScreenPayload.DEAF_FREQUENCY) != 0 ? state.frequency() : state.reset();
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(deaf);
            return Component.translatable(KEY + "deaf", FrequencyNames.label(null, deaf), claim != null ? claim.ownerName() : "?")
                    .withStyle(ChatFormatting.RED);
        }
        Component hearing = state.heard() > 0 ? Component.literal(Integer.toString(state.heard())).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "nothing");
        if (state.on()) {
            return Component.translatable(KEY + "reached", state.target(), hearing).withStyle(ChatFormatting.GREEN);
        }
        boolean forgotten = state.forgetIn() > 0 && elapsed() >= state.forgetIn();
        return Component.translatable(KEY + "count", forgotten ? 0 : state.count(), state.target(), hearing).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
