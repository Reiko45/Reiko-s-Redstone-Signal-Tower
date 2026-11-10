package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The Radio Drone as an item (v0.67.0): right-click a block to set the drone down on it (facing
 * away from you). It keeps the drone's frequency, charge, settings, name and identity, so a
 * controller paired with it stays paired after it's picked up and put down again.
 */
public class RadioDroneItem extends Item {
    public RadioDroneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        Vec3 at = context.getClickLocation();
        RadioDroneEntity drone = ModRegistry.RADIO_DRONE_ENTITY.get().create(level);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        double y = context.getClickedFace() == net.minecraft.core.Direction.UP ? at.y
                : context.getClickedFace() == net.minecraft.core.Direction.DOWN ? at.y - drone.getBbHeight() - 0.01 : clicked.getY() + 0.01;
        Vec3 spot = context.getClickedFace().getAxis().isHorizontal()
                ? Vec3.atBottomCenterOf(clicked.relative(context.getClickedFace())) : new Vec3(at.x, y, at.z);
        Player player = context.getPlayer();
        drone.moveTo(spot.x, spot.y, spot.z, player == null ? 0 : player.getYRot(), 15.0F);
        if (!level.noCollision(drone)) {
            return InteractionResult.FAIL;
        }
        UUID id = stack.get(ModRegistry.DRONE_ID.get());
        if (id != null && level.getEntity(id) == null) {
            drone.setUUID(id); // the same drone again: its controllers stay paired
        }
        drone.fromItem(stack);
        drone.setTunedBy(player == null ? null : player.getUUID());
        level.addFreshEntity(drone);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.5F, 1.4F);
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        Integer frequency = stack.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
        }
        Integer charge = stack.get(ModRegistry.DRONE_CHARGE.get());
        int percent = (int) Math.round((charge == null ? RadioDroneEntity.CAPACITY : charge) * 100.0 / RadioDroneEntity.CAPACITY);
        tooltip.add(Component.translatable(prefix + "drone_battery", percent).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(prefix + "drone_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
