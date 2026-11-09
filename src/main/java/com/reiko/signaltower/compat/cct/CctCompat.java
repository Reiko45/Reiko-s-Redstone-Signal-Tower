package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.registry.ModRegistry;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * v0.58.0: CC: Tweaked support. Only touched when CC: Tweaked is loaded ({@code SignalTower} checks
 * first), so the mod doesn't need it. Radio Transmitters and Compact ones, every kind of Radio
 * Receiver, and Radio Telegraphs are peripherals on every side; since v0.59.0 a Radio Pager makes a
 * Radio Turtle or Radio Pocket Computer ({@link RadioUpgrades}).
 */
public final class CctCompat {
    public static final String MOD_ID = "computercraft";

    private CctCompat() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CctCompat::registerCapabilities);
        RadioUpgrades.register(modEventBus); // v0.59.0: Radio Turtles and Radio Pocket Computers
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            CctClient.register(modEventBus);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.RADIO_TRANSMITTER_BE.get(), (be, side) -> new TransmitterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.RADIO_RECEIVER_BE.get(), (be, side) -> new ReceiverPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.TELEGRAPH_BE.get(), (be, side) -> new TelegraphPeripheral(be));
    }

    /**
     * For the GameTests (v0.59.0): checks the radio upgrades as CC: Tweaked loaded them from the data
     * files: both are there, take any Radio Pager and keep its frequency (and give it back), and make
     * a "radio" peripheral whose frequency is the upgrade's. Returns what's wrong (empty if nothing).
     */
    public static java.util.List<String> checkRadioUpgrades(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.reiko.signaltower.SignalTower.MOD_ID, "radio");
        var turtle = level.registryAccess().registryOrThrow(dan200.computercraft.api.turtle.ITurtleUpgrade.REGISTRY).get(id);
        var pocket = level.registryAccess().registryOrThrow(dan200.computercraft.api.pocket.IPocketUpgrade.REGISTRY).get(id);
        if (!(turtle instanceof RadioUpgrades.TurtleRadio) || !(pocket instanceof RadioUpgrades.PocketRadio)) {
            problems.add("upgrades: turtle " + turtle + ", pocket " + pocket);
            return problems;
        }
        net.minecraft.world.item.ItemStack pager = new net.minecraft.world.item.ItemStack(ModRegistry.PAGER.get());
        com.reiko.signaltower.item.PagerItem.setFrequency(pager, 4627);
        for (dan200.computercraft.api.upgrades.UpgradeBase upgrade : java.util.List.of(turtle, pocket)) {
            if (!upgrade.isItemSuitable(pager) || upgrade.isItemSuitable(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE))) {
                problems.add(upgrade + ": not a pager's upgrade");
            }
            var data = upgrade.getUpgradeData(pager);
            if (MobileRadioPeripheral.frequencyOf(data) != 4627) {
                problems.add(upgrade + ": frequency not kept: " + data);
            }
            var back = upgrade.getUpgradeItem(data);
            if (!back.is(ModRegistry.PAGER.get()) || back.getOrDefault(ModRegistry.FREQUENCY.get(), 0) != 4627) {
                problems.add(upgrade + ": pager back " + back + " " + back.getComponentsPatch());
            }
            if (!upgrade.getAdjective().getString().equals("Radio")) {
                problems.add(upgrade + ": adjective " + upgrade.getAdjective().getString());
            }
        }
        net.minecraft.core.component.DataComponentPatch[] data = {turtle.getUpgradeData(pager)};
        MobileRadioPeripheral.Host host = new MobileRadioPeripheral.Host() {
            public net.minecraft.server.level.ServerLevel level() { return level; }
            public net.minecraft.core.BlockPos position() { return pos; }
            public java.util.UUID owner() { return null; }
            public net.minecraft.core.component.DataComponentPatch data() { return data[0]; }
            public void setData(net.minecraft.core.component.DataComponentPatch patch) { data[0] = patch; }
            public String nameKey() { return "computer.reikos_signal_tower.turtle"; }
            public boolean sameAs(MobileRadioPeripheral.Host other) { return other == this; }
        };
        MobileRadioPeripheral radio = new MobileRadioPeripheral(host);
        try {
            if (!radio.getType().equals("radio") || radio.getFrequency() != 4627 || !radio.equals((dan200.computercraft.api.peripheral.IPeripheral) radio)) {
                problems.add("peripheral: " + radio.getType() + " " + radio.getFrequency());
            }
            radio.setFrequency(4628);
            if (radio.getFrequency() != 4628 || turtle.getUpgradeItem(data[0]).getOrDefault(ModRegistry.FREQUENCY.get(), 0) != 4628) {
                problems.add("setFrequency: " + data[0]);
            }
        } catch (dan200.computercraft.api.lua.LuaException e) {
            problems.add("Lua: " + e.getMessage());
        }
        return problems;
    }

    /** For the GameTests: the peripheral type CC: Tweaked finds on the block at {@code pos}, or null. */
    public static String typeAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        var peripheral = level.getCapability(PeripheralCapability.get(), pos, net.minecraft.core.Direction.NORTH);
        return peripheral == null ? null : peripheral.getType();
    }
}
