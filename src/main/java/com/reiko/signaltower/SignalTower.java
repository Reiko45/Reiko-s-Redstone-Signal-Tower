package com.reiko.signaltower;

import com.mojang.logging.LogUtils;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.network.ConfigureConverterPayload;
import com.reiko.signaltower.network.ConfigureFavoritesPayload;
import com.reiko.signaltower.network.ConfigureMultiRemotePayload;
import com.reiko.signaltower.network.ConfigurePagerPayload;
import com.reiko.signaltower.network.ConfigureRadioItemPayload;
import com.reiko.signaltower.network.DirectoryRequestPayload;
import com.reiko.signaltower.network.DirectoryResultPayload;
import com.reiko.signaltower.network.EditGroupPayload;
import com.reiko.signaltower.network.FrequencyClaimPayload;
import com.reiko.signaltower.network.FrequencyClaimsPayload;
import com.reiko.signaltower.network.FrequencyNamePayload;
import com.reiko.signaltower.network.FrequencyNamesPayload;
import com.reiko.signaltower.network.FrequencyNotePayload;
import com.reiko.signaltower.network.FrequencyNotesPayload;
import com.reiko.signaltower.network.GroupsPayload;
import com.reiko.signaltower.network.OpenConverterScreenPayload;
import com.reiko.signaltower.network.RemoteScanRequestPayload;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import com.reiko.signaltower.network.ScannerRequestPayload;
import com.reiko.signaltower.network.ScannerResultPayload;
import com.reiko.signaltower.network.ScrollFrequencyPayload;
import com.reiko.signaltower.network.SelectRemoteButtonPayload;
import com.reiko.signaltower.network.SetFrequencyNamePayload;
import com.reiko.signaltower.network.SetFrequencyNotePayload;
import com.reiko.signaltower.network.SetFrequencyPrivacyPayload;
import com.reiko.signaltower.network.TrackTransmitterPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.slf4j.Logger;

/**
 * Reiko's Redstone Signal Tower.
 * <p>
 * Radio Transmitters broadcast the redstone signal they receive on a numbered frequency. The higher
 * a transmitter sits above sea level, the farther it reaches. Radio Receivers tuned to the same
 * frequency output that signal. Radio Masts are tower segments that carry a redstone signal up to
 * the transmitter on top. The Frequency Tuner sets and copies frequencies.
 */
