# Reiko's Wireless Redstone

**Wireless redstone over radio towers.** Put a Radio Transmitter on top of a tower and it broadcasts the redstone signal it gets (strength 0–15) on a frequency. Every Radio Receiver tuned to that frequency within reach outputs the same signal: a lever at home can open a gate across the valley, and a pressure plate at the mine can ring a bell in your base.

The higher a transmitter sits, the farther it reaches: 32 blocks at sea level, 728 blocks at Y=150 and over 2,000 blocks at the build limit. Antennas, repeater towers and satellites take it farther still.

![Two radio towers](https://raw.githubusercontent.com/Reiko45/Reiko-s-Wireless-Redstone/main/docs/images/towers.png)

## Getting started

1. **Build a tower.** Stack a few Radio Masts and put a Radio Transmitter on top.
2. **Power it.** Put a Radio Battery next to the transmitter or any mast in its column, and charge it by right-clicking it with redstone. (Towers need power to send; server owners can turn this off in the config.)
3. **Tune it.** Right-click the air with a Frequency Tuner, type any frequency from 1 to 999,999,999, then right-click the transmitter with the tuner.
4. **Give it a signal.** A lever, button or redstone wire next to the transmitter or any mast in the column is broadcast.
5. **Listen.** Put a Radio Receiver where you want the signal and tune it to the same frequency. It works like a redstone block with a dimmer.

Every block and item has a page in JEI and EMI with its recipe and what it does.

## What's in it

**Towers and signals**

- Transmitters, receivers and button-sized compact versions for walls, floors and ceilings.
- Repeaters to relay a signal tower to tower, and converters that carry it onto another frequency.
- Iron, Gold, Diamond and Netherite antennas (up to 4× the reach), a Dish Antenna that beams one way, an Ender Antenna that sends into the Nether, and satellites that carry a tower across a whole dimension.
- Weather, hills and lightning affect towers: rain shortens their reach, a thunderstorm can knock a tall one off the air for a moment, and a Lightning Rod near the top protects it.

**Receivers that do more**

- Toggle, inverted, AND/OR/XOR logic, delay and counter receivers: wireless T flip-flops, NOT gates, timers and secret knocks.
- Radio Lamps, Radio Speakers (bells, chimes, doorbells, sirens) and Status Boards that show four frequencies at a glance.
- Timer transmitters, signal recorders that replay up to 30 seconds of redstone, and a Radio Jukebox that plays music on every speaker in reach.

**In your hand**

- Remote Controls and 4-button remotes, like garage door openers.
- Radio Pagers that beep when a frequency turns on, and Walkie-Talkies for chatting with friends on the same frequency.
- A Signal Scanner that lists everything you can hear and points to it, and a Signal Meter showing the strength where you stand.

**Messages and screens**

- Radio Telegraphs and a Morse Key for sending short messages, and a Ticker Tape Printer that prints them into a book.
- A Radar Dish and Radar Screen that show players, mobs and vehicles around a tower.
- TV Cameras and TV Monitors with live pictures, and monitors that join into video walls up to 8 × 5.
- A Radio Drone you fly with a Drone Controller and see through its camera. It can carry items and act as a flying relay.

**Security**

- A Security Console that watches up to 8 zones. Door Sensors, Motion Sensors or anything else that turns a zone's frequency on trips it, and a Code Keypad arms and disarms it.
- When the alarm goes off, sirens sound, pagers and walkie-talkies get "ALARM: Front Door (Alex)" and TV Monitors switch to the camera on that zone.
- Code Keypads with guest codes, and Motion Sensors.

**Your frequencies**

- Name your frequencies, make them private to you and your friends, or encrypt them so only your receivers hear them.
- A directory of named frequencies, a log of who turned what on, and an alert when someone else uses yours.

**The world**

- Abandoned radio towers to find and revive, radio shacks in villages, and Radio Operator villagers who trade tower parts.
- Climbable masts, Tower Platforms, guy wires, aviation beacons, solar panels and Coverage Maps.
- Advancements, sounds with subtitles, and raid alerts from village stations.

![TV Cameras and a video wall](https://raw.githubusercontent.com/Reiko45/Reiko-s-Wireless-Redstone/main/docs/images/tv.png)

## Works with (all optional)

- **Jade** or **WTHIT:** tooltips with each block's frequency, signal, reach and battery.
- **JEI** or **EMI:** an information page for every item, and the Radio Operator's trades.
- **JourneyMap** or **Xaero's Minimap:** waypoints for your towers and the old towers you've found.
- **CC: Tweaked** (1.114 or newer): transmitters, receivers and telegraphs as computer peripherals, plus radio turtles and pocket computers.
- **Reiko's Weather:** its storms cut reach, can blow down unbraced towers, and feed the Radio Weather Station.

## Requirements

- Minecraft 1.21.1 with NeoForge 21.1.
- Install it on both the server and every client.

## More

The full guide (every block, recipe and setting) is in the README on [GitHub](https://github.com/Reiko45/Reiko-s-Wireless-Redstone). This mod was called Reiko's Redstone Signal Tower before version 0.68.1. Worlds made with the old version keep everything.
