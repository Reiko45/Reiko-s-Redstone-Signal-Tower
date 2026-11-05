package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.WallRedstoneBlock;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.network.SelectRemoteButtonPayload;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = SignalTower.MOD_ID, dist = Dist.CLIENT)
public final class SignalTowerClient {
    public SignalTowerClient(IEventBus modEventBus, ModContainer container) {
        // Adds a "Config" button for this mod in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SignalTowerClient::registerBlockColors);
        NeoForge.EVENT_BUS.addListener(SignalTowerClient::onMouseScroll);
    }

    /** Sneak + scroll while holding a Multi-Button Remote picks its button instead of the hotbar slot. */
    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !player.isShiftKeyDown() || event.getScrollDeltaY() == 0) {
            return;
        }
        InteractionHand hand;
        if (player.getMainHandItem().getItem() instanceof MultiRemoteItem) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() instanceof MultiRemoteItem) {
            hand = InteractionHand.OFF_HAND;
        } else {
            return;
        }
        event.setCanceled(true);
        // Scrolling down moves to the next button, the same way it moves to the next hotbar slot.
        PacketDistributor.sendToServer(new SelectRemoteButtonPayload(hand == InteractionHand.OFF_HAND, event.getScrollDeltaY() < 0));
    }

    /** Wall Redstone glows brighter red the stronger its signal, exactly like normal dust. */
    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) ->
                        tintIndex == 0 ? RedStoneWireBlock.getColorForPower(state.getValue(WallRedstoneBlock.POWER)) : -1,
                ModRegistry.WALL_REDSTONE.get());
    }
}
