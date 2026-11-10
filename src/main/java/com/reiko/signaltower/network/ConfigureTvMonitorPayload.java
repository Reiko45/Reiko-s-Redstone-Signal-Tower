package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvMonitorBlockEntity;
import io.netty.buffer.ByteBuf;
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

import java.util.List;

/** Client to server (v0.66.0): Done (or Next channel) was pressed in a TV Monitor's settings. */
public record ConfigureTvMonitorPayload(BlockPos pos, List<Integer> channels, int flags, boolean next) implements CustomPacketPayload {
    public static final Type<ConfigureTvMonitorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "configure_tv_monitor"));

    public static final StreamCodec<ByteBuf, ConfigureTvMonitorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureTvMonitorPayload::pos,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(TvMonitorBlockEntity.EXTRA_CHANNELS)), ConfigureTvMonitorPayload::channels,
            ByteBufCodecs.VAR_INT, ConfigureTvMonitorPayload::flags,
            ByteBufCodecs.BOOL, ConfigureTvMonitorPayload::next,
            ConfigureTvMonitorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(ConfigureTvMonitorPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the wall's channels and overlay (and steps to the next channel) if the player can reach
     * the monitor (like clicking it), and tells them. Returns the message, or null if there's no
     * monitor they can reach there. Public for the GameTests.
     */
    public static @Nullable Component apply(Player player, ConfigureTvMonitorPayload payload) {
        BlockPos pos = payload.pos();
        if (!player.canInteractWithBlock(pos, 1.0) || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(pos) instanceof TvMonitorBlockEntity monitor)) {
            return null;
        }
        TvMonitorBlockEntity lead = monitor.lead(level);
        Component message = lead.configure(level, player, payload.channels().stream().mapToInt(Integer::intValue).toArray(),
                payload.flags(), payload.next());
        player.displayClientMessage(message, true);
        return message;
    }
}
