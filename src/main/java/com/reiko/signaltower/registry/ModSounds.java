package com.reiko.signaltower.registry;

import com.reiko.signaltower.SignalTower;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The mod's own sounds (v0.59.0), made by {@code tools/make_sounds.py}: each has a subtitle and
 * follows the volume slider of where it's played (Blocks for blocks, Players for things in hand).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, SignalTower.MOD_ID);

    /** A tuner (or remote, pager, meter, map) set to a frequency: static, and a whistle tuning in. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TUNER_TUNE = register("tuner.tune");
    /** One step of the frequency dial (sneak + scroll). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TUNER_STEP = register("tuner.step");
    /** A telegraph sending: ".-." tapped on its key. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_KEY = register("telegraph.key");
    /** A telegraph receiving: its sounder clacks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_SOUNDER = register("telegraph.sounder");
    /** A pager's alert: two high beeps. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAGER_BEEP = register("pager.beep");
    /** The Signal Meter's Geiger tick. */
    public static final DeferredHolder<SoundEvent, SoundEvent> METER_TICK = register("meter.tick");
    /** A Radio Transmitter coming on the air: a relay clunks and the carrier whines up. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_ON = register("tower.on");
    /** A Radio Transmitter going off the air: the carrier sinks into static. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_OFF = register("tower.off");
    /** A village station's raid siren. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_SIREN = register("station.siren");
    /** v0.61.0: a Launch Pad's rocket lifting off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_LAUNCH = register("satellite.launch");
    /** v0.62.0: a Radio Speaker's Tone (a steady beep, looped while its frequency is on). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAKER_TONE = register("speaker.tone");

    /** Every one, for the tests. */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> ALL = List.of(TUNER_TUNE, TUNER_STEP, TELEGRAPH_KEY,
            TELEGRAPH_SOUNDER, PAGER_BEEP, METER_TICK, TOWER_ON, TOWER_OFF, STATION_SIREN, SATELLITE_LAUNCH, SPEAKER_TONE);

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
