package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.network.DroneControlPayload;
import com.reiko.signaltower.network.DroneStatusPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who's flying which Radio Drone (v0.67.0), on the server. A flight starts when a player uses a
 * paired Drone Controller (if the drone is loaded, in reach, charged and theirs to fly) and ends
 * when they use it again, put it away, leave, fly it out of reach, or the drone goes. Their client
 * is told both ways ({@link DroneControlPayload}), and gets the drone's state four times a second
 * ({@link DroneStatusPayload}).
 */
public final class DronePilots {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    private static final Map<UUID, RadioDroneEntity> FLYING = new HashMap<>();

    private DronePilots() {
    }

    /** The drone {@code player} is flying, or null. */
    public static @Nullable RadioDroneEntity droneOf(Player player) {
        RadioDroneEntity drone = FLYING.get(player.getUUID());
        if (drone != null && (drone.isRemoved() || drone.getPilot() != player)) {
            FLYING.remove(player.getUUID());
            return null;
        }
        return drone;
    }

    /**
     * {@code player} tries to take control of the drone {@code droneId} (from their controller).
     * Returns the action bar line: why not, or that they have it.
     */
    public static Component start(Player player, UUID droneId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Component.empty();
        }
        Entity found = level.getEntity(droneId);
        if (!(found instanceof RadioDroneEntity drone)) {
            return Component.translatable(KEY + "drone_not_found").withStyle(ChatFormatting.RED);
        }
        if (!drone.mayUse(player)) {
            Component refused = FrequencyClaims.refusal(level, drone.getFrequency(), player.getUUID(), "private_refused_drone");
            return refused != null ? refused : Component.translatable(KEY + "drone_not_found").withStyle(ChatFormatting.RED);
        }
        if (drone.getPilot() != null && drone.getPilot() != player && !drone.getPilot().isRemoved()) {
            return Component.translatable(KEY + "drone_busy", drone.getPilot().getName()).withStyle(ChatFormatting.RED);
        }
        if (drone.getCharge() < RadioDroneEntity.LOW_CHARGE) {
            return Component.translatable(KEY + "drone_low", drone.percent()).withStyle(ChatFormatting.RED);
        }
        double signal = drone.signalFrom(level, player.blockPosition());
        if (signal > 1.0) {
            return Component.translatable(KEY + "drone_out_of_reach").withStyle(ChatFormatting.RED);
        }
        RadioDroneEntity previous = droneOf(player);
        if (previous != null && previous != drone) {
            stop(player, null);
        }
        drone.setPilot(player);
        FLYING.put(player.getUUID(), drone);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new DroneControlPayload(drone.getId(), true));
        }
        sendStatus(drone);
        return Component.translatable(KEY + "drone_flying", drone.label(), WalkieTalkie.bars(signal)).withStyle(ChatFormatting.GREEN);
    }

    /** {@code player} stops flying (their drone hovers); {@code why} goes on their action bar, if any. */
    public static void stop(Player player, @Nullable Component why) {
        RadioDroneEntity drone = FLYING.remove(player.getUUID());
        if (drone != null && drone.getPilot() == player) {
            drone.setPilot(null);
        }
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.hasDisconnected()) {
            PacketDistributor.sendToPlayer(serverPlayer, new DroneControlPayload(drone == null ? -1 : drone.getId(), false));
        }
        if (why != null) {
            player.displayClientMessage(why, true);
        }
    }

    /** The drone's state for its pilot's panel. */
    public static void sendStatus(RadioDroneEntity drone) {
        if (drone.getPilot() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, DroneStatusPayload.of(drone));
        }
    }

    /** For the GameTests: forget every flight. */
    public static void resetForTests() {
        FLYING.clear();
    }
}
