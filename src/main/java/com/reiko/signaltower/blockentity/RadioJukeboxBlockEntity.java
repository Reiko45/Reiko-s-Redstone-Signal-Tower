package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Radio Jukebox (v0.60.0): a full-size transmitter (reach by height, masts, antennas, power)
 * with a record player. It broadcasts the music disc in it on its frequency, sending 15 while the
 * song plays, and Radio Speakers that hear the frequency within its reach (or a repeater's on it)
 * play the song where they stand.
 * <ul>
 *   <li>A disc put in plays once (over and over while redstone reaches it).</li>
 *   <li>A redstone pulse into it (or its masts) plays the disc again from the start; while the
 *   redstone stays on, it repeats. When the redstone goes, the song playing finishes, then it stops.</li>
 *   <li>An empty hand takes the disc out. Hoppers and pipes can put a disc in when it's empty, and
 *   take it out once it has stopped, so a hopper line makes a playlist.</li>
 * </ul>
 * What it's playing is also kept in the network ({@link SignalNetwork.Music}), so it keeps playing
 * (and stops on time) while its chunk isn't loaded.
 */
public class RadioJukeboxBlockEntity extends RadioTransmitterBlockEntity implements Clearable {
    /** Ticks after a song before it counts as over (as a vanilla jukebox). */
    public static final int SONG_END_PADDING = 20;
    private static final String DISC_TAG = "jukebox_disc";
    private static final String PLAYING_TAG = "jukebox_playing";
    private static final String START_TAG = "jukebox_start";
    private static final String LOOP_TAG = "jukebox_loop";

    private ItemStack disc = ItemStack.EMPTY;
    /** Whether a song is on (the last play of a repeat included), when it (or its repeats) began, and whether it repeats. Saved. */
    private boolean playing;
    private long start;
    private boolean loop;
    /** Whether redstone reached it at the last look (not saved; see {@link #primed}). */
    private boolean powered;
    /** False until it has looked at its redstone once, so loading or placing it while powered doesn't count as a pulse. */
    private boolean primed;
    /** What it's broadcasting now (15 while a song plays, else 0). Not saved. */
    private int output;
    private final IItemHandler items = new DiscSlot();

    public RadioJukeboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_JUKEBOX_BE.get(), pos, state);
    }

    // ---- What it has ----

    public ItemStack getDisc() {
        return disc;
    }

    public boolean hasDisc() {
        return !disc.isEmpty();
    }

    /** True if {@code stack} is something a jukebox plays. */
    public static boolean isDisc(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.JUKEBOX_PLAYABLE);
    }

    /** The song on its disc, or null. */
    public @Nullable Holder<JukeboxSong> song() {
        return level == null || disc.isEmpty() ? null : JukeboxSong.fromStack(level.registryAccess(), disc).orElse(null);
    }

    /** How long one play takes, in ticks: the song and a second after it (0 with no disc). */
    public int playLength() {
        Holder<JukeboxSong> song = song();
        return song == null ? 0 : song.value().lengthInTicks() + SONG_END_PADDING;
    }

    /** True while a song is playing at {@code now}. */
    public boolean isPlaying(long now) {
        int length = playLength();
        return playing && length > 0 && now >= start && (loop || now - start < length);
    }

    /** True if it repeats the song (redstone reaches it). */
    public boolean isLooping() {
        return loop;
    }

    /** When the play going on at {@code now} began. */
    public long playStart(long now) {
        int length = playLength();
        return loop && length > 0 && now > start ? start + (now - start) / length * length : start;
    }

    /** How far into the song it is at {@code now}, in ticks (0 if it isn't playing). */
    public int elapsed(long now) {
        return isPlaying(now) ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, now - playStart(now))) : 0;
    }

    /** What it's broadcasting now. */
    public int getOutput() {
        return output;
    }

    /** Hoppers, pipes: one slot for a disc. */
    public IItemHandler items() {
        return items;
    }

    // ---- Discs in and out ----

    /**
     * Puts {@code stack} (one of it) in and plays it, if it's a disc and there's none in. Returns
     * true if it went in (the caller takes it from the stack).
     */
    public boolean insert(ItemStack stack) {
        if (!isDisc(stack) || hasDisc() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        disc = stack.copyWithCount(1);
        playing = true;
        start = serverLevel.getGameTime();
        loop = primed && powered; // with redstone on it, it repeats
        setChanged();
        refresh();
        return true;
    }

    /** Takes the disc out (stopping the song). Returns it, or empty. */
    public ItemStack eject() {
        ItemStack out = disc;
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        disc = ItemStack.EMPTY;
        playing = false;
        setChanged();
        refresh();
        return out;
    }

    /** For the tests: as if the song had started at {@code start} (game time). */
    public void startedAtForTests(long start) {
        this.start = start;
        setChanged();
        refresh();
    }

    /** Clears the disc without dropping it (the block is being moved or replaced by a command). */
    @Override
    public void clearContent() {
        disc = ItemStack.EMPTY;
        playing = false;
        setChanged();
    }

    // ---- Playing ----

    /** True if a redstone signal reaches it, or a mast under it. */
    public boolean isPowered(Level level) {
        return RadioHelper.readTransmitterInput(level, worldPosition) > 0;
    }

    /** Moves the song on to {@code now}: it ends (unless it repeats), and with steady redstone and a disc it starts again. */
    private void advance(long now) {
        int length = playLength();
        if (playing && (length <= 0 || !loop && now - start >= length)) {
            playing = false;
            setChanged();
        }
        if (!playing && powered && length > 0) {
            playing = true;
            start = now;
            loop = true;
            setChanged();
        }
    }

    /**
     * Looks at its redstone (a pulse plays the disc from the start, and it repeats while the
     * redstone stays; when the redstone goes, the play going on finishes), works out what it sends,
     * tells the network, and puts its song on the air (or takes it off).
     */
    @Override
    public void refresh() {
        if (level instanceof ServerLevel serverLevel) {
            long now = serverLevel.getGameTime();
            boolean in = isPowered(serverLevel);
            if (!primed) {
                primed = true;
                powered = in;
                if (loop && !in) {
                    start = playStart(now); // its redstone went while it was away: this play is the last
                    loop = false;
                } else if (in && playing) {
                    loop = true;
                }
            } else if (in != powered) {
                powered = in;
                if (in && hasDisc()) {
                    playing = true; // a pulse: from the start
                    start = now;
                    loop = true;
                } else if (!in && loop) {
                    start = playStart(now); // finish this play, then stop
                    loop = false;
                }
                setChanged();
            }
            advance(now);
            int was = output;
            output = isPlaying(now) ? 15 : 0;
            if (output != was) {
                serverLevel.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); // comparators
            }
        }
        super.refresh();
        if (level instanceof ServerLevel serverLevel) {
            broadcast(serverLevel);
        }
    }

    /** Puts what it plays in the network (while it's on the air: sending, not out of power or kept quiet), or takes it off. */
    private void broadcast(ServerLevel level) {
        SignalNetwork network = SignalNetwork.get(level);
        Holder<JukeboxSong> song = song();
        ResourceKey<JukeboxSong> key = song == null ? null : song.unwrapKey().orElse(null);
        boolean onAir = output > 0 && key != null && !network.isDown(worldPosition) && !network.isSilenced(level, worldPosition);
        network.setMusic(worldPosition, onAir ? new SignalNetwork.Music(getFrequency(), key, start, playLength(), loop) : null);
    }

    @Override
    protected SignalNetwork.@Nullable Cause turnOnCause() {
        Holder<JukeboxSong> song = song();
        return SignalNetwork.Cause.jukebox(worldPosition, getTunedBy(), song == null ? "" : song.value().description().getString());
    }

    public static void jukeboxTick(Level level, BlockPos pos, BlockState state, RadioJukeboxBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level.getGameTime();
        if (!be.primed) {
            be.refresh();
        }
        be.advance(now);
        if ((be.isPlaying(now) ? 15 : 0) != be.output) {
            be.refresh();
        } else if (be.output > 0 && now % 20 == 0) {
            // A note now and then, like a jukebox playing.
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    0, serverLevel.random.nextInt(4) / 24.0, 0, 0, 1);
        }
        RadioTransmitterBlockEntity.serverTick(level, pos, state, be);
    }

    // ---- Labels ----

    /** "1:05" for 65 seconds' worth of ticks. */
    public static String clock(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /** "C418 - cat (1:05 / 3:05)", "C418 - cat (stopped)" or "no disc". */
    public static Component statusText(@Nullable Component song, boolean playing, int elapsed, int length, boolean loop) {
        String key = "message." + SignalTower.MOD_ID + ".jukebox_";
        if (song == null) {
            return Component.translatable(key + "no_disc");
        }
        if (!playing) {
            return Component.translatable(key + "stopped", song);
        }
        return Component.translatable(key + (loop ? "playing_loop" : "playing"), song, clock(elapsed), clock(Math.max(0, length - SONG_END_PADDING)));
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!disc.isEmpty()) {
            tag.put(DISC_TAG, disc.save(registries));
        }
        tag.putBoolean(PLAYING_TAG, playing);
        tag.putLong(START_TAG, start);
        tag.putBoolean(LOOP_TAG, loop);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        disc = tag.contains(DISC_TAG) ? ItemStack.parse(registries, tag.getCompound(DISC_TAG)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        playing = tag.getBoolean(PLAYING_TAG) && !disc.isEmpty();
        start = tag.getLong(START_TAG);
        loop = tag.getBoolean(LOOP_TAG);
        primed = false;
    }

    /** One slot: a disc goes in when it's empty, and comes out once it has stopped. */
    private final class DiscSlot implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return disc;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || hasDisc() || !isDisc(stack)) {
                return stack;
            }
            if (!simulate && !insert(stack)) {
                return stack;
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || !hasDisc() || level == null || isPlaying(level.getGameTime())) {
                return ItemStack.EMPTY;
            }
            return simulate ? disc.copy() : eject();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && isDisc(stack);
        }
    }
}
