package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.AviationBeaconBlock;
import com.reiko.signaltower.radio.AviationLight;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Client to server (v0.44.0): Done was pressed in an Aviation Beacon's screen. The settings go by
 * the ordinals of {@link AviationLight.When}, {@link AviationLight.Pattern} and
 * {@link AviationLight.LightColor}.
 */
public record ConfigureBeaconPayload(BlockPos pos, int when, int pattern, int color) implements CustomPacketPayload {
    public static final Type<ConfigureBeaconPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_beacon"));

    public static final StreamCodec<ByteBuf, ConfigureBeaconPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureBeaconPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureBeaconPayload::when,
            ByteBufCodecs.VAR_INT, ConfigureBeaconPayload::pattern,
            ByteBufCodecs.VAR_INT, ConfigureBeaconPayload::color,
            ConfigureBeaconPayload::new);

    public static ConfigureBeaconPayload of(BlockPos pos, AviationLight.When when, AviationLight.Pattern pattern, AviationLight.LightColor color) {
        return new ConfigureBeaconPayload(pos, when.ordinal(), pattern.ordinal(), color.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureBeaconPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    private static <E extends Enum<E>> E pick(E[] values, int ordinal) {
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    /**
     * Sets the beacon's settings if the player can reach it (like clicking it; anyone may, it's a
     * light), and tells them. Returns the message, or null if they can't reach one. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureBeaconPayload payload) {
        BlockPos pos = payload.pos();
        BlockState state = player.level().getBlockState(pos);
        if (!player.canInteractWithBlock(pos, 1.0) || !(state.getBlock() instanceof AviationBeaconBlock)) {
            return null;
        }
        BlockState set = state
                .setValue(AviationBeaconBlock.WHEN, pick(AviationLight.When.values(), payload.when()))
                .setValue(AviationBeaconBlock.PATTERN, pick(AviationLight.Pattern.values(), payload.pattern()))
                .setValue(AviationBeaconBlock.COLOR, pick(AviationLight.LightColor.values(), payload.color()));
        if (set != state) {
            player.level().setBlock(pos, set, Block.UPDATE_ALL);
        }
        Component message = Component.translatable("message." + SignalTower.MOD_ID + ".beacon_set", AviationBeaconBlock.describe(set))
                .withStyle(ChatFormatting.AQUA);
        player.displayClientMessage(message, true);
        return message;
    }
}
