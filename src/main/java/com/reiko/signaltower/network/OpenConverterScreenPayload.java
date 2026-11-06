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
public record OpenConverterScreenPayload(BlockPos pos, int listen, int send, int power, int reach, int quiet)
        implements CustomPacketPayload {
    public static final int SILENCED = 1;
    public static final int DEAF = 2;

    public static final Type<OpenConverterScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_converter_screen"));

    public static final StreamCodec<ByteBuf, OpenConverterScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenConverterScreenPayload::pos,
            ByteBufCodecs.VAR_INT, OpenConverterScreenPayload::listen,
            ByteBufCodecs.VAR_INT, OpenConverterScreenPayload::send,
            ByteBufCodecs.VAR_INT, OpenConverterScreenPayload::power,
            ByteBufCodecs.VAR_INT, OpenConverterScreenPayload::reach,
            ByteBufCodecs.VAR_INT, OpenConverterScreenPayload::quiet,
            OpenConverterScreenPayload::new);

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
