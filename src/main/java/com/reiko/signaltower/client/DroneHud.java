package com.reiko.signaltower.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.network.DroneStatusPayload;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.TvCamera;
import com.reiko.signaltower.radio.WalkieTalkie;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The pilot's panel while flying a Radio Drone (v0.67.0), at the top left: the drone's name and
 * what it's doing, its battery, the signal (bars, or NO SIGNAL), its height, how far off and which
 * way it is, what it carries, and the keys. Through the drone's camera the picture gets snowy
 * past 70% of the way to the edge of the pilot's reach, like a TV Monitor's.
 */
public final class DroneHud {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "drone_hud");
    private static final String KEY = "hud." + SignalTower.MOD_ID + ".drone.";
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private DroneHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DronePilot.isActive() || minecraft.player == null || minecraft.options.hideGui || minecraft.level == null) {
            return;
        }
        DroneStatusPayload status = DronePilot.status();
        RadioDroneEntity drone = DronePilot.drone();
        Font font = minecraft.font;
        float signal = status == null ? 0 : status.signal();
        // Snow over the drone's view near the edge of the reach.
        if (DronePilot.seesThroughDrone()) {
            float snow = TvCamera.snow(signal);
            if (snow > 0) {
                ResourceLocation texture = CameraFeeds.snow(minecraft.level);
                RenderSystem.enableBlend();
                graphics.setColor(1, 1, 1, snow);
                graphics.blit(texture, 0, 0, 0, 0, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight());
                graphics.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
            }
        } else if (DronePilot.cameraTooFar()) {
            Component far = Component.translatable(KEY + "camera_far");
            graphics.drawString(font, far, (graphics.guiWidth() - font.width(far)) / 2, graphics.guiHeight() / 2 - 30, 0xFFFFD25A);
        }
        int x = 6;
        int y = 6;
        int width = 168;
        int lines = 6;
        graphics.fill(x - 3, y - 3, x + width, y + lines * 11 + 1, 0x90000000);
        String name = drone != null ? drone.droneName() : "";
        Component title = name.isEmpty() ? Component.translatable(KEY + "title") : Component.translatable(KEY + "title_named", name);
        RadioDroneEntity.State state = status == null ? RadioDroneEntity.State.LANDED : RadioDroneEntity.State.of(status.flags() & 7);
        graphics.drawString(font, title, x, y, 0xFFFFFFFF);
        Component stateText = Component.translatable(KEY + "state_" + state.name().toLowerCase(java.util.Locale.ROOT));
        graphics.drawString(font, stateText, x + width - 6 - font.width(stateText), y, state == RadioDroneEntity.State.SINKING ? 0xFFFF6A5A : 0xFF7CFF9A);
        y += 11;
        // Battery.
        int charge = status == null ? (drone == null ? 0 : drone.getCharge()) : status.charge();
        int percent = (int) Math.round(charge * 100.0 / RadioDroneEntity.CAPACITY);
        int color = percent > 40 ? 0xFF7CFF9A : percent > 15 ? 0xFFFFD25A : 0xFFFF6A5A;
        graphics.drawString(font, Component.translatable(KEY + "battery", percent), x, y, color);
        int barX = x + 70;
        graphics.fill(barX, y + 1, barX + 90, y + 7, 0xFF303030);
        graphics.fill(barX, y + 1, barX + Math.max(0, Math.min(90, percent * 90 / 100)), y + 7, color);
        y += 11;
        // Signal.
        if (status == null || signal > 1.0F) {
            graphics.drawString(font, Component.translatable(KEY + "no_signal").withStyle(ChatFormatting.RED), x, y, 0xFFFF6A5A);
        } else {
            int bars = WalkieTalkie.bars(signal);
            graphics.drawString(font, Component.translatable(KEY + "signal"), x, y, 0xFFFFFFFF);
            for (int i = 0; i < 4; i++) {
                int h = 2 + i * 2;
                graphics.fill(x + 40 + i * 5, y + 8 - h, x + 43 + i * 5, y + 8, i < bars ? 0xFF7CFF9A : 0xFF404040);
            }
        }
        y += 11;
        // Where it is.
        BlockPos at = drone != null ? drone.blockPosition() : status == null ? null : status.pos();
        if (at != null) {
            BlockPos me = minecraft.player.blockPosition();
            int dx = at.getX() - me.getX();
            int dz = at.getZ() - me.getZ();
            int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            int direction = SignalNetwork.compassDirection(dx, dz);
            Component where = direction < 0 ? Component.translatable(KEY + "overhead")
                    : Component.translatable(KEY + "away", distance, Component.translatable("screen." + SignalTower.MOD_ID + ".locator.dir_" + DIRECTIONS[direction]));
            graphics.drawString(font, Component.translatable(KEY + "height", at.getY(), where), x, y, 0xFFCCCCCC);
        }
        y += 11;
        // What it carries.
        ItemStack carried = drone != null ? drone.getCarried() : ItemStack.EMPTY;
        Component carrying = !carried.isEmpty() ? Component.translatable(KEY + "carrying", carried.getHoverName(), carried.getCount())
                : status != null && (status.flags() & 8) != 0 ? Component.translatable(KEY + "carrying_something")
                : Component.translatable(KEY + "carrying_nothing");
        graphics.drawString(font, carrying, x, y, 0xFFCCCCCC);
        y += 11;
        Component keys = Component.translatable(KEY + "keys", DronePilot.CAMERA.getTranslatedKeyMessage(), DronePilot.GRAB.getTranslatedKeyMessage());
        graphics.drawString(font, keys, x, y, 0xFF9A9A9A);
    }
}
