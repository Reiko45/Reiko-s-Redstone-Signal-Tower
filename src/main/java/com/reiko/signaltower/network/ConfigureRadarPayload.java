package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadarDishBlockEntity;
import com.reiko.signaltower.blockentity.RadarScreenBlockEntity;
import com.reiko.signaltower.block.RadarScreenBlock;
import com.reiko.signaltower.radio.Radar;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
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

/** Client to server (v0.65.0): Done was pressed in a Radar Dish's or Radar Screen's settings. */
public record ConfigureRadarPayload(BlockPos pos, int settings) implements CustomPacketPayload {
    public static final Type<ConfigureRadarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_radar"));

    public static final StreamCodec<ByteBuf, ConfigureRadarPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureRadarPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureRadarPayload::settings,
            ConfigureRadarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureRadarPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the dish's or screen's settings if the player can reach it (like clicking it) and may (a
     * dish on a private frequency: members only), and tells them. Returns the message, or null if
     * there's no radar block they can reach there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureRadarPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0) || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Component message;
        if (level.getBlockEntity(pos) instanceof RadarDishBlockEntity dish) {
            message = dish.configure(level, player, payload.settings());
        } else if (level.getBlockEntity(pos) instanceof RadarScreenBlockEntity screen) {
            screen.setSettings(payload.settings());
            message = Component.translatable("message." + SignalTower.MOD_ID + ".radar_screen_set",
                    RadarScreenBlock.zoomLabel(screen.getSettings()),
                    Component.translatable("message." + SignalTower.MOD_ID + "." + ((screen.getSettings() & Radar.PING) != 0 ? "radar_ping_on" : "radar_ping_off")))
                    .withStyle(ChatFormatting.AQUA);
        } else {
            return null;
        }
        player.displayClientMessage(message, true);
        return message;
    }
}
