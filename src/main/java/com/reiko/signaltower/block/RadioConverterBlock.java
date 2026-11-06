package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.network.OpenConverterScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

import java.util.List;

/**
 * A repeater that changes the frequency: it listens on one frequency where it stands and sends
 * what it hears out on another, at the same strength, with its own height-based reach. One switch
 * can then drive receivers on a second frequency, or two networks can be joined.
 * <p>
 * The Frequency Tuner sets (and copies) the frequency it <b>listens on</b>, like any radio block.
 * Right-clicking it with an empty hand opens a screen for both frequencies, with what it's
 * converting right now.
 */
public class RadioConverterBlock extends RadioRepeaterBlock {
    public static final MapCodec<RadioConverterBlock> CODEC = simpleCodec(RadioConverterBlock::new);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 0.95F), 1.0F);

    public RadioConverterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected DustParticleOptions sparks() {
        return TEAL;
    }

    /** Opens the converter's screen (on the client, sent from the server so it has the current values). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity converter) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenConverterScreenPayload(pos, converter.getFrequency(),
                    converter.getOutputFrequency(), SignalNetwork.get(serverLevel).repeaterPower(serverLevel, pos),
                    RadioHelper.reachAt(serverLevel, pos), SignalNetwork.get(serverLevel).isSilenced(serverLevel, pos)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        int listen = 0;
        int send = 0;
        if (level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity converter) {
            listen = converter.getFrequency();
            send = converter.getOutputFrequency();
        }
        int power = SignalNetwork.get(level).repeaterPower(level, pos);
        String key = "message." + SignalTower.MOD_ID + ".";
        Component converting = power > 0
                ? Component.translatable(key + "converter_converting", power)
                : Component.translatable(key + "converter_quiet");
        return Component.translatable(key + "converter_status", FrequencyNames.label(level, listen), FrequencyNames.label(level, send),
                converting, RadioHelper.reachAt(level, pos));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer listen = stack.get(ModRegistry.FREQUENCY.get());
        Integer send = stack.get(ModRegistry.OUTPUT_FREQUENCY.get());
        if (listen != null) {
            tooltip.add(Component.translatable(prefix + "converter_listens", FrequencyNames.label(null, listen)).withStyle(ChatFormatting.AQUA));
        }
        if (send != null) {
            tooltip.add(Component.translatable(prefix + "converter_sends", FrequencyNames.label(null, send)).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(prefix + "converter_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
