package com.reiko.signaltower.world;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioBatteryBlock;
import com.reiko.signaltower.block.TowerPlatformBlock;
import com.reiko.signaltower.blockentity.RadioBatteryBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.radio.AntennaTier;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * v0.55.0: an old radio tower in the world, one piece: a stone and spruce hut with a chest of parts,
 * a sign with the tower's frequency, and beside it a mast column with a transmitter on top. One still
 * on the air has a charged battery with a solar panel, a redstone torch at its foot, an aviation
 * beacon on top, three guy wires and a platform round its mast just under the top; one off the air has a lever
 * instead (off), and maybe a flat battery (its solar panel gone), fewer guy wires and no platform.
 * <p>
 * Laid out in its own coordinates (x 0-11, z 0-8, y 0 = the ground) and turned by {@link #rotation}
 * about {@link #origin}; block states turn with it.
 */
public class RadioTowerPiece extends StructurePiece {
    public static final int WIDTH = 12;
    public static final int DEPTH = 9;
    /** Where the mast stands, in the piece's own coordinates. */
    public static final int MAST_X = 7;
    public static final int MAST_Z = 4;
    private static final int[][] ANCHORS = {{11, 4}, {5, 0}, {5, 8}};

    /**
     * What this tower is like, decided once when the structure starts (so every chunk it covers
     * builds the same tower).
     */
    public record Plan(int masts, boolean onAir, int frequency, AntennaTier antenna, int anchors, boolean platform,
                       boolean battery, int charge) {
        public static Plan random(RandomSource random) {
            boolean onAir = random.nextFloat() < 0.6F;
            int masts = 6 + random.nextInt(5);
            int frequency = RadioTransmitterBlockEntity.FOUND_FREQUENCY_MIN + random.nextInt(RadioTransmitterBlockEntity.FOUND_FREQUENCY_SPAN);
            AntennaTier antenna = random.nextFloat() < 0.3F ? AntennaTier.IRON : AntennaTier.NONE;
            int anchors = onAir ? 3 : random.nextInt(3);
            boolean platform = onAir || random.nextBoolean();
            boolean battery = onAir || random.nextFloat() < 0.7F;
            int charge = onAir ? 30_000 + random.nextInt(50_001) : 0;
            return new Plan(masts, onAir, frequency, antenna, anchors, platform, battery, charge);
        }

        /** Its height in blocks, ground to beacon. */
        public int height() {
            return masts + 3;
        }

        void save(CompoundTag tag) {
            tag.putInt("Masts", masts);
            tag.putBoolean("OnAir", onAir);
            tag.putInt("Frequency", frequency);
            tag.putString("Antenna", antenna.getSerializedName());
            tag.putInt("Anchors", anchors);
            tag.putBoolean("Platform", platform);
            tag.putBoolean("Battery", battery);
            tag.putInt("Charge", charge);
        }

        static Plan load(CompoundTag tag) {
            AntennaTier antenna = AntennaTier.NONE;
            for (AntennaTier tier : AntennaTier.values()) {
                if (tier.getSerializedName().equals(tag.getString("Antenna"))) {
                    antenna = tier;
                }
            }
            return new Plan(Math.max(1, tag.getInt("Masts")), tag.getBoolean("OnAir"), tag.getInt("Frequency"), antenna,
                    tag.getInt("Anchors"), tag.getBoolean("Platform"), tag.getBoolean("Battery"), tag.getInt("Charge"));
        }
    }

    private final BlockPos origin;
    private final Rotation rotation;
    private final Plan plan;

    public RadioTowerPiece(BlockPos origin, Rotation rotation, Plan plan) {
        super(ModWorldgen.RADIO_TOWER_PIECE.get(), 0, boxFor(origin, rotation, plan));
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.plan = plan;
    }

    public RadioTowerPiece(CompoundTag tag) {
        super(ModWorldgen.RADIO_TOWER_PIECE.get(), tag);
        this.origin = BlockPos.of(tag.getLong("Origin"));
        Rotation[] rotations = Rotation.values();
        this.rotation = rotations[Math.floorMod(tag.getInt("Rotation"), rotations.length)];
        this.plan = Plan.load(tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("Origin", origin.asLong());
        tag.putInt("Rotation", rotation.ordinal());
        plan.save(tag);
    }

    public Plan plan() {
        return plan;
    }

    public Rotation rotation() {
        return rotation;
    }

    private static BoundingBox boxFor(BlockPos origin, Rotation rotation, Plan plan) {
        return BoundingBox.fromCorners(origin.offset(BlockPos.ZERO.rotate(rotation)),
                origin.offset(new BlockPos(WIDTH - 1, plan.height() - 1, DEPTH - 1).rotate(rotation)));
    }

    /** Where (x, y, z) in the piece's own coordinates is in the world. */
    public BlockPos at(int x, int y, int z) {
        return origin.offset(new BlockPos(x, y, z).rotate(rotation));
    }

    /** The foot of its mast (the lowest mast), in the world. */
    public BlockPos mastFoot() {
        return at(MAST_X, 1, MAST_Z);
    }

    /** Its transmitter, in the world. */
    public BlockPos top() {
        return at(MAST_X, plan.masts() + 1, MAST_Z);
    }

    /** The hut's sign, in the world. */
    public BlockPos sign() {
        return at(5, 2, 3);
    }

    /** The hut's chest, in the world. */
    public BlockPos chest() {
        return at(1, 1, 5);
    }

    /** What the hut's sign says for a tower on {@code frequency}. */
    public static SignText signText(int frequency) {
        String key = "sign." + SignalTower.MOD_ID + ".radio_tower.";
        return new SignText()
                .setMessage(0, Component.translatable(key + "station"))
                .setMessage(1, Component.translatable(key + "frequency"))
                .setMessage(2, Component.literal(Integer.toString(frequency)))
                .setMessage(3, Component.empty());
    }

    private void put(WorldGenLevel level, BoundingBox box, BlockState state, int x, int y, int z) {
        BlockPos pos = at(x, y, z);
        if (box.isInside(pos)) {
            level.setBlock(pos, state.rotate(rotation), 2);
        }
    }

    /** Fills the ground under (x, z) with {@code state} down to whatever it stands on. */
    private void footing(WorldGenLevel level, BoundingBox box, BlockState state, int x, int z) {
        BlockPos.MutableBlockPos pos = at(x, -1, z).mutable();
        if (!box.isInside(pos.getX(), box.minY(), pos.getZ())) {
            return;
        }
        for (int i = 0; i < 24 && pos.getY() > level.getMinBuildHeight() + 1; i++) {
            BlockState below = level.getBlockState(pos);
            if (!(below.isAir() || below.liquid() || below.canBeReplaced())) {
                return;
            }
            level.setBlock(pos, state, 2);
            pos.move(Direction.DOWN);
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();

        // The ground: level, clear above, filled below.
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                boolean hut = x <= 4 && z >= 2 && z <= 6;
                for (int y = 1; y <= 4; y++) {
                    put(level, box, air, x, y, z);
                }
                if (hut) {
                    put(level, box, (x + z) % 3 == 0 ? mossy : bricks, x, 0, z);
                    footing(level, box, cobble, x, z);
                } else {
                    BlockPos ground = at(x, 0, z);
                    if (box.isInside(ground)) {
                        BlockState here = level.getBlockState(ground);
                        if (here.isAir() || here.liquid() || here.canBeReplaced()) {
                            put(level, box, grass, x, 0, z);
                        }
                    }
                    footing(level, box, dirt, x, z);
                }
            }
        }

        // The hut: spruce walls on stone bricks, log corners, a doorway facing the mast, a flat roof.
        for (int x = 0; x <= 4; x++) {
            for (int z = 2; z <= 6; z++) {
                boolean edgeX = x == 0 || x == 4;
                boolean edgeZ = z == 2 || z == 6;
                for (int y = 1; y <= 3; y++) {
                    if (edgeX && edgeZ) {
                        put(level, box, log, x, y, z);
                    } else if (edgeX || edgeZ) {
                        put(level, box, y == 1 ? cobble : planks, x, y, z);
                    }
                }
                put(level, box, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), x, 4, z);
            }
        }
        put(level, box, air, 4, 1, 4); // the doorway
        put(level, box, air, 4, 2, 4);
        put(level, box, Blocks.GLASS.defaultBlockState(), 0, 2, 4); // windows
        put(level, box, Blocks.GLASS.defaultBlockState(), 2, 2, 6);
        put(level, box, Blocks.GLASS.defaultBlockState(), 2, 2, 2);
        put(level, box, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2, 3, 4);
        put(level, box, Blocks.CRAFTING_TABLE.defaultBlockState(), 1, 1, 3);
        put(level, box, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), 1, 1, 5);
        BlockPos chest = chest();
        if (box.isInside(chest)) {
            RandomizableContainer.setBlockEntityLootTable(level, random, chest, ModWorldgen.TOWER_CHEST);
        }
        // The sign by the door, with the tower's frequency.
        put(level, box, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.EAST), 5, 2, 3);
        BlockPos sign = sign();
        if (box.isInside(sign) && level.getBlockEntity(sign) instanceof SignBlockEntity signEntity) {
            CompoundTag text = new CompoundTag();
            SignText.DIRECT_CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), signText(plan.frequency()))
                    .result().ifPresent(encoded -> text.put("front_text", encoded));
            text.putBoolean("is_waxed", true);
            signEntity.loadCustomOnly(text, level.registryAccess());
        }

        // The tower: a stone base, the mast column, the transmitter on top.
        put(level, box, bricks, MAST_X, 0, MAST_Z);
        for (int y = 1; y <= plan.masts(); y++) {
            put(level, box, ModRegistry.RADIO_MAST.get().defaultBlockState(), MAST_X, y, MAST_Z);
        }
        put(level, box, ModRegistry.RADIO_TRANSMITTER.get().defaultBlockState().setValue(AntennaTier.PROPERTY, plan.antenna()),
                MAST_X, plan.masts() + 1, MAST_Z);
        BlockPos top = top();
        if (box.isInside(top) && level.getBlockEntity(top) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.markFound(plan.frequency(), sign);
        }
        if (plan.onAir()) {
            put(level, box, ModRegistry.AVIATION_BEACON.get().defaultBlockState(), MAST_X, plan.masts() + 2, MAST_Z);
            put(level, box, Blocks.REDSTONE_TORCH.defaultBlockState(), MAST_X - 1, 1, MAST_Z);
        } else {
            put(level, box, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, Direction.EAST), MAST_X - 1, 1, MAST_Z);
        }
        if (plan.battery()) {
            int capacity = com.reiko.signaltower.radio.PowerCost.capacity();
            put(level, box, ModRegistry.RADIO_BATTERY.get().defaultBlockState()
                    .setValue(RadioBatteryBlock.CHARGE, RadioBatteryBlockEntity.bars(plan.charge(), capacity)), MAST_X + 1, 1, MAST_Z);
            BlockPos battery = at(MAST_X + 1, 1, MAST_Z);
            if (box.isInside(battery) && level.getBlockEntity(battery) instanceof RadioBatteryBlockEntity charged) {
                charged.setCharge(plan.charge());
            }
            if (plan.onAir()) {
                put(level, box, ModRegistry.SOLAR_PANEL.get().defaultBlockState(), MAST_X + 1, 2, MAST_Z); // gone from one off the air
            }
        }
        for (int i = 0; i < Math.min(plan.anchors(), ANCHORS.length); i++) {
            put(level, box, ModRegistry.GUY_ANCHOR.get().defaultBlockState(), ANCHORS[i][0], 1, ANCHORS[i][1]);
        }
        if (plan.platform()) {
            // A ring round the mast one below the top (standing on it, your head clears the
            // transmitter, which you can then reach), rails worked out from the ring itself (not the
            // world, so it comes out the same whichever chunk is built first).
            BlockPos mast = at(MAST_X, plan.masts() - 1, MAST_Z);
            Set<BlockPos> ring = new HashSet<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ring.add(mast.offset(dx, 0, dz));
                }
            }
            BlockState platform = ModRegistry.TOWER_PLATFORM.get().defaultBlockState();
            for (BlockPos cell : ring) {
                if (cell.equals(mast) || !box.isInside(cell)) {
                    continue;
                }
                BlockState state = platform;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    state = state.setValue(TowerPlatformBlock.railOn(direction), !ring.contains(cell.relative(direction)));
                }
                level.setBlock(cell, state, 2);
            }
        }
    }

    /** For the GameTests: builds the whole tower in {@code level} at once. */
    public void buildAll(WorldGenLevel level, ChunkGenerator generator, RandomSource random) {
        postProcess(level, null, generator, random, getBoundingBox(), new ChunkPos(origin), origin);
    }

    /** Where to put the piece so its middle is at {@code middle} (its ground at y). */
    public static BlockPos originFor(BlockPos middle, Rotation rotation) {
        return middle.subtract(new BlockPos(WIDTH / 2, 0, DEPTH / 2).rotate(rotation));
    }

    /** True if (x, z) in the piece's own coordinates is part of the footprint it builds on. */
    public static boolean inFootprint(int x, int z) {
        return x >= 0 && x < WIDTH && z >= 0 && z < DEPTH;
    }

    @Nullable
    public BlockPos anchor(int index) {
        return index < plan.anchors() && index < ANCHORS.length ? at(ANCHORS[index][0], 1, ANCHORS[index][1]) : null;
    }
}
