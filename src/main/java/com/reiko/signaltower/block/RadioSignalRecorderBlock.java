package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.blockentity.SignalRecorderBlockEntity;
import com.reiko.signaltower.network.OpenRecorderScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.RecorderTape;
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
 * The Radio Signal Recorder (v0.44.0): a full-size transmitter with a tape. It records up to 30
 * seconds of what a frequency does where it stands, and plays it back on its own frequency: once
 * on each redstone pulse into it (or its masts), or on a loop while powered. An empty-hand
 * right-click opens its screen; the tuner sets the frequency it plays on. The tape is in
 * {@link SignalRecorderBlockEntity}.
 */
public class RadioSignalRecorderBlock extends RadioTransmitterBlock {
    public static final MapCodec<RadioSignalRecorderBlock> CODEC = simpleCodec(RadioSignalRecorderBlock::new);

    public RadioSignalRecorderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, false); // it sends nothing until it plays
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (com.reiko.signaltower.item.AntennaItem.takeOffBySneaking(state, level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SignalRecorderBlockEntity recorder) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenRecorderScreenPayload.of(serverLevel, recorder));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof SignalRecorderBlockEntity recorder)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".recorder_status", FrequencyNames.label(level, recorder.getFrequency()),
                SignalRecorderBlockEntity.tapeText(recorder.getLength(), recorder.getChanges().length), recorder.getOutput(),
                RadioTransmitterBlockEntity.reachOf(level, pos, state));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "recorder_plays_on", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(frequency, tooltip);
        }
        RecorderTape tape = stack.get(ModRegistry.RECORDER_TAPE.get());
        if (tape != null) {
            if (tape.listen() > 0) {
                tooltip.add(Component.translatable(prefix + "recorder_records_from", FrequencyNames.label(null, tape.listen()))
                        .withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable(prefix + "recorder_tape", SignalRecorderBlockEntity.tapeText(tape.length(), tape.changes().size()),
                    SignalRecorderBlockEntity.modeText(tape.loop())).withStyle(ChatFormatting.YELLOW));
        }
        com.reiko.signaltower.radio.AntennaTier.addTooltipLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignalRecorderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.SIGNAL_RECORDER_BE.get(), SignalRecorderBlockEntity::recorderTick);
    }
}
