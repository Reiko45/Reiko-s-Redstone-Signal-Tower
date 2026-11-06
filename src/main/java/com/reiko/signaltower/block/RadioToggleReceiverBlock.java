package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A receiver that works like a light switch: each time its frequency turns on where it stands
 * (from silent to any strength), it flips between off and on. While on it outputs full strength
 * (15), whatever strength it heard. A pulse remote, or a button or pressure plate on a transmitter,
 * then switches things on with one press and off with the next. The logic is in
 * {@link RadioReceiverBlockEntity}; it starts off, and its state is saved.
 */
public class RadioToggleReceiverBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioToggleReceiverBlock> CODEC = simpleCodec(RadioToggleReceiverBlock::new);

    public RadioToggleReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int frequency = 0;
        boolean on = false;
        if (level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx) {
            frequency = rx.getFrequency();
            on = rx.isToggledOn();
        }
        SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, frequency, pos);
        String key = "message." + SignalTower.MOD_ID + ".";
        Component state1 = on
                ? Component.translatable(key + "toggle_on").withStyle(ChatFormatting.GREEN)
                : Component.translatable(key + "toggle_off").withStyle(ChatFormatting.GRAY);
        return Component.translatable(key + "toggle_status", frequency, state1, reception.power(), reception.transmittersInReach());
    }
}
