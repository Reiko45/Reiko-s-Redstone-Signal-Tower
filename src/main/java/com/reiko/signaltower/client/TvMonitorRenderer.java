package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import com.reiko.signaltower.blockentity.TvMonitorBlockEntity;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.MonitorWall;
import com.reiko.signaltower.radio.TvCamera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws a TV Monitor's picture (v0.66.0): the wall's lead draws it over the whole wall, 16:9 in the
 * middle with black bars as needed, full bright; the other monitors of a wall draw nothing (their
 * panels are under the picture). The picture is the camera's, drawn by this client
 * ({@link CameraFeeds}); snow over it past {@link TvCamera#CLEAR_FRACTION} of the way to the edge
 * of the camera's reach, and a moment of static when the channel changes. Over it (if the overlay
 * is on): "CH 2 · Front Gate" at the top left, a red "● REC" at the top right, and the day and time
 * at the bottom. With no picture: "NO SIGNAL" on snow, "TOO FAR" (the camera is past this player's
 * render distance) or "PRIVATE" (someone else's private frequency).
 */
public class TvMonitorRenderer implements BlockEntityRenderer<TvMonitorBlockEntity> {
    /** Font pixels per block. */
    private static final float SCALE = 1.0F / 96.0F;
    /** The panel's face: 2 pixels out from the wall. */
    private static final float FACE_Z = -0.5F + 2.0F / 16.0F + 0.0025F;
    /** The edge of the wall the picture stays inside (font pixels). */
    private static final float INSET = 4.0F;
    private static final int STATIC_TICKS = 8;

    private final Font font;

    public TvMonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(TvMonitorBlockEntity monitor, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        MonitorWall.Wall wall = monitor.wall();
        if (!wall.isLead() || !(monitor.getLevel() instanceof ClientLevel level)) {
            return; // the lead draws the wall
        }
        Direction facing = monitor.facing();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0.0, 0.0, FACE_Z);
        poseStack.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = poseStack.last().pose();
        // The wall, in font pixels from the lead's middle (x right, y down).
        float left = -48 + INSET;
        float top = -48 + INSET;
        float right = -48 + wall.width() * 96 - INSET;
        float bottom = -48 + wall.height() * 96 - INSET;
        float width = right - left;
        float height = bottom - top;
        // 16:9 in the middle.
        float pw = Math.min(width, height * 16 / 9);
        float ph = pw * 9 / 16;
        float px = left + (width - pw) / 2;
        float py = top + (height - ph) / 2;
        rect(buffers.getBuffer(RenderType.textBackground()), matrix, left, top, right, bottom, 0xFF050505, 0.0F);
        long gameTime = level.getGameTime();
        boolean switching = monitor.switchedAt() != Long.MIN_VALUE && gameTime >= monitor.switchedAt()
                && gameTime - monitor.switchedAt() < STATIC_TICKS;
        String key = "screen." + SignalTower.MOD_ID + ".tv_monitor.";
        float textScale = Math.max(0.4F, ph / 110.0F);
        Component message = null;
        int messageColor = 0xFFFFFFFF;
        boolean snowAll = false;
        if (!monitor.lockedBy().isEmpty()) {
            message = Component.translatable(key + "face_private", monitor.lockedBy());
            messageColor = 0xFFFF6A5A;
        } else if (!monitor.isShowing()) {
            message = Component.translatable(key + "face_no_signal");
            snowAll = true;
        } else {
            BlockPos cameraPos = monitor.cameraPos();
            CameraFeeds.Picture picture = monitor.droneId() >= 0 ? CameraFeeds.pictureOfDrone(level, monitor.droneId())
                    : CameraFeeds.picture(level, cameraPos);
            switch (picture.status()) {
                case LIVE -> {
                    textured(buffers.getBuffer(RenderType.text(picture.texture())), matrix, px, py, px + pw, py + ph, 0xFFFFFFFF, 0.1F);
                    float snow = TvCamera.snow(monitor.fraction());
                    if (snow > 0 && !switching) {
                        textured(buffers.getBuffer(RenderType.text(CameraFeeds.snow(level))), matrix, px, py, px + pw, py + ph,
                                Math.round(snow * 255) << 24 | 0xFFFFFF, 0.2F);
                    }
                }
                case STARTING -> snowAll = true;
                case TOO_FAR -> {
                    message = Component.translatable(key + "face_too_far");
                    messageColor = 0xFFFFD25A;
                }
                case NO_CAMERA -> {
                    message = Component.translatable(key + "face_no_signal");
                    snowAll = true;
                }
            }
            if (monitor.showsOverlay()) {
                overlay(poseStack, buffers, monitor, level, cameraPos, px, py, pw, ph, textScale, partialTick);
            }
        }
        if (snowAll || switching) {
            textured(buffers.getBuffer(RenderType.text(CameraFeeds.snow(level))), matrix, px, py, px + pw, py + ph,
                    switching ? 0xFFFFFFFF : 0xC8FFFFFF, 0.3F);
        }
        if (message != null && !switching) {
            float scale = Math.min(textScale * 2.2F, pw * 0.8F / Math.max(1, font.width(message)));
            float w = font.width(message) * scale;
            // A fresh buffer: asking for the picture's and the text's buffers may have ended the first one.
            rect(buffers.getBuffer(RenderType.textBackground()), matrix, px + pw / 2 - w / 2 - 3 * scale, py + ph / 2 - 6 * scale, px + pw / 2 + w / 2 + 2 * scale,
                    py + ph / 2 + 5 * scale, 0xC0000000, 0.4F);
            text(poseStack, buffers, message, px + pw / 2 - w / 2, py + ph / 2 - 4 * scale, scale, messageColor, 0.5F);
        }
        poseStack.popPose();
    }

    /** "CH 2 · Front Gate", "● REC" and the time, over the picture. */
    private void overlay(PoseStack poseStack, MultiBufferSource buffers, TvMonitorBlockEntity monitor, ClientLevel level,
                         BlockPos cameraPos, float px, float py, float pw, float ph, float scale, float partialTick) {
        String key = "screen." + SignalTower.MOD_ID + ".tv_monitor.";
        String name = monitor.cameraName();
        if (name.isEmpty() && monitor.droneId() < 0 && level.getBlockEntity(cameraPos) instanceof TvCameraBlockEntity camera) {
            name = camera.getName();
        }
        if (name.isEmpty() && monitor.droneId() >= 0) {
            name = net.minecraft.client.resources.language.I18n.get(key + "face_drone");
        }
        if (name.isEmpty()) {
            String frequencyName = FrequencyNames.nameOf(null, monitor.channelFrequency());
            name = frequencyName != null ? frequencyName : Integer.toString(monitor.channelFrequency());
        }
        Component channel = Component.translatable(key + "face_channel", monitor.channelIndex() + 1, name);
        float margin = 3.0F * scale / 0.5F;
        float fit = Math.min(scale, (pw * 0.62F) / Math.max(1, font.width(channel)));
        text(poseStack, buffers, channel, px + margin, py + margin, fit, 0xFFFFFFFF, 0.6F);
        // A blinking REC.
        if ((level.getGameTime() / 10) % 2 == 0) {
            Component rec = Component.translatable(key + "face_rec");
            text(poseStack, buffers, rec, px + pw - margin - font.width(rec) * scale, py + margin, scale, 0xFFFF3B30, 0.6F);
        }
        Object[] dayTime = PagerItem.dayAndTime(level.getDayTime());
        Component time = Component.translatable(key + "face_time", dayTime[0], dayTime[1]);
        text(poseStack, buffers, time, px + margin, py + ph - margin - 8 * scale, scale, 0xFFE8E8E8, 0.6F);
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, Component text, float x, float y, float scale, int color, float z) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, 0, 0, color, true, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void rect(VertexConsumer quads, Matrix4f matrix, float x1, float y1, float x2, float y2, int color, float z) {
        // Both ways round (so it shows whichever way the corners go).
        quads.addVertex(matrix, x1, y1, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x1, y2, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x2, y2, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x2, y1, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x2, y1, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x2, y2, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x1, y2, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        quads.addVertex(matrix, x1, y1, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
    }

    /** A textured quad (the whole texture), both ways round. */
    private static void textured(VertexConsumer quads, Matrix4f matrix, float x1, float y1, float x2, float y2, int color, float z) {
        vertex(quads, matrix, x1, y1, z, 0, 0, color);
        vertex(quads, matrix, x1, y2, z, 0, 1, color);
        vertex(quads, matrix, x2, y2, z, 1, 1, color);
        vertex(quads, matrix, x2, y1, z, 1, 0, color);
        vertex(quads, matrix, x2, y1, z, 1, 0, color);
        vertex(quads, matrix, x2, y2, z, 1, 1, color);
        vertex(quads, matrix, x1, y2, z, 0, 1, color);
        vertex(quads, matrix, x1, y1, z, 0, 0, color);
    }

    private static void vertex(VertexConsumer quads, Matrix4f matrix, float x, float y, float z, float u, float v, int color) {
        quads.addVertex(matrix, x, y, z).setColor(color).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    /** The lead draws the whole wall, so it's drawn while any of the wall can be seen. */
    @Override
    public AABB getRenderBoundingBox(TvMonitorBlockEntity monitor) {
        MonitorWall.Wall wall = monitor.wall();
        BlockPos lead = monitor.getBlockPos();
        if (wall.size() <= 1) {
            return new AABB(lead);
        }
        Direction right = MonitorWall.right(monitor.facing());
        BlockPos far = lead.relative(right, wall.width() - 1).below(wall.height() - 1);
        return new AABB(lead).minmax(new AABB(far));
    }

    @Override
    public boolean shouldRenderOffScreen(TvMonitorBlockEntity monitor) {
        return monitor.wall().size() > 1;
    }
}
