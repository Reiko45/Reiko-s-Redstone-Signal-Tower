#!/usr/bin/env python3
"""
v0.59.0: makes the mod's own sounds from scratch (no recordings): each is synthesized with numpy,
written as a mono 44.1 kHz WAV and encoded to Ogg Vorbis with ffmpeg (Minecraft only places mono
sounds in the world). Also writes sounds.json. Run from the tools folder:

    python3 make_sounds.py

The random parts (static, clicks) use a fixed seed, so the files come out the same every time.
"""
import json
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

MOD = "reikos_signal_tower"
ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "src" / "main" / "resources" / "assets" / MOD / "sounds"
RATE = 44100
rng = np.random.default_rng(20261109)


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def silence(seconds):
    return np.zeros(int(seconds * RATE))


def envelope(n, attack=0.005, release=0.03):
    """A flat envelope with short fades at both ends, so nothing clicks."""
    env = np.ones(n)
    a = max(1, int(attack * RATE))
    r = max(1, int(release * RATE))
    env[:a] = np.linspace(0, 1, a)
    env[-r:] = np.minimum(env[-r:], np.linspace(1, 0, r))
    return env


def decay(n, seconds):
    return np.exp(-np.arange(n) / (seconds * RATE))


def lowpass(x, cutoff):
    """A simple one-pole low-pass (enough for shaping noise)."""
    alpha = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, low, high):
    return lowpass(highpass(x, low), high)


def sweep(f0, f1, seconds, shape="exp"):
    """A sine whose frequency glides from f0 to f1."""
    n = int(seconds * RATE)
    if shape == "exp":
        f = f0 * (f1 / f0) ** (np.arange(n) / n)
    else:
        f = np.linspace(f0, f1, n)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.sin(phase)


def soft_square(freq, seconds, hardness=4.0):
    """A square wave with rounded corners (tanh of a sine), less harsh than a true square."""
    x = np.sin(2 * np.pi * freq * t(seconds))
    return np.tanh(hardness * x) / np.tanh(hardness)


def place(base, sound, at):
    """Adds {@code sound} into {@code base} starting at {@code at} seconds (growing base if needed)."""
    start = int(at * RATE)
    end = start + len(sound)
    if end > len(base):
        base = np.concatenate([base, np.zeros(end - len(base))])
    base[start:end] += sound
    return base


def click(body=420, ring=2600, length=0.03, bright=1.0):
    """A mechanical click: a noise burst with a metal ring and a low knock, decaying fast."""
    n = int(length * RATE)
    noise = rng.uniform(-1, 1, n) * decay(n, 0.002)
    tt = np.arange(n) / RATE
    ring_part = np.sin(2 * np.pi * ring * tt) * decay(n, 0.006) * 0.6 * bright
    knock = np.sin(2 * np.pi * body * tt) * decay(n, 0.008) * 0.8
    return noise * 0.7 + ring_part + knock


def static(seconds, low=500, high=5000):
    n = int(seconds * RATE)
    crackle = rng.uniform(-1, 1, n)
    # Pops: a few sharp spikes, like a radio between stations.
    pops = np.zeros(n)
    for i in rng.integers(0, n, max(1, int(seconds * 60))):
        pops[i] = rng.choice([-1, 1]) * rng.uniform(1.5, 3.0)
    return bandpass(crackle * 0.6 + pops, low, high)


def normalize(x, peak=0.89):
    m = np.max(np.abs(x))
    return x if m == 0 else x * (peak / m)


# ---- The sounds ----

def tuner_tune():
    """Static that a whistle sweeps through and settles out of: tuning in."""
    d = 0.5
    n = int(d * RATE)
    noise = static(d) * np.linspace(1.0, 0.15, n) ** 1.5
    whistle = np.concatenate([sweep(2400, 900, 0.32), sweep(900, 1000, d - 0.32, "lin")])[:n]
    whistle *= np.concatenate([np.linspace(0.2, 0.7, int(0.32 * RATE)), np.linspace(0.7, 0.0, n - int(0.32 * RATE))])[:n]
    return normalize((noise * 0.8 + whistle * 0.55) * envelope(n, 0.004, 0.08))


