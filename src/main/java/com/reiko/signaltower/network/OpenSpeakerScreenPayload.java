package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.43.0): open a Radio Speaker's screen, with its frequency, its settings
 * ({@link RadioReceiverBlockEntity#packSpeaker packed}), what it hears and whether it's deaf there.
 */
public record OpenSpeakerScreenPayload(BlockPos pos, int frequency, int speaker, int heard, boolean deaf)
        implements CustomPacketPayload {
    public static final Type<OpenSpeakerScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_speaker_screen"));

    public static final StreamCodec<ByteBuf, OpenSpeakerScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenSpeakerScreenPayload::pos,
            ByteBufCodecs.VAR_INT, OpenSpeakerScreenPayload::frequency,
            ByteBufCodecs.VAR_INT, OpenSpeakerScreenPayload::speaker,
            ByteBufCodecs.VAR_INT, OpenSpeakerScreenPayload::heard,
            ByteBufCodecs.BOOL, OpenSpeakerScreenPayload::deaf,
            OpenSpeakerScreenPayload::new);

    public static OpenSpeakerScreenPayload of(ServerLevel level, RadioReceiverBlockEntity rx) {
        boolean deaf = !FrequencyClaims.mayHear(level, rx.getFrequency(), rx.getTunedBy());
        int heard = deaf ? 0 : SignalNetwork.get(level).listen(level, rx.getFrequency(), rx.getBlockPos()).power();
        return new OpenSpeakerScreenPayload(rx.getBlockPos(), rx.getFrequency(), rx.packedSpeaker(), heard, deaf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenSpeakerScreenPayload payload, IPayloadContext context) {
        ClientHooks.openSpeakerScreen(payload);
    }
}
