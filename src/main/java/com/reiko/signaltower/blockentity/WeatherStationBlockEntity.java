package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.StationReading;
import com.reiko.signaltower.radio.Telegraph;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Radio Weather Station (v0.53.0). It's a full-size transmitter (reach by height, antennas,
 * masts, batteries) that broadcasts one {@link StationReading reading} of the conditions where it
 * stands instead of the redstone it gets: the weather, the time of day, the moon, or (with Reiko's
 * Weather) its weather code, storm danger, wind, precipitation, temperature, season or the nearest
 * storm. A redstone signal into it (or its masts) pauses it, like the Timer Transmitter.
 * <p>
 * With "announce" on it also sends a message on its frequency when the weather where it stands
 * changes ("Now: Thunderstorm"), to pagers, Radio Telegraphs and Status Boards in its reach, as a
 * telegraph does. A new weather has to hold for {@link #ANNOUNCE_CHECK_TICKS} × 2 before it's
 * announced, so a storm's ragged edge doesn't send one message after another.
 */
public class WeatherStationBlockEntity extends RadioTransmitterBlockEntity {
    /** How often it looks at the weather to announce. */
    public static final int ANNOUNCE_CHECK_TICKS = 100;
    private static final String MODE_TAG = "station_mode";
    private static final String ANNOUNCE_TAG = "station_announce";
    private static final String ANNOUNCED_TAG = "station_announced";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private StationReading mode = StationReading.WEATHER;
    private boolean announce;
    /** The weather it last announced (or saw when announcing was turned on), by id. Saved. */
    private String announced = "";
    /** A different weather seen at the last look, waiting to hold (not saved). */
    private String pending = "";
    private int announceTimer;

    public WeatherStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.WEATHER_STATION_BE.get(), pos, state);
    }

    public StationReading getMode() {
        return mode;
    }

    public boolean isAnnouncing() {
        return announce;
    }

    /** Sets what it reads (one that needs Reiko's Weather only if it's installed) and whether it announces. */
    public void setSettings(StationReading mode, boolean announce) {
        this.mode = mode.offered() ? mode : this.mode;
        if (announce && !this.announce && level instanceof ServerLevel serverLevel) {
            announced = StationReading.weatherNow(serverLevel, worldPosition)[0]; // only changes from now on
            pending = "";
        }
        this.announce = announce;
        setChanged();
        refresh();
    }

    /** True while a redstone signal reaches it (or a mast under it), which pauses it. */
    public boolean isPaused(Level level) {
        return RadioHelper.readTransmitterInput(level, worldPosition) > 0;
    }

    /** Its reading now (whether or not redstone pauses it). */
    public StationReading.Value reading(ServerLevel level) {
        return mode.read(level, worldPosition);
    }

    /** What it broadcasts now: its reading, or 0 while redstone pauses it. */
    public int output(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || isPaused(level)) {
            return 0;
        }
        return reading(serverLevel).strength();
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return SignalNetwork.Cause.station(worldPosition, getTunedBy());
    }

    // ---- Announcing ----

    public static void stationTick(Level level, BlockPos pos, BlockState state, WeatherStationBlockEntity be) {
        if (be.announce && level instanceof ServerLevel serverLevel && ++be.announceTimer >= ANNOUNCE_CHECK_TICKS) {
            be.announceTimer = 0;
            be.checkWeather(serverLevel);
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, be);
    }

    /** Looks at the weather; a change that held since the last look is announced. Public for the GameTests. */
    public @Nullable SignalNetwork.Message checkWeather(ServerLevel level) {
        String[] now = StationReading.weatherNow(level, worldPosition);
        if (announced.isEmpty()) {
            announced = now[0]; // placed with announcing on: only changes from now on
            setChanged();
            return null;
        }
        if (now[0].equals(announced)) {
            pending = "";
            return null;
        }
        if (!now[0].equals(pending)) {
            pending = now[0]; // look again next time: it has to hold
            return null;
        }
        announced = now[0];
        pending = "";
        setChanged();
        return announce(level, now[1], !now[2].isEmpty());
    }

    /**
     * Sends "Now: {@code weather}" on its frequency, as a telegraph message from "Weather Station",
     * over its own reach (and repeaters on it). Not while it's paused, silenced, or out of power.
     */
    private @Nullable SignalNetwork.Message announce(ServerLevel level, String weather, boolean severe) {
        SignalNetwork network = SignalNetwork.get(level);
        if (isPaused(level) || network.isSilenced(level, worldPosition) || network.isDown(worldPosition)) {
            return null;
        }
        String text = Telegraph.clean(Component.translatable(KEY + (severe ? "station_announce_severe" : "station_announce"), weather).getString());
        String name = Component.translatable("block." + SignalTower.MOD_ID + ".radio_weather_station.short").getString();
        SignalNetwork.Message message = Telegraph.broadcast(level, worldPosition, getFrequency(),
                network.stationCoverage(level, worldPosition, getFrequency(), RadioTransmitterBlockEntity.reachOf(level, worldPosition,
                        level.getBlockState(worldPosition))), name, text);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, severe ? 0.7F : 1.4F);
        return message;
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(MODE_TAG, mode.name());
        tag.putBoolean(ANNOUNCE_TAG, announce);
        tag.putString(ANNOUNCED_TAG, announced);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // A reading that needs Reiko's Weather is kept even if it's gone now (it reads 0 until it's back).
        mode = tag.contains(MODE_TAG) ? StationReading.named(tag.getString(MODE_TAG)) : StationReading.WEATHER;
        announce = tag.getBoolean(ANNOUNCE_TAG);
        announced = tag.getString(ANNOUNCED_TAG);
    }

    // ---- Item <-> block: it keeps its settings ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        String stored = componentInput.get(ModRegistry.STATION_MODE.get());
        if (stored != null) {
            mode = StationReading.named(stored);
        }
        Boolean storedAnnounce = componentInput.get(ModRegistry.STATION_ANNOUNCE.get());
        if (storedAnnounce != null) {
            announce = storedAnnounce;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.STATION_MODE.get(), mode.name());
        components.set(ModRegistry.STATION_ANNOUNCE.get(), announce);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(MODE_TAG);
        tag.remove(ANNOUNCE_TAG);
    }
}
