package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.WallRedstoneBlock;
import com.reiko.signaltower.item.RadioFavorites;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteLight;
import com.reiko.signaltower.network.ScrollFrequencyPayload;
import com.reiko.signaltower.network.SelectRemoteButtonPayload;
import com.reiko.signaltower.registry.ModRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = SignalTower.MOD_ID, dist = Dist.CLIENT)
public final class SignalTowerClient {
    public SignalTowerClient(IEventBus modEventBus, ModContainer container) {
        // Adds a "Config" button for this mod in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SignalTowerClient::registerBlockColors);
        modEventBus.addListener(SignalTowerClient::registerItemColors);
        modEventBus.addListener(SignalTowerClient::clientSetup);
        modEventBus.addListener(SignalTowerClient::registerGuiLayers);
        modEventBus.addListener(TalkKey::register); // v0.64.0: the Walkie-Talkie's Talk key
        modEventBus.addListener(DronePilot::register); // v0.67.0: the drone's camera and grab keys
        // v0.43.0: the status board's rows.
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(ModRegistry.STATUS_BOARD_BE.get(), StatusBoardRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.AVIATION_BEACON_BE.get(), AviationBeaconRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.GUY_ANCHOR_BE.get(), GuyWireRenderer::new); // v0.54.0
            event.registerBlockEntityRenderer(ModRegistry.LAUNCH_PAD_BE.get(), LaunchPadRenderer::new); // v0.61.0
            event.registerBlockEntityRenderer(ModRegistry.RADAR_DISH_BE.get(), RadarDishRenderer::new); // v0.65.0
            event.registerBlockEntityRenderer(ModRegistry.RADAR_SCREEN_BE.get(), RadarScreenRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.TV_CAMERA_BE.get(), TvCameraRenderer::new); // v0.66.0
            event.registerBlockEntityRenderer(ModRegistry.TV_MONITOR_BE.get(), TvMonitorRenderer::new);
            event.registerEntityRenderer(ModRegistry.RADIO_DRONE_ENTITY.get(), DroneRenderer::new); // v0.67.0
        });
        // v0.65.0: the Radar Dish's spinning dish, a model of its own.
        // v0.66.0: the TV Camera's turning head (and with its REC light on).
        modEventBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) -> {
            event.register(RadarDishRenderer.ROTOR);
            event.register(TvCameraRenderer.HEAD);
            event.register(TvCameraRenderer.HEAD_REC);
            event.register(DroneRenderer.BODY); // v0.67.0
            event.register(DroneRenderer.ROTOR);
        });
        // v0.66.0: camera pictures are drawn a little before each frame; new textures after a reload.
        NeoForge.EVENT_BUS.addListener(CameraFeeds::onRenderFrame);
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> CameraFeeds.clear()));
        NeoForge.EVENT_BUS.addListener(SignalTowerClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(SignalTowerClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(AuditBeamRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(AuditBeamRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReachRing::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ReachRing::onClientTick);
        NeoForge.EVENT_BUS.addListener(SignalMeterHud::onClientTick);
        NeoForge.EVENT_BUS.addListener(BroadcastMusic::onClientTick); // v0.60.0
        NeoForge.EVENT_BUS.addListener(SatelliteSky::onRenderLevel); // v0.61.0
        NeoForge.EVENT_BUS.addListener(TalkKey::onClientTick); // v0.64.0
        NeoForge.EVENT_BUS.addListener(DronePilot::onClientTick); // v0.67.0
        NeoForge.EVENT_BUS.addListener(DronePilot::onMovementInput);
        NeoForge.EVENT_BUS.addListener(DronePilot::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(DronePilot::onInteraction);
        NeoForge.EVENT_BUS.addListener(DronePilot::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(DronePilot::onRenderHand);
        if (com.reiko.signaltower.compat.map.MapWaypoints.anyMap()) {
            NeoForge.EVENT_BUS.addListener(com.reiko.signaltower.compat.map.MapWaypoints::onClientTick);
        }
    }

    /**
     * The remotes' item models switch on a "light" property: 0 = idle, 0.5 = green (heard), 1 = red
     * (nobody heard). See the overrides in models/item/remote_control.json and multi_remote.json.
     */
    private static void clientSetup(FMLClientSetupEvent event) {
        ResourceLocation light = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "light");
        event.enqueueWork(() -> {
            ItemProperties.register(ModRegistry.REMOTE_CONTROL.get(), light, SignalTowerClient::remoteLight);
            ItemProperties.register(ModRegistry.MULTI_REMOTE.get(), light, SignalTowerClient::remoteLight);
            // The pager's screen lights up while its frequency is on.
            ItemProperties.register(ModRegistry.PAGER.get(), ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "pager_on"),
                    (stack, level, entity, seed) -> PagerItem.lastSignal(stack) > 0 ? 1.0F : 0.0F);
            // The Signal Meter's needle follows its reading (v0.40.0).
            ItemProperties.register(ModRegistry.SIGNAL_METER.get(), ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "meter_level"),
                    SignalMeterHud::needle);
        });
    }

    private static float remoteLight(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        RemoteLight light = stack.get(ModRegistry.REMOTE_LIGHT.get());
        ClientLevel clock = level != null ? level : Minecraft.getInstance().level;
        if (light == null || clock == null || clock.getGameTime() >= light.untilGameTime()) {
            return 0.0F;
        }
        return light.heard() ? 0.5F : 1.0F;
    }

    /** Sneak + scroll while holding a Multi-Button Remote picks its button instead of the hotbar slot. */
    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !player.isShiftKeyDown() || event.getScrollDeltaY() == 0) {
            return;
        }
        // The main hand wins; then the off-hand.
        for (InteractionHand hand : InteractionHand.values()) {
            Item item = player.getItemInHand(hand).getItem();
            boolean offhand = hand == InteractionHand.OFF_HAND;
            if (item instanceof MultiRemoteItem) {
                event.setCanceled(true);
                // Scrolling down moves to the next button, the same way it moves to the next hotbar slot.
                PacketDistributor.sendToServer(new SelectRemoteButtonPayload(offhand, event.getScrollDeltaY() < 0));
                return;
            }
            if (RadioFavorites.isScrollable(player.getItemInHand(hand))) {
                event.setCanceled(true);
                // Up raises the frequency by 1; with Ctrl it jumps between favorites (down = next).
                PacketDistributor.sendToServer(new ScrollFrequencyPayload(offhand, event.getScrollDeltaY() > 0, Screen.hasControlDown()));
                return;
            }
        }
    }

    /** Wall Redstone glows brighter red the stronger its signal, exactly like normal dust. */
    /** The Signal Scanner's tracking arrow, drawn just above the boss bars' layer, and the reach ring's lines (v0.37.0). */
    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ScannerHud.LAYER_ID, ScannerHud::render);
        event.registerAbove(ScannerHud.LAYER_ID, ReachRing.LAYER_ID, ReachRing::renderHud);
        event.registerAbove(ReachRing.LAYER_ID, SignalMeterHud.LAYER_ID, SignalMeterHud::render);
        event.registerAbove(SignalMeterHud.LAYER_ID, DroneHud.LAYER_ID, DroneHud::render); // v0.67.0
    }

    /** The Multi-Button Remote's four buttons show their chosen colors (model layers 1-4). */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(MultiRemoteItem::tintColor, ModRegistry.MULTI_REMOTE.get());
        // v0.45.0: the Radio Lamp item in its color (as if lit).
        event.register((stack, tintIndex) -> tintIndex == 0
                ? com.reiko.signaltower.block.RadioLampBlock.tint(com.reiko.signaltower.block.RadioLampBlock.colorOf(stack), 15) : -1,
                ModRegistry.RADIO_LAMP_ITEM.get());
        // v0.54.0: a Cable Tap's band (white on the item).
        event.register((stack, tintIndex) -> tintIndex == 0 ? com.reiko.signaltower.block.CableTapBlock.tint(null) : -1,
                ModRegistry.CABLE_TAP_ITEM.get());
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) ->
                        tintIndex == 0 ? RedStoneWireBlock.getColorForPower(state.getValue(WallRedstoneBlock.POWER)) : -1,
                ModRegistry.WALL_REDSTONE.get());
        // v0.45.0: the Radio Lamp's glass, in its color and as bright as what it hears.
        event.register((state, level, pos, tintIndex) -> tintIndex == 0
                ? com.reiko.signaltower.block.RadioLampBlock.tint(state.getValue(com.reiko.signaltower.block.RadioLampBlock.COLOR),
                        state.getValue(com.reiko.signaltower.block.RadioLampBlock.POWER)) : -1,
                ModRegistry.RADIO_LAMP.get());
        // v0.54.0: a Cable Tap's band, in its color and as bright as what it carries.
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? com.reiko.signaltower.block.CableTapBlock.tint(state) : -1,
                ModRegistry.CABLE_TAP.get());
    }

    /** Leaving a world or server: forget its frequency names (the next one sends its own). */
    private static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.reiko.signaltower.radio.FrequencyNames.clientClear();
        SatelliteSky.clear(); // v0.61.0
        com.reiko.signaltower.compat.map.MapWaypoints.clear();
        com.reiko.signaltower.radio.FrequencyNotes.clientClear();
        AuditBeams.clear();
        ReachRing.clear();
        SignalMeterHud.clear();
        com.reiko.signaltower.radio.FrequencyClaims.clientClear();
        BroadcastMusic.clear();
    }
}
