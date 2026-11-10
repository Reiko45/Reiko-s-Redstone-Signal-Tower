package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.SecurityConsoleBlock;
import com.reiko.signaltower.blockentity.SecurityConsoleBlockEntity;
import com.reiko.signaltower.radio.SecuritySetup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Draws a Security Console's face (v0.68.0), full bright: "SECURITY", its state in big letters
 * (DISARMED in green, ARMING 7 and ENTRY 4 in amber, ARMED in red, ALARM flashing red), and its eight
 * zones in two columns, a light each: green clear, amber heard now, red flashing for the zone that
 * set off the alarm (or the entry delay), dark for a zone not set up. Only within 32 blocks.
 */
public class SecurityConsoleRenderer implements BlockEntityRenderer<SecurityConsoleBlockEntity> {
    private static final float SCALE = 1.0F / 96.0F;
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".security.face_";
    private static final int GREEN = 0xFF55FF55;
    private static final int AMBER = 0xFFFFC040;
    private static final int RED = 0xFFFF4040;
    private static final int DARK = 0xFF404040;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int DIM = 0xFF909090;

    private final Font font;

    public SecurityConsoleRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(SecurityConsoleBlockEntity console, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (console.getLevel() == null) {
            return;
        }
        long now = console.getLevel().getGameTime();
        boolean blink = (now / 10) % 2 == 0;
        Direction facing = console.getBlockState().getValue(SecurityConsoleBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0.0, 0.0, -0.375 + 0.0035);
        poseStack.scale(SCALE, -SCALE, SCALE);
        // "SECURITY", small.
        small(Component.translatable(KEY + "title"), -38, -39, 0.6F, DIM, poseStack, buffer);
        // The state.
        SecurityConsoleBlockEntity.State state = console.getState();
        long left = Math.max(0, (console.getUntil() - now + 19) / 20);
        Component text = switch (state) {
            case DISARMED -> Component.translatable(KEY + "disarmed");
            case ARMING -> Component.translatable(KEY + "arming", left);
            case ARMED -> Component.translatable(KEY + "armed");
            case ENTRY -> Component.translatable(KEY + "entry", left);
            case ALARM -> Component.translatable(KEY + "alarm");
        };
        int color = switch (state) {
            case DISARMED -> GREEN;
            case ARMING -> AMBER;
            case ARMED -> RED;
            case ENTRY -> blink ? AMBER : 0xFFFFFFFF;
            case ALARM -> blink ? RED : 0xFFFFFFFF;
        };
        float scale = Math.min(1.3F, 78.0F / Math.max(1, font.width(text)));
        poseStack.pushPose();
        poseStack.translate(0, -28, 0);
        poseStack.scale(scale, scale, scale);
        draw(text, -font.width(text) / 2.0F, 0, color, poseStack.last().pose(), buffer);
        poseStack.popPose();
        // The zones.
        SecuritySetup setup = console.getSetup();
        int open = console.getOpenMask();
        boolean alarming = state == SecurityConsoleBlockEntity.State.ALARM || state == SecurityConsoleBlockEntity.State.ENTRY;
        for (int i = 0; i < SecuritySetup.ZONES; i++) {
            float x = i < 4 ? -39 : 2;
            float y = -10 + (i % 4) * 12;
            int frequency = setup.frequency(i);
            int light = frequency <= 0 ? DARK
                    : alarming && console.getAlarmZone() == i ? (blink ? RED : 0xFF601010)
                    : (open & 1 << i) != 0 ? AMBER : GREEN;
            draw(Component.literal("●"), x, y, light, poseStack.last().pose(), buffer);
            if (frequency > 0) {
                String name = setup.label(i);
                int room = (int) (30 / 0.6F);
                if (font.width(name) > room) {
                    name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
                }
                small(Component.literal(name), x + 7, y + 1.5F, 0.6F, TEXT, poseStack, buffer);
            }
        }
        poseStack.popPose();
    }

    private void small(Component text, float x, float y, float scale, int color, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, scale);
        draw(text, 0, 0, color, poseStack.last().pose(), buffer);
        poseStack.popPose();
    }

    private void draw(Component text, float x, float y, int color, Matrix4f matrix, MultiBufferSource buffer) {
        font.drawInBatch(text, x, y, color, false, matrix, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