def tuner_step():
    """A detent on the dial: a small bright click."""
    return normalize(click(body=900, ring=3800, length=0.025) * envelope(int(0.025 * RATE), 0.0005, 0.008), 0.6)


def telegraph_key():
    """Morse on a straight key, ".-." (R): each press is a click down and a lighter click up."""
    unit = 0.07
    out = silence(0.05)
    at = 0.01
    for symbol in ".-.":
        held = unit if symbol == "." else unit * 3
        out = place(out, click(body=380, ring=2300, length=0.03), at)
        out = place(out, click(body=520, ring=3100, length=0.025, bright=0.6) * 0.6, at + held)
        at += held + unit
    return normalize(out * envelope(len(out), 0.001, 0.02))


def telegraph_sounder():
    """A sounder's armature: a sharp clack down, a softer click back up."""
    out = silence(0.16)
    out = place(out, click(body=300, ring=1900, length=0.04), 0.0)
    out = place(out, click(body=600, ring=2700, length=0.03, bright=0.5) * 0.5, 0.09)
    return normalize(out * envelope(len(out), 0.0005, 0.02))


def pager_beep():
    """One quick high beep, like an old pager's (an alert plays it three times, 4 ticks apart)."""
    beep = soft_square(2730, 0.09, 3.0) * envelope(int(0.09 * RATE), 0.003, 0.012)
    return normalize(beep, 0.7)


def meter_tick():
    """A Geiger counter's tick."""
    n = int(0.018 * RATE)
    x = highpass(rng.uniform(-1, 1, n), 1500) * decay(n, 0.0025)
    x[:3] += np.array([1.0, -0.8, 0.4])
    return normalize(x * envelope(n, 0.0002, 0.004), 0.7)


def relay_clunk():
    n = int(0.06 * RATE)
    tt = np.arange(n) / RATE
    thud = np.sin(2 * np.pi * 110 * tt) * decay(n, 0.02)
    return thud + click(body=250, ring=1500, length=0.06) * 0.5


def tower_on():
    """A relay clunks, a carrier whines up and steadies, with a little static."""
    d = 0.75
    n = int(d * RATE)
    whine = np.concatenate([sweep(220, 1180, 0.38), sweep(1180, 1200, d - 0.38, "lin")])[:n]
    whine += 0.35 * np.concatenate([sweep(440, 2360, 0.38), sweep(2360, 2400, d - 0.38, "lin")])[:n]
    shape = np.concatenate([np.linspace(0, 1, int(0.38 * RATE)), np.linspace(1, 0, n - int(0.38 * RATE)) ** 2])[:n]
    hum = np.sin(2 * np.pi * 120 * t(d)) * 0.25 * shape
    noise = static(d, 800, 4000) * 0.25 * shape
    out = place(np.zeros(n), (whine * 0.6 + hum + noise) * shape, 0.0)
    out = place(out, relay_clunk() * 0.9, 0.0)
    return normalize(out * envelope(len(out), 0.002, 0.06))


def tower_off():
    """The carrier sinks away into static, then a relay clunks."""
    d = 0.7
    n = int(d * RATE)
    whine = sweep(1200, 140, d) + 0.3 * sweep(2400, 280, d)
    shape = np.linspace(1, 0, n) ** 1.6
    noise = static(d, 600, 3500) * 0.35 * np.linspace(0.6, 0, n)
    out = (whine * 0.6 * shape + noise)
    out = place(out, relay_clunk() * 0.9, d - 0.06)
    return normalize(out * envelope(len(out), 0.004, 0.03))


def station_siren():
    """A two-tone siren, high-low three times, with a slight wobble."""
    d = 1.6
    tt = t(d)
    step = (np.floor(tt / 0.27) % 2 == 0)
    freq = np.where(step, 940.0, 740.0)
    # Glide between the tones a little instead of jumping.
    smooth = lowpass(freq, 40)
    smooth *= 1 + 0.006 * np.sin(2 * np.pi * 6 * tt)
    phase = 2 * np.pi * np.cumsum(smooth) / RATE
    x = np.tanh(2.5 * np.sin(phase)) + 0.25 * np.sin(2 * phase)
    return normalize(x * envelope(len(x), 0.05, 0.2), 0.8)


