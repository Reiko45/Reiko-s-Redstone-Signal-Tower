package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioBlock;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: Done was pressed in a Radio Converter's screen. */
public record ConfigureConverterPayload(BlockPos pos, int listen, int send) implements CustomPacketPayload {
    public static final Type<ConfigureConverterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_converter"));

    public static final StreamCodec<ByteBuf, ConfigureConverterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureConverterPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureConverterPayload::listen,
            ByteBufCodecs.VAR_INT, ConfigureConverterPayload::send,
            ConfigureConverterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureConverterPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the converter's frequencies if the player can reach it (like clicking it), and tells
     * them. Returns false if nothing was changed. Public for the GameTests.
     */
    public static boolean apply(Player player, ConfigureConverterPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0)
                || !(player.level().getBlockState(pos).getBlock() instanceof RadioConverterBlock)
                || !(player.level().getBlockEntity(pos) instanceof RadioRepeaterBlockEntity converter)) {
            return false;
        }
        Component refused = FrequencyClaims.refusal(player.level(), Frequency.clamp(payload.send()), player.getUUID(),
                "private_refused_block");
        if (refused != null) {
            player.displayClientMessage(refused, true);
            com.reiko.signaltower.radio.IntruderAlerts.report(player.getServer(), Frequency.clamp(payload.send()), player,
                    com.reiko.signaltower.radio.IntruderAlerts.Kind.TUNE, player.level().getBlockState(pos).getBlock());
            return false;
        }
        converter.setTunedBy(player.getUUID());
        converter.setFrequencies(payload.listen(), payload.send());
        // Set, but on someone else's encrypted frequency it won't hear anything: say so instead.
        Component deaf = player.level() instanceof ServerLevel level ? RadioBlock.deafNotice(level, pos) : null;
        if (deaf != null) {
            com.reiko.signaltower.radio.IntruderAlerts.report(player.getServer(), converter.getFrequency(), player,
                    com.reiko.signaltower.radio.IntruderAlerts.Kind.DEAF, player.level().getBlockState(pos).getBlock());
        }
        player.displayClientMessage(deaf != null ? deaf : Component.translatable("message." + SignalTower.MOD_ID + ".converter_set",
                converter.getFrequency(), converter.getOutputFrequency()).withStyle(ChatFormatting.AQUA), true);
        return true;
    }
}
