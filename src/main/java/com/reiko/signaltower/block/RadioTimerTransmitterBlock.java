package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity;
import com.reiko.signaltower.network.OpenTimerScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Radio Timer Transmitter (v0.42.0): a full-size transmitter with a clock face. Instead of
 * broadcasting the redstone it receives, it broadcasts full strength on a schedule: a pulse every
 * few seconds or minutes, or on between two in-game hours. Redstone into it (or into its masts)
 * pauses it. An empty-hand right-click opens its screen; the tuner sets its frequency. The clock
 * is in {@link TimerTransmitterBlockEntity}.
 */
public class RadioTimerTransmitterBlock extends RadioTransmitterBlock {
    public static final MapCodec<RadioTimerTransmitterBlock> CODEC = simpleCodec(RadioTimerTransmitterBlock::new);

    public RadioTimerTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, false); // its first tick decides
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TimerTransmitterBlockEntity timer) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenTimerScreenPayload.of(serverLevel, timer));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof TimerTransmitterBlockEntity timer)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".timer_status", FrequencyNames.label(level, timer.getFrequency()),
                timer.scheduleText(), timer.output(level), RadioTransmitterBlockEntity.reachOf(level, pos, state));
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
        Integer mode = stack.get(ModRegistry.TIMER_MODE.get());
        if (mode != null) {
            Integer interval = stack.get(ModRegistry.TIMER_INTERVAL.get());
            Integer pulse = stack.get(ModRegistry.TIMER_PULSE.get());
            Integer hours = stack.get(ModRegistry.TIMER_HOURS.get());
            int packed = hours == null ? TimerTransmitterBlockEntity.DEFAULT_ON_HOUR * 24 + TimerTransmitterBlockEntity.DEFAULT_OFF_HOUR : hours;
            tooltip.add(Component.translatable(prefix + "timer_schedule", TimerTransmitterBlockEntity.scheduleText(mode == 1,
                    interval == null ? TimerTransmitterBlockEntity.DEFAULT_INTERVAL : interval,
                    pulse == null ? TimerTransmitterBlockEntity.DEFAULT_PULSE : pulse, packed / 24, packed % 24)).withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimerTransmitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.TIMER_TRANSMITTER_BE.get(), TimerTransmitterBlockEntity::timerTick);
    }
}
