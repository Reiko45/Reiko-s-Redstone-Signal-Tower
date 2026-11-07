package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.MotionSensorBlockEntity;
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

/**
 * Server to client (v0.39.0): open a Radio Motion Sensor's settings screen for its owner, with its
 * frequency, range, who it reacts to ({@code mode}, a {@link MotionSensorBlockEntity.Mode} ordinal),
 * whether it sees someone right now (and who it saw first), whether it's kept quiet on someone
 * else's private frequency, and its reach.
 */
public record OpenSensorScreenPayload(BlockPos pos, int frequency, String ownerName, int range, int mode,
                                      boolean sensing, boolean silenced, String seenName, int reach)
        implements CustomPacketPayload {
    public static final Type<OpenSensorScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_sensor_screen"));

    private static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(16);

    public static final StreamCodec<ByteBuf, OpenSensorScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.frequency());
                NAME.encode(buffer, payload.ownerName());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.range());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.mode());
                ByteBufCodecs.BOOL.encode(buffer, payload.sensing());
                ByteBufCodecs.BOOL.encode(buffer, payload.silenced());
                NAME.encode(buffer, payload.seenName());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.reach());
            },
            buffer -> new OpenSensorScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    NAME.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), NAME.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    public static OpenSensorScreenPayload of(ServerLevel level, MotionSensorBlockEntity sensor, boolean silenced) {
        BlockPos pos = sensor.getBlockPos();
        return new OpenSensorScreenPayload(pos, sensor.getFrequency(), cut(sensor.getOwnerName()), sensor.getRange(),
                sensor.getMode().ordinal(), sensor.isSensing(), silenced, sensor.isSensing() ? cut(sensor.getSeenName()) : "",
                RadioTransmitterBlockEntity.reachOf(level, pos, sensor.getBlockState()));
    }

    private static String cut(String name) {
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    public MotionSensorBlockEntity.Mode modeValue() {
        return MotionSensorBlockEntity.Mode.of(mode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenSensorScreenPayload payload, IPayloadContext context) {
        ClientHooks.openSensorScreen(payload);
    }
}
