package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.AntennaTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Dish Rotator (v0.52.0): a mast segment with a motor in it. Put it in the mast column of a
 * tower with a Dish Antenna and the redstone strength reaching it (0-15) turns the dish: 0 points
 * it the rotator's way (the way the placer looked, its arrow), each step another 22.5° clockwise,
 * so 4 is a right angle and 8 the opposite way. A lever, a comparator, or a Radio Receiver hearing a
 * strength from far away can swing the beam between bases. It counts as a mast (the column, power
 * cost, the mast builder), but its own redstone only turns the dish: it doesn't go up the column
 * to the tower. With two in a column, the one nearest the top turns the dish.
 */
public class DishRotatorBlock extends RadioMastBlock {
    public static final MapCodec<DishRotatorBlock> CODEC = simpleCodec(DishRotatorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final int SCAN_LIMIT = 4096;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 16, 13), Block.box(2, 4, 2, 14, 12, 14));

    public DishRotatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** A direction as a dish aim: north 0, east 4, south 8, west 12. */
    public static int segment(Direction direction) {
        return (direction.get2DDataValue() + 2) % 4 * 4;
    }

    /** Where a rotator in {@code state} given {@code power} points the dish. */
    public static int aim(BlockState state, int power) {
        return (segment(state.getValue(FACING)) + Math.max(0, Math.min(15, power))) & 15;
    }

    /** The redstone strength reaching the rotator at {@code pos}. */
    public static int power(Level level, BlockPos pos) {
        return level.getBestNeighborSignal(pos);
    }

    /** The rotator nearest the top in the mast column under the tower top at {@code top}, or null. */
    public static @Nullable BlockPos rotatorUnder(Level level, BlockPos top) {
        BlockPos.MutableBlockPos cursor = top.mutable().move(Direction.DOWN);
        for (int i = 0; i < SCAN_LIMIT; i++) {
            BlockState state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof RadioMastBlock)) {
                return null;
            }
            if (state.getBlock() instanceof DishRotatorBlock) {
                return cursor.immutable();
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    /** The aim a rotator under the tower top at {@code top} gives its dish, or -1 if there's none. */
    public static int aimAt(Level level, BlockPos top) {
        BlockPos rotator = rotatorUnder(level, top);
        return rotator == null ? -1 : aim(level.getBlockState(rotator), power(level, rotator));
    }

    /** The tower top on the mast column the rotator at {@code pos} is in, or null. */
    public static @Nullable BlockPos topAbove(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof RadioMastBlock; i++) {
            cursor.move(Direction.UP);
        }
        return AntennaTier.fits(level.getBlockState(cursor)) ? cursor.immutable() : null;
    }

    /**
     * For a tower top's refresh: if it has a Dish Antenna and a rotator in its column, turns the
     * dish the way the rotator says (with a whirr) and returns true if it moved.
     */
    public static boolean turnDish(ServerLevel level, BlockPos top) {
        BlockState state = level.getBlockState(top);
        if (AntennaTier.dishAim(state) < 0) {
            return false;
        }
        int aim = aimAt(level, top);
        if (aim < 0 || state.getValue(AntennaTier.AIM) == aim) {
            return false;
        }
        level.setBlock(top, state.setValue(AntennaTier.AIM, aim), Block.UPDATE_CLIENTS);
        level.playSound(null, top, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.35F, 1.7F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".dish_rotator").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".dish_rotator_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
