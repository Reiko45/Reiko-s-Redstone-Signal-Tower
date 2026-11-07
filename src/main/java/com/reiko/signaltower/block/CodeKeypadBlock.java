package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.network.OpenKeypadScreenPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.IntruderAlerts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Code Keypad (v0.37.0): a thin panel on a wall, floor or ceiling. Right-click it (with an
 * empty hand or most items) to open its keypad screen; the right code sends a pulse on its
 * frequency, see {@link CodeKeypadBlockEntity}. While the pulse lasts it's lit and, like a button,
 * powers the block it's on, so a door right next to it opens without any radio.
 */
public class CodeKeypadBlock extends RadioBlock {
    public static final MapCodec<CodeKeypadBlock> CODEC = simpleCodec(CodeKeypadBlock::new);
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    // An 8 x 10 plate, 1 pixel thick.
    private static final VoxelShape CEILING_X = Block.box(3, 15, 4, 13, 16, 12);
    private static final VoxelShape CEILING_Z = Block.box(4, 15, 3, 12, 16, 13);
    private static final VoxelShape FLOOR_X = Block.box(3, 0, 4, 13, 1, 12);
    private static final VoxelShape FLOOR_Z = Block.box(4, 0, 3, 12, 1, 13);
    private static final VoxelShape NORTH = Block.box(4, 3, 15, 12, 13, 16);
    private static final VoxelShape SOUTH = Block.box(4, 3, 0, 12, 13, 1);
    private static final VoxelShape WEST = Block.box(15, 3, 4, 16, 13, 12);
    private static final VoxelShape EAST = Block.box(0, 3, 4, 1, 13, 12);

    public CodeKeypadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CodeKeypadBlockEntity(pos, state);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        return Component.translatable("block." + SignalTower.MOD_ID + ".code_keypad");
    }

    /** The way the keypad faces, away from the block it's on. */
    public static Direction pointing(BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(FACING);
        };
    }

    // ---- Using it ----

    /**
     * The tuner keeps its own use on it (it tunes the keypad); anything else in hand, a remote
     * included, opens the keypad. (Sneak-clicking with an item never gets here: the item's own
     * use runs, so remotes, pagers and the scanner still copy its frequency.)
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return stack.getItem() instanceof FrequencyTunerItem
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CodeKeypadBlockEntity keypad) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenKeypadScreenPayload.of(keypad, serverPlayer, level.getGameTime()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Whoever places it owns it (and is its tuner). On someone else's private frequency it stays
     * silent unless they're a member: they're told, and so is the frequency's owner.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof CodeKeypadBlockEntity keypad) {
            keypad.setTunedBy(player.getUUID());
            keypad.setOwner(player.getUUID(), player.getGameProfile().getName());
            Component silenced = FrequencyClaims.refusal(serverLevel, keypad.getFrequency(), player.getUUID(), "private_silenced");
            if (silenced != null) {
                player.displayClientMessage(silenced, true);
                IntruderAlerts.report(serverLevel.getServer(), keypad.getFrequency(), player, IntruderAlerts.Kind.SILENT, this);
            } else {
                player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".keypad_placed"), true);
            }
        }
    }

    /** Lights it and powers the attached block for {@code ticks} (a new press during a pulse restarts the time). */
    public void press(BlockState state, ServerLevel level, BlockPos pos, int ticks) {
        if (!state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
            updateNeighbours(state, level, pos);
        }
        level.scheduleTick(pos, this, ticks);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
            updateNeighbours(state, level, pos);
        }
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(pointing(state).getOpposite()), this);
    }

    // ---- Redstone: like a button, weak power all around and strong power into the attached block ----

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
        return state.getValue(POWERED) && pointing(state) == direction ? 15 : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!movedByPiston && !state.is(newState.getBlock()) && state.getValue(POWERED)) {
            updateNeighbours(state, level, pos);
        }
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
}
