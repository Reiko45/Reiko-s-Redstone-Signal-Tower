package com.reiko.signaltower.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Radar (v0.65.0). A Radar Dish on a tower top sweeps everything inside its reach (measured flat,
 * like a tower's) once a second: players, hostile mobs, peaceful mobs (animals, villagers, golems,
 * fish) and vehicles (boats and minecarts), each kind switchable, and optionally only strangers. It
 * can't see anything under {@link #UNDERGROUND} or more solid blocks (in a cave, deep in the ground),
 * in a dimension with a sky; in the Nether and the End it sees everything in its reach. Its latest sweep goes into the
 * dimension's {@link SignalNetwork}, where Radar Screens on its frequency find it; it also sends how
 * many things it sees (at most 15) on its frequency, and a comparator reads the same.
 * <p>
 * Dishes and screens turn together, once every {@link #SWEEP_TICKS}, by game time: the screen's
 * sweep line points where every dish on the frequency is pointing.
 */
public final class Radar {
    /** One turn of the dish (and the screen's sweep line): 4 seconds. */
    public static final int SWEEP_TICKS = 80;
    /** How often a dish sweeps (and a screen looks at it again). */
    public static final int SCAN_TICKS = 20;
    /** Things with this many solid blocks (or more) above their heads don't show. */
    public static final int UNDERGROUND = 8;
    /** At most this many contacts per sweep (the nearest). */
    public static final int MAX_CONTACTS = 64;
    /** At most this many towers on a screen (the nearest to the dish). */
    public static final int MAX_TOWERS = 24;

    // ---- A Radar Dish's settings (one int, kept on the item) ----
    public static final int TRACK_PLAYERS = 1;
    public static final int TRACK_HOSTILE = 2;
    public static final int TRACK_PEACEFUL = 4;
    public static final int TRACK_VEHICLES = 8;
    /** Ignores its tuner and, on a private frequency, the owner and members. */
    public static final int STRANGERS_ONLY = 16;
    /** Sends how many things it sees on its frequency. */
    public static final int SEND = 32;
    public static final int DISH_DEFAULT = TRACK_PLAYERS | TRACK_HOSTILE | TRACK_PEACEFUL | TRACK_VEHICLES | SEND;
    public static final int DISH_MASK = 63;

    // ---- A Radar Screen's settings ----
    /** How far round the dish it shows, in the low three bits: 0 the dish's whole reach, else {@link #ZOOM_RANGES}. */
    public static final int ZOOM_MASK = 7;
    /** The ranges a screen can show (blocks round the dish; never more than the dish's reach). */
    public static final int[] ZOOM_RANGES = {0, 256, 128, 64, 32};
    public static final int PING = 8;
    public static final int SHOW_TOWERS = 16;
    public static final int SCREEN_DEFAULT = 2 | PING | SHOW_TOWERS;
    public static final int SCREEN_MASK = 31;
    private Radar() {
    }

    /** What a blip is. Sent by ordinal: only add at the end. */
    public enum Kind {
        PLAYER(TRACK_PLAYERS), HOSTILE(TRACK_HOSTILE), PEACEFUL(TRACK_PEACEFUL), VEHICLE(TRACK_VEHICLES), TOWER(0);

        private final int bit;

        Kind(int bit) {
            this.bit = bit;
        }

        /** Its "track" bit in a dish's settings (0 for towers, which only screens show). */
        public int bit() {
            return bit;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind of(int ordinal) {
            Kind[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    /** The four kinds a dish can track, in screen order. */
    public static final Kind[] TRACKED = {Kind.PLAYER, Kind.HOSTILE, Kind.PEACEFUL, Kind.VEHICLE};

    /**
     * One thing a dish sees: its kind, where it is from the dish (blocks east and south), the
     * entity's id (so screens notice new ones) and its type's description id (for the log).
     */
    public record Contact(Kind kind, int dx, int dz, int id, String type) {}

    /**
     * A dish's latest sweep: its frequency, its reach now, who tuned it, when, whether it's working
     * (not out of power, knocked out or blown down), what it saw and the towers in its reach.
     */
    public record Sweep(int frequency, int reach, @Nullable UUID tunedBy, long gameTime, boolean working,
                        List<Contact> contacts, List<BlockPos> towers) {
        /** How many of each tracked kind it saw ({@link #TRACKED} order). */
        public int[] counts() {
            return Radar.counts(contacts);
        }
    }

    /** How many of each tracked kind are in {@code contacts} ({@link #TRACKED} order). */
    public static int[] counts(List<Contact> contacts) {
        int[] counts = new int[TRACKED.length];
        for (Contact contact : contacts) {
            if (contact.kind().ordinal() < counts.length) {
                counts[contact.kind().ordinal()]++;
            }
        }
        return counts;
    }

    /** {@code settings} trimmed to a dish's bits. */
    public static int dishSettings(int settings) {
        return settings & DISH_MASK;
    }

    /** {@code settings} trimmed to a screen's bits (and a zoom that exists). */
    public static int screenSettings(int settings) {
        int zoom = Math.min(ZOOM_RANGES.length - 1, settings & ZOOM_MASK);
        return (settings & SCREEN_MASK & ~ZOOM_MASK) | zoom;
    }

    /** How far round the dish a screen with {@code settings} shows, for a dish reaching {@code reach}. */
    public static int shownRange(int settings, int reach) {
        int zoom = Math.min(ZOOM_RANGES.length - 1, settings & ZOOM_MASK);
        return zoom == 0 ? reach : Math.min(reach, ZOOM_RANGES[zoom]);
    }

    /** What a radar makes of {@code entity}, or null if it doesn't show (items, arrows, spectators...). */
    public static @Nullable Kind kindOf(Entity entity) {
        if (!entity.isAlive()) {
            return null;
        }
        if (entity instanceof Player player) {
            return player.isSpectator() ? null : Kind.PLAYER;
        }
        if (entity instanceof Enemy && entity instanceof Mob) {
            return Kind.HOSTILE;
        }
        if (entity instanceof Mob) {
            return Kind.PEACEFUL;
        }
        if (entity instanceof VehicleEntity) {
            return Kind.VEHICLE;
        }
        return null;
    }

    /**
     * True if {@code entity} has at least {@link #UNDERGROUND} solid blocks (not leaves) above its head in
     * its column: in a cave, or deep in the ground. A roof, a tree or a floor or two above it doesn't hide
     * it. Only where there's a sky: in the Nether and the End nothing is hidden.
     */
    public static boolean underground(Level level, Entity entity) {
        if (!level.dimensionType().hasSkyLight()) {
            return false;
        }
        int x = entity.getBlockX();
        int z = entity.getBlockZ();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int from = net.minecraft.util.Mth.floor(entity.getEyeY()) + 1;
        if (surface - from < UNDERGROUND) {
            return false; // not even room for that many blocks above it
        }
        int solid = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, from, z);
        for (int y = from; y < surface && solid < UNDERGROUND; y++) {
            cursor.setY(y);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
            if (state.blocksMotion() && !(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)) {
                solid++;
            }
        }
        return solid >= UNDERGROUND;
    }

    /** The loaded entities in the column of {@code reach} round {@code dish} (to be filtered by {@link #scan}). */
    public static List<Entity> around(ServerLevel level, BlockPos dish, int reach) {
        AABB box = new AABB(dish.getX() - reach, level.getMinBuildHeight(), dish.getZ() - reach,
                dish.getX() + reach + 1, level.getMaxBuildHeight(), dish.getZ() + reach + 1);
        return level.getEntities((Entity) null, box, entity -> kindOf(entity) != null);
    }

    /**
     * What a dish at {@code dish} with {@code settings} sees among {@code candidates}: tracked kinds
     * within {@code reach} (flat), not underground, not ignored players ({@code known}, with
     * {@link #STRANGERS_ONLY}); the nearest {@link #MAX_CONTACTS}, nearest first.
     */
    public static List<Contact> scan(Level level, BlockPos dish, int reach, int settings, Predicate<Player> known,
                                     Iterable<? extends Entity> candidates) {
        List<Contact> found = new ArrayList<>();
        long reach2 = (long) reach * reach;
        for (Entity entity : candidates) {
            Kind kind = kindOf(entity);
            if (kind == null || (settings & kind.bit()) == 0 || entity.level() != level) {
                continue;
            }
            int dx = entity.getBlockX() - dish.getX();
            int dz = entity.getBlockZ() - dish.getZ();
            if ((long) dx * dx + (long) dz * dz > reach2 || underground(level, entity)) {
                continue;
            }
            if (kind == Kind.PLAYER && (settings & STRANGERS_ONLY) != 0 && known.test((Player) entity)) {
                continue;
            }
            found.add(new Contact(kind, dx, dz, entity.getId(), entity.getType().getDescriptionId()));
        }
        found.sort(Comparator.comparingLong(contact -> (long) contact.dx() * contact.dx() + (long) contact.dz() * contact.dz()));
        return found.size() > MAX_CONTACTS ? new ArrayList<>(found.subList(0, MAX_CONTACTS)) : found;
    }

    /** Where (dx east, dz south) is seen from the middle, in radians clockwise from north (0 to 2π). */
    public static double bearing(double dx, double dz) {
        double angle = Math.atan2(dx, -dz);
        return angle < 0 ? angle + Math.PI * 2 : angle;
    }

    /** Where every dish (and sweep line) points at {@code gameTime} + {@code partialTick}, in radians clockwise from north. */
    public static double sweepAngle(long gameTime, float partialTick) {
        return ((Math.floorMod(gameTime, SWEEP_TICKS) + partialTick) / SWEEP_TICKS) * Math.PI * 2;
    }
}
