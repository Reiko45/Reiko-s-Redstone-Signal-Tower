package com.reiko.signaltower.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * What a Radio Signal Recorder's item keeps (v0.44.0): the frequency it records from (0: none),
 * whether it loops, and its recording: its length in ticks and its changes, each
 * {@code tick << 4 | strength}.
 */
public record RecorderTape(int listen, boolean loop, int length, List<Integer> changes) {
    /** The longest recording: 30 seconds. */
    public static final int MAX_TICKS = 600;
    /** The most changes a recording keeps. */
    public static final int MAX_CHANGES = 256;

    public static final Codec<RecorderTape> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Frequency.MAX).optionalFieldOf("listen", 0).forGetter(RecorderTape::listen),
            Codec.BOOL.optionalFieldOf("loop", false).forGetter(RecorderTape::loop),
            Codec.intRange(0, MAX_TICKS).optionalFieldOf("length", 0).forGetter(RecorderTape::length),
            Codec.INT.listOf(0, MAX_CHANGES).optionalFieldOf("changes", List.of()).forGetter(RecorderTape::changes)
    ).apply(instance, RecorderTape::new));

    public static final StreamCodec<ByteBuf, RecorderTape> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RecorderTape::listen,
            ByteBufCodecs.BOOL, RecorderTape::loop,
            ByteBufCodecs.VAR_INT, RecorderTape::length,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_CHANGES)), RecorderTape::changes,
            RecorderTape::new);

    public RecorderTape {
        changes = List.copyOf(changes);
    }
}
