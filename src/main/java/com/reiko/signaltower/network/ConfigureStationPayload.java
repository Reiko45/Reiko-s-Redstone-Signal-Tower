package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioWeatherStationBlock;
import com.reiko.signaltower.blockentity.WeatherStationBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.IntruderAlerts;
import com.reiko.signaltower.radio.StationReading;
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

/** Client to server (v0.53.0): Done was pressed in a Radio Weather Station's screen (the reading by ordinal). */
public record ConfigureStationPayload(BlockPos pos, int mode, boolean announce) implements CustomPacketPayload {
    public static final Type<ConfigureStationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_station"));

    public static final StreamCodec<ByteBuf, ConfigureStationPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureStationPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureStationPayload::mode,
            ByteBufCodecs.BOOL, ConfigureStationPayload::announce,
            ConfigureStationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureStationPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets what the station reads and whether it announces, if the player can reach it and may send
     * on its frequency (on someone else's private frequency only its owner and members may: anyone
     * else gets a red refusal and the owner an alert), and tells them what it reads now. A reading
     * that needs Reiko's Weather can't be picked without it. Returns the message, or null if they
     * can't reach a station there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureStationPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0) || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockState(pos).getBlock() instanceof RadioWeatherStationBlock block)
                || !(level.getBlockEntity(pos) instanceof WeatherStationBlockEntity station)) {
            return null;
        }
        Component refused = FrequencyClaims.refusal(level, station.getFrequency(), player.getUUID(), "private_refused_block");
        if (refused != null) {
            player.displayClientMessage(refused, true);
            IntruderAlerts.report(player.getServer(), station.getFrequency(), player, IntruderAlerts.Kind.TUNE, block);
            return refused;
        }
        StationReading mode = StationReading.of(payload.mode());
        if (!mode.offered()) {
            Component message = Component.translatable("message." + SignalTower.MOD_ID + ".station_needs_reikos", mode.label())
                    .withStyle(ChatFormatting.RED);
            player.displayClientMessage(message, true);
            return message;
        }
        station.setSettings(mode, payload.announce());
        StationReading.Value value = station.reading(level);
        Component message = Component.translatable("message." + SignalTower.MOD_ID + ".station_set", mode.label(), value.label(),
                value.strength()).withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(message, true);
        return message;
    }
}
