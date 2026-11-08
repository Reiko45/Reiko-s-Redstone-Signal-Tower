package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioSpeakerBlock;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.SpeakerSound;
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

/** Client to server (v0.43.0): Done was pressed in a Radio Speaker's screen ({@code speaker} packed). */
public record ConfigureSpeakerPayload(BlockPos pos, int speaker) implements CustomPacketPayload {
    public static final Type<ConfigureSpeakerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_speaker"));

    public static final StreamCodec<ByteBuf, ConfigureSpeakerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureSpeakerPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureSpeakerPayload::speaker,
            ConfigureSpeakerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureSpeakerPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the speaker's sound, note, volume and repeat if the player can reach it (like clicking
     * it), and tells them. Returns the message, or null if they can't reach one. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureSpeakerPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioSpeakerBlock)
                || !(player.level().getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return null;
        }
        int packed = payload.speaker();
        rx.setSpeaker(SpeakerSound.byId(packed & 15), packed >> 7, SpeakerSound.Volume.byId(packed >> 4 & 3), (packed >> 6 & 1) == 1);
        Component message = Component.translatable("message." + SignalTower.MOD_ID + ".speaker_set",
                RadioReceiverBlockEntity.speakerText(rx.packedSpeaker())).withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(message, true);
        return message;
    }
}
