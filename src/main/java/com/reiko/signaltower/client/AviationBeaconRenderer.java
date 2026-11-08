package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.AviationBeaconBlock;
import com.reiko.signaltower.blockentity.AviationBeaconBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws an Aviation Beacon's light while it's on (v0.44.0): its dome lit up, and a glow around it
 * that always faces the camera. The glow grows with distance, so far off it stays a bright dot of
 * about the same size instead of shrinking to nothing, and it isn't fogged, so a tower shows as far
 * as its chunk is drawn. Added on top of what's behind it (like light), and hidden by what's in front.
 */
public class AviationBeaconRenderer implements BlockEntityRenderer<AviationBeaconBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "textures/misc/aviation_beacon_glow.png");
    /** Half the dome's size, a little over it so the lit box covers it. */
    private static final float DOME = 3.1F / 16.0F;
    /** How far it's drawn from: past the usual block entity distance, up to what's loaded. */
    private static final int VIEW_DISTANCE = 1024;

    public AviationBeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AviationBeaconBlockEntity beacon, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!beacon.isLitNow()) {
            return;
        }
        BlockState state = beacon.getBlockState();
        int rgb = state.getValue(AviationBeaconBlock.COLOR).rgb();
        Vec3 center = AviationBeaconBlock.lightCenter(state);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 world = Vec3.atLowerCornerOf(beacon.getBlockPos()).add(center);
        Vec3 toCamera = camera.getPosition().subtract(world);
        double distance = toCamera.length();
        VertexConsumer consumer = buffers.getBuffer(GlowType.GLOW);

        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        litDome(consumer, poseStack.last().pose(), rgb);
        // The glow: a little toward the camera so the dome doesn't cut it, then turned to face it.
        if (distance > 0.5) {
            Vec3 pull = toCamera.scale(0.35 / distance);
            poseStack.translate(pull.x, pull.y, pull.z);
        }
        poseStack.mulPose(camera.rotation());
        float size = glowSize(distance);
        glow(consumer, poseStack.last().pose(), size, scale(rgb, glowStrength(distance)));
        poseStack.popPose();
    }

    /**
     * How wide (half, in blocks) the glow is {@code distance} blocks away: two blocks across up
     * close, then growing with distance so it covers about the same part of the screen.
     */
    public static float glowSize(double distance) {
        return (float) Math.max(1.1, distance / 40.0);
    }

    /** A bit brighter far off, where it's only a few pixels. */
    private static float glowStrength(double distance) {
        return (float) Math.min(1.0, 0.7 + distance / 400.0);
    }

    private static int scale(int rgb, float by) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * by));
        int g = Math.min(255, Math.round((rgb >> 8 & 255) * by));
        int b = Math.min(255, Math.round((rgb & 255) * by));
        return r << 16 | g << 8 | b;
    }

    /** A box just over the dome, in the glow's color (the middle of the glow texture is white). */
    private static void litDome(VertexConsumer consumer, Matrix4f matrix, int rgb) {
        int argb = 0xFF000000 | scale(rgb, 0.85F);
        float d = DOME;
        float[][] faces = {
                {-d, -d, -d, d, -d, -d, d, d, -d, -d, d, -d},   // north
                {-d, -d, d, -d, d, d, d, d, d, d, -d, d},       // south
                {-d, -d, -d, -d, d, -d, -d, d, d, -d, -d, d},   // west
                {d, -d, -d, d, -d, d, d, d, d, d, d, -d},       // east
                {-d, d, -d, d, d, -d, d, d, d, -d, d, d},       // up
                {-d, -d, -d, -d, -d, d, d, -d, d, d, -d, -d},   // down
        };
        for (float[] face : faces) {
            for (int i = 0; i < 12; i += 3) {
                consumer.addVertex(matrix, face[i], face[i + 1], face[i + 2]).setUv(0.5F, 0.5F).setColor(argb);
            }
        }
    }

    /** A square of the glow texture, {@code size} out from the middle, facing the camera. */
    private static void glow(VertexConsumer consumer, Matrix4f matrix, float size, int rgb) {
        int argb = 0xFF000000 | rgb;
        consumer.addVertex(matrix, -size, -size, 0).setUv(0, 1).setColor(argb);
        consumer.addVertex(matrix, -size, size, 0).setUv(0, 0).setColor(argb);
        consumer.addVertex(matrix, size, size, 0).setUv(1, 0).setColor(argb);
        consumer.addVertex(matrix, size, -size, 0).setUv(1, 1).setColor(argb);
    }

    @Override
    public boolean shouldRenderOffScreen(AviationBeaconBlockEntity beacon) {
        return true; // the glow reaches past the block
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(AviationBeaconBlockEntity beacon) {
        // The glow grows with distance (about 50 blocks wide at 1,000 blocks).
        return new AABB(beacon.getBlockPos()).inflate(26);
    }

    /** The glow's render type: the glow texture times the vertex color, added to what's behind, no fog. */
    private static final class GlowType extends RenderType {
        static final RenderType GLOW = create(SignalTower.MOD_ID + "_aviation_glow", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(AviationBeaconRenderer.GLOW, false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private GlowType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                         boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }
    }
}
