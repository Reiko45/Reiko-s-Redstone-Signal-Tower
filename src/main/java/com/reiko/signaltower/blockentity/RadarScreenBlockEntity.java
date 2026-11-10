package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadarScreenBlock;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.Radar;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Radar Screen's settings and picture (v0.65.0). Once every {@link Radar#SCAN_TICKS} the server
 * picks the nearest working Radar Dish on its frequency whose reach covers it (and that may send
 * there: not someone else's on a private frequency), and copies that dish's latest sweep: where the
 * dish is from the screen, its reach, its contacts (from the dish) and the towers in its reach. Those
 * go to clients in the block entity's update packet for the renderer and Jade; nothing of it is saved.
 * <p>
 * On a private (or encrypted) frequency it shows nothing unless whoever placed or tuned it is the
 * owner or a member ({@link #lockedBy}). With Ping on it plays a ping when something new shows up
 * (not right after it's placed, loaded or re-tuned).
 */
public class RadarScreenBlockEntity extends RadioBlockEntity {
    private static final String SETTINGS_TAG = "radar_settings";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private int settings = Radar.SCREEN_DEFAULT;
    // The picture (worked out on the server, sent to clients; not saved).
    private boolean shown;
    private int dishDx;
    private int dishDz;
    private int dishReach;
    /** Kind, dx, dz and id of each contact, from the dish. */
    private int[] contacts = new int[0];
    /** dx and dz of each tower, from the dish. */
    private int[] towers = new int[0];
    private String lockedBy = "";
    // Server only.
    private final Set<Integer> seenIds = new HashSet<>();
    private boolean primed;
    private int timer;
    private int pings;

    public RadarScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADAR_SCREEN_BE.get(), pos, state);
        timer = Math.floorMod(pos.hashCode() + 7, Radar.SCAN_TICKS);
    }

    // ---- Settings ----

    public int getSettings() {
        return settings;
    }

    public void setSettings(int newSettings) {
        settings = Radar.screenSettings(newSettings);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- The picture ----

    /** True if it shows a dish. */
    public boolean isShowing() {
        return shown;
    }

    /** Where the dish it shows is, from the screen (blocks east, south). */
    public int dishDx() {
        return dishDx;
    }

    public int dishDz() {
        return dishDz;
    }

    /** The reach of the dish it shows (the whole picture, before zooming in). */
    public int dishReach() {
        return dishReach;
    }

    /** Kind, dx, dz and id of each contact, from the dish (4 ints each). */
    public int[] contacts() {
        return contacts;
    }

    /** dx and dz of each tower, from the dish (2 ints each). */
    public int[] towers() {
        return towers;
    }

    /** The owner of the private frequency it may not show, or "". */
    public String lockedBy() {
        return lockedBy;
    }

    public int contactCount() {
        return contacts.length / 4;
    }

    /** How many times it has pinged since it was made (for the GameTests). */
    public int pingsForTests() {
        return pings;
    }

    /** "Radar 120 blocks north, reach 528: 3 contacts", "No radar dish on 2026 reaches here" or "Private to Dev". */
    public Component statusText() {
        if (!lockedBy.isEmpty()) {
            return Component.translatable(KEY + "radar_screen_locked", lockedBy).withStyle(ChatFormatting.RED);
        }
        if (!shown) {
            return Component.translatable(KEY + "radar_screen_none").withStyle(ChatFormatting.GRAY);
        }
        int distance = (int) Math.round(Math.sqrt((double) dishDx * dishDx + (double) dishDz * dishDz));
        int direction = SignalNetwork.compassDirection(dishDx, dishDz);
        Component where = direction < 0 ? Component.translatable(KEY + "radar_screen_here")
                : Component.translatable(KEY + "radar_screen_away", distance,
                Component.translatable("screen." + SignalTower.MOD_ID + ".locator.dir_" + DIRECTIONS[direction]));
        return Component.translatable(KEY + "radar_screen_shows", where, dishReach, contactCount());
    }

    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    /**
     * Picks the dish to show and copies its sweep; pings for new contacts; tells clients if the
     * picture changed. Public for the GameTests.
     */
    public void update(ServerLevel level) {
        SignalNetwork network = SignalNetwork.get(level);
        int frequency = getFrequency();
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(frequency);
        String locked = claim != null && getTunedBy() != null && !claim.allows(getTunedBy()) ? claim.ownerName() : "";
        Map.Entry<BlockPos, Radar.Sweep> best = null;
        long bestDistance = Long.MAX_VALUE;
        if (locked.isEmpty()) {
            for (Map.Entry<BlockPos, Radar.Sweep> entry : network.radarsOn(frequency)) {
                Radar.Sweep sweep = entry.getValue();
                if (!sweep.working() || !FrequencyClaims.maySend(level, frequency, sweep.tunedBy())) {
                    continue;
                }
                long dx = entry.getKey().getX() - worldPosition.getX();
                long dz = entry.getKey().getZ() - worldPosition.getZ();
                long distance = dx * dx + dz * dz;
                if (distance <= (long) sweep.reach() * sweep.reach() && distance < bestDistance) {
                    best = entry;
                    bestDistance = distance;
                }
            }
        }
        boolean nowShown = best != null;
        int newDx = 0, newDz = 0, newReach = 0;
        int[] newContacts = new int[0];
        int[] newTowers = new int[0];
        if (best != null) {
            Radar.Sweep sweep = best.getValue();
            BlockPos dish = best.getKey();
            newDx = dish.getX() - worldPosition.getX();
            newDz = dish.getZ() - worldPosition.getZ();
            newReach = sweep.reach();
            List<Radar.Contact> seen = sweep.contacts();
            newContacts = new int[seen.size() * 4];
            for (int i = 0; i < seen.size(); i++) {
                Radar.Contact contact = seen.get(i);
                newContacts[i * 4] = contact.kind().ordinal();
                newContacts[i * 4 + 1] = contact.dx();
                newContacts[i * 4 + 2] = contact.dz();
                newContacts[i * 4 + 3] = contact.id();
            }
            List<BlockPos> near = sweep.towers();
            newTowers = new int[near.size() * 2];
            for (int i = 0; i < near.size(); i++) {
                newTowers[i * 2] = near.get(i).getX() - dish.getX();
                newTowers[i * 2 + 1] = near.get(i).getZ() - dish.getZ();
            }
        }
        ping(level, newContacts);
        boolean changed = nowShown != shown || newDx != dishDx || newDz != dishDz || newReach != dishReach
                || !Arrays.equals(newContacts, contacts) || !Arrays.equals(newTowers, towers) || !locked.equals(lockedBy);
        shown = nowShown;
        dishDx = newDx;
        dishDz = newDz;
        dishReach = newReach;
        contacts = newContacts;
        towers = newTowers;
        lockedBy = locked;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RadarScreenBlock && state.getValue(RadarScreenBlock.ACTIVE) != shown) {
            level.setBlock(worldPosition, state.setValue(RadarScreenBlock.ACTIVE, shown), Block.UPDATE_CLIENTS);
            state = level.getBlockState(worldPosition);
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** A ping when a contact shows up that wasn't there at the last look (higher for players, lower for hostile mobs). */
    private void ping(ServerLevel level, int[] newContacts) {
        Set<Integer> now = new HashSet<>();
        int firstNewKind = -1;
        for (int i = 0; i + 3 < newContacts.length; i += 4) {
            int id = newContacts[i + 3];
            now.add(id);
            if (firstNewKind < 0 && !seenIds.contains(id)) {
                firstNewKind = newContacts[i];
            }
        }
        boolean wasPrimed = primed;
        primed = true;
        seenIds.clear();
        seenIds.addAll(now);
        if (!wasPrimed || firstNewKind < 0 || (settings & Radar.PING) == 0) {
            return;
        }
        pings++;
        Radar.Kind kind = Radar.Kind.of(firstNewKind);
        float pitch = kind == Radar.Kind.HOSTILE ? 0.75F : kind == Radar.Kind.PLAYER ? 1.25F : 1.0F;
        level.playSound(null, worldPosition, ModSounds.RADAR_PING.get(), SoundSource.BLOCKS, 0.5F, pitch);
    }

    /** If it shows nothing because it's on someone else's private frequency, the red line saying so; else null. */
    public @Nullable Component lockNotice(ServerLevel level) {
        FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(getFrequency());
        return claim != null && getTunedBy() != null && !claim.allows(getTunedBy())
                ? Component.translatable(KEY + "radar_screen_locked", claim.ownerName()).withStyle(ChatFormatting.RED) : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarScreenBlockEntity screen) {
        if (level instanceof ServerLevel serverLevel && --screen.timer <= 0) {
            screen.timer = Radar.SCAN_TICKS;
            screen.update(serverLevel);
        }
    }

    @Override
    protected void onFrequencyChanged() {
        primed = false; // a new frequency's contacts aren't "new"
        timer = 1;
    }

    @Override
    protected void onTunedByChanged() {
        timer = 1;
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SETTINGS_TAG, settings);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = tag.contains(SETTINGS_TAG) ? Radar.screenSettings(tag.getInt(SETTINGS_TAG)) : Radar.SCREEN_DEFAULT;
        // Only the update packet carries the picture (the client's copy).
        if (tag.contains("radar_shown")) {
            shown = tag.getBoolean("radar_shown");
            dishDx = tag.getInt("radar_dish_dx");
            dishDz = tag.getInt("radar_dish_dz");
            dishReach = tag.getInt("radar_reach");
            contacts = tag.getIntArray("radar_contacts");
            towers = tag.getIntArray("radar_towers");
            lockedBy = tag.getString("radar_locked");
        }
    }

    /** What clients get: the frequency, the settings and the picture (not who tuned it). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("frequency", getFrequency());
        tag.putInt(SETTINGS_TAG, settings);
        tag.putBoolean("radar_shown", shown);
        tag.putInt("radar_dish_dx", dishDx);
        tag.putInt("radar_dish_dz", dishDz);
        tag.putInt("radar_reach", dishReach);
        tag.putIntArray("radar_contacts", contacts);
        tag.putIntArray("radar_towers", towers);
        tag.putString("radar_locked", lockedBy);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Item <-> block: it keeps its settings ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.RADAR_SETTINGS.get());
        if (stored != null) {
            settings = Radar.screenSettings(stored);
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
