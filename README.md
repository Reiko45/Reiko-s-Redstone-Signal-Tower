# Reiko's Redstone Signal Tower

A Minecraft **1.21.1 NeoForge** mod that adds radio towers for redstone. Put a transmitter high up on a tower and it sends your redstone signal to receivers far away. The higher the tower, the farther it reaches.

![Two radio towers](docs/images/towers.png)

## Blocks and items

| | What it does |
|---|---|
| **Radio Transmitter** | Broadcasts the redstone signal it receives (strength 0–15) on its frequency. The higher above sea level it sits, the farther it reaches. Its warning light glows while it's broadcasting. |
| **Radio Receiver** | Outputs the strongest signal it hears on its frequency, at the same strength. Works like a redstone block with a dimmer. |
| **Compact Radio Receiver** | A button-sized receiver. Sticks to walls, floors and ceilings like a button, and like a button it also powers the block it's stuck to, so it can drive redstone on the other side of a wall. Pops off (keeping its frequency) if that block is removed. |
| **Radio Mast** | Lattice tower segment for building your tower. A mast column also carries redstone **up**: power any mast in the column and the transmitter on top broadcasts it. |
| **Frequency Tuner** | Sets which frequency a transmitter or receiver uses. |
| **Remote Control** | A handheld transmitter, like a garage door opener. Press it to send a signal on its frequency: a 1-second pulse like a button, or toggle on/off. |
| **Multi-Button Remote** | A remote with 4 buttons, each with its own frequency and mode. One remote can run your door, lights and farm. |
| **Wall Redstone** | Redstone dust that sticks to the side of a block. It runs up, down and sideways along a wall, loses 1 strength per block like normal dust, connects to normal dust and redstone components, and powers the block it's on. |

![Compact Radio Receivers on a ceiling, on top of a block, on both sides of a block, and on a wall powering a lamp behind it](docs/images/compact-receivers.png)

![Wall Redstone running up and across a wall, fed by a lever and by normal dust](docs/images/wall-redstone.png)

## How to use it

1. **Build a tower.** Stack Radio Masts and put a Radio Transmitter on top.
2. **Power it from the ground.** Put a lever, redstone wire, or any other signal next to any mast in the column, or next to the transmitter itself.
3. **Tune it.** Right-click the air with a Frequency Tuner and type a frequency (any whole number from 1 to 999,999,999). Then right-click the transmitter with the tuner.

   ![The Frequency Tuner screen](docs/images/tuner-screen.png)

4. **Set up the far end.** Put a Radio Receiver where you want the signal and right-click it with the same tuner. It outputs whatever the transmitter is sending.

![Tuning a receiver](docs/images/tuned-receiver.png)

**Tuner controls:**
- Right-click the air: type a new frequency.
- Right-click a transmitter or receiver: tune it to the tuner's frequency.
- Sneak + right-click a transmitter or receiver: copy its frequency into the tuner.

**Remote Control:**
- Right-click: press the button.
- Sneak + right-click the air: change its frequency and switch between **Pulse** (1 second, like a stone button) and **Toggle** (on until you press it again).
- Sneak + right-click a transmitter or receiver: copy its frequency.
- Its reach uses the same height rule as a transmitter, measured from where you're standing, so pressing it from a hilltop or the top of a tower reaches farther.
- Any remote on the same frequency can switch a toggle off, so a lost remote can't leave something stuck on.

![The Remote Control screen](docs/images/remote-screen.png)

**Multi-Button Remote:**
- Right-click: press the **selected** button.
- Sneak + scroll: choose the selected button. Your hotbar slot doesn't move.
- Sneak + right-click the air: open the keypad. From there you can press any button, set each button's frequency and Pulse/Toggle mode, and click a button's number to make it the selected one.
- Sneak + right-click a transmitter or receiver: copy its frequency into the selected button.
- The tooltip lists all 4 buttons, with the selected one marked ».

![The Multi-Button Remote keypad](docs/images/multi-remote-keypad.png)

**Other details:**
- Both receivers are tuned with the Frequency Tuner and act the same; pick the full block or the compact one depending on how much room you have.
- Right-click a transmitter or receiver with an empty hand to see its frequency, signal, height and reach.
- Breaking a transmitter or receiver keeps its frequency on the dropped item, so you can move it without re-tuning.
- One transmitter can feed any number of receivers. A receiver that hears several transmitters on its frequency outputs the strongest.
- A transmitter keeps broadcasting its last signal while its chunk is unloaded, so long-distance links keep working.
- Don't put a receiver next to a transmitter (or its mast column) on the **same** frequency. That makes a loop that latches on, like a redstone torch loop.

### Reach

Reach is measured horizontally from the transmitter, so a receiver works at any height inside that circle.

```
reach = 32 + 8 × (blocks above sea level)
```

| Transmitter at | Height above sea level | Reach |
|---|---|---|
| Y=63 or lower (Overworld) | 0 | 32 blocks |
| Y=80 | 17 | 168 blocks |
| Y=100 | 37 | 328 blocks |
| Y=150 | 87 | 728 blocks |
| Y=319 (build limit) | 256 | 2,080 blocks |

Sea level is the dimension's own: Y=63 in the Overworld, Y=32 in the Nether, Y=−63 in a default superflat world. Building on a mountain counts too.

All three numbers (base reach, reach per block of height, and an optional maximum) are in the server config: `<world>/serverconfig/reikos_signal_tower-server.toml`, or Mods → Reiko's Redstone Signal Tower → Config.

### Recipes

```
Radio Transmitter       Radio Receiver          Radio Mast (x6)       Frequency Tuner
  .  L  .                 .  L  .                 B  .  B               .  T  .
  I  E  I                 I  E  I                 B  I  B               I  G  I
  I  T  I                 I  P  I                 B  .  B               I  R  I

L = Lightning Rod   E = Ender Pearl   T = Redstone Torch   P = Redstone Repeater
I = Iron Ingot      B = Iron Bars     G = Glass Pane       R = Redstone Dust
```

