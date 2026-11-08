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

/**
 * Server to client: open a Radio Converter's screen, with its two frequencies and what it's
 * sending right now (the client doesn't know any of that, since block entities aren't synced).
 * {@code quiet}: {@link #SILENCED} if it stays quiet because it sends on someone else's private
 * frequency, plus {@link #DEAF} if it hears nothing because it listens on someone else's encrypted
 * one (0: neither).
 */
public record OpenConverterScreenPayload(BlockPos pos, int listen, int send, int power, int reach, int quiet,
                                         int gate, int threshold, int heard)
        implements CustomPacketPayload {
    /** Without a signal gate (as before v0.41.0). */
    public OpenConverterScreenPayload(BlockPos pos, int listen, int send, int power, int reach, int quiet) {
        this(pos, listen, send, power, reach, quiet, 0, 1, power);
    }

    public static final int SILENCED = 1;
    public static final int DEAF = 2;

    public static final Type<OpenConverterScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_converter_screen"));

    public static final StreamCodec<ByteBuf, OpenConverterScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                for (int value : new int[]{payload.listen(), payload.send(), payload.power(), payload.reach(), payload.quiet(),
                        payload.gate(), payload.threshold(), payload.heard()}) {
                    ByteBufCodecs.VAR_INT.encode(buffer, value);
                }
            },
            buffer -> new OpenConverterScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    /** True if it stays quiet because it sends on someone else's private frequency. */
    public boolean silenced() {
        return (quiet & SILENCED) != 0;
    }

    /** True if it hears nothing because it listens on someone else's encrypted frequency. */
    public boolean deaf() {
        return (quiet & DEAF) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenConverterScreenPayload payload, IPayloadContext context) {
        ClientHooks.openConverterScreen(payload);
    }
}
