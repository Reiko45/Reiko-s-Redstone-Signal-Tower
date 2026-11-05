package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by the tuner screen: "set the tuner in my main/off hand to this frequency". */
public record SetTunerFrequencyPayload(boolean offhand, int frequency) implements CustomPacketPayload {
    public static final Type<SetTunerFrequencyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "set_tuner_frequency"));

    public static final StreamCodec<ByteBuf, SetTunerFrequencyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetTunerFrequencyPayload::offhand,
            ByteBufCodecs.VAR_INT, SetTunerFrequencyPayload::frequency,
            SetTunerFrequencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. Never trust the client: check the item and clamp the value. */
    public static void handle(SetTunerFrequencyPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof FrequencyTunerItem) {
            FrequencyTunerItem.setFrequency(stack, payload.frequency());
        }
    }
}
