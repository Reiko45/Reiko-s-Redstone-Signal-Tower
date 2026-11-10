package com.reiko.signaltower.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.reiko.signaltower.radio.TvCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws what a TV Camera sees (v0.66.0), on the player's own client, from the blocks and entities
 * the client already has: one ray per pixel from the camera's lens through the blocks (64 at most),
 * coloured by the block's own texture where it's hit (tinted like grass and leaves are), lit by the
 * light there (sky light dimmed at night) and shaded by which face it is; see-through pixels (glass,
 * leaves) let the ray go on; water tints what's under it; far away it fades into the sky. Mobs and
 * players are boxes in their spawn egg's colours. Nothing here touches the game's own renderer.
 * <p>
 * A picture is drawn a few rows at a time ({@link #rows}), so that a frame's work stays small.
 */
public final class CameraRaycaster {
    public static final int WIDTH = 128;
    public static final int HEIGHT = 72;
    private static final double MAX_DISTANCE = TvCamera.VIEW_DISTANCE;
    private static final float[] FACE_SHADE = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F}; // down, up, north, south, west, east
    private static final int MAX_ENTITIES = 48;

    /** One painted layer of a block face: the texture's pixels (ARGB) and its tint, or -1. */
    private record Layer(int[] pixels, int width, int height, int tint) {
    }

    private static final Layer[] NO_LAYERS = new Layer[0];
    private static final Map<BlockState, Layer[][]> LAYERS = new IdentityHashMap<>();
    private static final Map<TextureAtlasSprite, int[]> PIXELS = new IdentityHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    private CameraRaycaster() {
    }

    /** After a resource reload the textures are new. */
    public static void clearCaches() {
        LAYERS.clear();
        PIXELS.clear();
    }

    /** Everything needed to draw one picture: where it looks from, the light, the sky, and the entities near. */
    public static final class Shot {
        final ClientLevel level;
        final BlockPos camera;
        /** v0.67.0: an entity left out of the picture (the drone it's taken from), or -1. */
        final int skipEntity;
        final Vec3 eye;
        final Vec3 forward;
        final Vec3 right;
        final Vec3 up;
        final double tanHalf;
        final boolean night;
        final float skyBright;
        final int skyTop;
        final int horizon;
        final int below;
        final boolean eyeInWater;
        final List<Target> targets = new ArrayList<>();
        // Per-ray scratch.
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
        double hitT;
        LevelChunk chunk;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ = Integer.MIN_VALUE;

        Shot(ClientLevel level, BlockPos camera, TvCamera.Pose pose, boolean night, float partialTick, int skipEntity) {
            this.level = level;
            this.camera = camera.immutable();
            this.skipEntity = skipEntity;
            this.eye = pose.eye();
            this.forward = pose.forward();
            Vec3 r = forward.cross(new Vec3(0, 1, 0));
            this.right = r.lengthSqr() < 1.0E-6 ? new Vec3(-1, 0, 0) : r.normalize();
            this.up = right.cross(forward).normalize();
            this.tanHalf = Math.tan(Math.toRadians(pose.fov()) / 2.0);
            this.night = night;
            this.skyBright = level.getSkyDarken(partialTick);
            this.eyeInWater = level.getFluidState(BlockPos.containing(eye)).is(FluidTags.WATER);
            if (level.dimensionType().hasSkyLight()) {
                Vec3 sky = level.getSkyColor(eye, partialTick);
                int fog = level.getBiome(BlockPos.containing(eye)).value().getFogColor();
                float day = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI) * 2.0F + 0.5F, 0.0F, 1.0F);
                int skyRgb = rgb(sky.x, sky.y, sky.z);
                int fogRgb = scale(fog, Math.max(0.06F, day));
                boolean underground = level.getBrightness(LightLayer.SKY, BlockPos.containing(eye)) == 0;
                this.skyTop = underground ? 0x050505 : skyRgb;
                this.horizon = underground ? 0x080808 : mix(skyRgb, fogRgb, 0.65F);
                this.below = underground ? 0x050505 : scale(mix(skyRgb, fogRgb, 0.65F), 0.75F);
            } else {
                int fog = level.getBiome(BlockPos.containing(eye)).value().getFogColor();
                this.skyTop = scale(fog, 0.55F);
                this.horizon = scale(fog, 0.7F);
                this.below = scale(fog, 0.45F);
            }
            gatherEntities(partialTick);
        }

        private void gatherEntities(float partialTick) {
            double reach2 = MAX_DISTANCE * MAX_DISTANCE;
            for (Entity entity : level.entitiesForRendering()) {
                if (targets.size() >= MAX_ENTITIES) {
                    break;
                }
                int kind = kindOf(entity);
                if (kind < 0 || entity.isInvisible() || entity.getId() == skipEntity) {
                    continue;
                }
                Vec3 at = entity.getPosition(partialTick);
                if (at.distanceToSqr(eye) > reach2) {
                    continue;
                }
                AABB box = entity.getBoundingBox().move(at.subtract(entity.position()));
                int[] rect = screenRect(box);
                if (rect != null) {
                    targets.add(new Target(box, rect, colors(entity, kind), kind == 0));
                }
            }
        }

        /** Where {@code box} is on the picture (pixels: x1, y1, x2, y2), or null if it isn't in front. */
        private int[] screenRect(AABB box) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            boolean behind = false;
            int inFront = 0;
            for (int i = 0; i < 8; i++) {
                Vec3 corner = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
                Vec3 d = corner.subtract(eye);
                double z = d.dot(forward);
                if (z < 0.05) {
                    behind = true;
                    continue;
                }
                inFront++;
                double sx = d.dot(right) / (z * tanHalf * WIDTH / HEIGHT);
                double sy = d.dot(up) / (z * tanHalf);
                minX = Math.min(minX, sx);
                maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy);
                maxY = Math.max(maxY, sy);
            }
            if (inFront == 0) {
                return null;
            }
            if (behind) {
                return new int[]{0, 0, WIDTH - 1, HEIGHT - 1}; // partly behind the lens: test it everywhere
            }
            int x1 = (int) Math.floor((minX + 1) / 2 * WIDTH) - 1;
            int x2 = (int) Math.ceil((maxX + 1) / 2 * WIDTH) + 1;
            int y1 = (int) Math.floor((1 - maxY) / 2 * HEIGHT) - 1;
            int y2 = (int) Math.ceil((1 - minY) / 2 * HEIGHT) + 1;
            if (x2 < 0 || y2 < 0 || x1 >= WIDTH || y1 >= HEIGHT) {
                return null;
            }
            return new int[]{Math.max(0, x1), Math.max(0, y1), Math.min(WIDTH - 1, x2), Math.min(HEIGHT - 1, y2)};
        }

        BlockState stateAt(int x, int y, int z) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return Blocks.AIR.defaultBlockState();
            }
            int cx = x >> 4, cz = z >> 4;
            if (cx != chunkX || cz != chunkZ || chunk == null) {
                chunk = level.getChunkSource().getChunk(cx, cz, false);
                chunkX = cx;
                chunkZ = cz;
            }
            if (chunk == null) {
                return Blocks.AIR.defaultBlockState();
            }
            LevelChunkSection[] sections = chunk.getSections();
            int index = level.getSectionIndex(y);
            if (index < 0 || index >= sections.length) {
                return Blocks.AIR.defaultBlockState();
            }
            LevelChunkSection section = sections[index];
            return section.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : section.getBlockState(x & 15, y & 15, z & 15);
        }
    }

    /** An entity on the picture: its box, where it is on the picture, its colours (head, body, legs). */
    private record Target(AABB box, int[] rect, int[] colors, boolean player) {
    }

    /** 0 player, 1 other mob, 2 item, 3 vehicle, 4 hanging, 5 falling block or TNT; -1 not drawn. */
    private static int kindOf(Entity entity) {
        if (entity instanceof Player) return 0;
        if (entity instanceof LivingEntity) return 1;
        if (entity instanceof ItemEntity) return 2;
        if (entity instanceof VehicleEntity || entity instanceof com.reiko.signaltower.entity.RadioDroneEntity) return 3;
        if (entity instanceof HangingEntity) return 4;
        if (entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) return 5;
        return -1;
    }

    private static int[] colors(Entity entity, int kind) {
        switch (kind) {
            case 0 -> {
                return new int[]{0xC69C6D, 0x2FA8A8, 0x3A3AA8}; // skin, shirt, trousers
            }
            case 1 -> {
                SpawnEggItem egg = SpawnEggItem.byId(entity.getType());
                if (egg != null) {
                    return new int[]{egg.getColor(1), egg.getColor(0), egg.getColor(0)};
                }
                int hash = 0x606060 | (entity.getType().hashCode() & 0x3F3F3F);
                return new int[]{hash, hash, hash};
            }
            case 2 -> {
                int c = ((ItemEntity) entity).getItem().getItem() instanceof BlockItem block
                        ? block.getBlock().defaultMapColor().col : 0xD8D8D8;
                return new int[]{c, c, c};
            }
            case 3 -> {
                int c = entity instanceof net.minecraft.world.entity.vehicle.Boat ? 0x8B6B3E : 0x6E6E6E;
                return new int[]{c, c, c};
            }
            case 4 -> {
                return new int[]{0x8B6B3E, 0x8B6B3E, 0x8B6B3E};
            }
            default -> {
                int c = entity instanceof FallingBlockEntity falling ? falling.getBlockState().getMapColor(entity.level(), entity.blockPosition()).col
                        : 0xD03020;
                return new int[]{c, c, c};
            }
        }
    }

    /** Sets up a picture of the camera at {@code camera} ({@code pose}) at this moment. */
    public static Shot shot(ClientLevel level, BlockPos camera, TvCamera.Pose pose, boolean night, float partialTick) {
        return new Shot(level, camera, pose, night, partialTick, -1);
    }

    /** v0.67.0: a picture from a Radio Drone's camera (no block of its own; the drone itself left out). */
    public static Shot droneShot(ClientLevel level, int droneId, TvCamera.Pose pose, boolean night, float partialTick) {
        return new Shot(level, new BlockPos(0, Integer.MIN_VALUE / 2, 0), pose, night, partialTick, droneId);
    }

    /** Draws rows {@code from} (inclusive) to {@code to} (exclusive) of {@code shot} into {@code out} (ARGB, WIDTH × HEIGHT). */
    public static void rows(Shot shot, int[] out, int from, int to) {
        double aspect = (double) WIDTH / HEIGHT;
        for (int py = from; py < to; py++) {
            double ny = (1.0 - (py + 0.5) / HEIGHT * 2.0) * shot.tanHalf;
            for (int px = 0; px < WIDTH; px++) {
                double nx = ((px + 0.5) / WIDTH * 2.0 - 1.0) * shot.tanHalf * aspect;
                double dx = shot.forward.x + shot.right.x * nx + shot.up.x * ny;
                double dy = shot.forward.y + shot.right.y * nx + shot.up.y * ny;
                double dz = shot.forward.z + shot.right.z * nx + shot.up.z * ny;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int color = trace(shot, px, py, dx / length, dy / length, dz / length);
                out[py * WIDTH + px] = 0xFF000000 | (shot.night ? nightVision(color) : color);
            }
        }
    }

    // ---- One ray ----

    private static int trace(Shot shot, int px, int py, double dx, double dy, double dz) {
        double ox = shot.eye.x, oy = shot.eye.y, oz = shot.eye.z;
        // The nearest entity on this ray.
        double entityT = MAX_DISTANCE;
        int entityColor = 0;
        for (Target target : shot.targets) {
            int[] r = target.rect();
            if (px < r[0] || px > r[2] || py < r[1] || py > r[3]) {
                continue;
            }
            double t = slab(target.box(), ox, oy, oz, dx, dy, dz);
            if (t >= 0 && t < entityT) {
                entityT = t;
                entityColor = entityColor(shot, target, ox + dx * t, oy + dy * t, oz + dz * t);
            }
        }
        int x = Mth.floor(ox), y = Mth.floor(oy), z = Mth.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double maxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? x + 1 - ox : ox - x) * deltaX;
        double maxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? y + 1 - oy : oy - y) * deltaY;
        double maxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? z + 1 - oz : oz - z) * deltaZ;
        double t = 0;
        Direction face = null; // the face of this block the ray came in through (null: it started inside)
        boolean inWater = shot.eyeInWater;
        double waterFrom = 0;
        double waterDepth = 0;
        int surface = -1;
        int hit = -1;
        double hitT = MAX_DISTANCE;
        while (t < MAX_DISTANCE && t < entityT) {
            double tOut = Math.min(maxX, Math.min(maxY, maxZ));
            boolean own = x == shot.camera.getX() && y == shot.camera.getY() && z == shot.camera.getZ();
            if (!own) {
                BlockState state = shot.stateAt(x, y, z);
                if (!state.isAir()) {
                    FluidState fluid = state.getFluidState();
                    boolean water = !fluid.isEmpty() && fluid.is(FluidTags.WATER);
                    if (water && !inWater) {
                        inWater = true;
                        waterFrom = t;
                        if (surface < 0 && face != null) {
                            surface = waterSurface(shot, x, y, z, face, ox + dx * t, oy + dy * t, oz + dz * t);
                        }
                    } else if (!water && inWater) {
                        inWater = false;
                        waterDepth += t - waterFrom;
                    }
                    if (!fluid.isEmpty() && fluid.is(FluidTags.LAVA)) {
                        hit = lava(shot, face, ox + dx * t, oy + dy * t, oz + dz * t, x, y, z);
                        hitT = t;
                        break;
                    }
                    if (state.getRenderShape() != RenderShape.INVISIBLE || state.hasBlockEntity()) {
                        int result = block(shot, state, x, y, z, face, t, tOut, ox, oy, oz, dx, dy, dz);
                        if (result >= 0) {
                            hit = result;
                            hitT = shot.hitT;
                            break;
                        }
                    }
                } else if (inWater) {
                    inWater = false;
                    waterDepth += t - waterFrom;
                }
            }
            // On to the next block.
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                t = maxX;
                maxX += deltaX;
                face = stepX > 0 ? Direction.WEST : Direction.EAST;
            } else if (maxY < maxZ) {
                y += stepY;
                t = maxY;
                maxY += deltaY;
                face = stepY > 0 ? Direction.DOWN : Direction.UP;
            } else {
                z += stepZ;
                t = maxZ;
                maxZ += deltaZ;
                face = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
            }
        }
        double end;
        int color;
        if (hit >= 0 && hitT <= entityT) {
            color = hit;
            end = hitT;
        } else if (entityT < MAX_DISTANCE) {
            color = entityColor;
            end = entityT;
        } else {
            color = sky(shot, dy);
            end = MAX_DISTANCE;
        }
        if (inWater) {
            waterDepth += Math.max(0, end - waterFrom);
        }
        if (waterDepth > 0) {
            int deep = scale(shot.level.getBlockTint(BlockPos.containing(shot.eye), BiomeColors.WATER_COLOR_RESOLVER), 0.3F);
            color = mix(color, deep, (float) (1.0 - Math.exp(-waterDepth * 0.16)));
        }
        if (surface >= 0) {
            color = mix(color, surface, 0.45F);
        }
        if (hit >= 0 || entityT < MAX_DISTANCE) {
            float fog = (float) Mth.clamp((end - MAX_DISTANCE * 0.6) / (MAX_DISTANCE * 0.4), 0.0, 1.0);
            color = mix(color, shot.horizon, fog);
        }
        return color;
    }

    /**
     * Where the ray meets the block at x, y, z (if it does): its colour, or -1 (the ray misses its
     * shape, or goes through a see-through pixel). Sets {@link Shot#hitT}.
     */
    private static int block(Shot shot, BlockState state, int x, int y, int z, Direction face, double tIn, double tOut,
                              double ox, double oy, double oz, double dx, double dy, double dz) {
        BlockPos pos = shot.cursor.set(x, y, z);
        VoxelShape shape = state.getShape(shot.level, pos);
        double t;
        Direction hitFace;
        double hx, hy, hz;
        if (shape == Shapes.block()) {
            if (face == null) {
                return -1; // the lens is inside it
            }
            t = tIn;
            hitFace = face;
        } else {
            if (shape.isEmpty()) {
                return -1;
            }
            Vec3 from = new Vec3(ox + dx * (tIn - 1.0E-4), oy + dy * (tIn - 1.0E-4), oz + dz * (tIn - 1.0E-4));
            Vec3 to = new Vec3(ox + dx * (tOut + 1.0E-4), oy + dy * (tOut + 1.0E-4), oz + dz * (tOut + 1.0E-4));
            BlockHitResult result = shape.clip(from, to, pos);
            if (result == null) {
                return -1;
            }
            Vec3 at = result.getLocation();
            t = (at.x - ox) * dx + (at.y - oy) * dy + (at.z - oz) * dz;
            hitFace = result.getDirection();
        }
        hx = ox + dx * t - x;
        hy = oy + dy * t - y;
        hz = oz + dz * t - z;
        Layer[] layers = layers(shot.level, state, hitFace);
        int texel = -1;
        int tint = -1;
        for (int i = layers.length - 1; i >= 0 && texel < 0; i--) {
            Layer layer = layers[i];
            double u, v;
            switch (hitFace) {
                case UP -> { u = hx; v = hz; }
                case DOWN -> { u = hx; v = 1 - hz; }
                case NORTH -> { u = 1 - hx; v = 1 - hy; }
                case SOUTH -> { u = hx; v = 1 - hy; }
                case WEST -> { u = hz; v = 1 - hy; }
                default -> { u = 1 - hz; v = 1 - hy; }
            }
            int tx = Mth.clamp((int) (u * layer.width()), 0, layer.width() - 1);
            int ty = Mth.clamp((int) (v * layer.height()), 0, layer.height() - 1);
            int argb = layer.pixels()[ty * layer.width() + tx];
            if ((argb >>> 24) >= 128) {
                texel = argb & 0xFFFFFF;
                tint = layer.tint();
            }
        }
        if (texel < 0) {
            return -1; // see-through here
        }
        if (tint >= 0) {
            int tintColor = blockColors().getColor(state, shot.level, pos, tint);
            if (tintColor != -1) {
                texel = multiply(texel, tintColor);
            }
        }
        float light = light(shot, pos, hitFace, state);
        shot.hitT = t;
        return scale(texel, light * FACE_SHADE[hitFace.get3DDataValue()]);
    }

    /** How bright a face is: the light in front of it (or in the block itself, for lamps and partial blocks), 0 to 1. */
    private static float light(Shot shot, BlockPos pos, Direction face, BlockState state) {
        BlockPos front = shot.near.setWithOffset(pos, face);
        int sky = Math.max(shot.level.getBrightness(LightLayer.SKY, front), shot.level.getBrightness(LightLayer.SKY, pos));
        int block = Math.max(shot.level.getBrightness(LightLayer.BLOCK, front), shot.level.getBrightness(LightLayer.BLOCK, pos));
        block = Math.max(block, state.getLightEmission());
        float skyLight = curve(sky / 15.0F) * shot.skyBright;
        float blockLight = curve(block / 15.0F);
        float light = Math.max(0.035F, Math.max(skyLight, blockLight));
        return shot.night ? Math.max(light * 1.6F, 0.65F) : light;
    }

    private static float curve(float f) {
        return f / (4.0F - 3.0F * f);
    }

    private static int sky(Shot shot, double dy) {
        if (dy >= 0) {
            return mix(shot.horizon, shot.skyTop, (float) Math.min(1.0, dy * 2.5));
        }
        return mix(shot.horizon, shot.below, (float) Math.min(1.0, -dy * 4.0));
    }

    private static int waterSurface(Shot shot, int x, int y, int z, Direction face, double hx, double hy, double hz) {
        BlockPos pos = new BlockPos(x, y, z);
        Layer[] layers = layers(shot.level, Blocks.WATER.defaultBlockState(), face);
        int base = 0x3F76E4;
        if (layers.length > 0) {
            Layer layer = layers[0];
            double u = face.getAxis() == Direction.Axis.X ? hz - z : hx - x;
            double v = face.getAxis() == Direction.Axis.Y ? hz - z : 1 - (hy - y);
            int tx = Mth.clamp((int) (u * layer.width()), 0, layer.width() - 1);
            int ty = Mth.clamp((int) (v * layer.height()), 0, layer.height() - 1);
            base = multiply(layer.pixels()[ty * layer.width() + tx] & 0xFFFFFF, shot.level.getBlockTint(pos, BiomeColors.WATER_COLOR_RESOLVER));
        }
        return scale(base, light(shot, pos, Direction.UP, Blocks.WATER.defaultBlockState()) * 1.1F);
    }

    private static int lava(Shot shot, Direction face, double hx, double hy, double hz, int x, int y, int z) {
        Layer[] layers = layers(shot.level, Blocks.LAVA.defaultBlockState(), face == null ? Direction.UP : face);
        if (layers.length == 0) {
            return 0xE85A10;
        }
        Layer layer = layers[0];
        int tx = Mth.clamp((int) ((hx - x + hz - z) % 1.0 * layer.width()), 0, layer.width() - 1);
        int ty = Mth.clamp((int) ((hy - y) * layer.height()), 0, layer.height() - 1);
        return layer.pixels()[ty * layer.width() + tx] & 0xFFFFFF;
    }

    private static int entityColor(Shot shot, Target target, double hx, double hy, double hz) {
        AABB box = target.box();
        double height = (hy - box.minY) / Math.max(0.01, box.getYsize());
        int base = height > 0.75 ? target.colors()[0] : height > (target.player() ? 0.375 : 0.25) ? target.colors()[1] : target.colors()[2];
        // Which side was hit, for shading.
        double ex = Math.min(Math.abs(hx - box.minX), Math.abs(hx - box.maxX));
        double ey = Math.min(Math.abs(hy - box.minY), Math.abs(hy - box.maxY));
        double ez = Math.min(Math.abs(hz - box.minZ), Math.abs(hz - box.maxZ));
        Direction side = ey <= ex && ey <= ez ? (Math.abs(hy - box.maxY) < Math.abs(hy - box.minY) ? Direction.UP : Direction.DOWN)
                : ex <= ez ? Direction.EAST : Direction.SOUTH;
        BlockPos pos = BlockPos.containing(hx, hy, hz);
        return scale(base, light(shot, pos, Direction.UP, Blocks.AIR.defaultBlockState()) * FACE_SHADE[side.get3DDataValue()]);
    }

    /** Where the ray meets {@code box}, or -1. */
    private static double slab(AABB box, double ox, double oy, double oz, double dx, double dy, double dz) {
        double tMin = 0, tMax = MAX_DISTANCE;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz};
        double[] min = {box.minX, box.minY, box.minZ}, max = {box.maxX, box.maxY, box.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                if (o[i] < min[i] || o[i] > max[i]) {
                    return -1;
                }
                continue;
            }
            double t1 = (min[i] - o[i]) / d[i];
            double t2 = (max[i] - o[i]) / d[i];
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return -1;
            }
        }
        return tMin > 0 ? tMin : -1; // the lens inside a box (someone standing in front of it) doesn't count
    }

    // ---- Textures ----

    private static BlockColors blockColors() {
        return Minecraft.getInstance().getBlockColors();
    }

    /** The painted layers of {@code state}'s {@code face}, bottom first (a grass block's side: dirt, then the green overlay). */
    private static Layer[] layers(ClientLevel level, BlockState state, Direction face) {
        Layer[][] byFace = LAYERS.get(state);
        if (byFace == null) {
            byFace = new Layer[6][];
            LAYERS.put(state, byFace);
        }
        Layer[] layers = byFace[face.get3DDataValue()];
        if (layers == null) {
            layers = buildLayers(state, face);
            byFace[face.get3DDataValue()] = layers;
        }
        return layers;
    }

    private static Layer[] buildLayers(BlockState state, Direction face) {
        BlockModelShaper shaper = Minecraft.getInstance().getModelManager().getBlockModelShaper();
        List<Layer> layers = new ArrayList<>();
        try {
            BakedModel model = shaper.getBlockModel(state);
            List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, face, RANDOM));
            if (quads.isEmpty()) {
                for (BakedQuad quad : model.getQuads(state, null, RANDOM)) {
                    if (quad.getDirection() == face) {
                        quads.add(quad);
                    }
                }
            }
            if (quads.isEmpty()) {
                quads.addAll(model.getQuads(state, null, RANDOM));
                if (quads.size() > 2) {
                    quads = quads.subList(0, 1);
                }
            }
            for (BakedQuad quad : quads) {
                if (layers.size() >= 3) {
                    break;
                }
                int[] pixels = pixels(quad.getSprite());
                if (pixels != null) {
                    layers.add(new Layer(pixels, quad.getSprite().contents().width(), quad.getSprite().contents().height(),
                            quad.isTinted() ? quad.getTintIndex() : -1));
                }
            }
            if (layers.isEmpty()) {
                TextureAtlasSprite particle = shaper.getParticleIcon(state);
                int[] pixels = pixels(particle);
                if (pixels != null) {
                    layers.add(new Layer(pixels, particle.contents().width(), particle.contents().height(),
                            state.is(Blocks.WATER) ? -1 : 0));
                }
            }
        } catch (RuntimeException e) {
            // A model we can't read: the block is drawn grey.
        }
        if (layers.isEmpty()) {
            int c = 0xFF000000 | state.getBlock().defaultMapColor().col;
            return new Layer[]{new Layer(new int[]{c}, 1, 1, -1)};
        }
        return layers.toArray(NO_LAYERS);
    }

    /** A sprite's first frame, ARGB. */
    private static int[] pixels(TextureAtlasSprite sprite) {
        int[] cached = PIXELS.get(sprite);
        if (cached != null) {
            return cached;
        }
        try {
            NativeImage image = sprite.contents().getOriginalImage();
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    pixels[y * width + x] = (abgr & 0xFF00FF00) | (abgr & 0xFF) << 16 | (abgr >> 16 & 0xFF);
                }
            }
            PIXELS.put(sprite, pixels);
            return pixels;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Colours ----

    static int rgb(double r, double g, double b) {
        return Mth.clamp((int) (r * 255), 0, 255) << 16 | Mth.clamp((int) (g * 255), 0, 255) << 8 | Mth.clamp((int) (b * 255), 0, 255);
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, (int) ((rgb >> 16 & 0xFF) * f));
        int g = Math.min(255, (int) ((rgb >> 8 & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return r << 16 | g << 8 | b;
    }

    static int multiply(int a, int b) {
        int r = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255;
        int g = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    static int mix(int a, int b, float f) {
        int r = (int) ((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
        int g = (int) ((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return r << 16 | g << 8 | bl;
    }

    /** Night vision: brightness only, in green. */
    private static int nightVision(int rgb) {
        float l = ((rgb >> 16 & 0xFF) * 0.3F + (rgb >> 8 & 0xFF) * 0.59F + (rgb & 0xFF) * 0.11F) / 255.0F;
        l = Math.min(1.0F, l * 1.25F + 0.04F);
        return rgb(l * 0.35, l, l * 0.4);
    }

    /** Copies a finished picture into {@code image} (ABGR). */
    public static void copy(int[] picture, NativeImage image) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int argb = picture[y * WIDTH + x];
                image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16 & 0xFF));
            }
        }
    }
}
