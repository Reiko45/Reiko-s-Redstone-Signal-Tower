package com.reiko.signaltower.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** The transmitter a Signal Scanner is tracking: its frequency, and where it is. */
public record ScannerTarget(int frequency, BlockPos pos, ResourceKey<Level> dimension) {
    public static final Codec<ScannerTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("frequency").forGetter(ScannerTarget::frequency),
            BlockPos.CODEC.fieldOf("pos").forGetter(ScannerTarget::pos),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ScannerTarget::dimension)
    ).apply(instance, ScannerTarget::new));

    public static final StreamCodec<ByteBuf, ScannerTarget> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScannerTarget::frequency,
            BlockPos.STREAM_CODEC, ScannerTarget::pos,
            ResourceKey.streamCodec(Registries.DIMENSION), ScannerTarget::dimension,
            ScannerTarget::new);

    public ScannerTarget {
        pos = pos.immutable();
    }

    /**
     * Where the arrow should point, in degrees clockwise from straight ahead (0 = ahead, 90 = to
     * the right, ±180 = behind), for someone at ({@code x}, {@code z}) facing {@code yaw} (the
     * game's yaw: 0 = south, 90 = west). Aims at the middle of the target block.
     */
    public static float relativeAngle(double x, double z, float yaw, BlockPos target) {
        double dx = target.getX() + 0.5 - x;
        double dz = target.getZ() + 0.5 - z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Mth.wrapDegrees(targetYaw - yaw);
    }
}
