package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadarScreenBlock;
import com.reiko.signaltower.blockentity.RadarScreenBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.Radar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Draws a Radar Screen's picture on its face (v0.65.0), full bright: a dark green scope with range
 * rings at a third and two thirds, a sweep line turning clockwise with the dish (by game time) and a
 * fading trail behind it, and the dish's contacts as blips that light up as the line passes and fade
 * over the turn: players pale yellow, hostile mobs red, peaceful mobs cyan, vehicles violet; towers
 * as small amber squares; the screen itself as a white cross. North is up, the dish in the middle.
 * Small text gives the frequency, and the range shown with how many contacts are on the scope. Off (no
 * dish) the scope is dark with a line saying why. Only drawn within 48 blocks.
 */
public class RadarScreenRenderer implements BlockEntityRenderer<RadarScreenBlockEntity> {
    /** Font pixels per block: the face (14 pixels) is 84 font pixels across. */
    private static final float SCALE = 1.0F / 96.0F;
    /** The scope's radius and middle, in font pixels. */
    private static final float RADIUS = 34.0F;
    private static final float CY = -4.0F;
    private static final int SEGMENTS = 48;
    /** How far round the trail behind the sweep line reaches (radians), in how many slices. */
    private static final double TRAIL = Math.toRadians(80);
    private static final int TRAIL_SLICES = 24;
    private static final int TEXT = 0xFF7CFF9A;
    private static final int DIM = 0xFF3E8A52;
    private static final int RED = 0xFFFF6A5A;

    private final Font font;

