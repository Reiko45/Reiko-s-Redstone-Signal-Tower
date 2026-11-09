package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.radio.MastClimbing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * v0.55.0: a steel grating to stand on up a tower, with a rail on every side that has nothing next
 * to it. Right-click a mast with them and they go in all round it at that height (see {@link #ring}).
 * Like scaffolding, the deck is only solid from above: climbing a mast you come up through it, and
 * sneaking beside the mast you go back down through it.
 */
public class TowerPlatformBlock extends Block {
    public static final MapCodec<TowerPlatformBlock> CODEC = simpleCodec(TowerPlatformBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape DECK = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape RAIL_NORTH = Block.box(0, 2, 0, 16, 16, 1);
    private static final VoxelShape RAIL_SOUTH = Block.box(0, 2, 15, 16, 16, 16);
    private static final VoxelShape RAIL_WEST = Block.box(0, 2, 0, 1, 16, 16);
    private static final VoxelShape RAIL_EAST = Block.box(15, 2, 0, 16, 16, 16);
    // Rails stop you as a fence does (1.5 blocks high), though they look a block high.
    private static final VoxelShape BAR_NORTH = Block.box(0, 0, 0, 16, 24, 1);
    private static final VoxelShape BAR_SOUTH = Block.box(0, 0, 15, 16, 24, 16);
    private static final VoxelShape BAR_WEST = Block.box(0, 0, 0, 1, 24, 16);
    private static final VoxelShape BAR_EAST = Block.box(15, 0, 0, 16, 24, 16);

    public TowerPlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    public static BooleanProperty railOn(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> EAST;
        };
    }

    /** A rail goes on a side with nothing next to it: not another platform, a mast, or a wall. */
    public static boolean railTowards(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos next = pos.relative(direction);
        BlockState neighbor = level.getBlockState(next);
        return !(neighbor.getBlock() instanceof TowerPlatformBlock || neighbor.getBlock() instanceof RadioMastBlock
                || neighbor.isFaceSturdy(level, next, direction.getOpposite()));
    }

    /** This platform's state at {@code pos} with its rails worked out from what's around it. */
    public BlockState withRails(BlockGetter level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(railOn(direction), railTowards(level, pos, direction));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withRails(context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis().isHorizontal() ? state.setValue(railOn(direction), railTowards(level, pos, direction)) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = DECK;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, RAIL_NORTH);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, RAIL_SOUTH);
        if (state.getValue(WEST)) shape = Shapes.or(shape, RAIL_WEST);
        if (state.getValue(EAST)) shape = Shapes.or(shape, RAIL_EAST);
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = deckSolidFor(level, pos, context) ? DECK : Shapes.empty();
        if (state.getValue(NORTH)) shape = Shapes.or(shape, BAR_NORTH);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, BAR_SOUTH);
        if (state.getValue(WEST)) shape = Shapes.or(shape, BAR_WEST);
        if (state.getValue(EAST)) shape = Shapes.or(shape, BAR_EAST);
        return shape;
    }

    /**
     * The deck holds whatever is above it, except someone sneaking beside a mast (going down the
     * tower); from below (climbing up the mast) you pass through it.
     */
    public static boolean deckSolidFor(BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext) || entityContext.getEntity() == null) {
            return true; // not an entity asking (block support, placing): a floor
        }
        if (!context.isAbove(DECK, pos, true)) {
            return false;
        }
        Entity entity = entityContext.getEntity();
        return !(entity instanceof Player player && player.isShiftKeyDown() && MastClimbing.touchingMast(level, player.getBoundingBox()));
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /**
     * Puts platforms in the eight places round the mast at {@code mast} (where there's room), as many
     * as {@code stack} holds unless {@code player} is in creative. Returns how many went in (or, with
     * {@code simulate}, would).
     */
    public static int ring(Level level, BlockPos mast, @Nullable Player player, ItemStack stack, boolean simulate) {
        TowerPlatformBlock block = (TowerPlatformBlock) com.reiko.signaltower.registry.ModRegistry.TOWER_PLATFORM.get();
        boolean free = player != null && player.getAbilities().instabuild;
        int budget = free ? Integer.MAX_VALUE : stack.getCount();
        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 && dz == 0) || placed >= budget) {
                    continue;
                }
                BlockPos pos = mast.offset(dx, 0, dz);
                if (!level.getBlockState(pos).canBeReplaced()
                        || !level.isUnobstructed(block.defaultBlockState(), pos,
                        player == null ? CollisionContext.empty() : CollisionContext.of(player))) {
                    continue;
                }
                placed++;
                if (!simulate) {
                    level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (!simulate && placed > 0) {
            // Now they're all in, each works out its rails.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = mast.offset(dx, 0, dz);
                    if (level.getBlockState(pos).getBlock() instanceof TowerPlatformBlock) {
                        level.setBlock(pos, block.withRails(level, pos), Block.UPDATE_ALL);
                    }
                }
            }
            if (!free) {
                stack.shrink(placed);
            }
            SoundType sound = block.defaultBlockState().getSoundType();
            level.playSound(null, mast, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, mast);
        }
        return placed;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
