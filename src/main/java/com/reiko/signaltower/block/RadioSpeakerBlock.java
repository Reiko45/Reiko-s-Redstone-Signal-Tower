package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.OpenSpeakerScreenPayload;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Radio Speaker (v0.43.0): a receiver that also plays a sound when its frequency turns on
 * (bell, chime, ding, doorbell, pling, bass or beep, at a note-block pitch, quiet, normal or loud),
 * once or every second while the frequency stays on. It outputs like a plain receiver. An
 * empty-hand right-click opens its screen; the tuner sets its frequency. The sound is played by
 * {@link RadioReceiverBlockEntity}.
 */
public class RadioSpeakerBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioSpeakerBlock> CODEC = simpleCodec(RadioSpeakerBlock::new);

    public RadioSpeakerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenSpeakerScreenPayload.of(serverLevel, rx));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".speaker_status", FrequencyNames.label(level, rx.getFrequency()),
                RadioReceiverBlockEntity.speakerText(rx.packedSpeaker()), state.getValue(POWER));
    }

    /** v0.60.0: on a client, a ticker that hands the speaker's music to the sound player. */
    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.@org.jetbrains.annotations.Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModRegistry.RADIO_RECEIVER_BE.get(), RadioReceiverBlockEntity::clientTick)
                : super.getTicker(level, state, type);
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
        Integer speaker = stack.get(ModRegistry.SPEAKER.get());
        if (speaker != null) {
            tooltip.add(Component.translatable(prefix + "speaker_plays", RadioReceiverBlockEntity.speakerText(speaker)).withStyle(ChatFormatting.YELLOW));
        }
    }
}
