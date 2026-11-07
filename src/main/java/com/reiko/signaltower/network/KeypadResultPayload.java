package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.37.0): what a Code Keypad said to a typed code or a settings change.
 * {@code outcome} is a {@link CodeKeypadBlockEntity.Outcome} ordinal.
 */
public record KeypadResultPayload(BlockPos pos, int outcome, Component message, int lockedSeconds) implements CustomPacketPayload {
    public static final Type<KeypadResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "keypad_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeypadResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KeypadResultPayload::pos,
            ByteBufCodecs.VAR_INT, KeypadResultPayload::outcome,
            ComponentSerialization.TRUSTED_STREAM_CODEC, KeypadResultPayload::message,
            ByteBufCodecs.VAR_INT, KeypadResultPayload::lockedSeconds,
            KeypadResultPayload::new);

    public CodeKeypadBlockEntity.Outcome outcomeKind() {
        CodeKeypadBlockEntity.Outcome[] all = CodeKeypadBlockEntity.Outcome.values();
        return all[Math.max(0, Math.min(all.length - 1, outcome))];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(KeypadResultPayload payload, IPayloadContext context) {
        ClientHooks.onKeypadResult(payload);
    }
}
