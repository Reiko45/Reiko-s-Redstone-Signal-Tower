package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvMonitorBlockEntity;
import com.reiko.signaltower.network.ConfigureTvMonitorPayload;
import com.reiko.signaltower.network.OpenTvMonitorPayload;
import com.reiko.signaltower.radio.Frequency;
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

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A TV Monitor's settings (v0.66.0), for its whole wall: channel 1 is the monitor's own frequency
 * (set with a Frequency Tuner), channels 2 to 6 are typed here (empty for none); the overlay on or
 * off; and Next channel. Done or Enter saves, Escape cancels.
 */
public class TvMonitorScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".tv_monitor.";

    private final OpenTvMonitorPayload state;
    private final EditBox[] boxes = new EditBox[TvMonitorBlockEntity.EXTRA_CHANNELS];
    private int flags;
    private int top;
    private Button doneButton;
    private Button nextButton;

    public TvMonitorScreen(OpenTvMonitorPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".tv_monitor"));
        this.state = state;
        this.flags = state.flags();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 100;
        String[] keep = new String[boxes.length];
        for (int i = 0; i < keep.length; i++) {
            int saved = i < state.channels().size() ? state.channels().get(i) : 0;
            keep[i] = boxes[i] != null ? boxes[i].getValue() : saved > 0 ? Integer.toString(saved) : "";
        }
        for (int i = 0; i < boxes.length; i++) {
            EditBox box = new EditBox(font, centerX - 40, top + 44 + i * 22, 80, 18, Component.translatable(KEY + "channel", i + 2));
            box.setMaxLength(Frequency.MAX_DIGITS);
            box.setFilter(text -> text.chars().allMatch(Character::isDigit));
            box.setValue(keep[i]);
            box.setHint(Component.translatable(KEY + "empty").withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(text -> updateDone());
            boxes[i] = addRenderableWidget(box);
        }
        int y = top + 44 + boxes.length * 22 + 4;
        addRenderableWidget(CycleButton.onOffBuilder((flags & TvMonitorBlockEntity.OVERLAY) != 0)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "overlay_tip")))
                .create(centerX - 100, y, 200, 20, Component.translatable(KEY + "overlay"),
                        (button, value) -> flags = value ? flags | TvMonitorBlockEntity.OVERLAY : flags & ~TvMonitorBlockEntity.OVERLAY));
        y += 26;
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save(false))
                .bounds(centerX - 154, y, 100, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "next"), b -> save(true))
                .tooltip(Tooltip.create(Component.translatable(KEY + "next_tip")))
                .bounds(centerX - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 54, y, 100, 20).build());
        setInitialFocus(boxes[0]);
        updateDone();
    }

    /** The frequency typed in a box: 0 if it's empty, empty optional if it isn't a frequency. */
    private static OptionalInt parse(EditBox box) {
        String text = box.getValue();
        if (text.isEmpty()) {
            return OptionalInt.of(0);
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateDone() {
        if (doneButton == null) {
            return;
        }
        boolean valid = true;
        for (EditBox box : boxes) {
            valid &= box != null && parse(box).isPresent();
        }
        doneButton.active = valid;
        nextButton.active = valid;
    }

    private void save(boolean next) {
        List<Integer> channels = new ArrayList<>();
        for (EditBox box : boxes) {
            OptionalInt value = parse(box);
            if (value.isEmpty()) {
                return;
            }
            channels.add(value.getAsInt());
        }
        PacketDistributor.sendToServer(new ConfigureTvMonitorPayload(state.pos(), channels, flags, next));
        onClose();
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
        int wallWidth = state.wallSize() / 10;
        int wallHeight = state.wallSize() % 10;
        Component wall = wallWidth * wallHeight > 1 ? Component.translatable(KEY + "wall", wallWidth, wallHeight)
                : Component.translatable(KEY + "single");
        graphics.drawCenteredString(font, wall.copy().withStyle(ChatFormatting.GRAY), centerX, top + 12, 0xA0A0A0);
        Component first = Component.translatable(KEY + "channel", 1);
        int y = top + 28;
        graphics.drawString(font, first, centerX - 48 - font.width(first), y, state.channel() == 0 ? 0x55FF55 : 0xFFFFFF);
        graphics.drawString(font, Component.translatable(KEY + "own", FrequencyNames.label(null, state.frequency())), centerX - 40, y, 0xAAAAAA);
        int shown = 1;
        for (int i = 0; i < boxes.length; i++) {
            int rowY = top + 49 + i * 22;
            Component label = Component.translatable(KEY + "channel", i + 2);
            int saved = i < state.channels().size() ? state.channels().get(i) : 0;
            boolean current = false;
            if (saved > 0) {
                current = shown == state.channel();
                shown++;
            }
            graphics.drawString(font, label, centerX - 48 - font.width(label), rowY, current ? 0x55FF55 : 0xFFFFFF);
            OptionalInt typed = parse(boxes[i]);
            if (typed.isPresent() && typed.getAsInt() > 0) {
                String name = FrequencyNames.nameOf(null, typed.getAsInt());
                if (name != null) {
                    graphics.drawString(font, font.plainSubstrByWidth(name, Math.max(0, width - centerX - 56)), centerX + 46, rowY,
                            0x55FFFF);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
