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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Redstone dust that sticks to the side of a block. It runs up, down, left and right along the wall,
 * loses 1 strength per block like normal dust, and connects to normal redstone dust and components.
 * <p>
 * {@code FACING} is the way it faces, away from the block it's stuck to. {@code LEFT}/{@code RIGHT}
 * are as seen by someone standing in front of the wall looking at it.
 * <p>
 * Power works like vanilla dust: while a wire works out its own strength it switches off its signal
 * output ({@link #shouldSignal}), so wires never feed themselves. Next to vanilla dust (whose own switch
 * we can't reach) it hands over one less than it has, so mixed loops of both still fade to 0 instead
 * of latching on.
 */
public class WallRedstoneBlock extends Block {
    public static final MapCodec<WallRedstoneBlock> CODEC = simpleCodec(WallRedstoneBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    // A thin layer against the wall it's stuck to.
    private static final VoxelShape ON_NORTH_WALL = Block.box(0, 0, 0, 16, 16, 1);   // facing south
    private static final VoxelShape ON_SOUTH_WALL = Block.box(0, 0, 15, 16, 16, 16); // facing north
    private static final VoxelShape ON_WEST_WALL = Block.box(0, 0, 0, 1, 16, 16);    // facing east
    private static final VoxelShape ON_EAST_WALL = Block.box(15, 0, 0, 16, 16, 16);  // facing west

    private boolean shouldSignal = true;

    public WallRedstoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH).setValue(POWER, 0)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER, UP, DOWN, LEFT, RIGHT);
    }

    public static Direction leftOf(Direction facing) {
        return facing.getClockWise();
    }

    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static @Nullable BooleanProperty armFor(Direction facing, Direction direction) {
        if (direction == Direction.UP) return UP;
        if (direction == Direction.DOWN) return DOWN;
        if (direction == leftOf(facing)) return LEFT;
        if (direction == rightOf(facing)) return RIGHT;
        return null;
    }

    private static BlockPos attachedTo(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    // ---- Shape and attachment ----

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> ON_SOUTH_WALL;
            case EAST -> ON_WEST_WALL;
            case WEST -> ON_EAST_WALL;
            default -> ON_NORTH_WALL;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos wall = attachedTo(state, pos);
        return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction looking : context.getNearestLookingDirections()) {
            if (looking.getAxis().isHorizontal()) {
                BlockState state = defaultBlockState().setValue(FACING, looking.getOpposite());
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return withConnections(context.getLevel(), context.getClickedPos(), state);
                }
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState(); // pops off with drops, like normal dust
        }
        BooleanProperty arm = armFor(state.getValue(FACING), direction);
        return arm == null ? state : state.setValue(arm, connectsTo(level, neighborPos, neighborState, direction));
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (Direction direction : new Direction[] {Direction.UP, Direction.DOWN, leftOf(facing), rightOf(facing)}) {
            BlockPos neighborPos = pos.relative(direction);
            state = state.setValue(armFor(facing, direction),
                    connectsTo(level, neighborPos, level.getBlockState(neighborPos), direction));
        }
        return state;
    }

    /** Whether to draw a line towards this neighbor. */
    private boolean connectsTo(BlockGetter level, BlockPos neighborPos, BlockState neighbor, Direction direction) {
        return neighbor.is(this) || neighbor.is(Blocks.REDSTONE_WIRE) || neighbor.canRedstoneConnectTo(level, neighborPos, direction);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        // Mirroring a wall swaps what's on the left and the right.
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
                .setValue(LEFT, state.getValue(RIGHT))
                .setValue(RIGHT, state.getValue(LEFT));
    }

    // ---- Power ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return shouldSignal;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!shouldSignal) {
            return 0;
        }
        int power = state.getValue(POWER);
        // Hand vanilla dust one less, the same as dust passing power to dust.
        BlockPos asker = pos.relative(direction.getOpposite());
        return power > 0 && level.getBlockState(asker).is(Blocks.REDSTONE_WIRE) ? power - 1 : power;
    }

    /** Strongly powers the block it's stuck to (one less, so loops through that block fade out). */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return shouldSignal && direction == state.getValue(FACING) ? Math.max(0, state.getValue(POWER) - 1) : 0;
    }

    private int calculateTargetPower(Level level, BlockPos pos) {
        int input = 0;
        shouldSignal = false;
        try {
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (!neighbor.is(this) && !neighbor.is(Blocks.REDSTONE_WIRE)) {
                    input = Math.max(input, level.getSignal(neighborPos, direction));
                }
            }
        } finally {
            shouldSignal = true;
        }
        if (input >= 15) {
            return 15;
        }
        int fromWires = 0;
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.is(this)) {
                fromWires = Math.max(fromWires, neighbor.getValue(POWER));
            } else if (neighbor.is(Blocks.REDSTONE_WIRE)) {
                fromWires = Math.max(fromWires, neighbor.getValue(RedStoneWireBlock.POWER));
            }
        }
        return Math.max(input, fromWires - 1);
    }

    private void updatePowerStrength(Level level, BlockPos pos, BlockState state) {
        int target = calculateTargetPower(level, pos);
        if (state.getValue(POWER) == target) {
            return;
        }
        if (level.getBlockState(pos) == state) {
            level.setBlock(pos, state.setValue(POWER, target), Block.UPDATE_CLIENTS);
        }
        // Same as vanilla dust: tell everything within two blocks, so the change ripples along the wire.
        Set<BlockPos> toUpdate = new HashSet<>();
        toUpdate.add(pos);
        for (Direction direction : Direction.values()) {
            toUpdate.add(pos.relative(direction));
        }
        for (BlockPos updatePos : toUpdate) {
            level.updateNeighborsAt(updatePos, this);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && state.canSurvive(level, pos)) {
            updatePowerStrength(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide) {
            // Placed by a command or structure rather than a player: work out the lines now.
            BlockState connected = withConnections(level, pos, state);
            if (connected != state) {
                level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
                state = connected;
            }
            updatePowerStrength(level, pos, state);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(attachedTo(state, pos), this);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!movedByPiston && !state.is(newState.getBlock()) && !level.isClientSide) {
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
        }
    }

    // ---- Visuals ----

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = state.getValue(POWER);
        if (power == 0 || random.nextInt(5) != 0) {
            return;
        }
        int rgb = RedStoneWireBlock.getColorForPower(power);
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 - facing.getStepX() * 0.42 + (random.nextDouble() - 0.5) * 0.3 * Math.abs(facing.getStepZ());
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.42 + (random.nextDouble() - 0.5) * 0.3 * Math.abs(facing.getStepX());
        level.addParticle(new DustParticleOptions(color, 1.0F), x, y, z, 0.0, 0.0, 0.0);
    }
}
