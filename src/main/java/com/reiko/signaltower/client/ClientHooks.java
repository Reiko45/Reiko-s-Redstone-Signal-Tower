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
    /** A screen's answer from the server: shown in the Guests screen if it's open, else above the hotbar. */
    public static void onScreenNotice(net.minecraft.network.chat.Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GuestScreen screen) {
            screen.notice(message);
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

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

    /** Opens a Radio Motion Sensor's settings screen (sent by the server when its owner right-clicks it). */
    public static void openSensorScreen(com.reiko.signaltower.network.OpenSensorScreenPayload state) {
        Minecraft.getInstance().setScreen(new SensorScreen(state));
    }

    /** Opens a Radio Speaker's screen (v0.43.0). */
    public static void openSpeakerScreen(com.reiko.signaltower.network.OpenSpeakerScreenPayload state) {
        Minecraft.getInstance().setScreen(new SpeakerScreen(state));
    }

    /** Opens a Radio Status Board's screen (v0.43.0). */
    public static void openBoardScreen(com.reiko.signaltower.network.OpenBoardScreenPayload state) {
        Minecraft.getInstance().setScreen(new BoardScreen(state));
    }

    /** Opens a Radio Timer Transmitter's screen (v0.42.0). */
    public static void openTimerScreen(com.reiko.signaltower.network.OpenTimerScreenPayload state) {
        Minecraft.getInstance().setScreen(new TimerScreen(state));
    }

    /** Opens a Radio Counter Receiver's screen (v0.42.0). */
    public static void openCounterScreen(com.reiko.signaltower.network.OpenCounterScreenPayload state) {
        Minecraft.getInstance().setScreen(new CounterScreen(state));
    }

    /** Opens a Radio Delay Receiver's screen (sent by the server when the block is right-clicked). */
    public static void openDelayScreen(com.reiko.signaltower.network.OpenDelayScreenPayload state) {
        Minecraft.getInstance().setScreen(new DelayScreen(state));
    }

    /** Opens a Radio Logic Receiver's screen (sent by the server when the block is right-clicked). */
    public static void openLogicScreen(com.reiko.signaltower.network.OpenLogicScreenPayload state) {
        Minecraft.getInstance().setScreen(new LogicScreen(state));
    }

    /** Opens a Signal Meter's frequency screen: the pager's, with only a Sound button. */
    public static void openMeterScreen(InteractionHand hand, int frequency, Component title, TunerPresets favorites, boolean sound) {
        Minecraft.getInstance().setScreen(new FrequencyScreen(hand, frequency, title, favorites, sound));
    }

    /** A Signal Meter reading arrived from the server. */
    public static void onMeterReading(com.reiko.signaltower.network.MeterReadingPayload reading) {
        SignalMeterHud.onReading(reading);
    }

    /** Opens a Radio Converter's screen (sent by the server when the block is right-clicked). */
    public static void openConverterScreen(OpenConverterScreenPayload state) {
        Minecraft.getInstance().setScreen(new ConverterScreen(state));
    }

    /** Opens a Code Keypad's screen (v0.37.0). */
    public static void openKeypadScreen(com.reiko.signaltower.network.OpenKeypadScreenPayload state) {
        Minecraft.getInstance().setScreen(new KeypadScreen(state));
    }

    /** What the keypad said: shown in its screen if that's still open, else above the hotbar. */
    public static void onKeypadResult(com.reiko.signaltower.network.KeypadResultPayload result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof KeypadScreen screen && screen.pos().equals(result.pos())) {
            screen.onResult(result);
        } else {
            minecraft.gui.setOverlayMessage(result.message(), false);
        }
    }

    /** A keypad's guest codes (v0.38.0): to the open Guest codes screen, else just what the change said. */
    public static void onKeypadGuestList(com.reiko.signaltower.network.KeypadGuestListPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof KeypadGuestsScreen screen && screen.pos().equals(payload.pos())) {
            screen.onList(payload);
        } else if (!payload.notice().getString().isEmpty()) {
            minecraft.gui.setOverlayMessage(payload.notice(), false);
        }
    }

    /** The server's answer for the reach preview ring (v0.37.0). */
    public static void onReachPreview(com.reiko.signaltower.network.ReachPreviewPayload answer) {
        ReachRing.onAnswer(answer);
    }
}
