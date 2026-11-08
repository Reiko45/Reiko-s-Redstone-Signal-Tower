package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.AviationBeaconBlock;
import com.reiko.signaltower.network.ConfigureBeaconPayload;
import com.reiko.signaltower.radio.AviationLight;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/**
 * An Aviation Beacon's screen (v0.44.0): When (at night, always, or while its tower sends), Pattern
 * (blink, flash, steady) and Color, with a little light that blinks the way it will, and which
 * tower it's on (and whether that's sending). Opened by the client from the beacon's block state.
 * Done or Enter saves, Escape cancels.
 */
public class AviationBeaconScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".beacon.";

    private final BlockPos pos;
    private AviationLight.When when;
    private AviationLight.Pattern pattern;
    private AviationLight.LightColor color;
    private int top;

    public AviationBeaconScreen(BlockPos pos, BlockState state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".aviation_beacon"));
        this.pos = pos;
        this.when = state.getValue(AviationBeaconBlock.WHEN);
        this.pattern = state.getValue(AviationBeaconBlock.PATTERN);
        this.color = state.getValue(AviationBeaconBlock.COLOR);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 76;
        addRenderableWidget(CycleButton.<AviationLight.When>builder(AviationLight.When::label)
                .withValues(Arrays.asList(AviationLight.When.values()))
                .withInitialValue(when)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "when_" + value.getSerializedName() + "_tip")))
                .create(centerX - 100, top + 30, 200, 20, Component.translatable(KEY + "when"), (button, value) -> when = value));
        addRenderableWidget(CycleButton.<AviationLight.Pattern>builder(AviationLight.Pattern::label)
                .withValues(Arrays.asList(AviationLight.Pattern.values()))
                .withInitialValue(pattern)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "pattern_" + value.getSerializedName() + "_tip")))
                .create(centerX - 100, top + 54, 200, 20, Component.translatable(KEY + "pattern"), (button, value) -> pattern = value));
        addRenderableWidget(CycleButton.<AviationLight.LightColor>builder(AviationLight.LightColor::label)
                .withValues(Arrays.asList(AviationLight.LightColor.values()))
                .withInitialValue(color)
                .create(centerX - 100, top + 78, 200, 20, Component.translatable(KEY + "color"), (button, value) -> color = value));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 140, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 140, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(ConfigureBeaconPayload.of(pos, when, pattern, color));
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
    public void tick() {
        super.tick();
        // Closes if the beacon is gone (broken, or popped off its tower).
        if (minecraft != null && (minecraft.level == null
                || !(minecraft.level.getBlockState(pos).getBlock() instanceof AviationBeaconBlock))) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        ClientLevel level = minecraft != null ? minecraft.level : null;
        if (level == null) {
            return;
        }
        // What it will do, with a little light blinking that way (always, as a preview).
        Component text = AviationLight.describe(when, pattern, color);
        int textWidth = font.width(text);
        int x = centerX - (textWidth + 14) / 2;
        boolean on = pattern.onAt(level.getGameTime());
        int rgb = color.rgb();
        int dim = (rgb >> 16 & 255) / 5 << 16 | (rgb >> 8 & 255) / 5 << 8 | (rgb & 255) / 5;
        int lamp = 0xFF000000 | (on ? rgb : dim);
        graphics.fill(x, top + 104, x + 9, top + 113, 0xFF101010);
        graphics.fill(x + 1, top + 105, x + 8, top + 112, lamp);
        graphics.drawString(font, text, x + 14, top + 105, 0xFFFF55);

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AviationBeaconBlock)) {
            return;
        }
        BlockPos tower = AviationBeaconBlock.towerOf(level, pos, state);
        Component towerLine;
        if (tower == null) {
            towerLine = when == AviationLight.When.SENDING
                    ? Component.translatable(KEY + "no_tower_dark").withStyle(ChatFormatting.GOLD)
                    : Component.translatable(KEY + "no_tower").withStyle(ChatFormatting.GRAY);
        } else {
            BlockState towerState = level.getBlockState(tower);
            Component name = Component.translatable("block." + SignalTower.MOD_ID + "." + AviationLight.towerName(towerState));
            int above = tower.getY() - pos.getY();
            towerLine = AviationLight.isSending(towerState)
                    ? Component.translatable(KEY + "tower_sending", name, Math.max(0, above)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "tower_silent", name, Math.max(0, above)).withStyle(ChatFormatting.GRAY);
        }
        graphics.drawCenteredString(font, towerLine, centerX, top + 120, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
