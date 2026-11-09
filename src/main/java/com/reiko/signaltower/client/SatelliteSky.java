package com.reiko.signaltower.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.SatelliteOrbit;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Satellites crossing the night sky (v0.61.0): each satellite over the player's dimension (from
 * {@link com.reiko.signaltower.network.SatellitesPayload}) is a steady point of light on its own
 * great circle round the sky, a few minutes an orbit, drawn just after the sky (so behind clouds
 * and everything else). They show as the stars do: from dusk to dawn, dimmed by rain.
 */
public final class SatelliteSky {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "textures/misc/aviation_beacon_glow.png");
    /** How far away the sky's lights are drawn (the stars are at 100). */
    private static final float DISTANCE = 100.0F;
    /** Half a satellite's size there. */
    private static final float SIZE = 1.0F;

    private static @Nullable ResourceLocation dimension;
    private static List<SatelliteOrbit> orbits = List.of();

    private SatelliteSky() {
    }

    public static void set(ResourceLocation forDimension, List<SignalNetwork.Satellite> satellites) {
        List<SatelliteOrbit> next = new ArrayList<>(satellites.size());
        for (SignalNetwork.Satellite satellite : satellites) {
            next.add(SatelliteOrbit.of(satellite));
        }
        dimension = forDimension;
        orbits = List.copyOf(next);
    }

    public static void clear() {
        dimension = null;
        orbits = List.of();
    }

    /** How many satellites are known for the dimension the player is in (for the tests' screenshots and debugging). */
    public static int count() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension().location().equals(dimension) ? orbits.size() : 0;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || orbits.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(dimension)
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            return;
        }
        FogType fog = event.getCamera().getFluidInCamera();
        if (fog == FogType.POWDER_SNOW || fog == FogType.LAVA || fog == FogType.WATER
                || event.getCamera().getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float brightness = level.getStarBrightness(partialTick) * (1.0F - level.getRainLevel(partialTick)) * 2.0F;
        if (brightness <= 0.02F) {
            return;
        }
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        double time = level.getGameTime() + partialTick;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (SatelliteOrbit orbit : orbits) {
            Vector3f d = orbit.direction(time);
            if (d.y <= 0.0F) {
                continue; // below the horizon
            }
            float alpha = Math.min(1.0F, brightness * Math.min(1.0F, d.y / 0.12F)); // dimmer low down, as through more air
            Vector3f u = new Vector3f(d).cross(0, 1, 0);
            if (u.lengthSquared() < 1e-4F) {
                u.set(1, 0, 0);
            }
            u.normalize().mul(SIZE);
            Vector3f v = new Vector3f(u).cross(d).normalize().mul(SIZE);
            Vector3f c = new Vector3f(d).mul(DISTANCE);
            quad(buffer, pose, c, u, v, alpha);
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        FogRenderer.setupNoFog();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GLOW);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, Vector3f c, Vector3f u, Vector3f v, float alpha) {
        float r = 1.0F, g = 0.96F, b = 0.86F;
        // Counter-clockwise as seen from the middle of the sky (the camera), so it faces it.
        buffer.addVertex(pose, c.x + u.x - v.x, c.y + u.y - v.y, c.z + u.z - v.z).setUv(1, 0).setColor(r, g, b, alpha);
        buffer.addVertex(pose, c.x + u.x + v.x, c.y + u.y + v.y, c.z + u.z + v.z).setUv(1, 1).setColor(r, g, b, alpha);
        buffer.addVertex(pose, c.x - u.x + v.x, c.y - u.y + v.y, c.z - u.z + v.z).setUv(0, 1).setColor(r, g, b, alpha);
        buffer.addVertex(pose, c.x - u.x - v.x, c.y - u.y - v.y, c.z - u.z - v.z).setUv(0, 0).setColor(r, g, b, alpha);
    }
}
