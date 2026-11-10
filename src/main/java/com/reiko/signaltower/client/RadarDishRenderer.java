package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadarDishBlock;
import com.reiko.signaltower.blockentity.RadarDishBlockEntity;
import com.reiko.signaltower.radio.Radar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a Radar Dish's dish on its turntable (v0.65.0): the {@code block/radar_dish_rotor} model,
 * turned clockwise (seen from above) once every {@link Radar#SWEEP_TICKS} by game time while the
 * dish works, so it points where the sweep line on every Radar Screen on its frequency points. A
 * dish that doesn't work stands still, facing north.
 */
public class RadarDishRenderer implements BlockEntityRenderer<RadarDishBlockEntity> {
    public static final ModelResourceLocation ROTOR = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "block/radar_dish_rotor"));

    public RadarDishRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RadarDishBlockEntity dish, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ROTOR);
        boolean active = dish.getBlockState().hasProperty(RadarDishBlock.ACTIVE) && dish.getBlockState().getValue(RadarDishBlock.ACTIVE);
        double angle = active && dish.getLevel() != null ? Radar.sweepAngle(dish.getLevel().getGameTime(), partialTick) : 0.0;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        // The model's dish faces north; turning by -angle about +Y turns it clockwise seen from above.
        poseStack.mulPose(Axis.YP.rotation((float) -angle));
        poseStack.translate(-0.5, 0.0, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), dish.getBlockState(), model, 1.0F, 1.0F, 1.0F,
                packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout());
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(RadarDishBlockEntity dish) {
        return new AABB(dish.getBlockPos()).inflate(0.6, 0.0, 0.6).expandTowards(0, 1.2, 0);
    }
}
