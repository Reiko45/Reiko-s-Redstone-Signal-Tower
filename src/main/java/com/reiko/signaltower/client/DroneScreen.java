package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.network.ConfigureDronePayload;
import com.reiko.signaltower.network.OpenDronePayload;
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

/**
 * A Radio Drone's settings (v0.67.0): its name (on monitors and the pilot's panel), Beacon, Relay
 * and Night vision. Above them its frequency and battery. Done or Enter saves, Escape cancels.
 */
public class DroneScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".drone.";

    private final OpenDronePayload state;
    private int settings;
    private int top;
    private EditBox nameBox;

    public DroneScreen(OpenDronePayload state) {
        super(Component.translatable("item." + SignalTower.MOD_ID + ".radio_drone"));
        this.state = state;
        this.settings = state.settings() & RadioDroneEntity.SETTINGS_MASK;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 76;
        String keep = nameBox != null ? nameBox.getValue() : state.name();
        nameBox = new EditBox(font, centerX - 100, top + 34, 200, 20, Component.translatable(KEY + "name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(keep);
        nameBox.setHint(Component.translatable(KEY + "name_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        int y = top + 58;
        y = toggle(centerX, y, RadioDroneEntity.BEACON, "beacon");
        y = toggle(centerX, y, RadioDroneEntity.RELAY, "relay");
        toggle(centerX, y, RadioDroneEntity.NIGHT_VISION, "night");
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(centerX - 102, top + 136, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(centerX + 2, top + 136, 100, 20).build());
    }

    private int toggle(int centerX, int y, int bit, String name) {
        addRenderableWidget(CycleButton.onOffBuilder((settings & bit) != 0)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + name + "_tip")))
                .create(centerX - 100, y, 200, 20, Component.translatable(KEY + name),
                        (button, value) -> settings = value ? settings | bit : settings & ~bit));
        return y + 24;
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureDronePayload(state.entityId(), settings, nameBox.getValue()));
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
        int percent = (int) Math.round(state.charge() * 100.0 / RadioDroneEntity.CAPACITY);
        graphics.drawCenteredString(font, Component.translatable(KEY + "status", FrequencyNames.label(null, state.frequency()), percent),
                centerX, top + 16, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
