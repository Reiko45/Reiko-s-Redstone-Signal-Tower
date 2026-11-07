package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyNotes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to every client: one frequency's note changed ("" means it was removed). */
public record FrequencyNotePayload(int frequency, String note) implements CustomPacketPayload {
    public static final Type<FrequencyNotePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_note"));

    public static final StreamCodec<ByteBuf, FrequencyNotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrequencyNotePayload::frequency,
            ByteBufCodecs.stringUtf8(FrequencyNotes.MAX_LENGTH * 4), FrequencyNotePayload::note,
            FrequencyNotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyNotePayload payload, IPayloadContext context) {
        FrequencyNotes.clientSet(payload.frequency(), payload.note());
    }
}
