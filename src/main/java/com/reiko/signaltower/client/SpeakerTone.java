package com.reiko.signaltower.client;

import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.radio.SpeakerSound;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * A Radio Speaker's Tone (v0.62.0): a steady beep, looped, from the moment the speaker hears its
 * frequency until it doesn't (or it's set to another sound, or gone), so Morse comes through as
 * short and long beeps. One per speaker; the speaker's client tick keeps it going.
 */
public final class SpeakerTone {
    private static final Map<BlockPos, ToneSound> PLAYING = new HashMap<>();

    private SpeakerTone() {
    }

    /** The speaker at {@code pos} is sounding its tone: start it if it isn't playing. */
    public static void keep(BlockPos pos, float pitch, float volume) {
        ToneSound playing = PLAYING.get(pos);
        if (playing != null && !playing.isStopped()) {
            return;
        }
        ToneSound sound = new ToneSound(pos, pitch, volume);
        PLAYING.put(pos, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** True if the speaker at {@code pos} should still be sounding: still a speaker on the Tone, hearing its frequency. */
    static boolean sounding(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof com.reiko.signaltower.block.RadioSpeakerBlock && state.getValue(RadioReceiverBlock.POWER) > 0
                && level.getBlockEntity(pos) instanceof RadioReceiverBlockEntity speaker && speaker.getSpeakerSound().whileOn()
                && speaker.getMusicSong() == null;
    }

    private static final class ToneSound extends AbstractTickableSoundInstance {
        private final BlockPos pos;

        ToneSound(BlockPos pos, float pitch, float volume) {
            super(ModSounds.SPEAKER_TONE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.pos = pos;
            this.looping = true;
            this.delay = 0;
            this.pitch = pitch;
            this.volume = volume;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.attenuation = Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (!sounding(pos)) {
                stop();
                PLAYING.remove(pos, this);
            }
        }
    }
}
