package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a Radio Drone (v0.67.0): its body ({@code item/radio_drone_body}, from
 * {@code tools/make_item_models.py}: the hull, four arms, the battery, the camera at the front,
 * lights) turned the way it faces and tipped a little the way it flies, four rotors
 * ({@code item/radio_drone_rotor}) spinning at the ends of the arms while it's in the air
 * (opposite ways in pairs), and what it carries hanging under it.
 */
public class DroneRenderer extends EntityRenderer<RadioDroneEntity> {
    public static final ModelResourceLocation BODY = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "item/radio_drone_body"));
    public static final ModelResourceLocation ROTOR = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "item/radio_drone_rotor"));
    /** Where the rotors are, from the middle (blocks). */
    private static final float ARM = 4.5F / 16.0F;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(RadioDroneEntity drone, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel body = minecraft.getModelManager().getModel(BODY);
        BakedModel rotor = minecraft.getModelManager().getModel(ROTOR);
        float yaw = Mth.rotLerp(partialTick, drone.yRotO, drone.getYRot());
        var motion = drone.getDeltaMovement();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Tip forward or sideways the way it moves (in its own frame: +z forward, -x right).
        double forward = motion.x * -Mth.sin(yaw * Mth.DEG_TO_RAD) + motion.z * Mth.cos(yaw * Mth.DEG_TO_RAD);
        double side = motion.x * Mth.cos(yaw * Mth.DEG_TO_RAD) + motion.z * Mth.sin(yaw * Mth.DEG_TO_RAD);
        poseStack.mulPose(Axis.XP.rotationDegrees((float) Mth.clamp(forward * 40, -18, 18)));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) Mth.clamp(side * 40, -18, 18)));
        // The models are 16 to a block, their middle at (8, 0, 8).
        poseStack.pushPose();
        poseStack.translate(-0.5, 0.0, -0.5);
        renderModel(poseStack, buffers, body, light);
        poseStack.popPose();
        float spin = Mth.lerp(partialTick, drone.rotorO, drone.rotor);
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? -ARM : ARM;
            float z = (i & 2) == 0 ? -ARM : ARM;
            poseStack.pushPose();
            poseStack.translate(x, 0.0, z);
            poseStack.mulPose(Axis.YP.rotation(((i == 0 || i == 3) ? spin : -spin) + i * 0.7F));
            poseStack.translate(-0.5, 0.0, -0.5);
            renderModel(poseStack, buffers, rotor, light);
            poseStack.popPose();
        }
        ItemStack carried = drone.getCarried();
        if (!carried.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.0, -0.18, 0.0);
            poseStack.scale(0.8F, 0.8F, 0.8F);
            minecraft.getItemRenderer().renderStatic(carried, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                    drone.level(), drone.getId());
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(drone, entityYaw, partialTick, poseStack, buffers, light);
    }

    private static void renderModel(PoseStack poseStack, MultiBufferSource buffers, BakedModel model, int light) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), null, model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.cutout());
    }

    @Override
    public ResourceLocation getTextureLocation(RadioDroneEntity drone) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
