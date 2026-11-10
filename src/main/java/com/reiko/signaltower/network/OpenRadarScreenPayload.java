package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadarDishBlockEntity;
import com.reiko.signaltower.blockentity.RadarScreenBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server to client (v0.65.0): open a Radar Dish's or Radar Screen's settings, with its frequency,
 * its settings, the reach (the dish's own, or the one the screen shows; 0 for none) and what's seen
 * (a dish: how many of each kind; a screen: the contact count, or -1 while it's locked).
 */
public record OpenRadarScreenPayload(BlockPos pos, boolean dish, int frequency, int settings, int reach, List<Integer> seen)
        implements CustomPacketPayload {
    public static final Type<OpenRadarScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_radar_screen"));

    public static final StreamCodec<ByteBuf, OpenRadarScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenRadarScreenPayload::pos,
            ByteBufCodecs.BOOL, OpenRadarScreenPayload::dish,
            ByteBufCodecs.VAR_INT, OpenRadarScreenPayload::frequency,
            ByteBufCodecs.VAR_INT, OpenRadarScreenPayload::settings,
            ByteBufCodecs.VAR_INT, OpenRadarScreenPayload::reach,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), OpenRadarScreenPayload::seen,
            OpenRadarScreenPayload::new);

    public static OpenRadarScreenPayload of(ServerLevel level, RadarDishBlockEntity dish) {
        BlockPos pos = dish.getBlockPos();
        List<Integer> counts = java.util.Arrays.stream(dish.counts()).boxed().toList();
        return new OpenRadarScreenPayload(pos, true, dish.getFrequency(), dish.getSettings(),
                RadioTransmitterBlockEntity.reachNow(level, pos, dish.getBlockState()), counts);
    }

    public static OpenRadarScreenPayload of(ServerLevel level, RadarScreenBlockEntity screen) {
        return new OpenRadarScreenPayload(screen.getBlockPos(), false, screen.getFrequency(), screen.getSettings(),
                screen.isShowing() ? screen.dishReach() : 0, List.of(screen.lockedBy().isEmpty() ? screen.contactCount() : -1));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenRadarScreenPayload payload, IPayloadContext context) {
        ClientHooks.openRadarScreen(payload);
    }
}
