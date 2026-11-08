package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioJammerBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import org.joml.Vector3f;

import java.util.List;

/**
 * The Radio Jammer (v0.46.0): a tower top that, while redstone reaches it (or a mast under it),
 * drowns out its frequency inside its reach. Everything there that listens on that frequency
 * (receivers, repeaters and converters, pagers, meters, scanners, status boards) hears that much
 * less: a full-strength (15) jammer silences it completely. Its reach is a share of a transmitter's
 * at the same height (config), so masts and antennas make it reach farther; weather and hills
 * weaken it like a signal. On a private frequency only the owner and members can tune or place
 * one that works: anyone else's stays quiet, and the owner is told.
 */
public class RadioJammerBlock extends RadioTransmitterBlock {
    public static final MapCodec<RadioJammerBlock> CODEC = simpleCodec(RadioJammerBlock::new);
    private static final DustParticleOptions STATIC = new DustParticleOptions(new Vector3f(1.0F, 0.32F, 0.78F), 0.8F);

    public RadioJammerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeJammer(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int frequency = level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity tx ? tx.getFrequency() : 0;
        int power = RadioHelper.readTransmitterInput(level, pos);
        String key = "message." + SignalTower.MOD_ID + ".";
        Component doing = power > 0 ? Component.translatable(key + "jammer_jamming", power) : Component.translatable(key + "jammer_off");
        return Component.translatable(key + "jammer_status", FrequencyNames.label(level, frequency), doing,
                RadioTransmitterBlockEntity.reachNow(level, pos, state));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "jammer_jams", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(frequency, tooltip);
        }
        com.reiko.signaltower.radio.AntennaTier.addTooltipLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioJammerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.RADIO_JAMMER_BE.get(), RadioTransmitterBlockEntity::serverTick);
    }

    /** Pink static round the antenna while it jams. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
            double y = pos.getY() + 1.1 + random.nextDouble() * 0.9;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
            level.addParticle(STATIC, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
