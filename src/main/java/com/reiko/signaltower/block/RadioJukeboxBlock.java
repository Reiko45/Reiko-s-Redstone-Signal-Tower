package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioJukeboxBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Radio Jukebox (v0.60.0): a full-size transmitter with a record player. A music disc put in
 * (by hand, or from a hopper) plays on its frequency, and Radio Speakers that hear it play the song.
 * An empty hand takes the disc out (sneak to take an antenna off). See {@link RadioJukeboxBlockEntity}.
 */
public class RadioJukeboxBlock extends RadioTransmitterBlock {
    public static final MapCodec<RadioJukeboxBlock> CODEC = simpleCodec(RadioJukeboxBlock::new);

    public RadioJukeboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return com.reiko.signaltower.radio.AntennaTier.aimed(defaultBlockState().setValue(POWERED, false), context.getRotation()); // quiet until it plays
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (RadioJukeboxBlockEntity.isDisc(stack) && level.getBlockEntity(pos) instanceof RadioJukeboxBlockEntity jukebox && !jukebox.hasDisc()) {
            if (!level.isClientSide) {
                ItemStack one = stack.copyWithCount(1);
                if (jukebox.insert(one)) {
                    stack.consume(1, player);
                    player.awardStat(Stats.PLAY_RECORD);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
                    Holder<JukeboxSong> song = jukebox.song();
                    if (song != null) {
                        player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".jukebox_now_on",
                                song.value().description(), FrequencyNames.label(level, jukebox.getFrequency())).withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (com.reiko.signaltower.item.AntennaItem.takeOffBySneaking(state, level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof RadioJukeboxBlockEntity jukebox && jukebox.hasDisc() && !player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                ItemStack disc = jukebox.eject();
                if (!player.getInventory().add(disc)) {
                    ItemEntity dropped = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, disc);
                    dropped.setDefaultPickUpDelay();
                    level.addFreshEntity(dropped);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof RadioJukeboxBlockEntity jukebox
                && jukebox.hasDisc()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), jukebox.eject());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RadioJukeboxBlockEntity jukebox)) {
            return Component.translatable(getDescriptionId());
        }
        long now = level.getGameTime();
        Holder<JukeboxSong> song = jukebox.song();
        return Component.translatable("message." + SignalTower.MOD_ID + ".jukebox_status", FrequencyNames.label(level, jukebox.getFrequency()),
                RadioJukeboxBlockEntity.statusText(song == null ? null : song.value().description(), jukebox.isPlaying(now),
                        jukebox.elapsed(now), jukebox.playLength(), jukebox.isLooping()),
                RadioTransmitterBlockEntity.reachNow(level, pos, state));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "jukebox_plays_on", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(frequency, tooltip);
        }
        com.reiko.signaltower.radio.AntennaTier.addTooltipLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- Comparators: the song's own level while it plays, like a jukebox ----

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RadioJukeboxBlockEntity jukebox && jukebox.isPlaying(level.getGameTime())) {
            Holder<JukeboxSong> song = jukebox.song();
            return song == null ? 0 : song.value().comparatorOutput();
        }
        return 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioJukeboxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.RADIO_JUKEBOX_BE.get(), RadioJukeboxBlockEntity::jukeboxTick);
    }
}
