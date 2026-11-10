package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.66.0): Done was pressed in a TV Camera's settings. */
public record ConfigureTvCameraPayload(BlockPos pos, int settings, String name) implements CustomPacketPayload {
    public static final Type<ConfigureTvCameraPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_tv_camera"));

    public static final StreamCodec<ByteBuf, ConfigureTvCameraPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureTvCameraPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureTvCameraPayload::settings,
            ByteBufCodecs.stringUtf8(TvCameraBlockEntity.NAME_LENGTH * 4), ConfigureTvCameraPayload::name,
            ConfigureTvCameraPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureTvCameraPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the camera's settings and name if the player can reach it (like clicking it) and may (on a
     * private frequency: members only), and tells them. Returns the message, or null if there's no
     * camera they can reach there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureTvCameraPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0) || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(pos) instanceof TvCameraBlockEntity camera)) {
            return null;
        }
        Component message = camera.configure(level, player, payload.settings(), payload.name());
        player.displayClientMessage(message, true);
        return message;
    }
}
