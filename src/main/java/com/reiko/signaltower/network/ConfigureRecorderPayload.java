package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioBlock;
import com.reiko.signaltower.block.RadioSignalRecorderBlock;
import com.reiko.signaltower.blockentity.SignalRecorderBlockEntity;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.IntruderAlerts;
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
 * Client to server (v0.44.0): a button in a Radio Signal Recorder's screen. Every button saves the
 * screen's settings (the frequency it plays on, the one it records from, 0 for none, and whether it
 * loops), then does its {@code action}: {@link #SAVE} (Done), {@link #RECORD}, {@link #STOP},
 * {@link #PLAY} or {@link #CLEAR}.
 */
public record ConfigureRecorderPayload(BlockPos pos, int frequency, int listen, boolean loop, int action) implements CustomPacketPayload {
    public static final int SAVE = 0;
    public static final int RECORD = 1;
    public static final int STOP = 2;
    public static final int PLAY = 3;
    public static final int CLEAR = 4;

    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public static final Type<ConfigureRecorderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_recorder"));

    public static final StreamCodec<ByteBuf, ConfigureRecorderPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureRecorderPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureRecorderPayload::frequency,
            ByteBufCodecs.VAR_INT, ConfigureRecorderPayload::listen,
            ByteBufCodecs.BOOL, ConfigureRecorderPayload::loop,
            ByteBufCodecs.VAR_INT, ConfigureRecorderPayload::action,
            ConfigureRecorderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread: applies it, then sends the screen the recorder's new status. */
    public static void handle(ConfigureRecorderPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
        if (context.player() instanceof ServerPlayer player) {
            RecorderStatus status = RecorderStatusPayload.answer(player, payload.pos());
            if (status != null) {
                PacketDistributor.sendToPlayer(player, new RecorderStatusPayload(status));
            }
        }
    }

    /**
     * Saves the settings and does the action if the player can reach the recorder and may send on
     * the frequency it plays on (on someone else's private frequency only its owner and members may:
     * anyone else gets a red refusal and the owner an alert). The player becomes its tuner, so it
     * records and plays as them. Returns the message shown, or null if they can't reach one. Public
     * for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureRecorderPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioSignalRecorderBlock block)
                || !(player.level().getBlockEntity(pos) instanceof SignalRecorderBlockEntity recorder)
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        int send = Frequency.clamp(payload.frequency());
        int listen = payload.listen() <= 0 ? 0 : Frequency.clamp(payload.listen());
        if (listen == send) {
            return tell(player, Component.translatable(KEY + "recorder_same", FrequencyNames.label(level, send)).withStyle(ChatFormatting.RED));
        }
        Component refused = FrequencyClaims.refusal(level, send, player.getUUID(), "private_refused_block");
        if (refused == null && send != recorder.getFrequency()) {
            refused = FrequencyClaims.refusal(level, recorder.getFrequency(), player.getUUID(), "private_refused_block");
        }
        if (refused != null) {
            IntruderAlerts.report(player.getServer(), send, player, IntruderAlerts.Kind.TUNE, block);
            return tell(player, refused);
        }
        recorder.setTunedBy(player.getUUID());
        if (send != recorder.getFrequency()) {
            recorder.setFrequency(send);
        }
        recorder.setSettings(listen, payload.loop());
        long now = level.getGameTime();
        Component deaf = listen > 0 ? FrequencyClaims.deafness(level, listen, player.getUUID(), "encrypted_deaf") : null;
        if (deaf != null && payload.action() == RECORD) {
            IntruderAlerts.report(player.getServer(), listen, player, IntruderAlerts.Kind.DEAF, block);
            return tell(player, deaf);
        }
        Component message = switch (payload.action()) {
            case RECORD -> recorder.record(now)
                    ? Component.translatable(KEY + "recorder_waiting", FrequencyNames.label(level, listen)).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable(KEY + "recorder_no_listen").withStyle(ChatFormatting.RED);
            case STOP -> switch (recorder.stop(now)) {
                case RECORDING -> Component.translatable(KEY + "recorder_recorded",
                        SignalRecorderBlockEntity.tapeText(recorder.getLength(), recorder.getChanges().length)).withStyle(ChatFormatting.AQUA);
                case WAITING -> Component.translatable(KEY + "recorder_stopped_waiting").withStyle(ChatFormatting.GRAY);
                default -> Component.translatable(KEY + "recorder_stopped").withStyle(ChatFormatting.GRAY);
            };
            case PLAY -> recorder.play(now)
                    ? Component.translatable(KEY + "recorder_playing", FrequencyNames.label(level, send)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "recorder_nothing").withStyle(ChatFormatting.GRAY);
            case CLEAR -> recorder.clear()
                    ? Component.translatable(KEY + "recorder_cleared").withStyle(ChatFormatting.GRAY)
                    : Component.translatable(KEY + "recorder_nothing").withStyle(ChatFormatting.GRAY);
            default -> deaf != null ? deaf : Component.translatable(KEY + "recorder_set", FrequencyNames.label(level, send),
                    listen > 0 ? FrequencyNames.label(level, listen) : Component.translatable(KEY + "recorder_no_source"),
                    SignalRecorderBlockEntity.modeText(payload.loop())).withStyle(ChatFormatting.AQUA);
        };
        if (deaf == null) {
            Component quiet = RadioBlock.silencedNotice(level, pos);
            if (quiet != null && payload.action() == SAVE) {
                message = quiet;
            }
        }
        return tell(player, message);
    }

    private static Component tell(Player player, Component message) {
        player.displayClientMessage(message, true);
        return message;
    }
}
