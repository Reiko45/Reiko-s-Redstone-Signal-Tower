# Design notes

**Target:** Minecraft 1.21.1 · NeoForge 21.1.265 · ModDevGradle 2.0.148 · Java 21 · Parchment 2024.11.17
**Mod id:** `reikos_signal_tower` · **Package:** `com.reiko.signaltower`

## Rules

- **Frequencies** are whole numbers from 1 to 999,999,999, stored in each transmitter, receiver and repeater's block entity (NBT key `frequency`). New blocks start on 1.
- **Reach** belongs to transmitters and repeaters: `baseReach + reachPerBlockOfHeight × max(0, y − seaLevel)`, capped by `maxReach` if that's above 0. Defaults are 32 and 8, with no cap.
- **Links:** a receiver hears a transmitter or repeater on its frequency if their *horizontal* distance is at most its reach. Height difference doesn't matter.
- **Radio Repeater:**
  - Listens for its frequency at its own position (like a receiver there) and sends the strongest signal it hears out again with its own reach, at the same strength. No redstone input.
  - It hears transmitters, remote pulses and latches, and other repeaters, but only signals that trace back to a real transmitter or remote press: two repeaters can't keep each other on.
  - Like a transmitter, it relays remote presses on any frequency when it's within the player's reach (`towersRelayRemotes`).
  - `LIT` block state (light level 7, amber sparks) while repeating. Recipe: Radio Receiver + Radio Transmitter (shapeless).
