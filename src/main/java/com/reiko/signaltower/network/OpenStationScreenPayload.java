package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.WeatherStationBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.compat.ReikosWeatherCompat;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.StationReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client (v0.53.0): open a Radio Weather Station's screen, with its frequency, the
 * reading picked (by ordinal) and whether it announces; which readings can be picked here (a bit
 * per ordinal); every reading's strength and label now, so the screen can show each as you go
 * through them; what it's doing ({@link #SENDING}, {@link #PAUSED}, {@link #SILENCED},
 * {@link #DOWN}); and whether Reiko's Weather is installed.
 */
public record OpenStationScreenPayload(BlockPos pos, int frequency, int mode, boolean announce, int offered,
                                       List<Integer> strengths, List<Component> labels, int status, boolean reikos)
        implements CustomPacketPayload {
    public static final int SENDING = 0;
    public static final int PAUSED = 1;
    public static final int SILENCED = 2;
    /** Knocked out by lightning or out of power. */
    public static final int DOWN = 3;

    public static final Type<OpenStationScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_station_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStationScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.frequency());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.mode());
                ByteBufCodecs.BOOL.encode(buffer, payload.announce());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.offered());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buffer, payload.strengths());
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, payload.labels());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.status());
                ByteBufCodecs.BOOL.encode(buffer, payload.reikos());
            },
            buffer -> new OpenStationScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buffer),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer)));

    public static OpenStationScreenPayload of(ServerLevel level, WeatherStationBlockEntity station) {
        BlockPos pos = station.getBlockPos();
        int offered = 0;
        List<Integer> strengths = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        for (StationReading reading : StationReading.values()) {
            if (reading.offered()) {
                offered |= 1 << reading.ordinal();
            }
            StationReading.Value value = reading.read(level, pos);
            strengths.add(value.strength());
            labels.add(value.label());
        }
        SignalNetwork network = SignalNetwork.get(level);
        int status = network.isSilenced(level, pos) ? SILENCED : station.isPaused(level) ? PAUSED
                : network.isDown(pos) ? DOWN : SENDING;
        return new OpenStationScreenPayload(pos, station.getFrequency(), station.getMode().ordinal(), station.isAnnouncing(), offered,
                strengths, labels, status, ReikosWeatherCompat.available());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenStationScreenPayload payload, IPayloadContext context) {
        ClientHooks.openStationScreen(payload);
    }
}