    public RadarScreenRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(RadarScreenBlockEntity screen, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Direction facing = screen.getBlockState().getValue(RadarScreenBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Facing south now: the face is 3 pixels out from the wall, at z = -0.3125; draw just in front of it.
        poseStack.translate(0.0, 0.0, -0.3125 + 0.002);
        poseStack.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer quads = buffers.getBuffer(RenderType.textBackground());
        boolean on = screen.isShowing();
        int shown = 0;
        // The scope.
        disc(quads, matrix, 0, CY, RADIUS, on ? 0xFF03200E : 0xFF07120A, 0.0F);
        if (on) {
            int ring = 0x5530FF70;
            annulus(quads, matrix, RADIUS / 3 - 0.35F, RADIUS / 3 + 0.35F, ring, 0.1F);
            annulus(quads, matrix, RADIUS * 2 / 3 - 0.35F, RADIUS * 2 / 3 + 0.35F, ring, 0.1F);
            annulus(quads, matrix, RADIUS - 0.8F, RADIUS, 0xAA30FF70, 0.1F);
            rect(quads, matrix, -RADIUS, CY - 0.25F, RADIUS, CY + 0.25F, 0x3330FF70, 0.1F);
            rect(quads, matrix, -0.25F, CY - RADIUS, 0.25F, CY + RADIUS, 0x3330FF70, 0.1F);
            long gameTime = screen.getLevel() == null ? 0 : screen.getLevel().getGameTime();
            double sweep = Radar.sweepAngle(gameTime, partialTick);
            trail(quads, matrix, sweep);
            float scale = RADIUS / Math.max(1, Radar.shownRange(screen.getSettings(), screen.dishReach()));
            if ((screen.getSettings() & Radar.SHOW_TOWERS) != 0) {
                int[] towers = screen.towers();
                for (int i = 0; i + 1 < towers.length; i += 2) {
                    float x = towers[i] * scale;
                    float y = towers[i + 1] * scale;
                    if (x * x + y * y <= (RADIUS - 1.5F) * (RADIUS - 1.5F)) {
                        rect(quads, matrix, x - 1.4F, CY + y - 1.4F, x + 1.4F, CY + y + 1.4F, 0xCCFFAA28, 0.3F);
                        rect(quads, matrix, x - 0.6F, CY + y - 0.6F, x + 0.6F, CY + y + 0.6F, 0xFF03200E, 0.35F);
                    }
                }
            }
            int[] contacts = screen.contacts();
            for (int i = 0; i + 3 < contacts.length; i += 4) {
                float x = contacts[i + 1] * scale;
                float y = contacts[i + 2] * scale;
                if (x * x + y * y > (RADIUS - 1.2F) * (RADIUS - 1.2F)) {
                    continue;
                }
                shown++;
                double behind = Math.floorMod((long) Math.round(Math.toDegrees(sweep - Radar.bearing(contacts[i + 1], contacts[i + 2]))), 360L);
                float glow = (float) Math.max(0.22, 1.0 - behind / 360.0);
                int color = blipColor(Radar.Kind.of(contacts[i]), glow);
                float size = 1.5F + glow * 0.6F;
                rect(quads, matrix, x - size, CY + y - size, x + size, CY + y + size, color, 0.5F);
            }
            // Where the screen is (a white cross), and the dish in the middle.
            float hx = -screen.dishDx() * scale;
            float hy = -screen.dishDz() * scale;
            if (hx * hx + hy * hy <= (RADIUS - 2) * (RADIUS - 2)) {
                rect(quads, matrix, hx - 2.2F, CY + hy - 0.45F, hx + 2.2F, CY + hy + 0.45F, 0xEEFFFFFF, 0.6F);
                rect(quads, matrix, hx - 0.45F, CY + hy - 2.2F, hx + 0.45F, CY + hy + 2.2F, 0xEEFFFFFF, 0.6F);
            }
            rect(quads, matrix, -1.0F, CY - 1.0F, 1.0F, CY + 1.0F, 0xFFB0FFC0, 0.6F);
        }
        // The text.
        String key = "screen." + SignalTower.MOD_ID + ".radar.";
        String name = FrequencyNames.nameOf(null, screen.getFrequency());
        small(poseStack, buffers, name != null ? name : Integer.toString(screen.getFrequency()), -41, -41, DIM, 0.5F, false);
        if (on) {
            Component footer = Component.translatable(key + "footer", Radar.shownRange(screen.getSettings(), screen.dishReach()), shown);
            centered(poseStack, buffers, footer, 33.5F, TEXT, 0.5F);
        } else {
            Component why = !screen.lockedBy().isEmpty()
                    ? Component.translatable(key + "face_locked", screen.lockedBy())
                    : Component.translatable(key + "face_none");
            centered(poseStack, buffers, why, CY - 2, screen.lockedBy().isEmpty() ? DIM : RED, 0.55F);
        }
        poseStack.popPose();
    }

    private static int blipColor(Radar.Kind kind, float glow) {
        int rgb = switch (kind) {
            case PLAYER -> 0xFFF58C;
            case HOSTILE -> 0xFF4636;
            case PEACEFUL -> 0x5ADCFF;
            case VEHICLE -> 0xB48CFF;
            case TOWER -> 0xFFAA28;
        };
        int alpha = Math.max(60, Math.min(255, Math.round(glow * 255)));
        return alpha << 24 | rgb;
    }

    /** The trail behind the sweep line: slices from bright (at the line) to nothing, then the line itself. */
    private void trail(VertexConsumer quads, Matrix4f matrix, double sweep) {
        for (int i = 0; i < TRAIL_SLICES; i++) {
            double a1 = sweep - TRAIL * i / TRAIL_SLICES;
            double a2 = sweep - TRAIL * (i + 1) / TRAIL_SLICES;
            int alpha = Math.round(110 * (1.0F - i / (float) TRAIL_SLICES));
            wedge(quads, matrix, a2, a1, RADIUS - 0.8F, alpha << 24 | 0x30FF70, 0.2F);
        }
        float ex = (float) Math.sin(sweep) * (RADIUS - 0.8F);
        float ey = (float) -Math.cos(sweep) * (RADIUS - 0.8F);
        float nx = (float) Math.cos(sweep) * 0.35F;
        float ny = (float) Math.sin(sweep) * 0.35F;
        quad(quads, matrix, -nx, CY - ny, nx, CY + ny, ex + nx, CY + ey + ny, ex - nx, CY + ey - ny, 0xFF90FFB0, 0.4F);
    }

    private static void disc(VertexConsumer quads, Matrix4f matrix, float cx, float cy, float radius, int color, float z) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = Math.PI * 2 * i / SEGMENTS;
            double a2 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float x1 = cx + (float) Math.sin(a1) * radius, y1 = cy - (float) Math.cos(a1) * radius;
            float x2 = cx + (float) Math.sin(a2) * radius, y2 = cy - (float) Math.cos(a2) * radius;
            quad(quads, matrix, cx, cy, x1, y1, x2, y2, x2, y2, color, z);
        }
    }

    private static void annulus(VertexConsumer quads, Matrix4f matrix, float inner, float outer, int color, float z) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = Math.PI * 2 * i / SEGMENTS;
            double a2 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
            float s2 = (float) Math.sin(a2), c2 = (float) -Math.cos(a2);
            quad(quads, matrix, s1 * inner, CY + c1 * inner, s1 * outer, CY + c1 * outer,
                    s2 * outer, CY + c2 * outer, s2 * inner, CY + c2 * inner, color, z);
        }
    }

    /** A slice of the scope from bearing {@code a1} to {@code a2} (clockwise from north). */
    private static void wedge(VertexConsumer quads, Matrix4f matrix, double a1, double a2, float radius, int color, float z) {
        float x1 = (float) Math.sin(a1) * radius, y1 = (float) -Math.cos(a1) * radius;
        float x2 = (float) Math.sin(a2) * radius, y2 = (float) -Math.cos(a2) * radius;
        quad(quads, matrix, 0, CY, x1, CY + y1, x2, CY + y2, x2, CY + y2, color, z);
    }

    private static void rect(VertexConsumer quads, Matrix4f matrix, float x1, float y1, float x2, float y2, int color, float z) {
        quad(quads, matrix, x1, y1, x1, y2, x2, y2, x2, y1, color, z);
    }

    /** One quad, both ways round (so it shows whichever way the corners go). {@code z}: font pixels toward the viewer. */
    private static void quad(VertexConsumer quads, Matrix4f matrix, float ax, float ay, float bx, float by,
                             float cx, float cy, float dx, float dy, int color, float z) {
        float depth = z; // the face points toward +z here
        vertex(quads, matrix, ax, ay, depth, color);
        vertex(quads, matrix, bx, by, depth, color);
        vertex(quads, matrix, cx, cy, depth, color);
        vertex(quads, matrix, dx, dy, depth, color);
        vertex(quads, matrix, dx, dy, depth, color);
        vertex(quads, matrix, cx, cy, depth, color);
        vertex(quads, matrix, bx, by, depth, color);
        vertex(quads, matrix, ax, ay, depth, color);
    }

    private static void vertex(VertexConsumer quads, Matrix4f matrix, float x, float y, float z, int color) {
        quads.addVertex(matrix, x, y, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
    }

    private void small(PoseStack poseStack, MultiBufferSource buffers, String text, float x, float y, int color, float scale, boolean right) {
        poseStack.pushPose();
        poseStack.translate(right ? x - font.width(text) * scale : x, y, 0.8F);
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, 0, 0, color, false, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private void centered(PoseStack poseStack, MultiBufferSource buffers, Component text, float y, int color, float scale) {
        float fit = Math.min(scale, 80.0F / Math.max(1, font.width(text)));
        poseStack.pushPose();
        poseStack.translate(-font.width(text) * fit / 2.0F, y, 0.8F);
        poseStack.scale(fit, fit, fit);
        font.drawInBatch(text, 0, 0, color, false, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