**Compact Radio Receiver:** put one Radio Receiver in the crafting grid to get a Compact Radio Receiver. It works the other way too, so you can switch back any time.

**Remote Control:**

```
  .  T  .        T = Redstone Torch   B = Stone Button
  I  B  I        I = Iron Ingot       E = Ender Pearl
  I  E  I
```

**Multi-Button Remote:** a Remote Control plus 3 Stone Buttons, in any arrangement in the crafting grid.

**Wall Redstone:** put one Redstone Dust in the crafting grid to get one Wall Redstone, and back again the same way. To place it, right-click the side of any solid block.

## Building and running

You need **Java 21**. If Gradle can't find it, it downloads one automatically.

```bash
./gradlew build              # builds build/libs/reikos_signal_tower-<version>.jar
./gradlew runClient          # starts Minecraft with the mod
./gradlew runServer          # starts a dedicated server with the mod
./gradlew runGameTestServer  # runs the automated in-game tests, then exits
```

On Windows use `gradlew.bat` instead of `./gradlew`.

**IntelliJ IDEA:** File → Open → pick this folder's `build.gradle` → Open as Project. Wait for the Gradle sync to finish, then use the generated run configurations (Client, Server, GameTestServer).

To play it in your own instance, copy the jar from `build/libs/` into the instance's `mods` folder. Every push to `main` is also built by GitHub Actions. The jar is attached to each run under **Actions → Build → Artifacts**.

## Project layout

```
src/main/java/com/reiko/signaltower/
  SignalTower.java                       mod entry point: registration, creative tabs, packets
  Config.java                            server config (reach settings)
  registry/ModRegistry.java              blocks, items, block entities, the frequency data component
  block/RadioBlock.java                  shared transmitter/receiver code: status readout, tooltips
  block/RadioTransmitterBlock.java       reads input, broadcasts
  block/RadioReceiverBlock.java          redstone output
  block/CompactRadioReceiverBlock.java   button-sized receiver that attaches to a face
  block/RadioMastBlock.java              tower segment, carries signal up to the transmitter
  block/WallRedstoneBlock.java           redstone dust for walls
  blockentity/RadioBlockEntity.java      stores the frequency, keeps it through break/re-place
  blockentity/RadioTransmitterBlockEntity.java
  blockentity/RadioReceiverBlockEntity.java
  item/FrequencyTunerItem.java           tune, copy, open the frequency screen
  item/RemoteControlItem.java            handheld transmitter: pulse or toggle
  item/MultiRemoteItem.java              4-button remote
  item/RemoteButtons.java                the 4 buttons' settings (data component)
  network/ConfigureMultiRemotePayload.java  keypad -> server (save, and optionally press)
  network/SelectRemoteButtonPayload.java sneak + scroll -> server
  network/ConfigureRadioItemPayload.java frequency screen -> server packet
  radio/SignalNetwork.java               per-dimension list of transmitters, saved with the world
  radio/RadioHelper.java                 height, reach, mast-column logic
  radio/Frequency.java                   frequency range (1 to 999,999,999)
  client/FrequencyScreen.java            the frequency (and remote mode) screen
  client/MultiRemoteScreen.java          the Multi-Button Remote keypad
  client/ClientHooks.java, SignalTowerClient.java   client-only setup
  gametest/SignalTowerGameTests.java     automated tests
src/main/resources/
  assets/reikos_signal_tower/            blockstates, models, textures, en_us.json
  data/reikos_signal_tower/              recipes, loot tables, recipe-book unlocks, test structure
tools/
  make_textures.py                       draws all textures (needs Pillow)
  make_data.py                           writes models, blockstates, lang, recipes, loot tables
docs/
  DESIGN.md                              how it works under the hood, ideas for later
```

The `tools/` scripts are how the textures and JSON files were generated. You can keep using them, or paint and edit the files directly. Re-running a script overwrites what it writes.

## Automated tests

`./gradlew runGameTestServer` boots a server and checks that:

1. a signal crosses the gap, turns off with the input, and stops when the transmitter is broken;
2. signal strength is kept (14 in, 14 out);
3. different frequencies don't hear each other, and re-tuning a receiver links it;
4. a receiver just out of reach goes live once the transmitter is raised 4 blocks on masts;
5. masts carry the signal up from any point in the column, and a gap in the column cuts it;
6. the tuner tunes a block on right-click and copies a block's frequency on sneak + right-click;
7. a broken transmitter drops an item that keeps its frequency;
8. a compact receiver on a wall powers that wall block, lighting a lamp on the far side;
9. removing the wall knocks the compact receiver off, and its dropped item keeps its frequency;
10. Wall Redstone climbing a pillar goes 15, 14, 13 … and lights a lamp at the top, then turns fully off;
11. Wall Redstone and normal dust power each other, and both turn fully off when the source is removed (no stuck-on loops);
12. Wall Redstone powers the block it's on, lighting a lamp behind the wall;
13. removing the wall knocks Wall Redstone off and drops it;
14. a Remote Control pulse turns a receiver on for about a second, then off;
15. a Remote Control in toggle mode stays on until pressed again;
16. a Remote Control can't reach a receiver beyond its reach;
17. sneak + right-clicking a transmitter copies its frequency into the remote;
18. a Multi-Button Remote's buttons each drive their own frequency and mode;
19. sneak + right-click copies a frequency into the selected button only;
20. remote button settings are always tidied up (4 buttons, valid selection and frequencies).

## License

All rights reserved. To open-source it, add a `LICENSE` file and update `mod_license` in `gradle.properties`.
