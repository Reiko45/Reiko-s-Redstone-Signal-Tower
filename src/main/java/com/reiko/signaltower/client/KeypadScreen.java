package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.network.KeypadConfigurePayload;
import com.reiko.signaltower.network.KeypadEnterPayload;
import com.reiko.signaltower.network.KeypadResultPayload;
import com.reiko.signaltower.network.OpenKeypadScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * A Code Keypad's screen (v0.37.0): a display and twelve keys (1-9, C, 0, OK). Digits can also
 * be typed on the keyboard (Backspace deletes, Enter is OK). The owner also gets **Set code**
 * (saves the digits typed as the new code), **Pulse** (how long the pulse lasts) and, since
 * v0.38.0, **Guest codes...** ({@link KeypadGuestsScreen}). The display
 * only shows stars; the code is never sent to the client.
 */
public class KeypadScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".keypad.";
    private static final int KEY_WIDTH = 30;
    private static final int KEY_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int GRID_WIDTH = KEY_WIDTH * 3 + GAP * 2;

    private final OpenKeypadScreenPayload state;
    private final StringBuilder typed = new StringBuilder();
    private int pulseTicks;
    private boolean hasCode;
    private Component status;
    private int top;
    private Button setCodeButton;
    private Button pulseButton;
    private Button okButton;
    /** Until when (client ticks) the player is locked out, if they are. */
    private long lockedUntilTick;
    private long ticks;

    public KeypadScreen(OpenKeypadScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".code_keypad"));
        this.state = state;
        this.pulseTicks = state.pulseTicks();
        this.hasCode = state.hasCode();
        this.lockedUntilTick = state.lockedSeconds() * 20L;
        this.status = initialStatus();
    }

    public BlockPos pos() {
        return state.pos();
    }

    private Component initialStatus() {
        if (state.lockedSeconds() > 0) {
            return Component.translatable("message." + SignalTower.MOD_ID + ".keypad_locked", state.lockedSeconds()).withStyle(ChatFormatting.RED);
        }
        if (!hasCode) {
            return state.mayConfigure()
                    ? Component.translatable(KEY + "set_first", CodeKeypadBlockEntity.MIN_CODE, CodeKeypadBlockEntity.MAX_CODE).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable(KEY + "no_code").withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable(KEY + "type_code").withStyle(ChatFormatting.GRAY);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int panelHeight = state.mayConfigure() ? 242 : 194;
        top = Math.max(4, height / 2 - panelHeight / 2);
        int left = centerX - GRID_WIDTH / 2;
        int gridTop = top + 52;
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            int x = left + (i % 3) * (KEY_WIDTH + GAP);
            int y = gridTop + (i / 3) * (KEY_HEIGHT + GAP);
            Button.Builder builder = switch (label) {
                case "C" -> Button.builder(Component.literal("C").withStyle(ChatFormatting.RED), b -> clearTyped())
                        .tooltip(Tooltip.create(Component.translatable(KEY + "clear")));
                case "OK" -> Button.builder(Component.literal("OK").withStyle(ChatFormatting.GREEN), b -> submit())
                        .tooltip(Tooltip.create(Component.translatable(KEY + "ok")));
                default -> Button.builder(Component.literal(label), b -> type(label.charAt(0)));
            };
            Button button = addRenderableWidget(builder.bounds(x, y, KEY_WIDTH, KEY_HEIGHT).build());
            if (label.equals("OK")) {
                okButton = button;
            }
        }
        int rowY = gridTop + 4 * (KEY_HEIGHT + GAP) + 22; // room for two status lines
        if (state.mayConfigure()) {
            int half = (GRID_WIDTH - GAP) / 2;
            setCodeButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "set_code"), b -> saveCode())
                    .tooltip(Tooltip.create(Component.translatable(KEY + "set_code_tooltip", CodeKeypadBlockEntity.MIN_CODE, CodeKeypadBlockEntity.MAX_CODE)))
                    .bounds(left, rowY, half, 20).build());
            pulseButton = addRenderableWidget(Button.builder(pulseLabel(), b -> cyclePulse())
                    .tooltip(Tooltip.create(Component.translatable(KEY + "pulse_tooltip")))
                    .bounds(left + half + GAP, rowY, half, 20).build());
            rowY += 24;
            // v0.38.0: extra codes for visitors.
            addRenderableWidget(Button.builder(Component.translatable(KEY + "guest_codes"), b -> {
                        if (minecraft != null) {
                            minecraft.setScreen(new KeypadGuestsScreen(this, state.pos()));
                        }
                    })
                    .tooltip(Tooltip.create(Component.translatable(KEY + "guest_codes_tooltip")))
                    .bounds(left, rowY, GRID_WIDTH, 20).build());
            rowY += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left, rowY, GRID_WIDTH, 20).build());
        updateButtons();
    }

    private Component pulseLabel() {
        return Component.translatable(KEY + "pulse", PulseLength.format(pulseTicks));
    }

    private void type(char digit) {
        if (typed.length() < CodeKeypadBlockEntity.MAX_CODE) {
            typed.append(digit);
            updateButtons();
        }
    }

    private void clearTyped() {
        typed.setLength(0);
        updateButtons();
    }

    private boolean locked() {
        return ticks < lockedUntilTick;
    }

    private void submit() {
        if (typed.isEmpty() || locked()) {
            return;
        }
        PacketDistributor.sendToServer(new KeypadEnterPayload(state.pos(), typed.toString()));
        typed.setLength(0);
        updateButtons();
    }

    private void saveCode() {
        if (!CodeKeypadBlockEntity.validCode(typed.toString())) {
            return;
        }
        PacketDistributor.sendToServer(new KeypadConfigurePayload(state.pos(), typed.toString(), pulseTicks));
        typed.setLength(0);
        hasCode = true;
        updateButtons();
    }

    /** Steps to the next pulse length (sneak-click: the previous one) and saves it. */
    private void cyclePulse() {
        pulseTicks = PulseLength.next(pulseTicks, hasShiftDown());
        pulseButton.setMessage(pulseLabel());
        PacketDistributor.sendToServer(new KeypadConfigurePayload(state.pos(), "", pulseTicks));
    }

    private void updateButtons() {
        if (okButton != null) {
            okButton.active = !typed.isEmpty() && !locked();
        }
        if (setCodeButton != null) {
            setCodeButton.active = CodeKeypadBlockEntity.validCode(typed.toString());
        }
    }

    /** The keypad's answer: the right code closes the screen (the message goes above the hotbar). */
    public void onResult(KeypadResultPayload result) {
        CodeKeypadBlockEntity.Outcome outcome = result.outcomeKind();
        if (outcome == CodeKeypadBlockEntity.Outcome.ACCEPTED && minecraft != null) {
            onClose();
            minecraft.gui.setOverlayMessage(result.message(), false);
            return;
        }
        status = result.message();
        if (result.lockedSeconds() > 0) {
            lockedUntilTick = ticks + result.lockedSeconds() * 20L;
        }
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        boolean wasLocked = locked();
        ticks++;
        if (wasLocked && !locked()) {
            status = Component.translatable(KEY + "type_code").withStyle(ChatFormatting.GRAY);
            updateButtons();
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= '0' && codePoint <= '9') {
            type(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!typed.isEmpty()) {
                typed.setLength(typed.length() - 1);
                updateButtons();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        Component owner = state.ownerName().isEmpty() ? Component.translatable(KEY + "frequency", FrequencyNames.label(null, state.frequency()))
                : Component.translatable(KEY + "frequency_owner", FrequencyNames.label(null, state.frequency()), state.ownerName());
        graphics.drawCenteredString(font, owner, centerX, top + 12, 0xA0A0A0);
        // The display: stars for the digits typed, never the code.
        int left = centerX - GRID_WIDTH / 2;
        int displayTop = top + 26;
        graphics.fill(left, displayTop, left + GRID_WIDTH, displayTop + 20, 0xFF0E1A10);
        graphics.renderOutline(left, displayTop, GRID_WIDTH, 20, 0xFF5A6A5C);
        String shown = typed.isEmpty() ? "" : "* ".repeat(typed.length()).trim();
        if (typed.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(locked() ? KEY + "display_locked" : KEY + "display_empty")
                    .withStyle(ChatFormatting.DARK_GREEN), centerX, displayTop + 6, 0x3A9A40);
        } else {
            graphics.drawCenteredString(font, shown, centerX, displayTop + 7, 0x60FF70);
        }
        int statusY = top + 52 + 4 * (KEY_HEIGHT + GAP) + 1;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(status, 240);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawCenteredString(font, lines.get(i), centerX, statusY + i * 10, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
