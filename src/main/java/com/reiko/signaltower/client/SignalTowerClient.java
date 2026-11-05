package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = SignalTower.MOD_ID, dist = Dist.CLIENT)
public final class SignalTowerClient {
    public SignalTowerClient(IEventBus modEventBus, ModContainer container) {
        // Adds a "Config" button for this mod in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
