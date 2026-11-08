package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.OpenLogicScreenPayload;
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
 * A receiver on two frequencies (v0.40.0): it outputs when both are on (AND, the weaker strength),
 * either is on (OR, the stronger) or exactly one is on (XOR, that one's strength). An empty-hand
 * right-click opens its screen, with a box for each frequency and the mode, like the converter's;
 * the Frequency Tuner sets (and copies) the first one. It counts as a receiver on both. The logic
 * is in {@link RadioReceiverBlockEntity}.
 */
public class RadioLogicReceiverBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioLogicReceiverBlock> CODEC = simpleCodec(RadioLogicReceiverBlock::new);

    public RadioLogicReceiverBlock(Properties properties) {
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
            PacketDistributor.sendToPlayer(serverPlayer, OpenLogicScreenPayload.of(serverLevel, rx));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        String key = "message." + SignalTower.MOD_ID + ".";
        if (!(level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return Component.translatable("block." + SignalTower.MOD_ID + ".radio_logic_receiver");
        }
        return Component.translatable(key + "logic_status", FrequencyNames.label(level, rx.getFrequency()),
                Component.translatable(key + "logic_" + rx.getLogicMode().key()), FrequencyNames.label(level, rx.getSecondFrequency()),
                rx.heardFirst(), rx.heardSecond(), state.getValue(POWER));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer first = stack.get(ModRegistry.FREQUENCY.get());
        Integer second = stack.get(ModRegistry.SECOND_FREQUENCY.get());
        Integer mode = stack.get(ModRegistry.LOGIC_MODE.get());
        if (first != null && second != null) {
            String modeKey = RadioReceiverBlockEntity.LogicMode.of(mode == null ? 0 : mode).key();
            tooltip.add(Component.translatable(prefix + "logic_inputs", FrequencyNames.label(null, first),
                    Component.translatable("message." + SignalTower.MOD_ID + ".logic_" + modeKey),
                    FrequencyNames.label(null, second)).withStyle(ChatFormatting.AQUA));
            FrequencyNotes.addTooltipLine(first, tooltip);
            FrequencyNotes.addTooltipLine(second, tooltip);
        } else if (first != null) {
            tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, first)).withStyle(ChatFormatting.AQUA));
        }
    }
}
