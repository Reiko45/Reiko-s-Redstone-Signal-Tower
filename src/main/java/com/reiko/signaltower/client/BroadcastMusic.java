package com.reiko.signaltower.client;

import com.reiko.signaltower.radio.SpeakerSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * v0.60.0: plays the songs Radio Speakers get from a Radio Jukebox, on this client. The server
 * tells each speaker what it plays and when that play began; this plays it from the speaker,
 * <b>from where the broadcast is</b> (so someone who arrives in the middle of a song hears the
 * middle), as loud as the speaker is set to (quiet: 16 blocks, normal: 32, loud: 64). Only the
 * {@value #MAX_PLAYING} nearest speakers in earshot play at once, since the game streams music
 * on few channels; the others start when they come in earshot. "Now Playing" shows once a play,
 * when it starts in earshot.
 */
public final class BroadcastMusic {
    /** The most speakers playing at once on this client. */
    public static final int MAX_PLAYING = 4;
    /** How far past its range a speaker keeps playing before it's stopped (so walking at the edge doesn't restart it). */
    private static final double KEEP_MARGIN = 16;
    private static final int CHECK_TICKS = 10;

    /** What a speaker plays: the song, when its play began (game time) and how loud. */
    private record Want(ResourceKey<JukeboxSong> song, long start, SpeakerSound.Volume volume) {
    }

    private record Playing(Want want, SoundInstance sound, long startedAt) {
    }

    private static final Map<BlockPos, Want> WANTS = new HashMap<>();
    private static final Map<BlockPos, Playing> PLAYING = new HashMap<>();
    /** Plays "Now Playing" has been shown for (song and start), so a room full of speakers shows it once. */
    private static final Set<String> SHOWN = new HashSet<>();
    private static int timer;

    private BroadcastMusic() {
    }

    /** What the speaker at {@code pos} plays now (null song: nothing). */
    public static void set(BlockPos pos, @Nullable ResourceKey<JukeboxSong> song, long start, SpeakerSound.Volume volume) {
        Want want = song == null ? null : new Want(song, start, volume);
        Want before = want == null ? WANTS.remove(pos) : WANTS.put(pos, want);
        if (java.util.Objects.equals(before, want)) {
            return;
        }
        Playing playing = PLAYING.get(pos);
        if (playing != null && !playing.want().equals(want)) {
            stop(pos);
        }
        timer = 0; // look again at once
    }

    /** Stops everything (leaving the world). */
    public static void clear() {
        for (BlockPos pos : List.copyOf(PLAYING.keySet())) {
            stop(pos);
        }
        WANTS.clear();
        SHOWN.clear();
    }

    private static void stop(BlockPos pos) {
        Playing playing = PLAYING.remove(pos);
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing.sound());
        }
    }

    /** The volume a speaker set to {@code volume} plays music at (above 1 it carries farther, 16 blocks a step). */
    public static float musicVolume(SpeakerSound.Volume volume) {
        return switch (volume) {
            case QUIET -> 0.5F;
            case NORMAL -> 2.0F;
            case LOUD -> 4.0F;
        };
    }

    /** How far a speaker set to {@code volume} is heard playing music. */
    public static double range(SpeakerSound.Volume volume) {
        return 16.0 * Math.max(1.0F, musicVolume(volume));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (--timer > 0) {
            return;
        }
        timer = CHECK_TICKS;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            if (!PLAYING.isEmpty() || !WANTS.isEmpty()) {
                clear();
            }
            return;
        }
        long now = level.getGameTime();
        var songs = level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
        List<BlockPos> candidates = new ArrayList<>();
        for (Map.Entry<BlockPos, Want> entry : WANTS.entrySet()) {
            BlockPos pos = entry.getKey();
            Want want = entry.getValue();
            Holder<JukeboxSong> song = songs.getHolder(want.song()).orElse(null);
            boolean over = song == null || now - want.start() >= song.value().lengthInTicks();
            double distance = Math.sqrt(player.distanceToSqr(pos.getCenter()));
            Playing playing = PLAYING.get(pos);
            if (over || distance > range(want.volume()) + (playing != null ? KEEP_MARGIN : 0)) {
                if (playing != null) {
                    stop(pos);
                }
                continue;
            }
            candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(pos -> player.distanceToSqr(pos.getCenter())));
        for (int i = MAX_PLAYING; i < candidates.size(); i++) {
            stop(candidates.get(i));
        }
        for (int i = 0; i < Math.min(MAX_PLAYING, candidates.size()); i++) {
            BlockPos pos = candidates.get(i);
            Want want = WANTS.get(pos);
            Playing playing = PLAYING.get(pos);
            if (playing != null && (minecraft.getSoundManager().isActive(playing.sound()) || now - playing.startedAt() < 100)) {
                continue; // playing (or just started: streams take a moment)
            }
            if (playing != null) {
                stop(pos); // it stopped (the sound engine was reset): start it again where the broadcast is now
            }
            Holder<JukeboxSong> song = songs.getHolder(want.song()).orElse(null);
            if (song == null) {
                continue;
            }
            int offset = (int) Math.max(0, now - want.start());
            SoundInstance sound = new SpeakerSongSound(song.value(), pos, musicVolume(want.volume()), offset);
            minecraft.getSoundManager().play(sound);
            PLAYING.put(pos, new Playing(want, sound, now));
            if (SHOWN.add(want.song().location() + "@" + want.start())) {
                minecraft.gui.setNowPlaying(song.value().description());
            }
        }
        if (SHOWN.size() > 64) {
            SHOWN.clear();
        }
    }

    /**
     * A song played from a speaker, starting {@code offsetTicks} into it: its stream skips that
     * much before it plays.
     */
    private static final class SpeakerSongSound extends AbstractSoundInstance {
        private final int offsetTicks;

        SpeakerSongSound(JukeboxSong song, BlockPos pos, float volume, int offsetTicks) {
            super(song.soundEvent().value(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = 1.0F;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.attenuation = Attenuation.LINEAR;
            this.looping = false;
            this.delay = 0;
            this.relative = false;
            this.offsetTicks = offsetTicks;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            CompletableFuture<AudioStream> stream = soundBuffers.getStream(sound.getPath(), looping);
            return offsetTicks <= 10 ? stream : stream.thenApply(audio -> skip(audio, offsetTicks / 20.0));
        }
    }

    /** {@code audio} with its first {@code seconds} read and thrown away. */
    static AudioStream skip(AudioStream audio, double seconds) {
        AudioFormat format = audio.getFormat();
        int frame = Math.max(1, format.getChannels() * Math.max(1, format.getSampleSizeInBits() / 8));
        long left = (long) (seconds * format.getSampleRate()) * frame;
        try {
            while (left > 0) {
                ByteBuffer read = audio.read((int) Math.min(left, 64 * 1024));
                if (read == null || !read.hasRemaining()) {
                    break;
                }
                left -= read.remaining();
            }
        } catch (IOException e) {
            // Play it from where it got to.
        }
        return audio;
    }
}
