package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioTimerTransmitterBlock;
import com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.IntruderAlerts;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.42.0): Done was pressed in a Radio Timer Transmitter's screen. */
public record ConfigureTimerPayload(BlockPos pos, boolean daily, int interval, int pulse, int onHour, int offHour)
        implements CustomPacketPayload {
    public static final Type<ConfigureTimerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_timer"));

    public static final StreamCodec<ByteBuf, ConfigureTimerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureTimerPayload::pos,
            ByteBufCodecs.BOOL, ConfigureTimerPayload::daily,
            ByteBufCodecs.VAR_INT, ConfigureTimerPayload::interval,
            ByteBufCodecs.VAR_INT, ConfigureTimerPayload::pulse,
            ByteBufCodecs.VAR_INT, ConfigureTimerPayload::onHour,
            ByteBufCodecs.VAR_INT, ConfigureTimerPayload::offHour,
            ConfigureTimerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureTimerPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the timer's schedule if the player can reach it and may send on its frequency (on
     * someone else's private frequency only its owner and members may: anyone else gets a red
     * refusal and the owner an alert), and tells them. Returns the message, or null if they can't
     * reach a timer there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureTimerPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioTimerTransmitterBlock block)
                || !(player.level().getBlockEntity(pos) instanceof TimerTransmitterBlockEntity timer)) {
            return null;
        }
        Component refused = FrequencyClaims.refusal(player.level(), timer.getFrequency(), player.getUUID(), "private_refused_block");
        if (refused != null) {
            player.displayClientMessage(refused, true);
            IntruderAlerts.report(player.getServer(), timer.getFrequency(), player, IntruderAlerts.Kind.TUNE, block);
            return refused;
        }
        timer.setTiming(payload.daily(), payload.interval(), payload.pulse(), payload.onHour(), payload.offHour(),
                player.level().getGameTime());
        Component message = Component.translatable("message." + SignalTower.MOD_ID + ".timer_set", timer.scheduleText())
                .withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(message, true);
        return message;
    }
}
