package com.reiko.signaltower.client;

import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * A Radio Drone's buzz (v0.67.0): its rotors' whine, looped while it's in the air and following it,
 * a little higher the faster it goes. One per drone; the drone's client tick keeps it going.
 */
public final class DroneBuzz {
    private static final Map<Integer, BuzzSound> PLAYING = new HashMap<>();

    private DroneBuzz() {
    }

    /** {@code drone} is in the air: start its buzz if it isn't playing. */
    public static void keep(RadioDroneEntity drone) {
        BuzzSound playing = PLAYING.get(drone.getId());
        if (playing != null && !playing.isStopped() && playing.drone == drone) {
            return;
        }
        BuzzSound sound = new BuzzSound(drone);
        PLAYING.put(drone.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** How many are playing (for the GameTests' client check). */
    public static int count() {
        PLAYING.values().removeIf(AbstractTickableSoundInstance::isStopped);
        return PLAYING.size();
    }

    private static final class BuzzSound extends AbstractTickableSoundInstance {
        final RadioDroneEntity drone;

        BuzzSound(RadioDroneEntity drone) {
            super(ModSounds.DRONE_BUZZ.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.drone = drone;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F;
            this.pitch = 1.0F;
            this.x = drone.getX();
            this.y = drone.getY();
            this.z = drone.getZ();
            this.attenuation = Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (drone.isRemoved() || !drone.getState().airborne() || Minecraft.getInstance().level != drone.level()) {
                stop();
                PLAYING.remove(drone.getId(), this);
                return;
            }
            x = drone.getX();
            y = drone.getY() + 0.2;
            z = drone.getZ();
            double speed = drone.getDeltaMovement().length();
            float targetPitch = (float) (0.92 + Math.min(0.35, speed * 0.9));
            pitch = Mth.lerp(0.25F, pitch, targetPitch);
            float targetVolume = drone.getState() == RadioDroneEntity.State.SINKING ? 0.3F : 0.6F;
            volume = Mth.lerp(0.2F, volume, targetVolume);
        }
    }
}
