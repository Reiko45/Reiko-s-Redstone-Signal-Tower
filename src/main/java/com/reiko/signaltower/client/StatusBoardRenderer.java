package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.StatusBoardBlock;
import com.reiko.signaltower.blockentity.StatusBoardBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Draws a Radio Status Board's rows on its face (v0.43.0): for each frequency a light (green while
 * it's heard, dark red while off, grey if the board can't hear it), its name (or number) and, on
 * the right, the strength, "off" or "enc". Full bright, so it reads at night. Only drawn within 32
 * blocks, like sign text.
 */
public class StatusBoardRenderer implements BlockEntityRenderer<StatusBoardBlockEntity> {
    /** Font pixels per block: the face (14 pixels) is 84 font pixels across. */
    private static final float SCALE = 1.0F / 96.0F;
    private static final int ON = 0xFF55FF55;
    private static final int OFF = 0xFF802020;
    private static final int DEAF = 0xFF808080;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFF9A9A9A;
    private static final int RED = 0xFFFF5555;

    private final Font font;

    public StatusBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(StatusBoardBlockEntity board, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Direction facing = board.getBlockState().getValue(StatusBoardBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Facing south now: the face is 2 pixels out from the wall, at z = -0.375; draw just in front of it.
        poseStack.translate(0.0, 0.0, -0.375 + 0.0035);
        poseStack.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = poseStack.last().pose();
        boolean any = false;
        for (int i = 0; i < StatusBoardBlockEntity.ROWS; i++) {
            int frequency = board.getFrequency(i);
            if (frequency <= 0) {
                continue;
            }
            any = true;
            float y = -37 + i * 19;
            boolean deaf = board.isDeaf(i);
            int strength = board.getStrength(i);
            draw("■", -40, y, deaf ? DEAF : strength > 0 ? ON : OFF, matrix, buffer);
            Component right = deaf ? Component.translatable("screen." + SignalTower.MOD_ID + ".board.enc")
                    : strength > 0 ? Component.literal(Integer.toString(strength)) : Component.translatable("screen." + SignalTower.MOD_ID + ".board.off");
            int rightWidth = font.width(right);
            draw(right, 40 - rightWidth, y, deaf ? RED : strength > 0 ? ON : DIM, matrix, buffer);
            String name = FrequencyNames.nameOf(null, frequency);
            String label = name != null ? name : Integer.toString(frequency);
            int room = 40 - rightWidth - 4 - (-32);
            String cut = font.plainSubstrByWidth(label, room);
            if (cut.length() < label.length() && room > 8) {
                cut = font.plainSubstrByWidth(label, room - font.width("…")) + "…";
            }
            draw(cut, -32, y, TEXT, matrix, buffer);
            if (name != null) {
                // The number under the name, small and dim.
                poseStack.pushPose();
                poseStack.translate(-32, y + 9, 0);
                poseStack.scale(0.6F, 0.6F, 0.6F);
                font.drawInBatch(Integer.toString(frequency), 0, 0, DIM, false, poseStack.last().pose(), buffer,
                        Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
                poseStack.popPose();
            }
        }
        if (!any) {
            // A small hint, sized to fit the face.
            Component hint = Component.translatable("screen." + SignalTower.MOD_ID + ".board.no_rows");
            float scale = Math.min(1.0F, 76.0F / Math.max(1, font.width(hint)));
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            draw(hint, -font.width(hint) / 2.0F, -4, DIM, poseStack.last().pose(), buffer);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void draw(String text, float x, float y, int color, Matrix4f matrix, MultiBufferSource buffer) {
        font.drawInBatch(text, x, y, color, false, matrix, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private void draw(Component text, float x, float y, int color, Matrix4f matrix, MultiBufferSource buffer) {
        font.drawInBatch(text, x, y, color, false, matrix, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
