package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.SecurityConsoleBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.SecuritySetup;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.68.0): open a Security Console's screen: its control frequency, its setup,
 * and its state, open zones and log ({@link SecurityConsoleBlockEntity#screenData}). Only sent to
 * someone who may use it.
 */
public record OpenSecurityPayload(BlockPos pos, int frequency, SecuritySetup setup, CompoundTag data) implements CustomPacketPayload {
    public static final Type<OpenSecurityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_security"));

    public static final StreamCodec<ByteBuf, OpenSecurityPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenSecurityPayload::pos,
            ByteBufCodecs.VAR_INT, OpenSecurityPayload::frequency,
            SecuritySetup.STREAM_CODEC, OpenSecurityPayload::setup,
            ByteBufCodecs.COMPOUND_TAG, OpenSecurityPayload::data,
            OpenSecurityPayload::new);

    public static OpenSecurityPayload of(ServerLevel level, SecurityConsoleBlockEntity console) {
        return new OpenSecurityPayload(console.getBlockPos(), console.getFrequency(), console.getSetup(),
                console.screenData(level.getGameTime()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSecurityPayload payload, IPayloadContext context) {
        ClientHooks.openSecurity(payload);
    }
}