- **Radio Converter (v0.22.0):**
  - A repeater that listens on one frequency and sends on another (its output frequency), same strength, own reach. Teal light and sparks while converting.
  - Chains with repeaters and other converters; loops (even across frequencies, A to B and back) can't latch, for the same reason as repeaters.
  - Right-click with an empty hand opens a screen for both frequencies. The tuner sets and copies the one it listens on.
  - A remote press reaching it is carried onto its output frequency ("+ 1 converter", and "Heard by" counts receivers there).
  - Recipe: Radio Repeater + Redstone Comparator (shapeless). The dropped item keeps both frequencies.
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
- **Multi-Button Remote:**
  - Stores a `remote_buttons` component: exactly 4 `(frequency, toggle)` buttons plus a selected index. The record constructor normalizes size, selection and frequencies, so bad packets or commands can't break it.
  - Pressing reuses `RemoteControlItem.sendSignal`.
  - Sneak + scroll is caught client-side by the cancellable `InputEvent.MouseScrollingEvent` (so the hotbar doesn't move) and sent as `SelectRemoteButtonPayload`.
  - The keypad sends `ConfigureMultiRemotePayload` (settings, plus an optional button to press). The server checks the item and has a short cooldown.
- **Pulse length:**
  - Stored in ticks, 2–200 (0.1–10s, default 20).
  - The single remote keeps it in its own `remote_pulse_ticks` component. Each Multi-Button Remote button has a `pulse_ticks` field, optional in saved data, so older remotes load as 1s.
  - The screens step through the presets in `PulseLength`. Sneak-click steps down.
  - `RemoteControlItem.sendSignal` takes the length and clamps it.
- **Signal light:**
  - `SignalNetwork` also tracks loaded receivers by frequency (not saved). A receiver checks in on its first tick and when re-tuned, and checks out in `setRemoved`, which covers both breaking and chunk unload.
  - On a press, `sendSignal` counts receivers within reach and sets a `remote_light` component (heard + show-until time). It's synced but never saved.
  - The client registers an item property `reikos_signal_tower:light` (0 off, 0.5 green, 1 red), and the item models' overrides pick the matching texture.
  - Both remotes override `shouldCauseReequipAnimation`, so the light changing doesn't replay the pull-out animation.
- **Button names:**
  - Each `RemoteButtons.Button` has a `name` (optional in saved data, so older remotes load unnamed). The record constructor cleans it with `StringUtil.filterText`, strips spaces and cuts it to 24 characters, on the server and the client alike.
  - `Button.label(index)` gives the name, or "Button N", and every message and tooltip uses it.
- **Button colors:**
  - Each `RemoteButtons.Button` has a `DyeColor color` (optional in saved data; null only on a button that isn't on a remote yet). The `RemoteButtons` constructor fills in its slot's default: red, lime, blue, yellow.
  - The item model has 5 layers (the vanilla maximum): the body (with the status light) and one white-and-grey button per layer 1-4. `MultiRemoteItem.tintColor` (registered in `RegisterColorHandlersEvent.Item`) multiplies each with its button's dye color, so the shading is baked into the greys. The green/red light models use the same button layers.
  - `Button.label` draws the name in `RemoteButtons.textColor`: the dye's color, lightened just enough for dark dyes to stay readable on tooltips.
  - The keypad has a color column: a button with the colored square drawn over it in `render`, click for the next dye, sneak-click for the previous one, and a tooltip naming the color.
- **Radio Pager:**
  - `PagerItem.inventoryTick` runs every tick on the server for each pager in a player's inventory (including off-hand and armor slots) and calls `checkSignal`: `SignalNetwork.listen` at the player's block position, the same rule as a receiver there. Every tick, so even a 0.1s remote pulse is caught.
  - The last strength is kept in a `pager_signal` component (saved, so reloading with the signal on doesn't re-alert; synced, so the client's `pager_on` item property can light the screen). Only a change from 0 to more than 0 alerts.
  - An alert shows an action-bar message (the anvil name if it has one) and plays `NOTE_BLOCK_BIT` with `ServerPlayer.playNotifySound`, so only the owner hears it. The other two beeps come from a `pager_beeps` component (synced, never saved), one every 4 ticks. `pager_silent` turns the beeps off.
  - Chat log: on by default (`pager_chat_log` component, only stored as `false`). An alert also goes to chat as `PagerItem.chatLine(dayTime, alert)`: "[Day N, HH:MM] " from `level.getDayTime()`, shifted 6000 ticks so days start at midnight (day time 0 is 06:00), then the alert. The pager's screen has Sound and Chat log buttons and saves with `ConfigurePagerPayload`, which only re-tunes (re-arming the alert) if the frequency really changed.
  - Catch-up: `SignalNetwork` keeps, per frequency, the last 32 `TurnOn`s (game time, day time, coverage), recorded when a transmitter goes from off (or absent, or another frequency) to on, on every remote pulse, and when a toggle switches on; saved as `turn_ons`. Stamps come from `tick(gameTime, dayTime)`. `PlayerLoggedOutEvent` → `PagerItem.markAway` stamps every pager with `pager_away_since` (it fires before the player is saved). `PlayerLoggedInEvent` → `PagerItem.catchUp` asks `turnOnsSince(frequency, since, player position)` for each stamped pager, removes the stamp and posts one chat line per pager that missed something, or one "heard nothing" line (skipped for a singleplayer world's owner, since that world is paused while they're away).
  - Re-tuning (`PagerItem.setFrequency`, used by the screen packet, scrolling and copying) clears the stored signal, so the new frequency alerts if it's already on.
  - `RemoteControlItem.sendSignal` adds `PagerItem.countPagers` (pagers on that frequency carried by players inside the coverage) to "Heard by". The locator doesn't include pagers, so it can't track players.
- **Signal Scanner:**
  - `SignalNetwork.scanAt(level, pos)` goes through every transmitter in the dimension and keeps, per frequency, the strongest one whose reach covers `pos` (the nearest, among equally strong ones), plus a count. Pulses and latches covering `pos` make a frequency strength 15 with source `REMOTE_PULSE`/`REMOTE_TOGGLE`, and no distance or direction, so it can't locate players. Sorted strongest first, then by frequency; at most 64. Stale transmitter entries are dropped, as in `listen`.
  - `ScannerScreen` (not a pause screen, so it stays live in singleplayer) sends `ScannerRequestPayload` every 20 ticks. The server answers only players holding a scanner, with `ScannerResultPayload`.
  - Tune reuses the existing packets: `ConfigureRadioItemPayload` for the tuner, Remote Control (sending its current mode and pulse length back unchanged) and pager; `ConfigureMultiRemotePayload` with the selected button re-tuned (`SignalScannerItem.retuneSelected`) for the Multi-Button Remote.
  - Tracking: clicking a row (with a transmitter in it) sends `TrackTransmitterPayload`. The server stores the transmitter from `SignalNetwork.strongestTransmitter` (the same pick as the row, shared through `bestTransmitters`) in the scanner's `scanner_target` component (frequency, position, dimension; synced). `SignalScannerItem.inventoryTick` checks once a second that it's still a transmitter on that frequency (`transmitterFrequencyAt`), and drops it with a "lost track" message if not.
  - `ScannerHud` is a GUI layer registered above `VanillaGuiLayers.BOSS_OVERLAY`. While a scanner with a target is held (main hand, then off-hand), it draws `textures/gui/scanner_arrow.png` at the top center, rotated by `ScannerTarget.relativeAngle` (0 = ahead, clockwise; from the interpolated position and view yaw), and the distance (plus "N up/down" when 3 or more). In another dimension it just says so; within a block it says "you're there".
- **Favorites and scrolling (tuner, Remote Control, pager):**
  - `RadioFavorites` holds the shared logic. `isScrollable` is true for the tuner, the Remote Control and the pager; the Multi-Button Remote keeps sneak + scroll for picking its button.
  - Favorites live in the `tuner_presets` component (the name dates from v0.15.0, when only the tuner had them; kept so saved favorites still load). It's a `TunerPresets` record that always has 5 slots, 0 meaning empty, removed entirely when every slot is empty.
  - `RadioFavorites.scroll(stack, up, favorites)`: ±1 (clamped), or `TunerPresets.next` to the next (scroll down) or previous favorite in slot order, wrapping and skipping empty slots. `RadioFavorites.setFrequency` goes through `PagerItem.setFrequency` for pagers, so they re-arm. The message names the item ("Remote Control: frequency 42 (favorite 2)").
  - The client's `MouseScrollingEvent` handler checks the main hand, then the off-hand: a Multi-Button Remote sends `SelectRemoteButtonPayload`, a scrollable item sends `ScrollFrequencyPayload` (with `Screen.hasControlDown()` for favorites). The event is cancelled so the hotbar doesn't move. The server plays a quiet `NOTE_BLOCK_HAT` tick to the player only.
  - `FrequencyScreen` shows the favorites row for all three (`ClientHooks.openFrequencyScreen` takes the favorites). Edits go at once in `ConfigureFavoritesPayload`; right-click is caught in `mouseClicked`. The layout is worked out from a `top` so the remote's version (locator lines, favorites, Mode/Length, Done) fits in a 240-pixel-high GUI.
- **Locator:**
  - An open remote screen sends `RemoteScanRequestPayload` (up to 4 frequencies) every 20 ticks, or sooner after typing. The server only answers players holding a remote.
  - It uses the receiver tracking (`SignalNetwork.scanReceivers`): count within reach, and nearest within 2× reach, with distance and 8-way compass direction.
  - It replies with `RemoteScanResultPayload`, the mod's only server-to-client packet. That goes to `ClientHooks.onRemoteScan`, and from there to the open screen.
  - Nothing is transmitted, and receivers beyond 2× reach are never revealed.
- **Tower relay:**
  - A remote signal covers a list of `SignalNetwork.Coverage` areas (origin, reach, tower flag). The first is the player's own; `remoteCoverage` adds one for every transmitter within the player's reach (horizontally, any frequency), with that transmitter's stored reach. One hop only: towers don't relay to each other.
  - `RemoteControlItem.coverageFor` reads the `towersRelayRemotes` server config (default on). Presses, the "Heard by" count, the light and the locator all use the same coverage.
  - Pressing again during a pulse merges the new areas into it. A latch keeps the areas it was switched on with. A tower area only counts while its transmitter is still there (if its chunk is loaded), so breaking a tower cuts off a latch it was carrying within a second.
  - Latches save a `coverage` list, plus the old `pos`/`reach` keys (the player's own area) so older versions can still read them. Old saves without `coverage` load as just the player's area.
  - The locator's "nearest" hint covers receivers within 2× the player's own reach or inside any relayed area. `RemoteScanResultPayload` also carries the tower count for the screens' reach line.
  - Two older GameTests that check a far receiver hears nothing press with relays left out, because the GameTest grid puts other tests' transmitters within reach.
- **Input:** a transmitter's input is the strongest of `getBestNeighborSignal` at the transmitter and at every mast in the unbroken column directly below it.

## How it works

- `SignalNetwork` is `SavedData`, one per dimension. It maps transmitter position → (frequency, power, reach), indexed by frequency, with a revision counter per frequency (not saved).
- **Repeaters and converters** are kept the same way: position → `Repeater(frequency, output, reach)` (a repeater has `output == frequency`), indexed both by the frequency they listen on and the one they send on, saved as `repeaters` (`output` only written for converters, so 0.20.0 saves load as repeaters). `RadioRepeaterBlockEntity` (shared by both blocks; a converter keeps its `output_frequency`) checks in on its first tick, when re-tuned and once a second; `onRemove` takes it out. What each sends isn't stored but worked out by `SignalNetwork.resolve(level)`:
  - each starts with what it hears straight from transmitters and remote signals on the frequency it listens on (`listenDirect`);
  - then, strongest first, each one that hears something passes that strength to the repeaters and converters inside its reach that listen on the frequency it sends on (a max-strength spread, O(n²) over the dimension). Because it only spreads out from real sources, a loop of repeaters, or of converters going A → B → A, with no source stays at 0.
  - One answer for the whole dimension, cached by `relayEpoch`, which goes up on any change to a relay and whenever a frequency some relay listens on is bumped; also re-worked after 20 ticks (for changes nothing reports, such as a remote's relay tower being broken). Stale entries (block gone, chunk loaded) are dropped while resolving.
  - `bump(frequency)` also bumps the output frequencies of converters listening on it, and so on down the line (with a visited set for loops), so receivers on a converted frequency re-scan as soon as the source changes.
  - `listen` = `listenDirect` plus every repeater/converter sending on that frequency whose reach covers the position (its resolved strength; counts toward `transmittersInReach`). So receivers, pagers and everything else built on `listen` hear them with no changes of their own.
  - `carried(level, frequency, coverage)` extends a coverage list through repeaters and converters (breadth-first over (frequency, area) pairs, each relay once, not re-adding one already relaying as a tower), giving the areas reached on each frequency plus how many repeaters and converters were added. `RemoteControlItem.sendSignal` uses it for "Heard by" (receivers and pagers on every frequency reached), the light and the "+ N repeaters + N converters" in the message; `recordTurnOn` stores a turn-on on every frequency it reached, so the pager catch-up covers converted frequencies too. `withRepeaters` is its same-frequency part, used by the locator.
  - `remoteCoverage` adds repeaters and converters as relay towers next to transmitters, and `RemoteSignal.reaches` accepts any of them (`SignalNetwork.isTower`).
  - The scanner's `bestTransmitters` also offers each repeater and converter covering the position, under the frequency it sends on (resolved strength, `Source.REPEATER` or `Source.CONVERTER`, added at the end of the enum since it's sent by ordinal). Ties go to the stronger, then nearer, then a transmitter. Tracking accepts any of them (`towerFrequencyAt` gives the frequency it sends on).
  - Converter screen: `RadioConverterBlock.useWithoutItem` sends `OpenConverterScreenPayload` (position, both frequencies, current strength, reach; block entities aren't synced) to the player; `ConverterScreen` sends `ConfigureConverterPayload` back, which only applies if `player.canInteractWithBlock(pos, 1.0)` and the block is a converter, then sets both with `setFrequencies`.
- **Jade (optional, v0.21.0):**
  - `compat/jade/SignalTowerJadePlugin` (`@WailaPlugin`) registers one provider (`reikos_signal_tower:radio`, a toggle in Jade's plugin settings) as both the block data provider and the block component for `RadioBlock` (transmitter, both receivers, repeater, converter) and `RadioMastBlock`. A converter (and a mast under one) shows "Listens on" and "Sends on" instead of "Frequency". Only Jade loads it, so nothing else references Jade's API.
  - The work is in `compat/RadioInfo`, which doesn't touch Jade: `read(ServerLevel, pos)` (server) gathers frequency, power (transmitter input, repeater's resolved strength, receiver output), reach, towers in range (`listen().transmittersInReach()`), and for a mast the block on top of its column, its distance and frequency. It goes into Jade's server data under the key `reikos_signal_tower`. `lines(state, data)` (client) builds the tooltip lines; without server data (Jade missing on the server) it falls back to the block state (`POWERED`, `LIT`, `POWER`).
  - Priority 100, after Jade's own body lines, so for receivers it removes Jade's generic "Power: N" redstone line (`JadeIds.MC_REDSTONE`) and shows "Output" instead. Jade tags each line with the provider that added it, which is what makes that removal work.
  - Build: `compileOnly` and `localRuntime` (dev runs only) on `maven.modrinth:jade:15.10.6+neoforge` from Modrinth's maven (`exclusiveContent`, group `maven.modrinth` only). `-PnoJade` drops it from the dev runs. `neoforge.mods.toml` lists Jade as an optional dependency, `[15.0,)`.

## Verified

- 88 GameTests pass (`./gradlew runGameTestServer`), with Jade and with `-PnoJade`. They cover linking, signal strength, frequency isolation, reach growing with height, masts carrying the signal, the tuner's tune and copy actions, and drops keeping their frequency.
- In the real client:
  - models, the glowing warning light and items render correctly;
  - Radio Converter, added to the repeater demo: placed by hand 20 blocks from the first repeater, it showed in Jade as "Listens on: 1 / Sends on: 1 / Nothing to convert". Right-clicking it opened its screen; typing 2026, Tab, 3001, Enter set it ("Radio Converter: listens on 2026, sends on 3001"), and Jade then said "Converting: 15". A receiver on 3001 about 50 blocks away lit its lamp ("Output: 15 / Towers in range: 1"); the scanner there listed "2,026: Repeater, 16 blocks south-east (+1 more)" and "3,001: Converter, 52 blocks west". A 2026 remote press beside it said "reach 56 blocks + 2 towers + 1 repeater + 1 converter | Heard by 3 receivers". Lever off: the 3001 lamp went out and the converter said "Nothing to convert"; on again: lit;
  - with Jade 15.10.6: looking at the transmitter showed "Frequency: 2026 / Sending: 15 / Reach: 56 blocks", the repeater "Repeating: 15 / Reach: 88 blocks", a mast "Top: Radio Repeater, 3 blocks up / Frequency: 2026", the far receiver "Output: 15 / Towers in range: 2" (with Jade's own "Power" line gone), and a compact receiver on an unused frequency "Output: off / Towers in range: 0". The plugin's switch shows under the mod's name in Jade's Plugin Settings. Without Jade (`-PnoJade`) the game loads normally and the right-click readout works;
  - repeater chain in a superflat world: a ground transmitter (reach 56) with a lever, a repeater on 4 masts 50 blocks away (reach 88) and a receiver with a lamp 130 blocks from the transmitter. Flipping the lever lit the repeater ("Repeating 15 | 7 blocks above sea level | Reach 88 blocks") and the far lamp ("Output 15 | Towers in range: 1"); the scanner there said "Repeater, 78 blocks west" and tracked it ("78 blocks, 4 up"). A second repeater placed by hand from its item (keeping frequency 2026) 85 blocks further lit a lamp 190 blocks from the transmitter. Lever off: everything went off ("Nothing to repeat"); on again: both lamps lit. A remote press beside the first repeater said "reach 56 blocks + 1 tower + 1 repeater | Heard by 2 receivers";
  - typing a frequency into the Tuner screen updates the held tuner on the server;
  - tuning a receiver links it and lights its lamps;
  - the status readout shows the right values;
  - on a dedicated server: logged out holding three pagers; switched transmitter 42 off and on twice and 301 on once from the console; after a server restart and logging back in, chat said "Gate: while you were away, frequency 42 turned on 2 times, last on Day 1 at 18:34" and "Workshop: ... frequency 301 turned on once, on Day 1 at 20:07" (the third pager stayed quiet); logging out and in again with nothing happening said "Your 3 pagers heard nothing while you were away";
  - the scanner tracked frequency 42 (row in green with "»"); the arrow pointed behind-left facing north, ahead facing south and right facing east, matching the target a little west of due south, 27 blocks away; tracking the tower showed "3 blocks, 30 up"; breaking its transmitter showed "Lost track of frequency 77" and removed the arrow;
  - three remote presses put three "[Day 1, 12:00] Radio Pager: frequency 12 is ON (strength 15)" lines in chat; after `/time set 74500` and `/time add 15700` the stamps read Day 4, 08:30 and Day 5, 00:12; a pager with its chat log switched off in its screen only showed the alert above the hotbar;
  - sneak + scroll took a Remote Control from 9 to 10 and a pager from 5 to 4; favorites saved in the remote's screen (which fits the locator lines, favorites, Mode/Length and Done); Ctrl-scrolling wrapped from 42 to favorite 1; a tuner kept the favorites it saved in v0.15.0;
  - sneak + scroll took the tuner from 40 to 42 without moving the hotbar; favorites saved, cleared and used in the screen; Sneak + Ctrl + scroll went 42 → 77 → 5 → 42 (skipping the empty slot); favorites were still there after reloading the world and show in the tooltip;
  - with five transmitters and a remote toggle around, the scanner lists all five frequencies with the right strengths, distances and directions (checked by hand), and Tune set the off-hand Remote Control to 42 (confirmed with `/data get`, mode and pulse length unchanged);
  - a pager in the hotbar goes off from a Multi-Button Remote press (the remote's light goes green from the pager alone) and a renamed one when the tower next to the player is powered; each screen lights while its signal is on; the sound switch and frequency screen work;
  - colored buttons show on the remote in hand and in the hotbar, change as soon as the keypad saves, and keep working with the status light; names show in their colors in the tooltip and press message;
  - standing at the foot of a 30-mast tower, the remote screen counts a receiver 150 blocks away as in range ("plus 1 tower relaying"), and a toggle press lights its lamp.

## History

- **v0.22.0:** Radio Converter (frequency converter).
- **v0.21.0:** Jade tooltip (optional).
- **v0.20.0:** Radio Repeater (repeater towers).
- **v0.19.0:** pager catch-up.
- **v0.18.0:** scanner tracking arrow.
- **v0.17.0:** pager chat log.
- **v0.16.0:** scroll and favorites for the Remote Control and Radio Pager.
- **v0.15.0:** tuner scroll and favorites.
- **v0.14.0:** added the Signal Scanner.
- **v0.13.0:** added the Radio Pager.
- **v0.12.0:** button colors on the Multi-Button Remote.
- **v0.11.0:** Radio Transmitters relay remote presses (remote range boost).
- **v0.10.0:** remote locator.
- **v0.9.0:** button names on the Multi-Button Remote.
- **v0.8.0:** signal light on both remotes.
- **v0.7.0:** pulse length setting for both remotes.
- **v0.6.0:** added the Multi-Button Remote.
- **v0.5.0:** added the Remote Control.
- **v0.4.0:** added Wall Redstone.
- **v0.3.0:** added the Compact Radio Receiver (button-sized).
- **v0.1.0:** dye-colored channels (16), reach from stacked masts on both ends. Replaced in v0.2.0 at Reiko's request.

## Ideas for later

- Show the frequency on the block (floating text); a WTHIT plugin like the Jade one
- Named frequencies ("Base Door") as aliases for numbers
- Receivers that also gain range with height, or terrain blocking the signal
- Links between dimensions
- A blinking warning light, or a separate light block for the top of tall towers
- Strong-power option on receivers; an inverted receiver
- Signal falloff at the edge of reach
- Private frequencies, locked to a player or team, so other players' remotes can't trigger them
- A ring on the ground showing a transmitter's or repeater's reach
