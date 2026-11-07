package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyNotes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server to one client, when they join: every frequency note in the world they may see, and which
 * of those are kept to members.
 */
public record FrequencyNotesPayload(Map<Integer, String> notes, List<Integer> membersOnly) implements CustomPacketPayload {
    public static final Type<FrequencyNotesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "frequency_notes"));

    public static final StreamCodec<ByteBuf, FrequencyNotesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.stringUtf8(FrequencyNotes.MAX_LENGTH * 4),
                    FrequencyNotes.MAX_NOTES), FrequencyNotesPayload::notes,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(FrequencyNotes.MAX_NOTES)), FrequencyNotesPayload::membersOnly,
            FrequencyNotesPayload::new);

    public FrequencyNotesPayload(Map<Integer, String> notes) {
        this(notes, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(FrequencyNotesPayload payload, IPayloadContext context) {
        FrequencyNotes.clientReplaceAll(payload.notes(), payload.membersOnly());
    }
}
