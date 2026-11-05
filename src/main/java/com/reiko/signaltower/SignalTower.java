package com.reiko.signaltower;

import com.mojang.logging.LogUtils;
import com.reiko.signaltower.network.SetTunerFrequencyPayload;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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

        // Server config: lives in each world's serverconfig folder and is synced to players.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetTunerFrequencyPayload.TYPE, SetTunerFrequencyPayload.STREAM_CODEC,
                SetTunerFrequencyPayload::handle);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModRegistry.RADIO_TRANSMITTER_ITEM.get());
            event.accept(ModRegistry.RADIO_RECEIVER_ITEM.get());
            event.accept(ModRegistry.RADIO_MAST_ITEM.get());
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistry.FREQUENCY_TUNER.get());
        }
    }
}
