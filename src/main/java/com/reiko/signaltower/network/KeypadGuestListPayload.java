package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client (v0.38.0): a keypad's guest codes for its owner's Guest codes screen, and what
 * the last change said ({@code notice}, empty for a plain list). The keypad's own code is never sent.
 */
public record KeypadGuestListPayload(BlockPos pos, List<Info> codes, Component notice) implements CustomPacketPayload {
    /** One guest code: {@code usesLeft} -1 for no limit; {@code secondsLeft} -1 for no time limit, 0 once expired. */
    public record Info(String label, String code, int usesLeft, long secondsLeft, int used, String lastUser) {
        public static final StreamCodec<ByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Info::label,
                ByteBufCodecs.stringUtf8(CodeKeypadBlockEntity.MAX_CODE), Info::code,
                ByteBufCodecs.VAR_INT, Info::usesLeft,
                ByteBufCodecs.VAR_LONG, Info::secondsLeft,
                ByteBufCodecs.VAR_INT, Info::used,
                ByteBufCodecs.stringUtf8(64), Info::lastUser,
                Info::new);
    }

    public static final Type<KeypadGuestListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "keypad_guest_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeypadGuestListPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KeypadGuestListPayload::pos,
            Info.STREAM_CODEC.apply(ByteBufCodecs.list(CodeKeypadBlockEntity.MAX_GUEST_CODES)), KeypadGuestListPayload::codes,
            ComponentSerialization.TRUSTED_STREAM_CODEC, KeypadGuestListPayload::notice,
            KeypadGuestListPayload::new);

    public static KeypadGuestListPayload of(CodeKeypadBlockEntity keypad, Component notice, long nowMillis) {
        List<Info> codes = new ArrayList<>();
        for (CodeKeypadBlockEntity.GuestCode guest : keypad.guestCodes()) {
            codes.add(new Info(guest.label(), guest.code(), guest.usesLeft(), guest.secondsLeft(nowMillis), guest.used(), guest.lastUser()));
        }
        return new KeypadGuestListPayload(keypad.getBlockPos(), codes, notice);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(KeypadGuestListPayload payload, IPayloadContext context) {
        ClientHooks.onKeypadGuestList(payload);
    }
}
