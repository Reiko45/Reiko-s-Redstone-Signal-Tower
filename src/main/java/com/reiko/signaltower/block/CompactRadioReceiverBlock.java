package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A button-sized Radio Receiver. It sticks to a wall, floor or ceiling like a button and pops off
 * (keeping its frequency) if that block is removed. Like a button or lever it strongly powers the
 * block it is attached to, so it can drive redstone on the other side of a wall.
 */
public class CompactRadioReceiverBlock extends RadioReceiverBlock {
    public static final MapCodec<CompactRadioReceiverBlock> CODEC = simpleCodec(CompactRadioReceiverBlock::new);
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Same size as a vanilla button.
    private static final VoxelShape CEILING_X = Block.box(6, 14, 5, 10, 16, 11);
    private static final VoxelShape CEILING_Z = Block.box(5, 14, 6, 11, 16, 10);
    private static final VoxelShape FLOOR_X = Block.box(6, 0, 5, 10, 2, 11);
    private static final VoxelShape FLOOR_Z = Block.box(5, 0, 6, 11, 2, 10);
    private static final VoxelShape NORTH = Block.box(5, 6, 14, 11, 10, 16);
    private static final VoxelShape SOUTH = Block.box(5, 6, 0, 11, 10, 2);
    private static final VoxelShape WEST = Block.box(14, 6, 5, 16, 10, 11);
    private static final VoxelShape EAST = Block.box(0, 6, 5, 2, 10, 11);

    public CompactRadioReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWER);
    }

    /** The way the receiver points, away from the block it's stuck to. */
    public static Direction pointing(BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(FACING);
        };
    }

    // ---- Shape and attachment (same rules as a button) ----

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean xAxis = state.getValue(FACING).getAxis() == Direction.Axis.X;
        return switch (state.getValue(FACE)) {
            case FLOOR -> xAxis ? FLOOR_X : FLOOR_Z;
            case CEILING -> xAxis ? CEILING_X : CEILING_Z;
            case WALL -> switch (state.getValue(FACING)) {
                case EAST -> EAST;
                case WEST -> WEST;
                case SOUTH -> SOUTH;
                default -> NORTH;
            };
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return FaceAttachedHorizontalDirectionalBlock.canAttach(level, pos, pointing(state).getOpposite());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState state = direction.getAxis() == Direction.Axis.Y
                    ? defaultBlockState()
                        .setValue(FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)
                        .setValue(FACING, context.getHorizontalDirection())
                    : defaultBlockState()
                        .setValue(FACE, AttachFace.WALL)
                        .setValue(FACING, direction.getOpposite());
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Pop off (with drops, so the frequency is kept) when the supporting block goes away.
        return pointing(state).getOpposite() == direction && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---- Redstone: weak power all around, strong power into the attached block ----

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return pointing(state) == direction ? state.getValue(POWER) : 0;
    }

    @Override
    public void afterOutputChanged(Level level, BlockPos pos, BlockState state) {
        // The attached block is strongly powered, so things touching it need to hear about it too.
        level.updateNeighborsAt(pos.relative(pointing(state).getOpposite()), this);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!movedByPiston && !state.is(newState.getBlock()) && state.getValue(POWER) > 0) {
            level.updateNeighborsAt(pos, this);
            afterOutputChanged(level, pos, state);
        }
    }

    // ---- Visuals ----

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWER) > 0 && random.nextInt(6) == 0) {
            Direction out = pointing(state);
            double x = pos.getX() + 0.5 - out.getStepX() * 0.3;
            double y = pos.getY() + 0.5 - out.getStepY() * 0.3;
            double z = pos.getZ() + 0.5 - out.getStepZ() * 0.3;
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
