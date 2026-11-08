package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.OpenCounterScreenPayload;
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
 * The Radio Counter Receiver (v0.42.0): counts how many times its frequency turns on and outputs
 * full strength (15) once the count reaches its target, either for a 1-second pulse (then it
 * counts again) or until its reset frequency turns on. It can forget the count after a pause, so
 * three quick presses work and three slow ones don't. An empty-hand right-click opens its screen;
 * the tuner sets the frequency it counts. The logic is in {@link RadioReceiverBlockEntity}.
 */
public class RadioCounterReceiverBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioCounterReceiverBlock> CODEC = simpleCodec(RadioCounterReceiverBlock::new);

    public RadioCounterReceiverBlock(Properties properties) {
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
            PacketDistributor.sendToPlayer(serverPlayer, OpenCounterScreenPayload.of(serverLevel, rx));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx)) {
            return Component.translatable(getDescriptionId());
        }
        String key = "message." + SignalTower.MOD_ID + ".";
        return Component.translatable(key + "counter_status", FrequencyNames.label(level, rx.getFrequency()),
                rx.getCount(), rx.getCounterTarget(), state.getValue(POWER) > 0
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
        Integer target = stack.get(ModRegistry.COUNTER_TARGET.get());
        if (target != null) {
            Integer latch = stack.get(ModRegistry.COUNTER_LATCH.get());
            Integer forget = stack.get(ModRegistry.COUNTER_FORGET.get());
            tooltip.add(Component.translatable(prefix + "counter_settings",
                    RadioReceiverBlockEntity.counterText(target, latch != null && latch == 1, forget == null ? 0 : forget))
                    .withStyle(ChatFormatting.YELLOW));
        }
        Integer reset = stack.get(ModRegistry.RESET_FREQUENCY.get());
        if (reset != null && reset > 0) {
            tooltip.add(Component.translatable(prefix + "counter_reset", FrequencyNames.label(null, reset)).withStyle(ChatFormatting.AQUA));
        }
    }
}
