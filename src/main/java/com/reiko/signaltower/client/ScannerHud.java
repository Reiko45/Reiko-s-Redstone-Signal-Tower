package com.reiko.signaltower.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.ScannerTarget;
import com.reiko.signaltower.item.SignalScannerItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * While the player holds a Signal Scanner that's tracking a transmitter, draws an arrow at the top
 * of the screen pointing to it, with the distance (and how far up or down) underneath.
 */
public final class ScannerHud {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "scanner_arrow");
    private static final ResourceLocation ARROW =
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "textures/gui/scanner_arrow.png");
    private static final String KEY = "hud." + SignalTower.MOD_ID + ".scanner_";

    private ScannerHud() {}

    /** The target of a scanner in the main hand, or else in the off-hand. */
    private static @Nullable ScannerTarget heldTarget(Player player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof SignalScannerItem) {
                ScannerTarget target = SignalScannerItem.getTarget(stack);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        ScannerTarget target = heldTarget(player);
        if (target == null) {
            return;
        }
        int centerX = graphics.guiWidth() / 2;
        int arrowY = 14;
        Component label;
        if (!target.dimension().equals(player.level().dimension())) {
            label = Component.translatable(KEY + "other_dimension", target.frequency());
        } else {
            float partial = delta.getGameTimeDeltaPartialTick(true);
            Vec3 at = player.getPosition(partial);
            double dx = target.pos().getX() + 0.5 - at.x;
            double dz = target.pos().getZ() + 0.5 - at.z;
            int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            int dy = target.pos().getY() - Mth.floor(at.y);
            if (distance <= 1 && Math.abs(dy) <= 2) {
                label = Component.translatable(KEY + "here", target.frequency());
            } else {
                float angle = ScannerTarget.relativeAngle(at.x, at.z, player.getViewYRot(partial), target.pos());
                RenderSystem.enableBlend();
                graphics.pose().pushPose();
                graphics.pose().translate(centerX, arrowY, 0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
                graphics.blit(ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
                graphics.pose().popPose();
                RenderSystem.disableBlend();
                label = Math.abs(dy) >= 3
                        ? Component.translatable(KEY + (dy > 0 ? "distance_up" : "distance_down"), target.frequency(), distance, Math.abs(dy))
                        : Component.translatable(KEY + "distance", target.frequency(), distance);
            }
        }
        graphics.drawCenteredString(minecraft.font, label, centerX, arrowY + 11, 0x80F080);
    }
}
