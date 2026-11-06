# Reiko's Redstone Signal Tower

A Minecraft **1.21.1 NeoForge** mod that adds radio towers for redstone. Put a transmitter high up on a tower and it sends your redstone signal to receivers far away. The higher the tower, the farther it reaches.

![Two radio towers](docs/images/towers.png)

## Blocks and items

| | What it does |
|---|---|
| **Radio Transmitter** | Broadcasts the redstone signal it receives (strength 0–15) on its frequency. The higher above sea level it sits, the farther it reaches. Its warning light glows while it's broadcasting. It also relays Remote Control presses made within reach of it. |
| **Radio Receiver** | Outputs the strongest signal it hears on its frequency, at the same strength. Works like a redstone block with a dimmer. |
| **Radio Repeater** | A relay station. It picks up its frequency where it stands and broadcasts it again, at the same strength, with its own reach. Put repeaters on towers along the way to send a signal much farther than one transmitter can. Needs no redstone. Its amber light glows while it's repeating. |
| **Radio Converter** | A repeater that changes the frequency. It picks up one frequency and broadcasts it on another, so one switch can drive receivers on a second frequency, or two networks can be joined. Its teal light glows while it's converting. |
| **Radio Toggle Receiver** | A receiver that works like a light switch: each time its frequency turns on, it flips between off and on (full strength). A pulse remote, a button or a pressure plate then switches things on with one press and off with the next. |
| **Compact Radio Receiver** | A button-sized receiver. Sticks to walls, floors and ceilings like a button, and like a button it also powers the block it's stuck to, so it can drive redstone on the other side of a wall. Pops off (keeping its frequency) if that block is removed. |
| **Radio Mast** | Lattice tower segment for building your tower. A mast column also carries redstone **up**: power any mast in the column and the transmitter on top broadcasts it. |
| **Frequency Tuner** | Sets which frequency a transmitter or receiver uses. Keeps 5 favorite frequencies, and sneak + scroll changes its frequency without opening a screen. |
| **Remote Control** | A handheld transmitter, like a garage door opener. Press it to send a signal on its frequency: a 1-second pulse like a button, or toggle on/off. Has the same scrolling and favorites as the tuner. |
| **Multi-Button Remote** | A remote with 4 buttons, each with its own frequency, mode, name and color. One remote can run your door, lights and farm. |
| **Radio Pager** | A pocket receiver. Keep it anywhere in your inventory and it beeps and shows a message when its frequency turns on near you. |
| **Signal Scanner** | A handheld direction-finder. It lists every frequency you can hear where you stand, how strong it is and where it comes from, and can tune the radio item in your other hand. |
| **Wall Redstone** | Redstone dust that sticks to the side of a block. It runs up, down and sideways along a wall, loses 1 strength per block like normal dust, connects to normal dust and redstone components, and powers the block it's on. |

![Compact Radio Receivers on a ceiling, on top of a block, on both sides of a block, and on a wall powering a lamp behind it](docs/images/compact-receivers.png)

![Wall Redstone running up and across a wall, fed by a lever and by normal dust](docs/images/wall-redstone.png)

## How to use it

1. **Build a tower.** Stack Radio Masts and put a Radio Transmitter on top.
2. **Power it from the ground.** Put a lever, redstone wire, or any other signal next to any mast in the column, or next to the transmitter itself.
3. **Tune it.** Right-click the air with a Frequency Tuner and type a frequency (any whole number from 1 to 999,999,999). Then right-click the transmitter with the tuner.

   ![The Frequency Tuner screen, with its row of favorites](docs/images/tuner-screen.png)

4. **Set up the far end.** Put a Radio Receiver where you want the signal and right-click it with the same tuner. It outputs whatever the transmitter is sending.

![Tuning a receiver](docs/images/tuned-receiver.png)

