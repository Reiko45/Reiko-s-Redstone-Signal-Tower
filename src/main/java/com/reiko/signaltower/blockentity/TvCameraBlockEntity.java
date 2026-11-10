package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.TvCameraBlock;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.TvCamera;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A TV Camera's settings and name, and what its picture reaches (v0.66.0). Every
 * {@link #COVERAGE_TICKS} (and when it's re-tuned, renamed or set) it puts itself in the network for
 * monitors: its frequency, who tuned it, its reach (like a walkie-talkie's, by height, carried by
 * towers and repeaters on its frequency), its settings and its name. Every second it counts the
 * monitors showing it, for its REC light and comparators. Nothing about the picture is on the
 * server: each client draws it ({@code client.CameraFeeds}).
 */
public class TvCameraBlockEntity extends RadioBlockEntity {
    private static final String SETTINGS_TAG = "camera_settings";
    private static final String NAME_TAG = "camera_name";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    public static final int NAME_LENGTH = 24;
    public static final int COVERAGE_TICKS = 40;

    private int settings = TvCamera.DEFAULT;
    private String name = "";
    // Server only (not saved).
    private int watchers;
    private int reach;
    private int towers;
    private int repeaters;
    private int timer;

    public TvCameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TV_CAMERA_BE.get(), pos, state);
        timer = Math.floorMod(pos.hashCode() + 3, 20);
    }

    // ---- Settings ----

    public int getSettings() {
        return settings;
    }

    public String getName() {
        return name;
    }

    /** True if {@code player} may set it: anyone, but on a private frequency only the owner, members and operators. */
    public boolean mayConfigure(ServerLevel level, Player player) {
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
        return claim == null || claim.allows(player.getUUID()) || player.hasPermissions(2);
    }

    /** Sets its settings and name. Returns the action bar line (red if {@code player} may not). */
    public Component configure(ServerLevel level, Player player, int newSettings, String newName) {
        if (!mayConfigure(level, player)) {
            FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
            return Component.translatable(KEY + "tv_not_yours", claim == null ? "?" : claim.ownerName()).withStyle(ChatFormatting.RED);
        }
        setSettings(newSettings);
        setName(newName);
        return Component.translatable(KEY + "tv_camera_set", TvCameraBlock.settingsText(settings)).withStyle(ChatFormatting.AQUA);
    }

    public void setSettings(int newSettings) {
        settings = TvCamera.settings(newSettings);
        changed();
    }

    public void setName(String newName) {
        String trimmed = newName == null ? "" : newName.strip();
        name = trimmed.length() > NAME_LENGTH ? trimmed.substring(0, NAME_LENGTH) : trimmed;
        changed();
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            publish(serverLevel);
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- Who watches ----

    /** How many monitors (walls) showed it at the last count. */
    public int getWatchers() {
        return watchers;
    }

    /** Its own reach (blocks) at the last look. */
    public int getReach() {
        return reach;
    }

    /** "Shown on 2 monitors, reach 180 blocks" and so on. */
    public Component statusText(ServerLevel level) {
        Component reachText = RemoteControlItem.describeReach(reach, towers, repeaters);
        String name = this.name.isEmpty() ? "" : "\"" + this.name + "\" ";
        if (watchers == 0) {
            return Component.translatable(KEY + "tv_camera_unwatched", name, reachText);
        }
        return Component.translatable(KEY + (watchers == 1 ? "tv_camera_watched" : "tv_camera_watched_many"), name, watchers, reachText);
    }

    /** If no monitor shows it because it's on someone else's private frequency, the red line saying so; else null. */
    public @org.jetbrains.annotations.Nullable Component lockNotice(ServerLevel level) {
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
        return claim != null && getTunedBy() != null && !claim.allows(getTunedBy())
                ? Component.translatable(KEY + "tv_camera_locked", com.reiko.signaltower.radio.FrequencyNames.label(level, getFrequency()),
                claim.ownerName()).withStyle(ChatFormatting.RED) : null;
    }

    /** Counts the monitors showing it; the REC light and comparators follow. Public for the GameTests. */
    public void count(ServerLevel level) {
        int now = SignalNetwork.get(level).watchers(worldPosition, level.getGameTime());
        BlockState state = level.getBlockState(worldPosition);
        if (now != watchers) {
            watchers = now;
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
        if (state.getBlock() instanceof TvCameraBlock && state.getValue(TvCameraBlock.REC) != watchers > 0) {
            level.setBlock(worldPosition, state.setValue(TvCameraBlock.REC, watchers > 0), Block.UPDATE_CLIENTS);
        }
    }

    /** Puts it in the network for monitors: frequency, tuner, what its picture reaches, settings, name. Public for the GameTests. */
    public void publish(ServerLevel level) {
        SignalNetwork network = SignalNetwork.get(level);
        reach = RadioHelper.reachAt(level, worldPosition);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, worldPosition, reach, Config.TOWERS_RELAY_REMOTES.get());
        List<SignalNetwork.Coverage> coverage = network.withRepeaters(level, getFrequency(), own);
        towers = own.size() - 1;
        repeaters = coverage.size() - own.size();
        network.updateCamera(worldPosition, new SignalNetwork.CameraSpot(getFrequency(), getTunedBy(), List.copyOf(coverage), settings, name));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TvCameraBlockEntity camera) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (--camera.timer <= 0) {
            camera.timer = 20;
            if (serverLevel.getGameTime() % COVERAGE_TICKS < 20 || SignalNetwork.get(serverLevel).cameraAt(pos) == null) {
                camera.publish(serverLevel);
            }
            camera.count(serverLevel);
        }
    }

    @Override
    protected void onFrequencyChanged() {
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeCamera(worldPosition); // its old watchers no longer see it
            publish(serverLevel);
            count(serverLevel);
        }
    }

    @Override
    protected void onTunedByChanged() {
        if (level instanceof ServerLevel serverLevel) {
            publish(serverLevel);
        }
    }

    /** Broken or unloaded: monitors stop showing it. */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeCamera(worldPosition);
        }
        super.setRemoved();
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SETTINGS_TAG, settings);
        if (!name.isEmpty()) {
            tag.putString(NAME_TAG, name);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = tag.contains(SETTINGS_TAG) ? TvCamera.settings(tag.getInt(SETTINGS_TAG)) : TvCamera.DEFAULT;
        name = tag.getString(NAME_TAG);
    }

    /** What clients get: the frequency, the settings (the head turns with them) and the name. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("frequency", getFrequency());
        tag.putInt(SETTINGS_TAG, settings);
        tag.putString(NAME_TAG, name);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Item <-> block: it keeps its settings, and an anvil name names it ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.CAMERA_SETTINGS.get());
        if (stored != null) {
            settings = TvCamera.settings(stored);
        }
        Component customName = componentInput.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            String text = customName.getString().strip();
            name = text.length() > NAME_LENGTH ? text.substring(0, NAME_LENGTH) : text;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.CAMERA_SETTINGS.get(), settings);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(SETTINGS_TAG);
        tag.remove(NAME_TAG);
    }
}
