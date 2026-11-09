package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.radio.RadioHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A lattice tower segment. Stack these to lift a transmitter high into the air. A mast column also
 * works like a redstone cable: power any mast in it and the transmitter on top broadcasts that signal.
 * Right-click one with another mast (v0.51.0) and it goes in under the tower's top, see
 * {@link com.reiko.signaltower.radio.MastBuilder}. Since v0.55.0 you can climb it like a ladder
 * ({@link com.reiko.signaltower.radio.MastClimbing}), and right-clicking it with Tower Platforms puts
 * them in all round it.
 */
public class RadioMastBlock extends Block {
    public static final MapCodec<RadioMastBlock> CODEC = simpleCodec(RadioMastBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public RadioMastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult repaired = com.reiko.signaltower.radio.StormDamage.repairWith(stack, level, pos, player); // v0.54.0
        if (repaired != null) {
            return repaired;
        }
        if (stack.is(com.reiko.signaltower.registry.ModRegistry.TOWER_PLATFORM_ITEM.get())) {
            // v0.55.0: platforms all round the mast at this height (sneak to place just one).
            int placed = TowerPlatformBlock.ring(level, pos, player, stack, level.isClientSide);
            return placed > 0 ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!com.reiko.signaltower.radio.MastBuilder.isMast(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            com.reiko.signaltower.radio.MastBuilder.build(player, pos, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + com.reiko.signaltower.SignalTower.MOD_ID + ".mast_builder").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip." + com.reiko.signaltower.SignalTower.MOD_ID + ".mast_climb").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Something next to this mast changed, maybe a lever or wire: tell the transmitter on top. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        RadioHelper.refreshTransmitterAbove(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            RadioHelper.refreshTransmitterAbove(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this)) {
            // The column is now cut here, so whatever is above may have lost its input.
            RadioHelper.refreshTransmitterAbove(level, pos.above());
        }
    }
}
