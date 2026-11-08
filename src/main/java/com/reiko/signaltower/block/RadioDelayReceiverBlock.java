package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.OpenDelayScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.SignalNetwork;
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
 * A receiver with a timer (v0.41.0): it outputs full strength (15) while what it heard its delay ago
 * was on (like a long redstone repeater), and stays on for its hold after that turns off, so a short
 * remote pulse can keep a door open for a while. An empty-hand right-click opens its screen to set
 * the delay and hold; the tuner sets its frequency. The logic is in {@link RadioReceiverBlockEntity}.
 */
public class RadioDelayReceiverBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioDelayReceiverBlock> CODEC = simpleCodec(RadioDelayReceiverBlock::new);

    public RadioDelayReceiverBlock(Properties properties) {
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
            PacketDistributor.sendToPlayer(serverPlayer, OpenDelayScreenPayload.of(serverLevel, rx));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        String key = "message." + SignalTower.MOD_ID + ".";
        if (!(level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return Component.translatable("block." + SignalTower.MOD_ID + ".radio_delay_receiver");
        }
        SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, rx.getFrequency(), pos, rx.getTunedBy());
        return Component.translatable(key + "delay_status", FrequencyNames.label(level, rx.getFrequency()),
                RadioReceiverBlockEntity.timeLabel(rx.getDelayTicks()), RadioReceiverBlockEntity.timeLabel(rx.getHoldTicks()),
                reception.power(), state.getValue(POWER) > 0
                        ? Component.translatable(key + "toggle_on").withStyle(ChatFormatting.GREEN)
                        : Component.translatable(key + "toggle_off").withStyle(ChatFormatting.GRAY));
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
        Integer delay = stack.get(ModRegistry.DELAY_TICKS.get());
        Integer hold = stack.get(ModRegistry.HOLD_TICKS.get());
        if (delay != null || hold != null) {
            tooltip.add(Component.translatable(prefix + "delay_timing", RadioReceiverBlockEntity.timeLabel(delay == null ? 20 : delay),
                    RadioReceiverBlockEntity.timeLabel(hold == null ? 0 : hold)).withStyle(ChatFormatting.YELLOW));
        }
    }
}
