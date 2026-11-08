# Reiko's Redstone Signal Tower

A Minecraft **1.21.1 NeoForge** mod that adds radio towers for redstone. Put a transmitter high up on a tower and it sends your redstone signal to receivers far away. The higher the tower, the farther it reaches.

![Two radio towers](docs/images/towers.png)

## Blocks and items

| | What it does |
|---|---|
| **Radio Transmitter** | Broadcasts the redstone signal it receives (strength 0–15) on its frequency. The higher above sea level it sits, the farther it reaches. Its warning light glows while it's broadcasting. It also relays Remote Control presses made within reach of it. |
| **Radio Timer Transmitter** | A transmitter with a clock. It sends full strength on a schedule: a pulse every 1 second to 20 minutes, or on between two in-game times (on at 18:00, off at 06:00 lights the night). A redstone signal into it pauses it. Otherwise it's a full-size transmitter, with reach by height. |
| **Radio Signal Recorder** | A transmitter with a tape. It records up to 30 seconds of a frequency (every on, off and strength, to the tick) and plays it back on its own frequency: once each time a redstone pulse reaches it, or on a loop while it's powered. Record a rhythm on a remote and get it back as a doorbell tune, a light show or a secret knock. |
| **Radio Receiver** | Outputs the strongest signal it hears on its frequency, at the same strength (weaker far out toward the edge of a tower's reach). Works like a redstone block with a dimmer. |
| **Radio Repeater** | A relay station. It picks up its frequency where it stands and broadcasts it again, at the strength it was sent at, with its own reach. Put repeaters on towers along the way to send a signal much farther than one transmitter can. Needs no redstone. Its amber light glows while it's repeating. |
| **Radio Converter** | A repeater that changes the frequency. It picks up one frequency and broadcasts it on another, so one switch can drive receivers on a second frequency, or two networks can be joined. Its teal light glows while it's converting. Its **signal gate** can make it a wireless comparator (send only at a level or more) or a wireless NOT gate (send 15 while it hears nothing). |
| **Radio Toggle Receiver** | A receiver that works like a light switch: each time its frequency turns on, it flips between off and on (full strength). A pulse remote, a button or a pressure plate then switches things on with one press and off with the next. |
| **Radio Inverted Receiver** | A receiver with a redstone torch built in: full strength while its frequency is silent, nothing while it's on. Turning a frequency on can then switch something off. |
| **Radio Logic Receiver** | A receiver on two frequencies. It outputs when both are on (AND), when either is on (OR), or when exactly one is on (XOR), so one switch can arm or disarm what another one triggers. |
| **Radio Delay Receiver** | A receiver with a timer. It turns on and off a set **delay** after its frequency does (up to a minute, like a very long redstone repeater), and its **hold** keeps it on for a while after the frequency turns off, so a short button press can light a hallway for 30 seconds. |
| **Radio Counter Receiver** | Counts how many times its frequency turns on, and turns on once the count reaches a set number (1 to 64): for a 1-second pulse, or until a second **reset frequency** turns on. It can forget the count after a pause, so three quick presses work and three slow ones don't (a secret knock). |
| **Radio Speaker** | A receiver that also plays a sound when its frequency turns on: a bell, chime, ding, doorbell, pling, bass or beep, at any note-block pitch, quiet, normal or loud (heard 48 blocks away). Once, or every second while it stays on (an alarm). It outputs like a plain receiver. |
| **Radio Status Board** | A panel for a wall that shows up to four frequencies at a glance: each one's name, a light and the strength heard there, glowing so it reads at night. A control-room display for your base. |
| **Compact Radio Receiver** | A button-sized receiver. Sticks to walls, floors and ceilings like a button, and like a button it also powers the block it's stuck to, so it can drive redstone on the other side of a wall. Pops off (keeping its frequency) if that block is removed. |
| **Compact Radio Transmitter** | A button-sized transmitter. Sticks to walls, floors and ceilings like a button and broadcasts the redstone signal around it (a lever next to it, dust running to it), but only over a short fixed reach (16 blocks by default), whatever its height. Handy for wiring inside a base without building a tower. |
| **Radio Motion Sensor** | A button-sized eye for a wall, floor or ceiling. It sends on its frequency while it sees a player nearby, over a compact transmitter's short reach. Its owner sets how far it looks and whether it reacts to everyone or only to strangers, and the frequency log names who set it off. |
| **Code Keypad** | A keypad for a wall, floor or ceiling. Type the right code and it sends a pulse on its frequency, and powers the block it's on, so it opens a door right next to it too. Only its owner sets the code, and three wrong codes in a row lock the typer out and alert the owner. |
| **Aviation Beacon** | A red (or white) warning light for the top or the side of a tower, or any block. It blinks at night, is seen from far beyond where the tower itself fades into the dark, and all beacons blink together. It can also blink only while its tower is broadcasting, so you can see from across the map whether the tower is on. |
| **Iron, Gold, Diamond and Netherite Antennas** | Upgrades for a tower: fit one to a transmitter (plain, timer or recorder), repeater, converter or jammer and it reaches 1.5, 2, 3 or 4 times as far. You can see it on the tower, in the tooltip and in Jade. |
| **Radio Battery** | Powers the towers next to it (or next to their mast) while they send. Charge it with redstone (by hand or with a hopper) or with FE from other mods. Towers without a charged battery go quiet (see Power cost). |
| **Ender Antenna** | A transmitter upgrade that also sends its frequency into another dimension, from the matching spot there: into the Nether from the Overworld (at 1/8 of the coordinates) and back. A lever in one dimension can open a door in the other. |
| **Solar Panel** | Sits on a Radio Battery and charges it by daylight: fastest at noon in clear weather, slower in rain, nothing at night. |
| **Radio Telegraph** | A desk with a brass key for typing short messages onto a frequency (up to 64 characters). Pagers, other telegraphs and Status Boards on that frequency within reach read them, and a telegraph that gets one clicks and gives a redstone pulse. |
| **Coverage Map** | A map you tune to a frequency. It paints where that frequency can be heard: green where it's strong, yellow, orange and red as it gets weaker, blank where it isn't heard, with markers on the towers. |
| **Dish Antenna** | A tower upgrade that points: fitted, it faces the way you look, and the tower reaches three times as far in a narrow beam that way (and as far as with no antenna everywhere else). For a long, straight link to one place. |
| **Radio Lamp** | A lamp with a receiver built in. It gives off as much light as the strength it hears, from dim to full, glowing in any of the 16 dye colors (right-click it with a dye). Wireless lighting in one block. |
| **Radio Jammer** | A tower top that drowns out one frequency around it while it gets redstone. Everything there that listens on that frequency hears that much less, and a full-strength signal silences it completely. It reaches half as far as a transmitter at the same height. On someone else's private frequency it does nothing, and the owner is told. |
| **Radio Mast** | Lattice tower segment for building your tower. A mast column also carries redstone **up**: power any mast in the column and the transmitter on top broadcasts it. Right-click a tower's mast with another one and it goes in under the top, so you can raise a tower from the ground. |
| **Frequency Tuner** | Sets which frequency a transmitter or receiver uses. Keeps 5 favorite frequencies, and sneak + scroll changes its frequency without opening a screen. Its screen can also name a frequency, or make it private to you and your friends, and its **Directory** button lists every named and private frequency in the world. Hold it and look at a tower to see a ring on the ground where its reach ends. |
| **Remote Control** | A handheld transmitter, like a garage door opener. Press it to send a signal on its frequency: a 1-second pulse like a button, or toggle on/off. Has the same scrolling and favorites as the tuner. |
| **Multi-Button Remote** | A remote with 4 buttons, each with its own frequency, mode, name and color. One remote can run your door, lights and farm. |
| **Radio Pager** | A pocket receiver. Keep it anywhere in your inventory and it beeps and shows a message when its frequency turns on near you. |
| **Signal Meter** | A handheld that measures one frequency where you stand: a strength bar beside the hotbar, a Geiger-counter tick, and how many blocks are left to the edge of the tower's reach. Quick for placing receivers at the edge. |
| **Signal Scanner** | A handheld direction-finder. It lists every frequency you can hear where you stand, how strong it is and where it comes from, and can tune the radio item in your other hand. |
| **Wall Redstone** | Redstone dust that sticks to the side of a block. It runs up, down and sideways along a wall, loses 1 strength per block like normal dust, connects to normal dust and redstone components, and powers the block it's on. |

![Compact Radio Receivers on a ceiling, on top of a block, on both sides of a block, and on a wall powering a lamp behind it](docs/images/compact-receivers.png)

![Wall Redstone running up and across a wall, fed by a lever and by normal dust](docs/images/wall-redstone.png)

## How to use it

1. **Build a tower.** Stack Radio Masts and put a Radio Transmitter on top. (Or put the transmitter on one mast and right-click the mast with more: each goes in under the top, see Mast builder.)
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
- **Shared:** names belong to the world. Everyone on a server sees the same names, they're saved with the world, and they count in every dimension. Anyone holding a tuner can rename a frequency, except a private one (only its owner and their friends can).
- **Where they show:** most places a frequency number appears, as "2026 (Front Gate)":
  - item tooltips (tuner, remotes, pager, radio blocks);
  - the remote's press message and the pager's alerts, chat log and catch-up lines;
  - the tuner's "Tuned to" message and the empty-hand readouts;
  - the scanner's rows ("Front Gate: Repeater, 7 blocks east") and its tracking arrow;
  - Jade, and the converter screen.

  The Remote Control and pager screens show the typed frequency's name under the title.
- **Unnamed frequencies:** they show just their number, as before. A world holds up to 4,096 names.

  ![Naming 2026 "Front Gate" in the tuner, and the name in the remote's screen, Jade, the tuner's tooltip, the scanner and a remote press](docs/images/frequency-names.png)

**Frequency notes:**
- **Adding one:** in the Frequency Tuner's screen, type the frequency and click **Note** (next to the Name box). Type a short note on what it's for, like "lever by the front door; also lights the hall" (up to 64 characters), and press **Save** or Enter: "Note for frequency 2026 (Front Gate) saved". Clear the box and save to remove it. The Note button turns yellow when the typed frequency has a note, and hovering it shows the note.
- **Shared:** like names, notes belong to the world: everyone on a server sees them, they're saved with the world, and they count in every dimension. Anyone holding a tuner can change one, except on a private frequency (only its owner, their friends and server operators can; others see it read-only).
- **Members only:** on a private frequency, the Note screen's **Who sees it** button switches between Everyone and **Members only**; press Save: "Note for frequency 2026 (Front Gate) saved, for members only". Then only the owner, their friends, members of groups the frequency uses and server operators get the note. Everyone else's game never receives it, so it's missing from their directory, Jade and tooltips. Members see it marked "(members only)", and the tuner's Note button tooltip says "Note (members only): …".
  - Adding or taking off friends or group members gives them the note or takes it away at once.
  - Making the frequency public makes the note everyone's again for good (the tuner warns "Done makes it public again, and its note visible to everyone"); claiming it later doesn't hide it again.
  - On a public frequency the button is greyed out: only a private frequency's note can be kept to its members.
- **Where they show**, in grey italics:
  - the frequency directory: after the name in the Name column, in the row's tooltip, and the search box matches notes too ("hall" finds 2026);
  - Jade, under the frequency line (a converter shows both its frequencies' notes);
  - item tooltips: the tuner, Remote Control, pager, radio blocks, the converter (both frequencies) and the Multi-Button Remote's selected button.
- A frequency with only a note (no name, public) is listed in the directory too. A world holds up to 4,096 notes.

  ![The Note screen for 2026 (Front Gate); Jade showing the note under the frequency; the directory finding it by searching "hall", with the note in the tooltip; and the tuner's item tooltip](docs/images/frequency-notes.png)

  ![The Note screen with "Who sees it: Members only", the tuner's "Note (members only)" tooltip, Steve's Jade showing the note marked "(members only)", and Alex's Jade on the same frequency without it](docs/images/members-only-notes.png)

**Private frequencies:**
- **Making one private:** in the Frequency Tuner's screen, type the frequency and click the button under the Name box so it says **Private** instead of **Public**, then press Done: "2026 (Front Gate) is now private: only you can send on it". The line under the button says who can send on the typed frequency, or what Done will change.
- **Friends:** type players' names in the box next to the button, separated by commas (up to 16), and press Done: "only you and Steve can send on it". A friend must be online the first time you add them; after that they stay a friend by their account, online or not. Delete a name and press Done to take them off.
- **What private means:** only you and your friends can send on it.
  - Other players' remote presses are refused, with a red light: "Can't send: 2026 (Front Gate) is private to Dev".
  - Transmitters, repeaters and converters only broadcast on it if you or a friend tuned or placed them (each block remembers who last tuned or placed it). Anyone else trying to tune one onto it is refused: "Can't do that: it would send on 2026 (Front Gate), which is private to Dev". One they place with that frequency already on it stays silent: "Silent: 2026 (Front Gate) is private to Dev, so this won't broadcast".
  - Taking a friend off silences their blocks on it at once; adding them back brings them back.
  - Only you and your friends can rename it.
- **Listening stays open:** anyone's receivers, toggle receivers, pagers and scanners still hear it, and anyone can tune a receiver to it. To close that too, encrypt it (below).
- **Claiming fairly:** you can't make a frequency private while other players' transmitters, repeaters or converters send on it ("other players' blocks already send on it"). Pick one nobody else uses.
- **Older blocks:** blocks tuned before version 0.25 don't know who tuned them, so they keep working on a private frequency. Tune one again to make it yours.
- **Who can change it:** only the owner can change the friends or make it public again (click the button until it says **Public**, then Done). Server operators can make anyone's private frequency public the same way. Other players see the button greyed out: "Private to Dev: you can't send on it".
- **Friend groups:** click **Groups** next to the Friends box to manage your own named lists of players, like "Base crew".
  - In the Groups screen, click **+ New group**, type a name and its members (separated by commas), and press **Save**: "Group Base crew created with 1 member". Pick a group on the left to change it; **Delete** (click twice) removes it.
  - Use a group on a frequency by typing `#Base crew` in the Friends box (on its own, or with single names: `#Base crew, Alex`), or click **Use here** in the Groups screen to add it to the frequency in the tuner. Done: "only you and #Base crew can send on it".
  - Changing a group changes every frequency using it at once: add someone and they can send on all of them; take them out and their blocks on all of them go silent. The Groups screen shows which frequencies use the selected group ("Used by: 2026 (Front Gate), 4242 (Vault)"). Renaming a group keeps it on those frequencies; deleting it takes its members off them.
  - Members must be online the first time you add them, like friends. Up to 16 groups per player, 32 members per group, and 4 groups per frequency.
- **Encrypted frequencies:** click the button once more so it says **Encrypted** (it goes Public, Private, Encrypted; sneak-click goes back) and press Done: "2026 (Front Gate) is now encrypted: only you and #Base crew can send on it or hear it". An encrypted frequency is private, and listening is closed too:
  - Only receivers, toggle receivers and converters that you or a friend (or a member of a group it uses) tuned or placed hear it. Anyone else's stay off. Jade shows "Deaf: encrypted by Dev" on them, and tuning or placing one says "Deaf: 2026 (Front Gate) is encrypted by Dev, so this won't hear it".
  - Only pagers and scanners carried by you or a friend hear it. Anyone else's pager stays quiet (its tooltip says "Encrypted by Dev: this pager can't hear it") and reports nothing about it after logging in. Their scanner doesn't list it, can't track its towers, and stops tracking one once the frequency is encrypted.
  - In anyone else's directory it just says "Encrypted", even standing next to its tower.
  - Remotes' "Heard by" counts and locator lines only count receivers and pagers that can hear it.
  - Adding a friend (or a group member) lets their receivers hear it at once; taking them off makes them deaf again. A toggle receiver doesn't count becoming able to hear (or deaf) as a turn-on.
  - Changing it back to **Private** opens listening again, without changing who can send.
  - Old blocks tuned before version 0.25 (no recorded tuner) still hear it, like they can still send. Tune one again to make it yours.
- **Limits:** up to 32 private frequencies per player (`privateFrequenciesPerPlayer` in the server config; 0 turns the feature off), and 4,096 per world.
- **Jade** shows "Private to Dev" (or "Encrypted by Dev") on a private frequency's blocks, "Silent: private to Dev" on ones kept quiet, and "Deaf: encrypted by Dev" on receivers and converters that can't hear. The empty-hand readout and the converter's screen also say when a block is kept quiet.

  ![Dev adding Steve as a friend on private frequency 2026; Steve's tuner screen before that ("Private to Dev: you can't send on it"); Jade on a friend's and on a silenced transmitter; and the messages for claiming, a refused remote press, a refused tuning and a silent placement](docs/images/private-frequencies.png)

  ![Encrypting 2026 in Dev's tuner ("Done encrypts it: only members will hear it"); Steve's receiver on 2026 deaf ("Deaf: encrypted by Dev", lamp off); and lit once Dev added Steve to Base crew ("Encrypted by Dev", Output 15)](docs/images/encrypted-frequencies.png)

  ![Dev's Groups screen with "Base crew" (Steve), used by 2026 and 4242; Steve's tuner on 4242 ("Private to Dev: you're a friend, so you can send"); and the messages: 4242 made private for #Base crew, Steve's press working, and refused once Dev took him out of the group](docs/images/friend-groups.png)

**Frequency directory:**
- **Opening it:** click **Directory** in the Frequency Tuner's, Remote Control's or Radio Pager's screen (between Done and Cancel). The Multi-Button Remote's keypad has a small **...** button after each button's frequency, and the Radio Converter's screen one after each of its two boxes (that works with nothing in your hand). It lists every named and private frequency in the world, in order.
- **Columns:**
  - **Frequency** and **Name**. The one typed in the screen you came from is marked "»".
  - **Who sends:** "Anyone" for a public frequency; "You" or "You + 2" (friends) for your own private one; "Dev + you" for one where you're a friend; "Dev" (red) for someone else's.
  - **Now:** "On 15" if something is sending on it anywhere in your dimension, "On 15, here" if you also hear it where you stand, "Off" if nothing is.
- **Others' private frequencies stay private:** for someone else's private frequency the directory only says "Here 15" when you can hear it where you stand, and "?" otherwise, so it can't be used to watch their base from afar. Someone else's encrypted frequency only says "Encrypted".
- **Using one:** click a row and it goes into the screen you came from (with its name and privacy, in the tuner's; into that button's or that box's frequency in the keypad and the converter's screen), so you just press Done. **Back** returns with what you'd typed.
- **Finding one:** type in the search box to match a name, a note, a number or an owner. The **Show** button switches between All, Named, Private, Mine (private ones you own or are a friend on) and On now; sneak-click goes back. Hover a row for details: who owns it, its friends (if you're one of them), and whether it's on.
- It updates every second while open.
- **Auditing one of yours:** sneak-click one of your own private frequencies (an operator: anyone's) to list every block on it, in every dimension: transmitters, repeaters and converters sending on it, converters listening on it, and receivers (only while their chunk is loaded).
  - Each row says what it is, where it is, who tuned or placed it, and its status: "On 15" or "Off" when it works, **Silent** for a sender whose tuner can't send on it, **Deaf** for a listener whose tuner can't hear it (encrypted). Hover a row for the distance and direction and what the status means. The line under the list sums it up ("10 blocks: 1 silent, 1 deaf, 5 with no recorded tuner").
  - Your blocks and your friends' show their position. So do old blocks with **no record** of who tuned them (set up before version 0.25): they still work on a private frequency, so the audit helps you find them; tuning one again with the Frequency Tuner makes it yours.
  - Other players' blocks are only counted ("Receiver ×2, Alex, Deaf"), without positions, so the audit can't be used to find someone's base.
  - **Finding one:** click a row that shows a position to put a gold beam of light over that block for a minute ("Beam on: Repeater at 535, −60, −15, for a minute. Close to see it"). The row gets a gold edge and its tooltip counts down; click it again to turn the beam off. Press **Close** (next to Back) to close every screen and look around. You can have up to 8 beams on at once.
    - Only your own game draws the beams, so nobody else sees them.
    - They show from far away, even past your render distance's fog where the block itself isn't drawn. Far beams are drawn wider so they stay easy to spot.
    - A beam in another dimension shows when you're there. Beams go away when you leave the world.
  - **Handing it over:** the owner's audit has a **Hand over...** button. It lists the frequency's friends and group members; click the one who should own it, then **Hand over** twice. Both of you get a chat message ("4242 (Vault) now belongs to Steve. You stay on it as their friend" and "Dev handed 4242 (Vault) over to you: it's yours now").
    - Everyone else on it, you included, stays on it as a friend, so no block goes silent. It keeps its name, note and encryption.
    - Groups belong to their owner, so their members become single friends of the new owner.
    - It's refused if the new owner already has the most private frequencies a player may have, or if it has more members than a frequency's 16 friends (take some off first). Only the owner can hand it over; operators can't.
  - **Its log:** the audit's **Log** button (for the owner and server operators) lists the frequency's last turn-ons, newest first, from every dimension. Each row says when (in-game day and time), what turned it on (a **Transmitter**, a **Remote pulse** or a **Remote toggle**) and who (who tuned the transmitter, or whose remote).
    - A transmitter that's yours, a friend's or has no record shows where it is; click its row for a beam of light over it, as in the audit. Another player's transmitter is named but not located, and where someone pressed a remote is never kept.
    - Hover a row for how long ago it was ("47 seconds ago"). A turn-on carried over from another frequency by a converter says so ("Transmitter on 2026").
    - Each dimension keeps the last 32 turn-ons of each frequency (the same ones the pager catch-up uses). Ones from before version 0.35 show as "Turned on", without a cause.
  - **Guest passes:** the owner's audit has a **Guests...** button. Type a player's name (they must be online), then pick **1 hour**, **1 day** or **1 week**: "Alex is a guest on 2026 (Front Gate) for 1 hour". Until the pass runs out they can send on it like a friend (and hear it, if it's encrypted), and they're told in chat.
    - The list shows each guest's time left ("59 minutes left"). Click one, then pick a time to renew their pass, or **Remove** to end it now.
    - Passes run on real time, so they keep counting while the server is off. When one runs out, the guest drops off, their blocks go silent (or deaf) again, and both of you are told if you're online.
    - Up to 8 guests per frequency. Friends and group members don't need a pass. Editing the friends or handing the frequency over keeps the guests. In the audit and the log, a guest's blocks show like a friend's.
  - **Intruder alerts:** when someone who isn't on one of your private frequencies tries to use it, you're told in chat: "Radio alert: Alex pressed a remote on 2026 (Front Gate), but it's private: refused". It covers a refused remote press, a transmitter, repeater or converter they tried to tune onto it, a block they placed on it that stays silent, and one they set to it that can't hear it (encrypted). Since version 0.37 it also covers three wrong codes in a row on one of your Code Keypads, on any frequency (see **Code Keypad** below).
    - Like the audit, an alert never says where their block is. (A keypad alert says where your keypad is.)
    - The same player, frequency and kind of try alerts at most once a minute.
    - If you're offline, you get them when you log in: "While you were away, someone tried your private frequencies or keypads (3 times):", then the last five with their in-game day and time.

  ![The directory as Dev (left) and as Steve (right): Dev's own and public frequencies show whether they're on, Steve's private ones only when Dev can hear them; the tooltip on Steve's Farm; and a frequency picked into the Remote Control's screen](docs/images/frequency-directory.png)

  ![The audit of 2026: Dev's re-tuned transmitter first, Steve's (a friend's) blocks, old blocks with no record, and Alex's silent transmitter counted without a position; the tooltips for an old block and for Alex's](docs/images/frequency-audit.png)

  ![The audit with beams on over Steve's receiver and a repeater 135 blocks east (gold edges, "Beam on for 58 more seconds"), and the two gold beams in the evening sky](docs/images/audit-beams.png)

  ![The log of 2026: Dev's and Steve's remote pulses, Steve's transmitter twice (with its position, and its tooltip: "Tuned by Steve, a friend", 5 blocks south-east), older turn-ons without a cause; and a remote pulse's tooltip ("Where they stood isn't kept")](docs/images/frequency-log.png)

  ![Dev's Guests screen giving Alex an hour on 2026; Alex's receiver hearing 2026 during the pass and his chat line; the alert Dev got when Alex pressed a remote after the pass was taken back; and the login summary of a tune Alex tried while Dev was away](docs/images/guests-and-alerts.png)

  ![The motion sensor wall with a Radio Logic Receiver on 4242 AND 56: with the "armed" lever off, Alex lights only the plain 4242 alarm; with it on, the logic receiver's lamp lights too; its screen ("Hearing 15 on 4242 (Vault) and 15 on 56", "Output: 15"); the Signal Meter on 55 by the compact transmitter ("1 tower, 9 blocks to the edge"), past its reach ("off", "Out of every tower's reach"), and Alex's meter on Dev's encrypted 2026 ("Encrypted by Dev")](docs/images/logic-receiver-and-signal-meter.png)

  ![A wall with a Radio Motion Sensor between two receivers with lamps on 4242: with nobody near, the inverted receiver's lamp is lit; with Alex walking up, the sensor glows, the alarm lamp lights and the inverted one goes out; Dev's sensor screen set to 6 blocks and Strangers only; Jade for Dev ("Looks 6 blocks, reacts to strangers only", "Sees Alex: sending"); Jade on the inverted receiver ("Off (frequency on)", "Hearing: 15"); and the log of 4242 with Alex's turn-on in red](docs/images/motion-sensor-and-inverted-receiver.png)

  ![A Compact Radio Transmitter's 16-block ring passing between two lamps, the near one lit and the far one dark; Dev's Guest codes screen with Plumber and Family; Alex opening the door with Plumber's code; Dev's chat line for each use; the fourth try "That guest code is used up"; and Dev's log with "Guest code: Plumber"](docs/images/compact-transmitter-and-guest-codes.png)

  ![The reach ring: a transmitter's red ring seen from 7 blocks outside it, and a repeater's 88-block ring from above; Dev's log with the Code Keypad's turn-ons typed by Alex; Alex locked out after three wrong codes; Dev's alert in chat with where the keypad is; and the right code opening the iron door](docs/images/reach-ring-and-keypad.png)

  ![Dev handing 4242 (Vault) over to Steve, a member through the Base crew group ("Click again"); Dev's directory afterwards ("Steve + you"); Steve's tuner on 4242 (Private, friend Dev) and his chat line](docs/images/hand-over.png)

  ![The Multi-Button Remote's keypad with a directory button after each frequency (3001 picked for button 2, which now finds a receiver), and a Radio Converter's screen with one after each box (42, Farm Door, picked for Sends on)](docs/images/directory-everywhere.png)

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
- **Tune:** hold a Frequency Tuner, Remote Control, Multi-Button Remote, Radio Pager or Signal Meter in your other hand, and each row's Tune button sets that frequency on it. A remote keeps its mode and pulse length, and a Multi-Button Remote changes only its selected button.

![The Signal Scanner listing five frequencies, after tuning the Remote Control in the off-hand to 42](docs/images/signal-scanner.png)

**Signal Meter (since 0.40):**
- Hold it (either hand) and a small panel to the right of your hotbar shows its frequency, a 15-segment bar with the strength a receiver where you stand would get, and a line like "1 tower, 9 blocks to the edge" (or "1 block to the edge", "right at the edge", "Out of every tower's reach"). It updates five times a second, and the needle on the meter in your hand swings with it.
- **The edge:** it counts the tower on that frequency whose reach goes farthest past where you stand, the same way as the reach ring. A transmitter that's switched off still counts, so you can place receivers before turning it on. Blocks kept quiet on someone else's private frequency don't.
- **Tick:** it ticks like a Geiger counter, faster and higher the stronger the signal. Right-click to turn the tick on or off.
- **Frequency:** sneak + right-click the air opens its screen (frequency, favorites, Tick, Directory). Sneak + scroll changes it like the tuner. Sneak + right-click a radio block measures the frequency it sends on (a converter's output), or its own. The scanner's Tune button sets it too.
- **Encrypted frequencies:** on someone else's encrypted frequency it says "Encrypted by Dev" and measures nothing, like a receiver of yours there.

**Jade (optional):**
- If you have [Jade](https://modrinth.com/mod/jade) installed, just look at a block to see its details in Jade's info box, with no clicking:
  - **Radio Transmitter:** frequency, what it's sending ("Sending: 15" or "Sending: nothing") and its reach.
  - **Radio Repeater:** frequency, what it's repeating ("Repeating: 15" or "Nothing to repeat") and its reach.
  - **Radio Converter:** both frequencies ("Listens on: 2026", "Sends on: 3001"), what it's converting and its reach.
  - **Radio Receiver** and **Compact Radio Receiver:** frequency, output ("Output: 15" or "Output: off") and how many transmitters and repeaters reach it ("Towers in range: 2").
  - **Radio Toggle Receiver:** frequency, "Switched on" or "Switched off", what it's hearing right now, and towers in range.
  - **Radio Mast:** what's on top of its column and how far up ("Top: Radio Repeater, 3 blocks up"), and that tower's frequency. Handy at the foot of a tall tower.
  - On a private frequency, any of them also shows "Private to Dev", or "Silent: private to Dev" for a transmitter, repeater or converter kept quiet.
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

### Inverted receivers

A **Radio Inverted Receiver** (since 0.39) is a receiver with a redstone torch built in. It outputs **full strength (15) while its frequency is silent** where it stands, and **nothing while it hears it** at any strength.

- **Use it to switch something off:** a lamp that's on until a motion sensor sees someone, a door that's open until a remote closes it, or a farm that runs until a lever on the other side of the base stops it.
- **Its lights:** it glows while it outputs (while the frequency is silent). The torch on its sides is lit then, and the incoming waves light up instead while it hears the frequency.
- **Someone else's encrypted frequency:** it hears nothing there, so it stays on. That way it can't be used to tell when that frequency is on.
- **Readout and Jade:** an empty-hand right-click shows "Inverted Receiver | Frequency 4242 | Hearing 15 | Output 0 | Towers in range: 1". Jade shows "On (frequency silent)" or "Off (frequency on)", and what it's hearing.
- Otherwise it's a receiver: the tuner tunes it, it keeps its frequency when broken, and the audit lists it as an Inverted Receiver.

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
- Breaking it keeps both frequencies (and its gate) on the dropped item.

![The converter's screen being set to listen on 2026 and send on 3001; Jade showing it converting; the scanner hearing both frequencies; and the receiver on 3001 lit](docs/images/radio-converter.png)

#### Signal gate

Since 0.41, the converter's screen has a **Gate** button and a **Level** button (1 to 15) under the two frequencies:

| Gate | Sends | Like |
|---|---|---|
| **Pass** (the default) | what it hears, at the same strength | a plain converter |
| **At least** | what it hears, but only when it's at the level or more | a comparator |
| **Below** | 15 while what it hears is below the level | at level 1, a NOT gate: 15 while its frequency is silent |

- **An example:** a lever on 4300, and a converter listening on 4300 and sending on 4301 with **Below 1**. A lamp on a 4301 receiver lights when the lever is off and goes out when it's on. With **At least 8**, a 4301 receiver only lights when 4300 arrives at 8 or more.
- **The screen** shows what it sends right now ("Sending 15 on 4301" or "Gate closed: nothing sent on 4301") and what it hears ("Reach: 56 blocks | hearing nothing"). Changing the gate shows what it will send once saved, in yellow ("Once saved: sends 15 on 4301").
- **A gate is a new source,** like a transmitter: when its frequency turns on, the log lists a "Converter gate", with where it is and who tuned it, and it doesn't carry remote presses across (a press on 4300 doesn't become one on 4301).
- **Loops are safe:** a NOT gate listening on what it sends blinks on and off, but no faster than every 2 ticks, so it never hangs the server. That makes a simple wireless clock.
- **Jade** says "Sending: 15" or "Gate closed: sending nothing", and "Gate: 15 while it hears nothing (hearing nothing)". The item's tooltip shows the gate too.

### Logic receivers

A **Radio Logic Receiver** (since 0.40) listens on two frequencies and outputs according to its mode:

| Mode | Outputs while | Strength |
|---|---|---|
| **AND** | both frequencies are on | the weaker of the two |
| **OR** | either one is on | the stronger of the two |
| **XOR** | exactly one of them is on | that one's strength |

- **Setting it up:** right-click it with an empty hand to open its screen: a box for each frequency (each with a **...** directory button), the mode button between them (click for the next mode, Shift-click to go back), and what it hears on each and outputs right now. Enter or **Done** saves ("Radio Logic Receiver: 4242 AND 56"). The Frequency Tuner sets (and copies) the first frequency.
- **An example:** a motion sensor on 4242 and a lever on a compact transmitter on 56 ("armed"). A logic receiver set to 4242 AND 56 only rings the alarm when the alarm is armed and someone's seen.
- **Counts as a receiver on both:** a remote's "Heard by", its signal light and the locator count it on either frequency, and the audit lists it under both as a Logic Receiver.
- **Encrypted frequencies:** on someone else's encrypted frequency it hears nothing there (that input counts as off). It says so when you set it, and Jade shows "Second deaf: encrypted by Dev".
- **Jade** shows both frequencies, the mode, "Hearing: 15 and nothing" and the output.
- Breaking it keeps both frequencies and the mode on the dropped item.

### Delay receivers

A **Radio Delay Receiver** (since 0.41) is a receiver with a timer. It outputs full strength (15) while it's on.

- **Delay:** it turns on this long after its frequency turns on, and off this long after it turns off. Every change comes through, in order, just later, like a very long redstone repeater (none, 0.5, 1, 2, 3, 5, 10, 30 or 60 seconds; default 1 second).
- **Hold:** it stays on this long after it would turn off (none by default, up to 60 seconds). A button pressed for a moment on a remote can then keep a hallway lit for 30 seconds.
- **Setting it up:** right-click it with an empty hand to open its screen. Click **Delay** or **Hold** for the next time (Shift-click or scroll to go back), then **Done** ("Radio Delay Receiver: delay 1s, hold 2s"). The screen also shows what it hears, whether it's on, and when it next changes ("Turns off in 1.1s", counting down). The Frequency Tuner sets its frequency like any receiver.
- **Jade** shows the delay and hold, what it hears, and "Turns on in 0.4s" or "Turns off in 2.1s" while a change is under way.
- **When it's loaded or re-tuned** it starts from what its frequency is doing right then, with nothing under way.
- **Counts as a receiver:** "Heard by", a remote's signal light, the locator and the audit ("Delay Receiver") all count it. On someone else's encrypted frequency it hears nothing.
- Breaking it keeps its frequency, delay and hold on the dropped item, and the item's tooltip shows them.

![A NOT gate set on the converter's screen; Jade counting down on the delay receiver while the lever is on; with the lever off, the NOT gate's lamp lit and the delay receiver's lamp held on; Alex seeing the same from across the row; the delay receiver's screen counting down; and the gate on the converter's item](docs/images/signal-gate-and-delay-receiver.png)

### Timer transmitters

A **Radio Timer Transmitter** (since 0.42) sends full strength (15) on its frequency on a schedule, with nothing wired to it. Right-click it with an empty hand to open its screen:

| Mode | Settings | Sends |
|---|---|---|
| **Repeating** (the default) | **Every** 1, 2, 3, 5, 10, 15 or 30 seconds, or 1, 2, 5, 10 or 20 minutes (default 10 seconds); **Pulse** 0.1 to 10 seconds (default 1 second) | a pulse every interval, starting when you press Done. A pulse is at most half the interval. |
| **Daily** | **On at** and **Off at**, whole in-game hours (default 18:00 and 06:00) | 15 between those times every in-game day. On and off at the same time means never. |

- **Pausing:** a redstone signal into it (or into a mast under it) stops it sending. A lever next to it is an off switch, and a daylight sensor can keep a repeating timer quiet by day.
- **The screen** shows what it's doing ("Sending 15 on 4400", "Not sending right now", "Paused: a redstone signal reaches it") and what's next: "Next pulse in 1.5s" counting down, or for a daily timer "It's 14:20: turns on at 18:00". While you change something, a yellow line says what Done will set ("Once saved: every 2s, 0.5s pulse, starting now", "Once saved: on 18:00 to 06:00 (12 hours a day)"). Click a button for the next value; Shift-click or scroll goes back.
- **Jade** shows its schedule ("Timer: every 2s, 0.5s pulse") and "Next pulse in 0.8s" / "Pulse ends in 0.1s", "On until 06:00" / "Turns on at 18:00", or "Paused".
- **It's a transmitter:** reach by height and masts, relaying remote presses, the tuner and the red reach ring work as usual ("Reach ring: Radio Timer Transmitter on 4400, 56 blocks"). On a private frequency it only sends if a member placed or tuned it, and only members can change its schedule (others get a red refusal, and the owner an alert). The log lists its turn-ons as "Timer", with where it is; the audit as "Timer Transmitter".
- Breaking it keeps its frequency and schedule on the dropped item, and the item's tooltip shows the schedule ("Timer: every 2s, 0.5s pulse").

### Counter receivers

A **Radio Counter Receiver** (since 0.42) counts how many times its frequency turns on where it stands (from off to any strength) and outputs full strength (15) when the count reaches its target. Right-click it with an empty hand to open its screen:

- **Turn on at:** 1 to 16, 20, 24, 32, 48 or 64 turn-ons (default 3).
- **Then:** **1s pulse, count again** (the default: on for a second, back to 0), or **Stay on until reset**.
- **Forget after:** none (the default), or 0.5 seconds to 60 seconds without a turn-on, after which the count starts over. With 5 seconds, three quick presses open the door but three slow ones don't.
- **Reset by:** a second frequency (empty for none, with "..." for the directory). When it turns on, the count goes back to 0 and the receiver switches off. **Reset count** in the screen does the same.
- **The screen and Jade** show the count ("Count: 2 of 3", "Reached 3: on until reset"), what it hears, and "Forgets the count in 2.7s" counting down.
- **An example:** a Remote Control on 4410 and another on 4411; a counter receiver counting 4410, Turn on at 3, Stay on until reset, Forget after 5s, Reset by 4411. Three quick presses of the first remote light its lamp, and one press of the second puts it out.
- It counts as a receiver on both frequencies ("Heard by", the remote's light, the locator, the audit as "Counter Receiver"). Pressing Done makes you its tuner, like the logic receiver's screen; on someone else's encrypted frequency it hears nothing there ("Can't hear its reset frequency: encrypted by Dev"). Its count is saved with the world, but turn-ons while its chunk isn't loaded aren't counted.
- Breaking it keeps its frequency and settings (not the count) on the dropped item.

![The timer's screen set to a 0.5 s pulse every 2 s; its daily mode before saving; Jade on the timer mid-pulse with the lamp lit; the counter's screen at 2 of 3 and forgetting in 2.7 s; Jade on the counter at "Reached 3: on until reset" with its lamp lit; and Alex's lever pausing the timer](docs/images/timer-transmitter-and-counter-receiver.png)

### Speakers

A **Radio Speaker** (since 0.43) is a receiver (it outputs what it hears, like a Radio Receiver) that also plays a sound when its frequency turns on. Right-click it with an empty hand to open its screen:

- **Sound:** Bell, Chime, Ding, Doorbell (two notes, "ding-dong"), Pling, Bass or Beep. They're the game's own sounds, so they show in subtitles ("Bell rings", "Note Block plays") and follow the Blocks volume slider.
- **Pitch:** any of a note block's 25 notes, F♯3 to F♯5 (F♯4 is the sound's own pitch).
- **Volume:** quiet or normal (heard within 16 blocks) or loud (within 48).
- **Plays:** **Once when it turns on** (the default), or **Every second while on**, for an alarm that rings until the signal stops.
- **Test** plays the sound just for you, so you can try sounds before saving.
- A note particle pops out of the top each time it plays. Placing it, loading it or re-tuning it while its frequency is on doesn't ring it.
- **An example:** a speaker on 4410 set to Doorbell. Any remote press on 4410 within reach rings it, with "Note Block plays" in the subtitles.
- Jade shows what it plays ("Plays: Doorbell, F♯4, normal, once"). It counts as a receiver ("Heard by", the remote's light, the locator, the audit as "Speaker"), and breaking it keeps its frequency and sound on the item.

### Status boards

A **Radio Status Board** (since 0.43) hangs on the side of a block, like a wall sign, and shows up to four frequencies:

- Each row has a light (green while the frequency is on, dark red while it's off), the frequency's name if it has one (with the number under it) or its number, and on the right the strength heard where the board hangs, or "off". The text is drawn full bright, so the board reads in the dark.
- **Setting it up:** right-click it with an empty hand. Type up to four frequencies (leave a box empty for an empty row), or pick them with "..." from the directory. Next to each box the screen shows what that row shows now ("Front Gate: 15", "off").
- **Encrypted frequencies:** the board hears them as whoever placed it or last pressed Done. On someone else's encrypted frequency that row shows "enc" with a grey light.
- It's only a display. It outputs no redstone and isn't counted by "Heard by" or the audit.
- Jade lists the rows ("2026 (Front Gate): 15", "4300: off"). It pops off if the block behind it goes, and keeps its rows on the dropped item.

![The speaker's screen set to the doorbell; Alex's remote press ringing it, with Jade and the "Note Block plays" subtitle; the board's screen with each row's state; the board on a wall showing the timer's pulse on 4400 and Front Gate on; the board glowing at night; and a speaker item's tooltip with its settings](docs/images/radio-speaker-and-status-board.png)

### Signal recorders

A **Radio Signal Recorder** (since 0.44) is a full-size transmitter (reach by height, masts, relaying remote presses) that broadcasts a recording instead of the redstone around it. Right-click it with an empty hand to open its screen:

- **Records from:** the frequency it records, as heard where it stands (with "..." for the directory). **Plays on:** the frequency it plays on (the tuner sets this one too). They have to be different.
- **Record** waits for Records from to turn on ("Waiting for 4410 to turn on..."), then records every change (off, on, and the strength) to the tick, until **Stop**, 30 seconds, or 256 changes. The time after the last change until Stop counts too, so a loop keeps its pause. The old recording stays until the new one starts.
- **Plays:** **once on a redstone pulse** (the default): each time a redstone signal reaches it, or a mast under it, it plays the recording from the start. Or **on a loop while powered**: over and over while the signal stays, stopping when it goes. **Play** plays it once now; **Clear** forgets it.
- **The screen** draws the recording as a strip (each stretch as tall as its strength, a white line where it's playing) and says what it's doing: "Recording: 3.4s, 6 changes", "Recorded: 4.7s, 10 changes", "Playing: 0.7s of 4.7s", what it hears and whether redstone reaches it. Record, Stop, Play and Clear also save the boxes, and keep the screen open.
- **An example:** a recorder recording 4410 and playing on 4420, with a receiver and a lamp on 4420. Press Record, close the screen and press a Remote Control on 4410 in a rhythm; open it again and press Stop. A button next to it now plays the rhythm on the lamp (or on a speaker on 4420), and a lever with Loop plays it over and over.
- **Jade** shows what it records from, the tape ("Tape: 4.7s, 10 changes; plays on a loop while powered") and "Playing: 1.1s of 4.7s". The log lists its turn-ons as "Signal Recorder", the audit as "Signal Recorder", and the tuner shows its reach ring.
- **Private and encrypted frequencies:** pressing a button makes you its tuner. It only plays on a private frequency for members (others get a red refusal, and the owner an alert), and records someone else's encrypted frequency as silence ("Deaf: 4410 is encrypted by Dev").
- Breaking it keeps its frequencies, mode and recording on the dropped item, and the item's tooltip shows them.

### Aviation beacons

An **Aviation Beacon** (since 0.44) is a warning light for towers. Put it on top of a transmitter, repeater or converter, on the side of a mast (its bracket reaches the mast), or on any block's top, side or underside.

- **When it's on:** **at night** (the default: from dusk to dawn, the hours you can sleep, and in rain and storms; always in the Nether and the End), **all the time**, or **while its tower sends**: only while the tower it's on (the tower top it stands on, or the one on top of the mast it's on) is broadcasting.
- **Pattern:** **Blink** (one second on, one off), **Flash** (a short flash every 1.5 seconds, like a strobe) or **Steady**. **Color:** red or white.
- **Seen from far away:** its glow grows with distance and isn't fogged, so at night the lights show as far as the world is drawn, long after the tower itself has faded. Every beacon blinks by the same clock, so all the lights on a tower (and on every tower) blink together.
- **Setting it:** right-click it with an empty hand. The screen has the three buttons, a little light that blinks the way it will, and which tower it's on ("On a Radio Transmitter (15 blocks up): sending", or "Not on a tower").
- **Jade** shows how it blinks ("Blinks red at night"), "On now" or "Off now", and its tower ("Tower: Radio Transmitter on 4430, 15 blocks up, sending").
- It's only a light: no redstone, no frequency. Anyone can change it. It pops off if what it's on goes, and keeps its settings on the dropped item.

![From 150 blocks at night only the tower's blinking beacons show; the same tower closer; a white beacon set to "while its tower sends" lit while the lever is on and dark after Alex switches it off (Jade says why); the beacon's screen; Jade on a recorder looping a rhythm onto a lamp; and the recorder's screen after recording five remote presses, then playing them](docs/images/aviation-beacon-and-signal-recorder.png)

### Antennas

**Antennas** (since 0.45) make a tower reach farther. Right-click a full-size Radio Transmitter, Radio Timer Transmitter, Radio Signal Recorder, Radio Repeater, Radio Converter or (since 0.46) Radio Jammer with one:

| Antenna | Reach | Made from |
|---|---|---|
| **Iron Antenna** | ×1.5 | 4 Iron Ingots and Redstone Dust |
| **Gold Antenna** | ×2 | an Iron Antenna, 2 Gold Ingots and Redstone Dust |
| **Diamond Antenna** | ×3 | a Gold Antenna and 2 Diamonds |
| **Netherite Antenna** | ×4 | a Diamond Antenna and a Netherite Ingot |

- The multiplier applies to the tower's whole reach (from its height), and the server config's `maxReach` still caps it. A transmitter 23 blocks above sea level reaches 216 blocks; with a Diamond Antenna, 648 ("Radio Transmitter: Diamond Antenna fitted, reach 216 → 648 blocks").
- **Changing it:** fitting another one replaces it and gives the old one back. **Sneak + right-click** the tower with an empty hand to take it off (you get it back). In creative mode antennas aren't used up or given back.
- **You can see it:** bands round the antenna rod and caps on the dipole's ends in the antenna's metal. Jade shows "Antenna: Diamond Antenna (×3 reach)" (also on a mast below), the reach ring and the readout show the new reach, and the remote, scanner, meter and every receiver go by it.
- On someone else's private frequency only its owner and members can fit or take off an antenna (others get a red refusal and the owner an alert, as when tuning).
- Breaking the tower keeps its antenna on the item ("Antenna: Diamond Antenna (×3 reach)" in its tooltip), and placing it puts it back. Compact transmitters and motion sensors don't take antennas.
- The multipliers are in the server config: `ironAntennaReach`, `goldAntennaReach`, `diamondAntennaReach`, `netheriteAntennaReach`.
- A **Dish Antenna** (since 0.48) fits the same towers but points: three times the reach in one direction only (see Dish Antenna).

### Radio Lamps

A **Radio Lamp** (since 0.45) is a lamp with a receiver built in. Tune it like any receiver:

- **Light:** it gives off as much light as the strength it hears on its frequency, so 15 is as bright as a glowstone and 6 a soft glow. A transmitter fed a weaker signal (through a line of redstone dust, or from a comparator) gives dimmer light.
- **Color:** its glass glows in one of the 16 dye colors, darker while it's off and brighter the stronger the signal. Right-click it with a dye to change the color (one dye, none in creative). New lamps are white.
- **Only a light:** it outputs no redstone, so it doesn't switch on what's next to it. It counts as a receiver ("Heard by", the remote's light, the locator, the audit as "Radio Lamp"), and on someone else's encrypted frequency it stays dark.
- Jade shows its light and color ("Light: 6", "Color: Red"). Breaking it keeps its frequency and color on the item, and the item shows its color.

![Transmitters with no antenna, then iron, gold, diamond and netherite; Dev fitting a Diamond Antenna to the beacon tower (reach 216 → 648 blocks, Jade and the reach ring agree); Alex refused on Dev's private repeater, then fitting a Gold Antenna to the recorder; colored lamps at full strength in front and at 6 behind, with Jade saying "Light: 6"; and Alex dyeing a white lamp purple](docs/images/antennas-and-radio-lamp.png)

### Weather and terrain

Since 0.46 the world gets in the way of signals:

- **Rain and thunderstorms** shorten the reach of transmitters (plain, timer and recorder), repeaters, converters and jammers: by 15% while it rains or snows where the tower stands, by 30% in a thunderstorm. A tower in a desert, savanna or badlands (where it never rains), or in the Nether or the End, isn't affected. Compact transmitters, motion sensors, keypads and remotes don't mind the weather.
- When the weather changes, everything on every frequency is worked out again at once: a receiver at the edge of a tower's reach can go dark when a storm rolls in, and come back when it clears. Jade shows the tower's reach in the weather now, with a line saying why ("Thunderstorm: reach -30%"), and the reach ring and the meter's distance to the edge move in too.
- **Hills** between a tower and what hears it weaken the signal. The game follows a straight line from the top of the tower to the receiver (or the pager, meter or scanner in your hand), and every 8 blocks of solid ground or water that line runs through takes 1 off the strength, so 32 blocks of hill turn 15 into 11 and 120 blocks block it completely. Leaves, caves and roofs above the line don't count, and neither do the first and last 8 blocks (the tower's own base, or the walls round your receiver). A taller tower, or a repeater on top of the hill, gets the signal over it.
- Only loaded chunks count for hills (the game can't look inside unloaded ones, so they count as clear). Remote presses don't mind hills.
- Jade shows what hills take off a receiver's signal ("Hills in the way: -4"), and the Signal Meter adds a line ("Hills -4").
- The numbers are in the server config: `rainReachLoss` and `thunderReachLoss` (percent; 0 = off) and `terrainBlocksPerStrength` (0 = hills don't matter).

### Receiver height

Since 0.47 a receiver standing **higher than a tower** hears it from farther: as far as a transmitter would reach where the receiver stands. A Radio Receiver on top of a 12-mast pillar, 15 blocks above sea level, hears towers below it up to 152 blocks away (32 + 8 × 15), even a transmitter on the ground whose own reach is 56.

- It works for everything that listens: receivers of every kind, lamps, speakers, status boards, repeaters and converters (so a tall repeater picks up low towers from farther), and the pager, meter and scanner in your hand.
- Only when the receiver is the higher one. A receiver lower than the tower, or at the same height, hears it within the tower's own reach as before. Compact transmitters and motion sensors keep their short reach, and remote presses and jammers aren't affected.
- Weather still applies (where the tower stands), and so do hills.
- Jade says when a signal is heard thanks to the height: "Up high: hears lower towers up to 152 blocks away". The meter's distance to the edge counts it too.
- `receiverHeightPercent` in the server config (100 by default) is how much of a transmitter's reach a high receiver gets; 0 switches it off.

### Lightning

Since 0.47 **lightning strikes tall towers** during thunderstorms:

- Each tower open to the sky (a full-size transmitter, timer, recorder, repeater, converter or jammer) has a chance of being hit while a storm rages where it stands: about 6 times an hour at 64 blocks above sea level, half as often at 32 (`lightningStrikesPerHour`). Compact transmitters and motion sensors are never hit.
- **A struck tower goes quiet** for 5 seconds (`lightningOutageSeconds`): it sends, relays and jams nothing, its light goes out, sparks crackle round its antenna, and its receivers go off. Then it comes back by itself. Jade shows "Struck by lightning: back in 3.5s".
- **A Lightning Rod** within 2 blocks of the tower's top (on its side, on top of it, or on a mast or post beside it) takes the hit instead: the rod gives its redstone pulse and the tower stays on.
- The mod's own bolts don't start fires or hurt anyone. A bolt that lands on a tower some other way (vanilla lightning, a Trident with Channeling, `/summon lightning_bolt`) knocks it out too, unless a rod protects it.

![A lime Radio Lamp on top of a 12-mast pillar, lit, and one on the ground at its foot, dark: both on a transmitter 80 blocks away whose reach is 56; Jade on the high lamp saying "Up high: hears lower towers up to 152 blocks away" (the meter: 15, 70 blocks to the edge); lightning striking Dev's tower in a thunderstorm ("Sending: nothing", "Struck by lightning: back in 4.7s"); sparks round the dark tower ("back in 3.5s"); and the same tower with a Lightning Rod on its side, still "Sending: 15" after the next strike](docs/images/receiver-height-and-lightning.png)

### Signal fade

Since 0.48 a signal **fades toward the edge** of a tower's reach. Out to half its reach it's heard at full strength; from there it gets weaker and weaker, down to 1 at the very edge (it never fades to nothing: inside the reach it's always heard). A transmitter sending 15 with a reach of 56 is heard at 15 up to 28 blocks away, 13 at 32, 9 at 40, 5 at 48 and 1 at 56. A row of Radio Lamps shows it at night: their light shrinks toward the edge.

- **Repeaters and converters hear it unfaded** and send it on at full strength, so a chain of repeaters gets 15 to the far end. Put the next repeater wherever you like within reach; only the receivers at the end hear the fade.
- It applies to everything that listens: receivers of every kind, lamps, speakers, status boards, and the pager, meter and scanner in your hand. A jammer's jamming fades the same way. Compact transmitters, motion sensors, keypads and remote presses don't fade (their reach is short anyway).
- It comes off first, then hills, then jamming.
- Jade shows how much a receiver's signal faded ("Faded: -10 (far out in its tower's reach)"), and the Signal Meter adds it to its bottom line ("Fade -11").
- The server config sets where it starts and how much it takes: `signalFadeStartPercent` (50: half the reach; 100 switches fading off) and `signalFadeAtEdge` (14: 15 becomes 1 at the edge; 0 switches it off).

### Dish Antenna

A **Dish Antenna** (since 0.48) is an antenna that points. Right-click a full-size Radio Transmitter, Timer Transmitter, Signal Recorder, Repeater, Converter or Jammer with it, and the dish faces the way you're looking (one of 16 directions: north, north-north-east, north-east and so on):

- **In its beam,** 15° either side of where it points, the tower reaches **three times** as far as with no antenna. Everywhere else it reaches as far as with no antenna. A transmitter with a reach of 56 then reaches 168 blocks east and 56 every other way ("Radio Transmitter: Dish Antenna aimed east: 168 blocks that way, 56 elsewhere"). The server config's `maxReach` still caps it.
- **To turn it,** right-click the tower with another Dish Antenna while looking the new way: the dish turns (nothing is used up). Fitting a different antenna replaces it and gives the dish back; sneak + right-click with an empty hand takes it off.
- Everything goes by the beam: receivers, repeaters (a dish on a repeater sends the relayed signal far in one direction), a jammer's jamming, remote presses the tower relays, the meter's distance to the edge, and weather and fade (which work on the longer reach).
- **You can see it:** a grey dish on the tower's mast, facing its way. Jade shows "Antenna: Dish Antenna, aimed east (×3 reach that way)" and "Reaches 168 blocks to the east" (also on a mast below, without the second line), and the reach ring becomes a keyhole: the circle, with the beam sticking out of it.
- A tower placed from an item that carries a dish faces the way you look when you place it.
- The server config has `dishReachMultiplier` (3) and `dishConeDegrees` (15 either side).

![A row of Radio Lamps at night on a transmitter whose reach is 56, their light shrinking toward the edge of its reach; Jade on the lamp 48 blocks out saying "Light: 5" and "Faded: -10 (far out in its tower's reach)", with the meter reading 4, "Fade -11"; a transmitter with a Dish Antenna, Jade saying "Antenna: Dish Antenna, aimed east (×3 reach that way)" and "Reaches 168 blocks to the east", and the reach ring "56 blocks (168 to the east)"; and its reach ring from above: a keyhole, the circle with the beam reaching out east](docs/images/signal-fade-and-dish-antenna.png)

### Power cost

Since 0.49 **towers need power to send.** A full-size transmitter (plain, timer or recorder), repeater, converter or jammer uses charge from a **Radio Battery** next to it, or next to any mast in the column under it, while it sends. Without a battery, or with only empty ones, it's **out of power**: it sends, relays and jams nothing, and its light stays off, until a battery next to it has charge again.

- **What it costs:** 1 charge a second for every 64 blocks of reach, only while it's sending (a transmitter with redstone, a repeater or converter hearing something, a jammer with redstone). A transmitter reaching 56 blocks uses 0.88 a second, so a full battery (100,000) lasts it over a day of sending; one with a Netherite Antenna reaching 2,080 blocks uses 32.5 a second. A Dish Antenna adds nothing to the cost.
- **Charging a battery:** right-click it with Redstone Dust (1,000 charge each) or Blocks of Redstone (9,000); it takes as many as fit. Hoppers and item pipes can feed it redstone, and FE cables from other mods charge it too (10 FE a charge). It holds 100,000.
- **Free:** compact transmitters, motion sensors, keypads, remotes, and everything that only listens.
- Several batteries round one tower add up (it takes from them in turn), and one battery can feed several towers. A tower only uses charge while its chunk is loaded; an unloaded one stays as it was.
- **You can see it:** the battery's four bars light up as it fills, and a comparator reads it (0 empty, 15 full). An empty-hand right-click says how full it is. Jade shows the battery's charge and what towers are using ("Powering towers: 0.88 a second (about 20h 19m left)"), and on a tower either "Battery: 64% (about 20h 18m left at 0.88 a second)" or "Out of power: needs a charged Radio Battery next to it or its mast". A battery keeps its charge when you break it.
- **Solar Panels** (since 0.50) put a battery on sunlight: see Solar Panel. A transmitter with an Ender Antenna costs twice as much (it sends into two dimensions).
- You can place blocks on a battery (a Solar Panel, say); only an empty hand gets its readout.
- **Upgrading an old world:** existing towers go quiet until they get a battery. To play without power cost, set `towersNeedPower` to false in the server config. The config also has `chargePerSecondPer64Blocks` (1), `batteryCapacity` (100,000), `chargePerRedstone` (1,000) and `fePerCharge` (10).

### Coverage Map

A **Coverage Map** (since 0.49) is a map for one frequency. Sneak + right-click a radio block to tune it to that block's frequency (a converter's output), then right-click the air to draw the map where you stand. While you hold it, it paints where the frequency can be heard, a few rows at a time (the whole map in under two seconds):

- **Green** where the signal is strong (13–15), **light green** (10–12), **yellow** (7–9), **orange** (4–6) and **red** (1–3) as it gets weaker toward the edge (see Signal fade), behind hills or near a jammer, and **blank** where it isn't heard at all.
- It goes by the same rules as a receiver on the ground at each spot: towers out of power or struck by lightning are silent, the weather shortens reach, a dish reaches far one way, repeaters carry it on.
- **Markers:** a red one on each transmitter sending on the frequency, a blue one on each repeater or converter, a red X on each jammer, and the white arrow for you.
- **Zoom:** sneak + right-click the air to step it out: 1:4 (512 blocks across) to start, then 1:8, 1:16, 1:1 and 1:2. Maps line up like vanilla maps of the same zoom, so they tile on a wall of item frames; drawing it in a new square (or at a new zoom) makes a new map.
- It's a real map: it shows in your hand and in item frames like any other (a framed one shows how it was last painted). On someone else's encrypted frequency it stays blank.

### Ender Antenna

An **Ender Antenna** (since 0.50) lets a Radio Transmitter (plain, timer or recorder) send into **another dimension** as well as its own. Right-click the transmitter with it:

- It sends from **the same spot** in the other dimension: the x and z scaled the way portals scale them, so a transmitter at 620, 90 in the Overworld arrives at 77, 11 in the Nether (1/8), and one in the Nether arrives 8 times farther out in the Overworld. The End and other dimensions are 1:1. The height is kept (inside the other dimension's build limits).
- Over there it's heard as if a transmitter stood at that spot: the same frequency and strength, the tower's own reach (in the weather where the tower stands), fading toward the edge. Hills don't block it. Receivers, lamps, repeaters (which carry it on), meters, scanners and Coverage Maps there all hear it, and it changes the moment the tower does.
- **Which dimension:** to start with it's the Nether from the Overworld, and the Overworld from anywhere else. Right-click the transmitter with another Ender Antenna to move it on to the next dimension (nothing is used up). The message and Jade say where it goes: "Radio Transmitter: Ender Antenna fitted: sending into the Nether too (arriving at 77, 0, 11)", "Antenna: Ender Antenna (also sends into the Nether at 77, 0, 11)".
- Only transmitters can take one (not repeaters, converters or jammers, so nothing can loop between dimensions). It replaces any other antenna (and gives it back); its reach stays as with no antenna. With power cost on, the tower uses twice as much charge.
- The tower keeps sending into the other dimension while its own chunk isn't loaded, like any tower; take the antenna off or break the tower and it stops.

### Solar Panel

A **Solar Panel** (since 0.50) is a flat panel you put on top of a Radio Battery. Once a second it charges the battery under it by how much sunlight it gets, measured like a Daylight Detector: up to **3 charge a second** at noon in clear weather (`solarChargePerSecond` in the server config), less in the morning and evening and in rain or thunder, nothing at night, under a roof or in the Nether and the End. Over a whole day that's about what a transmitter with a reach of 56 uses, so one panel keeps a small tower on the air for free.

- Jade on the panel: "Charging the battery under it: 3 a second" and "Sunlight: 15 of 15" (or "Not charging: too dark (night, or something over it)", "No Radio Battery under it"). On the battery: "Solar Panel: +3 a second", and the time left takes it into account ("the Solar Panel keeps up" when it does).
- One panel per battery; a battery fed by a panel can still be charged with redstone or FE as well.

![Dev fitting an Ender Antenna to the transmitter on 4480 in the Overworld: "Ender Antenna fitted: sending into the Nether too (arriving at 77, 0, 11)", Jade "Antenna: Ender Antenna (also sends into the Nether at 77, 0, 11)" and "Battery: 64% (about 10h 8m left at 1.75 a second)"; Alex in the Nether next to a purple Radio Lamp on 4480, Jade "Light: 15", "Towers in range: 1", a receiver lighting a redstone lamp beside it, and the meter "15", "1 tower, 55 blocks to the edge"; a Solar Panel on the transmitter's battery, Jade on the battery "Solar Panel: +3 a second", "Powering towers: 1.75 a second"; and Jade on the panel "Charging the battery under it: 3 a second", "Sunlight: 15 of 15"](docs/images/ender-antenna-and-solar-panel.png)

![Dev looking at a Radio Transmitter on 4480 at night: Jade saying "Sending: nothing" and "Out of power: needs a charged Radio Battery next to it or its mast"; Dev right-clicking the Radio Battery next to it with a stack of redstone: "Radio Battery: +64,000 charge (64,000 / 100,000)", three bars lit, Jade "Charge: 63,999 / 100,000 (64%)", "Powering towers: 0.88 a second (about 20h 19m left)"; the tower sending again, Jade "Sending: 15", "Battery: 64% (about 20h 18m left at 0.88 a second)"; and Alex holding a Coverage Map of 4460 at 1:2: a green disc round the transmitter's red marker, fading through yellow and orange to red at the edge, a blue marker on the repeater and a red X on the jammer](docs/images/power-cost-and-coverage-map.png)

### Mast builder

Since 0.51, a **Radio Mast** in your hand raises a tower: right-click any mast of the tower (or its top) and a new mast goes in **under the top**, lifting the top one block. So you can build a tall tower standing on the ground, one click per mast.

- The top keeps everything: its frequency, its antenna (and where a dish points, or where an Ender Antenna sends), its schedule or recording, who tuned it. An Aviation Beacon standing on it or a Lightning Rod on it goes up with it.
- The message says how far up it is and how far it reaches now: "Mast added: the Radio Transmitter is 3 masts up, reach 72 → 80 blocks".
- A mast column with nothing on top grows from the top instead ("Mast added on top: 5 masts tall").
- It needs room: if something solid is in the way above, it says what and where. It can't go past the top of the world.
- On someone else's private frequency, only its owner and members can raise the tower (like fitting an antenna).
- Sneak + right-click to place a mast the normal way, next to the one you click.

### Radio Telegraph

A **Radio Telegraph** (since 0.51) is a desk for sending short text messages over the radio. Right-click it to open its screen: its frequency (with "..." for the directory), how far it reaches, the last 6 messages that reached it, and a box to type one (up to 64 characters). **Send** or Enter sends it (changing the frequency box re-tunes it first).

- A message goes out like a Remote Control press from where the telegraph stands: its reach by its height, plus every tower within that reach passing it on with its own reach, plus repeaters on the frequency.
- **Who reads it** (on the same frequency, where it reaches):
  - **Pagers** anywhere in a player's inventory: a line in chat and above the hotbar, "Radio Pager: message on 4600 from Dev: “Front gate is open, come on over”", and two beeps (unless the pager is silent). A pager whose owner was offline says at the next login what it missed, the last message in full.
  - **Other telegraphs:** it shows in their screens (also ones that weren't loaded at the time), and a loaded one clicks three times, lights its lamp and gives a **1-second redstone pulse** (strongly powering the block under it), so it can ring a bell or light a lamp.
  - **Status Boards** showing that frequency: the last message, small and yellow under its row, for 5 minutes.
- The sender sees who got it: "Sent on 4600 (reach 56 blocks + 2 towers) | Read by 1 pager and 1 telegraph".
- Messages don't turn the frequency on (receivers don't react). One telegraph can send one every 2 seconds. Each dimension keeps the last 16 per frequency.
- **Private frequencies:** only the owner and members can send (others are refused, and the owner is told). On an encrypted one only members' pagers and telegraphs (and boards set by members) can read the text; others see that a message came, but "(encrypted)". The frequency log lists messages with who sent them, where from, and the text.
- Jade: "Last message (just now): “Lights on in the hall too” from Dev", "Messages here: 2", "A message just arrived" while its lamp is lit.

![Dev raising the tower on 4601 by right-clicking its bottom mast with a Radio Mast: "Mast added: the Radio Transmitter is 3 masts up, reach 72 → 80 blocks", Jade on the mast "Top: Radio Transmitter, 3 blocks up"; the raised tower with its Aviation Beacon still on top, Jade "Reach: 80 blocks", "Battery: 80% (about 17h 45m left at 1.25 a second)"; Dev's Radio Telegraph screen on 4600 with two messages and "Sent on 4600 (reach 56 blocks + 2 towers) | Read by 1 pager and 1 telegraph"; Alex 40 blocks away by his own telegraph, its lamp lit, Jade "Last message (just now): “Lights on in the hall too” from Dev", "A message just arrived", and his pager's line in chat; the Status Board with the message under row 4600; and Alex logging back in to "Radio Pager: while you were away, a message on 4600 from Dev (Day 1, 12:00): “Back in 5 minutes”"](docs/images/mast-builder-and-telegraph.png)

### Radio Jammers

A **Radio Jammer** (since 0.46) is built like a transmitter: it goes on top of a mast, the tuner sets its frequency, and redstone reaching it (or any mast under it) switches it on. It sends nothing itself. Instead:

- **It drowns out its frequency** inside its reach: everything that listens on it there (receivers of every kind, lamps, speakers, status boards, repeaters and converters, pagers, meters, scanners) hears that much less. The strength of the redstone it gets is how strong it jams, so 15 silences the frequency and 5 takes 5 off a 12. A repeater inside it has nothing to pass on, so a jammer can cut a chain.
- **Its reach** is half what a transmitter at the same height would have (with the same antenna), shown by the reach ring in magenta. Rain and hills weaken jamming the same way they weaken signals.
- **On someone else's private frequency** only its owner and members can tune a jammer to it or place one that works. Anyone else is refused ("Can't do that: it would jam 2026 (Front Gate), which is private to Dev"), a jammer of theirs placed already tuned stays silent, and the owner gets an alert. Jammers show up in the frequency audit as "Radio Jammer".
- Jade shows what it jams, how strongly ("Jamming: -15") and how far, and on a jammed receiver "Jammed: -15". The Signal Meter says "Jammed" instead of the strength. Its sides glow magenta and static crackles round its antenna while it jams.
- The share of a transmitter's reach is `jammerReachPercent` in the server config (50 by default).

![A receiver and a Radio Lamp behind a hill with a repeater on top, and Alex by a jammer; Jade on the receiver saying "Hills in the way: -4" (output 11, the meter "Hills -4"); the tower in a thunderstorm ("Reach: 62 blocks", "Thunderstorm: reach -30%"); Alex at the jammer ("Jamming: -15", its magenta reach ring) refused when tuning it to Dev's private frequency; and Dev's receiver "Jammed: -15", with the meter saying "Jammed" and Dev's radio alert](docs/images/weather-terrain-and-jammer.png)

### Code Keypad

A **Code Keypad** is a thin panel you put on a wall, floor or ceiling. Type its code and it sends a pulse on its frequency, from where it is, like a remote pressed there (towers in its reach relay it). For as long as the pulse lasts, its display lights green and, like a button, it powers the block it's on, so an iron door next to that block opens without any radio at all.

- **Setting it up:** place it, tune it with the Frequency Tuner like any radio block (or place one that already has a frequency), and right-click it. You placed it, so you own it: type 4 to 8 digits and click **Set code** ("New code saved (4 digits)"). **Pulse** sets how long the pulse lasts, from 0.1 to 10 seconds (sneak-click for shorter).
- **Using it:** anyone right-clicks it (empty hand or holding anything but the tuner) and types the code with the keys or the keyboard, then **OK** or Enter. The right code closes the screen: "Code accepted: 1s pulse on 2026 (Front Gate) | Heard by 3 receivers".
- **Wrong codes:** "Wrong code (1 of 3 tries)". Three in a row lock that player out for 30 seconds ("LOCKED"), and you're told in chat: "Radio alert: Alex typed the wrong code 3 times on your Code Keypad at 406, -59, -9, on 2026 (Front Gate): locked out for 30 seconds". If you're offline, it's in the summary you get when you log in. Your own mistakes don't alert you.
- **The code stays secret:** it's never sent to anyone's game, the display only shows stars, and the message after setting it only says how many digits. Breaking the keypad keeps its frequency on the item but forgets the code and the owner: whoever places it next sets a new one.
- **Only the owner changes it:** only you (or a server operator) can set the code and pulse, or re-tune it ("This Code Keypad is Dev's: only they can tune it").
- **Private frequencies:** like a transmitter, it only sends on someone else's private frequency if you're a member. Otherwise it stays silent, you're told when you place it, and the frequency's owner gets an intruder alert. Anyone who knows the code can use your keypad, though, even if they aren't on the frequency themselves: that's what the code is for.
- **The log** lists its turn-ons as **Code Keypad**, with who typed the code and where the keypad is (click the row for a beam over it).
- **Jade** shows its frequency, owner, whether a code is set (never the code itself), "Waiting for the code" or "Code accepted: on", and its reach.
- **Guest codes (since 0.38):** the owner's keypad screen has a **Guest codes...** button for extra codes to hand out, up to 8 per keypad. Type a name (who it's for, like "Plumber") and a 4 to 8 digit code, pick **Uses** (1, 3, 10 or any) and **Time** (1 hour, 1 day, 1 week or any), then **Add**.
  - The list shows each one's code, uses left and time left (time runs in real time, so it keeps counting while the server is off). Hover a row to see how often it was used and by whom; click it, then **Remove** to end it.
  - A guest code works just like the keypad's own code ("Guest code "Plumber" accepted: 1s pulse on 2026 (Front Gate)") until its uses or time run out. After that it says "That guest code is used up" or "has expired", without counting as a wrong code.
  - If you're online, you're told each time one is used: "Code Keypad at 406, -59, -9: Alex used the guest code "Plumber" (2 uses left)". The frequency log shows "Guest code: Plumber" with who typed it.
  - A guest code can't be the keypad's own code or another guest code, and names must be different.

### Compact Radio Transmitter

A **Compact Radio Transmitter** is a button-sized transmitter. It sticks to walls, floors and ceilings like a button (and pops off, keeping its frequency, if that block goes).

- **Input:** it broadcasts the strongest redstone signal around it: a lever or button next to it, redstone dust running to it, or a block it's next to that's strongly powered. It doesn't read a mast column.
- **Reach:** a short fixed reach, 16 blocks by default (`compactTransmitterReach` in the server config), whatever its height. That's enough for a base, and it keeps your signal from spreading over the whole area.
- **Not a tower:** it doesn't relay remote presses the way a full transmitter does.
- Otherwise it's a transmitter: tune it with the tuner, its red light glows while it's sending, it works on private frequencies like any sender, and Jade, the readout ("Compact Transmitter | Frequency 55 | Sending 15 | Reach 16 blocks"), the audit, the scanner and the reach ring all know it.

### Radio Motion Sensor

A **Radio Motion Sensor** (since 0.39) is a button-sized eye that sticks to walls, floors and ceilings like a button (and pops off, keeping its frequency, if that block goes). While it sees a player nearby, it broadcasts full strength on its frequency, like a Compact Radio Transmitter with someone holding the lever.

- **What it sees:** players within its range (measured to the middle of their body) with a clear line from its face to their eyes or body. A block in between hides them, as it would from a mob (glass too). Spectators are never seen. It looks twice a second.
- **How long it sends:** for as long as it sees someone, plus 2 seconds after it last saw them, so a player walking past doesn't make it flicker.
- **Reach:** the compact transmitter's short fixed reach, 16 blocks by default (`compactTransmitterReach`), whatever its height. It isn't a relay tower, and the redstone around it does nothing.
- **Settings (its owner):** whoever places it owns it. Right-click it with an empty hand to open its screen: **Range** cycles 2, 4, 6, 8, 12 or 16 blocks (6 by default; Shift-click or scroll goes back), and **Reacts to** picks **Everyone** or **Strangers only**. The screen also says whether it sees someone right now, and who. **Done** saves ("Motion sensor set: looks 6 blocks, reacts to strangers only").
- **Strangers only** ignores you and, on a private frequency, its owner and members (friends, group members and guests). Everyone else sets it off. Put it on your private frequency and add your friends, and it only goes off for people who aren't on your list.
- **Anyone else** who right-clicks it just gets a readout: "Motion Sensor | Frequency 4242 (Vault) | Owner: Dev | Reach 16 blocks". Its range and mode stay the owner's business, in Jade too. Only the owner (or an operator) changes its settings or re-tunes it ("This motion sensor is Dev's: only they can re-tune it").
- **The log** lists its turn-ons as **Motion sensor**, with the first player it saw and where the sensor is (click the row for a beam over it). On a private frequency only a member's sensor sends, like any sender.
- **Jade** shows its owner, "Sees nobody" or "Sees someone: sending", and its reach. Its owner also sees its settings and who it sees ("Sees Alex: sending").
- **Pair it with an inverted receiver** for a light or door that's on until someone comes, or with a normal receiver for an alarm lamp, a bell or a pager.

### Reach preview ring

Hold a Frequency Tuner (either hand) and look at a Radio Transmitter (full-size or compact), Repeater, Converter, Radio Jammer, Code Keypad or Radio Motion Sensor, or at a mast under one, from up to 128 blocks away. A dotted ring appears on the ground where its reach ends, in the color of its light (red, amber, teal, magenta for a jammer, green for a keypad, or purple for a motion sensor), and two lines above your hotbar say what it is and where you stand: "Reach ring: Radio Transmitter on 2026 (Front Gate), 56 blocks" and "You're inside: 54 blocks to the edge" (or "You're 7 blocks outside").

- The ring stays on that tower while you keep holding the tuner, so you can walk out to the edge to see where to put the next repeater or receiver. Look at another tower to move it; put the tuner away to clear it.
- The dots follow the ground, and far ones are drawn bigger so the ring still shows from a hilltop. Only you see it, and only the part within your render distance.
- The reach comes from the server, so it's always the real one (the dimension's sea level, the server config and the weather).
- A tower with a Dish Antenna (since 0.48) gets a keyhole: the circle, with its beam reaching out the way the dish points. The first line adds how far that is ("56 blocks (168 to the east)"), and "You're inside" counts to whichever edge is yours.

### Reach

Reach is measured horizontally from the transmitter (or repeater), so a receiver works at any height inside that circle, and one higher than the tower hears it even farther (see Receiver height).

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

An antenna multiplies a tower's reach (see Antennas; a Dish Antenna only in one direction), rain shortens it (see Weather and terrain), and the signal fades toward the edge of it (see Signal fade). A tower needs a charged Radio Battery to send at all (see Power cost). All three numbers (base reach, reach per block of height, and an optional maximum) are in the server config: `<world>/serverconfig/reikos_signal_tower-server.toml`, or Mods → Reiko's Redstone Signal Tower → Config. The same file has `towersRelayRemotes` (on by default), which lets transmitters and repeaters relay remote presses on any frequency, and `privateFrequenciesPerPlayer` (32 by default; 0 means nobody can make a frequency private).

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

**Radio Inverted Receiver:** a Radio Receiver and a Redstone Torch, in any arrangement in the crafting grid. Put an inverted receiver in the grid on its own to get the Radio Receiver back.

**Radio Logic Receiver:** two Radio Receivers and a Nether Quartz, in any arrangement in the crafting grid.

**Radio Speaker:** a Radio Receiver and a Note Block, in any arrangement in the crafting grid. Put a speaker in the grid on its own to get the Radio Receiver back.

**Radio Status Board:** a Radio Receiver, a Redstone Lamp and a Glass Pane, in any arrangement in the crafting grid.

**Radio Lamp:** a Radio Receiver and a Redstone Lamp, in any arrangement in the crafting grid.

**Iron Antenna:**

```
     .  I  .
     I  I  I
     .  R  .

I = Iron Ingot   R = Redstone Dust
```

**Gold Antenna:** an Iron Antenna, 2 Gold Ingots and a Redstone Dust, in any arrangement. **Diamond Antenna:** a Gold Antenna and 2 Diamonds. **Netherite Antenna:** a Diamond Antenna and a Netherite Ingot.

**Dish Antenna:**

```
     I  .  I
     .  I  .
     .  A  .

I = Iron Ingot   A = Iron Antenna
```

**Radio Battery:**

```
     I  C  I
     C  B  C
     I  C  I

I = Iron Ingot   C = Copper Ingot   B = Block of Redstone
```

**Coverage Map:** a Map (the empty one) and a Compact Radio Receiver, in any arrangement in the crafting grid.

**Ender Antenna:** an Iron Antenna, an Eye of Ender and an Obsidian, in any arrangement in the crafting grid.

**Solar Panel:**

```
     G  G  G
     C  D  C

G = Glass Pane   C = Copper Ingot   D = Daylight Detector
```

**Radio Telegraph:** a Radio Transmitter, a Radio Receiver and a Book and Quill, in any arrangement in the crafting grid.

**Radio Signal Recorder:** a Radio Transmitter, a Radio Receiver and a Book, in any arrangement in the crafting grid.

**Radio Jammer:** a Radio Transmitter, a Note Block and a Redstone Dust, in any arrangement in the crafting grid.

**Aviation Beacon** (makes 2):

```
     .  G  .
     R  L  R
     .  I  .

G = Red Stained Glass   R = Redstone Dust   L = Redstone Lamp   I = Iron Ingot
```

**Radio Timer Transmitter:** a Radio Transmitter and a Clock, in any arrangement in the crafting grid. Put a timer transmitter in the grid on its own to get the Radio Transmitter back.

**Radio Counter Receiver:** a Radio Receiver and a Hopper, in any arrangement in the crafting grid. Put a counter receiver in the grid on its own to get the Radio Receiver back.

**Radio Delay Receiver:** a Radio Receiver and a Redstone Repeater, in any arrangement in the crafting grid. Put a delay receiver in the grid on its own to get the Radio Receiver back.

**Compact Radio Receiver:** put one Radio Receiver in the crafting grid to get a Compact Radio Receiver. It works the other way too, so you can switch back any time.

**Compact Radio Transmitter:** put one Radio Transmitter in the crafting grid to get a Compact Radio Transmitter, and back again the same way.

**Radio Motion Sensor:** a Compact Radio Transmitter and an Observer, in any arrangement in the crafting grid.

**Remote Control:**

```
  .  T  .        T = Redstone Torch   B = Stone Button
  I  B  I        I = Iron Ingot       E = Ender Pearl
  I  E  I
```

**Multi-Button Remote:** a Remote Control plus 3 Stone Buttons, in any arrangement in the crafting grid.

**Code Keypad:** a Remote Control, an Iron Ingot and a Stone Button, in any arrangement in the crafting grid.

**Radio Pager:** a Compact Radio Receiver, a Note Block and an Iron Ingot, in any arrangement in the crafting grid.

**Signal Scanner:** a Radio Receiver, a Compass and an Iron Ingot, in any arrangement in the crafting grid.

**Signal Meter:** a Compact Radio Receiver, a Redstone Comparator and an Iron Ingot, in any arrangement in the crafting grid.

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
  Config.java                            server config (reach settings, tower relay switch, private frequency limit)
  registry/ModRegistry.java              blocks, items, block entities, the frequency data component
  block/RadioBlock.java                  shared transmitter/receiver code: status readout, tooltips
  block/RadioTransmitterBlock.java       reads input, broadcasts
  block/RadioReceiverBlock.java          redstone output
  block/RadioToggleReceiverBlock.java    receiver that flips on and off with each turn-on
  block/RadioInvertedReceiverBlock.java  receiver that's on while its frequency is silent
  block/RadioLogicReceiverBlock.java     receiver on two frequencies (AND / OR / XOR); opens its screen
  client/LogicScreen.java                the logic receiver's screen
  network/OpenLogicScreenPayload.java, ConfigureLogicPayload.java  logic receiver screen <-> server
  block/RadioDelayReceiverBlock.java     receiver with a delay and a hold; opens its screen
  client/DelayScreen.java                the delay receiver's screen
  network/OpenDelayScreenPayload.java, ConfigureDelayPayload.java  delay receiver screen <-> server
  block/RadioTimerTransmitterBlock.java  transmitter on a schedule; opens its screen
  blockentity/TimerTransmitterBlockEntity.java  the timer's clock: interval or daily, pause, labels
  client/TimerScreen.java                the timer transmitter's screen
  network/OpenTimerScreenPayload.java, ConfigureTimerPayload.java  timer screen <-> server
  radio/AntennaTier.java                 the antenna tiers (block state "antenna"), multipliers, item tooltips
  item/AntennaItem.java                  antenna items: fit, replace, take off (sneak + empty hand)
  block/RadioLampBlock.java              receiver that's a light: light level = strength, dye colors, no redstone
  block/RadioJammerBlock.java            tower top that drowns out its frequency around it while powered
  blockentity/RadioJammerBlockEntity.java  tells the network how strongly and how far it jams
  radio/Weather.java                     clear, rain, thunder: how much reach each takes off (config)
  radio/Lightning.java                   lightning strikes on towers: chance, outages, lightning rods
  radio/PowerCost.java                   power cost: finding a tower's batteries, paying each second, out of power
  block/RadioBatteryBlock.java           the battery: charging with redstone, its bars, the comparator
  blockentity/RadioBatteryBlockEntity.java  its charge, hoppers and FE, the item keeping its charge
  item/CoverageMapItem.java              a map that paints where a frequency is heard (colors, zoom, markers)
  radio/CrossDimension.java              Ender Antenna links: where a tower arrives in another dimension, which one
  block/SolarPanelBlock.java             the flat panel on a battery
  blockentity/SolarPanelBlockEntity.java  sunlight (like a Daylight Detector) into charge for the battery under it
  radio/MastBuilder.java                 a mast used on a tower goes in under its top (lifting the top and what's on it)
  radio/Telegraph.java                   telegraph messages: cleaning, where they go, who reads them, the screen's lines
  block/RadioTelegraphBlock.java         the telegraph desk: opens its screen, pulses when a message arrives
  blockentity/TelegraphBlockEntity.java  checks in with the network; clicks and pulses; sending cooldown
  client/TelegraphScreen.java            the telegraph's screen (frequency, messages, typing)
  network/TelegraphFeedPayload.java, TelegraphActionPayload.java  telegraph screen <-> server
  block/RadioSignalRecorderBlock.java    transmitter that records a frequency and plays it back; opens its screen
  blockentity/SignalRecorderBlockEntity.java  the recorder's tape: record, stop, play, loop, labels
  radio/RecorderTape.java                what the recorder's item keeps (frequencies, mode, recording)
  client/RecorderScreen.java             the recorder's screen (with the recording drawn as a strip)
  network/OpenRecorderScreenPayload.java, ConfigureRecorderPayload.java, RecorderStatus.java, RecorderStatusPayload.java  recorder screen <-> server
  block/AviationBeaconBlock.java         warning light on a tower, mast or block; settings in its block state
  blockentity/AviationBeaconBlockEntity.java  lets the client draw the light (and remembers whether it's on)
  radio/AviationLight.java               when it's lit, blink patterns, colors, finding its tower
  client/AviationBeaconRenderer.java     draws the lit dome and the glow that shows from far away
  client/AviationBeaconScreen.java       the beacon's screen
  network/ConfigureBeaconPayload.java    beacon screen -> server
  block/RadioSpeakerBlock.java           receiver that plays a sound; opens its screen
  radio/SpeakerSound.java                the speaker's sounds, notes and volumes
  client/SpeakerScreen.java              the speaker's screen (with Test)
  network/OpenSpeakerScreenPayload.java, ConfigureSpeakerPayload.java  speaker screen <-> server
  block/StatusBoardBlock.java            wall panel showing up to four frequencies
  blockentity/StatusBoardBlockEntity.java  the board's rows and what's heard there, synced to clients
  client/StatusBoardRenderer.java        draws the rows on the board's face
  client/BoardScreen.java                the board's screen
  network/OpenBoardScreenPayload.java, ConfigureBoardPayload.java  board screen <-> server
  block/RadioCounterReceiverBlock.java   receiver that counts turn-ons; opens its screen
  client/CounterScreen.java              the counter receiver's screen
  network/OpenCounterScreenPayload.java, ConfigureCounterPayload.java  counter screen <-> server
  item/SignalMeterItem.java              handheld meter: reads a frequency where you stand
  network/MeterReadingPayload.java       server -> client: the meter's reading
  client/SignalMeterHud.java             the meter's panel beside the hotbar, its tick and needle
  block/RadioRepeaterBlock.java          picks up its frequency and broadcasts it again
  block/RadioConverterBlock.java         a repeater that sends on another frequency; opens its screen
  block/CompactRadioReceiverBlock.java   button-sized receiver that attaches to a face
  block/RadioMastBlock.java              tower segment, carries signal up to the transmitter
  block/WallRedstoneBlock.java           redstone dust for walls
  blockentity/RadioBlockEntity.java      stores the frequency (kept through break/re-place) and who last tuned or placed it
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
  radio/FrequencyClaims.java             private frequencies: owners and friends, saved with the world (and the client's synced copy)
  network/SetFrequencyPrivacyPayload.java, FrequencyClaimPayload.java, FrequencyClaimsPayload.java  the Private button and Friends box, and keeping clients in sync
  client/GroupsScreen.java               the friend groups screen
  network/EditGroupPayload.java, GroupsPayload.java  saving a friend group, and sending a player their groups
  compat/RadioInfo.java                  what Jade shows for each radio block (works without Jade)
  compat/jade/SignalTowerJadePlugin.java the Jade plugin (only loaded by Jade)
  client/FrequencyScreen.java            the frequency (and remote mode) screen
  client/DirectoryScreen.java            the frequency directory
  network/DirectoryRequestPayload.java, DirectoryResultPayload.java  the directory asking the server what's on
  client/MultiRemoteScreen.java          the Multi-Button Remote keypad
  client/ConverterScreen.java            the Radio Converter's two frequencies and its signal gate
  block/CodeKeypadBlock.java             the Code Keypad: attaches like a button, opens its screen, powers the block it's on
  blockentity/CodeKeypadBlockEntity.java its owner, code (server only), pulse length, lockouts, and sending the pulse
  client/KeypadScreen.java               the keypad's screen
  client/KeypadGuestsScreen.java         the keypad owner's Guest codes screen
  network/KeypadGuestCodesPayload.java, KeypadGuestListPayload.java  guest codes screen <-> server
  block/CompactRadioTransmitterBlock.java  button-sized transmitter with a short fixed reach
  block/RadioMotionSensorBlock.java      button-sized motion sensor: owner, screen or readout
  blockentity/MotionSensorBlockEntity.java  its owner, range and mode, looking for players, sending
  client/SensorScreen.java               the motion sensor owner's settings screen
  network/OpenSensorScreenPayload.java, ConfigureSensorPayload.java  sensor screen <-> server
  network/OpenKeypadScreenPayload.java, KeypadEnterPayload.java, KeypadConfigurePayload.java, KeypadResultPayload.java  keypad screen <-> server
  client/ReachRing.java, ReachRingShape.java  the reach preview ring and its two lines above the hotbar
  network/ReachPreviewRequestPayload.java, ReachPreviewPayload.java  asking the server how far a tower reaches
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
95. naming a frequency only works with a tuner in hand, and the name then shows in the remote's press message, the pager's alert, Jade and the tuner's message; a frequency without a name shows just its number;
96. private frequency claims allow only the owner and friends, cap the friends at 16 (leaving the owner out, keeping their order), and survive saving, both network packets and the client's copy; the Friends box splits names on commas and spaces without repeats;
97. on a private frequency, a stranger's transmitter, repeater and converter stay quiet (receivers and the scanner don't hear them, their turn-ons aren't remembered, a press isn't carried through them), while the owner's, a friend's and an old untracked one work; who tuned them is saved; taking a friend off silences theirs at once, and making it public brings everyone back;
98. a stranger's remote press on a private frequency is refused (with a red light and the owner's name) and can't switch it on or off, while the owner's and a friend's presses work;
99. the Private button only works with a tuner in hand; only the owner changes a private frequency, an operator can make it public; unknown names are reported, offline friends are kept by name, the per-player limit is enforced, and a frequency other players' blocks send on can't be claimed; only members rename it;
100. a stranger can't tune a transmitter or converter onto a private frequency (a receiver is fine), a block they place with it stays silent and says so (readout and Jade), and whoever tunes or places a block becomes its tuner, saved with it;
101. the directory's answer survives the network, and each entry knows whether it's hidden and whether it's on;
102. "strongest anywhere" counts transmitters far from everything, leaves out silenced ones, and counts converters sending on the frequency and remote toggles;
103. the directory answers only players holding a radio item, lists named and private frequencies in order, hides someone else's private frequency except for what's heard where the player stands, and shows a player's own, a public one and one they're a friend on;
104. friend groups leave out their owner and cap their members; a claim uses its owner's groups by name (ignoring case, unknown ones left out) and lets their members send; renaming carries the frequencies along, changing members or deleting the group changes who can send; all of it survives saving and the network; the Friends box tells "#groups" from names;
105. a group member's transmitter broadcasts on a frequency using the group, goes silent as soon as they're taken out, and comes back when they're put back;
106. the Groups screen's packet needs a tuner in hand and a name that isn't already one of the player's groups; unknown members are reported and offline ones kept by name; renaming carries a frequency along; the per-player limit; deleting; and the Friends box takes "#group" and reports unknown ones;
107. the directory answers a player with nothing in hand who opened it from a converter's screen within reach, but not for a converter out of reach or a block that isn't one; a Multi-Button Remote in hand counts; the request survives the network;
108. an encrypted claim is heard only by its owner, friends and group members (and blocks with no recorded tuner); it's saved (older saves load unencrypted), survives the network along with the tuner's packet, and stays encrypted when a group it uses is renamed or deleted;
109. on an encrypted frequency, a non-member's receiver hears nothing (no signal, no towers), their scanner doesn't list it, their converter neither passes it on nor carries presses, and their receivers aren't counted by remotes or the locator; members' and old blocks hear it; made just private, everyone hears it again;
110. real blocks: a stranger's receiver and toggle receiver stay off and say why (the tuner's message, the readout, Jade), the friend's and an old one light up; just private, the stranger's lights up, and becoming able to hear isn't a turn-on for the toggle receiver;
111. a non-member's pager doesn't alert, isn't counted for "Heard by" and reports nothing in the catch-up; their scanner can't track it and drops a target on it; their directory shows only "Encrypted"; the friend's do all of that;
112. the tuner's packet encrypts a new claim, decrypts and re-encrypts it, says so, changes nothing when asked for what it already is, refuses anyone but the owner, and lets an operator only make it public;
113. notes are tidied (spaces trimmed, formatting codes dropped, cut to 64 characters), an empty one removes the note, frequencies are clamped, a world keeps at most 4,096 (existing ones can still change), they're saved, the three note packets survive the network, and the client's copy (with its tooltip line) is kept up to date and cleared on leaving;
114. the Note screen's packet needs a tuner in hand, sets and removes a note and says so, changes nothing when nothing changed, and on a private frequency refuses non-members but lets a friend and an operator change it;
115. Jade's data carries the note and shows it right under the frequency line (under both lines for a converter, none without a note), and the directory lists frequencies that only have a note;
116. the audit of an encrypted frequency lists the owner's transmitter (sending 15), an old one with no recorded tuner, a friend's receiver and the owner's toggle receiver and listening converter with their positions; a stranger's silent transmitter, two deaf receivers (one row, counted) and deaf converter without positions; the owner's rows first; nothing for a public frequency; the row limit;
117. only the owner or an operator may audit a frequency (not a friend, not a public frequency), and the request and answer survive the network;
118. a note kept to members is saved (older saves load open), saving without the switch or removing the note drops it, releasing keeps the note, the three note packets carry the switch, and the client's copy (with "(members only)" on the tooltip line) follows each change;
119. a members-only note reaches the owner, a friend, a group member and an operator but not a stranger (nor nobody): the stranger's one-note packet is empty and their join list leaves it out; made public, everyone sees it, and claiming it again doesn't hide it;
120. the Note screen's packet keeps a note to members on a private frequency and says so, counts a change of only the switch, ignores the switch on a public frequency, and Jade's data leaves the note out for a stranger and marks it for the owner;
121. audit beams turn on for a minute (in their own dimension only) and off when clicked again, burn down tick by tick and go when spent, a ninth drops the one with the least time left, and leaving the world clears them;
122. handing a frequency over: only its owner may (not a friend or an operator), only to one of its members (a group member counts, the name ignoring case), not to someone at their limit nor when it has more members than 16 friends; the new owner gets it with everyone else as single friends (the old owner first), still encrypted, with its note; the old owner's blocks still send and a stranger's don't; a public one has nothing to hand over; the packet survives the network;
123. the frequency log lists the owner's transmitter (with its position), one with no recorded tuner (with its position), a friend's remote pulse and a stranger's toggle (named, no position), newest first; a silenced stranger's transmitter isn't a turn-on; someone no longer on it is named but not located; a converter's output frequency shows them carried over; only the owner and operators get it, not for a public frequency; causes are saved and old saves load without them; the answer survives the network;
124. a guest can send on a private frequency and hear it (encrypted) until their pass runs out, then drops off and their blocks go silent; never the owner, a friend or a ninth guest (a renewal still works when full); the owner's packet renews and removes guests and refuses an offline name, a wrong length, a non-owner and a public frequency; editing the friends or handing it over keeps the guests (not one who became a friend); guests are saved and sent to clients;
125. intruder alerts: a stranger's refused remote press (the real remote code), a transmitter they place that stays silent (the real placement) and a tune attempt are kept for an owner who's away, at most once a minute each; friends and public frequencies don't alert; at login the owner gets how many and the last five, then they're forgotten; older ones are only counted; kept alerts survive saving.
126. the reach preview: the server answers a player holding a tuner (either hand) with the reach of a transmitter, repeater, converter (on the frequency it sends on) or Code Keypad; a receiver, another block, no tuner or more than 160 blocks away gets "nothing to show"; the answer survives the network;
127. the ring's shape: "inside" follows the network's own rule for every offset and reach, the distance to the edge counts down to 0 and goes negative outside, the dots sit 1.5 blocks apart half a block outside the reach, and huge rings are capped;
128. the Code Keypad: only its owner sets a code (4 to 8 digits; an operator may too) and the message doesn't repeat it; a remote in hand still opens it and only the owner re-tunes it; three wrong codes lock that player (not others) out for 30 seconds, even for the right code, and alert the owner (kept with where the keypad is, surviving saving), but not for the owner's own mistakes; afterwards the right code sends the pulse (heard nearby, recorded as the keypad with who typed it), lights it and powers the block it's on until the pulse ends; the code is saved, but only the frequency goes on the item, and the screen's packet never carries it;
129. a keypad placed on someone else's private frequency says it's silent, alerts that frequency's owner and sends nothing for the right code (Jade says silent); a member's keypad shows in Jade (owner, no code yet, waiting, reach, never the code) and in the frequency log (the keypad's position, typed by a stranger who knew the code), and the log's new kind survives the network.
130. the Compact Radio Transmitter: on a wall, it broadcasts the signal around it over its 16-block reach (heard at 16 blocks, not at 17, at any height, where a full transmitter would reach farther); it isn't a relay tower (a full transmitter next to it is), even after saving; the reach ring, Jade and the audit know it; and it pops off with its frequency when its wall goes;
131. keypad guest codes: only the owner adds them (with a name, 4 to 8 digits, not the keypad's code nor one already used, names different, at most 8) and the main code can't be changed to one; the owner's list carries them (never the main code) and survives the network, Alex's is empty; a code with 3 uses works 3 times (counted, with who used it last), then says it's used up without counting as wrong codes; an hourly one works within the hour and then has expired; one with no limits keeps working; they're saved with the keypad; the frequency log names the guest code (surviving the network and saving); removing one (name ignoring case) makes it a wrong code;
132. the inverted receiver: on (15, a lamp next to it lit) while its frequency is silent, off (0, the lamp out) while it's on, and on again once it's silent; on someone else's encrypted frequency it hears nothing and stays on; Jade (with and without server data), the readout and the audit know it;
133. the motion sensor: placing it makes it the placer's (and tuned by them); it sees the nearest player in its 6-block range with a clear line from its face, not someone too far, behind a block or a spectator (and someone farther once the range is 12); only its owner changes the settings (7 blocks, which isn't offered, becomes 6); strangers-only ignores the owner and, on a private frequency, its friends; redstone around it doesn't make it send; seeing Rex it lights and sends over its 16-block reach (not past it, not a relay tower), and the log names Rex (red) at the sensor; Jade shows the owner its settings and who it sees, and Alex neither; the screen packet (with a round trip), the audit and the reach ring know it; it keeps sending for 2 seconds with nobody in sight, then stops; Alex can't re-tune it; it keeps its owner, range and mode when saved, and its frequency when it pops off.;
134. the logic receiver: AND is the weaker, OR the stronger, XOR the one that's on; with both off AND is off and it's tracked on both frequencies (counted for Heard by on the second); with only the first on AND stays off, a player out of reach can't change it, Dev's packet sets OR (and tunes it) and it lights a lamp; Jade and the screen packet (round trip) show both frequencies, the mode and what it hears; XOR is on with one, off with both; AND is on with both; the audit lists it; on Rex's tuning with Dev's encrypted second frequency it's deaf there (notice, screen flag, Jade line); it's saved; re-tuning the second moves its tracking; broken, it drops with both frequencies and XOR, and placed again from that item it has them back;
135. the Signal Meter: two blocks from a transmitter it reads 15 and its reach minus 2 to the edge, out of reach nothing and -1; with the transmitter off 0 but still inside; on someone else's encrypted frequency deaf with nothing else, while its owner reads it; the edge is 20 at 30 blocks into a 50 reach, 0 exactly at it, -1 a block past it, and an off tower beside counts; the reading packet survives the network (-1 too); its settings packet sets the frequency and turns the tick off; it scrolls, takes the scanner's Tune and opens the directory; sneak-clicking a converter measures what it sends;
136. the signal gate, with a transmitter sending 11: Pass sends 11, At least 8 sends 11 (set by Dev's packet), At least 12 nothing, Below 12 sends 15 (the log names the gate), Below 11 nothing, and Below 1 nothing until the transmitter goes quiet, then 15; Jade, the screen and settings packets (round trips) know it; on a fresh network a gate is a source, saved with what it sends, and a press isn't carried through it (only through a plain converter); a NOT gate feeding itself blinks, never changing twice in a row within 2 ticks; it's saved, and drops with its gate;
137. the delay receiver: with a 1 s delay and 2 s hold it turns on 20 to 24 ticks after its frequency and off 60 to 65 ticks after it goes quiet, saying when (Jade's lines, the screen packet and a round trip); with no delay and a 2 s hold a 4-tick pulse turns it on at once and holds it 40 ticks; a player out of reach can't set it, times it doesn't offer become none, time labels read 0.5s, 60s and none (and countdowns 2.4s, 2s, 0.1s); it's saved, and drops with its timings;
138. the timer transmitter: set by Dev's packet to every 1 s with a 0.25 s pulse, a receiver turns on every 19 to 21 ticks for 4 to 6, and the log names the timer; Jade, the screen and settings packets (round trips) know it; a 10 s pulse is cut to half the interval, and times it doesn't offer become the defaults; a redstone block next to it pauses it (and Jade and the screen say so) until it's gone; a daily timer is on during its hours and off outside them (Jade says until when, or when it turns on), and the clock's hours and labels are right; on someone else's private frequency a stranger's packet is refused, and one out of reach does nothing; it's saved, and drops with its schedule;
139. the counter receiver: set by Dev's packet to 3 with reset frequency 4052 (Dev becomes its tuner; out of reach does nothing), two turn-ons leave it off and the third pulses it on and counts from 0 again; with a 1 s forget time a pause forgets the count (Jade's lines while counting); its reset frequency clears the count; set to stay on at 2, it stays on through more turn-ons (Jade, the screen packet and a round trip, saved with its count) until the reset frequency turns on; Reset count, no reset frequency, and it drops with its settings;
140. the speaker: set by Dev's packet to a bell played once (out of reach does nothing), a short pulse plays one note and 50 ticks on still one; one placed while its frequency is on stays quiet but outputs 15; set to the doorbell repeating, 45 ticks on play three rings of two notes; note labels (F♯3, F♯4, C5, F♯5) and pitches are a note block's; Jade, the screen packet (round trip) and saving keep its settings, and it drops with them;
141. the status board: hung on a wall and set by Dev's packet to 4151, 4152, an empty row and 4153 (out of reach does nothing), its rows follow their transmitters on and off, the update tag carries the rows and strengths (not who set it) to clients, Jade lists the rows, a row on someone else's encrypted frequency is deaf (Jade and the screen say so), the screen packet round-trips, it's saved, and with its wall gone it pops off with its rows on the item;
142. the aviation beacon: its clock (dusk to dawn, blink, flash, steady) and when it's lit; on top of a transmitter on masts, on a mast's side and on a stone wall it hangs (not in the air), the first two find the tower and the third none; set to "while its tower sends" it's on only while the transmitter broadcasts (Jade knows its tower, how far up, and whether it's on); the screen's packet sets it (round trip), from anyone within reach; and broken, or popped off when its mast goes, it drops with its settings;
143. the signal recorder: with nothing to record from, or the same frequency both ways, it says so; Record waits for 4201 to turn on, then records two 10-tick pulses to the tick until Stop; Play sends them on 4202 once, with the same rhythm (the log names the recorder; Jade knows the tape); in Once mode a redstone pulse plays it once; in Loop mode it plays over and over while powered and stops when the power goes; the status, screen and settings packets round-trip (no status out of reach); a stranger can't change it on someone else's private frequency, nor anyone out of reach; it's saved, and drops with its recording;
144. antennas: a Diamond Antenna fitted to a transmitter triples its reach (in the network too) and uses one up; fitting it again does nothing; a Netherite one replaces it (×4) and gives the diamond one back; taking it off (also by sneaking with an empty hand) gives it back and restores the reach; a Gold Antenna doubles a repeater's; only full-size tower tops take one; a stranger is refused on someone else's private frequency; Jade shows it; and the broken transmitter keeps its antenna on the item;
145. the Radio Lamp: a transmitter fed through six blocks of dust sends 10 and the lamp gives off light 10, with no redstone out (a redstone lamp beside it stays dark); a dye recolors it (using one); its tint is darker off than on; Jade shows its light and color; dark when the signal goes; and broken, it keeps its color and frequency on the item;
146. weather: rain takes 15% off a tower's reach and a thunderstorm 30%, where it stands; it rains on plains but not in a desert or the Nether; a repeater's and a jammer's reach shrink too, a compact transmitter's doesn't; the meter's edge moves in; and a change of weather bumps the frequency;
147. hills: 32 blocks of hill between a transmitter and a receiver take 4 off (the receiver outputs 11, Jade and the meter say "Hills"); a wall right by the receiver and the test's own ceiling don't count; and a repeater on top of the hill gets it over (15 again);
148. jammers in the network: 5 off 12 leaves 7 and 15 silences it, inside its reach only and on its own frequency; the scanner hears nothing either; a repeater inside it has nothing to pass on; switched off it's heard (and passed on) again; saved and loaded with the world;
149. the Radio Jammer: powered by redstone it silences a receiver near it and not one far away, at half a transmitter's reach; Jade says "Jammed" on the receiver and what it jams on the jammer; the meter says "Jammed"; it sends nothing itself; a stranger's jammer on someone else's private frequency is silent (and says so, and shows in the audit); and broken, it stops and keeps its frequency on the item.

150. receiver height: 4 blocks up a receiver hears a tower 30 blocks past its reach (3 up doesn't), one lower than the tower gains nothing, a compact transmitter keeps its short reach, a repeater up high picks it up and passes it on, and the meter and scanner go by it; a real receiver 11 blocks above a transmitter hears it from 98 blocks (Jade says why) and one at the transmitter's height doesn't;
151. lightning: the chance grows with height (none below sea level); struck, a transmitter on masts goes quiet for 5 seconds (receiver off, light out, Jade counts down) and comes back; a Lightning Rod beside its top takes the next hit (the rod pulses, the tower stays on) but one 3 blocks away is too far; a bolt that isn't the mod's knocks it out too; compact transmitters can't be struck, repeaters can;
152. signal fade (in a batch of its own; every other test runs with fading off, to check other things at exact strengths): 15 out to half the reach, 8 at three quarters and 1 at the very edge, the same in every direction; a repeater 90 blocks out sends it on at 15 and it fades again toward the repeater's edge; a compact transmitter doesn't fade; a jammer three quarters out jams 8; the meter's packet carries it; a real receiver three quarters out outputs what's left and Jade says how much faded;
153. the Dish Antenna: looking north, east, south or west aims it 0, 4, 8 or 12; its beam is 15° either side; a tower reaches 192 blocks in its beam and 64 everywhere else (the meter's edge too, and a press it relays), saved and loaded; without it, 64 again; a jammer's dish jams farther that way only; fitted in the world it reaches a receiver 125 blocks east, Jade and the reach ring (a keyhole, also over the network) show it; a second dish turns it west (nothing used up, the receiver goes off), the same way again says it already faces there, and an iron antenna replaces it and gives the dish back;
154. power cost (in a batch of its own, with it on; every other test runs with it off): a transmitter with no battery, or an empty one, is out of power (its light off, Jade says so, its receiver dark); repeaters and jammers need one too, compact transmitters don't; a battery by its mast feeds the tower on top; three Redstone Dust (3,000) bring it back, it uses 1 a second per 64 blocks of reach, Jade shows both ends, and it goes quiet when the charge runs out; a hopper puts Blocks of Redstone in, FE charges it, it's full at 100,000, a comparator reads 15 full and 8 at half, and the item keeps its charge;
155. the Coverage Map: green, yellow, orange, red, blank; every pixel of a map at 1:4 painted from the network (heard inside the 100-block reach, blank in a jammer's 20 and beyond); drawn in the world it's a real map at 1:4, the same map again in the same square, painted green round a transmitter with a red marker on it after a whole pass while held, and a new map at a new zoom;
156. the Ender Antenna: 1/8 into the Nether and back (rounding down), 1:1 into the End, the height kept inside the world; the Nether from the Overworld and the Overworld from elsewhere to start with, and the next dimension round after that; a repeater can't take one; fitted to a transmitter, the Nether hears it at the spot it arrives, out to its reach and no farther (the meter's edge too), Jade says where it goes, and the Nether knows at once when it turns off; another one moves it on to the End (nothing used up) and the Nether stops hearing it; saved in the network and the block; gone with the tower;
157. the Solar Panel: 3 a second in full sun and a third at 5; fractions add up from one second to the next; with no battery under it Jade says so; on a battery it charges it by day (Jade on both) and not in the dark;
158. the mast builder: a mast used on a tower's bottom mast goes in under the top, lifting it with its frequency, its Diamond Antenna and the beacon on it, the network following and the reach growing (the message says 2 masts up, the reach before and after); used on the top itself it works too, and redstone still comes up the column; with stone in the way it says there's no room; a bare column grows on top, and a lightning rod rides up; someone else's private tower is refused (nothing used up);
159. the Radio Telegraph: messages are cleaned and cut to 64 characters; one sent on 4533 is remembered and read by the other telegraph on 4533 (it pulses, its screen shows it), not one on 4534 or the sender; sending again at once, or nothing, is refused; a pager back from being away says what it missed, and one in reach gets it (in chat too), not one on another frequency; a Status Board row would show it; Jade shows the last one; saved; the pulse ends; on an encrypted frequency a non-member can't send, and the owner's message isn't readable on a non-member's telegraph (its screen says "encrypted", Jade says deaf) but the log keeps it; the screen re-tunes it, and its packet round-trips.

All 159 also pass with `-PnoJade`, so the mod runs the same without Jade.

## License

All rights reserved. To open-source it, add a `LICENSE` file and update `mod_license` in `gradle.properties`.
