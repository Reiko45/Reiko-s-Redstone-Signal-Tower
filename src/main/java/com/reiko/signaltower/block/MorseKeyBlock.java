package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.MorseKeyBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.Telegraph;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Morse Key (v0.62.0): a straight key on a small wooden base, tuned like any radio block.
 * Right-click it and hold for a dash, click for a dot (or give it redstone); sneak + right-click
 * shows its status. See {@link MorseKeyBlockEntity}. {@code DOWN} is the lever, for the model.
 */
public class MorseKeyBlock extends RadioBlock {
    public static final MapCodec<MorseKeyBlock> CODEC = simpleCodec(MorseKeyBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final VoxelShape NORTH_SOUTH = Block.box(3, 0, 1, 13, 6, 15);
    private static final VoxelShape EAST_WEST = Block.box(1, 0, 3, 15, 6, 13);

    public MorseKeyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOWN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Its knob towards whoever placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return super.useWithoutItem(state, level, pos, player, hitResult); // the status readout
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof MorseKeyBlockEntity key) {
            Component refused = key.press(serverLevel, player);
            if (refused != null) {
                player.displayClientMessage(refused, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MorseKeyBlockEntity key) {
            key.redstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this) && level.getBlockEntity(pos) instanceof MorseKeyBlockEntity key) {
            key.redstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof MorseKeyBlockEntity key)) {
            return Component.empty();
        }
        Component last = key.lastSent().isEmpty() ? Component.translatable("message." + SignalTower.MOD_ID + ".morse_nothing_sent")
                : Component.literal(key.lastSent());
        return Component.translatable("message." + SignalTower.MOD_ID + ".morse_status", FrequencyNames.label(level, key.getFrequency()),
                Telegraph.reachText(level, pos, key.getFrequency()), last);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MorseKeyBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.MORSE_KEY_BE.get(), MorseKeyBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(frequency, tooltip);
        }
        tooltip.add(Component.translatable(prefix + "morse_key_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
