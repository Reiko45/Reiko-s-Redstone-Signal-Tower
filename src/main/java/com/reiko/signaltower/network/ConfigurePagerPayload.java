package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.PagerItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when Done is pressed in a Radio Pager's screen: its frequency, sound and chat log settings. */
public record ConfigurePagerPayload(boolean offhand, int frequency, boolean sound, boolean chatLog) implements CustomPacketPayload {
    public static final Type<ConfigurePagerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_pager"));

    public static final StreamCodec<ByteBuf, ConfigurePagerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConfigurePagerPayload::offhand,
            ByteBufCodecs.VAR_INT, ConfigurePagerPayload::frequency,
            ByteBufCodecs.BOOL, ConfigurePagerPayload::sound,
            ByteBufCodecs.BOOL, ConfigurePagerPayload::chatLog,
            ConfigurePagerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigurePagerPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /** Applies it to the pager or Signal Meter in that hand (nothing for anything else). Public for the GameTests. */
    public static void apply(Player player, ConfigurePagerPayload payload) {
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof com.reiko.signaltower.item.SignalMeterItem) {
            // v0.40.0: the Signal Meter uses the pager's screen and packet (frequency and Sound; no chat log).
            FrequencyTunerItem.setFrequency(stack, payload.frequency());
            PagerItem.setSilent(stack, !payload.sound());
            return;
        }
        if (!(stack.getItem() instanceof PagerItem)) {
            return;
        }
        // Only re-tune (which re-arms the alert) if the frequency really changed.
        if (FrequencyTunerItem.getFrequency(stack) != payload.frequency()) {
            PagerItem.setFrequency(stack, payload.frequency());
        }
        PagerItem.setSilent(stack, !payload.sound());
        PagerItem.setChatLog(stack, payload.chatLog());
    }
}
