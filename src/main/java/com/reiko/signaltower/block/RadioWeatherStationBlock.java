package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.blockentity.WeatherStationBlockEntity;
import com.reiko.signaltower.network.OpenStationScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.StationReading;
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
 * The Radio Weather Station (v0.53.0): a full-size transmitter with a weather vane and a rain
 * gauge. It broadcasts a reading of the conditions where it stands (see {@link StationReading})
 * instead of the redstone it gets; redstone into it pauses it. An empty-hand right-click opens its
 * screen; the tuner sets its frequency. The rest is in {@link WeatherStationBlockEntity}.
 */
public class RadioWeatherStationBlock extends RadioTransmitterBlock {
    public static final MapCodec<RadioWeatherStationBlock> CODEC = simpleCodec(RadioWeatherStationBlock::new);

    public RadioWeatherStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return com.reiko.signaltower.radio.AntennaTier.aimed(defaultBlockState().setValue(POWERED, false), context.getRotation()); // its first tick decides
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (com.reiko.signaltower.item.AntennaItem.takeOffBySneaking(state, level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof WeatherStationBlockEntity station) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenStationScreenPayload.of(serverLevel, station));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof WeatherStationBlockEntity station)) {
            return Component.translatable(getDescriptionId());
        }
        StationReading.Value reading = station.reading(level);
        return Component.translatable("message." + SignalTower.MOD_ID + ".station_status", FrequencyNames.label(level, station.getFrequency()),
                station.getMode().label(), reading.label(), station.output(level), RadioTransmitterBlockEntity.reachNow(level, pos, state));
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
        String mode = stack.get(ModRegistry.STATION_MODE.get());
        if (mode != null) {
            StationReading reading = StationReading.named(mode);
            tooltip.add(Component.translatable(prefix + "station_reading", reading.label(), reading.scale()).withStyle(ChatFormatting.YELLOW));
            if (Boolean.TRUE.equals(stack.get(ModRegistry.STATION_ANNOUNCE.get()))) {
                tooltip.add(Component.translatable(prefix + "station_announces").withStyle(ChatFormatting.YELLOW));
            }
        }
        com.reiko.signaltower.radio.AntennaTier.addTooltipLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.WEATHER_STATION_BE.get(), WeatherStationBlockEntity::stationTick);
    }
}
