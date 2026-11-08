package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.network.ConfigureTimerPayload;
import com.reiko.signaltower.network.OpenTimerScreenPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * A Radio Timer Transmitter's screen (v0.42.0): Mode (Repeating or Daily), then either Every and
 * Pulse, or On at and Off at; Shift-click or scroll goes back through a button's values. Below,
 * what it's doing now: sending, paused by redstone or silenced, and its next pulse counting down
 * (or the time of day and when it turns on or off). While the schedule picked differs from the
 * saved one, a yellow line says what Done will set. Done or Enter saves, Escape cancels.
 */
public class TimerScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".timer.";

    private final OpenTimerScreenPayload state;
    private boolean daily;
    private int interval;
    private int pulse;
    private int onHour;
    private int offHour;
    private boolean rebuild;
    private int top;
    /** The game time when the screen opened, to count the next pulse down. */
    private final long openedAt;

    public TimerScreen(OpenTimerScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_timer_transmitter"));
        this.state = state;
        this.daily = state.daily();
        this.interval = TimerTransmitterBlockEntity.validInterval(state.interval());
        this.pulse = TimerTransmitterBlockEntity.validPulse(state.pulse());
        this.onHour = TimerTransmitterBlockEntity.validHour(state.onHour());
        this.offHour = TimerTransmitterBlockEntity.validHour(state.offHour());
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
        top = height / 2 - 82;
        addRenderableWidget(CycleButton.<Boolean>builder(value -> Component.translatable(KEY + (value ? "mode_daily" : "mode_interval")))
                .withValues(false, true)
                .withInitialValue(daily)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + (value ? "mode_daily_tip" : "mode_interval_tip"))))
                .create(centerX - 100, top + 32, 200, 20, Component.translatable(KEY + "mode"), (button, value) -> {
                    daily = value;
                    rebuild = true;
                }));
        if (daily) {
            List<Integer> hours = IntStream.range(0, 24).boxed().toList();
            addRenderableWidget(CycleButton.<Integer>builder(TimerTransmitterBlockEntity::hourLabel)
                    .withValues(hours)
                    .withInitialValue(onHour)
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "on_hour_tip")))
                    .create(centerX - 100, top + 56, 200, 20, Component.translatable(KEY + "on_hour"), (button, value) -> onHour = value));
            addRenderableWidget(CycleButton.<Integer>builder(TimerTransmitterBlockEntity::hourLabel)
                    .withValues(hours)
                    .withInitialValue(offHour)
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "off_hour_tip")))
                    .create(centerX - 100, top + 80, 200, 20, Component.translatable(KEY + "off_hour"), (button, value) -> offHour = value));
        } else {
            addRenderableWidget(CycleButton.<Integer>builder(TimerTransmitterBlockEntity::durationLabel)
                    .withValues(Arrays.stream(TimerTransmitterBlockEntity.INTERVALS).boxed().toList())
                    .withInitialValue(interval)
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "interval_tip")))
                    .create(centerX - 100, top + 56, 200, 20, Component.translatable(KEY + "interval"), (button, value) -> {
                        interval = value;
                        rebuild = true; // the pulse it can send may change
                    }));
            addRenderableWidget(CycleButton.<Integer>builder(value -> TimerTransmitterBlockEntity.durationLabel(
                            TimerTransmitterBlockEntity.effectivePulse(interval, value)))
                    .withValues(Arrays.stream(PulseLength.presets()).boxed().toList())
                    .withInitialValue(pulse)
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "pulse_tip")))
                    .create(centerX - 100, top + 80, 200, 20, Component.translatable(KEY + "pulse"), (button, value) -> pulse = value));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 146, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 146, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuild) {
            rebuild = false;
            rebuildWidgets();
        }
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureTimerPayload(state.pos(), daily, interval, pulse, onHour, offHour));
        onClose();
    }

    /** True if the schedule picked here differs from the saved one. */
    private boolean changed() {
        if (daily != state.daily()) {
            return true;
        }
        return daily ? onHour != state.onHour() || offHour != state.offHour()
                : interval != state.interval() || TimerTransmitterBlockEntity.effectivePulse(interval, pulse)
                        != TimerTransmitterBlockEntity.effectivePulse(state.interval(), state.pulse());
    }

    /** How many hours a day a daily timer from {@code on} to {@code off} is on. */
    static int hoursOn(int on, int off) {
        return Math.floorMod(off - on, 24);
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

        Component status;
        if (state.silenced()) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(state.frequency());
            status = Component.translatable(KEY + "silenced", frequency, claim != null ? claim.ownerName() : "?").withStyle(ChatFormatting.RED);
        } else if (state.paused()) {
            status = Component.translatable(KEY + "paused").withStyle(ChatFormatting.GOLD);
        } else {
            status = sendingNow() ? Component.translatable(KEY + "sending", frequency).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "not_sending").withStyle(ChatFormatting.GRAY);
        }
        graphics.drawCenteredString(font, status, centerX, top + 106, 0xFFFFFF);
        graphics.drawCenteredString(font, nextLine(), centerX, top + 118, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "hint"), centerX, top + 130, 0x808080);
    }

    /**
     * Where its saved interval is now, ticks into the current round (counting on from when the
     * screen opened); -1 for a daily timer.
     */
    private long phaseNow() {
        if (state.daily() || state.changeIn() == 0) {
            return -1;
        }
        int length = TimerTransmitterBlockEntity.effectivePulse(state.interval(), state.pulse());
        long phase = state.changeIn() < 0 ? length + state.changeIn() : state.interval() - state.changeIn();
        return Math.floorMod(phase + elapsed(), (long) state.interval());
    }

    /** Whether its saved schedule has it sending now (counted on from when the screen opened). */
    private boolean sendingNow() {
        if (state.daily()) {
            ClientLevel level = minecraft != null ? minecraft.level : null;
            return level != null ? TimerTransmitterBlockEntity.dailyOn(level.getDayTime(), state.onHour(), state.offHour()) : state.sending();
        }
        long phase = phaseNow();
        return phase >= 0 ? phase < TimerTransmitterBlockEntity.effectivePulse(state.interval(), state.pulse()) : state.sending();
    }

    /** The yellow line: what Done will set, the next pulse counting down, or the time of day and the next change. */
    private Component nextLine() {
        if (changed()) {
            Component schedule = TimerTransmitterBlockEntity.scheduleText(daily, interval, pulse, onHour, offHour);
            return (daily ? Component.translatable(KEY + "preview_daily", schedule, hoursOn(onHour, offHour))
                    : Component.translatable(KEY + "preview", schedule)).withStyle(ChatFormatting.YELLOW);
        }
        if (!state.daily()) {
            long phase = phaseNow();
            int length = TimerTransmitterBlockEntity.effectivePulse(state.interval(), state.pulse());
            if (phase < 0) {
                return Component.empty();
            }
            return (phase < length
                    ? Component.translatable(KEY + "pulse_ends", RadioReceiverBlockEntity.countdownLabel(length - phase))
                    : Component.translatable(KEY + "next_pulse", RadioReceiverBlockEntity.countdownLabel(state.interval() - phase)))
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (state.onHour() == state.offHour()) {
            return Component.translatable(KEY + "never").withStyle(ChatFormatting.YELLOW);
        }
        ClientLevel level = minecraft != null ? minecraft.level : null;
        long dayTime = level != null ? level.getDayTime() : state.dayTime();
        long clock = Math.floorMod(dayTime + 6000, 24000L);
        Component now = Component.literal(String.format(Locale.ROOT, "%02d:%02d", clock / 1000, clock % 1000 * 60 / 1000));
        boolean on = TimerTransmitterBlockEntity.dailyOn(dayTime, state.onHour(), state.offHour());
        return Component.translatable(KEY + (on ? "clock_on" : "clock_off"), now,
                TimerTransmitterBlockEntity.hourLabel(on ? state.offHour() : state.onHour())).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
