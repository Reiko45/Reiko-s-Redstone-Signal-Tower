package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.radio.DronePilots;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Drone Controller (v0.67.0). Right-click a Radio Drone with it to pair them; then right-click
 * (anywhere) to take control, and again to let go (so does putting it away). While you fly, your
 * own movement keys steer the drone ({@code client.DronePilot}) and you stand still.
 */
public class DroneControllerItem extends Item {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public DroneControllerItem(Properties properties) {
        super(properties);
    }

    public static @Nullable DroneLink linkOf(ItemStack stack) {
        return stack.get(ModRegistry.DRONE_LINK.get());
    }

    /** Pairs {@code stack} with {@code drone}. */
    public static void link(ItemStack stack, RadioDroneEntity drone) {
        stack.set(ModRegistry.DRONE_LINK.get(), new DroneLink(drone.getUUID(), drone.droneName(), drone.getFrequency()));
    }

    /** True if {@code player} holds a controller paired with {@code drone} in either hand. */
    public static boolean holdsLinked(Player player, UUID drone) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            DroneLink link = stack.getItem() instanceof DroneControllerItem ? linkOf(stack) : null;
            if (link != null && link.drone().equals(drone)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        RadioDroneEntity flying = DronePilots.droneOf(player);
        if (flying != null) {
            DronePilots.stop(player, Component.translatable(KEY + "drone_let_go", flying.label()).withStyle(ChatFormatting.AQUA));
            return InteractionResultHolder.success(stack);
        }
        DroneLink link = linkOf(stack);
        if (link == null) {
            player.displayClientMessage(Component.translatable(KEY + "drone_unpaired").withStyle(ChatFormatting.GOLD), true);
            return InteractionResultHolder.fail(stack);
        }
        Component result = DronePilots.start(player, link.drone());
        player.displayClientMessage(result, true);
        if (DronePilots.droneOf(player) != null) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WALKIE_SQUELCH.get(), SoundSource.PLAYERS, 0.4F, 1.4F);
        }
        return InteractionResultHolder.success(stack);
    }

    /** On a block it does what it does in the air (it doesn't use blocks). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        DroneLink link = linkOf(stack);
        if (link == null) {
            tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".drone_unpaired").withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".drone_paired",
                    link.name().isEmpty() ? Component.translatable(KEY + "drone_unnamed") : Component.literal(link.name()),
                    FrequencyNames.label(null, link.frequency())).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".drone_controls").withStyle(ChatFormatting.DARK_GRAY));
    }
}
