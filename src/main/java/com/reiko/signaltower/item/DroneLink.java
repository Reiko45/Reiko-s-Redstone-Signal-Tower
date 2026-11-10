package com.reiko.signaltower.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** v0.67.0: the Radio Drone a Drone Controller is paired with: which one, its name and frequency when paired (for the tooltip). */
public record DroneLink(UUID drone, String name, int frequency) {
    public static final Codec<DroneLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("drone").forGetter(DroneLink::drone),
            Codec.STRING.optionalFieldOf("name", "").forGetter(DroneLink::name),
            Codec.INT.optionalFieldOf("frequency", 0).forGetter(DroneLink::frequency)
    ).apply(instance, DroneLink::new));

    public static final StreamCodec<ByteBuf, DroneLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DroneLink::drone,
            ByteBufCodecs.stringUtf8(96), DroneLink::name,
            ByteBufCodecs.VAR_INT, DroneLink::frequency,
            DroneLink::new);
}
