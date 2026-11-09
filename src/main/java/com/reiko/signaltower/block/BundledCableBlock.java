package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.BundledCables;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Bundled Cable (v0.54.0): a thick cable carrying 16 colors at once (see {@link BundledCables}).
 * It joins every way, to other cables, to Cable Taps facing it, to Bundled Radio Transmitters and
 * Receivers, and to Radio Masts (so a Bundled Radio Transmitter can be fed at the foot of its
 * tower). It needs nothing under it. It gives out no redstone itself. Empty-hand right-click says
 * what the network carries.
 */
public class BundledCableBlock extends Block {
    public static final MapCodec<BundledCableBlock> CODEC = simpleCodec(BundledCableBlock::new);
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = {
            Block.box(5, 0, 5, 11, 5, 11), Block.box(5, 11, 5, 11, 16, 11), // down, up
            Block.box(5, 5, 0, 11, 11, 5), Block.box(5, 5, 11, 11, 11, 16), // north, south
            Block.box(0, 5, 5, 5, 11, 11), Block.box(11, 5, 5, 16, 11, 11)}; // west, east
    private final VoxelShape[] shapes = new VoxelShape[64];

    public BundledCableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction direction : Direction.values()) {
                if ((mask & 1 << direction.ordinal()) != 0) {
                    shape = Shapes.or(shape, ARMS[direction.ordinal()]);
                }
            }
            shapes[mask] = shape;
        }
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(new BooleanProperty[0]));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                mask |= 1 << direction.ordinal();
            }
        }
        return shapes[mask];
    }

    /** {@code state} with its arms drawn to whatever it joins around {@code pos}. */
    public BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                    BundledCables.connects(level.getBlockState(pos.relative(direction)), direction));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), BundledCables.connects(neighbor, direction));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            BundledCables.update(level, pos); // two networks may have joined
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !newState.is(state.getBlock())) {
            BundledCables.updateAround(level, pos); // it may have split in two
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BundledCables.Net net = BundledCables.scan(level, pos);
            player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".cable_status", net.cables(),
                    net.devices().size(), CableTapBlock.channelsText(net.channels())).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
