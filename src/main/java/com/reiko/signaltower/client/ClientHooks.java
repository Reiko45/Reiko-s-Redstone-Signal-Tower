package com.reiko.signaltower.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * Client-only entry points called from common code. Keep the method signatures free of client
 * classes so that common code can reference this class safely on a dedicated server.
 */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openTunerScreen(InteractionHand hand, int frequency) {
        Minecraft.getInstance().setScreen(new TunerScreen(hand, frequency));
    }
}
