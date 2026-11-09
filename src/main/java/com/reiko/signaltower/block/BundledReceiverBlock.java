package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.BundledReceiverBlockEntity;
import com.reiko.signaltower.radio.BundledCables;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bundled Radio Receiver (v0.54.0): hears the 16 frequencies from its own on (F is white, F + 15
 * black) and puts what it hears onto the Bundled Cables touching it, each on its color. It gives
 * out no redstone itself. {@link #LIT} while it hears any.
 */
public class BundledReceiverBlock extends RadioBlock {
    public static final MapCodec<BundledReceiverBlock> CODEC = simpleCodec(BundledReceiverBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BundledReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !newState.is(state.getBlock())) {
            BundledCables.updateAround(level, pos); // what it put on the cables goes
        }
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof BundledReceiverBlockEntity rx)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".bundled_rx_status", FrequencyNames.label(level, rx.getFrequency()),
                BundledTransmitterBlock.bundleRange(rx.getFrequency()), CableTapBlock.channelsText(rx.getHeard()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "bundle_frequencies", FrequencyNames.label(null, frequency),
                    BundledTransmitterBlock.bundleRange(frequency)).withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(frequency, tooltip);
        }
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BundledReceiverBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.BUNDLED_RECEIVER_BE.get(), BundledReceiverBlockEntity::serverTick);
    }
}
