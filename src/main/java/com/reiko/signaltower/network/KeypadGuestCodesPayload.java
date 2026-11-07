package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import io.netty.buffer.ByteBuf;
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
 * Client to server (v0.38.0): the keypad owner's Guest codes screen lists ({@link #LIST}), adds
 * ({@link #ADD}: {@code label}, {@code code}, {@code uses} (-1: no limit), {@code seconds} (0: no
 * limit)) or removes ({@link #REMOVE}: {@code label}) guest codes. Only for the owner or an
 * operator within reach; answered with a {@link KeypadGuestListPayload}.
 */
public record KeypadGuestCodesPayload(BlockPos pos, int action, String label, String code, int uses, int seconds)
        implements CustomPacketPayload {
    public static final int LIST = 0;
    public static final int ADD = 1;
    public static final int REMOVE = 2;

    public static final Type<KeypadGuestCodesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "keypad_guest_codes"));

    public static final StreamCodec<ByteBuf, KeypadGuestCodesPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KeypadGuestCodesPayload::pos,
            ByteBufCodecs.VAR_INT, KeypadGuestCodesPayload::action,
            ByteBufCodecs.stringUtf8(64), KeypadGuestCodesPayload::label,
            ByteBufCodecs.stringUtf8(CodeKeypadBlockEntity.MAX_CODE), KeypadGuestCodesPayload::code,
            ByteBufCodecs.VAR_INT, KeypadGuestCodesPayload::uses,
            ByteBufCodecs.VAR_INT, KeypadGuestCodesPayload::seconds,
            KeypadGuestCodesPayload::new);

    public static KeypadGuestCodesPayload list(BlockPos pos) {
        return new KeypadGuestCodesPayload(pos, LIST, "", "", 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KeypadGuestCodesPayload payload, IPayloadContext context) {
        KeypadGuestListPayload answer = apply(context.player(), payload, System.currentTimeMillis());
        if (answer != null && context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, answer);
        }
    }

    /**
     * What the keypad answers {@code player} at real time {@code nowMillis}, or null if they can't
     * reach a keypad there. Someone who may not change it gets an empty list and the refusal.
     * Public for the GameTests.
     */
    public static @Nullable KeypadGuestListPayload apply(Player player, KeypadGuestCodesPayload payload, long nowMillis) {
        if (!(player.level() instanceof ServerLevel level) || !player.canInteractWithBlock(payload.pos(), 1.0)
                || !(level.getBlockEntity(payload.pos()) instanceof CodeKeypadBlockEntity keypad)) {
            return null;
        }
        if (!keypad.mayConfigure(player)) {
            return new KeypadGuestListPayload(payload.pos(), java.util.List.of(),
                    keypad.removeGuestCode(player, "").message());
        }
        Component notice = switch (payload.action()) {
            case ADD -> keypad.addGuestCode(player, payload.label(), payload.code(), payload.uses(), payload.seconds(), nowMillis).message();
            case REMOVE -> keypad.removeGuestCode(player, payload.label()).message();
            default -> Component.empty();
        };
        return KeypadGuestListPayload.of(keypad, notice, nowMillis);
    }
}
