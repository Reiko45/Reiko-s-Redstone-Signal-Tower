package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.40.0): open a Radio Logic Receiver's screen, with its two frequencies, its
 * mode (a {@link RadioReceiverBlockEntity.LogicMode} ordinal), what it hears on each and outputs
 * right now, and {@code quiet}: {@link #DEAF_FIRST} / {@link #DEAF_SECOND} if it can't hear one
 * because it's someone else's encrypted frequency.
 */
public record OpenLogicScreenPayload(BlockPos pos, int first, int second, int mode, int heardFirst, int heardSecond,
                                     int output, int quiet) implements CustomPacketPayload {
    public static final int DEAF_FIRST = 1;
    public static final int DEAF_SECOND = 2;

    public static final Type<OpenLogicScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_logic_screen"));

    public static final StreamCodec<ByteBuf, OpenLogicScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.first());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.second());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.mode());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.heardFirst());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.heardSecond());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.output());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.quiet());
            },
            buffer -> new OpenLogicScreenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer)));

    public static OpenLogicScreenPayload of(ServerLevel level, RadioReceiverBlockEntity rx) {
        int quiet = (FrequencyClaims.mayHear(level, rx.getFrequency(), rx.getTunedBy()) ? 0 : DEAF_FIRST)
                | (FrequencyClaims.mayHear(level, rx.getSecondFrequency(), rx.getTunedBy()) ? 0 : DEAF_SECOND);
        return new OpenLogicScreenPayload(rx.getBlockPos(), rx.getFrequency(), rx.getSecondFrequency(), rx.getLogicMode().ordinal(),
                rx.heardFirst(), rx.heardSecond(), rx.getBlockState().getValue(com.reiko.signaltower.block.RadioReceiverBlock.POWER), quiet);
    }

    public RadioReceiverBlockEntity.LogicMode modeValue() {
        return RadioReceiverBlockEntity.LogicMode.of(mode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenLogicScreenPayload payload, IPayloadContext context) {
        ClientHooks.openLogicScreen(payload);
    }
}
