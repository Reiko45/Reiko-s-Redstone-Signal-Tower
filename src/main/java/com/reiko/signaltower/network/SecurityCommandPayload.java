package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.SecurityConsoleBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** Client to server (v0.68.0): the Arm or Disarm button in a Security Console's screen. */
public record SecurityCommandPayload(BlockPos pos, int command) implements CustomPacketPayload {
    public static final int ARM = 0;
    public static final int DISARM = 1;

    public static final Type<SecurityCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "security_command"));

    public static final StreamCodec<ByteBuf, SecurityCommandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SecurityCommandPayload::pos,
            ByteBufCodecs.VAR_INT, SecurityCommandPayload::command,
            SecurityCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecurityCommandPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /** Arms or disarms it for the player if they're near and may; returns the action bar line. Public for the GameTests. */
    public static @Nullable Component apply(Player player, SecurityCommandPayload payload) {
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())
                || !(level.getBlockEntity(payload.pos()) instanceof SecurityConsoleBlockEntity console) || !console.inReach(player)) {
            return null;
        }
        String key = "message." + SignalTower.MOD_ID + ".";
        Component message;
        if (!console.mayConfigure(player)) {
            message = com.reiko.signaltower.radio.FrequencyClaims.refusal(level, console.getFrequency(), player.getUUID(), "private_refused_security");
            if (message == null) {
                message = Component.translatable(key + "security_not_yours").withStyle(ChatFormatting.RED);
            }
        } else if (payload.command() == ARM) {
            message = console.arm(level, player.getGameProfile().getName())
                    ? Component.translatable(key + "security_arming", console.getSetup().delaySeconds()).withStyle(ChatFormatting.GOLD)
                    : Component.translatable(key + "security_already_armed").withStyle(ChatFormatting.GRAY);
        } else {
            message = console.disarm(level, player.getGameProfile().getName())
                    ? Component.translatable(key + "security_disarmed").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(key + "security_already_disarmed").withStyle(ChatFormatting.GRAY);
        }
        player.displayClientMessage(message, true);
        return message;
    }
}
