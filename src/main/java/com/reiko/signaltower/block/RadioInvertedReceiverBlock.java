package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A receiver with a redstone torch built in (v0.39.0): it outputs full strength (15) while its
 * frequency is silent where it stands, and nothing while it hears the frequency at any strength.
 * Turning a frequency on can then switch something off, such as a door that's open until a remote
 * or a motion sensor says otherwise. The logic is in {@link RadioReceiverBlockEntity}.
 * <p>
 * On someone else's encrypted frequency it hears nothing, so it stays on: it can't be used to tell
 * when that frequency is on.
 */
public class RadioInvertedReceiverBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioInvertedReceiverBlock> CODEC = simpleCodec(RadioInvertedReceiverBlock::new);

    public RadioInvertedReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        RadioReceiverBlockEntity rx = level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity found ? found : null;
        int frequency = rx != null ? rx.getFrequency() : 0;
        SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, frequency, pos, rx != null ? rx.getTunedBy() : null);
        return Component.translatable("message." + SignalTower.MOD_ID + ".inverted_status",
                FrequencyNames.label(level, frequency), reception.power(), state.getValue(POWER), reception.transmittersInReach());
    }
}
