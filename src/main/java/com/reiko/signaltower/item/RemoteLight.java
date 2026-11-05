package com.reiko.signaltower.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The remote's status light after a press: green if at least one receiver heard it, red if none
 * did. Shown until {@code untilGameTime}. Synced to the client but never saved.
 */
public record RemoteLight(boolean heard, long untilGameTime) {
    /** How long the light stays lit after a press: 2 seconds. */
    public static final int SHOW_TICKS = 40;

    public static final StreamCodec<ByteBuf, RemoteLight> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RemoteLight::heard,
            ByteBufCodecs.VAR_LONG, RemoteLight::untilGameTime,
            RemoteLight::new);
}
