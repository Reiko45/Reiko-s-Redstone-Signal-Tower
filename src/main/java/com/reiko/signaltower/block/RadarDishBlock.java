package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadarDishBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.network.OpenRadarScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.Radar;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Radar Dish (v0.65.0): a tower top (on a mast column, reach by height, a battery when towers
 * need power) with a turntable whose dish spins once every {@link Radar#SWEEP_TICKS} while it works.
 * It sweeps its reach for players, mobs and vehicles (see {@link Radar}) and, with Send on, sends how
 * many it sees (at most 15) on its frequency like a transmitter; a comparator reads the same count.
 * An empty-hand right-click opens its settings (sneak: the status line). It takes no antenna (it is
 * one), and its redstone input does nothing. The spinning dish is drawn by {@code client.RadarDishRenderer}.
 */
public class RadarDishBlock extends RadioTransmitterBlock {
    public static final MapCodec<RadarDishBlock> CODEC = simpleCodec(RadarDishBlock::new);
    /** True while it sweeps: tuned, and not out of power, knocked out by lightning or blown down. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

    public RadarDishBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, ACTIVE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState(); // its first sweep decides
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            return super.useWithoutItem(state, level, pos, player, hitResult); // the status line
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RadarDishBlockEntity dish) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenRadarScreenPayload.of(serverLevel, dish));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RadarDishBlockEntity dish)) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("message." + SignalTower.MOD_ID + ".radar_status", FrequencyNames.label(level, dish.getFrequency()),
                RadarDishBlockEntity.seenText(dish.counts()), dish.output(),
                RadioTransmitterBlockEntity.reachNow(level, pos, state));
    }

    // ---- Comparator: how many things it sees ----

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RadarDishBlockEntity dish ? dish.comparatorOutput() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeRadar(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
        Integer settings = stack.get(ModRegistry.RADAR_SETTINGS.get());
        if (settings != null) {
            tooltip.add(Component.translatable(prefix + "radar_tracks", tracksText(settings)).withStyle(ChatFormatting.YELLOW));
            if ((settings & Radar.SEND) == 0) {
                tooltip.add(Component.translatable(prefix + "radar_send_off").withStyle(ChatFormatting.YELLOW));
            }
        }
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Players, hostile mobs (strangers only)": what a dish with {@code settings} tracks ("nothing" if none). */
    public static Component tracksText(int settings) {
        String key = "message." + SignalTower.MOD_ID + ".radar_kind_";
        List<Component> parts = new ArrayList<>();
        for (Radar.Kind kind : Radar.TRACKED) {
            if ((settings & kind.bit()) != 0) {
                parts.add(Component.translatable(key + kind.key() + "s"));
            }
        }
        Component list = parts.isEmpty() ? Component.translatable(key + "nothing") : joined(parts);
        return (settings & Radar.STRANGERS_ONLY) != 0 && (settings & Radar.TRACK_PLAYERS) != 0
                ? Component.translatable("message." + SignalTower.MOD_ID + ".radar_strangers", list) : list;
    }

    /** {@code parts} with ", " between. */
    public static Component joined(List<Component> parts) {
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(parts.get(i));
        }
        return result;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadarDishBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.RADAR_DISH_BE.get(), RadarDishBlockEntity::dishTick);
    }

    /** No sparks: the dish sits where a transmitter's antenna tip would be. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }
}
