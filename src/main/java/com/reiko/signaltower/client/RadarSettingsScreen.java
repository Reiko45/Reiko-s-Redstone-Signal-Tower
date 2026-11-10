package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadarDishBlockEntity;
import com.reiko.signaltower.network.ConfigureRadarPayload;
import com.reiko.signaltower.network.OpenRadarScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.Radar;
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

import java.util.List;

/**
 * A Radar Dish's or Radar Screen's settings (v0.65.0). A dish: which kinds it tracks, strangers
 * only, and whether it sends what it sees on its frequency. A screen: how far out it shows, whether
 * it pings, and whether it shows towers. Below them, what it saw when the screen opened. Done or
 * Enter saves, Escape cancels.
 */
public class RadarSettingsScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".radar.";

    private final OpenRadarScreenPayload state;
    private int settings;
    private int top;

    public RadarSettingsScreen(OpenRadarScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + (state.dish() ? ".radar_dish" : ".radar_screen")));
        this.state = state;
        this.settings = state.dish() ? Radar.dishSettings(state.settings()) : Radar.screenSettings(state.settings());
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 90;
        int y = top + 32;
        if (state.dish()) {
            for (Radar.Kind kind : Radar.TRACKED) {
                y = toggle(centerX, y, kind.bit(), "track_" + kind.key());
            }
            y = toggle(centerX, y, Radar.STRANGERS_ONLY, "strangers");
            toggle(centerX, y, Radar.SEND, "send");
        } else {
            addRenderableWidget(CycleButton.<Integer>builder(value -> Component.translatable(KEY + "zoom_" + value))
                    .withValues(List.of(0, 1, 2, 3, 4))
                    .withInitialValue(settings & Radar.ZOOM_MASK)
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "zoom_tip")))
                    .create(centerX - 100, y, 200, 20, Component.translatable(KEY + "zoom"),
                            (button, value) -> settings = (settings & ~Radar.ZOOM_MASK) | value));
            y += 24;
            y = toggle(centerX, y, Radar.PING, "ping");
            toggle(centerX, y, Radar.SHOW_TOWERS, "towers");
        }
        int buttons = state.dish() ? top + 196 : top + 136;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, buttons, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, buttons, 100, 20).build());
    }

    /** An On/Off button for {@code bit}; returns where the next one goes. */
    private int toggle(int centerX, int y, int bit, String name) {
        addRenderableWidget(CycleButton.onOffBuilder((settings & bit) != 0)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + name + "_tip")))
                .create(centerX - 100, y, 200, 20, Component.translatable(KEY + name),
                        (button, value) -> settings = value ? settings | bit : settings & ~bit));
        return y + 24;
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureRadarPayload(state.pos(), settings));
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
        Component status;
        if (state.dish()) {
            int[] counts = state.seen().stream().mapToInt(Integer::intValue).toArray();
            status = Component.translatable(KEY + "sees", RadarDishBlockEntity.seenText(counts), state.reach()).withStyle(ChatFormatting.GREEN);
        } else {
            int contacts = state.seen().isEmpty() ? 0 : state.seen().get(0);
            status = contacts < 0 ? Component.translatable(KEY + "locked").withStyle(ChatFormatting.RED)
                    : state.reach() <= 0 ? Component.translatable(KEY + "no_dish", FrequencyNames.label(null, state.frequency())).withStyle(ChatFormatting.GRAY)
                    : Component.translatable(KEY + "shows", Radar.shownRange(settings, state.reach()), contacts).withStyle(ChatFormatting.GREEN);
        }
        graphics.drawCenteredString(font, status, centerX, state.dish() ? top + 178 : top + 112, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
