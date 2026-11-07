package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioMotionSensorBlock;
import com.reiko.signaltower.blockentity.MotionSensorBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.39.0): Done was pressed in a Radio Motion Sensor's screen. */
public record ConfigureSensorPayload(BlockPos pos, int range, int mode) implements CustomPacketPayload {
    public static final Type<ConfigureSensorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_sensor"));

    public static final StreamCodec<ByteBuf, ConfigureSensorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureSensorPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureSensorPayload::range,
            ByteBufCodecs.VAR_INT, ConfigureSensorPayload::mode,
            ConfigureSensorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureSensorPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the sensor's range and mode if the player can reach it (like clicking it) and it's theirs
     * (or they're an operator), and tells them. Returns the message, or null if they can't reach a
     * sensor there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureSensorPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioMotionSensorBlock)
                || !(player.level().getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor)) {
            return null;
        }
        Component message = sensor.configure(player, payload.range(), MotionSensorBlockEntity.Mode.of(payload.mode()));
        player.displayClientMessage(message, true);
        return message;
    }
}
