package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.registry.ModRegistry;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * v0.58.0: CC: Tweaked support. Only touched when CC: Tweaked is loaded ({@code SignalTower} checks
 * first), so the mod doesn't need it. Radio Transmitters and Compact ones, every kind of Radio
 * Receiver, and Radio Telegraphs are peripherals on every side.
 */
public final class CctCompat {
    public static final String MOD_ID = "computercraft";

    private CctCompat() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CctCompat::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.RADIO_TRANSMITTER_BE.get(), (be, side) -> new TransmitterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.RADIO_RECEIVER_BE.get(), (be, side) -> new ReceiverPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.TELEGRAPH_BE.get(), (be, side) -> new TelegraphPeripheral(be));
    }

    /** For the GameTests: the peripheral type CC: Tweaked finds on the block at {@code pos}, or null. */
    public static String typeAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        var peripheral = level.getCapability(PeripheralCapability.get(), pos, net.minecraft.core.Direction.NORTH);
        return peripheral == null ? null : peripheral.getType();
    }
}