**Tuner controls:**
- Right-click the air: type a new frequency.
- Right-click a transmitter or receiver: tune it to the tuner's frequency.
- Sneak + right-click a transmitter or receiver: copy its frequency into the tuner.
- Sneak + scroll: step the tuner's frequency up or down by 1. The new number shows above your hotbar, and your hotbar slot doesn't move.
- Sneak + Ctrl + scroll: jump to the next favorite (scroll down) or the previous one (scroll up).
- **Favorites:** the tuner's screen has 5 favorite slots under the frequency.
  - Click an empty slot to save the frequency above it.
  - Click a saved one to put its frequency in the box.
  - Sneak-click a saved one to replace it with the frequency above.
  - Right-click a saved one to clear it.

  Favorites are saved straight away (even if you then press Cancel), stay on the tuner, and are listed in its tooltip. The slot matching the frequency in the box shows in green.

  The Remote Control and Radio Pager have the same scrolling and favorites, each with its own 5 slots. The Multi-Button Remote keeps sneak + scroll for picking its button.

  ![Saving favorites in the tuner's screen, then jumping between them with Sneak + Ctrl + scroll](docs/images/tuner-favorites.png)

**Frequency names:**
- **Naming one:** give a frequency a name in the Frequency Tuner's screen. Type the frequency, type a name in the **Name** box under it (up to 24 characters), and press Done: "Frequency 2026 is now called Front Gate, for everyone". The box shows the typed frequency's current name; clear it and press Done to remove the name.
- **Shared:** names belong to the world. Everyone on a server sees the same names, they're saved with the world, and they count in every dimension. Anyone holding a tuner can rename a frequency.
- **Where they show:** most places a frequency number appears, as "2026 (Front Gate)":
  - item tooltips (tuner, remotes, pager, radio blocks);
  - the remote's press message and the pager's alerts, chat log and catch-up lines;
  - the tuner's "Tuned to" message and the empty-hand readouts;
  - the scanner's rows ("Front Gate: Repeater, 7 blocks east") and its tracking arrow;
  - Jade, and the converter screen.

  The Remote Control and pager screens show the typed frequency's name under the title.
- **Unnamed frequencies:** they show just their number, as before. A world holds up to 4,096 names.

  ![Naming 2026 "Front Gate" in the tuner, and the name in the remote's screen, Jade, the tuner's tooltip, the scanner and a remote press](docs/images/frequency-names.png)

**Remote Control:**
- Right-click: press the button.
- Sneak + right-click the air: change its frequency, switch between **Pulse** and **Toggle** (on until you press it again), and set the **pulse length**. Click the length button for longer or sneak-click for shorter: 0.1s, 0.25s, 0.5s, 1s (the default, like a stone button), 1.5s, 2s, 3s, 5s or 10s.
- Sneak + right-click a transmitter or receiver: copy its frequency.
- Sneak + scroll, Sneak + Ctrl + scroll and the 5 favorites in its screen work just like the tuner's (see **Tuner controls** above). Scrolling doesn't change its mode or pulse length.

  ![The Remote Control's screen with two favorites saved, and the messages from scrolling](docs/images/remote-favorites.png)
- Its reach uses the same height rule as a transmitter, measured from where you're standing, so pressing it from a hilltop or the top of a tower reaches farther.
- **Tower relay:** every Radio Transmitter (and Radio Repeater) within your remote's reach relays your press. The signal goes out again from the top of that tower, with the tower's reach. So you can stand at the foot of a tall tower, press, and reach as far as the tower does. It works on any frequency (the tower doesn't need to be on the remote's frequency, or powered), so one tall tower at your base boosts every button. The message shows how many towers relayed it ("reach 56 blocks + 1 tower"), and the signal light and "Heard by" count include them. A toggle switched on through a tower goes off if that tower is broken. Server owners can turn relaying off in the config (`towersRelayRemotes`).

![Toggling a lamp 150 blocks away through a tower: the screen counts the receiver as in range, the message counts the tower, and the lamp lights](docs/images/tower-relay.png)
- Any remote on the same frequency can switch a toggle off, so a lost remote can't leave something stuck on.
- **Signal light:** after each press, the light on the remote shows **green** for 2 seconds if at least one receiver on that frequency was in range, or **red** if none was. The message also says how many receivers heard it. This works on both remotes.

![The remote's light: off, green after a press a receiver heard, red after a press nobody heard, then off again](docs/images/remote-light.png)

- **Locator:** while a remote's screen is open, each frequency shows who's listening, without sending anything. For example "2 receivers in range, nearest 14 blocks north-east", or "None in range. Nearest is 120 blocks south". A line underneath shows your reach and how many towers are relaying for you ("Your reach: 56 blocks, plus 1 tower relaying"). It updates about once a second, follows what you type, and only sees receivers in loaded chunks: within twice your own reach, or anywhere a relaying tower (or a repeater on that frequency) carries your press.

![The keypad's locator lines](docs/images/remote-locator.png)

![The Remote Control screen](docs/images/remote-screen.png)

**Multi-Button Remote:**
- Right-click: press the **selected** button.
- Sneak + scroll: choose the selected button. Your hotbar slot doesn't move.
- Sneak + right-click the air: open the keypad. From there you can press any button, give each button a **name** (like "Front Door") and a **color**, set its frequency, Pulse/Toggle mode and pulse length, and click a button's number to make it the selected one.
- **Button colors:** click a button's color square in the keypad for the next of the 16 dye colors, or sneak-click for the previous one. The four buttons on the remote itself (in your hand, hotbar and inventory) change to match, and each button's name shows in its color in the tooltip and press messages. New remotes, and remotes made before this version, start red, green, blue and yellow.
- Names show up everywhere: the tooltip, the press message ("Front Door: 1s pulse sent…") and the selection message ("Farm Lights selected…"). Unnamed buttons show as "Button 1" to "Button 4". Names can be up to 24 characters.
- Sneak + right-click a transmitter or receiver: copy its frequency into the selected button.
- The tooltip lists all 4 buttons, with the selected one marked ».

![The Multi-Button Remote keypad, with a color square next to each button's number](docs/images/multi-remote-keypad.png)

![A remote with purple, orange, cyan and white buttons next to one with the default red, green, blue and yellow](docs/images/button-colors.png)

![The Multi-Button Remote's tooltip, with each named button in its color](docs/images/multi-remote-tooltip.png)

**Radio Pager:**
- Keep it anywhere in your inventory (hotbar, backpack slots or off-hand). When its frequency turns on where you are, it beeps three times and shows "Radio Pager: frequency 5 is ON (strength 15)". "Where you are" means inside the reach of a transmitter or repeater on that frequency, or of a remote press (including towers relaying it).
- It alerts once each time the signal turns on, not again while it stays on. Its little screen glows green while the signal is on, and the tooltip shows the live status.
- Rename it in an anvil and the alert uses that name: "Front Door: frequency 5 is ON".
- **Chat log:** each alert also goes into chat, with the in-game day and time in front: "[Day 4, 08:30] Front Door: frequency 5 is ON (strength 15)". Open chat to scroll back and see what went off while you weren't looking. It's on for every pager by default; switch it off for one pager (say, one on a fast clock) with the **Chat log** button in its screen. The time comes from the world's clock, with each day starting at midnight.

  ![Pager alerts in chat with their day and time, and the pager's Sound and Chat log buttons](docs/images/pager-chat-log.png)
- **Catch-up (on servers):** when you log out, each pager in your inventory notes the time. When you log back in, each one that missed something tells you in chat: "Gate: while you were away, frequency 42 turned on 2 times, last on Day 1 at 18:34". It counts every time its frequency turned on where you're standing (a transmitter being powered, a remote press, a toggle switched on), even across server restarts. If none of your pagers missed anything, you get one line saying so. Each frequency remembers its last 32 turn-ons, so after a long time away it may say "at least 32 times". A singleplayer world is paused while you're out of it, so there's nothing to catch up on there and the pagers stay quiet.

  ![Pagers catching up after logging back in to a server, and the line when nothing happened](docs/images/pager-catch-up.png)
- Right-click: switch the sound on or off. With the sound off it still shows the message.
- Sneak + scroll, Sneak + Ctrl + scroll and the 5 favorites in its screen work just like the tuner's. After a change it alerts if the new frequency is already on.
- Sneak + right-click the air: change its frequency, and switch its **Sound** and **Chat log** on or off. Sneak + right-click a transmitter or receiver: copy its frequency.
- Pagers count toward a remote's "Heard by" number and signal light, so a doorbell press shows green when only your pager hears it. The locator never shows pagers, so it can't be used to find players.
- The beep is only heard by the pager's owner.

![Two pagers going off: one from a remote press, one renamed "Tower Alarm" from a tower, with its tooltip](docs/images/pager.png)

**Signal Scanner:**
- Right-click to open it. It shows a live list (updated every second) of everything a receiver where you're standing would hear: every frequency, its strength ("ON 15", or "off" for a transmitter that's silent right now), and its strongest source.
- For a transmitter, the source is its distance and direction: "Transmitter, 27 blocks south". If several transmitters on that frequency reach you, it points to the strongest one (the nearest, if they're equally strong) and says how many more there are ("+1 more").
- For a repeater it says "Repeater, 78 blocks west" in the same way, and a repeater with nothing to repeat is listed as "off". A converter is listed under the frequency it sends on ("Converter, 52 blocks west").
- For a remote press it just says "Remote pulse" or "Remote toggle", with no distance or direction, so it can't be used to find players.
- Strongest signals come first. Scroll the mouse wheel if there are more than 6.
- **Track with an arrow:** click a transmitter's (or repeater's) row to track it. While you hold the scanner (either hand), an arrow at the top of the screen points to that transmitter as you walk and turn, with the distance underneath and how far up or down it is: "Frequency 77: 3 blocks, 30 up". The tracked row shows in green with a "»". Click it again to stop. If the transmitter is broken or re-tuned to another frequency, the scanner says it lost track. With several transmitters on one frequency, it tracks the one the row points to. Remote presses can't be tracked, so it still never points at players.

  ![Tracking frequency 42 in the scanner, then the arrow pointing to it facing north, south and east, and to the tower 30 blocks up](docs/images/scanner-arrow.png)
- **Tune:** hold a Frequency Tuner, Remote Control, Multi-Button Remote or Radio Pager in your other hand, and each row's Tune button sets that frequency on it. A remote keeps its mode and pulse length, and a Multi-Button Remote changes only its selected button.

![The Signal Scanner listing five frequencies, after tuning the Remote Control in the off-hand to 42](docs/images/signal-scanner.png)

**Jade (optional):**
- If you have [Jade](https://modrinth.com/mod/jade) installed, just look at a block to see its details in Jade's info box, with no clicking:
  - **Radio Transmitter:** frequency, what it's sending ("Sending: 15" or "Sending: nothing") and its reach.
  - **Radio Repeater:** frequency, what it's repeating ("Repeating: 15" or "Nothing to repeat") and its reach.
  - **Radio Converter:** both frequencies ("Listens on: 2026", "Sends on: 3001"), what it's converting and its reach.
  - **Radio Receiver** and **Compact Radio Receiver:** frequency, output ("Output: 15" or "Output: off") and how many transmitters and repeaters reach it ("Towers in range: 2").
  - **Radio Toggle Receiver:** frequency, "Switched on" or "Switched off", what it's hearing right now, and towers in range.
  - **Radio Mast:** what's on top of its column and how far up ("Top: Radio Repeater, 3 blocks up"), and that tower's frequency. Handy at the foot of a tall tower.
- Jade needs to be on the server too for the frequency, reach and tower count (they're only known there). With Jade only on your side, it still shows what the block itself shows: on or off, and a receiver's output.
- Switch it off in Jade's settings (numpad 0 → Plugin Settings → Reiko's Redstone Signal Tower).
- The mod doesn't need Jade. Without it, everything works the same, and an empty-hand right-click still shows the status readout.

![Jade showing a transmitter, a repeater, a mast with the repeater on top, and a receiver](docs/images/jade-tooltips.png)

**Other details:**
- Both receivers are tuned with the Frequency Tuner and act the same; pick the full block or the compact one depending on how much room you have.
- Right-click a transmitter or receiver with an empty hand to see its frequency, signal, height and reach. A receiver's "Towers in range" counts the transmitters and repeaters on its frequency that reach it.
- Breaking a transmitter, receiver or repeater keeps its frequency on the dropped item, so you can move it without re-tuning.
- One transmitter can feed any number of receivers. A receiver that hears several transmitters on its frequency outputs the strongest.
- A transmitter keeps broadcasting its last signal while its chunk is unloaded, so long-distance links keep working.
- Don't put a receiver next to a transmitter (or its mast column) on the **same** frequency. That makes a loop that latches on, like a redstone torch loop.

### Toggle receivers

A **Radio Toggle Receiver** doesn't pass the signal through like a normal receiver. It **flips** each time its frequency turns on where it stands, like a T flip-flop: the first turn-on switches it on, the next one switches it off, and so on. Turning off does nothing.

- **What it outputs:** while it's on, it outputs full strength (15), whatever strength it heard, and its lights glow. It starts off.
- **What counts as a turn-on:** any time its frequency goes from silent to on. That includes a remote pulse, a button or pressure plate driving a transmitter, a lever, or a toggle remote being switched on.
- **Use it as a light switch:** with a pulse remote, one press turns your lights on and the next turns them off. Put buttons on transmitters at both ends of a hallway and either one switches the same lamps.
- **No surprise flips:** placing it, re-tuning it, or its chunk loading while the frequency is already on doesn't flip it. It waits for the next turn-on.
- **Saving:** its on/off state is saved with the world. Breaking it drops a toggle receiver that remembers its frequency, but starts off when placed again.
- **Unloaded chunks:** turn-ons while its chunk is unloaded are missed, because it only listens while loaded.
- **Readout and Jade:** an empty-hand right-click shows "Toggle Receiver | Frequency 2026 | ON | Hearing 15 | Towers in range: 2". Jade shows "Switched on" or "Switched off", and what it's hearing.
- **Counts as a receiver:** it counts toward a remote's "Heard by" and signal light, and the locator finds it like any receiver.

![Tuning it while 2026 is on leaves it off; the lever turning on switches it on; the lever turning off leaves it on; a remote pulse switches it off](docs/images/toggle-receiver.png)

### Repeater towers

One transmitter only reaches so far. To go farther, put a **Radio Repeater** partway out, inside the transmitter's reach, and tune it to the same frequency. The repeater picks up the signal and broadcasts it again from where it stands, with its own reach. Receivers in either circle hear it.

- **Build it like a transmitter tower:** Radio Masts with the repeater on top. It uses the same height rule, so the higher it is, the farther it sends. It doesn't need redstone or power.
- **Chain them:** a repeater inside another repeater's reach passes the signal on again, so a line of towers can carry it across the map. The strength stays the same at every hop (15 in, 15 out at the far end).
- **No loops:** repeaters only pass on a signal that comes from a real transmitter or remote press somewhere up the chain. Two repeaters in each other's reach can't keep each other on: when the transmitter goes quiet, the whole chain goes quiet within a tick.
- **Remote presses too:** a remote press that reaches a repeater on its frequency is passed along the chain like anything else. The message counts those repeaters ("reach 56 blocks + 1 tower + 1 repeater"), and the "Heard by" count and signal light include the receivers they reach.
- **Relay tower:** like a transmitter, a repeater within your remote's reach also relays your presses on any frequency.
- It keeps repeating while its chunk is unloaded, like a transmitter.
- Right-click it with an empty hand: "Repeater | Frequency 2026 | Repeating 15 | 7 blocks above sea level | Reach 88 blocks", or "Nothing to repeat".
- The pager, the Signal Scanner ("Repeater, 78 blocks west"), the remote's locator and the pager catch-up all take repeaters into account.

![A repeater on a mast tower repeating frequency 2026; the lamp 130 blocks from the transmitter lit through it; the scanner pointing to the repeater; and a remote press carried on by a second repeater](docs/images/repeater-tower.png)

### Converters

A **Radio Converter** works like a repeater, but it sends on a **different** frequency than it listens on. It hears frequency 2026 where it stands, say, and broadcasts it on 3001, at the same strength, with its own reach. That lets one lever or remote button drive things on a second frequency too, or link two separate networks.

- **Setting it up:** right-click it with an empty hand to open its screen, with a **Listens on** box and a **Sends on** box (Tab moves between them, Enter saves). The screen also shows what it's converting right now and its reach. The Frequency Tuner sets (and copies) the frequency it listens on, like any radio block.
- **Height, chains and towers:** it follows the same rules as a repeater. Reach grows with height, it needs no redstone, and it chains with repeaters and other converters (a repeater listening on 3001 carries the converted signal further). Like other towers, it relays remote presses on any frequency.
- **No loops:** even two converters feeding each other (2026 to 3001 and 3001 back to 2026) go quiet as soon as the real source does.
- **Remote presses:** a press on 2026 that reaches the converter is carried over to 3001. The message counts it ("reach 56 blocks + 2 towers + 1 repeater + 1 converter"), and "Heard by" and the signal light include the receivers on 3001.
- **Everything else:** pagers on 3001 go off from it, the Signal Scanner lists it under the frequency it sends on ("Converter, 52 blocks west"), and the pager catch-up remembers turn-ons it carried. Jade shows both frequencies.
- Breaking it keeps both frequencies on the dropped item.

![The converter's screen being set to listen on 2026 and send on 3001; Jade showing it converting; the scanner hearing both frequencies; and the receiver on 3001 lit](docs/images/radio-converter.png)

### Reach

Reach is measured horizontally from the transmitter (or repeater), so a receiver works at any height inside that circle.

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

All three numbers (base reach, reach per block of height, and an optional maximum) are in the server config: `<world>/serverconfig/reikos_signal_tower-server.toml`, or Mods → Reiko's Redstone Signal Tower → Config. The same file has `towersRelayRemotes` (on by default), which lets transmitters and repeaters relay remote presses on any frequency.

### Recipes

```
Radio Transmitter       Radio Receiver          Radio Mast (x6)       Frequency Tuner
  .  L  .                 .  L  .                 B  .  B               .  T  .
  I  E  I                 I  E  I                 B  I  B               I  G  I
  I  T  I                 I  P  I                 B  .  B               I  R  I

L = Lightning Rod   E = Ender Pearl   T = Redstone Torch   P = Redstone Repeater
I = Iron Ingot      B = Iron Bars     G = Glass Pane       R = Redstone Dust
```

**Radio Repeater:** a Radio Receiver and a Radio Transmitter, in any arrangement in the crafting grid.

**Radio Converter:** a Radio Repeater and a Redstone Comparator, in any arrangement in the crafting grid.

**Radio Toggle Receiver:** a Radio Receiver and a Lever, in any arrangement in the crafting grid. Put a toggle receiver in the grid on its own to get the Radio Receiver back.

**Compact Radio Receiver:** put one Radio Receiver in the crafting grid to get a Compact Radio Receiver. It works the other way too, so you can switch back any time.

**Remote Control:**

```
  .  T  .        T = Redstone Torch   B = Stone Button
  I  B  I        I = Iron Ingot       E = Ender Pearl
  I  E  I
```

**Multi-Button Remote:** a Remote Control plus 3 Stone Buttons, in any arrangement in the crafting grid.

**Radio Pager:** a Compact Radio Receiver, a Note Block and an Iron Ingot, in any arrangement in the crafting grid.

**Signal Scanner:** a Radio Receiver, a Compass and an Iron Ingot, in any arrangement in the crafting grid.

**Wall Redstone:** put one Redstone Dust in the crafting grid to get one Wall Redstone, and back again the same way. To place it, right-click the side of any solid block.

## Building and running

You need **Java 21**. If Gradle can't find it, it downloads one automatically.

```bash
./gradlew build              # builds build/libs/reikos_signal_tower-<version>.jar
./gradlew runClient          # starts Minecraft with the mod
./gradlew runServer          # starts a dedicated server with the mod
./gradlew runGameTestServer  # runs the automated in-game tests, then exits
```

The dev runs include [Jade](https://modrinth.com/mod/jade) for testing the optional tooltip plugin (it isn't bundled in the jar). Add `-PnoJade` to any run to leave it out, for example `./gradlew runGameTestServer -PnoJade`.

On Windows use `gradlew.bat` instead of `./gradlew`.

**IntelliJ IDEA:** File → Open → pick this folder's `build.gradle` → Open as Project. Wait for the Gradle sync to finish, then use the generated run configurations (Client, Server, GameTestServer).

To play it in your own instance, copy the jar from `build/libs/` into the instance's `mods` folder. Every push to `main` is also built by GitHub Actions. The jar is attached to each run under **Actions → Build → Artifacts**.

## Project layout

```
src/main/java/com/reiko/signaltower/
  SignalTower.java                       mod entry point: registration, creative tabs, packets
  Config.java                            server config (reach settings, tower relay switch)
  registry/ModRegistry.java              blocks, items, block entities, the frequency data component
  block/RadioBlock.java                  shared transmitter/receiver code: status readout, tooltips
  block/RadioTransmitterBlock.java       reads input, broadcasts
  block/RadioReceiverBlock.java          redstone output
  block/RadioToggleReceiverBlock.java    receiver that flips on and off with each turn-on
  block/RadioRepeaterBlock.java          picks up its frequency and broadcasts it again
  block/RadioConverterBlock.java         a repeater that sends on another frequency; opens its screen
  block/CompactRadioReceiverBlock.java   button-sized receiver that attaches to a face
  block/RadioMastBlock.java              tower segment, carries signal up to the transmitter
  block/WallRedstoneBlock.java           redstone dust for walls
  blockentity/RadioBlockEntity.java      stores the frequency, keeps it through break/re-place
  blockentity/RadioTransmitterBlockEntity.java
  blockentity/RadioReceiverBlockEntity.java
  blockentity/RadioRepeaterBlockEntity.java  repeater and converter: checks in with the network, drives the light, a converter's output frequency
  item/TunerPresets.java                 5 favorite frequencies (data component)
  item/RadioFavorites.java               sneak + scroll and favorites for the tuner, remote and pager
  item/FrequencyTunerItem.java           tune, copy, open the frequency screen
  item/RemoteControlItem.java            handheld transmitter: pulse or toggle
  item/MultiRemoteItem.java              4-button remote
  item/PagerItem.java                    pocket receiver that beeps when its frequency turns on
  item/SignalScannerItem.java            handheld that lists every frequency heard where you stand, and tracks one
  item/ScannerTarget.java                the transmitter a scanner is tracking (data component), arrow angle
  client/ScannerHud.java                 the tracking arrow at the top of the screen
  item/RemoteButtons.java                the 4 buttons' settings (data component), colors
  item/PulseLength.java                  pulse length presets, limits and labels
  item/RemoteLight.java                  the remotes' green/red status light after a press
  network/RemoteScanRequestPayload.java  remote screen -> server: who's listening? (locator)
  network/RemoteScanResultPayload.java   server -> remote screen: the answer
  client/LocatorText.java                turns a locator answer into a line of text
  network/ConfigureMultiRemotePayload.java  keypad -> server (save, and optionally press)
  network/SelectRemoteButtonPayload.java sneak + scroll -> server
  network/ConfigureRadioItemPayload.java frequency screen -> server packet
  radio/SignalNetwork.java               per-dimension list of transmitters, repeaters and remote signals (with relay coverage), saved with the world
  radio/RadioHelper.java                 height, reach, mast-column logic
  radio/Frequency.java                   frequency range (1 to 999,999,999)
  radio/FrequencyNames.java              frequency names, saved with the world (and the client's synced copy)
  network/SetFrequencyNamePayload.java, FrequencyNamePayload.java, FrequencyNamesPayload.java  naming, and keeping clients in sync
  compat/RadioInfo.java                  what Jade shows for each radio block (works without Jade)
  compat/jade/SignalTowerJadePlugin.java the Jade plugin (only loaded by Jade)
  client/FrequencyScreen.java            the frequency (and remote mode) screen
  client/MultiRemoteScreen.java          the Multi-Button Remote keypad
  client/ConverterScreen.java            the Radio Converter's two frequencies
  network/OpenConverterScreenPayload.java, ConfigureConverterPayload.java  converter screen <-> server
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
20. remote button settings are always tidied up (4 buttons, valid selection and frequencies);
21. a Remote Control set to a 5-second pulse is still on after 3 seconds, then turns off;
22. a Multi-Button Remote button set to 0.25 seconds turns off well before a normal 1-second pulse would;
23. pulse length presets step and wrap correctly and stay within 0.1–10 seconds;
24. a remote's light goes green when a receiver in range heard the press;
25. it goes red when receivers are only out of range or on another frequency;
26. a re-tuned receiver stops counting for its old frequency;
27. a broken receiver stops counting straight away;
28. button names lose colour codes, control characters and extra spaces, and are cut to 24 characters;
29. named buttons are labelled with their name, unnamed ones as "Button N";
30. names survive saving and network sync, and remotes saved before names existed still load;
31. copying a frequency into a named button keeps its name;
32. the locator counts receivers in range and points at the nearest one;
33. with nothing in range, it says where the nearest one just out of range is;
34. receivers more than twice the reach away stay hidden;
35. the eight compass directions come out right;
36. a transmitter on a tower next to the player relays a remote press (on another frequency, unpowered) to a receiver beyond the player's own reach, and the light and message count it;
37. a tower farther away than the player's reach doesn't relay;
38. with relaying switched off, only the player's own reach counts;
39. a toggle switched on through a tower turns off when the tower is broken;
40. a toggle's tower areas survive saving and loading, and the old save keys are still written for older versions;
41. toggles saved by version 0.10.0 still load;
42. the locator and the light count receivers that only a tower reaches;
43. buttons default to red, lime, blue and yellow, and clicking goes through all 16 dye colors both ways;
44. button colors survive saving and network sync, and remotes saved before colors existed load with the defaults;
45. the item's button layers are tinted with each button's color, labels use it, and every color stays readable on a tooltip;
46. a pager in the inventory alerts once when a transmitter in reach turns on (frequency and strength in the message), starts beeping, goes quiet, and alerts again next time;
47. a pager hears a Remote Control press made near its owner;
48. a powered transmitter out of reach leaves the pager quiet;
49. a renamed pager uses its name in the alert, and a silenced one shows the message without beeping;
50. pagers count toward a remote's "Heard by" only when in range and on that frequency, and re-tuning re-arms the alert;
51. the scanner lists what a receiver here would hear (silent transmitters too), strongest first, with distance and direction, and leaves out transmitters whose reach doesn't cover you;
52. with several transmitters on one frequency, it points to the strongest, then the nearest, and counts them all;
53. remote signals show as "remote" with no distance or direction, and remotes that don't cover you aren't listed;
54. a real placed transmitter shows up in a scan of the world;
55. the Tune buttons work on the tuner, both remotes and the pager, and change only the Multi-Button Remote's selected button's frequency;
56. tuner favorites always have 5 slots, empty bad numbers, cap huge ones, and can be saved and cleared;
57. jumping between favorites goes in slot order, skips empty slots and wraps around, both ways;
58. favorites survive saving and network sync, and a tuner without any has no extra data;
59. sneak + scroll steps by 1 and stops at 1 and 999,999,999; with Ctrl it jumps between favorites, and says so when there are none;
60. the Remote Control and pager scroll and jump between favorites too (the remote keeps its mode and pulse length, the pager re-arms its alert), and the Multi-Button Remote and scanner don't;
61. the remote's, pager's and tuner's tooltips list their favorites right under the frequency and explain scrolling;
62. a pager posts its alert in chat (day and time, then the alert) as well as above the hotbar, and one with its chat log off only shows it above the hotbar;
63. the chat line's day and time come from the world's day time, with days starting at midnight;
64. the chat log is on by default, can be switched off and on, and the tooltip says which;
65. the scanner tracks the same transmitter its row points to (strongest, then nearest), and can't track one whose reach doesn't cover you;
66. tracking a placed transmitter stores where it is; re-tuning or breaking it loses the track; remote-only and empty frequencies can't be tracked; stopping clears it;
67. the target survives saving and sync, and the arrow points ahead, left, right or behind correctly for each facing;
68. the network remembers each turn-on (a transmitter going from off to on, a remote pulse, a toggle switched on), not staying on, strength changes or switching off, and keeps the last 32 per frequency;
69. the remembered turn-ons are saved with the world;
70. at login, each pager reports how often its frequency turned on where the player is while they were away ("once", "N times"), one line says so if nothing happened, and singleplayer stays quiet;
71. logging out stamps every pager in the inventory, off-hand included, and nothing else;
72. a repeater in a transmitter's reach passes the signal on at the same strength, a second one carries it further, other frequencies aren't repeated, and switching off or removing a repeater breaks the chain;
73. two repeaters in each other's reach stay on only while a real source (transmitter, remote pulse or toggle) feeds them, and each repeats the strongest signal it gets;
74. repeaters are saved with the world and keep repeating after loading;
75. a remote press counts the receivers repeaters carry it to (without adding a relaying repeater twice), and the message wording names the repeaters;
76. turn-ons are remembered over the repeaters they reached, for the pager catch-up;
77. the scanner names a repeater as the source with its distance and direction, can track it, lists a quiet one as off, and prefers a transmitter in a tie;
78. a placed repeater checks in, lights up while repeating, reaches a receiver the transmitter can't, relays remote presses as a tower, stops when re-tuned or broken, and drops with its frequency;
79. a remote press with no towers relaying is carried by a repeater on its frequency to a far receiver, and the message and light count it;
80. the Jade info: the server reads each transmitter's, receiver's and repeater's frequency, signal, reach and towers in range, and what's on top of a mast column; the lines come out in the right order; without server data only what the block state shows is listed;
81. quiet blocks say "Sending: nothing", "Output: off" and "Nothing to repeat", the compact receiver matches the full one, and other blocks get nothing;
82. a converter sends what it hears on another frequency at the same strength, isn't heard on the one it listens on, bumps its output frequency's revision when its input changes, and turns back into a repeater when set to the same frequency;
83. two converters feeding each other (A to B and back) only stay on while a real source feeds them, and a repeater carries the converted frequency further;
84. a remote press is carried across a converter: receivers on the other frequency count, and the message names it;
85. a turn-on carried across a converter is remembered on the converted frequency too;
86. the scanner lists a converter under the frequency it sends on, as "Converter", and can track it;
87. converters are saved with their output frequency, and repeaters saved by 0.20.0 still load as repeaters;
88. a placed converter is set from its screen's packet (only from within reach), lights a receiver on its output frequency, shows in Jade (and on the mast under it), and keeps both frequencies on the dropped item and when placed again;
89. a toggle receiver flips on each time the transmitter turns on and ignores it turning off; its state is saved and loaded back, and Jade shows it;
90. a weak signal (14) switches it fully on (15);
91. remote pulses switch it on with one press and off with the next, and the press counts it as heard;
92. a frequency that's already on when it's placed, or when it's re-tuned to it, isn't a turn-on: it waits for the next one;
93. frequency names are tidied (spaces, formatting codes, length), removed by an empty name, capped at 4,096, saved, and survive the network;
94. the client's copy of the names is filled by the full list, changed one at a time and cleared on leaving;
95. naming a frequency only works with a tuner in hand, and the name then shows in the remote's press message, the pager's alert, Jade and the tuner's message; a frequency without a name shows just its number.

All 95 also pass with `-PnoJade`, so the mod runs the same without Jade.

## License

All rights reserved. To open-source it, add a `LICENSE` file and update `mod_license` in `gradle.properties`.
