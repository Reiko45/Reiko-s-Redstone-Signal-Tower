package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.LaunchPadBlockEntity;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Launch Pad (v0.61.0): load a Satellite and eight Firework Rockets (by hand or by hopper), then
 * give it a redstone pulse to send the satellite up to orbit over the dimension
 * ({@link LaunchPadBlockEntity}). An empty hand takes back what's on it. A comparator reads 15 when
 * it's ready.
 */
public class LaunchPadBlock extends BaseEntityBlock {
    public static final MapCodec<LaunchPadBlock> CODEC = simpleCodec(LaunchPadBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.LAUNCH_PAD_BE.get(),
                level.isClientSide ? LaunchPadBlockEntity::clientTick : LaunchPadBlockEntity::serverTick);
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
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)
                || !LaunchPadBlockEntity.isSatellite(stack) && !LaunchPadBlockEntity.isFirework(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (pad.isLaunching()) {
            player.displayClientMessage(Component.translatable(KEY + "launch_launching").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }
        if (LaunchPadBlockEntity.isSatellite(stack)) {
            if (!pad.load(stack)) {
                player.displayClientMessage(Component.translatable(KEY + "launch_has_satellite").withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 0.9F);
        } else {
            int taken = pad.loadFireworks(stack);
            if (taken <= 0) {
                player.displayClientMessage(Component.translatable(pad.fireworkCount() >= LaunchPadBlockEntity.FIREWORKS_NEEDED
                        ? KEY + "launch_fireworks_full" : KEY + "launch_fireworks_mixed").withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) stack.shrink(taken);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        player.displayClientMessage(status(level, pad), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        List<ItemStack> back = pad.unload();
        if (back.isEmpty()) {
            player.displayClientMessage(status(level, pad), true);
            return InteractionResult.CONSUME;
        }
        for (ItemStack stack : back) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(KEY + "launch_unloaded").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }

    /** "Ready for launch: give it a redstone pulse", or what it still needs. */
    private static Component status(Level level, LaunchPadBlockEntity pad) {
        String problem = pad.launchProblem(level);
        return problem == null ? Component.translatable(KEY + "launch_ready").withStyle(ChatFormatting.GREEN)
                : "launching".equals(problem) ? Component.translatable(KEY + "launch_launching").withStyle(ChatFormatting.GRAY)
                : pad.refusalMessage(level, problem).copy().withStyle(ChatFormatting.GOLD);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            pad.redstone(serverLevel, level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            if (pad.isLaunching()) {
                pad.reachOrbit(serverLevel); // it's already on its way up
            }
            for (ItemStack stack : pad.unload()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad ? pad.comparatorOutput() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".launch_pad_help",
                LaunchPadBlockEntity.FIREWORKS_NEEDED).withStyle(ChatFormatting.GRAY));
    }
}
