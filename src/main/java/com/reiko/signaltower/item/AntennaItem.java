package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.radio.AntennaTier;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.IntruderAlerts;
import com.reiko.signaltower.radio.RadioHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An antenna upgrade (v0.45.0): right-click a full-size transmitter (plain, timer or recorder),
 * repeater or converter with it to fit it, multiplying the tower's reach. A tower already wearing
 * another antenna gives that one back. Sneak + right-click the tower with an empty hand takes it
 * off again. On someone else's private frequency only its owner and members may change it.
 * A Dish Antenna (v0.48.0) is aimed the way the player looks; using one on a tower that already
 * has a dish turns it to face that way instead. An Ender Antenna (v0.50.0) only fits transmitters;
 * using another on one that has it sends into the next dimension instead.
 */
public class AntennaItem extends Item {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private final AntennaTier tier;

    public AntennaItem(AntennaTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public AntennaTier tier() {
        return tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!AntennaTier.fits(state) || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            fit(context.getPlayer(), context.getClickedPos(), tier, context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Fits {@code tier} on the tower top at {@code pos} for {@code player}, using up one of
     * {@code stack} (not in creative) and giving back the antenna it replaces. Tells them, and
     * returns the message, or null if there's no tower top there. Public for the GameTests.
     */
    public static @Nullable Component fit(Player player, BlockPos pos, AntennaTier tier, ItemStack stack) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        if (!AntennaTier.fits(state)) {
            return null;
        }
        AntennaTier old = state.getValue(AntennaTier.PROPERTY);
        Component name = Component.translatable(state.getBlock().getDescriptionId());
        if (tier == AntennaTier.ENDER) {
            return fitEnder(player, pos, state, old, name, stack);
        }
        int aim = AntennaTier.aimFor(player.getYRot());
        // v0.52.0: a Dish Rotator in the tower's mast decides where a dish points.
        int rotated = tier == AntennaTier.DISH ? com.reiko.signaltower.block.DishRotatorBlock.aimAt(level, pos) : -1;
        if (rotated >= 0) {
            if (old == AntennaTier.DISH) {
                return tell(player, Component.translatable(KEY + "dish_rotated", name, AntennaTier.aimLabel(rotated)).withStyle(ChatFormatting.GOLD));
            }
            aim = rotated;
        }
        if (old == tier && (tier != AntennaTier.DISH || AntennaTier.aimOf(state) == aim)) {
            return tell(player, tier == AntennaTier.DISH
                    ? Component.translatable(KEY + "dish_already", name, AntennaTier.aimLabel(aim)).withStyle(ChatFormatting.GRAY)
                    : Component.translatable(KEY + "antenna_already", name, tier.label()).withStyle(ChatFormatting.GRAY));
        }
        Component refused = refusal(player, pos, state);
        if (refused != null) {
            return tell(player, refused);
        }
        int before = reachShown(level, pos, state);
        BlockState fitted = state.setValue(AntennaTier.PROPERTY, tier);
        if (tier == AntennaTier.DISH && fitted.hasProperty(AntennaTier.AIM)) {
            fitted = fitted.setValue(AntennaTier.AIM, aim);
        }
        level.setBlock(pos, fitted, Block.UPDATE_ALL);
        refresh(level, pos);
        if (old == tier) { // a dish, turned to face the other way: nothing used up or given back
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 1.3F);
            return tell(player, Component.translatable(KEY + "dish_reaimed", name, AntennaTier.aimLabel(aim),
                    dishReachShown(level, pos, fitted)).withStyle(ChatFormatting.AQUA));
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            giveBack(player, old);
        }
        level.playSound(null, pos, sound(tier), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (tier == AntennaTier.DISH) {
            return tell(player, Component.translatable(KEY + "dish_fitted", name, AntennaTier.aimLabel(aim),
                    dishReachShown(level, pos, fitted), reachShown(level, pos, fitted)).withStyle(ChatFormatting.AQUA));
        }
        return tell(player, Component.translatable(KEY + "antenna_fitted", name, tier.label(), before,
                reachShown(level, pos, fitted)).withStyle(ChatFormatting.AQUA));
    }

    /**
     * Takes the antenna off the tower top at {@code pos} for {@code player} (given back, not in
     * creative). Returns the message, or null if there's no antenna there. Public for the GameTests.
     */
    public static @Nullable Component takeOff(Player player, BlockPos pos) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        AntennaTier old = AntennaTier.of(state);
        if (old == AntennaTier.NONE) {
            return null;
        }
        Component refused = refusal(player, pos, state);
        if (refused != null) {
            return tell(player, refused);
        }
        int before = reachShown(level, pos, state);
        BlockState bare = state.setValue(AntennaTier.PROPERTY, AntennaTier.NONE);
        level.setBlock(pos, bare, Block.UPDATE_ALL);
        refresh(level, pos);
        if (!player.getAbilities().instabuild) {
            giveBack(player, old);
        }
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
        return tell(player, Component.translatable(KEY + "antenna_taken_off", old.label(), before,
                reachShown(level, pos, bare)).withStyle(ChatFormatting.AQUA));
    }

    /**
     * For a tower top's empty-hand click: if the player is sneaking and it wears an antenna, takes
     * it off (on the server) and returns true, so the click does nothing else.
     */
    public static boolean takeOffBySneaking(BlockState state, Level level, BlockPos pos, Player player) {
        if (!player.isSecondaryUseActive() || AntennaTier.of(state) == AntennaTier.NONE) {
            return false;
        }
        if (!level.isClientSide) {
            takeOff(player, pos);
        }
        return true;
    }

    /** {@link #towerRefusal}. */
    private static @Nullable Component refusal(Player player, BlockPos pos, BlockState state) {
        return towerRefusal(player, pos, state);
    }

    /**
     * The red refusal (and the owner's alert) if the tower top at {@code pos} sends on someone else's
     * private frequency, else null. Also for raising it with a mast (v0.51.0).
     */
    public static @Nullable Component towerRefusal(Player player, BlockPos pos, BlockState state) {
        if (!(player.level().getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            return null;
        }
        int sends = radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter() ? relay.getOutputFrequency() : radio.getFrequency();
        Component refused = FrequencyClaims.refusal(player.level(), sends, player.getUUID(),
                radio instanceof com.reiko.signaltower.blockentity.RadioJammerBlockEntity ? "private_refused_jammer" : "private_refused_block");
        if (refused != null) {
            IntruderAlerts.report(player.getServer(), sends, player, IntruderAlerts.Kind.TUNE, state.getBlock());
        }
        return refused;
    }

    /** The reach shown in the messages: a tower's (a jammer's jamming reach), in the weather where it stands now. */
    public static int reachShown(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return RadioHelper.towerReach(level, pos, state);
        }
        return state.getBlock() instanceof com.reiko.signaltower.block.RadioTransmitterBlock
                ? RadioTransmitterBlockEntity.reachNow(serverLevel, pos, state)
                : RadioHelper.towerReachNow(serverLevel, pos, state);
    }

    /** The reach of a tower in {@code state} with a dish, in the way it's aimed, in the weather where it stands now (v0.48.0). */
    public static int dishReachShown(Level level, BlockPos pos, BlockState state) {
        int clear = state.getBlock() instanceof com.reiko.signaltower.block.RadioJammerBlock
                ? com.reiko.signaltower.blockentity.RadioJammerBlockEntity.jamReach(level, pos, state)
                : RadioHelper.towerReach(level, pos, state);
        int far = AntennaTier.dishReach(clear);
        return level instanceof net.minecraft.server.level.ServerLevel serverLevel
                ? com.reiko.signaltower.radio.SignalNetwork.get(serverLevel).reachNow(serverLevel, pos, far) : far;
    }

    /** v0.50.0: fits an Ender Antenna (transmitters only), or turns one that's there to the next dimension. */
    private static Component fitEnder(Player player, BlockPos pos, BlockState state, AntennaTier old, Component name, ItemStack stack) {
        Level level = player.level();
        if (!AntennaTier.sendsAcross(state)) {
            return tell(player, Component.translatable(KEY + "ender_transmitters_only").withStyle(ChatFormatting.RED));
        }
        Component refused = refusal(player, pos, state);
        if (refused != null) {
            return tell(player, refused);
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity transmitter)) {
            return null;
        }
        if (old == AntennaTier.ENDER) {
            transmitter.setLinkTarget(com.reiko.signaltower.radio.CrossDimension.next(serverLevel, transmitter.linkTarget(serverLevel)));
            level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.8F, 1.2F);
            return tell(player, enderMessage("ender_relinked", serverLevel, pos, name, transmitter));
        }
        level.setBlock(pos, state.setValue(AntennaTier.PROPERTY, AntennaTier.ENDER), Block.UPDATE_ALL);
        transmitter.setLinkTarget(null); // the default: the Nether from the Overworld, else the Overworld
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            giveBack(player, old);
        }
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return tell(player, enderMessage("ender_fitted", serverLevel, pos, name, transmitter));
    }

    private static Component enderMessage(String key, net.minecraft.server.level.ServerLevel level, BlockPos pos, Component name,
                                          com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity transmitter) {
        var target = transmitter.linkTarget(level);
        if (target == null) {
            return Component.translatable(KEY + "ender_nowhere", name).withStyle(ChatFormatting.GRAY);
        }
        net.minecraft.server.level.ServerLevel there = level.getServer().getLevel(target);
        BlockPos arrival = com.reiko.signaltower.radio.CrossDimension.arrival(level, there, pos);
        return Component.translatable(KEY + key, name, com.reiko.signaltower.radio.CrossDimension.name(target),
                com.reiko.signaltower.radio.CrossDimension.coords(arrival)).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** Tells the network about the tower's new reach. */
    private static void refresh(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.refresh();
        } else if (level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity relay) {
            relay.refresh();
        }
    }

    private static void giveBack(Player player, AntennaTier tier) {
        Item item = tier.item();
        if (item != null) {
            ItemStack antenna = new ItemStack(item);
            if (!player.getInventory().add(antenna)) {
                player.drop(antenna, false);
            }
        }
    }

    private static SoundEvent sound(AntennaTier tier) {
        return switch (tier) {
            case GOLD -> SoundEvents.ARMOR_EQUIP_GOLD.value();
            case DIAMOND -> SoundEvents.ARMOR_EQUIP_DIAMOND.value();
            case NETHERITE -> SoundEvents.ARMOR_EQUIP_NETHERITE.value();
            case DISH -> SoundEvents.ARMOR_EQUIP_CHAIN.value();
            case ENDER -> SoundEvents.END_PORTAL_FRAME_FILL;
            default -> SoundEvents.ARMOR_EQUIP_IRON.value();
        };
    }

    private static Component tell(Player player, Component message) {
        player.displayClientMessage(message, true);
        return message;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + (tier == AntennaTier.DISH ? ".dish_item" : tier == AntennaTier.ENDER ? ".ender_item" : ".antenna_item"),
                tier.multiplierText()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".antenna_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
