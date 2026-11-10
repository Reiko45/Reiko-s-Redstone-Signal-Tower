package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadarDishBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.Radar;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Radar Dish's settings and its latest sweep (v0.65.0). It's a full-size transmitter (reach by
 * height, power, weather, lightning, storms) whose "input" is how many things it sees, if Send is
 * on. Once every {@link Radar#SCAN_TICKS} it sweeps its reach (see {@link Radar#scan}), puts the
 * sweep in the network for Radar Screens, and tells the network (and comparators) when the count
 * changes. The frequency log names what it saw first when it turned its frequency on.
 * <p>
 * Anyone may change its settings, except on a private frequency: there only the owner and members
 * (and operators). With "strangers only" it ignores whoever tuned it and, on a private frequency,
 * the owner and members.
 */
public class RadarDishBlockEntity extends RadioTransmitterBlockEntity {
    private static final String SETTINGS_TAG = "radar_settings";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private int settings = Radar.DISH_DEFAULT;
    // Not saved: after a reload it simply sweeps again.
    private List<Radar.Contact> contacts = List.of();
    private String firstSeen = "";
    private int scanTimer;
    private boolean working;

    public RadarDishBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADAR_DISH_BE.get(), pos, state);
        scanTimer = Math.floorMod(pos.hashCode(), Radar.SCAN_TICKS); // dishes placed together don't all sweep on one tick
    }

    // ---- Settings ----

    public int getSettings() {
        return settings;
    }

    /** True if it may be set by {@code player}: anyone, but on a private frequency only the owner, members and operators. */
    public boolean mayConfigure(ServerLevel level, Player player) {
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
        return claim == null || claim.allows(player.getUUID()) || player.hasPermissions(2);
    }

    /** Sets what it tracks, strangers only and Send. Returns the action bar line (red if {@code player} may not). */
    public Component configure(ServerLevel level, Player player, int newSettings) {
        if (!mayConfigure(level, player)) {
            FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
            return Component.translatable(KEY + "radar_not_yours", claim == null ? "?" : claim.ownerName()).withStyle(ChatFormatting.RED);
        }
        setSettings(newSettings);
        sweep(level, Radar.around(level, worldPosition, RadioTransmitterBlockEntity.reachNow(level, worldPosition, getBlockState())));
        return Component.translatable(KEY + "radar_set", RadarDishBlock.tracksText(settings),
                Component.translatable(KEY + ((settings & Radar.SEND) != 0 ? "radar_sends" : "radar_quiet"))).withStyle(ChatFormatting.AQUA);
    }

    /** Sets its settings (trimmed to a dish's bits). For {@link #configure} and the GameTests. */
    public void setSettings(int newSettings) {
        settings = Radar.dishSettings(newSettings);
        setChanged();
        refresh();
    }

    /** True if {@code player} is someone "strangers only" ignores: whoever tuned it, or the owner or a member of its private frequency. */
    public boolean knows(ServerLevel level, Player player) {
        if (player.getUUID().equals(getTunedBy())) {
            return true;
        }
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
        return claim != null && claim.allows(player.getUUID());
    }

    // ---- What it sees ----

    public List<Radar.Contact> getContacts() {
        return contacts;
    }

    /** How many of each tracked kind it saw at its last sweep ({@link Radar#TRACKED} order). */
    public int[] counts() {
        return Radar.counts(contacts);
    }

    /** True if it swept at its last look (tuned, and not out of power, knocked out or blown down). */
    public boolean isWorking() {
        return working;
    }

    /** What it sends: how many things it sees (at most 15), if Send is on. */
    public int output() {
        return (settings & Radar.SEND) != 0 ? Math.min(15, contacts.size()) : 0;
    }

    /** What a comparator reads: how many things it sees, at most 15 (Send or not). */
    public int comparatorOutput() {
        return Math.min(15, contacts.size());
    }

    /** "2 players, 1 hostile mob" for {@code counts} ({@link Radar#TRACKED} order), or "nothing". */
    public static Component seenText(int[] counts) {
        List<Component> parts = new ArrayList<>();
        for (int i = 0; i < counts.length && i < Radar.TRACKED.length; i++) {
            if (counts[i] > 0) {
                parts.add(Component.translatable(KEY + "radar_count_" + Radar.TRACKED[i].key() + (counts[i] == 1 ? "" : "s"), counts[i]));
            }
        }
        return parts.isEmpty() ? Component.translatable(KEY + "radar_kind_nothing") : RadarDishBlock.joined(parts);
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        return SignalNetwork.Cause.radar(worldPosition, getTunedBy(), firstSeen);
    }

    /**
     * One sweep among {@code candidates} (the GameTests pass their mock players, which aren't in the
     * level): what it sees goes into the network for screens, and a new count to the network and
     * comparators. Nothing is seen while it's out of power, knocked out or blown down.
     */
    public List<Radar.Contact> sweep(ServerLevel level, Iterable<? extends Entity> candidates) {
        SignalNetwork network = SignalNetwork.get(level);
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadarDishBlock)) {
            return List.of();
        }
        working = !network.isDown(worldPosition);
        int reach = RadioTransmitterBlockEntity.reachNow(level, worldPosition, state);
        List<Radar.Contact> found = working
                ? Radar.scan(level, worldPosition, reach, settings, player -> knows(level, player), candidates) : List.of();
        int before = Math.min(15, contacts.size());
        if (contacts.isEmpty() && !found.isEmpty()) {
            firstSeen = found.get(0).type(); // the log names what it saw first
        }
        contacts = found;
        publish(level, network, reach);
        if (Math.min(15, found.size()) != before) {
            refresh();
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
        BlockState now = level.getBlockState(worldPosition);
        if (now.getBlock() instanceof RadarDishBlock && now.getValue(RadarDishBlock.ACTIVE) != working) {
            level.setBlock(worldPosition, now.setValue(RadarDishBlock.ACTIVE, working), Block.UPDATE_CLIENTS);
        }
        return found;
    }

    /** Puts its latest sweep (or a new frequency or tuner) in the network for screens. */
    private void publish(ServerLevel level, SignalNetwork network, int reach) {
        List<BlockPos> towers = working ? network.towersNear(worldPosition, reach, Radar.MAX_TOWERS) : List.of();
        network.updateRadar(worldPosition, new Radar.Sweep(getFrequency(), reach, getTunedBy(), level.getGameTime(),
                working, contacts, towers));
    }

    public static void dishTick(Level level, BlockPos pos, BlockState state, RadarDishBlockEntity dish) {
        if (level instanceof ServerLevel serverLevel && --dish.scanTimer <= 0) {
            dish.scanTimer = Radar.SCAN_TICKS;
            int reach = RadioTransmitterBlockEntity.reachNow(serverLevel, pos, state);
            dish.sweep(serverLevel, Radar.around(serverLevel, pos, reach));
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, dish);
    }

    @Override
    protected void onFrequencyChanged() {
        super.onFrequencyChanged();
        republish();
    }

    @Override
    protected void onTunedByChanged() {
        super.onTunedByChanged();
        republish();
    }

    private void republish() {
        if (level instanceof ServerLevel serverLevel) {
            publish(serverLevel, SignalNetwork.get(serverLevel), RadioTransmitterBlockEntity.reachNow(serverLevel, worldPosition, getBlockState()));
        }
    }

    /** Broken or unloaded: screens stop showing it. */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SignalNetwork.get(serverLevel).removeRadar(worldPosition);
        }
        super.setRemoved();
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SETTINGS_TAG, settings);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = tag.contains(SETTINGS_TAG) ? Radar.dishSettings(tag.getInt(SETTINGS_TAG)) : Radar.DISH_DEFAULT;
    }

    // ---- Item <-> block: it keeps its settings ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.RADAR_SETTINGS.get());
        if (stored != null) {
            settings = Radar.dishSettings(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.RADAR_SETTINGS.get(), settings);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(SETTINGS_TAG);
    }
}
