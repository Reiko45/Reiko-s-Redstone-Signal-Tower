package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CodeKeypadBlock;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.RadioHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server (v0.37.0): a player holding a Frequency Tuner looks at a transmitter, repeater,
 * converter or Code Keypad (or a mast under one); how far does it reach? Only the server knows
 * (the dimension's sea level and the server config). Answered with a {@link ReachPreviewPayload}.
 */
public record ReachPreviewRequestPayload(BlockPos pos) implements CustomPacketPayload {
    /** The farthest block (in a straight line) a player can ask about: the client looks up to 128 blocks. */
    public static final int MAX_DISTANCE = 160;

    public static final Type<ReachPreviewRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "reach_preview_request"));

    public static final StreamCodec<ByteBuf, ReachPreviewRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ReachPreviewRequestPayload::pos,
            ReachPreviewRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReachPreviewRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, answer(player, payload.pos()));
        }
    }

    /**
     * The reach of the sender at {@code pos}, for {@code player}: only while they hold a tuner, for
     * a sender in their dimension within {@link #MAX_DISTANCE}. Otherwise reach -1 ("nothing to
     * show"). Public for the GameTests.
     */
    public static ReachPreviewPayload answer(Player player, BlockPos pos) {
        ReachPreviewPayload none = new ReachPreviewPayload(pos, -1, 0, 0);
        boolean holdsTuner = player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
        if (!holdsTuner || !(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)
                || player.position().distanceToSqr(pos.getCenter()) > (double) MAX_DISTANCE * MAX_DISTANCE
                || !(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            return none;
        }
        Block block = level.getBlockState(pos).getBlock();
        int kind = block instanceof RadioConverterBlock ? ReachPreviewPayload.CONVERTER
                : block instanceof RadioRepeaterBlock ? ReachPreviewPayload.REPEATER
                : block instanceof com.reiko.signaltower.block.RadioMotionSensorBlock ? ReachPreviewPayload.MOTION_SENSOR
                : block instanceof com.reiko.signaltower.block.CompactRadioTransmitterBlock ? ReachPreviewPayload.COMPACT_TRANSMITTER
                : block instanceof com.reiko.signaltower.block.RadioTimerTransmitterBlock ? ReachPreviewPayload.TIMER_TRANSMITTER
                : block instanceof com.reiko.signaltower.block.RadioSignalRecorderBlock ? ReachPreviewPayload.SIGNAL_RECORDER
                : block instanceof com.reiko.signaltower.block.RadioJammerBlock ? ReachPreviewPayload.JAMMER
                : block instanceof RadioTransmitterBlock ? ReachPreviewPayload.TRANSMITTER
                : block instanceof CodeKeypadBlock ? ReachPreviewPayload.KEYPAD
                : -1;
        if (kind < 0) {
            return none;
        }
        // A converter is heard on the frequency it sends on.
        int frequency = radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter()
                ? relay.getOutputFrequency() : radio.getFrequency();
        int reach = block instanceof RadioTransmitterBlock
                ? com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachNow(level, pos, level.getBlockState(pos))
                : RadioHelper.towerReachNow(level, pos, level.getBlockState(pos));
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        int aim = com.reiko.signaltower.radio.AntennaTier.dishAim(state);
        if (aim >= 0) {
            // v0.48.0: a keyhole, its dish's beam reaching farther.
            int far = Math.max(reach, com.reiko.signaltower.item.AntennaItem.dishReachShown(level, pos, state));
            return new ReachPreviewPayload(pos, reach, kind, frequency, far,
                    aim | com.reiko.signaltower.radio.AntennaTier.dishConeDegrees() << 4);
        }
        return new ReachPreviewPayload(pos, reach, kind, frequency);
    }
}
