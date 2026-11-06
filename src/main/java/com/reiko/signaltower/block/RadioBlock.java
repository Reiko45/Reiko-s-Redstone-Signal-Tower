package com.reiko.signaltower.block;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

/**
 * Shared behaviour for the Radio Transmitter and Radio Receiver: both store a frequency in their
 * block entity and show a status readout when clicked with an empty hand. Tuning is done by the
 * Frequency Tuner item, see {@link com.reiko.signaltower.item.FrequencyTunerItem}.
 */
public abstract class RadioBlock extends BaseEntityBlock {
    protected RadioBlock(Properties properties) {
        super(properties);
    }

    /** One-line status shown above the hotbar. */
    protected abstract Component describe(ServerLevel level, BlockPos pos, BlockState state);

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // BaseEntityBlock defaults to INVISIBLE; we want our JSON model drawn.
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Empty hand: fall through to the status readout. Holding anything else (the tuner, a mast,
        // another block) skips the readout so that item gets used normally.
        return stack.isEmpty()
                ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            Component silenced = silencedNotice(serverLevel, pos);
            player.displayClientMessage(silenced != null ? silenced : describe(serverLevel, pos, state), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * If the transmitter, repeater or converter at {@code pos} is kept quiet because it sends on
     * someone else's private frequency, the red line saying so; else null.
     */
    public static @Nullable Component silencedNotice(ServerLevel level, BlockPos pos) {
        SignalNetwork network = SignalNetwork.get(level);
        OptionalInt sends = network.towerFrequencyAt(pos);
        if (sends.isEmpty() || !network.isSilenced(level, pos)) {
            return null;
        }
        return FrequencyClaims.refusal(level, sends.getAsInt(), null, "private_silenced");
    }

    /**
     * Whoever places the block becomes its tuner (see {@link RadioBlockEntity#getTunedBy()}). If
     * that means it won't broadcast, because it's tuned to someone else's private frequency, they're
     * told.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            radio.setTunedBy(player.getUUID());
            Component silenced = silencedNotice(serverLevel, pos);
            if (silenced != null) {
                player.displayClientMessage(silenced, true);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".frequency", FrequencyNames.label(null, frequency))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
