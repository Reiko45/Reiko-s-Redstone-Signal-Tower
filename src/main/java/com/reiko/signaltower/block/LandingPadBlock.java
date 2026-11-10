package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The Landing Pad (v0.67.0): a thin pad with an "H" on it. A Radio Drone sitting on it charges
 * from a Radio Battery or a Solar Panel beside the pad (the drone does the work:
 * {@code RadioDroneEntity.chargeOnPad}).
 */
public class LandingPadBlock extends Block {
    public static final MapCodec<LandingPadBlock> CODEC = simpleCodec(LandingPadBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public LandingPadBlock(Properties properties) {
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".landing_pad_help").withStyle(ChatFormatting.GRAY));
    }
}
