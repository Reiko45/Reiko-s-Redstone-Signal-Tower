package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server to client (v0.61.0): the towers to show on the player's JourneyMap or Xaero's map: their own
 * full-size towers (with reach, for the coverage circles), and the old towers and village radio
 * stations they've been near. Sent when the list changes ({@link com.reiko.signaltower.radio.MapTowers}).
 */
public record MapWaypointsPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;

    /** Own transmitter. */
    public static final int TRANSMITTER = 0;
    /** Own repeater or converter. */
    public static final int RELAY = 1;
    /** Own jammer. */
    public static final int JAMMER = 2;
    /** An old radio tower found in the world. */
    public static final int OLD_TOWER = 3;
    /** A village's radio station. */
    public static final int VILLAGE_STATION = 4;
    /** The tower a Signal Scanner tracks (only made on the client). */
    public static final int TRACKED = 5;

    /** One waypoint: where, what kind, its frequency, and (own towers) its reach: 0 none shown, -1 the whole dimension (uplinked). */
    public record Entry(ResourceLocation dimension, BlockPos pos, int kind, int frequency, int reach) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::dimension,
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.VAR_INT, Entry::kind,
                ByteBufCodecs.VAR_INT, Entry::frequency,
                ByteBufCodecs.VAR_INT, Entry::reach,
                Entry::new);

        public boolean own() {
            return kind <= JAMMER;
        }
    }

    public static final Type<MapWaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "map_waypoints"));

    public static final StreamCodec<ByteBuf, MapWaypointsPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), MapWaypointsPayload::entries,
            MapWaypointsPayload::new);

    public static void handle(MapWaypointsPayload payload, IPayloadContext context) {
        ClientHooks.mapWaypoints(payload.entries());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
