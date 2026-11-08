package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.ConfigureDelayPayload;
import com.reiko.signaltower.network.OpenDelayScreenPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;

/**
 * A Radio Delay Receiver's screen (v0.41.0): its delay and hold (each cycles through none, 0.5s,
 * 1s, 2s, 3s, 5s, 10s, 30s and 60s; Shift-click or scroll goes back), and what it hears and outputs
 * right now (as it was when the screen opened, with any change under way counted down). Done or
 * Enter saves, Escape cancels.
 */
public class DelayScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".delay.";

    private final OpenDelayScreenPayload state;
    private int delay;
    private int hold;
    private int top;
    /** The game time when the screen opened, to count down a change under way. */
    private final long openedAt;

    public DelayScreen(OpenDelayScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_delay_receiver"));
        this.state = state;
        this.delay = RadioReceiverBlockEntity.validTime(state.delay());
        this.hold = RadioReceiverBlockEntity.validTime(state.hold());
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        this.openedAt = level != null ? level.getGameTime() : 0;
    }

    /** Ticks since the screen opened. */
    private long elapsed() {
        net.minecraft.client.multiplayer.ClientLevel level = minecraft != null ? minecraft.level : null;
        return level != null ? Math.max(0, level.getGameTime() - openedAt) : 0;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 66;
        List<Integer> times = Arrays.stream(RadioReceiverBlockEntity.TIMES).boxed().toList();
        addRenderableWidget(CycleButton.<Integer>builder(RadioReceiverBlockEntity::timeLabel)
                .withValues(times)
                .withInitialValue(delay)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "delay_tip")))
                .create(centerX - 100, top + 32, 200, 20, Component.translatable(KEY + "delay"), (button, value) -> delay = value));
        addRenderableWidget(CycleButton.<Integer>builder(RadioReceiverBlockEntity::timeLabel)
                .withValues(times)
                .withInitialValue(hold)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "hold_tip")))
                .create(centerX - 100, top + 56, 200, 20, Component.translatable(KEY + "hold"), (button, value) -> hold = value));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 124, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 124, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureDelayPayload(state.pos(), delay, hold));
        onClose();
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
        graphics.drawCenteredString(font, Component.translatable(KEY + "frequency", FrequencyNames.label(null, state.frequency())),
                centerX, top + 14, 0xA0A0A0);
        graphics.drawCenteredString(font, Component.translatable(KEY + "explain"), centerX, top + 82, 0xA0A0A0);
        Component status;
        if (state.deaf()) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(state.frequency());
            status = Component.translatable(KEY + "deaf", FrequencyNames.label(null, state.frequency()),
                    claim != null ? claim.ownerName() : "?").withStyle(ChatFormatting.RED);
        } else {
            Component hearing = state.heard() > 0 ? Component.literal(Integer.toString(state.heard())).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "nothing");
            // Once a change under way is due, show the output it was heading for.
            boolean due = state.changeIn() != 0 && elapsed() >= Math.abs(state.changeIn());
            boolean on = due ? state.changeIn() > 0 : state.output() > 0;
            Component output = on ? Component.translatable(KEY + "on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "off");
            status = Component.translatable(KEY + "status", hearing, output).withStyle(ChatFormatting.GRAY);
        }
        graphics.drawCenteredString(font, status, centerX, top + 96, 0xFFFFFF);
        long left = Math.abs(state.changeIn()) - elapsed();
        if (state.changeIn() != 0 && left > 0 && !state.deaf()) {
            Component when = RadioReceiverBlockEntity.countdownLabel(left);
            graphics.drawCenteredString(font, Component.translatable(KEY + (state.changeIn() > 0 ? "turns_on" : "turns_off"), when)
                    .withStyle(ChatFormatting.YELLOW), centerX, top + 108, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
