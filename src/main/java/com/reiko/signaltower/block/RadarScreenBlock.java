package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadarScreenBlockEntity;
import com.reiko.signaltower.network.OpenRadarScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.Radar;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Radar Screen (v0.65.0): a panel on a wall, tuned like a receiver, that shows the nearest
 * Radar Dish on its frequency whose reach covers it: a green sweep line turning with the dish, blips
 * for what the dish sees (colored by kind), towers as small squares, and where the screen itself is.
 * North is up. On a private frequency only a screen placed (or tuned) by the owner or a member shows
 * anything. It pings when something new shows up. An empty-hand right-click opens its settings
 * (sneak: the status line); it pops off with its wall. The work is in {@link RadarScreenBlockEntity};
 * the picture is drawn by {@code client.RadarScreenRenderer}.
 */
public class RadarScreenBlock extends RadioBlock {
    public static final MapCodec<RadarScreenBlock> CODEC = simpleCodec(RadarScreenBlock::new);
    /** The way its face looks (the wall it hangs on is behind it). */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** True while it shows a dish (it glows a little). */
    public static final BooleanProperty ACTIVE = RadarDishBlock.ACTIVE;

    private static final VoxelShape NORTH = Block.box(1, 1, 13, 15, 15, 16);
    private static final VoxelShape SOUTH = Block.box(1, 1, 0, 15, 15, 3);
    private static final VoxelShape WEST = Block.box(13, 1, 1, 16, 15, 15);
    private static final VoxelShape EAST = Block.box(0, 1, 1, 3, 15, 15);

    public RadarScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return level.getBlockState(pos.relative(facing.getOpposite())).isSolid();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite());
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return state;
                }
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            return super.useWithoutItem(state, level, pos, player, hitResult); // the status line
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RadarScreenBlockEntity screen) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenRadarScreenPayload.of(serverLevel, screen));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RadarScreenBlockEntity screen)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".radar_screen_status",
                FrequencyNames.label(level, screen.getFrequency()), screen.statusText());
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
        Integer settings = stack.get(ModRegistry.RADAR_SETTINGS.get());
        if (settings != null) {
            tooltip.add(Component.translatable(prefix + "radar_zoom", zoomLabel(settings)).withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Whole reach", or "128 blocks" and so on. */
    public static Component zoomLabel(int settings) {
        return Component.translatable("screen." + SignalTower.MOD_ID + ".radar.zoom_" + Math.min(Radar.ZOOM_RANGES.length - 1, settings & Radar.ZOOM_MASK));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadarScreenBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.RADAR_SCREEN_BE.get(), RadarScreenBlockEntity::serverTick);
    }
}
