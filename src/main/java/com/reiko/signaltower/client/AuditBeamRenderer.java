package com.reiko.signaltower.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Draws the {@link AuditBeams} in the player's dimension as gold beacon beams, from the top of the
 * block up to the build limit. Drawn after block entities, the same way a beacon draws its beam, so
 * it shows from far away (it isn't tied to the block's chunk being loaded); far beams are drawn
 * wider so they don't thin out to nothing, and without fog so they don't fade into the sky.
 */
public final class AuditBeamRenderer {
    /** Gold, so it doesn't look like a real beacon's. */
    private static final int COLOR = 0xFFFFC21A;

    private AuditBeamRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || AuditBeams.count() == 0) {
            return;
        }
        List<AuditBeams.Beam> beams = AuditBeams.in(minecraft.level.dimension().location().toString());
        if (beams.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = minecraft.level.getGameTime();
        // No fog on them, so a beam far out still shows gold instead of fading into the sky. Set for
        // the whole loop: the buffer draws each layer as soon as the next one starts.
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        try {
            for (AuditBeams.Beam beam : beams) {
                double dx = beam.pos().getX() + 0.5 - camera.x;
                double dz = beam.pos().getZ() + 0.5 - camera.z;
                float widen = widening(Math.sqrt(dx * dx + dz * dz));
                pose.pushPose();
                pose.translate(beam.pos().getX() - camera.x, beam.pos().getY() - camera.y, beam.pos().getZ() - camera.z);
                BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, gameTime,
                        1, BeaconRenderer.MAX_RENDER_Y, COLOR, 0.2F * widen, 0.25F * widen);
                pose.popPose();
            }
            buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
            buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
        }
    }

    /**
     * How much wider than a beacon's to draw a beam {@code distance} blocks away (horizontally):
     * normal up to 48 blocks, then growing with distance, so it stays a few pixels wide instead of
     * thinning to a hairline far off.
     */
    public static float widening(double distance) {
        return (float) Math.max(1.0, distance / 48.0);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (AuditBeams.count() > 0 && !Minecraft.getInstance().isPaused()) {
            AuditBeams.tick();
        }
    }
}
