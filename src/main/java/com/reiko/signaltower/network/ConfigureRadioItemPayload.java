package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteControlItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the frequency screen: "set the tuner or remote in my main/off hand to this frequency"
 * (and, for a remote, this mode and pulse length).
 */
public record ConfigureRadioItemPayload(boolean offhand, int frequency, boolean toggleMode, int pulseTicks) implements CustomPacketPayload {
    public static final Type<ConfigureRadioItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_radio_item"));

    public static final StreamCodec<ByteBuf, ConfigureRadioItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConfigureRadioItemPayload::offhand,
            ByteBufCodecs.VAR_INT, ConfigureRadioItemPayload::frequency,
            ByteBufCodecs.BOOL, ConfigureRadioItemPayload::toggleMode,
            ByteBufCodecs.VAR_INT, ConfigureRadioItemPayload::pulseTicks,
            ConfigureRadioItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. Never trust the client: check the item and clamp the value. */
    public static void handle(ConfigureRadioItemPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof FrequencyTunerItem) {
            FrequencyTunerItem.setFrequency(stack, payload.frequency());
        } else if (stack.getItem() instanceof RemoteControlItem) {
            FrequencyTunerItem.setFrequency(stack, payload.frequency());
            RemoteControlItem.setToggleMode(stack, payload.toggleMode());
            RemoteControlItem.setPulseTicks(stack, payload.pulseTicks());
        } else if (stack.getItem() instanceof PagerItem) {
            PagerItem.setFrequency(stack, payload.frequency());
        }
    }
}
