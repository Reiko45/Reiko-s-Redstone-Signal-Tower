package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.client.ClientHooks;
import com.reiko.signaltower.network.MeterReadingPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * The Signal Meter (v0.40.0): a handheld that measures one frequency where you stand. While it's
 * in either hand the server sends a reading five times a second ({@link #read}): the strength a
 * receiver here would get, how many towers reach here, and how many blocks are left to the edge of
 * the farthest-reaching one. The client draws it beside the hotbar ({@code SignalMeterHud}) and
 * ticks like a Geiger counter, faster the stronger it is.
 * <p>
 * Right-click: tick sound on or off. Sneak + right-click the air: its frequency screen (favorites,
 * Sound, Directory). Sneak + right-click a radio block: measure the frequency it sends on (a
 * converter's output) or listens on. Sneak + scroll and favorites like the tuner. On someone else's
 * encrypted frequency it measures nothing, like a receiver of yours there would.
 */
public class SignalMeterItem extends Item {
    /** Ticks between readings. */
    public static final int READ_INTERVAL = 4;
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public SignalMeterItem(Properties properties) {
        super(properties);
    }

    /**
     * What a meter held by {@code player} reads on {@code frequency}: strength, towers in reach,
     * blocks to the edge (-1: out of every tower's reach), or {@code deaf} with nothing else if it's
     * someone else's encrypted frequency. {@code hills} and {@code jammed} (v0.46.0) as in
     * {@link SignalNetwork.Reception}.
     */
    public record Reading(int frequency, int strength, int towers, int margin, boolean deaf, int hills, int jammed, int faded) {
        public Reading(int frequency, int strength, int towers, int margin, boolean deaf) {
            this(frequency, strength, towers, margin, deaf, 0, 0, 0);
        }

        public Reading(int frequency, int strength, int towers, int margin, boolean deaf, int hills, int jammed) {
            this(frequency, strength, towers, margin, deaf, hills, jammed, 0);
        }
    }

    public static Reading read(ServerLevel level, Player player, int frequency) {
        UUID who = player.getUUID();
        if (!FrequencyClaims.mayHear(level, frequency, who)) {
            return new Reading(frequency, 0, 0, -1, true);
        }
        BlockPos pos = player.blockPosition();
        SignalNetwork network = SignalNetwork.get(level);
        SignalNetwork.Reception reception = network.listen(level, frequency, pos, who);
        return new Reading(frequency, reception.power(), reception.transmittersInReach(), network.edgeMargin(level, frequency, pos), false,
                reception.hills(), reception.jammed(), reception.faded());
    }

    public static boolean isSilent(ItemStack stack) {
        return PagerItem.isSilent(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)
                || level.getGameTime() % READ_INTERVAL != 0) {
            return;
        }
        boolean main = player.getMainHandItem() == stack;
        boolean off = player.getOffhandItem() == stack;
        if (!main && !(off && !(player.getMainHandItem().getItem() instanceof SignalMeterItem))) {
            return; // only the one in hand (the main hand's if both hold one)
        }
        PacketDistributor.sendToPlayer(player, MeterReadingPayload.of(read(serverLevel, player, FrequencyTunerItem.getFrequency(stack))));
    }

    /** Sneak + right-click a radio block: measure the frequency it sends on (a converter's output), or its own. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            if (!level.isClientSide) {
                int frequency = radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter()
                        ? relay.getOutputFrequency() : radio.getFrequency();
                FrequencyTunerItem.setFrequency(context.getItemInHand(), frequency);
                player.displayClientMessage(Component.translatable(KEY + "frequency_copied_meter", FrequencyNames.label(level, frequency)), true);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                ClientHooks.openMeterScreen(hand, FrequencyTunerItem.getFrequency(stack), stack.getHoverName(),
                        RadioFavorites.get(stack), !isSilent(stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) {
            boolean silent = !isSilent(stack);
            PagerItem.setSilent(stack, silent);
            player.displayClientMessage(Component.translatable(KEY + (silent ? "meter_sound_off" : "meter_sound_on")), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        int frequency = FrequencyTunerItem.getFrequency(stack);
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
        FrequencyNotes.addTooltipLine(frequency, tooltip);
        RadioFavorites.addFavoritesLine(stack, tooltip);
        tooltip.add(Component.translatable(prefix + (isSilent(stack) ? "meter_silent" : "meter_ticking")).withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable(prefix + "meter_help").withStyle(ChatFormatting.GRAY));
    }
}
