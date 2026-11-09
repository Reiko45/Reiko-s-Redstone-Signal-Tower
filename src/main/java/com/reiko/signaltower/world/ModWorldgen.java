package com.reiko.signaltower.world;

import com.reiko.signaltower.SignalTower;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** v0.55.0: the old radio towers that generate in the world (the structure itself is data: see make_data.py). */
public final class ModWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, SignalTower.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, SignalTower.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<RadioTowerStructure>> RADIO_TOWER =
            STRUCTURE_TYPES.register("radio_tower", () -> (StructureType<RadioTowerStructure>) () -> RadioTowerStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RADIO_TOWER_PIECE =
            STRUCTURE_PIECES.register("radio_tower", () -> (StructurePieceType.ContextlessType) RadioTowerPiece::new);

    public static final ResourceKey<Structure> RADIO_TOWER_STRUCTURE = ResourceKey.create(Registries.STRUCTURE, id("radio_tower"));
    public static final ResourceKey<StructureSet> RADIO_TOWERS = ResourceKey.create(Registries.STRUCTURE_SET, id("radio_towers"));
    public static final TagKey<Biome> HAS_RADIO_TOWER = TagKey.create(Registries.BIOME, id("has_structure/radio_tower"));
    public static final ResourceKey<LootTable> TOWER_CHEST = ResourceKey.create(Registries.LOOT_TABLE, id("chests/radio_tower"));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, path);
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
    }

    private ModWorldgen() {}
}
