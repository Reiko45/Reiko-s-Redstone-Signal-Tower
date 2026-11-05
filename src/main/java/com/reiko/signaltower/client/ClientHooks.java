package com.reiko.signaltower.client;

import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.network.RemoteScanResultPayload;
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

    /**
     * Opens the frequency screen. {@code toggleMode} and {@code pulseTicks} are null for items without
     * a mode (the tuner).
     */
    public static void openFrequencyScreen(InteractionHand hand, int frequency, Component title,
                                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks) {
        Minecraft.getInstance().setScreen(new FrequencyScreen(hand, frequency, title, toggleMode, pulseTicks));
    }

    /** A locator answer arrived: hand it to the open remote screen, if there is one. */
    public static void onRemoteScan(RemoteScanResultPayload result) {
        if (Minecraft.getInstance().screen instanceof RemoteScanListener listener) {
            listener.onRemoteScan(result);
        }
    }

    public static void openMultiRemoteScreen(InteractionHand hand, RemoteButtons buttons, Component title) {
        Minecraft.getInstance().setScreen(new MultiRemoteScreen(hand, buttons, title));
    }
}
