package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.ConfigureStationPayload;
import com.reiko.signaltower.network.OpenStationScreenPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.StationReading;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A Radio Weather Station's screen (v0.53.0): Reading (Shift-click or scroll goes back), with what
 * each reads there now and its scale, and Announce weather changes. Below, what it's doing: sending
 * the reading, paused by redstone, silenced or down. Without Reiko's Weather a grey line says it
 * offers more readings. Done or Enter saves, Escape cancels.
 */
public class StationScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".station.";

    private final OpenStationScreenPayload state;
    private StationReading mode;
    private boolean announce;
    private int top;

    public StationScreen(OpenStationScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_weather_station"));
        this.state = state;
        this.mode = StationReading.of(state.mode());
        this.announce = state.announce();
    }

    private boolean offered(StationReading reading) {
        return (state.offered() & 1 << reading.ordinal()) != 0;
    }

    private List<StationReading> choices() {
        List<StationReading> list = new ArrayList<>();
        for (StationReading reading : StationReading.values()) {
            if (offered(reading) || reading == mode) {
                list.add(reading);
            }
        }
        return list;
    }

    private int strength(StationReading reading) {
        return reading.ordinal() < state.strengths().size() ? state.strengths().get(reading.ordinal()) : 0;
    }

    private Component reading(StationReading reading) {
        return reading.ordinal() < state.labels().size() ? state.labels().get(reading.ordinal()) : Component.empty();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 82;
        addRenderableWidget(CycleButton.<StationReading>builder(StationReading::label)
                .withValues(choices())
                .withInitialValue(mode)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "mode." + value.key() + "_tip")))
                .create(centerX - 100, top + 32, 200, 20, Component.translatable(KEY + "reading"), (button, value) -> mode = value));
        addRenderableWidget(CycleButton.onOffBuilder(announce)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "announce_tip")))
                .create(centerX - 100, top + 56, 200, 20, Component.translatable(KEY + "announce"), (button, value) -> announce = value));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 146, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 146, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureStationPayload(state.pos(), mode.ordinal(), announce));
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
        Object frequency = FrequencyNames.label(null, state.frequency());
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "frequency", frequency), centerX, top + 14, 0xA0A0A0);

        // What the reading picked says here now, and what its strengths mean.
        Component now = offered(mode) ? Component.translatable(KEY + "now", reading(mode), strength(mode)).withStyle(ChatFormatting.YELLOW)
                : Component.translatable(KEY + "needs_reikos").withStyle(ChatFormatting.RED);
        graphics.drawCenteredString(font, now, centerX, top + 82, 0xFFFFFF);
        graphics.drawCenteredString(font, mode.scale(), centerX, top + 94, 0x808080);

        Component status = switch (state.status()) {
            case OpenStationScreenPayload.SILENCED -> {
                FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(state.frequency());
                yield Component.translatable(KEY + "silenced", frequency, claim != null ? claim.ownerName() : "?").withStyle(ChatFormatting.RED);
            }
            case OpenStationScreenPayload.PAUSED -> Component.translatable(KEY + "paused").withStyle(ChatFormatting.GOLD);
            case OpenStationScreenPayload.DOWN -> Component.translatable(KEY + "down").withStyle(ChatFormatting.RED);
            default -> mode != StationReading.of(state.mode()) ? Component.translatable(KEY + "will_send", strength(mode), frequency)
                    .withStyle(ChatFormatting.AQUA)
                    : strength(mode) > 0 ? Component.translatable(KEY + "sending", strength(mode), frequency).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "sending_zero", frequency).withStyle(ChatFormatting.GRAY);
        };
        graphics.drawCenteredString(font, status, centerX, top + 112, 0xFFFFFF);
        if (!state.reikos()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "more_with_reikos"), centerX, top + 128, 0x707070);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
