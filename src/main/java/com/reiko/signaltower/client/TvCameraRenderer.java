package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.TvCameraBlock;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import com.reiko.signaltower.radio.TvCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a TV Camera's head (v0.66.0): the {@code block/tv_camera_head} model (with its red REC light
 * lit while a monitor shows it: {@code block/tv_camera_head_rec}), turned on its pivot the way the
 * camera looks: its facing, panned by game time if Pan is on, tilted by its Tilt. So the head points
 * where the picture on the monitors comes from.
 */
public class TvCameraRenderer implements BlockEntityRenderer<TvCameraBlockEntity> {
    public static final ModelResourceLocation HEAD = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "block/tv_camera_head"));
    public static final ModelResourceLocation HEAD_REC = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "block/tv_camera_head_rec"));

    public TvCameraRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TvCameraBlockEntity camera, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = camera.getBlockState();
        if (!(state.getBlock() instanceof TvCameraBlock)) {
            return;
        }
        boolean rec = state.getValue(TvCameraBlock.REC);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(rec ? HEAD_REC : HEAD);
        AttachFace face = state.getValue(TvCameraBlock.FACE);
        Direction facing = state.getValue(TvCameraBlock.FACING);
        int settings = camera.getSettings();
        long gameTime = camera.getLevel() == null ? 0 : camera.getLevel().getGameTime();
        float yaw = facing.toYRot() + TvCamera.panOffset(settings, gameTime, partialTick);
        float pitch = TvCamera.tiltDegrees(face, settings);
        Vec3 pivot = TvCamera.pivot(face, facing);
        poseStack.pushPose();
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        // The model looks south (+z) with its pivot at (8, 8, 8): turn it to yaw, then tip it down by pitch.
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model, 1.0F, 1.0F, 1.0F,
                packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout());
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TvCameraBlockEntity camera) {
        return new AABB(camera.getBlockPos()).inflate(0.4);
    }
}
