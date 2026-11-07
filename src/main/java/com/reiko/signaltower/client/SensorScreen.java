package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.MotionSensorBlockEntity;
import com.reiko.signaltower.network.ConfigureSensorPayload;
import com.reiko.signaltower.network.OpenSensorScreenPayload;
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
 * A Radio Motion Sensor's settings (v0.39.0), for its owner: how far it looks and who it reacts to,
 * with what it's doing right now (as it was when the screen opened). The two buttons cycle on a
 * click (Shift-click or scroll goes back); Done or Enter saves, Escape cancels.
 */
public class SensorScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".sensor.";

    private final OpenSensorScreenPayload state;
    private int range;
    private MotionSensorBlockEntity.Mode mode;
    private int top;

    public SensorScreen(OpenSensorScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_motion_sensor"));
        this.state = state;
        this.range = MotionSensorBlockEntity.validRange(state.range());
        this.mode = state.modeValue();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 70;
        List<Integer> ranges = Arrays.stream(MotionSensorBlockEntity.RANGES).boxed().toList();
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.translatable(KEY + "blocks", value))
                .withValues(ranges)
                .withInitialValue(range)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "range_tip")))
                .create(centerX - 100, top + 32, 200, 20, Component.translatable(KEY + "range"), (button, value) -> range = value));
        addRenderableWidget(CycleButton.<MotionSensorBlockEntity.Mode>builder(value -> Component.translatable(KEY + "mode_" + value.key()))
                .withValues(MotionSensorBlockEntity.Mode.values())
                .withInitialValue(mode)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "mode_" + value.key() + "_tip")))
                .create(centerX - 100, top + 56, 200, 20, Component.translatable(KEY + "mode"), (button, value) -> mode = value));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 128, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 128, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureSensorPayload(state.pos(), range, mode.ordinal()));
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
        graphics.drawCenteredString(font, Component.translatable(KEY + "frequency", FrequencyNames.label(null, state.frequency()),
                state.ownerName().isEmpty() ? "?" : state.ownerName()), centerX, top + 14, 0xA0A0A0);
        graphics.drawCenteredString(font, Component.translatable(KEY + "explain_" + mode.key()), centerX, top + 82, 0xA0A0A0);
        Component status = state.silenced()
                ? Component.translatable(KEY + "silenced", FrequencyNames.label(null, state.frequency())).withStyle(ChatFormatting.RED)
                : state.sensing()
                ? Component.translatable(KEY + "sees", state.seenName().isEmpty() ? "?" : state.seenName()).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "sees_nobody").withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, status, centerX, top + 98, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "reach", state.reach()), centerX, top + 112, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
