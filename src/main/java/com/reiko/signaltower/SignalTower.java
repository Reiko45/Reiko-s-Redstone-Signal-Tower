package com.reiko.signaltower;

import com.mojang.logging.LogUtils;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.network.ConfigureFavoritesPayload;
import com.reiko.signaltower.network.ConfigureMultiRemotePayload;
import com.reiko.signaltower.network.ConfigurePagerPayload;
import com.reiko.signaltower.network.ConfigureRadioItemPayload;
import com.reiko.signaltower.network.RemoteScanRequestPayload;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import com.reiko.signaltower.network.ScannerRequestPayload;
import com.reiko.signaltower.network.ScannerResultPayload;
import com.reiko.signaltower.network.ScrollFrequencyPayload;
import com.reiko.signaltower.network.SelectRemoteButtonPayload;
import com.reiko.signaltower.network.TrackTransmitterPayload;
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
        registrar.playToClient(ScannerResultPayload.TYPE, ScannerResultPayload.STREAM_CODEC,
                ScannerResultPayload::handle);
        registrar.playToClient(RemoteScanResultPayload.TYPE, RemoteScanResultPayload.STREAM_CODEC,
                RemoteScanResultPayload::handle);
    }

    /** Ends Remote Control pulses on time. */
    /** Pagers note when their owner left, for the catch-up at the next login. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PagerItem.markAway(event.getEntity(), event.getEntity().level().getGameTime());
    }

    /** Pagers say what turned on while their owner was away. */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A singleplayer world is paused while its owner is away, so there's nothing to report then.
            boolean worldRan = !player.server.isSingleplayerOwner(player.getGameProfile());
            PagerItem.catchUp(player.serverLevel(), player, worldRan);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).tick(serverLevel.getGameTime(), serverLevel.getDayTime());
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModRegistry.RADIO_TRANSMITTER_ITEM.get());
            event.accept(ModRegistry.RADIO_RECEIVER_ITEM.get());
            event.accept(ModRegistry.COMPACT_RADIO_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_MAST_ITEM.get());
            event.accept(ModRegistry.WALL_REDSTONE_ITEM.get());
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
            event.accept(ModRegistry.REMOTE_CONTROL.get());
            event.accept(ModRegistry.MULTI_REMOTE.get());
            event.accept(ModRegistry.PAGER.get());
            event.accept(ModRegistry.SIGNAL_SCANNER.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
            event.accept(ModRegistry.REMOTE_CONTROL.get());
            event.accept(ModRegistry.MULTI_REMOTE.get());
            event.accept(ModRegistry.PAGER.get());
            event.accept(ModRegistry.SIGNAL_SCANNER.get());
        }
    }
}
