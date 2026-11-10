package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import com.reiko.signaltower.network.ConfigureTvCameraPayload;
import com.reiko.signaltower.network.OpenTvCameraPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.TvCamera;
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

import java.util.List;

/**
 * A TV Camera's settings (v0.66.0): its name (shown on monitors), tilt, lens, pan and night vision.
 * Below them, how many monitors showed it when the screen opened. Done or Enter saves, Escape cancels.
 */
public class TvCameraScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".tv_camera.";

    private final OpenTvCameraPayload state;
    private int settings;
    private int top;
    private EditBox nameBox;

    public TvCameraScreen(OpenTvCameraPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".tv_camera"));
        this.state = state;
        this.settings = TvCamera.settings(state.settings());
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 84;
        String keep = nameBox != null ? nameBox.getValue() : state.name();
        nameBox = new EditBox(font, centerX - 100, top + 32, 200, 20, Component.translatable(KEY + "name"));
        nameBox.setMaxLength(TvCameraBlockEntity.NAME_LENGTH);
        nameBox.setValue(keep);
        nameBox.setHint(Component.translatable(KEY + "name_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        int y = top + 56;
        addRenderableWidget(CycleButton.<Integer>builder(this::tiltText)
                .withValues(List.of(TvCamera.TILT_AUTO, 0, 1, 2, 3, 4, 5))
                .withInitialValue(settings & TvCamera.TILT_MASK)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "tilt_tip")))
                .create(centerX - 100, y, 200, 20, Component.translatable(KEY + "tilt"),
                        (button, value) -> settings = (settings & ~TvCamera.TILT_MASK) | value));
        y += 24;
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.translatable(KEY + "lens_" + value))
                .withValues(List.of(0, 1, 2))
                .withInitialValue(TvCamera.lens(settings))
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "lens_tip")))
                .create(centerX - 100, y, 200, 20, Component.translatable(KEY + "lens"),
                        (button, value) -> settings = (settings & ~TvCamera.LENS_MASK) | value << TvCamera.LENS_SHIFT));
        y += 24;
        y = toggle(centerX, y, TvCamera.PAN, "pan");
        toggle(centerX, y, TvCamera.NIGHT_VISION, "night");
        int buttons = top + 168;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, buttons, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, buttons, 100, 20).build());
    }

    private Component tiltText(int value) {
        return value < TvCamera.TILTS.length ? Component.translatable(KEY + "tilt_degrees", -TvCamera.TILTS[value])
                : Component.translatable(KEY + "tilt_auto");
    }

    private int toggle(int centerX, int y, int bit, String name) {
        addRenderableWidget(CycleButton.onOffBuilder((settings & bit) != 0)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + name + "_tip")))
                .create(centerX - 100, y, 200, 20, Component.translatable(KEY + name),
                        (button, value) -> settings = value ? settings | bit : settings & ~bit));
        return y + 24;
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureTvCameraPayload(state.pos(), settings, nameBox.getValue()));
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
        Component status = state.watchers() == 0
                ? Component.translatable(KEY + "unwatched", state.reach()).withStyle(ChatFormatting.GRAY)
                : Component.translatable(KEY + (state.watchers() == 1 ? "watched" : "watched_many"), state.watchers(), state.reach())
                .withStyle(ChatFormatting.GREEN);
        graphics.drawCenteredString(font, status, centerX, top + 154, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
