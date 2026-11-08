package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The Radio Lamp (v0.45.0): a lamp with a receiver built in. It gives off as much light as the
 * strength it hears on its frequency (0-15; light level = strength), glowing in one of the 16 dye
 * colors (right-click it with a dye to change it). It's a receiver for "Heard by", the locator
 * and the audit, but it outputs no redstone: it's only a light.
 */
public class RadioLampBlock extends RadioReceiverBlock {
    public static final MapCodec<RadioLampBlock> CODEC = simpleCodec(RadioLampBlock::new);
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public RadioLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(COLOR, DyeColor.WHITE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, COLOR);
    }

    /**
     * The tint for a lamp's glass (0xAARRGGBB): its dye color, dark while off and brighter the
     * stronger the signal (0-15). The lit glass is drawn full bright, so the tint is its brightness.
     */
    public static int tint(DyeColor color, int power) {
        int rgb = color.getTextureDiffuseColor();
        double f = power <= 0 ? 0.32 : 0.45 + 0.55 * Math.min(15, power) / 15.0;
        int r = (int) Math.round((rgb >> 16 & 255) * f);
        int g = (int) Math.round((rgb >> 8 & 255) * f);
        int b = (int) Math.round((rgb & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Light level for a lamp in {@code state}: the strength it hears. */
    public static int lightOf(BlockState state) {
        return state.getValue(POWER);
    }

    // ---- Only a light: no redstone ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    // ---- Dyeing ----

    /** A dye recolors it (one dye, not in creative); anything else works as on any radio block. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof DyeItem dye) {
            DyeColor color = dye.getDyeColor();
            if (state.getValue(COLOR) == color) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /** "red", "light blue": a dye color's name. */
    public static Component colorLabel(DyeColor color) {
        return Component.translatable("color.minecraft." + color.getName());
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int frequency = level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity rx ? rx.getFrequency() : 0;
        return Component.translatable("message." + SignalTower.MOD_ID + ".lamp_status", FrequencyNames.label(level, frequency),
                colorLabel(state.getValue(COLOR)), state.getValue(POWER));
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
        tooltip.add(Component.translatable(prefix + "lamp_color", colorLabel(colorOf(stack))).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(prefix + "tune_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The color a lamp item places with (white unless its block state component says otherwise). */
    public static DyeColor colorOf(ItemStack stack) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        DyeColor color = properties == null ? null : properties.get(COLOR);
        return color == null ? DyeColor.WHITE : color;
    }

    /** No redstone dust: a lamp doesn't sparkle like a receiver. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }
}
