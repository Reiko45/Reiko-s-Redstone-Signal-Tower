package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.TvMonitorBlock;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.MonitorWall;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.WalkieTalkie;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A TV Monitor's channels and what it shows (v0.66.0). Once a second the server works out which
 * wall the monitor is part of ({@link MonitorWall}); the wall's lead then picks, for its channel's
 * frequency, the nearest TV Camera whose picture reaches it (and that may send there: not someone
 * else's on a private frequency), and tells the network it's watching (the camera's REC light). What
 * goes to clients is which camera, how far out in its reach the wall is (for snow), the channel and
 * whether it's locked; each client then draws the picture itself. Channel 1 is the monitor's own
 * frequency, channels 2 to 6 are set in its settings; tuning one monitor of a wall tunes the whole
 * wall.
 * <p>
 * On a private frequency (or an encrypted one) the wall shows nothing unless its lead was placed
 * or tuned (or its channels set) by the owner or a member ({@link #lockedBy}).
 */
public class TvMonitorBlockEntity extends RadioBlockEntity {
    public static final int EXTRA_CHANNELS = 5;
    /** Flag: show "CH 2 · Front Gate", REC and the time over the picture. */
    public static final int OVERLAY = 1;
    public static final int DEFAULT_FLAGS = OVERLAY;
    private static final String CHANNELS_TAG = "tv_channels";
    private static final String CHANNEL_TAG = "tv_channel";
    private static final String FLAGS_TAG = "tv_flags";
    private static final String POWERED_TAG = "tv_powered";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    private static boolean retuningWall;

    private int[] extra = new int[EXTRA_CHANNELS];
    private int channel;
    private int flags = DEFAULT_FLAGS;
    private boolean wasPowered;
    // The picture (worked out on the server, sent to clients; not saved).
    private boolean shown;
    private long cameraPos;
    private String cameraName = "";
    private float fraction;
    private String lockedBy = "";
    private int switches;
    // The wall it's part of.
    private MonitorWall.Wall wall;
    // Server only.
    private int timer;
    // Client only: when the channel last changed, for a moment of static (never: Long.MIN_VALUE).
    private long switchedAt = Long.MIN_VALUE;
    private boolean synced;

    public TvMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TV_MONITOR_BE.get(), pos, state);
        wall = MonitorWall.Wall.single(pos);
        timer = Math.floorMod(pos.hashCode() + 11, 20);
    }

    // ---- Channels ----

    /** The frequencies of its channels: its own, then the extra ones set (at most 6). */
    public List<Integer> channels() {
        List<Integer> channels = new ArrayList<>(1 + EXTRA_CHANNELS);
        channels.add(getFrequency());
        for (int frequency : extra) {
            if (frequency > 0) {
                channels.add(frequency);
            }
        }
        return channels;
    }

    /** The extra channels' frequencies (channels 2 to 6; 0 for none). */
    public int[] extraChannels() {
        return extra.clone();
    }

    /** Which channel it's on (0 for channel 1). */
    public int channelIndex() {
        return Math.min(channel, channels().size() - 1);
    }

    public int channelFrequency() {
        return channels().get(channelIndex());
    }

    public int getFlags() {
        return flags;
    }

    public boolean showsOverlay() {
        return (flags & OVERLAY) != 0;
    }

    public void setExtraChannels(int[] frequencies) {
        int[] trimmed = trimmed(frequencies);
        if (!Arrays.equals(trimmed, extra)) {
            extra = trimmed;
            channel = Math.min(channel, channels().size() - 1);
            changed();
        }
    }

    public void setFlags(int newFlags) {
        flags = newFlags & OVERLAY;
        changed();
    }

    /** Steps to the next channel (back to channel 1 after the last), with a burst of static. */
    public void nextChannel(ServerLevel level) {
        int count = channels().size();
        channel = (channelIndex() + 1) % count;
        switches++;
        level.playSound(null, worldPosition, ModSounds.TV_SWITCH.get(), SoundSource.BLOCKS, 0.6F, 1.0F);
        changed();
        update(level);
    }

    /** Sets the channels and the overlay, and maybe steps to the next channel. Whoever sets the channels becomes its tuner. */
    public Component configure(ServerLevel level, Player player, int[] newExtra, int newFlags, boolean next) {
        if (!Arrays.equals(extra, trimmed(newExtra))) {
            setTunedBy(player.getUUID());
        }
        setExtraChannels(newExtra);
        setFlags(newFlags);
        if (next) {
            nextChannel(level);
        } else {
            update(level);
        }
        Component locked = lockNotice(level);
        return locked != null ? locked : Component.translatable(KEY + "tv_monitor_set", channels().size(),
                channelIndex() + 1, FrequencyNames.label(level, channelFrequency())).withStyle(ChatFormatting.AQUA);
    }

    private static int[] trimmed(int[] frequencies) {
        int[] trimmed = new int[EXTRA_CHANNELS];
        for (int i = 0; i < EXTRA_CHANNELS && i < frequencies.length; i++) {
            trimmed[i] = frequencies[i] <= 0 ? 0 : Frequency.clamp(frequencies[i]);
        }
        return trimmed;
    }

    /** A redstone signal around it: a new one (a pulse, a button) steps its wall to the next channel. */
    public void powered(ServerLevel level, boolean powered) {
        if (powered && !wasPowered) {
            lead(level).nextChannel(level);
        }
        if (powered != wasPowered) {
            wasPowered = powered;
            setChanged();
        }
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Tuning one monitor of a wall tunes them all (and they all get its tuner). */
    @Override
    public void setFrequency(int newFrequency) {
        if (level instanceof ServerLevel serverLevel && !retuningWall && Frequency.clamp(newFrequency) != getFrequency()) {
            MonitorWall.Wall found = MonitorWall.find(serverLevel, worldPosition);
            if (found.size() > 1) {
                retuningWall = true;
                try {
                    for (BlockPos member : MonitorWall.members(found, getBlockState().getValue(TvMonitorBlock.FACING))) {
                        if (!member.equals(worldPosition) && serverLevel.getBlockEntity(member) instanceof TvMonitorBlockEntity other) {
                            other.setTunedBy(getTunedBy());
                            other.setFrequency(newFrequency);
                        }
                    }
                } finally {
                    retuningWall = false;
                }
            }
        }
        super.setFrequency(newFrequency);
    }

    // ---- The wall ----

    /** The wall it's part of, as of the last look (on a client: as the server last said). */
    public MonitorWall.Wall wall() {
        return wall;
    }

    /** Its wall's lead (itself if it's on its own). */
    public TvMonitorBlockEntity lead(ServerLevel level) {
        MonitorWall.Wall found = MonitorWall.find(level, worldPosition);
        return !found.lead().equals(worldPosition) && level.getBlockEntity(found.lead()) instanceof TvMonitorBlockEntity lead ? lead : this;
    }

    // ---- The picture ----

    public boolean isShowing() {
        return shown;
    }

    /** The camera it shows (if {@link #isShowing}). */
    public BlockPos cameraPos() {
        return BlockPos.of(cameraPos);
    }

    public String cameraName() {
        return cameraName;
    }

    /** How far out in the camera's reach it is: 0 in the middle, 1 at the edge (snowy past 0.7). */
    public float fraction() {
        return fraction;
    }

    /** The owner of the private frequency it may not show, or "". */
    public String lockedBy() {
        return lockedBy;
    }

    /** How many times its channel has changed (for a moment of static on clients). */
    public int switches() {
        return switches;
    }

    /** On a client: the game time its channel last changed, or {@link Long#MIN_VALUE} if it hasn't since it was seen. */
    public long switchedAt() {
        return switchedAt;
    }

    /** "CH 2 of 3: "Front Gate", a camera 40 blocks north", "CH 1: no camera on 2026 reaches here" or "Private to Dev". */
    public Component statusText() {
        Component channelText = Component.translatable(KEY + "tv_channel_of", channelIndex() + 1, channels().size());
        if (!lockedBy.isEmpty()) {
            return Component.translatable(KEY + "tv_monitor_locked", channelText, lockedBy).withStyle(ChatFormatting.RED);
        }
        if (!shown) {
            return Component.translatable(KEY + "tv_monitor_none", channelText).withStyle(ChatFormatting.GRAY);
        }
        BlockPos camera = cameraPos();
        int dx = camera.getX() - worldPosition.getX();
        int dz = camera.getZ() - worldPosition.getZ();
        int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        int direction = SignalNetwork.compassDirection(dx, dz);
        Component where = direction < 0 ? Component.translatable(KEY + "tv_camera_here")
                : Component.translatable(KEY + "tv_camera_away", distance,
                Component.translatable("screen." + SignalTower.MOD_ID + ".locator.dir_" + DIRECTIONS[direction]));
        Component name = cameraName.isEmpty() ? Component.translatable(KEY + "tv_camera_unnamed") : Component.literal("\"" + cameraName + "\"");
        return Component.translatable(KEY + (fraction > WalkieTalkie.CLEAR_FRACTION ? "tv_monitor_shows_snowy" : "tv_monitor_shows"),
                channelText, name, where);
    }

    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    /** If its wall's channel is someone else's private frequency, the red line saying so; else null. */
    public @Nullable Component lockNotice(ServerLevel level) {
        TvMonitorBlockEntity lead = lead(level);
        int frequency = lead.channelFrequency();
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(frequency);
        return claim != null && lead.getTunedBy() != null && !claim.allows(lead.getTunedBy())
                ? Component.translatable(KEY + "tv_monitor_locked_line", FrequencyNames.label(level, frequency), claim.ownerName())
                .withStyle(ChatFormatting.RED) : null;
    }

    /**
     * Works out its wall; as the lead, picks the camera to show and says it's watching; tells
     * clients if anything changed. Public for the GameTests.
     */
    public void update(ServerLevel level) {
        SignalNetwork network = SignalNetwork.get(level);
        MonitorWall.Wall newWall = MonitorWall.find(level, worldPosition);
        boolean nowShown = false;
        long newCamera = 0;
        String newName = "";
        float newFraction = 0;
        String locked = "";
        if (!newWall.isLead()) {
            network.unwatch(worldPosition);
            nowShown = level.getBlockEntity(newWall.lead()) instanceof TvMonitorBlockEntity lead && lead.shown;
        } else {
            int frequency = channelFrequency();
            FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(frequency);
            locked = claim != null && getTunedBy() != null && !claim.allows(getTunedBy()) ? claim.ownerName() : "";
            Map.Entry<BlockPos, SignalNetwork.CameraSpot> best = null;
            double bestFraction = 0;
            double bestDistance = Double.MAX_VALUE;
            if (locked.isEmpty()) {
                for (Map.Entry<BlockPos, SignalNetwork.CameraSpot> entry : network.camerasOn(frequency)) {
                    SignalNetwork.CameraSpot spot = entry.getValue();
                    if (!FrequencyClaims.maySend(level, frequency, spot.tunedBy())) {
                        continue;
                    }
                    double edge = WalkieTalkie.edgeFraction(spot.coverage(), worldPosition);
                    double distance = entry.getKey().distSqr(worldPosition);
                    if (edge <= 1.0 && distance < bestDistance) {
                        best = entry;
                        bestFraction = edge;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) {
                nowShown = true;
                newCamera = best.getKey().asLong();
                newName = best.getValue().name();
                newFraction = Math.round(bestFraction * 100) / 100.0F;
                network.watch(best.getKey(), worldPosition, level.getGameTime());
            } else {
                network.unwatch(worldPosition);
            }
        }
        boolean changed = nowShown != shown || newCamera != cameraPos || !newName.equals(cameraName)
                || newFraction != fraction || !locked.equals(lockedBy) || !newWall.equals(wall);
        shown = nowShown;
        cameraPos = newCamera;
        cameraName = newName;
        fraction = newFraction;
        lockedBy = locked;
        wall = newWall;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof TvMonitorBlock && state.getValue(TvMonitorBlock.ACTIVE) != shown) {
            level.setBlock(worldPosition, state.setValue(TvMonitorBlock.ACTIVE, shown), Block.UPDATE_CLIENTS);
            state = level.getBlockState(worldPosition);
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TvMonitorBlockEntity monitor) {
        if (level instanceof ServerLevel serverLevel && --monitor.timer <= 0) {
            monitor.timer = 20;
            monitor.update(serverLevel);
        }
    }

    @Override
    protected void onFrequencyChanged() {
        channel = 0;
        timer = 1;
    }

    @Override
    protected void onTunedByChanged() {
        timer = 1;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).unwatch(worldPosition);
        }
        super.setRemoved();
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray(CHANNELS_TAG, extra);
        tag.putInt(CHANNEL_TAG, channel);
        tag.putInt(FLAGS_TAG, flags);
        tag.putBoolean(POWERED_TAG, wasPowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        extra = trimmed(tag.getIntArray(CHANNELS_TAG));
        channel = Math.max(0, tag.getInt(CHANNEL_TAG));
        flags = tag.contains(FLAGS_TAG) ? tag.getInt(FLAGS_TAG) & OVERLAY : DEFAULT_FLAGS;
        wasPowered = tag.getBoolean(POWERED_TAG);
        // Only the update packet carries the picture and the wall (the client's copy).
        if (tag.contains("tv_shown")) {
            shown = tag.getBoolean("tv_shown");
            cameraPos = tag.getLong("tv_camera");
            cameraName = tag.getString("tv_camera_name");
            fraction = tag.getFloat("tv_fraction");
            lockedBy = tag.getString("tv_locked");
            int newSwitches = tag.getInt("tv_switches");
            if (newSwitches != switches && synced && level != null) {
                switchedAt = level.getGameTime(); // not for the first look (it may have switched long ago)
            }
            switches = newSwitches;
            synced = true;
            int[] w = tag.getIntArray("tv_wall");
            wall = w.length == 4 ? new MonitorWall.Wall(BlockPos.of(tag.getLong("tv_lead")), w[0], w[1], w[2], w[3])
                    : MonitorWall.Wall.single(worldPosition);
        }
    }

    /** What clients get: the channels, the picture and the wall (not who tuned it). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("frequency", getFrequency());
        tag.putIntArray(CHANNELS_TAG, extra);
        tag.putInt(CHANNEL_TAG, channel);
        tag.putInt(FLAGS_TAG, flags);
        tag.putBoolean("tv_shown", shown);
        tag.putLong("tv_camera", cameraPos);
        tag.putString("tv_camera_name", cameraName);
        tag.putFloat("tv_fraction", fraction);
        tag.putString("tv_locked", lockedBy);
        tag.putInt("tv_switches", switches);
        tag.putLong("tv_lead", wall.lead().asLong());
        tag.putIntArray("tv_wall", new int[]{wall.width(), wall.height(), wall.column(), wall.row()});
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Item <-> block: it keeps its channels ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        List<Integer> stored = componentInput.get(ModRegistry.TV_CHANNELS.get());
        if (stored != null) {
            extra = trimmed(stored.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (Arrays.stream(extra).anyMatch(f -> f > 0)) {
            components.set(ModRegistry.TV_CHANNELS.get(), Arrays.stream(extra).boxed().toList());
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(CHANNELS_TAG);
    }

    /** Facing, for the renderer. */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof TvMonitorBlock ? state.getValue(TvMonitorBlock.FACING) : Direction.NORTH;
    }
}
