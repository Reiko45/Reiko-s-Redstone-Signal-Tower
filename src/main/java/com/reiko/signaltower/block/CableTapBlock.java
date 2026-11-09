package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.BundledCables;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cable Tap (v0.54.0): where one color of a Bundled Cable meets normal redstone. It sits against a
 * cable ({@link #FACING} points at it) and works one color, picked with a dye (white at first):
 * <ul>
 *   <li><b>In</b> (the default): the redstone it gets, from any other side, goes onto that color
 *   (2 ticks later, like a repeater, so loops can't run away);</li>
 *   <li><b>Out:</b> it gives that color out as redstone on every other side.</li>
 * </ul>
 * Right-click with an empty hand switches in and out. {@link #POWER} is what it puts in (in) or
 * gives out (out); its colored band glows with it.
 */
public class CableTapBlock extends Block {
    public static final MapCodec<CableTapBlock> CODEC = simpleCodec(CableTapBlock::new);
    /** Towards the cable. */
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /** True: the color comes out as redstone; false: redstone goes onto the color. */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        for (Direction facing : Direction.values()) {
            // The body sits away from the cable, with a collar reaching it.
            VoxelShape shape = switch (facing) {
                case UP -> Shapes.or(Block.box(3, 0, 3, 13, 10, 13), Block.box(5, 10, 5, 11, 16, 11));
                case DOWN -> Shapes.or(Block.box(3, 6, 3, 13, 16, 13), Block.box(5, 0, 5, 11, 6, 11));
                case NORTH -> Shapes.or(Block.box(3, 3, 6, 13, 13, 16), Block.box(5, 5, 0, 11, 11, 6));
                case SOUTH -> Shapes.or(Block.box(3, 3, 0, 13, 13, 10), Block.box(5, 5, 10, 11, 11, 16));
                case WEST -> Shapes.or(Block.box(6, 3, 3, 16, 13, 13), Block.box(0, 5, 5, 6, 11, 11));
                case EAST -> Shapes.or(Block.box(0, 3, 3, 10, 13, 13), Block.box(10, 5, 5, 16, 11, 11));
            };
            SHAPES[facing.ordinal()] = shape;
        }
    }

    public CableTapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(COLOR, DyeColor.WHITE)
                .setValue(OUTPUT, false).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR, OUTPUT, POWER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    /** It faces the cable it was put against, else any cable beside it, else the block it was put against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction against = context.getClickedFace().getOpposite();
        Direction facing = against;
        if (!(level.getBlockState(pos.relative(against)).getBlock() instanceof BundledCableBlock)) {
            for (Direction direction : context.getNearestLookingDirections()) {
                if (level.getBlockState(pos.relative(direction)).getBlock() instanceof BundledCableBlock) {
                    facing = direction;
                    break;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---- In: redstone onto the color ----

    /** The redstone it gets from every side but the cable's. */
    public static int input(Level level, BlockPos pos, BlockState state) {
        Direction cable = state.getValue(FACING);
        int best = 0;
        for (Direction direction : Direction.values()) {
            if (direction != cable) {
                best = Math.max(best, level.getSignal(pos.relative(direction), direction));
            }
        }
        return best;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide && !state.getValue(OUTPUT) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OUTPUT)) {
            return;
        }
        int now = input(level, pos, state);
        if (now != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, now), Block.UPDATE_CLIENTS);
            BundledCables.update(level, pos);
        }
    }

    // ---- Out: the color as redstone ----

    /** Gives {@code power} out, if it's an output tap and that's new. */
    public void setOutput(Level level, BlockPos pos, BlockState state, int power) {
        if (state.getValue(OUTPUT) && state.getValue(POWER) != power) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        }
    }

    /** Dust joins it on every side but the cable's (an in tap reads it, an out tap feeds it). */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(OUTPUT);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // direction points from the block asking to this one: the cable's side gets nothing
        return state.getValue(OUTPUT) && direction != state.getValue(FACING).getOpposite() ? state.getValue(POWER) : 0;
    }

    // ---- Placing, breaking, using ----

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        if (!state.getValue(OUTPUT) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 2); // reads what it gets (again)
        }
        if (!oldState.is(state.getBlock()) || oldState.getValue(COLOR) != state.getValue(COLOR)
                || oldState.getValue(OUTPUT) != state.getValue(OUTPUT) || oldState.getValue(FACING) != state.getValue(FACING)) {
            BundledCables.update(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !newState.is(state.getBlock())) {
            BundledCables.update(level, pos.relative(state.getValue(FACING)));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            boolean output = !state.getValue(OUTPUT);
            BlockState next = state.setValue(OUTPUT, output).setValue(POWER, 0);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            if (!output) {
                level.scheduleTick(pos, this, 2);
            }
            BundledCables.update(level, pos);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4F, output ? 0.6F : 0.5F);
            player.displayClientMessage(describe(next).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof DyeItem dye) {
            DyeColor color = dye.getDyeColor();
            if (state.getValue(COLOR) == color) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                BlockState next = state.setValue(COLOR, color).setValue(POWER, state.getValue(OUTPUT) ? 0 : state.getValue(POWER));
                level.setBlock(pos, next, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                BundledCables.update(level, pos); // the old color loses it, the new one gets it
                player.displayClientMessage(describe(next).withStyle(ChatFormatting.AQUA), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** "Cable Tap (red, in): puts 15 onto red" / "(red, out): gives red out (8)". */
    public static MutableComponent describe(BlockState state) {
        Component color = RadioLampBlock.colorLabel(state.getValue(COLOR));
        return state.getValue(OUTPUT)
                ? Component.translatable(KEY + "tap_out", color, state.getValue(POWER))
                : Component.translatable(KEY + "tap_in", color, state.getValue(POWER));
    }

    /** "white 15, red 8", or "nothing" when every color is off. */
    public static Component channelsText(int[] channels) {
        MutableComponent text = null;
        for (int c = 0; c < channels.length; c++) {
            if (channels[c] > 0) {
                Component part = Component.translatable(KEY + "channel", RadioLampBlock.colorLabel(DyeColor.byId(c)), channels[c]);
                if (text == null) {
                    text = Component.empty().append(part);
                } else {
                    text.append(", ").append(part);
                }
            }
        }
        return text != null ? text : Component.translatable(KEY + "channels_none");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    /** For the block color handler: the band's dye color, brighter the more it carries. */
    public static int tint(@Nullable BlockState state) {
        return state == null ? RadioLampBlock.tint(DyeColor.WHITE, 8)
                : RadioLampBlock.tint(state.getValue(COLOR), state.getValue(POWER));
    }
}
