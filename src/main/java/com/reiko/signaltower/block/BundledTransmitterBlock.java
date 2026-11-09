package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.BundledTransmitterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bundled Radio Transmitter (v0.54.0): a full-size transmitter that sends a whole Bundled Cable.
 * Tuned to F, it sends white on F, orange on F + 1, ... black on F + 15 (dye order), each at the
 * strength the cable carries. A cable beside it, or beside any mast of its tower, feeds it;
 * redstone doesn't. Everything else is a transmitter's: reach by height, antennas, power, the
 * tuner. See {@link BundledTransmitterBlockEntity}.
 */
public class BundledTransmitterBlock extends RadioTransmitterBlock {
    public static final MapCodec<BundledTransmitterBlock> CODEC = simpleCodec(BundledTransmitterBlock::new);

    public BundledTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            com.reiko.signaltower.radio.BundledCables.update(level, pos);
        }
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof BundledTransmitterBlockEntity tx)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".bundled_tx_status", FrequencyNames.label(level, tx.getFrequency()),
                bundleRange(tx.getFrequency()), CableTapBlock.channelsText(tx.getChannels()), RadioTransmitterBlockEntity.reachNow(level, pos, state));
    }

    /** "5000–5015": the frequencies a bundle on {@code base} uses. */
    public static String bundleRange(int base) {
        return base + "–" + Math.min(com.reiko.signaltower.radio.Frequency.MAX, base + 15);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "bundle_frequencies", FrequencyNames.label(null, frequency), bundleRange(frequency))
                    .withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(frequency, tooltip);
        }
        com.reiko.signaltower.radio.AntennaTier.addTooltipLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BundledTransmitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.BUNDLED_TRANSMITTER_BE.get(), RadioTransmitterBlockEntity::serverTick);
    }
}
