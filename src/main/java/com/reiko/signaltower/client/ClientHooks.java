package com.reiko.signaltower.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only entry points called from common code. Keep the method signatures free of client
 * classes so that common code can reference this class safely on a dedicated server.
 */
public final class ClientHooks {
    private ClientHooks() {}

    /** Opens the frequency screen. {@code toggleMode} is null for items without a mode (the tuner). */
    public static void openFrequencyScreen(InteractionHand hand, int frequency, Component title, @Nullable Boolean toggleMode) {
        Minecraft.getInstance().setScreen(new FrequencyScreen(hand, frequency, title, toggleMode));
    }
}
