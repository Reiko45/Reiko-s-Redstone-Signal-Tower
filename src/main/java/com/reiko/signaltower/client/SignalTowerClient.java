package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.WallRedstoneBlock;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = SignalTower.MOD_ID, dist = Dist.CLIENT)
public final class SignalTowerClient {
    public SignalTowerClient(IEventBus modEventBus, ModContainer container) {
        // Adds a "Config" button for this mod in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SignalTowerClient::registerBlockColors);
    }

    /** Wall Redstone glows brighter red the stronger its signal, exactly like normal dust. */
    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) ->
                        tintIndex == 0 ? RedStoneWireBlock.getColorForPower(state.getValue(WallRedstoneBlock.POWER)) : -1,
                ModRegistry.WALL_REDSTONE.get());
    }
}
