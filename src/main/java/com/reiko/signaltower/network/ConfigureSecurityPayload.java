package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.SecurityConsoleBlockEntity;
import com.reiko.signaltower.radio.SecuritySetup;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.68.0): a Security Console's new zones, alarm frequency, delay and schedule. */
public record ConfigureSecurityPayload(BlockPos pos, SecuritySetup setup) implements CustomPacketPayload {
    public static final Type<ConfigureSecurityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_security"));

    public static final StreamCodec<ByteBuf, ConfigureSecurityPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureSecurityPayload::pos,
            SecuritySetup.STREAM_CODEC, ConfigureSecurityPayload::setup,
            ConfigureSecurityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureSecurityPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /** Sets it if the player is near it and may; returns the action bar line (null if there's no console there). Public for the GameTests. */
    public static @Nullable Component apply(Player player, ConfigureSecurityPayload payload) {
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())
                || !(level.getBlockEntity(payload.pos()) instanceof SecurityConsoleBlockEntity console) || !console.inReach(player)) {
            return null;
        }
        Component message = console.configure(level, player, payload.setup());
        player.displayClientMessage(message, true);
        return message;
    }
}
