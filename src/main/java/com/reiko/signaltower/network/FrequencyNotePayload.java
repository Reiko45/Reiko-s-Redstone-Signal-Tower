package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyNotes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to a client: one frequency's note changed ("" means it was removed, or that it's kept to
 * members and this player isn't one any more). {@code membersOnly}: it's kept to members.
 */
public record FrequencyNotePayload(int frequency, String note, boolean membersOnly) implements CustomPacketPayload {
    public static final Type<FrequencyNotePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_note"));

    public static final StreamCodec<ByteBuf, FrequencyNotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrequencyNotePayload::frequency,
            ByteBufCodecs.stringUtf8(FrequencyNotes.MAX_LENGTH * 4), FrequencyNotePayload::note,
            ByteBufCodecs.BOOL, FrequencyNotePayload::membersOnly,
            FrequencyNotePayload::new);

    public FrequencyNotePayload(int frequency, String note) {
        this(frequency, note, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyNotePayload payload, IPayloadContext context) {
        FrequencyNotes.clientSet(payload.frequency(), payload.note(), payload.membersOnly());
    }
}
