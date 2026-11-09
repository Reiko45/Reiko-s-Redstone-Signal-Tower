package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.signaltower.blockentity.GuyAnchorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a Guy Wire Anchor's wire (v0.54.0): a taut steel line from its eye bolt to two thirds of the
 * way up the mast column it braces (two lines a hair apart, so it reads at a distance).
 */
public class GuyWireRenderer implements BlockEntityRenderer<GuyAnchorBlockEntity> {
    public GuyWireRenderer(BlockEntityRendererProvider.Context context) {}

    /** Where the wire meets the mast, relative to the anchor. */
    private static Vec3 attach(GuyAnchorBlockEntity anchor, BlockPos base) {
        int height = anchor.clientHeight();
        double up = Math.max(1, height * 2 / 3.0);
        BlockPos pos = anchor.getBlockPos();
        return new Vec3(base.getX() + 0.5 - pos.getX(), base.getY() + up - pos.getY(), base.getZ() + 0.5 - pos.getZ());
    }

    @Override
    public void render(GuyAnchorBlockEntity anchor, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos base = anchor.getBase();
        if (base == null || anchor.clientHeight() <= 0) {
            return;
        }
        Vec3 from = new Vec3(0.5, 0.42, 0.5);
        Vec3 to = attach(anchor, base);
        Vec3 normal = to.subtract(from).normalize();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        for (double offset : new double[]{-0.015, 0.015}) {
            lines.addVertex(last, (float) (from.x + offset), (float) from.y, (float) (from.z - offset)).setColor(70, 74, 82, 255)
                    .setNormal(last, (float) normal.x, (float) normal.y, (float) normal.z);
            lines.addVertex(last, (float) (to.x + offset), (float) to.y, (float) (to.z - offset)).setColor(70, 74, 82, 255)
                    .setNormal(last, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(GuyAnchorBlockEntity anchor) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(GuyAnchorBlockEntity anchor) {
        BlockPos pos = anchor.getBlockPos();
        BlockPos base = anchor.getBase();
        if (base == null) {
            return new AABB(pos);
        }
        Vec3 to = attach(anchor, base).add(pos.getX(), pos.getY(), pos.getZ());
        return new AABB(pos).minmax(new AABB(to, to)).inflate(1);
    }
}
