# Design notes

**Target:** Minecraft 1.21.1 · NeoForge 21.1.265 · ModDevGradle 2.0.148 · Java 21 · Parchment 2024.11.17
**Mod id:** `reikos_signal_tower` · **Package:** `com.reiko.signaltower`

## Rules

- **Frequencies** are whole numbers from 1 to 999,999,999, stored in each transmitter and receiver's block entity (NBT key `frequency`). New blocks start on 1.
- **Reach** belongs to the transmitter only: `baseReach + reachPerBlockOfHeight × max(0, y − seaLevel)`, capped by `maxReach` if that's above 0. Defaults are 32 and 8, with no cap.
- **Links:** a receiver hears a transmitter on its frequency if their *horizontal* distance is at most the transmitter's reach. Height difference doesn't matter.
- **Sea level** comes from the dimension's chunk generator (`ServerLevel.getChunkSource().getGenerator().getSeaLevel()`), because `Level.getSeaLevel()` is hard-coded to 63 in 1.21.1.
- **Compact Radio Receiver:**
  - It's a `RadioReceiverBlock` subclass with the button's `face`/`facing` properties, shapes, attach rules and model rotations, and it shares the receiver block entity type.
  - Like a lever, it gives weak power in every direction and strong power into the block it's attached to (`getDirectSignal`).
  - When its output changes, it also updates that block's neighbors (`afterOutputChanged`).
  - It pops off with drops via `updateShape` when its support goes.
- **Wall Redstone:**
  - `facing` points away from the wall. Its `up`/`down`/`left`/`right` arms are drawn only toward in-plane neighbors, using vanilla's dust textures, tinted with `RedStoneWireBlock.getColorForPower`.
  - Power works like vanilla dust:
    - while calculating, it switches off its own output (`shouldSignal`);
    - it takes the best non-wire signal from all 6 neighbors, or the strongest neighboring wire (its own or vanilla dust) minus 1.
  - **Vanilla dust has its own private switch**, so plain neighbors would latch each other on. To stop that, Wall Redstone hands vanilla dust 1 less, and strongly powers its wall block at 1 less. Mixed loops then always fade to 0 (covered by a GameTest).
  - It re-works its connections in `onPlace`, so `/fill` and `/setblock` give correct lines.
- **Remote Control:**
  - It isn't a block, so `SignalNetwork` keeps remote signals separately, per frequency:
    - a **pulse**: not saved; it ends on time via `LevelTickEvent.Post`;
    - a **latch**: saved; any remote on that frequency toggles it.
  - Both send full strength from where the player stood, with that spot's height-based reach, and bump the frequency's revision so receivers react within a tick.
  - The item stores `frequency` and `remote_toggle_mode` data components. A 10-tick cooldown stops held right-click from flickering a toggle.
- **Input:** a transmitter's input is the strongest of `getBestNeighborSignal` at the transmitter and at every mast in the unbroken column directly below it.

## How it works

- `SignalNetwork` is `SavedData`, one per dimension. It maps transmitter position → (frequency, power, reach), indexed by frequency, with a revision counter per frequency (not saved).
- **Transmitters** update their entry when:
  - a neighbor changes,
  - a mast in their column reports a change (masts walk up their column on neighbor change, placement and removal),
  - their frequency changes,
  - and once a second in their block entity tick, which also catches config changes and world edits.
- **Receivers** re-scan only when their frequency's revision changes or their own frequency changes, plus a once-a-second fallback. Changes arrive within one tick.
- **Cleanup:** entries whose transmitter block is gone are removed the next time a receiver in range scans.
- **Dropped items:** the frequency rides on the item as the `reikos_signal_tower:frequency` data component. The block entity's `collectImplicitComponents` adds it, and the loot table's `copy_components` copies it onto the drop. `applyImplicitComponents` reads it back when the block is placed.
- **Tuner:** the screen sends `SetTunerFrequencyPayload` (hand + value). The server checks the player is holding a tuner and clamps the value. Tuning and copying happen in `FrequencyTunerItem.useOn` on the server. Radio blocks return `SKIP_DEFAULT_BLOCK_INTERACTION` for any held item so the tuner's `useOn` runs, and so masts can be placed against them.

## Verified

- 17 GameTests pass (`./gradlew runGameTestServer`). They cover linking, signal strength, frequency isolation, reach growing with height, masts carrying the signal, the tuner's tune and copy actions, and drops keeping their frequency.
- In the real client:
  - models, the glowing warning light and items render correctly;
  - typing a frequency into the Tuner screen updates the held tuner on the server;
  - tuning a receiver links it and lights its lamps;
  - the status readout shows the right values.

## History

- **v0.5.0:** added the Remote Control.
- **v0.4.0:** added Wall Redstone.
- **v0.3.0:** added the Compact Radio Receiver (button-sized).
- **v0.1.0:** dye-colored channels (16), reach from stacked masts on both ends. Replaced in v0.2.0 at Reiko's request.

## Ideas for later

- Show the frequency on the block (floating text, or a Jade/WTHIT plugin)
- Named frequencies ("Base Door") as aliases for numbers
- Receivers that also gain range with height, or terrain blocking the signal
- Links between dimensions
- A blinking warning light, or a separate light block for the top of tall towers
- Strong-power option on receivers; an inverted receiver
- Signal falloff at the edge of reach
