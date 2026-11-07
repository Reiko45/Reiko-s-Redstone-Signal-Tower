package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
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
 * Client to server (v0.37.0): the digits typed on a Code Keypad's screen, after OK. The server
 * checks them (only for a player within reach of the keypad) and answers with a
 * {@link KeypadResultPayload}.
 */
public record KeypadEnterPayload(BlockPos pos, String digits) implements CustomPacketPayload {
    public static final Type<KeypadEnterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "keypad_enter"));

    public static final StreamCodec<ByteBuf, KeypadEnterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KeypadEnterPayload::pos,
            ByteBufCodecs.stringUtf8(CodeKeypadBlockEntity.MAX_CODE), KeypadEnterPayload::digits,
            KeypadEnterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KeypadEnterPayload payload, IPayloadContext context) {
        Player player = context.player();
        CodeKeypadBlockEntity.Result result = apply(player, payload);
        if (result != null && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new KeypadResultPayload(payload.pos(), result.outcome().ordinal(),
                    result.message(), result.lockedSeconds()));
        }
    }

    /** The keypad's answer, or null if {@code player} can't reach a keypad at that position. Public for the GameTests. */
    public static CodeKeypadBlockEntity.@Nullable Result apply(Player player, KeypadEnterPayload payload) {
        if (!(player.level() instanceof ServerLevel level) || !player.canInteractWithBlock(payload.pos(), 1.0)
                || !(level.getBlockEntity(payload.pos()) instanceof CodeKeypadBlockEntity keypad)) {
            return null;
        }
        return keypad.enter(level, player, payload.digits(), level.getGameTime());
    }
}
