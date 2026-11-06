package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** The server's answer to {@link ScannerRequestPayload}: what a receiver where the player stands would hear. */
public record ScannerResultPayload(List<Entry> entries) implements CustomPacketPayload {
    /**
     * One frequency. {@code source} is a {@link SignalNetwork.Source} ordinal. {@code distance} and
     * {@code direction} (0 = north, clockwise) are -1 for remote signals.
     */
    public record Entry(int frequency, int power, int source, int transmitters, int distance, int direction) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::frequency,
                ByteBufCodecs.VAR_INT, Entry::power,
                ByteBufCodecs.VAR_INT, Entry::source,
                ByteBufCodecs.VAR_INT, Entry::transmitters,
                ByteBufCodecs.VAR_INT, Entry::distance,
                ByteBufCodecs.VAR_INT, Entry::direction,
                Entry::new);

        public SignalNetwork.Source sourceType() {
            SignalNetwork.Source[] all = SignalNetwork.Source.values();
            return source >= 0 && source < all.length ? all[source] : SignalNetwork.Source.TRANSMITTER;
        }
    }

    public static final Type<ScannerResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "scanner_result"));

    public static final StreamCodec<ByteBuf, ScannerResultPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(SignalNetwork.MAX_SCAN_RESULTS)), ScannerResultPayload::entries,
            ScannerResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(ScannerResultPayload payload, IPayloadContext context) {
        ClientHooks.onScannerResult(payload);
    }
}