def satellite_launch():
    """v0.61.0: a rocket lifting off: an ignition bang, then a deep roar with crackle that fades as it climbs."""
    d = 6.0
    tt = t(d)
    roar = lowpass(rng.standard_normal(len(tt)), 180) * 3.2
    rumble = lowpass(rng.standard_normal(len(tt)), 60) * 5.0
    crackle = highpass(rng.standard_normal(len(tt)) * (rng.random(len(tt)) < 0.004), 1500) * 1.4
    hiss = bandpass(rng.standard_normal(len(tt)), 900, 3500) * 0.35
    # Loudest as it clears the pad, then it climbs away (quieter, and duller).
    swell = np.minimum(1.0, tt / 0.6) * np.exp(-np.maximum(0, tt - 1.2) / 2.2)
    dull = np.exp(-np.maximum(0, tt - 1.5) / 1.2)
    x = (roar + rumble) * swell + (crackle + hiss) * swell * dull
    bang = lowpass(rng.standard_normal(int(RATE * 0.25)), 400) * decay(int(RATE * 0.25), 0.07) * 4.0
    x = place(x, bang, 0.0)
    return normalize(x * envelope(len(x), 0.01, 0.5), 0.9)


def speaker_tone():
    """v0.62.0: a Radio Speaker's Tone: one second of a 740 Hz sine (F#5, so a speaker's notes
    tune it like its other sounds) with a little of its octave, a whole number of cycles so it
    loops without a click. No fade: the client loops it while the frequency is on."""
    tt = t(1.0)
    x = np.sin(2 * np.pi * 740 * tt) + 0.18 * np.sin(2 * np.pi * 1480 * tt)
    return x / np.max(np.abs(x)) * 0.55


def printer_print():
    """v0.63.0: a Ticker Tape Printer printing: the type wheel's quick uneven chatter over a motor's
    hum, then the clunk of the tape moving on."""
    d = 0.7
    out = silence(d)
    at = 0.02
    while at < 0.5:
        out = place(out, click(body=700, ring=3400, length=0.018, bright=0.7) * rng.uniform(0.35, 0.7), at)
        at += rng.uniform(0.022, 0.045)
    hum_t = t(0.55)
    hum = (np.sin(2 * np.pi * 110 * hum_t) + 0.4 * np.sin(2 * np.pi * 220 * hum_t)) * 0.12 * envelope(len(hum_t), 0.03, 0.08)
    out = place(out, hum, 0.0)
    out = place(out, click(body=260, ring=1500, length=0.05) * 0.9, 0.56)
    return normalize(out * envelope(len(out), 0.002, 0.03), 0.8)


SOUND_LIST = {
    # event id: (file name, the function that makes it)
    "tuner.tune": ("tuner_tune", tuner_tune),
    "tuner.step": ("tuner_step", tuner_step),
    "telegraph.key": ("telegraph_key", telegraph_key),
    "telegraph.sounder": ("telegraph_sounder", telegraph_sounder),
    "pager.beep": ("pager_beep", pager_beep),
    "meter.tick": ("meter_tick", meter_tick),
    "tower.on": ("tower_on", tower_on),
    "tower.off": ("tower_off", tower_off),
    "station.siren": ("station_siren", station_siren),
    "satellite.launch": ("satellite_launch", satellite_launch),
    "speaker.tone": ("speaker_tone", speaker_tone),
    "printer.print": ("printer_print", printer_print),
}


def write_ogg(samples, path):
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
                    "-fflags", "+bitexact", "-flags:a", "+bitexact", "-map_metadata", "-1", str(path)], check=True)
    Path(wav_path).unlink()


def main():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    sounds_json = {}
    for event, (file, make) in SOUND_LIST.items():
        write_ogg(make(), SOUNDS / f"{file}.ogg")
        sounds_json[event] = {"subtitle": f"subtitles.{MOD}.{event}", "sounds": [f"{MOD}:{file}"]}
    (SOUNDS.parent / "sounds.json").write_text(json.dumps(sounds_json, indent=2) + "\n")
    print(f"{len(SOUND_LIST)} sounds written to {SOUNDS}")


if __name__ == "__main__":
    main()
