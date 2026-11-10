package com.reiko.signaltower.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.item.DroneControllerItem;
import com.reiko.signaltower.network.DroneControlPayload;
import com.reiko.signaltower.network.DroneGrabPayload;
import com.reiko.signaltower.network.DroneInputPayload;
import com.reiko.signaltower.network.DroneStatusPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Flying a Radio Drone, on the pilot's own game (v0.67.0). While the server says you fly one
 * ({@link DroneControlPayload}): your movement keys (WASD, jump to climb, sneak to descend) go to
 * the drone every tick instead of moving you, and the drone turns the way you look. The Drone
 * Camera key (B) puts your view in the drone (while your game has it loaded: not farther than your
 * render distance) and back; the Grab key (G) grabs or drops a stack. Attacking and picking blocks
 * do nothing while you fly, and using the controller lets go.
 */
public final class DronePilot {
    public static final KeyMapping CAMERA = new KeyMapping("key." + SignalTower.MOD_ID + ".drone_camera", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories." + SignalTower.MOD_ID);
    public static final KeyMapping GRAB = new KeyMapping("key." + SignalTower.MOD_ID + ".drone_grab", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories." + SignalTower.MOD_ID);

    private static boolean active;
    private static int droneId = -1;
    /** True while the player wants to see through the drone (it may be too far to, for now). */
    private static boolean wantsCamera;
    private static @Nullable DroneStatusPayload status;
    private static long statusAt;

    private DronePilot() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CAMERA);
        event.register(GRAB);
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean pilots(int entityId) {
        return active && droneId == entityId;
    }

    public static int droneId() {
        return droneId;
    }

    public static @Nullable DroneStatusPayload status() {
        return status;
    }

    /** True while the view is in the drone. */
    public static boolean seesThroughDrone() {
        Minecraft minecraft = Minecraft.getInstance();
        return active && minecraft.getCameraEntity() instanceof RadioDroneEntity drone && drone.getId() == droneId;
    }

    /** True while the player wants the drone's view but the drone is too far for their game to have it. */
    public static boolean cameraTooFar() {
        return active && wantsCamera && !seesThroughDrone();
    }

    public static @Nullable RadioDroneEntity drone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.level == null) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(droneId);
        return entity instanceof RadioDroneEntity drone && !drone.isRemoved() ? drone : null;
    }

    public static void onControl(DroneControlPayload payload) {
        if (payload.on()) {
            active = true;
            droneId = payload.entityId();
            wantsCamera = true;
            status = null;
            switchCamera();
        } else if (payload.entityId() < 0 || payload.entityId() == droneId || !active) {
            stop();
        }
    }

    public static void onStatus(DroneStatusPayload payload) {
        if (active && payload.entityId() == droneId) {
            status = payload;
            Minecraft minecraft = Minecraft.getInstance();
            statusAt = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        }
    }

    private static void stop() {
        active = false;
        droneId = -1;
        wantsCamera = false;
        status = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
    }

    /** Puts the view where the player wants it: in the drone (if the game has it) or back in the player. */
    private static void switchCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        RadioDroneEntity drone = drone();
        Entity wanted = wantsCamera && drone != null ? drone : minecraft.player;
        if (minecraft.getCameraEntity() != wanted) {
            minecraft.setCameraEntity(wanted);
        }
    }

    /** The player's own movement keys steer the drone; the player stands still. */
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active) {
            return;
        }
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            if (active) {
                stop();
            }
            while (CAMERA.consumeClick()) { }
            while (GRAB.consumeClick()) { }
            return;
        }
        while (CAMERA.consumeClick()) {
            if (active) {
                wantsCamera = !wantsCamera;
                switchCamera();
                if (wantsCamera && drone() == null) {
                    player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".drone_camera_far"), true);
                }
            } else if (holdsController(player)) {
                player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".drone_not_flying"), true);
            }
        }
        while (GRAB.consumeClick()) {
            if (active) {
                PacketDistributor.sendToServer(new DroneGrabPayload());
            }
        }
        if (!active) {
            return;
        }
        // The view follows the drone in and out of the game's reach.
        switchCamera();
        Options options = minecraft.options;
        boolean free = minecraft.screen == null;
        float forward = free ? (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0) : 0;
        float strafe = free ? (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0) : 0;
        float vertical = free ? (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0) : 0;
        PacketDistributor.sendToServer(new DroneInputPayload(forward, strafe, vertical, player.getYRot(), player.getXRot()));
    }

    /** Before each frame: the drone on this game looks where the pilot looks, so the drone's view turns at once. */
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        RadioDroneEntity drone = drone();
        if (drone == null || minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        drone.setYRot(player.getYRot());
        drone.yRotO = player.yRotO;
        drone.setXRot(player.getXRot());
        drone.xRotO = player.xRotO;
    }

    /** No attacking or picking blocks while flying (from the drone's view they'd be far away); using the controller lets go. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isAttack() || event.isPickBlock()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (event.isUseItem() && minecraft.player != null && minecraft.gameMode != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            for (InteractionHand hand : InteractionHand.values()) {
                if (minecraft.player.getItemInHand(hand).getItem() instanceof DroneControllerItem) {
                    minecraft.gameMode.useItem(minecraft.player, hand);
                    return;
                }
            }
        }
    }

    /** Seen through the drone's camera, the player's hand and what's in it aren't there. */
    public static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if (seesThroughDrone()) {
            event.setCanceled(true);
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        droneId = -1;
        wantsCamera = false;
        status = null;
    }

    private static boolean holdsController(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof DroneControllerItem || player.getOffhandItem().getItem() instanceof DroneControllerItem;
    }

    /** How long ago (ticks) the last status came. */
    public static long statusAge() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || status == null ? Long.MAX_VALUE : minecraft.level.getGameTime() - statusAt;
    }
}
