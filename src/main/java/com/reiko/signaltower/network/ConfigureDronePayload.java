package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.67.0): Done was pressed in a Radio Drone's settings. */
public record ConfigureDronePayload(int entityId, int settings, String name) implements CustomPacketPayload {
    public static final Type<ConfigureDronePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_drone"));
    public static final StreamCodec<ByteBuf, ConfigureDronePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfigureDronePayload::entityId,
            ByteBufCodecs.VAR_INT, ConfigureDronePayload::settings,
            ByteBufCodecs.stringUtf8(96), ConfigureDronePayload::name,
            ConfigureDronePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureDronePayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the drone's jobs and name if the player is near it (or flying it) and may (on a private
     * frequency: members only). Returns the message, or null if there's no such drone. Public for
     * the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureDronePayload payload) {
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(payload.entityId()) instanceof RadioDroneEntity drone)) {
            return null;
        }
        if (drone.distanceToSqr(player) > 8 * 8 && drone.getPilot() != player) {
            return null;
        }
        Component message;
        if (!drone.mayUse(player)) {
            message = Component.translatable("message." + SignalTower.MOD_ID + ".drone_not_yours").withStyle(ChatFormatting.RED);
        } else {
            drone.setSettings(payload.settings());
            drone.setDroneName(payload.name());
            message = Component.translatable("message." + SignalTower.MOD_ID + ".drone_set", drone.label()).withStyle(ChatFormatting.AQUA);
        }
        player.displayClientMessage(message, true);
        return message;
    }
}
