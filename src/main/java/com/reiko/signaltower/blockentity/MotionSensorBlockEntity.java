package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioMotionSensorBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * The Radio Motion Sensor's state and eyes (v0.39.0). Twice a second it looks for players within
 * its range that it can see from its face (a block with a collision shape in between hides them,
 * as it would from a mob), and while it sees one it broadcasts full strength on its frequency like
 * a Compact Radio Transmitter. It keeps sending for {@link #HOLD_TICKS} after it last saw someone,
 * so a player walking past doesn't flicker it. Spectators are never seen.
 * <p>
 * Whoever places it owns it: only they (or an operator) change its range and who it reacts to, and
 * only they re-tune it. In {@link Mode#STRANGERS} mode it ignores its owner and, on a private
 * frequency, the frequency's owner and members (friends, group members and guests). The frequency
 * log names the first player it saw when it turned on.
 */
public class MotionSensorBlockEntity extends RadioTransmitterBlockEntity {
    /** The ranges its owner can pick, in blocks. */
    public static final int[] RANGES = {2, 4, 6, 8, 12, 16};
    public static final int DEFAULT_RANGE = 6;
    /** How often it looks around, in ticks. */
    public static final int LOOK_INTERVAL = 10;
    /** How long it keeps sending after it last saw someone, in ticks. */
    public static final int HOLD_TICKS = 40;

    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    /** Who it reacts to. Saved by name, sent by ordinal: only add at the end. */
    public enum Mode {
        EVERYONE, STRANGERS;

        public static Mode of(int ordinal) {
            Mode[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        static Mode named(String name) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return EVERYONE;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private @Nullable UUID owner;
    private String ownerName = "";
    private int range = DEFAULT_RANGE;
    private Mode mode = Mode.EVERYONE;

    // Not saved: after a reload it simply looks again.
    private int lookTimer;
    private boolean sensing;
    private long sensingUntil;
    private @Nullable UUID seenId;
    private String seenName = "";

    public MotionSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MOTION_SENSOR_BE.get(), pos, state);
        lookTimer = Math.floorMod(pos.hashCode(), LOOK_INTERVAL); // sensors placed together don't all look on the same tick
    }

    // ---- Owner and settings ----

    public @Nullable UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(@Nullable UUID player, String name) {
        owner = player;
        ownerName = name == null ? "" : name;
        setChanged();
    }

    /** The owner, an operator, or anyone if nobody owns it (set by a command). */
    public boolean mayConfigure(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    public int getRange() {
        return range;
    }

    public Mode getMode() {
        return mode;
    }

    /** {@code range} if it's one of {@link #RANGES}, else the default. */
    public static int validRange(int range) {
        for (int allowed : RANGES) {
            if (allowed == range) {
                return range;
            }
        }
        return DEFAULT_RANGE;
    }

    /** The range after {@code range} in {@link #RANGES} (back to the first after the last; {@code back}: the one before). */
    public static int nextRange(int range, boolean back) {
        for (int i = 0; i < RANGES.length; i++) {
            if (RANGES[i] == range) {
                return RANGES[Math.floorMod(i + (back ? -1 : 1), RANGES.length)];
            }
        }
        return DEFAULT_RANGE;
    }

    /**
     * The owner (or an operator) sets the range and who it reacts to. Returns the action bar message:
     * what it's set to, or a red refusal if it isn't theirs.
     */
    public Component configure(Player player, int newRange, Mode newMode) {
        if (!mayConfigure(player)) {
            return Component.translatable(KEY + "sensor_not_yours", ownerLabel()).withStyle(ChatFormatting.RED);
        }
        if (owner == null) {
            setOwner(player.getUUID(), player.getGameProfile().getName());
        }
        range = validRange(newRange);
        mode = newMode;
        setChanged();
        return Component.translatable(KEY + "sensor_set", range,
                Component.translatable(KEY + "sensor_mode_" + mode.key())).withStyle(ChatFormatting.AQUA);
    }

    public Component ownerLabel() {
        return Component.literal(ownerName.isEmpty() ? "?" : ownerName);
    }

    // ---- Sensing ----

    /** True while it sees someone (or saw them less than {@link #HOLD_TICKS} ago). */
    public boolean isSensing() {
        return sensing;
    }

    /** Who it saw first when it last turned on ("" if nobody yet). */
    public String getSeenName() {
        return seenName;
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return SignalNetwork.Cause.sensor(worldPosition, seenId, seenName);
    }

    /**
     * True if it knows {@code player}, so {@link Mode#STRANGERS} ignores them: its owner, or the
     * owner or a member (friend, group member or guest) of the private frequency it sends on.
     */
    public boolean knows(ServerLevel level, Player player) {
        if (owner != null && owner.equals(player.getUUID())) {
            return true;
        }
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
        return claim != null && claim.allows(player.getUUID());
    }

    /** Where it looks from: just in front of its face. */
    public static Vec3 eyeOf(BlockPos pos, BlockState state) {
        Direction out = CompactRadioTransmitterBlock.pointing(state);
        return Vec3.atCenterOf(pos).subtract(out.getStepX() * 0.3, out.getStepY() * 0.3, out.getStepZ() * 0.3);
    }

    /**
     * The nearest of {@code candidates} it reacts to: within its range (to the middle of their
     * body), not a spectator, not ignored by its mode, and with a clear line from its face to
     * their eyes or the middle of their body. Null if none.
     */
    public @Nullable Player nearestSeen(ServerLevel level, Iterable<? extends Player> candidates) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RadioMotionSensorBlock)) {
            return null;
        }
        Vec3 eye = eyeOf(worldPosition, state);
        double best = (double) range * range;
        Player found = null;
        for (Player player : candidates) {
            if (player.isSpectator() || !player.isAlive() || player.level() != level) {
                continue;
            }
            Vec3 body = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
            double distance = body.distanceToSqr(eye);
            if (distance > best || found != null && distance >= best) {
                continue;
            }
            if (mode == Mode.STRANGERS && knows(level, player)) {
                continue;
            }
            if (canSee(level, eye, player.getEyePosition(), player) || canSee(level, eye, body, player)) {
                best = distance;
                found = player;
            }
        }
        return found;
    }

    private static boolean canSee(Level level, Vec3 from, Vec3 to, Player player) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /**
     * Looks at {@code candidates} once (at {@code gameTime}): seeing someone keeps it sending for
     * {@link #HOLD_TICKS} more. Returns who it saw, or null. Public for the GameTests, whose mock
     * players aren't in the level's player list.
     */
    public @Nullable Player lookAround(ServerLevel level, long gameTime, Iterable<? extends Player> candidates) {
        Player seen = nearestSeen(level, candidates);
        if (seen != null) {
            if (!sensing) {
                seenId = seen.getUUID();
                seenName = seen.getGameProfile().getName();
            }
            sensingUntil = gameTime + HOLD_TICKS;
        }
        update(gameTime);
        return seen;
    }

    /** Turns it off once the hold time is over, and tells the network when it changes. */
    private void update(long gameTime) {
        boolean now = gameTime < sensingUntil;
        if (now != sensing) {
            sensing = now;
            refresh();
        }
    }

    public static void sensorTick(Level level, BlockPos pos, BlockState state, MotionSensorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level.getGameTime();
        if (be.lookTimer-- <= 0) {
            be.lookTimer = LOOK_INTERVAL - 1;
            be.lookAround(serverLevel, now, serverLevel.players());
        } else {
            be.update(now);
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, be);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        if (!ownerName.isEmpty()) {
            tag.putString("owner_name", ownerName);
        }
        tag.putInt("range", range);
        tag.putString("mode", mode.key());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        range = tag.contains("range") ? validRange(tag.getInt("range")) : DEFAULT_RANGE;
        mode = Mode.named(tag.getString("mode"));
    }
}
