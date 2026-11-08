package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.radio.AntennaTier;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Broadcasts the strongest redstone signal it receives (0-15) on its frequency. Input can come from
 * the blocks around it, or from around any Radio Mast in the column directly beneath it. Its reach
 * grows with its height above sea level.
 */
public class RadioTransmitterBlock extends RadioBlock {
    public static final MapCodec<RadioTransmitterBlock> CODEC = simpleCodec(RadioTransmitterBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public RadioTransmitterBlock(Properties properties) {
        super(properties);
        BlockState initial = stateDefinition.any().setValue(POWERED, false);
        // A full-size one has an antenna slot (v0.45.0); the compact ones define their own states without it.
        registerDefaultState(initial.hasProperty(AntennaTier.PROPERTY) ? initial.setValue(AntennaTier.PROPERTY, AntennaTier.NONE) : initial);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, AntennaTier.PROPERTY);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = RadioHelper.readTransmitterInput(context.getLevel(), context.getClickedPos()) > 0;
        return defaultBlockState().setValue(POWERED, powered);
    }

    // ---- Keeping the network up to date ----

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.refresh();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeTransmitter(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int frequency = level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity tx ? tx.getFrequency() : 0;
        int height = RadioHelper.heightAboveSeaLevel(level, pos);
        Component heightText = height > 0
                ? Component.translatable("message." + SignalTower.MOD_ID + ".height_above", height)
                : Component.translatable("message." + SignalTower.MOD_ID + ".height_below");
        return Component.translatable("message." + SignalTower.MOD_ID + ".transmitter_status",
                FrequencyNames.label(level, frequency),
                RadioHelper.readTransmitterInput(level, pos),
                heightText,
                RadioHelper.towerReach(level, pos, state));
    }

    // ---- Redstone ----

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        // Lets redstone dust visibly connect to the transmitter so it's clear where the input goes.
        return direction != null;
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioTransmitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.RADIO_TRANSMITTER_BE.get(), RadioTransmitterBlockEntity::serverTick);
    }

    // ---- Visuals ----

    /** Red sparks from the antenna tip while broadcasting. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED) && random.nextInt(3) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double y = pos.getY() + 1.95 + random.nextDouble() * 0.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