@Mod(SignalTower.MOD_ID)
public final class SignalTower {
    public static final String MOD_ID = "reikos_signal_tower";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SignalTower(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistry.register(modEventBus);
        modEventBus.addListener(SignalTower::addToCreativeTabs);
        modEventBus.addListener(SignalTower::registerPayloads);
        NeoForge.EVENT_BUS.addListener(SignalTower::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SignalTower::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(SignalTower::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(SignalTower::onServerTick);

        // Server config: lives in each world's serverconfig folder and is synced to players.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ConfigureRadioItemPayload.TYPE, ConfigureRadioItemPayload.STREAM_CODEC,
                ConfigureRadioItemPayload::handle);
        registrar.playToServer(ConfigureMultiRemotePayload.TYPE, ConfigureMultiRemotePayload.STREAM_CODEC,
                ConfigureMultiRemotePayload::handle);
        registrar.playToServer(SelectRemoteButtonPayload.TYPE, SelectRemoteButtonPayload.STREAM_CODEC,
                SelectRemoteButtonPayload::handle);
        registrar.playToServer(RemoteScanRequestPayload.TYPE, RemoteScanRequestPayload.STREAM_CODEC,
                RemoteScanRequestPayload::handle);
        registrar.playToServer(ConfigurePagerPayload.TYPE, ConfigurePagerPayload.STREAM_CODEC,
                ConfigurePagerPayload::handle);
        registrar.playToServer(ScrollFrequencyPayload.TYPE, ScrollFrequencyPayload.STREAM_CODEC,
                ScrollFrequencyPayload::handle);
        registrar.playToServer(ConfigureFavoritesPayload.TYPE, ConfigureFavoritesPayload.STREAM_CODEC,
                ConfigureFavoritesPayload::handle);
        registrar.playToServer(TrackTransmitterPayload.TYPE, TrackTransmitterPayload.STREAM_CODEC,
                TrackTransmitterPayload::handle);
        registrar.playToServer(ScannerRequestPayload.TYPE, ScannerRequestPayload.STREAM_CODEC,
                ScannerRequestPayload::handle);
        registrar.playToServer(SetFrequencyNamePayload.TYPE, SetFrequencyNamePayload.STREAM_CODEC,
                SetFrequencyNamePayload::handle);
        registrar.playToClient(FrequencyNamePayload.TYPE, FrequencyNamePayload.STREAM_CODEC,
                FrequencyNamePayload::handle);
        registrar.playToClient(FrequencyNamesPayload.TYPE, FrequencyNamesPayload.STREAM_CODEC,
                FrequencyNamesPayload::handle);
        registrar.playToServer(SetFrequencyNotePayload.TYPE, SetFrequencyNotePayload.STREAM_CODEC,
                SetFrequencyNotePayload::handle);
        registrar.playToClient(FrequencyNotePayload.TYPE, FrequencyNotePayload.STREAM_CODEC,
                FrequencyNotePayload::handle);
        registrar.playToClient(FrequencyNotesPayload.TYPE, FrequencyNotesPayload.STREAM_CODEC,
                FrequencyNotesPayload::handle);
        registrar.playToServer(EditGroupPayload.TYPE, EditGroupPayload.STREAM_CODEC, EditGroupPayload::handle);
        registrar.playToClient(GroupsPayload.TYPE, GroupsPayload.STREAM_CODEC, GroupsPayload::handle);
        registrar.playToServer(DirectoryRequestPayload.TYPE, DirectoryRequestPayload.STREAM_CODEC,
                DirectoryRequestPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.AuditRequestPayload.TYPE,
                com.reiko.signaltower.network.AuditRequestPayload.STREAM_CODEC, com.reiko.signaltower.network.AuditRequestPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.LogRequestPayload.TYPE,
                com.reiko.signaltower.network.LogRequestPayload.STREAM_CODEC, com.reiko.signaltower.network.LogRequestPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.LogResultPayload.TYPE,
                com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC, com.reiko.signaltower.network.LogResultPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.GuestPayload.TYPE,
                com.reiko.signaltower.network.GuestPayload.STREAM_CODEC, com.reiko.signaltower.network.GuestPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.ScreenNoticePayload.TYPE,
                com.reiko.signaltower.network.ScreenNoticePayload.STREAM_CODEC, com.reiko.signaltower.network.ScreenNoticePayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.HandOverFrequencyPayload.TYPE,
                com.reiko.signaltower.network.HandOverFrequencyPayload.STREAM_CODEC, com.reiko.signaltower.network.HandOverFrequencyPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.AuditResultPayload.TYPE,
                com.reiko.signaltower.network.AuditResultPayload.STREAM_CODEC, com.reiko.signaltower.network.AuditResultPayload::handle);
        registrar.playToClient(DirectoryResultPayload.TYPE, DirectoryResultPayload.STREAM_CODEC,
                DirectoryResultPayload::handle);
        registrar.playToServer(SetFrequencyPrivacyPayload.TYPE, SetFrequencyPrivacyPayload.STREAM_CODEC,
                SetFrequencyPrivacyPayload::handle);
        registrar.playToClient(FrequencyClaimPayload.TYPE, FrequencyClaimPayload.STREAM_CODEC,
                FrequencyClaimPayload::handle);
        registrar.playToClient(FrequencyClaimsPayload.TYPE, FrequencyClaimsPayload.STREAM_CODEC,
                FrequencyClaimsPayload::handle);
        registrar.playToServer(ConfigureConverterPayload.TYPE, ConfigureConverterPayload.STREAM_CODEC,
                ConfigureConverterPayload::handle);
        registrar.playToClient(OpenConverterScreenPayload.TYPE, OpenConverterScreenPayload.STREAM_CODEC,
                OpenConverterScreenPayload::handle);
        registrar.playToClient(ScannerResultPayload.TYPE, ScannerResultPayload.STREAM_CODEC,
                ScannerResultPayload::handle);
        registrar.playToClient(RemoteScanResultPayload.TYPE, RemoteScanResultPayload.STREAM_CODEC,
                RemoteScanResultPayload::handle);
        // v0.37.0: the reach preview ring and the Code Keypad.
        registrar.playToServer(com.reiko.signaltower.network.ReachPreviewRequestPayload.TYPE,
                com.reiko.signaltower.network.ReachPreviewRequestPayload.STREAM_CODEC, com.reiko.signaltower.network.ReachPreviewRequestPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.ReachPreviewPayload.TYPE,
                com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC, com.reiko.signaltower.network.ReachPreviewPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenKeypadScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenKeypadScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenKeypadScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.KeypadEnterPayload.TYPE,
                com.reiko.signaltower.network.KeypadEnterPayload.STREAM_CODEC, com.reiko.signaltower.network.KeypadEnterPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.KeypadConfigurePayload.TYPE,
                com.reiko.signaltower.network.KeypadConfigurePayload.STREAM_CODEC, com.reiko.signaltower.network.KeypadConfigurePayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.KeypadResultPayload.TYPE,
                com.reiko.signaltower.network.KeypadResultPayload.STREAM_CODEC, com.reiko.signaltower.network.KeypadResultPayload::handle);
        // v0.38.0: keypad guest codes.
        registrar.playToServer(com.reiko.signaltower.network.KeypadGuestCodesPayload.TYPE,
                com.reiko.signaltower.network.KeypadGuestCodesPayload.STREAM_CODEC, com.reiko.signaltower.network.KeypadGuestCodesPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.KeypadGuestListPayload.TYPE,
                com.reiko.signaltower.network.KeypadGuestListPayload.STREAM_CODEC, com.reiko.signaltower.network.KeypadGuestListPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenDelayScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenDelayScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenDelayScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureDelayPayload.TYPE,
                com.reiko.signaltower.network.ConfigureDelayPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureDelayPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenSpeakerScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenSpeakerScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenSpeakerScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureSpeakerPayload.TYPE,
                com.reiko.signaltower.network.ConfigureSpeakerPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureSpeakerPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenBoardScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenBoardScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenBoardScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureBoardPayload.TYPE,
                com.reiko.signaltower.network.ConfigureBoardPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureBoardPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenTimerScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenTimerScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenTimerScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureTimerPayload.TYPE,
                com.reiko.signaltower.network.ConfigureTimerPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureTimerPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenCounterScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenCounterScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenCounterScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureCounterPayload.TYPE,
                com.reiko.signaltower.network.ConfigureCounterPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureCounterPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenLogicScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenLogicScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenLogicScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureLogicPayload.TYPE,
                com.reiko.signaltower.network.ConfigureLogicPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureLogicPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.MeterReadingPayload.TYPE,
                com.reiko.signaltower.network.MeterReadingPayload.STREAM_CODEC, com.reiko.signaltower.network.MeterReadingPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenSensorScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenSensorScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenSensorScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureSensorPayload.TYPE,
                com.reiko.signaltower.network.ConfigureSensorPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureSensorPayload::handle);
    }

    /** Pagers note when their owner left, for the catch-up at the next login. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PagerItem.markAway(event.getEntity(), event.getEntity().level().getGameTime());
    }

    /**
     * The player gets the world's frequency names, notes and private frequencies, and their own friend
     * groups, and pagers say what turned on while their owner was away.
     */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new FrequencyNamesPayload(FrequencyNames.get(player.server).all()));
            PacketDistributor.sendToPlayer(player, FrequencyNotes.allFor(player.server, player));
            PacketDistributor.sendToPlayer(player, new FrequencyClaimsPayload(FrequencyClaims.get(player.server).allInfo()));
            PacketDistributor.sendToPlayer(player, new GroupsPayload(FrequencyClaims.get(player.server).groupInfo(player.getUUID())));
            // A singleplayer world is paused while its owner is away, so there's nothing to report then.
            boolean worldRan = !player.server.isSingleplayerOwner(player.getGameProfile());
            PagerItem.catchUp(player.serverLevel(), player, worldRan);
            // Anyone who tried to use their private frequencies while they were away (v0.36.0).
            for (net.minecraft.network.chat.Component line : com.reiko.signaltower.radio.IntruderAlerts.missedLines(player.server, player.getUUID())) {
                player.displayClientMessage(line, false);
            }
        }
    }

    /** Once a second: guest passes that ran out end (v0.36.0). */
    private static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            com.reiko.signaltower.network.GuestPayload.expire(event.getServer(), System.currentTimeMillis());
        }
    }

    /** Ends Remote Control pulses on time. */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).tick(serverLevel.getGameTime(), serverLevel.getDayTime());
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModRegistry.RADIO_TRANSMITTER_ITEM.get());
            event.accept(ModRegistry.RADIO_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_TOGGLE_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_INVERTED_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_LOGIC_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_DELAY_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_COUNTER_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_SPEAKER_ITEM.get());
            event.accept(ModRegistry.RADIO_STATUS_BOARD_ITEM.get());
            event.accept(ModRegistry.RADIO_REPEATER_ITEM.get());
            event.accept(ModRegistry.RADIO_CONVERTER_ITEM.get());
            event.accept(ModRegistry.COMPACT_RADIO_RECEIVER_ITEM.get());
            event.accept(ModRegistry.COMPACT_RADIO_TRANSMITTER_ITEM.get());
            event.accept(ModRegistry.CODE_KEYPAD_ITEM.get());
            event.accept(ModRegistry.RADIO_MOTION_SENSOR_ITEM.get());
            event.accept(ModRegistry.RADIO_TIMER_TRANSMITTER_ITEM.get());
            event.accept(ModRegistry.RADIO_MAST_ITEM.get());
            event.accept(ModRegistry.WALL_REDSTONE_ITEM.get());
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
            event.accept(ModRegistry.REMOTE_CONTROL.get());
            event.accept(ModRegistry.MULTI_REMOTE.get());
            event.accept(ModRegistry.PAGER.get());
            event.accept(ModRegistry.SIGNAL_SCANNER.get());
            event.accept(ModRegistry.SIGNAL_METER.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
            event.accept(ModRegistry.REMOTE_CONTROL.get());
            event.accept(ModRegistry.MULTI_REMOTE.get());
            event.accept(ModRegistry.PAGER.get());
            event.accept(ModRegistry.SIGNAL_SCANNER.get());
            event.accept(ModRegistry.SIGNAL_METER.get());
        }
    }
}
