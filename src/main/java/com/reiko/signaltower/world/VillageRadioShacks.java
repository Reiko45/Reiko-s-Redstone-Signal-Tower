package com.reiko.signaltower.world;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * v0.57.0: village radio shacks. Each of the five village types gets a small shack among its houses:
 * a Radio Telegraph (a Radio Operator's workstation) and a bed inside, a villager to live there, and
 * outside a short tower on the air, with a charged battery and a solar panel, an aviation beacon on
 * top and a "Radio Station" sign by the door. Its transmitter is a {@code village_station}: on its
 * first tick it picks a free frequency of its own and writes it on the sign.
 * <p>
 * The templates ({@code reikos_signal_tower:village/<type>/radio_shack}) are added to the vanilla
 * pools ({@code minecraft:village/<type>/houses}) when the server starts, so other mods' changes to
 * those pools stay.
 */
public final class VillageRadioShacks {
    public static final List<String> TYPES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    private static final ResourceKey<StructureProcessorList> NO_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty"));

    private VillageRadioShacks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(VillageRadioShacks::onServerAboutToStart);
    }

    public static ResourceLocation template(String type) {
        return ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "village/" + type + "/radio_shack");
    }

    public static ResourceLocation housesPool(String type) {
        return ResourceLocation.withDefaultNamespace("village/" + type + "/houses");
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        int added = addToVillages(event.getServer().registryAccess(), Config.VILLAGE_RADIO_SHACKS.get());
        if (added > 0) {
            SignalTower.LOGGER.debug("Added radio shacks to {} village house pools", added);
        }
    }

    /** Adds the shacks to each village type's houses, {@code weight} times each. Returns the pools changed. */
    public static int addToVillages(RegistryAccess access, int weight) {
        if (weight <= 0) {
            return 0;
        }
        Registry<StructureTemplatePool> pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = access.registryOrThrow(Registries.PROCESSOR_LIST);
        int changed = 0;
        for (String type : TYPES) {
            StructureTemplatePool pool = pools.get(housesPool(type));
            if (pool == null || count(pool, type) > 0) {
                continue;
            }
            // Plains houses get vanilla's moss (one cobblestone in ten); the others none.
            Holder<StructureProcessorList> list = processors.getHolder(type.equals("plains") ? ProcessorLists.MOSSIFY_10_PERCENT : NO_PROCESSORS)
                    .map(holder -> (Holder<StructureProcessorList>) holder)
                    .orElse(null);
            if (list == null) {
                continue;
            }
            StructurePoolElement shack = StructurePoolElement.legacy(template(type).toString(), list).apply(StructureTemplatePool.Projection.RIGID);
            ObjectArrayList<StructurePoolElement> templates = templates(pool);
            if (templates == null) {
                return changed;
            }
            for (int i = 0; i < weight; i++) {
                templates.add(shack);
            }
            changed++;
        }
        return changed;
    }

    /** How many times a pool holds the {@code type} radio shack (its weight). */
    public static int count(StructureTemplatePool pool, String type) {
        ObjectArrayList<StructurePoolElement> templates = templates(pool);
        if (templates == null) {
            return 0;
        }
        String id = template(type).toString();
        int count = 0;
        for (StructurePoolElement element : templates) {
            if (element.toString().contains(id)) {
                count++;
            }
        }
        return count;
    }

    /** The pool's list of elements, one entry per weight (vanilla keeps it private). */
    @SuppressWarnings("unchecked")
    private static ObjectArrayList<StructurePoolElement> templates(StructureTemplatePool pool) {
        try {
            for (Field field : StructureTemplatePool.class.getDeclaredFields()) {
                if (field.getType() == ObjectArrayList.class) {
                    field.setAccessible(true);
                    return (ObjectArrayList<StructurePoolElement>) field.get(pool);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            SignalTower.LOGGER.warn("Couldn't add radio shacks to villages", e);
        }
        return null;
    }
}
