package com.reiko.signaltower.world;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * v0.55.0: where an old radio tower can go. On dry, fairly level ground (no more than
 * {@link #MAX_SLOPE} blocks between the corners of its site) in the biomes its tag lists; the
 * structure set spaces them out. Off with {@code worldTowers = false}.
 */
public class RadioTowerStructure extends Structure {
    public static final MapCodec<RadioTowerStructure> CODEC = simpleCodec(RadioTowerStructure::new);
    public static final int MAX_SLOPE = 6;

    public RadioTowerStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        if (!enabled()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        ChunkGenerator generator = context.chunkGenerator();
        int middleX = chunk.getMiddleBlockX();
        int middleZ = chunk.getMiddleBlockZ();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int sum = 0;
        int[][] samples = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {0, 0}};
        for (int[] at : samples) {
            int y = generator.getFirstOccupiedHeight(middleX + at[0], middleZ + at[1], Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            int floor = generator.getFirstOccupiedHeight(middleX + at[0], middleZ + at[1], Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            if (y != floor) {
                return Optional.empty(); // water there
            }
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
            sum += y;
        }
        if (highest - lowest > MAX_SLOPE || lowest < context.heightAccessor().getMinBuildHeight()) {
            return Optional.empty();
        }
        int ground = Math.round(sum / (float) samples.length);
        NoiseColumn column = generator.getBaseColumn(middleX, middleZ, context.heightAccessor(), context.randomState());
        if (!column.getBlock(ground).getFluidState().isEmpty()) {
            return Optional.empty();
        }
        WorldgenRandom random = context.random();
        Rotation rotation = Rotation.getRandom(random);
        RadioTowerPiece.Plan plan = RadioTowerPiece.Plan.random(random);
        BlockPos middle = new BlockPos(middleX, ground, middleZ);
        return Optional.of(new Structure.GenerationStub(middle,
                builder -> builder.addPiece(new RadioTowerPiece(RadioTowerPiece.originFor(middle, rotation), rotation, plan))));
    }

    private static boolean enabled() {
        try {
            return Config.WORLD_TOWERS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.RADIO_TOWER.get();
    }
}
