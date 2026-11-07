package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client (v0.37.0): open a Code Keypad's screen. Never carries the code itself, only
 * whether there is one; {@code flags} says whether this player may change its settings
 * ({@link #MAY_CONFIGURE}) and whether a code is set ({@link #HAS_CODE}). {@code lockedSeconds}:
 * how long this player is still locked out (0 if they aren't).
 */
public record OpenKeypadScreenPayload(BlockPos pos, int frequency, String ownerName, int flags, int pulseTicks, int lockedSeconds)
        implements CustomPacketPayload {
    public static final int MAY_CONFIGURE = 1;
    public static final int HAS_CODE = 2;

    public static final Type<OpenKeypadScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "open_keypad_screen"));

    public static final StreamCodec<ByteBuf, OpenKeypadScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenKeypadScreenPayload::pos,
            ByteBufCodecs.VAR_INT, OpenKeypadScreenPayload::frequency,
            ByteBufCodecs.stringUtf8(16), OpenKeypadScreenPayload::ownerName,
            ByteBufCodecs.VAR_INT, OpenKeypadScreenPayload::flags,
            ByteBufCodecs.VAR_INT, OpenKeypadScreenPayload::pulseTicks,
            ByteBufCodecs.VAR_INT, OpenKeypadScreenPayload::lockedSeconds,
            OpenKeypadScreenPayload::new);

    /** What {@code player} sees of {@code keypad} at {@code gameTime}. */
    public static OpenKeypadScreenPayload of(CodeKeypadBlockEntity keypad, Player player, long gameTime) {
        int flags = (keypad.mayConfigure(player) ? MAY_CONFIGURE : 0) | (keypad.hasCode() ? HAS_CODE : 0);
        String owner = keypad.getOwnerName();
        return new OpenKeypadScreenPayload(keypad.getBlockPos(), keypad.getFrequency(), owner.length() > 16 ? owner.substring(0, 16) : owner,
                flags, keypad.getPulseTicks(), keypad.lockedSeconds(player.getUUID(), gameTime));
    }

    public boolean mayConfigure() {
        return (flags & MAY_CONFIGURE) != 0;
    }

    public boolean hasCode() {
        return (flags & HAS_CODE) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(OpenKeypadScreenPayload payload, IPayloadContext context) {
        ClientHooks.openKeypadScreen(payload);
    }
}
