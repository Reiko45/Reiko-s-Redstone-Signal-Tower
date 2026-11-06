package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.TunerPresets;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by the tuner's screen whenever a favorite is saved or cleared. */
public record ConfigureTunerPresetsPayload(boolean offhand, TunerPresets presets) implements CustomPacketPayload {
    public static final Type<ConfigureTunerPresetsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_tuner_presets"));

    public static final StreamCodec<ByteBuf, ConfigureTunerPresetsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConfigureTunerPresetsPayload::offhand,
            TunerPresets.STREAM_CODEC, ConfigureTunerPresetsPayload::presets,
            ConfigureTunerPresetsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureTunerPresetsPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof FrequencyTunerItem) {
            FrequencyTunerItem.setPresets(stack, payload.presets());
        }
    }
}
