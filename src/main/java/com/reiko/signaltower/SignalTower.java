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
 * Reiko's Wireless Redstone (called Reiko's Redstone Signal Tower before v0.68.1; its mod id is still
 * {@value #MOD_ID}, so worlds made before keep their blocks).
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
        com.reiko.signaltower.registry.ModSounds.register(modEventBus); // v0.59.0
        com.reiko.signaltower.world.ModWorldgen.register(modEventBus); // v0.55.0
        com.reiko.signaltower.advancement.TowerAdvancements.register(modEventBus); // v0.56.0
        com.reiko.signaltower.world.ModVillagers.register(modEventBus); // v0.56.0
        com.reiko.signaltower.world.VillageRadioShacks.register(); // v0.57.0
        com.reiko.signaltower.radio.MapTowers.register(modEventBus); // v0.61.0
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            com.reiko.signaltower.compat.cct.CctCompat.register(modEventBus); // v0.58.0: only with CC: Tweaked
        }
        modEventBus.addListener(SignalTower::addToCreativeTabs);
        modEventBus.addListener(SignalTower::registerPayloads);
        modEventBus.addListener(SignalTower::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(SignalTower::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SignalTower::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(SignalTower::onLoggedIn);
        // v0.61.0: a player in another dimension (or back from death) sees that dimension's satellites.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) com.reiko.signaltower.network.SatellitesPayload.sendTo(player);
            com.reiko.signaltower.radio.DronePilots.stop(event.getEntity(), null); // v0.67.0
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) com.reiko.signaltower.network.SatellitesPayload.sendTo(player);
        });
        NeoForge.EVENT_BUS.addListener(SignalTower::onServerTick);
        NeoForge.EVENT_BUS.addListener(SignalTower::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.reiko.signaltower.radio.MastClimbing::onPlayerTick); // v0.55.0

        // Server config: lives in each world's serverconfig folder and is synced to players.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    /** v0.49.0: hoppers, pipes and FE cables can charge a Radio Battery. */
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModRegistry.RADIO_BATTERY_BE.get(),
                (battery, side) -> battery.items());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, ModRegistry.RADIO_BATTERY_BE.get(),
                (battery, side) -> battery.energy());
        // v0.60.0: hoppers and pipes can feed a Radio Jukebox discs, and take them out once a song is over.
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModRegistry.RADIO_JUKEBOX_BE.get(),
                (jukebox, side) -> jukebox.items());
        // v0.61.0: hoppers can load a Launch Pad.
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModRegistry.LAUNCH_PAD_BE.get(),
                (pad, side) -> pad.items());
        // v0.63.0: hoppers can give a Ticker Tape Printer a Book and Quill, and take out finished logbooks.
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModRegistry.TICKER_PRINTER_BE.get(),
                (printer, side) -> printer.items());
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
        registrar.playToServer(com.reiko.signaltower.network.WalkieTalkPayload.TYPE, com.reiko.signaltower.network.WalkieTalkPayload.STREAM_CODEC,
                com.reiko.signaltower.network.WalkieTalkPayload::handle); // v0.64.0
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
        registrar.playToClient(com.reiko.signaltower.network.TelegraphFeedPayload.TYPE, // v0.51.0
                com.reiko.signaltower.network.TelegraphFeedPayload.STREAM_CODEC, com.reiko.signaltower.network.TelegraphFeedPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.TelegraphActionPayload.TYPE,
                com.reiko.signaltower.network.TelegraphActionPayload.STREAM_CODEC, com.reiko.signaltower.network.TelegraphActionPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenBoardScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenBoardScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenBoardScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureBoardPayload.TYPE,
                com.reiko.signaltower.network.ConfigureBoardPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureBoardPayload::handle);
        // v0.44.0: the Aviation Beacon and the Radio Signal Recorder.
        registrar.playToServer(com.reiko.signaltower.network.ConfigureBeaconPayload.TYPE,
                com.reiko.signaltower.network.ConfigureBeaconPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureBeaconPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenRecorderScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenRecorderScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenRecorderScreenPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.RecorderStatusPayload.TYPE,
                com.reiko.signaltower.network.RecorderStatusPayload.STREAM_CODEC, com.reiko.signaltower.network.RecorderStatusPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.RecorderStatusPayload.Request.TYPE,
                com.reiko.signaltower.network.RecorderStatusPayload.Request.STREAM_CODEC, com.reiko.signaltower.network.RecorderStatusPayload.Request::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureRecorderPayload.TYPE,
                com.reiko.signaltower.network.ConfigureRecorderPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureRecorderPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenTimerScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenTimerScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenTimerScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureTimerPayload.TYPE,
                com.reiko.signaltower.network.ConfigureTimerPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureTimerPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenStationScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenStationScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenStationScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureStationPayload.TYPE,
                com.reiko.signaltower.network.ConfigureStationPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureStationPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenCounterScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenCounterScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenCounterScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureCounterPayload.TYPE,
                com.reiko.signaltower.network.ConfigureCounterPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureCounterPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenLogicScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenLogicScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenLogicScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureLogicPayload.TYPE,
                com.reiko.signaltower.network.ConfigureLogicPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureLogicPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.SatellitesPayload.TYPE, // v0.61.0
                com.reiko.signaltower.network.SatellitesPayload.STREAM_CODEC, com.reiko.signaltower.network.SatellitesPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.MapWaypointsPayload.TYPE,
                com.reiko.signaltower.network.MapWaypointsPayload.STREAM_CODEC, com.reiko.signaltower.network.MapWaypointsPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.MeterReadingPayload.TYPE,
                com.reiko.signaltower.network.MeterReadingPayload.STREAM_CODEC, com.reiko.signaltower.network.MeterReadingPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenRadarScreenPayload.TYPE, // v0.65.0
                com.reiko.signaltower.network.OpenRadarScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenRadarScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureRadarPayload.TYPE,
                com.reiko.signaltower.network.ConfigureRadarPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureRadarPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenTvCameraPayload.TYPE, // v0.66.0
                com.reiko.signaltower.network.OpenTvCameraPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenTvCameraPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureTvCameraPayload.TYPE,
                com.reiko.signaltower.network.ConfigureTvCameraPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureTvCameraPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenTvMonitorPayload.TYPE,
                com.reiko.signaltower.network.OpenTvMonitorPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenTvMonitorPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureTvMonitorPayload.TYPE,
                com.reiko.signaltower.network.ConfigureTvMonitorPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureTvMonitorPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.DroneControlPayload.TYPE, // v0.67.0
                com.reiko.signaltower.network.DroneControlPayload.STREAM_CODEC, com.reiko.signaltower.network.DroneControlPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.DroneInputPayload.TYPE,
                com.reiko.signaltower.network.DroneInputPayload.STREAM_CODEC, com.reiko.signaltower.network.DroneInputPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.DroneGrabPayload.TYPE,
                com.reiko.signaltower.network.DroneGrabPayload.STREAM_CODEC, com.reiko.signaltower.network.DroneGrabPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.DroneStatusPayload.TYPE,
                com.reiko.signaltower.network.DroneStatusPayload.STREAM_CODEC, com.reiko.signaltower.network.DroneStatusPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenDronePayload.TYPE,
                com.reiko.signaltower.network.OpenDronePayload.STREAM_CODEC, com.reiko.signaltower.network.OpenDronePayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureDronePayload.TYPE,
                com.reiko.signaltower.network.ConfigureDronePayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureDronePayload::handle);
        // v0.68.0: the Security Console
        registrar.playToClient(com.reiko.signaltower.network.OpenSecurityPayload.TYPE,
                com.reiko.signaltower.network.OpenSecurityPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenSecurityPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureSecurityPayload.TYPE,
                com.reiko.signaltower.network.ConfigureSecurityPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureSecurityPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.SecurityCommandPayload.TYPE,
                com.reiko.signaltower.network.SecurityCommandPayload.STREAM_CODEC, com.reiko.signaltower.network.SecurityCommandPayload::handle);
        registrar.playToClient(com.reiko.signaltower.network.OpenSensorScreenPayload.TYPE,
                com.reiko.signaltower.network.OpenSensorScreenPayload.STREAM_CODEC, com.reiko.signaltower.network.OpenSensorScreenPayload::handle);
        registrar.playToServer(com.reiko.signaltower.network.ConfigureSensorPayload.TYPE,
                com.reiko.signaltower.network.ConfigureSensorPayload.STREAM_CODEC, com.reiko.signaltower.network.ConfigureSensorPayload::handle);
    }

    /** Pagers note when their owner left, for the catch-up at the next login. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PagerItem.markAway(event.getEntity(), event.getEntity().level().getGameTime());
        com.reiko.signaltower.radio.DronePilots.stop(event.getEntity(), null); // v0.67.0: their drone hovers
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
            com.reiko.signaltower.network.SatellitesPayload.sendTo(player); // v0.61.0
            com.reiko.signaltower.radio.MapTowers.forget(player); // the waypoints go out again soon
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
        com.reiko.signaltower.radio.MapTowers.tick(event.getServer()); // v0.61.0
    }

    /** Ends Remote Control pulses on time. */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SignalNetwork network = SignalNetwork.get(serverLevel);
            network.tick(serverLevel.getGameTime(), serverLevel.getDayTime());
            network.watchWeather(serverLevel); // v0.46.0: rain and thunder shorten reach
            com.reiko.signaltower.radio.Lightning.tick(serverLevel, network); // v0.47.0: lightning strikes towers
            com.reiko.signaltower.radio.PowerCost.tick(serverLevel, network); // v0.49.0: towers pay for sending
        }
    }

    /** v0.47.0: a lightning bolt landing on a tower (vanilla lightning, a trident, /summon) knocks it out. */
    private static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel serverLevel
                && event.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt) {
            com.reiko.signaltower.radio.Lightning.onBoltJoined(serverLevel, bolt);
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
            event.accept(ModRegistry.RADIO_WEATHER_STATION_ITEM.get());
            event.accept(ModRegistry.BUNDLED_TRANSMITTER_ITEM.get());
            event.accept(ModRegistry.BUNDLED_RECEIVER_ITEM.get());
            event.accept(ModRegistry.BUNDLED_CABLE_ITEM.get());
            event.accept(ModRegistry.CABLE_TAP_ITEM.get());
            event.accept(ModRegistry.GUY_ANCHOR_ITEM.get());
            event.accept(ModRegistry.TOWER_PLATFORM_ITEM.get());
            event.accept(ModRegistry.RADIO_SIGNAL_RECORDER_ITEM.get());
            event.accept(ModRegistry.RADIO_JUKEBOX_ITEM.get());
            event.accept(ModRegistry.RADIO_JAMMER_ITEM.get());
            event.accept(ModRegistry.RADIO_MAST_ITEM.get());
            event.accept(ModRegistry.DISH_ROTATOR_ITEM.get());
            event.accept(ModRegistry.AVIATION_BEACON_ITEM.get());
            event.accept(ModRegistry.RADIO_LAMP_ITEM.get());
            event.accept(ModRegistry.RADIO_BATTERY_ITEM.get());
            event.accept(ModRegistry.SOLAR_PANEL_ITEM.get());
            event.accept(ModRegistry.RADIO_TELEGRAPH_ITEM.get());
            event.accept(ModRegistry.MORSE_KEY_ITEM.get()); // v0.62.0
            event.accept(ModRegistry.TICKER_PRINTER_ITEM.get()); // v0.63.0
            event.accept(ModRegistry.RADAR_DISH_ITEM.get()); // v0.65.0
            event.accept(ModRegistry.RADAR_SCREEN_ITEM.get());
            event.accept(ModRegistry.TV_CAMERA_ITEM.get()); // v0.66.0
            event.accept(ModRegistry.TV_MONITOR_ITEM.get());
            event.accept(ModRegistry.RADIO_DRONE.get()); // v0.67.0
            event.accept(ModRegistry.DRONE_CONTROLLER.get());
            event.accept(ModRegistry.LANDING_PAD_ITEM.get());
            event.accept(ModRegistry.SECURITY_CONSOLE_ITEM.get()); // v0.68.0
            event.accept(ModRegistry.DOOR_SENSOR_ITEM.get());
            event.accept(ModRegistry.COVERAGE_MAP.get());
            event.accept(ModRegistry.IRON_ANTENNA.get());
            event.accept(ModRegistry.GOLD_ANTENNA.get());
            event.accept(ModRegistry.DIAMOND_ANTENNA.get());
            event.accept(ModRegistry.NETHERITE_ANTENNA.get());
            event.accept(ModRegistry.DISH_ANTENNA.get());
            event.accept(ModRegistry.ENDER_ANTENNA.get());
            event.accept(ModRegistry.SATELLITE_DISH.get()); // v0.61.0
            event.accept(ModRegistry.SATELLITE.get());
            event.accept(ModRegistry.LAUNCH_PAD_ITEM.get());
            event.accept(ModRegistry.WALL_REDSTONE_ITEM.get());
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
            event.accept(ModRegistry.REMOTE_CONTROL.get());
            event.accept(ModRegistry.MULTI_REMOTE.get());
            event.accept(ModRegistry.PAGER.get());
            event.accept(ModRegistry.WALKIE_TALKIE.get()); // v0.64.0
            event.accept(ModRegistry.SIGNAL_SCANNER.get());
            event.accept(ModRegistry.SIGNAL_METER.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
            event.accept(ModRegistry.REMOTE_CONTROL.get());
            event.accept(ModRegistry.MULTI_REMOTE.get());
            event.accept(ModRegistry.PAGER.get());
            event.accept(ModRegistry.WALKIE_TALKIE.get()); // v0.64.0
            event.accept(ModRegistry.SIGNAL_SCANNER.get());
            event.accept(ModRegistry.SIGNAL_METER.get());
        }
    }
}
