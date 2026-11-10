package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioMotionSensorBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tells the network what this transmitter is sending. It updates the moment its input or frequency
 * changes, and also re-checks once a second (and right after its chunk loads) as a safety net for
 * changes no block update reports, such as world edits or config changes.
 */
public class RadioTransmitterBlockEntity extends RadioBlockEntity {
    private static final int RECHECK_INTERVAL = 20;
    private int timer;

    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_TRANSMITTER_BE.get(), pos, state);
    }

    /** For blocks that send like a compact transmitter but keep more state (the Radio Motion Sensor). */
    protected RadioTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** What the frequency log names if this turns its frequency on; null: this transmitter and its tuner. */
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return null;
    }

    /** v0.50.0: the dimension its Ender Antenna sends into, if one was picked (null: the default). */
    private @Nullable net.minecraft.resources.ResourceKey<Level> linkTarget;

    /** The dimension its Ender Antenna sends into (the one picked, if the server still has it, else the default). */
    public @Nullable net.minecraft.resources.ResourceKey<Level> linkTarget(ServerLevel level) {
        return com.reiko.signaltower.radio.CrossDimension.valid(level, linkTarget);
    }

    /** Picks the dimension its Ender Antenna sends into (null: the default), and tells the network. */
    public void setLinkTarget(@Nullable net.minecraft.resources.ResourceKey<Level> target) {
        linkTarget = target;
        setChanged();
        refresh();
    }

    // ---- v0.55.0: an old tower generated in the world ----

    /** The frequencies an old tower in the world is given: 100,000 to 999,999. */
    public static final int FOUND_FREQUENCY_MIN = 100_000;
    public static final int FOUND_FREQUENCY_SPAN = 900_000;
    private boolean foundTower;
    /** Whether its frequency has been checked against the world's private frequencies and names (once). */
    private boolean foundChecked;
    private @Nullable BlockPos foundSign;
    /** v0.56.0: an old tower that has gone quiet (not lightning): sending again earns Finders Keepers. */
    private boolean foundQuiet;
    /** v0.57.0: a village radio shack's tower (built from a template, so it picks its own frequency). */
    private boolean villageStation;
    public static final String STATION_SIGN_KEY = "sign.reikos_signal_tower.radio_tower.station";

    // ---- v0.58.0: raid alerts (village stations) and computers (CC: Tweaked) ----

    /** The raid a village station is following (its id), or -1; the last wave it announced, and how many there'll be. */
    private int raidId = -1;
    private int raidWave;
    private int raidWaves;
    private int raidTimer;
    /** What a computer set it to send (0-15; not saved: a computer sets it again when it starts up). */
    private int computerSignal;

    /** v0.58.0: true while a village station is warning of a raid (it sends 15). */
    public boolean isRaidAlert() {
        return raidId >= 0;
    }

    /** The wave of the raid it last announced (0 before the first comes). */
    public int raidWave() {
        return raidWave;
    }

    /** How many waves the raid it's following will have. */
    public int raidWaves() {
        return raidWaves;
    }

    /** v0.58.0: the strength a computer set it to send (it sends the higher of this and its redstone). */
    public int getComputerSignal() {
        return computerSignal;
    }

    public void setComputerSignal(int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped != computerSignal) {
            computerSignal = clamped;
            refresh();
        }
    }

    /** What it sends besides its redstone: a computer's strength, or 15 during a raid alert. */
    private int extraInput() {
        return Math.max(computerSignal, raidId >= 0 ? 15 : 0);
    }

    /**
     * v0.58.0: a village station's look at the raid near it ({@code now}: null if there's none).
     * A new raid: it sends 15 and telegraphs "RAID!"; each new wave: "Raid: wave 2 of 5"; the end:
     * whether the village held or fell (or that the raid has ended, if it just went away).
     */
    public void updateRaid(ServerLevel level, com.reiko.signaltower.radio.RaidAlerts.@Nullable Snapshot now) {
        if (!villageStation) {
            return;
        }
        if (!Config.RAID_ALERTS.get()) {
            if (raidId >= 0) {
                raidId = -1;
                setChanged();
                refresh();
            }
            return;
        }
        if (raidId >= 0 && (now == null || now.id() != raidId)) {
            announceRaid(level, com.reiko.signaltower.radio.RaidAlerts.endText(null)); // it went away (stopped)
            raidId = -1;
            setChanged();
            refresh();
        }
        if (now == null) {
            return;
        }
        if (raidId < 0) {
            if (now.status() != com.reiko.signaltower.radio.RaidAlerts.Status.ONGOING) {
                return; // one that was already over when it first saw it
            }
            raidId = now.id();
            raidWave = now.wave();
            raidWaves = now.waves();
            setChanged();
            refresh(); // on the air at 15 before the message goes out
            announceRaid(level, com.reiko.signaltower.radio.RaidAlerts.startText());
            if (!SignalNetwork.get(level).isDown(worldPosition)) {
                // v0.59.0: and its siren, heard 48 blocks round
                level.playSound(null, worldPosition, com.reiko.signaltower.registry.ModSounds.STATION_SIREN.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
            }
            return;
        }
        raidWaves = now.waves();
        if (now.status() != com.reiko.signaltower.radio.RaidAlerts.Status.ONGOING) {
            announceRaid(level, com.reiko.signaltower.radio.RaidAlerts.endText(now.status()));
            raidId = -1;
            setChanged();
            refresh();
            return;
        }
        if (now.wave() > raidWave) {
            raidWave = now.wave();
            setChanged();
            announceRaid(level, com.reiko.signaltower.radio.RaidAlerts.waveText(raidWave, raidWaves));
        }
    }

    /** Telegraphs {@code text} on its frequency from "Village Radio", over its reach: not while it's silenced or out of power. */
    private void announceRaid(ServerLevel level, String text) {
        SignalNetwork network = SignalNetwork.get(level);
        if (network.isSilenced(level, worldPosition) || network.isDown(worldPosition)) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        com.reiko.signaltower.radio.Telegraph.broadcast(level, worldPosition, getFrequency(),
                network.stationCoverage(level, worldPosition, getFrequency(), reachOf(level, worldPosition, state)),
                com.reiko.signaltower.radio.RaidAlerts.senderName(), text);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    /** True for the transmitter of an old radio tower generated in the world (or a village's). */
    public boolean isFoundTower() {
        return foundTower;
    }

    /** v0.57.0: true for the transmitter of a village radio shack. */
    public boolean isVillageStation() {
        return villageStation;
    }

    /**
     * World generation: makes this the transmitter of an old tower, on {@code frequency}, with the hut's
     * sign (showing the frequency) at {@code sign}. Only sets its own fields (the level isn't there yet).
     */
    public void markFound(int frequency, @Nullable BlockPos sign) {
        foundTower = true;
        foundChecked = false;
        foundSign = sign == null ? null : sign.immutable();
        setFrequency(frequency);
        setChanged();
    }

    /**
     * The first time an old tower ticks: if someone has already made its frequency private, or named
     * it, it moves to a free one (and its sign says so), so it never breaks into anyone's frequency.
     */
    public void checkFound(ServerLevel level) {
        if (!foundTower || foundChecked) {
            return;
        }
        foundChecked = true;
        setChanged();
        if (villageStation && foundSign == null) {
            foundSign = findStationSign(level); // v0.57.0: the template can't know where it'll be
        }
        // v0.57.0: every village station comes from the same template, so each picks its own frequency.
        if (!villageStation && !frequencyTaken(level, getFrequency())) {
            return;
        }
        for (int i = 0; i < 64; i++) {
            int frequency = FOUND_FREQUENCY_MIN + level.getRandom().nextInt(FOUND_FREQUENCY_SPAN);
            if (!frequencyTaken(level, frequency)) {
                setFrequency(frequency);
                if (foundSign != null && level.getBlockEntity(foundSign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                    sign.setText(com.reiko.signaltower.world.RadioTowerPiece.signText(frequency), true);
                }
                return;
            }
        }
    }

    /** v0.57.0: the "Radio Station" sign near a village station (within 8 blocks), if there is one. */
    private @Nullable BlockPos findStationSign(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-8, -8, -8), worldPosition.offset(8, 2, 8))) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                    && sign.getFrontText().getMessage(0, false).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && STATION_SIGN_KEY.equals(text.getKey())) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** True if {@code frequency} is someone's private frequency, or has a name. */
    public static boolean frequencyTaken(ServerLevel level, int frequency) {
        return com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer()).get(frequency) != null
                || com.reiko.signaltower.radio.FrequencyNames.nameOf(level, frequency) != null;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkTarget != null) {
            tag.putString("link_target", linkTarget.location().toString());
        }
        if (foundTower) {
            tag.putBoolean("found_tower", true);
            tag.putBoolean("found_checked", foundChecked);
            tag.putBoolean("found_quiet", foundQuiet);
            if (villageStation) {
                tag.putBoolean("village_station", true);
            }
            if (foundSign != null) {
                tag.putLong("found_sign", foundSign.asLong());
            }
        }
        if (raidId >= 0) {
            tag.putInt("raid_id", raidId);
            tag.putInt("raid_wave", raidWave);
            tag.putInt("raid_waves", raidWaves);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        net.minecraft.resources.ResourceLocation id = tag.contains("link_target")
                ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString("link_target")) : null;
        linkTarget = id == null ? null : net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        foundTower = tag.getBoolean("found_tower");
        foundChecked = tag.getBoolean("found_checked");
        foundQuiet = tag.getBoolean("found_quiet");
        villageStation = tag.getBoolean("village_station");
        foundSign = tag.contains("found_sign") ? BlockPos.of(tag.getLong("found_sign")) : null;
        raidId = tag.contains("raid_id") ? tag.getInt("raid_id") : -1;
        loaded = true; // v0.59.0: its first refresh after loading makes no on/off sound
        raidWave = tag.getInt("raid_wave");
        raidWaves = tag.getInt("raid_waves");
    }

    /** Re-reads input power, height and frequency, and tells the network. Safe to call any time. */
    public void refresh() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (foundTower && !foundChecked) {
            checkFound(serverLevel); // v0.57.0: before it first sends (a placed template can be refreshed before it ticks)
        }
        com.reiko.signaltower.block.DishRotatorBlock.turnDish(serverLevel, worldPosition); // v0.52.0
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadioTransmitterBlock)) {
            return;
        }
        int power = inputOf(serverLevel, worldPosition, state);
        int reach = reachOf(serverLevel, worldPosition, state);
        SignalNetwork.get(serverLevel).updateTransmitter(worldPosition, getFrequency(), power,
                reach, getTunedBy(), state.getBlock() instanceof CompactRadioTransmitterBlock,
                power > 0 ? turnOnCause() : null);
        SignalNetwork.get(serverLevel).setDish(worldPosition, com.reiko.signaltower.radio.AntennaTier.dishAim(state)); // v0.48.0
        SignalNetwork.get(serverLevel).setLink(worldPosition, // v0.50.0: an Ender Antenna sends into another dimension too
                com.reiko.signaltower.radio.AntennaTier.of(state) == com.reiko.signaltower.radio.AntennaTier.ENDER
                        && com.reiko.signaltower.radio.AntennaTier.sendsAcross(state) ? linkTarget(serverLevel) : null);
        SignalNetwork.get(serverLevel).setUplink(worldPosition, com.reiko.signaltower.radio.AntennaTier.uplinks(serverLevel, worldPosition, state)); // v0.61.0
        com.reiko.signaltower.radio.PowerCost.check(serverLevel, worldPosition); // v0.49.0: a charged battery, or quiet
        // Its light goes out while lightning has it knocked out (v0.47.0) or it's out of power (v0.49.0);
        // the network keeps what it would send.
        boolean powered = power > 0 && !SignalNetwork.get(serverLevel).isDown(worldPosition);
        if (state.getValue(RadioTransmitterBlock.POWERED) != powered) {
            serverLevel.setBlock(worldPosition, state.setValue(RadioTransmitterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
            airSound(serverLevel, state, powered);
        }
        airSeen = true;
        advancements(serverLevel, state, powered, reach);
    }

    // ---- v0.59.0: the sound of a tower going on and off the air ----

    /** At least this long between two of its on/off sounds (a tower on a fast clock stays bearable). */
    public static final int AIR_SOUND_GAP_TICKS = 40;
    private long lastAirSound = Long.MIN_VALUE;
    /** Whether it has been refreshed since it was made or loaded; and whether it was loaded (or placed from a template). */
    private boolean airSeen;
    private boolean loaded;
    /** For the tests: the last on/off sound it played (true on, false off), or null. */
    private @Nullable Boolean lastAirSoundOn;

    /**
     * A full-size Radio Transmitter coming on or going off the air: a relay clunk and a carrier
     * whining up, or sinking into static. Not the first time a loaded (or generated) tower is
     * looked at, not for the compact or special transmitters, and not more often than every 2 seconds.
     */
    private void airSound(ServerLevel level, BlockState state, boolean on) {
        if (!state.is(ModRegistry.RADIO_TRANSMITTER.get()) || !airSeen && (loaded || foundTower)) {
            return;
        }
        long now = level.getGameTime();
        if (lastAirSound != Long.MIN_VALUE && now - lastAirSound < AIR_SOUND_GAP_TICKS) {
            return;
        }
        lastAirSound = now;
        lastAirSoundOn = on;
        level.playSound(null, worldPosition, on ? com.reiko.signaltower.registry.ModSounds.TOWER_ON.get()
                : com.reiko.signaltower.registry.ModSounds.TOWER_OFF.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    /** For the tests: the last on/off sound it played (true on, false off), or null if none. */
    public @Nullable Boolean lastAirSoundForTests() {
        return lastAirSoundOn;
    }

    /** v0.56.0: First Broadcast and Over the Horizon for its tuner; Finders Keepers for an old tower brought back. */
    private void advancements(ServerLevel level, BlockState state, boolean sending, int reach) {
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioJammerBlock) {
            return; // jamming isn't broadcasting
        }
        if (sending) {
            com.reiko.signaltower.advancement.TowerAdvancements.fire(level, worldPosition,
                    com.reiko.signaltower.advancement.TowerAdvancements.ON_AIR, reach, getTunedBy(), 0);
            if (foundTower && foundQuiet && !villageStation) {
                foundQuiet = false;
                setChanged();
                com.reiko.signaltower.advancement.TowerAdvancements.fire(level, worldPosition,
                        com.reiko.signaltower.advancement.TowerAdvancements.FOUND_TOWER_REVIVED, reach, getTunedBy(),
                        com.reiko.signaltower.advancement.TowerAdvancements.REVIVED_RADIUS);
            }
        } else if (foundTower && !villageStation && !foundQuiet && !SignalNetwork.get(level).isKnockedOut(worldPosition)) {
            foundQuiet = true;
            setChanged();
        }
    }

    /** v0.56.0: true while an old tower is quiet (it earns Finders Keepers when it sends again). */
    public boolean isFoundQuiet() {
        return foundQuiet;
    }

    /**
     * The redstone signal a transmitter in {@code state} at {@code pos} broadcasts: a normal one also
     * reads its mast column; a compact one (v0.38.0) only what's around it, like a button's block;
     * a Radio Motion Sensor (v0.39.0) full strength while it sees someone, whatever redstone is around.
     */
    public static int inputOf(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioTimerTransmitterBlock) {
            // v0.42.0: full strength while its clock is on, unless redstone pauses it.
            return level.getBlockEntity(pos) instanceof TimerTransmitterBlockEntity timer ? timer.output(level) : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.BundledTransmitterBlock) {
            // v0.54.0: its strongest color (it sends each on its own frequency).
            return level.getBlockEntity(pos) instanceof BundledTransmitterBlockEntity bundled ? bundled.strongest() : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioWeatherStationBlock) {
            // v0.53.0: its reading of the weather, time or moon where it stands (redstone only pauses it).
            return level.getBlockEntity(pos) instanceof WeatherStationBlockEntity station ? station.output(level) : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioJukeboxBlock) {
            // v0.60.0: 15 while its song plays (redstone only starts and repeats it).
            return level.getBlockEntity(pos) instanceof RadioJukeboxBlockEntity jukebox ? jukebox.getOutput() : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioSignalRecorderBlock) {
            // v0.44.0: what its recording sends now (redstone only starts and stops it).
            return level.getBlockEntity(pos) instanceof SignalRecorderBlockEntity recorder ? recorder.getOutput() : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadarDishBlock) {
            // v0.65.0: how many things it sees (at most 15) while Send is on; redstone does nothing.
            return level.getBlockEntity(pos) instanceof RadarDishBlockEntity dish ? dish.output() : 0;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioDoorSensorBlock) {
            // v0.68.0: full strength while a door (or chest ...) touching it is open.
            return level.getBlockEntity(pos) instanceof DoorSensorBlockEntity sensor && sensor.isSending() ? 15 : 0;
        }
        if (state.getBlock() instanceof RadioMotionSensorBlock) {
            return level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor && sensor.isSensing() ? 15 : 0;
        }
        int redstone = state.getBlock() instanceof CompactRadioTransmitterBlock
                ? level.getBestNeighborSignal(pos)
                : RadioHelper.readTransmitterInput(level, pos);
        // v0.58.0: a computer's strength, or a village station's raid alert, if higher.
        return level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity tx ? Math.max(redstone, tx.extraInput()) : redstone;
    }

    /**
     * How far a transmitter in {@code state} at {@code pos} reaches: by height, or a compact one's
     * fixed reach (config, capped by maxReach); a Radio Jammer (v0.46.0) its share of a transmitter's.
     */
    public static int reachOf(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CompactRadioTransmitterBlock) {
            int reach = Config.COMPACT_TRANSMITTER_REACH.get();
            int max = Config.MAX_REACH.get();
            return max > 0 ? Math.min(reach, max) : reach;
        }
        if (state.getBlock() instanceof com.reiko.signaltower.block.RadioJammerBlock) {
            return RadioJammerBlockEntity.jamReach(level, pos, state);
        }
        return RadioHelper.towerReach(level, pos, state);
    }

    /** {@link #reachOf} in the weather where it stands now (v0.46.0; compact ones don't mind it). For status lines, Jade and the reach ring. */
    public static int reachNow(ServerLevel level, BlockPos pos, BlockState state) {
        int reach = reachOf(level, pos, state);
        return state.getBlock() instanceof CompactRadioTransmitterBlock ? reach : SignalNetwork.get(level).reachNow(level, pos, reach);
    }

    @Override
    protected void onFrequencyChanged() {
        refresh();
    }

    @Override
    protected void onTunedByChanged() {
        refresh();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadioTransmitterBlockEntity be) {
        if (be.foundTower && !be.foundChecked && level instanceof ServerLevel serverLevel) {
            be.checkFound(serverLevel); // v0.55.0
        }
        if (be.villageStation && level instanceof ServerLevel serverLevel && --be.raidTimer <= 0) {
            be.raidTimer = com.reiko.signaltower.radio.RaidAlerts.CHECK_TICKS; // v0.58.0
            be.updateRaid(serverLevel, com.reiko.signaltower.radio.RaidAlerts.near(serverLevel, pos));
        }
        if (be.foundTower && level instanceof ServerLevel serverLevel && --be.mapTimer <= 0) {
            be.mapTimer = 40; // v0.61.0: players nearby get it on their map
            com.reiko.signaltower.radio.MapTowers.nearFoundTower(serverLevel, pos, be.villageStation, be.getFrequency());
        }
        if (be.timer-- > 0) {
            return;
        }
        be.timer = RECHECK_INTERVAL;
        be.refresh();
    }

    /** Ticks until an old tower or village station next checks for players nearby, for their maps (v0.61.0; not saved). */
    private int mapTimer;
}
