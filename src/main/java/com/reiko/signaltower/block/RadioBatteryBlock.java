package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBatteryBlockEntity;
import com.reiko.signaltower.radio.PowerCost;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Radio Battery (v0.49.0): powers the towers next to it, or next to a mast in their column,
 * while they send (see {@link PowerCost}). Right-click it with Redstone Dust or Blocks of Redstone
 * to charge it (it takes as many as fit); hoppers, pipes and FE cables work too. An empty-hand
 * right-click says how full it is; a comparator reads it; it keeps its charge when broken.
 */
public class RadioBatteryBlock extends BaseEntityBlock {
    public static final MapCodec<RadioBatteryBlock> CODEC = simpleCodec(RadioBatteryBlock::new);
    /** How many of its four bars are lit. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public RadioBatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioBatteryBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof RadioBatteryBlockEntity battery) {
            battery.syncState(); // the item's charge is in by now
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (RadioBatteryBlockEntity.valueOf(stack) <= 0 || !(level.getBlockEntity(pos) instanceof RadioBatteryBlockEntity battery)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int before = battery.getCharge();
            int used = battery.insert(stack, false);
            if (used <= 0) {
                player.displayClientMessage(Component.translatable(KEY + "battery_full", PowerCost.number(battery.getCapacity()))
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(used);
                }
                player.displayClientMessage(Component.translatable(KEY + "battery_charged", PowerCost.number(battery.getCharge() - before),
                        PowerCost.number(battery.getCharge()), PowerCost.number(battery.getCapacity())).withStyle(ChatFormatting.RED), true);
                level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.6F, 1.4F);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RadioBatteryBlockEntity battery) {
            player.displayClientMessage(status(battery), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** "Radio Battery: 45,000 / 100,000 charge (45%)" */
    public static Component status(RadioBatteryBlockEntity battery) {
        return Component.translatable(KEY + "battery_status", PowerCost.number(battery.getCharge()), PowerCost.number(battery.getCapacity()),
                percent(battery.getCharge(), battery.getCapacity())).withStyle(ChatFormatting.RED);
    }

    public static int percent(long charge, long capacity) {
        return capacity <= 0 ? 0 : (int) Math.min(100, Math.round(charge * 100.0 / capacity));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RadioBatteryBlockEntity battery ? battery.comparatorLevel() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        int charge = stack.getOrDefault(ModRegistry.BATTERY_CHARGE.get(), 0);
        tooltip.add(Component.translatable(prefix + "battery_item", PowerCost.number(charge), PowerCost.number(PowerCost.capacity()))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable(prefix + "battery_help").withStyle(ChatFormatting.GRAY));
    }
}
