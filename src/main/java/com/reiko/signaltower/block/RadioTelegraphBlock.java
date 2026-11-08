package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TelegraphBlockEntity;
import com.reiko.signaltower.network.TelegraphFeedPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.Telegraph;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Radio Telegraph (v0.51.0): a desk with a brass key and a sounder. Right-click it to open its
 * screen: its frequency, a box to type a message (up to {@link Telegraph#MAX_LENGTH} characters)
 * and the last messages that reached it. See {@link Telegraph} for where messages go. When a
 * message arrives it clicks, its lamp lights, and it gives a 1-second redstone pulse (strongly
 * powering the block under it), so it can ring a bell or light a lamp.
 */
public class RadioTelegraphBlock extends RadioBlock {
    public static final MapCodec<RadioTelegraphBlock> CODEC = simpleCodec(RadioTelegraphBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape DESK = Block.box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape NORTH = Shapes.or(DESK, Block.box(3, 4, 11, 13, 10, 15), Block.box(6, 4, 2, 10, 8, 11));
    private static final VoxelShape SOUTH = Shapes.or(DESK, Block.box(3, 4, 1, 13, 10, 5), Block.box(6, 4, 5, 10, 8, 14));
    private static final VoxelShape EAST = Shapes.or(DESK, Block.box(1, 4, 3, 5, 10, 13), Block.box(5, 4, 6, 14, 8, 10));
    private static final VoxelShape WEST = Shapes.or(DESK, Block.box(11, 4, 3, 15, 10, 13), Block.box(2, 4, 6, 11, 8, 10));

    public RadioTelegraphBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // The key's knob points at whoever placed it.
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
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TelegraphBlockEntity telegraph) {
            PacketDistributor.sendToPlayer(serverPlayer, TelegraphFeedPayload.of(serverLevel, telegraph, null, true));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int frequency = level.getBlockEntity(pos) instanceof TelegraphBlockEntity telegraph ? telegraph.getFrequency() : 0;
        return Component.translatable("message." + SignalTower.MOD_ID + ".telegraph_status", FrequencyNames.label(level, frequency),
                SignalNetwork.get(level).messagesAt(frequency, pos).size(), Telegraph.reachText(level, pos, frequency));
    }

    // ---- Redstone: a pulse when a message arrives ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == Direction.UP ? 15 : 0;
    }

    /** After its pulse starts or ends: the block under it (strongly powered) tells its neighbours. */
    public static void updateBelow(Level level, BlockPos pos, Block block) {
        level.updateNeighborsAt(pos.below(), block);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean wasPowered = state.getValue(POWERED) && !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (wasPowered) {
            updateBelow(level, pos, this);
        }
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TelegraphBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.TELEGRAPH_BE.get(), TelegraphBlockEntity::serverTick);
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
        tooltip.add(Component.translatable(prefix + "telegraph_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
