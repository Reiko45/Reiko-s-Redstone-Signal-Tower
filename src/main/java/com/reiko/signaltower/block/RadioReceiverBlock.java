package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Outputs the strongest signal it hears on its frequency, like a redstone block with a dimmer.
 */
public class RadioReceiverBlock extends RadioBlock {
    public static final MapCodec<RadioReceiverBlock> CODEC = simpleCodec(RadioReceiverBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public RadioReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    // ---- Redstone output ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    /**
     * Called on the server right after the output power changed (neighbours next to this block have
     * already been told). Lets the compact receiver also update the block it is attached to.
     */
    public void afterOutputChanged(Level level, BlockPos pos, BlockState state) {}

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        RadioReceiverBlockEntity rx = level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity found ? found : null;
        int frequency = rx != null ? rx.getFrequency() : 0;
        SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, frequency, pos, rx != null ? rx.getTunedBy() : null);
        return Component.translatable("message." + SignalTower.MOD_ID + ".receiver_status",
                FrequencyNames.label(level, frequency), reception.power(), reception.transmittersInReach());
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioReceiverBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.RADIO_RECEIVER_BE.get(), RadioReceiverBlockEntity::serverTick);
    }

    // ---- Visuals ----

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWER) > 0 && random.nextInt(4) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 1.05;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
