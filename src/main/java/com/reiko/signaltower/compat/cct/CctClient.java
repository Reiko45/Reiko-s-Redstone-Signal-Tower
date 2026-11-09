package com.reiko.signaltower.compat.cct;

import dan200.computercraft.api.client.turtle.RegisterTurtleModellersEvent;
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
import net.neoforged.bus.api.IEventBus;

/** v0.59.0: the client side of the CC: Tweaked support: a Radio Turtle shows its pager flat on its side. */
final class CctClient {
    private CctClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterTurtleModellersEvent event) -> event.register(RadioUpgrades.TURTLE.get(), TurtleUpgradeModeller.flatItem()));
    }
}
