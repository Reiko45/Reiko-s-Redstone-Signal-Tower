package com.reiko.signaltower.world;

import com.google.common.collect.ImmutableSet;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.registry.ModRegistry;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * v0.56.0: the Radio Operator. A villager with no job who finds a Radio Telegraph takes it as their
 * workstation. They buy redstone and copper, and sell masts, tuners, receivers and transmitters; as
 * they level up, platforms, batteries, better antennas, and at Master a Diamond Antenna.
 */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SignalTower.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, SignalTower.MOD_ID);

    public static final ResourceKey<PoiType> RADIO_OPERATOR_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "radio_operator"));

    /** Every Radio Telegraph (any facing, keyed or not) is a Radio Operator's workstation. */
    public static final DeferredHolder<PoiType, PoiType> RADIO_OPERATOR_POI = POI_TYPES.register("radio_operator",
            () -> new PoiType(ImmutableSet.copyOf(ModRegistry.RADIO_TELEGRAPH.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> RADIO_OPERATOR = PROFESSIONS.register("radio_operator",
            () -> new VillagerProfession("radio_operator", holder -> holder.is(RADIO_OPERATOR_POI_KEY), holder -> holder.is(RADIO_OPERATOR_POI_KEY),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.NOTE_BLOCK_HAT.value()));

    private ModVillagers() {
    }

    public static void register(IEventBus modEventBus) {
        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(ModVillagers::onVillagerTrades);
    }

    private static ItemStack stack(ItemLike item, int count) {
        return new ItemStack(item, count);
    }

    /** What a Radio Operator trades, by level (1 Novice ... 5 Master). */
    public static void addTrades(it.unimi.dsi.fastutil.ints.Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        // Novice: they buy redstone and copper, and sell masts and a tuner.
        trades.get(1).add(new BasicItemListing(stack(Items.REDSTONE, 12), stack(Items.EMERALD, 1), 16, 2, 0.05F));
        trades.get(1).add(new BasicItemListing(stack(Items.COPPER_INGOT, 8), stack(Items.EMERALD, 1), 16, 2, 0.05F));
        trades.get(1).add(new BasicItemListing(1, stack(ModRegistry.RADIO_MAST_ITEM.get(), 4), 16, 1, 0.05F));
        trades.get(1).add(new BasicItemListing(3, stack(ModRegistry.FREQUENCY_TUNER.get(), 1), 12, 1, 0.05F));
        // Apprentice: receivers, transmitters and guy wire anchors.
        trades.get(2).add(new BasicItemListing(stack(Items.IRON_INGOT, 4), stack(Items.EMERALD, 1), 12, 10, 0.05F));
        trades.get(2).add(new BasicItemListing(4, stack(ModRegistry.RADIO_RECEIVER_ITEM.get(), 1), 12, 5, 0.05F));
        trades.get(2).add(new BasicItemListing(5, stack(ModRegistry.RADIO_TRANSMITTER_ITEM.get(), 1), 12, 5, 0.05F));
        trades.get(2).add(new BasicItemListing(3, stack(ModRegistry.GUY_ANCHOR_ITEM.get(), 3), 12, 5, 0.05F));
        // Journeyman: an iron antenna, platforms, a battery and a scanner.
        trades.get(3).add(new BasicItemListing(6, stack(ModRegistry.IRON_ANTENNA.get(), 1), 8, 10, 0.05F));
        trades.get(3).add(new BasicItemListing(3, stack(ModRegistry.TOWER_PLATFORM_ITEM.get(), 8), 12, 10, 0.05F));
        trades.get(3).add(new BasicItemListing(8, stack(ModRegistry.RADIO_BATTERY_ITEM.get(), 1), 8, 10, 0.05F));
        trades.get(3).add(new BasicItemListing(7, stack(ModRegistry.SIGNAL_SCANNER.get(), 1), 6, 10, 0.05F));
        // Expert: a gold antenna, a solar panel and a pager.
        trades.get(4).add(new BasicItemListing(14, stack(ModRegistry.GOLD_ANTENNA.get(), 1), 6, 15, 0.05F));
        trades.get(4).add(new BasicItemListing(10, stack(ModRegistry.SOLAR_PANEL_ITEM.get(), 1), 8, 15, 0.05F));
        trades.get(4).add(new BasicItemListing(8, stack(ModRegistry.PAGER.get(), 1), 6, 15, 0.05F));
        // Master: a diamond antenna (emeralds and a diamond) and a coverage map.
        trades.get(5).add(new BasicItemListing(stack(Items.EMERALD, 24), stack(Items.DIAMOND, 1),
                stack(ModRegistry.DIAMOND_ANTENNA.get(), 1), 3, 30, 0.05F));
        trades.get(5).add(new BasicItemListing(12, stack(ModRegistry.COVERAGE_MAP.get(), 1), 6, 30, 0.05F));
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == RADIO_OPERATOR.get()) {
            addTrades(event.getTrades());
        }
    }
}
