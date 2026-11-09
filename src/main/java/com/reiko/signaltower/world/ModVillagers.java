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
                    ImmutableSet.of(), ImmutableSet.of(), com.reiko.signaltower.registry.ModSounds.TELEGRAPH_KEY.get())); // v0.59.0: works its telegraph's key

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

    /**
     * One of the Radio Operator's trades: at {@code level} (1 Novice ... 5 Master) they take
     * {@code costA} (and {@code costB}, if not empty) for {@code result}. v0.57.0: also what JEI and
     * EMI show.
     */
    public record Trade(int level, ItemStack costA, ItemStack costB, ItemStack result, int maxUses, int xp) {
        public static final float PRICE_MULTIPLIER = 0.05F;

        static Trade buy(int level, ItemLike item, int count, int maxUses, int xp) {
            return new Trade(level, stack(item, count), ItemStack.EMPTY, stack(Items.EMERALD, 1), maxUses, xp);
        }

        static Trade sell(int level, int emeralds, ItemLike item, int count, int maxUses, int xp) {
            return new Trade(level, stack(Items.EMERALD, emeralds), ItemStack.EMPTY, stack(item, count), maxUses, xp);
        }

        public VillagerTrades.ItemListing listing() {
            return costB.isEmpty()
                    ? new BasicItemListing(costA.copy(), result.copy(), maxUses, xp, PRICE_MULTIPLIER)
                    : new BasicItemListing(costA.copy(), costB.copy(), result.copy(), maxUses, xp, PRICE_MULTIPLIER);
        }
    }

    /** Everything a Radio Operator trades, Novice first. */
    public static List<Trade> trades() {
        return List.of(
                // Novice: they buy redstone and copper, and sell masts and a tuner.
                Trade.buy(1, Items.REDSTONE, 12, 16, 2),
                Trade.buy(1, Items.COPPER_INGOT, 8, 16, 2),
                Trade.sell(1, 1, ModRegistry.RADIO_MAST_ITEM.get(), 4, 16, 1),
                Trade.sell(1, 3, ModRegistry.FREQUENCY_TUNER.get(), 1, 12, 1),
                // Apprentice: receivers, transmitters and guy wire anchors.
                Trade.buy(2, Items.IRON_INGOT, 4, 12, 10),
                Trade.sell(2, 4, ModRegistry.RADIO_RECEIVER_ITEM.get(), 1, 12, 5),
                Trade.sell(2, 5, ModRegistry.RADIO_TRANSMITTER_ITEM.get(), 1, 12, 5),
                Trade.sell(2, 3, ModRegistry.GUY_ANCHOR_ITEM.get(), 3, 12, 5),
                // Journeyman: an iron antenna, platforms, a battery and a scanner.
                Trade.sell(3, 6, ModRegistry.IRON_ANTENNA.get(), 1, 8, 10),
                Trade.sell(3, 3, ModRegistry.TOWER_PLATFORM_ITEM.get(), 8, 12, 10),
                Trade.sell(3, 8, ModRegistry.RADIO_BATTERY_ITEM.get(), 1, 8, 10),
                Trade.sell(3, 7, ModRegistry.SIGNAL_SCANNER.get(), 1, 6, 10),
                // Expert: a gold antenna, a solar panel and a pager.
                Trade.sell(4, 14, ModRegistry.GOLD_ANTENNA.get(), 1, 6, 15),
                Trade.sell(4, 10, ModRegistry.SOLAR_PANEL_ITEM.get(), 1, 8, 15),
                Trade.sell(4, 8, ModRegistry.PAGER.get(), 1, 6, 15),
                // Master: a diamond antenna (emeralds and a diamond) and a coverage map.
                new Trade(5, stack(Items.EMERALD, 24), stack(Items.DIAMOND, 1), stack(ModRegistry.DIAMOND_ANTENNA.get(), 1), 3, 30),
                Trade.sell(5, 12, ModRegistry.COVERAGE_MAP.get(), 1, 6, 30));
    }

    /** What a Radio Operator trades, by level (1 Novice ... 5 Master). */
    public static void addTrades(it.unimi.dsi.fastutil.ints.Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        for (Trade trade : trades()) {
            trades.get(trade.level()).add(trade.listing());
        }
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == RADIO_OPERATOR.get()) {
            addTrades(event.getTrades());
        }
    }
}
