package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.AviationBeaconBlockEntity;
import com.reiko.signaltower.radio.AviationLight;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Aviation Beacon (v0.44.0): a warning light for a tower. It goes on top of a tower or a block,
 * on the side of a mast or a wall, or under a ceiling, and blinks red (or white) so the tower shows
 * from far away. Its settings are in its block state (so the client knows them and the item keeps
 * them): {@link #WHEN} (at night, always, or while its tower is broadcasting), {@link #PATTERN}
 * (blink, flash, steady) and {@link #COLOR}. An empty-hand right-click opens its screen. The light
 * itself is drawn by the client ({@code AviationBeaconRenderer}); its block entity only carries that.
 */
public class AviationBeaconBlock extends BaseEntityBlock {
    public static final MapCodec<AviationBeaconBlock> CODEC = simpleCodec(AviationBeaconBlock::new);
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AviationLight.When> WHEN = EnumProperty.create("when", AviationLight.When.class);
    public static final EnumProperty<AviationLight.Pattern> PATTERN = EnumProperty.create("pattern", AviationLight.Pattern.class);
    public static final EnumProperty<AviationLight.LightColor> COLOR = EnumProperty.create("color", AviationLight.LightColor.class);

    private static final VoxelShape FLOOR = Block.box(5, 0, 5, 11, 15.5, 11);
    private static final VoxelShape CEILING = Block.box(5, 2, 5, 11, 16, 11);
    // On a wall, facing away from it: the bracket reaches back to the wall.
    private static final VoxelShape NORTH = Block.box(5, 6, 5, 11, 15.5, 16);
    private static final VoxelShape SOUTH = Block.box(5, 6, 0, 11, 15.5, 11);
    private static final VoxelShape EAST = Block.box(0, 6, 5, 11, 15.5, 11);
    private static final VoxelShape WEST = Block.box(5, 6, 5, 16, 15.5, 11);

    public AviationBeaconBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH)
                .setValue(WHEN, AviationLight.When.NIGHT)
                .setValue(PATTERN, AviationLight.Pattern.BLINK)
                .setValue(COLOR, AviationLight.LightColor.RED));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, WHEN, PATTERN, COLOR);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ---- Where it is ----

    /** The way to the block it's attached to. */
    public static Direction toSupport(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> state.getValue(FACING).getOpposite();
        };
    }

    /** The middle of its light, inside its block (0-1 on each axis). */
    public static Vec3 lightCenter(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> new Vec3(0.5, 11.0 / 16, 0.5);
            case CEILING -> new Vec3(0.5, 6.0 / 16, 0.5);
            // On a wall the light stands on its bracket in the middle of the block, 8 pixels out.
            case WALL -> new Vec3(0.5, 11.5 / 16, 0.5);
        };
    }

    /** The tower it's on (see {@link AviationLight#towerOf}), or null. */
    public static @Nullable BlockPos towerOf(BlockGetter level, BlockPos pos, BlockState state) {
        return AviationLight.towerOf(level, pos, toSupport(state));
    }

    /**
     * True if it should be on now, blinking aside: always, while it's dark (night, rain, or a
     * dimension with no day), or while its tower is broadcasting. Works on both sides.
     */
    public static boolean isActive(Level level, BlockPos pos, BlockState state) {
        return switch (state.getValue(WHEN)) {
            case ALWAYS -> true;
            case NIGHT -> AviationLight.isDark(level);
            case SENDING -> {
                BlockPos tower = towerOf(level, pos, state);
                yield tower != null && AviationLight.isSending(level.getBlockState(tower));
            }
        };
    }

    /** True if its light is on right now: {@link #isActive} and in the on part of its blink. */
    public static boolean isLit(Level level, BlockPos pos, BlockState state) {
        return isActive(level, pos, state) && state.getValue(PATTERN).onAt(level.getGameTime());
    }

    public static Component describe(BlockState state) {
        return AviationLight.describe(state.getValue(WHEN), state.getValue(PATTERN), state.getValue(COLOR));
    }

    // ---- Shape and attachment ----

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> switch (state.getValue(FACING)) {
                case SOUTH -> SOUTH;
                case EAST -> EAST;
                case WEST -> WEST;
                default -> NORTH;
            };
        };
    }

    /** On any sturdy face, and also on a mast or a tower top (which aren't full blocks). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = toSupport(state);
        BlockPos support = pos.relative(toSupport);
        BlockState supportState = level.getBlockState(support);
        return supportState.getBlock() instanceof RadioMastBlock || AviationLight.isTowerTop(supportState)
                || supportState.isFaceSturdy(level, support, toSupport.getOpposite());
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
        // Pops off (keeping its settings) when what it's on goes.
        return toSupport(state) == direction && !state.canSurvive(level, pos)
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

    // ---- Using it ----

    /** Empty hand: its screen (it only shows the block state, so the client opens it by itself). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            com.reiko.signaltower.client.ClientHooks.openBeaconScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        BlockItemStateProperties properties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        BlockState state = properties.apply(defaultBlockState());
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".beacon_light", describe(state)).withStyle(ChatFormatting.YELLOW));
    }

    // ---- Block entity (for the client's light) ----

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AviationBeaconBlockEntity(pos, state);
    }
}
