package com.reiko.signaltower.client;

import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.item.TunerPresets;
import com.reiko.signaltower.network.DirectoryResultPayload;
import com.reiko.signaltower.network.OpenConverterScreenPayload;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import com.reiko.signaltower.network.ScannerResultPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
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
     * Opens the frequency screen (tuner, Remote Control, pager), with its row of favorites.
     * {@code toggleMode} and {@code pulseTicks} are null for items without a mode.
     */
    public static void openFrequencyScreen(InteractionHand hand, int frequency, Component title,
                                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks, TunerPresets favorites) {
        Minecraft.getInstance().setScreen(new FrequencyScreen(hand, frequency, title, toggleMode, pulseTicks, favorites));
    }

    /** Opens a Radio Pager's screen: frequency and favorites, plus its Sound and Chat log buttons. */
    public static void openPagerScreen(InteractionHand hand, int frequency, Component title, TunerPresets favorites,
                                       boolean sound, boolean chatLog) {
        Minecraft.getInstance().setScreen(new FrequencyScreen(hand, frequency, title, favorites, sound, chatLog));
    }

    /** A locator answer arrived: hand it to the open remote screen, if there is one. */
    public static void onRemoteScan(RemoteScanResultPayload result) {
        if (Minecraft.getInstance().screen instanceof RemoteScanListener listener) {
            listener.onRemoteScan(result);
        }
    }

    public static void openScannerScreen(InteractionHand hand, Component title) {
        Minecraft.getInstance().setScreen(new ScannerScreen(hand, title));
    }

    /** A scanner answer arrived: hand it to the open scanner screen, if there is one. */
    public static void onScannerResult(ScannerResultPayload result) {
        if (Minecraft.getInstance().screen instanceof ScannerScreen screen) {
            screen.onResult(result);
        }
    }

    /**
     * True if this client's player can hear {@code frequency}: anything but someone else's
     * encrypted frequency (as far as this client knows). For item tooltips.
     */
    public static boolean localPlayerMayHear(int frequency) {
        FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(frequency);
        var player = Minecraft.getInstance().player;
        return claim == null || !claim.encrypted() || player == null
                || claim.isMember(player.getUUID(), player.getGameProfile().getName());
    }

    /** A directory answer arrived: hand it to the open directory screen, if there is one. */
    public static void onDirectoryResult(DirectoryResultPayload result) {
        if (Minecraft.getInstance().screen instanceof DirectoryScreen screen) {
            screen.onResult(result);
        }
    }

    /** An audit answer arrived: hand it to the open audit screen, if there is one. */
    public static void onLogResult(com.reiko.signaltower.network.LogResultPayload result) {
        if (Minecraft.getInstance().screen instanceof LogScreen screen) {
            screen.onResult(result);
        }
    }

    public static void onAuditResult(com.reiko.signaltower.network.AuditResultPayload result) {
        if (Minecraft.getInstance().screen instanceof AuditScreen screen) {
            screen.onResult(result);
        }
    }

    public static void openMultiRemoteScreen(InteractionHand hand, RemoteButtons buttons, Component title) {
        Minecraft.getInstance().setScreen(new MultiRemoteScreen(hand, buttons, title));
    }

    /** Opens a Radio Converter's screen (sent by the server when the block is right-clicked). */
    public static void openConverterScreen(OpenConverterScreenPayload state) {
        Minecraft.getInstance().setScreen(new ConverterScreen(state));
    }
}
