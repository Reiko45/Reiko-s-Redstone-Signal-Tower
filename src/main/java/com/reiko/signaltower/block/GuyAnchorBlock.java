package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.GuyAnchorBlockEntity;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.StormDamage;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Guy Wire Anchor (v0.54.0): a concrete footing with an eye bolt. On the ground near a tower (2 to
 * 12 blocks out), it finds the nearest Radio Mast column and a taut steel wire runs from it to two
 * thirds of the way up. Enough of them on one mast ({@code guyWiresToBrace}, 3) brace the tower, so
 * storms can't blow it down (see {@link StormDamage}). It needs a solid block under it.
 */
public class GuyAnchorBlock extends BaseEntityBlock {
    public static final MapCodec<GuyAnchorBlock> CODEC = simpleCodec(GuyAnchorBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 3, 13), Block.box(6.5, 3, 6.5, 9.5, 7, 9.5));

    public GuyAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(state.getBlock())) {
            SignalNetwork.get((ServerLevel) level).setAnchor(pos, null);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof GuyAnchorBlockEntity anchor) {
            anchor.findMast(serverLevel);
            player.displayClientMessage(status(serverLevel, anchor), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** "Bracing the mast at 702, -60, 180 (5 masts): 3 of 3 guy wires, it stands in any storm". */
    public static Component status(ServerLevel level, GuyAnchorBlockEntity anchor) {
        String key = "message." + SignalTower.MOD_ID + ".";
        BlockPos base = anchor.getBase();
        if (base == null) {
            return Component.translatable(key + "anchor_none").withStyle(ChatFormatting.GOLD);
        }
        SignalNetwork network = SignalNetwork.get(level);
        int masts = GuyAnchorBlockEntity.columnHeight(level, base);
        int braces = network.bracesOf(base);
        int needed = com.reiko.signaltower.Config.GUY_WIRES_TO_BRACE.get();
        return Component.translatable(key + (braces >= needed ? "anchor_braced" : "anchor_more"), base.getX(), base.getY(), base.getZ(),
                masts, braces, needed).withStyle(braces >= needed ? ChatFormatting.GREEN : ChatFormatting.AQUA);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuyAnchorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.GUY_ANCHOR_BE.get(), GuyAnchorBlockEntity::serverTick);
    }
}
