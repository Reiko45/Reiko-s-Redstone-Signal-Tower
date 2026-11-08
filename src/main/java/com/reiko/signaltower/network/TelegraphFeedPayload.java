package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TelegraphBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.Telegraph;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Server to client (v0.51.0): a Radio Telegraph's screen: where it is and what it's on (how far it
 * reaches, whether it can read its frequency), the last messages that reached it, and what just
 * happened (sent, refused, re-tuned; {@code sent} if a message just went out, so the screen
 * empties its box). {@code open} opens the screen; otherwise it's the answer to
 * the screen's once-a-second check or to Send, for the screen that's open.
 */
public record TelegraphFeedPayload(Header header, List<Telegraph.Line> lines, Optional<Component> notice, boolean sent, boolean open)
        implements CustomPacketPayload {
    public static final Type<TelegraphFeedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "telegraph_feed"));

    /** The telegraph: where, its frequency, its own reach, how many towers relay it, and whether it's deaf (encrypted). */
    public record Header(BlockPos pos, int frequency, int reach, int towers, int repeaters, boolean deaf) {
        static final StreamCodec<ByteBuf, Header> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Header::pos,
                ByteBufCodecs.VAR_INT, Header::frequency,
                ByteBufCodecs.VAR_INT, Header::reach,
                ByteBufCodecs.VAR_INT, Header::towers,
                ByteBufCodecs.VAR_INT, Header::repeaters,
                ByteBufCodecs.BOOL, Header::deaf,
                Header::new);
    }

    private static final StreamCodec<ByteBuf, Telegraph.Line> LINE_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Telegraph.Line::secondsAgo,
            ByteBufCodecs.VAR_LONG, Telegraph.Line::dayTime,
            ByteBufCodecs.stringUtf8(64), Telegraph.Line::who,
            ByteBufCodecs.stringUtf8(Telegraph.MAX_LENGTH), Telegraph.Line::text,
            ByteBufCodecs.BOOL, Telegraph.Line::encrypted,
            Telegraph.Line::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, TelegraphFeedPayload> STREAM_CODEC = StreamCodec.composite(
            Header.CODEC, TelegraphFeedPayload::header,
            LINE_CODEC.apply(ByteBufCodecs.list(Telegraph.FEED_LINES)), TelegraphFeedPayload::lines,
            ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC), TelegraphFeedPayload::notice,
            ByteBufCodecs.BOOL, TelegraphFeedPayload::sent,
            ByteBufCodecs.BOOL, TelegraphFeedPayload::open,
            TelegraphFeedPayload::new);

    /** The screen for {@code telegraph} now, with {@code notice} (or none). */
    public static TelegraphFeedPayload of(ServerLevel level, TelegraphBlockEntity telegraph, @Nullable Component notice, boolean open) {
        return of(level, telegraph, notice, false, open);
    }

    /** {@link #of(ServerLevel, TelegraphBlockEntity, Component, boolean)}, saying whether a message just went out. */
    public static TelegraphFeedPayload of(ServerLevel level, TelegraphBlockEntity telegraph, @Nullable Component notice, boolean sent, boolean open) {
        BlockPos pos = telegraph.getBlockPos();
        int frequency = telegraph.getFrequency();
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, pos, RadioHelper.reachAt(level, pos),
                com.reiko.signaltower.Config.TOWERS_RELAY_REMOTES.get());
        int repeaters = network.withRepeaters(level, frequency, own).size() - own.size();
        Header header = new Header(pos, frequency, own.get(0).reach(), own.size() - 1, repeaters,
                !FrequencyClaims.mayHear(level, frequency, telegraph.getTunedBy()));
        return new TelegraphFeedPayload(header, Telegraph.feed(level, pos, frequency, telegraph.getTunedBy()), Optional.ofNullable(notice), sent, open);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(TelegraphFeedPayload payload, IPayloadContext context) {
        ClientHooks.onTelegraphFeed(payload);
    }
}
