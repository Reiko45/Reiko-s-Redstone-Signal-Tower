package com.reiko.signaltower.network;

import com.reiko.signaltower.blockentity.SignalRecorderBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.List;

/**
 * What a Radio Signal Recorder is doing (v0.44.0), for its screen: its phase (by ordinal), the ticks
 * since that began, its recording (length and changes; while recording, the changes so far), what
 * it hears on its "Records from" frequency, whether it's deaf to that (someone else's encrypted
 * frequency) or silenced on the one it plays on (someone else's private one), whether redstone
 * reaches it, and what it's sending.
 */
public record RecorderStatus(BlockPos pos, int phase, int elapsed, int length, List<Integer> changes, int heard,
                             boolean deaf, boolean silenced, boolean powered, int sending) {
    public static final StreamCodec<ByteBuf, RecorderStatus> STREAM_CODEC = StreamCodec.of(
            (buffer, status) -> {
                BlockPos.STREAM_CODEC.encode(buffer, status.pos());
                ByteBufCodecs.VAR_INT.encode(buffer, status.phase());
                ByteBufCodecs.VAR_INT.encode(buffer, status.elapsed());
                ByteBufCodecs.VAR_INT.encode(buffer, status.length());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(SignalRecorderBlockEntity.MAX_CHANGES)).encode(buffer, status.changes());
                ByteBufCodecs.VAR_INT.encode(buffer, status.heard());
                ByteBufCodecs.BOOL.encode(buffer, status.deaf());
                ByteBufCodecs.BOOL.encode(buffer, status.silenced());
                ByteBufCodecs.BOOL.encode(buffer, status.powered());
                ByteBufCodecs.VAR_INT.encode(buffer, status.sending());
            },
            buffer -> new RecorderStatus(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(SignalRecorderBlockEntity.MAX_CHANGES)).decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer)));

    public RecorderStatus {
        changes = List.copyOf(changes);
    }

    public SignalRecorderBlockEntity.Phase phaseValue() {
        return SignalRecorderBlockEntity.Phase.of(phase);
    }

    public static RecorderStatus of(ServerLevel level, SignalRecorderBlockEntity recorder) {
        BlockPos pos = recorder.getBlockPos();
        long now = level.getGameTime();
        SignalRecorderBlockEntity.Phase phase = recorder.getPhase();
        List<Integer> changes = phase == SignalRecorderBlockEntity.Phase.RECORDING
                ? recorder.getTaking() : Arrays.stream(recorder.getChanges()).boxed().toList();
        int length = phase == SignalRecorderBlockEntity.Phase.RECORDING ? recorder.elapsed(now) : recorder.getLength();
        boolean deaf = recorder.getListenFrequency() > 0
                && !FrequencyClaims.mayHear(level, recorder.getListenFrequency(), recorder.getTunedBy());
        boolean silenced = SignalNetwork.get(level).isSilenced(level, pos);
        return new RecorderStatus(pos, phase.ordinal(), recorder.elapsed(now), length, changes, recorder.heard(level),
                deaf, silenced, recorder.isPowered(level), silenced ? 0 : recorder.getOutput());
    }
}
