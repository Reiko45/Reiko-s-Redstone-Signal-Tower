package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.signaltower.blockentity.LaunchPadBlockEntity;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the rocket on a Launch Pad (v0.61.0): with a satellite loaded, a white rocket with red fins
 * stands on the pad with the satellite on its nose; launched, it climbs out of sight (lit by its
 * own flame). The exhaust is particles, from the block entity's client tick.
 */
public class LaunchPadRenderer implements BlockEntityRenderer<LaunchPadBlockEntity> {
    private static final float DECK = 6.0F / 16.0F;
    private final BlockRenderDispatcher blocks;
    private final ItemRenderer items;

    public LaunchPadRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
        items = context.getItemRenderer();
    }

    @Override
    public void render(LaunchPadBlockEntity pad, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        boolean flying = pad.isLaunching();
        if (!flying && !pad.hasSatellite()) {
            return;
        }
        double height = flying && pad.getLevel() != null ? pad.height(pad.getLevel().getGameTime(), partialTick) : 0;
        int light = flying ? LightTexture.FULL_BRIGHT : packedLight;
        poseStack.pushPose();
        poseStack.translate(0.5, DECK + height, 0.5);
        // The body: a white column 0.36 wide and 1.4 tall, a nose cone, and four red fins.
        part(poseStack, buffers, Blocks.WHITE_CONCRETE.defaultBlockState(), -0.18F, 0.0F, -0.18F, 0.36F, 1.4F, 0.36F, light, packedOverlay);
        part(poseStack, buffers, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), -0.12F, 1.4F, -0.12F, 0.24F, 0.12F, 0.24F, light, packedOverlay);
        part(poseStack, buffers, Blocks.GRAY_CONCRETE.defaultBlockState(), -0.12F, -0.12F, -0.12F, 0.24F, 0.12F, 0.24F, light, packedOverlay);
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(45 + 90 * i));
            part(poseStack, buffers, Blocks.RED_CONCRETE.defaultBlockState(), 0.16F, 0.0F, -0.03F, 0.18F, 0.42F, 0.06F, light, packedOverlay);
            poseStack.popPose();
        }
        // The satellite on its nose.
        ItemStack satellite = pad.hasSatellite() ? pad.satellite() : new ItemStack(ModRegistry.SATELLITE.get());
        poseStack.translate(0.0, 1.65, 0.0);
        poseStack.scale(0.8F, 0.8F, 0.8F);
        items.renderStatic(satellite, ItemDisplayContext.FIXED, light, packedOverlay, poseStack, buffers, pad.getLevel(), 0);
        poseStack.popPose();
    }

    private void part(PoseStack poseStack, MultiBufferSource buffers, BlockState state, float x, float y, float z,
                      float w, float h, float d, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(w, h, d);
        blocks.renderSingleBlock(state, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LaunchPadBlockEntity pad) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(LaunchPadBlockEntity pad) {
        return pad.isLaunching() ? AABB.INFINITE : new AABB(pad.getBlockPos()).expandTowards(0, 3, 0);
    }
}
