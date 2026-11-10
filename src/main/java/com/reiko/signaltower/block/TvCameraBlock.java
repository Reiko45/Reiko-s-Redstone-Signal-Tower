package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import com.reiko.signaltower.network.OpenTvCameraPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.TvCamera;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The TV Camera (v0.66.0). It stands on a floor (a tripod), hangs on a wall (a bracket) or from a
 * ceiling, and looks the way it faces: on a wall away from the wall, on a floor or ceiling the way
 * whoever placed it was looking. Tuned like a transmitter (on a private frequency only the owner and
 * members may tune one), it doesn't turn its frequency on: TV Monitors on its frequency that it
 * reaches show its picture. Its red REC light is on while a monitor shows it, and a comparator reads
 * how many do. An empty-hand right-click opens its settings (sneak: the status line); it pops off
 * with the block it's stuck to. The work is in {@link TvCameraBlockEntity}; the head is drawn by
 * {@code client.TvCameraRenderer}.
 */
public class TvCameraBlock extends RadioBlock {
    public static final MapCodec<TvCameraBlock> CODEC = simpleCodec(TvCameraBlock::new);
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** True while a monitor shows it (the red light on its head). */
    public static final BooleanProperty REC = BooleanProperty.create("rec");

    private static final VoxelShape FLOOR = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(7, 2, 7, 9, 9, 9),
            Block.box(4, 8, 4, 12, 15, 12));
    private static final VoxelShape CEILING = Shapes.or(Block.box(5, 14, 5, 11, 16, 11), Block.box(7, 10, 7, 9, 14, 9),
            Block.box(4, 3, 4, 12, 10, 12));
    private static final VoxelShape[] WALL = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // The bracket on the wall behind, the head in front of it (shapes for one looking north, turned).
            WALL[facing.get2DDataValue()] = Shapes.or(turned(facing, 5, 4, 14, 11, 12, 16), turned(facing, 4, 5, 3, 12, 13, 14));
        }
    }

    /** {@code Block.box(x1, y1, z1, x2, y2, z2)} for one looking north, turned to look {@code facing}. */
    private static VoxelShape turned(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public TvCameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH)
                .setValue(REC, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, REC);
    }

    /** The way it points away from the block it's stuck to. */
    public static Direction pointing(BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(FACING);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> WALL[state.getValue(FACING).get2DDataValue()];
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

    // ---- Use ----

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            return super.useWithoutItem(state, level, pos, player, hitResult); // the status line
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TvCameraBlockEntity camera) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenTvCameraPayload.of(serverLevel, camera));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof TvCameraBlockEntity camera)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".tv_camera_status",
                FrequencyNames.label(level, camera.getFrequency()), camera.statusText(level));
    }

    // ---- Comparator: how many monitors show it ----

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TvCameraBlockEntity camera ? Math.min(15, camera.getWatchers()) : 0;
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
        Integer settings = stack.get(ModRegistry.CAMERA_SETTINGS.get());
        if (settings != null) {
            tooltip.add(settingsText(settings).withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Tilt: Auto, Lens: Normal, Pan, Night vision". */
    public static net.minecraft.network.chat.MutableComponent settingsText(int settings) {
        String key = "screen." + SignalTower.MOD_ID + ".tv_camera.";
        int tilt = settings & TvCamera.TILT_MASK;
        Component tiltText = tilt < TvCamera.TILTS.length ? Component.translatable(key + "tilt_degrees", -TvCamera.TILTS[tilt])
                : Component.translatable(key + "tilt_auto");
        net.minecraft.network.chat.MutableComponent text = Component.translatable(key + "summary", tiltText,
                Component.translatable(key + "lens_" + TvCamera.lens(settings)));
        if ((settings & TvCamera.PAN) != 0) {
            text.append(", ").append(Component.translatable(key + "pan_on"));
        }
        if ((settings & TvCamera.NIGHT_VISION) != 0) {
            text.append(", ").append(Component.translatable(key + "night_on"));
        }
        return text;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TvCameraBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.TV_CAMERA_BE.get(), TvCameraBlockEntity::serverTick);
    }
}
