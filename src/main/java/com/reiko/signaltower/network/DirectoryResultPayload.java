package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** The server's answer to {@link DirectoryRequestPayload}: which named and private frequencies are on. */
public record DirectoryResultPayload(List<Entry> entries) implements CustomPacketPayload {
    /** Every name and every claim, with room to spare. */
    public static final int MAX_ENTRIES = FrequencyNames.MAX_NAMES + FrequencyClaims.MAX_CLAIMS;

    /**
     * One frequency: the strongest signal on it anywhere in the player's dimension ({@code -1}:
     * someone else's private frequency, not shown), and what the player hears where they stand
     * ({@code -1}: someone else's encrypted frequency, which they can't hear).
     */
    public record Entry(int frequency, int anywhere, int here) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::frequency,
                ByteBufCodecs.VAR_INT, Entry::anywhere,
                ByteBufCodecs.VAR_INT, Entry::here,
                Entry::new);

        public boolean hidden() {
            return anywhere < 0;
        }

        /** Someone else's encrypted frequency: nothing about it is shown. */
        public boolean encrypted() {
            return here < 0;
        }

        /** On somewhere this player may know about: anywhere (if shown) or where they stand. */
        public boolean on() {
            return anywhere > 0 || here > 0;
        }
    }

    public static final Type<DirectoryResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "directory_result"));

    public static final StreamCodec<ByteBuf, DirectoryResultPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), DirectoryResultPayload::entries,
            DirectoryResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(DirectoryResultPayload payload, IPayloadContext context) {
        ClientHooks.onDirectoryResult(payload);
    }
}
