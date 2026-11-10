package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioBlock;
import com.reiko.signaltower.blockentity.TelegraphBlockEntity;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.IntruderAlerts;
import com.reiko.signaltower.radio.Telegraph;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Client to server (v0.51.0), from a Radio Telegraph's screen: {@code send} is Send (or Enter):
 * re-tune it first if the frequency box changed (the player becomes the one it reads as), then
 * send {@code text} if there is any. Otherwise it's the screen's once-a-second check. Either way
 * the answer is a {@link TelegraphFeedPayload}. {@code morse} (v0.62.0) is the screen's Morse
 * button: 1 on, 0 off, -1 leave it.
 */
public record TelegraphActionPayload(BlockPos pos, int frequency, String text, boolean send, int morse) implements CustomPacketPayload {
    public TelegraphActionPayload(BlockPos pos, int frequency, String text, boolean send) {
        this(pos, frequency, text, send, -1);
    }

    public static final Type<TelegraphActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "telegraph_action"));

    public static final StreamCodec<ByteBuf, TelegraphActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelegraphActionPayload::pos,
            ByteBufCodecs.VAR_INT, TelegraphActionPayload::frequency,
            ByteBufCodecs.stringUtf8(256), TelegraphActionPayload::text,
            ByteBufCodecs.BOOL, TelegraphActionPayload::send,
            ByteBufCodecs.VAR_INT, TelegraphActionPayload::morse,
            TelegraphActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(TelegraphActionPayload payload, IPayloadContext context) {
        TelegraphFeedPayload answer = apply(context.player(), payload);
        if (answer != null && context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, answer);
        }
    }

    /**
     * Does what the screen asked for {@code player}, if they can reach a telegraph there, and
     * returns the screen's new state (null if they can't). Public for the GameTests.
     */
    public static @Nullable TelegraphFeedPayload apply(Player player, TelegraphActionPayload payload) {
        BlockPos pos = payload.pos();
        if (!(player.level() instanceof ServerLevel level) || !player.canInteractWithBlock(pos, 1.0)
                || !(level.getBlockEntity(pos) instanceof TelegraphBlockEntity telegraph)) {
            return null;
        }
        Component notice = null;
        boolean sent = false;
        if (payload.morse() >= 0) {
            telegraph.setMorse(payload.morse() == 1); // v0.62.0
        }
        if (payload.send()) {
            int frequency = Frequency.clamp(payload.frequency());
            if (frequency != telegraph.getFrequency()) {
                telegraph.setTunedBy(player.getUUID());
                telegraph.setFrequency(frequency);
                Component deaf = RadioBlock.deafNotice(level, pos);
                if (deaf != null) {
                    IntruderAlerts.report(level.getServer(), frequency, player, IntruderAlerts.Kind.DEAF, level.getBlockState(pos).getBlock());
                }
                notice = deaf != null ? deaf : Component.translatable("message." + SignalTower.MOD_ID + ".telegraph_tuned",
                        FrequencyNames.label(level, frequency)).withStyle(ChatFormatting.AQUA);
            }
            if (!Telegraph.clean(payload.text()).isEmpty()) {
                Telegraph.Result result = Telegraph.send(level, pos, player, payload.text());
                if (result != null) {
                    notice = result.message();
                    sent = result.sent();
                }
            }
        }
        return TelegraphFeedPayload.of(level, telegraph, notice, sent, false);
    }
}
