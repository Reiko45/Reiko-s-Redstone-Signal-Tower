package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import org.joml.Vector3f;

/**
 * Listens for its frequency where it stands and sends the strongest signal it hears out again
 * with its own reach, which grows with its height like a transmitter's. It needs no redstone: put
 * one on top of a mast column partway between a transmitter and far-away receivers to chain the
 * signal along. Its amber light glows while it's repeating something.
 * <p>
 * What it repeats is worked out by {@link SignalNetwork}, so it keeps working while its chunk is
 * unloaded. It also relays remote presses on any frequency, like a transmitter.
 */
public class RadioRepeaterBlock extends RadioBlock {
    public static final MapCodec<RadioRepeaterBlock> CODEC = simpleCodec(RadioRepeaterBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0F, 0.66F, 0.1F), 1.0F);

    public RadioRepeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY,
                com.reiko.signaltower.radio.AntennaTier.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, com.reiko.signaltower.radio.AntennaTier.PROPERTY);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeRepeater(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int frequency = level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity repeater ? repeater.getFrequency() : 0;
        int power = SignalNetwork.get(level).repeaterPower(level, pos);
        int height = RadioHelper.heightAboveSeaLevel(level, pos);
        String key = "message." + SignalTower.MOD_ID + ".";
        Component heightText = height > 0
                ? Component.translatable(key + "height_above", height)
                : Component.translatable(key + "height_below");
        Component repeating = power > 0
                ? Component.translatable(key + "repeater_repeating", power)
                : Component.translatable(key + "repeater_quiet");
        return Component.translatable(key + "repeater_status", FrequencyNames.label(level, frequency), repeating, heightText,
                RadioHelper.towerReach(level, pos, state));
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioRepeaterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.RADIO_REPEATER_BE.get(), RadioRepeaterBlockEntity::serverTick);
    }

    // ---- Visuals ----

    /** The sparks' color: amber for a repeater. */
    protected DustParticleOptions sparks() {
        return AMBER;
    }

    /** Sparks from the antenna tip while repeating. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double y = pos.getY() + 1.95 + random.nextDouble() * 0.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(sparks(), x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
