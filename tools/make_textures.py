"""Draws the 16x16 block textures for Reiko's Redstone Signal Tower (the items' textures come from make_item_models.py).

Usage: python3 tools/make_textures.py
Writes into src/main/resources/assets/reikos_signal_tower/textures/. Needs Pillow (pip install pillow).
Feel free to paint over the results in any image editor instead; nothing else depends on this script.
"""
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/reikos_signal_tower/textures"
BLOCK = ROOT / "block"
ITEM = ROOT / "item"

# Steel casing palette
O = (38, 40, 46, 255)      # outline
D = (60, 63, 71, 255)      # dark
M = (86, 90, 99, 255)      # mid
M2 = (94, 98, 108, 255)    # mid, slightly lighter (noise)
L = (118, 123, 134, 255)   # light
H = (156, 162, 175, 255)   # highlight

# Redstone, off and on
OFF = [(74, 10, 10, 255), (112, 18, 16, 255), (146, 30, 26, 255)]
ON = [(170, 14, 10, 255), (236, 44, 30, 255), (255, 150, 120, 255)]

# Repeater amber, off and on
AMBER_OFF = [(84, 52, 8, 255), (124, 80, 14, 255), (156, 104, 24, 255)]
AMBER_ON = [(204, 122, 10, 255), (255, 178, 40, 255), (255, 226, 140, 255)]

# Converter teal, off and on
TEAL_OFF = [(10, 66, 70, 255), (18, 98, 104, 255), (28, 128, 134, 255)]
TEAL_ON = [(12, 160, 170, 255), (44, 222, 232, 255), (176, 250, 255, 255)]
# Radio Motion Sensor (v0.39.0)
PURPLE_OFF = [(46, 20, 66, 255), (72, 34, 104, 255), (98, 52, 138, 255)]
PURPLE_ON = [(140, 50, 220, 255), (192, 100, 255, 255), (238, 196, 255, 255)]

# Radio Jammer (v0.46.0)
MAGENTA_OFF = [(70, 14, 50, 255), (108, 26, 80, 255), (140, 38, 104, 255)]
MAGENTA_ON = [(200, 30, 136, 255), (255, 80, 200, 255), (255, 196, 238, 255)]

CLEAR = (0, 0, 0, 0)


def blank(color=CLEAR):
    return Image.new("RGBA", (16, 16), color)


def casing(seed, rivets=True):
    """Beveled steel plate with a little noise and (optionally) corner rivets."""
    rnd = random.Random(seed)
    img = blank(M)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.18:
                px[x, y] = M2
    for i in range(16):
        px[i, 0] = px[i, 15] = O
        px[0, i] = px[15, i] = O
    for i in range(1, 15):
        px[i, 1] = H
        px[1, i] = L
        px[i, 14] = D
        px[14, i] = D
    px[1, 1] = H
    px[14, 1] = L
    px[1, 14] = L
    for (x, y) in ([(3, 3), (12, 3), (3, 12), (12, 12)] if rivets else []):
        px[x, y] = H
        px[x + 1, y + 1] = O
        px[x + 1, y] = D
        px[x, y + 1] = D
    return img


def vents(img):
    """Three horizontal vent slots near the bottom of a side texture."""
    px = img.load()
    for y in (11, 13):
        for x in range(4, 12):
            px[x, y] = O
        px[4, y + 1 if y + 1 < 15 else y] = px[4, y]
    for x in range(4, 12):
        px[x, 12] = D


def transmitter_side(lit):
    """A broadcast mast emitting waves to both sides."""
    r = ON if lit else OFF
    img = casing(1)
    px = img.load()
    vents(img)
    for y in range(3, 10):
        px[7, y] = r[2]
        px[8, y] = r[1]
    px[7, 2] = px[8, 2] = r[2]
    # Outgoing wave arcs left and right of the mast
    for (x, y) in [(5, 4), (4, 5), (4, 6), (5, 7)]:
        px[x, y] = r[1]
        px[15 - x, y] = r[1]
    for (x, y) in [(3, 3), (2, 4), (2, 5), (2, 6), (2, 7), (3, 8)]:
        px[x, y] = r[0] if not lit else r[1]
        px[15 - x, y] = px[x, y]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def receiver_side(lit):
    """Waves coming in to a pickup dot, like a wifi icon."""
    r = ON if lit else OFF
    img = casing(2)
    px = img.load()
    vents(img)
    for p in [(7, 8), (8, 8), (7, 9), (8, 9)]:
        px[p] = r[1]
    px[7, 8] = r[2]
    for p in [(6, 6), (7, 5), (8, 5), (9, 6)]:
        px[p] = r[2]
    for p in [(4, 5), (5, 4), (6, 3), (7, 3), (8, 3), (9, 3), (10, 4), (11, 5)]:
        px[p] = r[1]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def toggle_receiver_side(lit):
    """Incoming waves above a little two-position switch: knob on the left when off, on the right when on."""
    r = ON if lit else OFF
    img = casing(8)
    px = img.load()
    vents(img)
    # Waves coming in, like the receiver's (smaller)
    for p in [(6, 4), (7, 3), (8, 3), (9, 4)]:
        px[p] = r[1]
    px[7, 5] = px[8, 5] = r[2]
    # Switch body
    for x in range(4, 12):
        px[x, 7] = O
        px[x, 9] = O
    px[4, 8] = px[11, 8] = O
    for x in range(5, 11):
        px[x, 8] = D
    # Knob
    knob = (8, 9, 10) if lit else (5, 6, 7)
    for x in knob:
        px[x, 8] = r[2] if lit else r[1]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def inverted_receiver_side(lit):
    """Incoming waves above a little redstone torch. "lit" is the output: the torch glows while the
    frequency is silent (waves dark), and goes out while it's heard (waves bright)."""
    waves = OFF if lit else ON
    torch = ON if lit else OFF
    img = casing(9)
    px = img.load()
    vents(img)
    for p in [(6, 3), (7, 2), (8, 2), (9, 3)]:
        px[p] = waves[1]
    px[7, 4] = px[8, 4] = waves[2]
    wood = (112, 82, 50, 255)
    wood_dark = (78, 56, 34, 255)
    for y in (8, 9):
        px[7, y] = wood
        px[8, y] = wood_dark
    for p in [(7, 6), (8, 6), (7, 7), (8, 7)]:
        px[p] = torch[1]
    px[7, 6] = torch[2]
    if lit:
        px[6, 6] = px[9, 6] = torch[0]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def logic_receiver_side(lit):
    """Two small sets of incoming waves (one per frequency) whose lines meet in a lamp below, like
    a gate symbol. "lit" is the output: the lamp glows while the gate's condition holds."""
    r = ON if lit else OFF
    img = casing(11)
    px = img.load()
    vents(img)
    for (x0) in (3, 9):
        for p in [(x0, 3), (x0 + 1, 2), (x0 + 2, 2), (x0 + 3, 3)]:
            px[p] = r[1]
        px[x0 + 1, 4] = px[x0 + 2, 4] = r[2] if lit else r[1]
    # Lines from each wave set down into the gate
    for p in [(5, 5), (6, 6), (10, 5), (9, 6)]:
        px[p] = D
    # The gate: a small dark body with the lamp
    for x in range(6, 10):
        px[x, 7] = O
        px[x, 9] = O
    px[6, 8] = px[9, 8] = O
    px[7, 8] = r[2] if lit else r[0]
    px[8, 8] = r[1] if lit else r[0]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def delay_receiver_side(lit):
    """Incoming waves above a small hourglass (v0.41.0): the sand glows red while it's on."""
    r = ON if lit else OFF
    img = casing(12)
    px = img.load()
    vents(img)
    for p in [(6, 3), (7, 2), (8, 2), (9, 3)]:
        px[p] = r[1]
    px[7, 4] = px[8, 4] = r[2] if lit else r[1]
    # Hourglass: frame top and bottom, glass sides narrowing to the middle
    for x in range(5, 11):
        px[x, 5] = O
        px[x, 10] = O
    for (x, y) in [(5, 6), (10, 6), (6, 7), (9, 7), (6, 8), (9, 8), (5, 9), (10, 9)]:
        px[x, y] = L
    # Sand: a little left on top, a heap at the bottom
    px[7, 6] = px[8, 6] = r[1]
    px[7, 7] = r[0]
    px[7, 8] = r[0]
    px[6, 9] = px[7, 9] = px[8, 9] = px[9, 9] = r[2] if lit else r[1]
    return img


def timer_transmitter_side(lit):
    """A clock face between outgoing waves (v0.42.0): the hands glow red while it's sending."""
    r = ON if lit else OFF
    img = casing(13)
    px = img.load()
    vents(img)
    # Clock rim: a small circle around (7.5, 5.5)
    for (x, y) in [(6, 2), (7, 2), (8, 2), (9, 2), (5, 3), (10, 3), (4, 4), (11, 4), (4, 5), (11, 5), (4, 6), (11, 6),
                   (4, 7), (11, 7), (5, 8), (10, 8), (6, 9), (7, 9), (8, 9), (9, 9)]:
        px[x, y] = O
    for y in range(3, 9):
        for x in range(5, 11):
            if px[x, y] != O and not ((x, y) in [(5, 3), (10, 3), (5, 8), (10, 8)]):
                px[x, y] = H
    # Hands: up to 12 and across to 3, meeting in the middle
    for y in (3, 4, 5):
        px[7, y] = r[1]
    px[7, 6] = r[2]
    for x in (8, 9):
        px[x, 6] = r[1]
    # Outgoing waves left and right
    for (x, y) in [(2, 4), (1, 5), (1, 6), (2, 7)]:
        px[x, y] = r[1] if lit else r[0]
        px[15 - x, y] = px[x, y]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def weather_station_side(lit):
    """A sun behind a rain cloud between outgoing waves (v0.53.0): the waves and raindrops glow red while it's sending."""
    r = ON if lit else OFF
    img = casing(14)
    px = img.load()
    vents(img)
    sun = [(250, 196, 40, 255), (255, 226, 92, 255)]
    # Sun, peeking out top left
    for (x, y) in [(5, 2), (6, 2), (4, 3), (5, 3), (6, 3), (7, 3), (4, 4), (5, 4), (6, 4)]:
        px[x, y] = sun[1] if (x + y) % 3 else sun[0]
    for (x, y) in [(3, 2), (5, 1), (8, 2), (3, 5)]:
        px[x, y] = sun[0]  # rays
    # Cloud: outline, then a light fill
    cloud = [(7, 3), (8, 3), (9, 3), (6, 4), (10, 4), (11, 5), (12, 5), (5, 5), (4, 6), (13, 6), (4, 7), (13, 7), (5, 8),
             (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8), (12, 8)]
    for p in cloud:
        px[p] = O
    for (x, y) in [(7, 4), (8, 4), (9, 4), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6),
                   (10, 6), (11, 6), (12, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (12, 7)]:
        px[x, y] = H if y < 7 else L
    # Raindrops under it
    for (x, y) in [(6, 9), (9, 9), (12, 9), (5, 10), (8, 10), (11, 10)]:
        px[x, y] = r[2] if lit else r[1]
    # Outgoing waves left and right
    for (x, y) in [(2, 5), (1, 6), (1, 7), (2, 8)]:
        px[x, y] = r[1] if lit else r[0]
        px[15 - x, y] = px[x, y]
    return img


# v0.54.0: bundled cable. The 16 dye colors, roughly as their wool looks.
DYES = [(233, 236, 236), (240, 118, 19), (189, 68, 179), (58, 175, 217), (248, 197, 39), (112, 185, 25),
        (237, 141, 172), (62, 68, 71), (142, 142, 134), (21, 137, 145), (121, 42, 172), (53, 57, 157),
        (114, 71, 40), (84, 109, 27), (161, 39, 34), (20, 21, 25)]


def bundled_cable():
    """Black insulation with a rainbow of thin cores showing through, along the cable (x)."""
    img = blank()
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = (34, 35, 40, 255) if (x + y) % 5 else (46, 47, 54, 255)
    # 16 cores in 4 rows of 4 colors, seen as dashes along the cable
    for i, (r, g, b) in enumerate(DYES):
        row = 4 + (i // 4) * 2
        x0 = (i % 4) * 4 + (i // 4) % 2 * 2
        for dx in range(2):
            px[(x0 + dx) % 16, row] = (r, g, b, 255)
    for x in range(16):
        px[x, 2] = (20, 20, 24, 255)
        px[x, 13] = (20, 20, 24, 255)
    return img


def bundled_side(lit, receiver):
    """A cable plug with rainbow pins, under waves going out (transmitter) or coming in (receiver)."""
    r = ON if lit else OFF
    img = casing(15 if receiver else 16)
    px = img.load()
    vents(img)
    for x in range(4, 12):
        px[x, 7] = O
        px[x, 10] = O
    px[4, 8] = px[4, 9] = px[11, 8] = px[11, 9] = O
    for i, x in enumerate(range(5, 11)):
        c = DYES[(i * 3) % 16]
        px[x, 8] = (c[0], c[1], c[2], 255)
        c = DYES[(i * 3 + 8) % 16]
        px[x, 9] = (c[0], c[1], c[2], 255)
    if receiver:
        for p in [(6, 4), (7, 5), (8, 5), (9, 4)]:
            px[p] = r[2]
        for p in [(4, 3), (5, 2), (6, 2), (9, 2), (10, 2), (11, 3)]:
            px[p] = r[1]
    else:
        for y in range(2, 6):
            px[7, y] = r[2]
            px[8, y] = r[1]
        for (x, y) in [(5, 3), (4, 4), (5, 5)]:
            px[x, y] = r[1]
            px[15 - x, y] = r[1]
    return img


def cable_tap_side(output):
    """A small casing with an arrow: into the cable (up, in) or out of it (down, out)."""
    img = casing(17, rivets=False)
    px = img.load()
    arrow = (232, 236, 240, 255) if output else (250, 200, 60, 255)
    for y in range(9, 15):
        px[7, y] = px[8, y] = arrow
    tip = [(6, 10), (9, 10), (5, 11), (10, 11)] if not output else [(6, 13), (9, 13), (5, 12), (10, 12)]
    for p in tip:
        px[p] = arrow
    return img


def cable_tap_band():
    """A white band (tinted with the tap's color in the game)."""
    img = blank()
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = (255, 255, 255, 255) if (x * 7 + y * 3) % 9 else (226, 226, 226, 255)
    return img


def speaker_side(lit):
    """A speaker cone under incoming waves (v0.43.0): its middle glows red while it hears its frequency."""
    r = ON if lit else OFF
    img = casing(15, rivets=False)
    px = img.load()
    for p in [(6, 2), (7, 1), (8, 1), (9, 2)]:
        px[p] = r[1]
    # Cone: a dark ring, a lighter ring, a dark ring and the middle
    import math
    for y in range(3, 15):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 8.5)
            if d <= 5.6:
                px[x, y] = O if d > 4.7 else L if d > 3.6 else D if d > 2.6 else M if d > 1.4 else (r[2] if lit else r[1])
    return img


def recorder_side(lit):
    """Two tape reels joined by the tape, over a record light (v0.44.0): it glows red while it plays."""
    r = ON if lit else OFF
    img = casing(18)
    px = img.load()
    vents(img)
    import math
    for cx in (4.5, 10.5):
        for y in range(1, 9):
            for x in range(0, 16):
                d = math.hypot(x - cx, y - 4.5)
                if d <= 3.0:
                    px[x, y] = O if d > 2.3 else L if d > 1.2 else D
    # The tape between the reels, along the bottom
    for x in range(4, 12):
        px[x, 7] = (40, 30, 26, 255)
    # Record light and a small window under the reels
    for (x, y) in [(7, 10), (8, 10), (7, 11), (8, 11)]:
        px[x, y] = r[2] if lit else r[1]
    for x in (6, 9):
        px[x, 10] = px[x, 11] = O
    return img


def jukebox_side(lit):
    """A record between two music notes (v0.60.0): its label and the notes glow red while it plays."""
    r = ON if lit else OFF
    img = casing(23)
    px = img.load()
    vents(img)
    import math
    for y in range(0, 11):
        for x in range(1, 15):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 5.5)
            if d > 4.7:
                continue
            if d > 4.0:
                px[x, y] = O
            elif d > 1.6:
                px[x, y] = (24, 24, 29, 255) if int((d - 1.6) / 0.8) % 2 == 0 else (52, 52, 60, 255)  # grooves
            elif d > 0.75:
                px[x, y] = r[2] if lit else r[1]  # the label
            else:
                px[x, y] = O  # the hole
    note = r[2] if lit else r[1]
    for (x, y) in [(2, 2), (2, 3), (2, 4), (2, 5), (1, 5), (1, 6), (2, 6), (3, 2), (3, 3)]:
        px[x, y] = note
    for (x, y) in [(13, 4), (13, 5), (13, 6), (13, 7), (12, 7), (12, 8), (13, 8), (14, 4), (14, 5)]:
        px[x, y] = note
    return img


def jammer_side(lit):
    """A jagged burst of static struck through by a slash (v0.46.0): it glows magenta while jamming."""
    r = MAGENTA_ON if lit else MAGENTA_OFF
    img = casing(21)
    px = img.load()
    vents(img)
    # A thin zigzag of static across the middle: bright tips, darker strokes between them
    points = [(2, 6), (3, 4), (4, 7), (5, 3), (6, 6), (7, 2), (8, 7), (9, 4), (10, 8), (11, 3), (12, 6), (13, 5)]
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        for y in range(min(y0, y1) + 1, max(y0, y1)):
            px[x1 if abs(y - y1) < abs(y - y0) else x0, y] = r[0] if not lit else r[1]
    for (x, y) in points:
        px[x, y] = r[2] if lit else r[1]
    # The slash: no signal gets through
    for i in range(10):
        px[3 + i, 9 - i] = O
    return img


def beacon_dome(color):
    """An aviation light's glass (v0.44.0): red or white, darker at the edges, with a shine."""
    base = {"red": [(96, 14, 12, 255), (150, 24, 18, 255), (196, 44, 32, 255), (236, 120, 100, 255)],
            "white": [(150, 150, 146, 255), (196, 196, 190, 255), (226, 226, 220, 255), (250, 250, 246, 255)]}[color]
    img = blank(base[1])
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = min(x, 15 - x, y, 15 - y)
            px[x, y] = base[0] if edge < 1 else base[1] if edge < 3 else base[2]
    # Ribs of the lens, and a shine at the top left
    for x in (6, 9):
        for y in range(2, 14):
            px[x, y] = base[1]
    for (x, y) in [(4, 3), (5, 3), (4, 4), (4, 5)]:
        px[x, y] = base[3]
    return img


def beacon_base():
    """An aviation light's housing and bracket (v0.44.0): dark painted steel."""
    img = casing(19, rivets=False)
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = (r * 3 // 5, g * 3 // 5, b * 3 // 5, a)
    return img


def beacon_glow():
    """The light around a lit aviation beacon (v0.44.0), 64 by 64: white in the middle fading to
    black at the edge, opaque (it's added to what's behind it, so black adds nothing)."""
    import math
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    px = img.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5) / 32.0
            v = max(0.0, 1.0 - d)
            v = v ** 1.7 * 0.8 + max(0.0, 0.5 - d * 2.5)
            c = max(0, min(255, int(round(v * 255))))
            px[x, y] = (c, c, c, 255)
    return img


def lamp_frame():
    """The Radio Lamp's steel frame (v0.45.0): a border and a cross, with clear windows the
    glass shows through."""
    img = casing(20, rivets=False)
    px = img.load()
    for y in range(16):
        for x in range(16):
            border = x < 2 or x > 13 or y < 2 or y > 13
            cross = x in (7, 8) or y in (7, 8)
            if not border and not cross:
                px[x, y] = CLEAR
            elif not border and cross:
                px[x, y] = M if (x + y) % 3 else D
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[x, y] = O
    return img


def lamp_glass():
    """The Radio Lamp's glass (v0.45.0): light grey with a soft bright middle, tinted by the
    client to the lamp's color."""
    import math
    img = blank((200, 200, 200, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 10.6
            v = int(255 - 70 * d)
            if (x * 7 + y * 3) % 11 == 0:
                v -= 18
            px[x, y] = (v, v, v, 255)
    return img


def battery_side(bars):
    """The Radio Battery's side (v0.49.0): a steel cell with a window of four bars, lit from the bottom up."""
    img = casing(4900 + bars, rivets=True)
    px = img.load()
    for y in range(1, 15):                      # the window
        for x in range(5, 11):
            px[x, y] = O if x in (5, 10) or y in (1, 14) else D
    for i in range(4):                          # bars, bottom (i = 0) to top, a dark line between each
        lit = i < bars
        y0 = 12 - i * 3
        for y in (y0, y0 + 1):
            for x in range(6, 10):
                px[x, y] = (ON[1] if (x + y) % 3 else ON[2]) if lit else OFF[0]
    for x in range(1, 15):                      # a red band round the middle
        if x < 5 or x > 10:
            px[x, 7] = ON[0] if bars else OFF[1]
            px[x, 8] = ON[0] if bars else OFF[1]
    return img


def battery_top():
    """Its top: two terminals, + (red) and - (steel)."""
    img = casing(4910)
    px = img.load()
    for (cx, color) in ((4, ON[1]), (11, L)):
        for y in range(5, 11):
            for x in range(cx - 2, cx + 2):
                px[x, y] = O if x in (cx - 2, cx + 1) or y in (5, 10) else color
    for x in range(3, 5):                       # the plus
        px[x, 7] = H
        px[x, 8] = H
    px[11, 7] = O
    px[10, 7] = O
    return img


def solar_top():
    """The Solar Panel's face (v0.50.0): dark blue cells in a 4 x 4 grid with silver lines, a steel rim."""
    img = blank()
    px = img.load()
    rim, line = (120, 125, 136, 255), (170, 176, 188, 255)
    cell = [(18, 34, 92, 255), (26, 48, 120, 255), (40, 70, 150, 255)]
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rim
            elif x % 4 == 0 or y % 4 == 0:
                px[x, y] = line
            else:
                px[x, y] = cell[2] if (x % 4 == 1 and y % 4 == 1) else cell[1] if (x + y) % 3 else cell[0]
    return img


def solar_side():
    """Its edge: a steel frame (the panel's three pixels are the bottom of the texture)."""
    img = casing(5000, rivets=False)
    px = img.load()
    for x in range(16):
        px[x, 13] = (40, 70, 150, 255)
    return img


def telegraph_top():
    """The Radio Telegraph's desk (v0.51.0): dark walnut with a grain, a steel rim."""
    rnd = random.Random(5100)
    img = blank()
    px = img.load()
    wood = [(58, 34, 20, 255), (74, 45, 26, 255), (90, 56, 33, 255)]
    rim = (118, 123, 134, 255)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rim
            else:
                grain = (y * 3 + (x // 5)) % 7
                px[x, y] = wood[0] if grain == 0 else wood[2] if rnd.random() < 0.12 else wood[1]
    return img


def telegraph_side():
    """Its edge (the bottom four rows show): walnut between two steel bands."""
    img = telegraph_top()
    px = img.load()
    for x in range(16):
        px[x, 12] = (156, 162, 175, 255)
        px[x, 15] = (60, 63, 71, 255)
    return img


def telegraph_brass():
    rnd = random.Random(5101)
    img = blank()
    px = img.load()
    shades = [(150, 112, 40, 255), (190, 150, 60, 255), (224, 184, 84, 255)]
    for y in range(16):
        for x in range(16):
            px[x, y] = shades[2] if y % 4 == 0 else shades[0] if rnd.random() < 0.2 else shades[1]
    return img


def telegraph_knob():
    img = blank((24, 24, 28, 255))
    px = img.load()
    for x in range(16):
        px[x, 0] = (70, 70, 78, 255)
    return img


def telegraph_sounder():
    """The sounder's steel case."""
    return casing(5102, rivets=False)


def telegraph_lamp(lit):
    """The sounder's front: steel, with a round amber lamp (lit when a message arrives)."""
    img = casing(5102, rivets=False)
    px = img.load()
    glass = [(255, 240, 180, 255), (255, 200, 80, 255), (214, 140, 30, 255)] if lit \
        else [(140, 100, 30, 255), (100, 70, 20, 255), (70, 46, 12, 255)]
    for y in range(6, 12):
        for x in range(3, 13):
            d = (x - 7.5) ** 2 + (y - 8.5) ** 2
            if d <= 5.5:
                px[x, y] = glass[0] if d < 1.2 else glass[1] if d < 3.5 else glass[2]
            elif d <= 8:
                px[x, y] = (38, 40, 46, 255)
    return img


def rotator_side():
    """The Dish Rotator's gearbox side (v0.52.0; rows 4-11 show): steel with two vent slots."""
    img = casing(5200, rivets=False)
    px = img.load()
    for y in (6, 9):
        for x in range(4, 12):
            px[x, y] = O
            px[x, y + 1] = D
    return img


def _arrow(px, top):
    """A yellow arrow pointing to the top of the texture, its tip at row {top}."""
    yellow, dark = (240, 200, 40, 255), (150, 110, 20, 255)
    for i, half in enumerate((0, 1, 2, 3)):
        for x in range(8 - half - 1, 8 + half + 1):
            px[x, top + i] = yellow
    for y in range(top + 4, top + 7):
        for x in (7, 8):
            px[x, y] = yellow
    for y in range(top, top + 7):
        for x in range(16):
            if px[x, y] == yellow and (x + 1 >= 16 or px[x + 1, y] != yellow):
                px[x, y] = dark if y > top + 3 else yellow


def rotator_front():
    """Its front: the way redstone 0 points the dish."""
    img = casing(5201, rivets=False)
    _arrow(img.load(), 5)
    return img


def rotator_top():
    """Its top: an arrow towards the front."""
    img = casing(5202, rivets=False)
    _arrow(img.load(), 4)
    return img


def board_front():
    """The status board's face (v0.43.0): a dark screen in a steel frame (the rows are drawn on it)."""
    img = casing(16, rivets=False)
    px = img.load()
    for y in range(1, 15):
        for x in range(1, 15):
            px[x, y] = (24, 26, 30, 255) if 1 < x < 14 and 1 < y < 14 else O
    return img


def board_side():
    """The status board's edges and back (v0.43.0)."""
    return casing(17, rivets=False)


def counter_receiver_side(lit):
    """Incoming waves above tally marks (v0.42.0): four strokes and one across, red while it's on."""
    r = ON if lit else OFF
    img = casing(14)
    px = img.load()
    vents(img)
    for p in [(6, 3), (7, 2), (8, 2), (9, 3)]:
        px[p] = r[1]
    px[7, 4] = px[8, 4] = r[2] if lit else r[1]
    for x in (5, 7, 9, 11):
        for y in range(5, 11):
            px[x, y] = O
        px[x, 5] = D
    for (x, y) in [(4, 10), (5, 9), (6, 9), (7, 8), (8, 8), (9, 7), (10, 7), (11, 6), (12, 6)]:
        px[x, y] = r[2] if lit else r[1]
    return img


def repeater_side(lit, palette=None):
    """An amber mast with arcs passing through it: picked up on the left, sent on to the right.
    The converter uses the same picture in teal (palette = (off, on))."""
    r = (palette[1] if lit else palette[0]) if palette else (AMBER_ON if lit else AMBER_OFF)
    img = casing(7 if palette else 6)
    px = img.load()
    vents(img)
    for y in range(3, 10):
        px[7, y] = r[2]
        px[8, y] = r[1]
    px[7, 2] = px[8, 2] = r[2]
    # Both sides bow the same way, so the waves read as travelling left to right through the mast.
    for (x, y) in [(4, 4), (5, 5), (5, 6), (4, 7)]:
        px[x, y] = r[1]
    for (x, y) in [(2, 3), (3, 4), (3, 5), (3, 6), (3, 7), (2, 8)]:
        px[x, y] = r[0] if not lit else r[1]
    for (x, y) in [(10, 4), (11, 5), (11, 6), (10, 7)]:
        px[x, y] = r[1]
    for (x, y) in [(12, 3), (13, 4), (13, 5), (13, 6), (13, 7), (12, 8)]:
        px[x, y] = r[0] if not lit else r[1]
    for x in range(5, 11):
        px[x, 10] = O
    return img


def top(lit, receiver, palette=None):
    r = palette if palette else (ON if lit else OFF)
    img = casing(3 if receiver else 4, rivets=not receiver)
    px = img.load()
    for i in range(5, 11):
        px[i, 5] = O
        px[i, 10] = O
        px[5, i] = O
        px[10, i] = O
    for y in range(6, 10):
        for x in range(6, 10):
            px[x, y] = D
    if receiver:
        for (x, y) in [(7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)]:
            px[x, y] = r[1]
        px[7, 7] = px[8, 8] = r[0]
        px[8, 7] = px[7, 8] = r[2]
    else:
        # Antenna mount bolt
        px[7, 7] = H
        px[8, 7] = L
        px[7, 8] = L
        px[8, 8] = D
        for (x, y) in [(6, 6), (9, 6), (6, 9), (9, 9)]:
            px[x, y] = r[1]
    return img


def bottom():
    img = casing(5)
    px = img.load()
    for i in range(3, 13):
        px[i, 8] = D
        px[8, i] = D
    return img


def mast_post():
    img = blank()
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = L if x % 2 == 0 else D
    for x in range(16):
        px[x, 0] = O
        px[x, 7] = M
        px[x, 8] = O
        px[x, 15] = M
    for x in range(0, 16, 2):
        px[x, 4] = H
        px[x, 12] = H
    return img


def mast_lattice():
    """X bracing between the posts. Only columns 5-10 are used by the model (uv 5..11)."""
    img = blank()
    px = img.load()
    for half in (0, 8):
        for r in range(8):
            a = 5 + round(r * 5 / 7)
            b = 10 - round(r * 5 / 7)
            px[a, half + r] = L
            px[b, half + r] = M
        for x in range(5, 11):
            px[x, half] = D
    return img


def platform_deck():
    """v0.55.0: Tower Platform deck: a steel frame round a grating with see-through holes."""
    img = blank()
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            bar = x % 3 == 0 or y % 3 == 0
            if edge:
                px[x, y] = O if (x + y) % 5 else D
            elif bar:
                px[x, y] = L if (x + y) % 2 else M
    for i in range(1, 15):
        px[i, 1] = H
        px[1, i] = H
    return img


def platform_rail():
    """v0.55.0: Tower Platform rail: posts at the ends and middle, a top rail and a mid rail."""
    img = blank()
    px = img.load()
    for y in range(16):
        for x in (0, 1, 7, 8, 14, 15):
            px[x, y] = L if x in (0, 7, 14) else D
    for x in range(16):
        px[x, 2] = H
        px[x, 3] = M
        px[x, 9] = L
        px[x, 10] = D
    return img


def antenna_tip(lit, amber=False, teal=False, magenta=False):
    """Warning light at the top of the antenna: red on a transmitter, amber on a repeater, teal on a converter."""
    img = blank()
    px = img.load()
    if magenta and lit:
        core, mid, rim = (255, 220, 246, 255), (255, 80, 200, 255), (196, 26, 132, 255)
    elif magenta:
        core, mid, rim = (140, 40, 108, 255), (100, 22, 76, 255), (62, 12, 46, 255)
    elif teal and lit:
        core, mid, rim = (200, 255, 255, 255), (60, 226, 236, 255), (12, 150, 160, 255)
    elif teal:
        core, mid, rim = (40, 120, 126, 255), (18, 84, 90, 255), (8, 52, 56, 255)
    elif amber and lit:
        core, mid, rim = (255, 238, 176, 255), (255, 182, 40, 255), (212, 122, 10, 255)
    elif amber:
        core, mid, rim = (152, 104, 32, 255), (112, 72, 14, 255), (72, 46, 8, 255)
    elif lit:
        core, mid, rim = (255, 200, 170, 255), (255, 60, 40, 255), (200, 20, 12, 255)
    else:
        core, mid, rim = (150, 40, 34, 255), (110, 18, 14, 255), (70, 10, 8, 255)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = core if d < 2.5 else mid if d < 5.5 else rim
    return img


def compact_receiver(lit):
    """Button-sized receiver. The model reads the front from uv 5..11 x 6..10 and the edges from
    rows 14-15, the same layout as vanilla button textures."""
    r = ON if lit else OFF
    # Fill the whole texture so no face can ever sample a transparent (black) pixel.
    img = blank(M)
    px = img.load()
    for x in range(5, 11):
        px[x, 6] = H
        px[x, 9] = D
    for y in range(6, 10):
        px[5, y] = L
        px[10, y] = O
    px[5, 6] = H
    px[10, 9] = O
    # Indicator light and a little signal-strength bar graph
    px[6, 7] = r[2]
    px[6, 8] = r[1]
    for (x, y) in [(8, 8), (9, 7), (9, 8)]:
        px[x, y] = r[1] if lit else D
    px[7, 7] = px[7, 8] = D
    px[8, 7] = M2
    # Edges
    for x in range(0, 16):
        px[x, 14] = L
        px[x, 15] = D
    return img


def code_keypad(lit):
    """The Code Keypad's 8 x 10 front (uv 4..12 x 3..13): a display with a light, then 3 x 3 keys.
    Its edges come from rows 14-15, like the compact receiver's."""
    img = blank(M)
    px = img.load()
    for x in range(4, 12):
        px[x, 3] = H
        px[x, 12] = O
    for y in range(3, 13):
        px[4, y] = L
        px[11, y] = O
    px[4, 3] = H
    # Display, and its light (green while the code was accepted)
    for x in range(5, 10):
        px[x, 4] = (20, 60, 28, 255) if not lit else (70, 210, 90, 255)
    px[10, 4] = (60, 230, 80, 255) if lit else (110, 30, 26, 255)
    # 3 x 3 keys, each with a shadow under it
    for row in range(3):
        for col in range(3):
            x, y = 5 + col * 2, 6 + row * 2
            px[x, y] = H
            px[x, y + 1] = D
    # Edges
    for x in range(0, 16):
        px[x, 14] = L
        px[x, 15] = D
    return img


def compact_transmitter(lit):
    """Button-sized transmitter (v0.38.0), same layout as the compact receiver: a red light with
    broadcast arcs on both sides of it, over a little mast."""
    r = ON if lit else OFF
    img = blank(M)
    px = img.load()
    for x in range(5, 11):
        px[x, 6] = H
        px[x, 9] = D
    for y in range(6, 10):
        px[5, y] = L
        px[10, y] = O
    px[5, 6] = H
    px[10, 9] = O
    arc = r[1] if lit else D
    px[6, 7] = px[6, 8] = arc
    px[9, 7] = px[9, 8] = arc
    px[7, 7] = px[8, 7] = r[2]
    px[7, 8] = px[8, 8] = O
    for x in range(0, 16):
        px[x, 14] = L
        px[x, 15] = D
    return img


def motion_sensor(lit):
    """Button-sized motion sensor (v0.39.0), same layout as the compact receiver: a round purple
    lens in a black housing, glowing while it sees someone."""
    r = PURPLE_ON if lit else PURPLE_OFF
    img = blank(M)
    px = img.load()
    for x in range(5, 11):
        px[x, 6] = H
        px[x, 9] = D
    for y in range(6, 10):
        px[5, y] = L
        px[10, y] = O
    px[5, 6] = H
    px[10, 9] = O
    px[6, 7] = px[6, 8] = O
    px[9, 7] = px[9, 8] = O
    px[7, 7] = r[2]
    px[8, 7] = px[7, 8] = r[1]
    px[8, 8] = r[0]
    for x in range(0, 16):
        px[x, 14] = L
        px[x, 15] = D
    return img


LIGHTS = {
    "idle": ((34, 52, 38, 255), (50, 74, 54, 255)),       # unlit
    "heard": ((70, 210, 70, 255), (190, 255, 170, 255)),  # green: a receiver heard the press
    "missed": ((200, 30, 20, 255), (255, 140, 120, 255)), # red: nobody in range
}


MULTI_BUTTONS = [(6, 7), (9, 7), (6, 10), (9, 10)]  # top-left pixel of each 2x2 button


def scanner_arrow():
    """The Signal Scanner's on-screen arrow (pointing up; the game rotates it): bright green with
    a dark outline so it shows against sky and ground alike."""
    img = blank()
    px = img.load()
    fill, light, edge = (120, 240, 110, 255), (200, 255, 190, 255), (16, 40, 18, 255)
    shape = set()
    # Head: a triangle from row 1 (tip) to row 8, then a shaft down to row 14
    for y in range(1, 9):
        half = y - 1
        for x in range(8 - half - 1, 8 + half + 1):
            if 0 <= x < 16:
                shape.add((x, y))
    for y in range(9, 15):
        for x in range(6, 10):
            shape.add((x, y))
    for (x, y) in shape:
        px[x, y] = fill
    for (x, y) in list(shape):
        if x <= 7 and (x + 1, y) in shape and (x - 1, y) not in shape:
            px[x, y] = light  # a shine down the left edge
    for y in range(16):
        for x in range(16):
            if (x, y) in shape:
                continue
            if any((x + dx, y + dy) in shape for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = edge
    return img


def satellite_panel():
    """A satellite's solar wing (v0.61.0): dark blue cells, gold rim."""
    img = blank()
    px = img.load()
    rim = (212, 168, 48, 255)
    cell = [(18, 34, 92, 255), (26, 48, 120, 255), (44, 76, 160, 255)]
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rim
            elif x % 5 == 0 or y % 5 == 0:
                px[x, y] = (150, 160, 180, 255)
            else:
                px[x, y] = cell[2] if (x % 5 == 1 and y % 5 == 1) else cell[1] if (x * 3 + y) % 4 else cell[0]
    return img


def satellite_foil():
    """A satellite's body (v0.61.0): crinkled gold foil."""
    rng = random.Random(6101)
    img = blank()
    px = img.load()
    shades = [(150, 110, 24, 255), (196, 150, 40, 255), (226, 184, 64, 255), (248, 216, 110, 255)]
    for y in range(16):
        for x in range(16):
            px[x, y] = shades[min(3, max(0, int(rng.gauss(1.6, 0.9))))]
    return img


def launch_pad_top():
    """The Launch Pad's deck (v0.61.0): concrete with yellow and black hazard stripes round a scorched ring."""
    import math
    rng = random.Random(6102)
    img = blank()
    px = img.load()
    for y in range(16):
        for x in range(16):
            g = 132 + rng.randint(-8, 8)
            px[x, y] = (g, g, g + 4, 255)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15) or x in (1, 14) or y in (1, 14):
                px[x, y] = (236, 190, 30, 255) if (x + y) // 2 % 2 == 0 else (30, 30, 32, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.0 <= d <= 4.2:
                px[x, y] = (60, 58, 56, 255)
            elif d < 3.0:
                g = 46 + rng.randint(-6, 6)
                px[x, y] = (g, g - 2, g - 4, 255)
    return img


def launch_pad_side():
    """Its edge: steel with a hazard band."""
    img = casing(6103, rivets=True)
    px = img.load()
    for x in range(16):
        for y in (12, 13):
            px[x, y] = (236, 190, 30, 255) if (x + y) // 2 % 2 == 0 else (30, 30, 32, 255)
    return img


def ticker_tape():
    """v0.63.0: the Ticker Tape Printer's paper: off-white, with a row of printed marks down the middle."""
    rnd = random.Random(6301)
    img = blank((236, 232, 214, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.12:
                px[x, y] = (222, 216, 196, 255)
    for y in range(1, 16, 2):
        for x in range(4, 12):
            if rnd.random() < 0.55:
                px[x, y] = (40, 40, 46, 255)
    return img


def ticker_book_cover():
    """v0.63.0: the logbook lying on the printer: brown leather with a gold band."""
    rnd = random.Random(6302)
    img = blank()
    px = img.load()
    for y in range(16):
        for x in range(16):
            base = (98, 58, 30) if rnd.random() < 0.8 else (84, 48, 24)
            px[x, y] = base + (255,)
    for x in range(16):
        px[x, 0] = (60, 34, 16, 255)
        px[x, 15] = (60, 34, 16, 255)
        px[x, 11] = (214, 172, 64, 255)
    return img


def ticker_book_pages():
    """v0.63.0: the logbook's page edges."""
    img = blank((232, 226, 206, 255))
    px = img.load()
    for y in range(0, 16, 2):
        for x in range(16):
            px[x, y] = (206, 198, 176, 255)
    return img



# v0.65.0: the Radar Dish and the Radar Screen
GREEN_OFF = [(14, 52, 26, 255), (24, 84, 40, 255), (40, 120, 60, 255)]
GREEN_ON = [(40, 170, 80, 255), (90, 240, 130, 255), (190, 255, 210, 255)]


def radar_dish_side(lit):
    """The cabinet's side (only rows 7-15 show, it's 9 pixels high): a round green scope with its sweep line
    and a blip, a lamp that glows red while it sends, and vents."""
    img = casing(31, rivets=False)
    px = img.load()
    for x in range(16):
        px[x, 7] = O
    for x in range(1, 15):
        px[x, 8] = H
    g = GREEN_ON if lit else GREEN_OFF
    cx, cy = 6.0, 11.5
    for y in range(9, 15):
        for x in range(1, 12):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 2.4:
                px[x, y] = (10, 30, 16, 255)
            elif d <= 3.2:
                px[x, y] = O
    px[6, 11] = px[7, 10] = g[1]
    px[8, 10] = g[2]
    px[5, 12] = g[2]  # a blip
    r = ON if lit else OFF
    px[12, 10] = px[13, 10] = r[2] if lit else r[1]
    px[12, 11] = px[13, 11] = r[1] if lit else r[0]
    for x in range(11, 15):
        px[x, 13] = O
    return img


def radar_dish_top():
    """The turntable's footprint: a ring of bolts round a dark bearing."""
    img = casing(32, rivets=False)
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if 5.2 <= d <= 6.2:
                px[x, y] = O
            elif d < 2.2:
                px[x, y] = D
    for (x, y) in [(7, 2), (12, 7), (8, 13), (3, 8), (4, 4), (11, 4), (11, 11), (4, 11)]:
        px[x, y] = H
    return img


def radar_reflector():
    """The dish's reflector: a light grey perforated mesh (dark holes on a grid), lighter in the middle."""
    rnd = random.Random(33)
    img = blank((176, 182, 190, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            base = 168 + int(14 * (1 - abs(x - 7.5) / 8)) + rnd.randint(-4, 4)
            px[x, y] = (base, base + 4, base + 10, 255)
            if x % 2 == 1 and y % 2 == 1:
                px[x, y] = (96, 102, 112, 255)
    for i in range(16):
        px[i, 0] = px[i, 15] = (120, 126, 136, 255)
    return img


def radar_reflector_back():
    """The reflector's back: dark ribs."""
    img = blank((70, 74, 82, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 5 == 0:
                px[x, y] = (46, 49, 56, 255)
    return img


def radar_screen_front(lit=False):
    """The Radar Screen's face: a round black glass scope in a steel bezel (the picture is drawn on it).
    {lit}: for the item, the scope already showing rings, a sweep and blips."""
    img = casing(34, rivets=True)
    px = img.load()
    for y in range(1, 15):
        for x in range(1, 15):
            if 1 < x < 14 and 1 < y < 14:
                px[x, y] = (20, 24, 22, 255)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.0
            d = (dx * dx + dy * dy) ** 0.5
            if 1 < x < 14 and 1 < y < 14 and d <= 5.9:
                px[x, y] = (6, 18, 10, 255)
                if lit:
                    px[x, y] = (6, 32, 14, 255)
                    if 5.0 <= d <= 5.9:
                        px[x, y] = (50, 190, 90, 255)
                    elif 2.6 <= d <= 3.3:
                        px[x, y] = (24, 96, 46, 255)
    if lit:
        for (x, y) in [(8, 6), (9, 5), (10, 4), (11, 3)]:  # the sweep line, up and right
            px[x, y] = (150, 255, 170, 255)
        for (x, y) in [(9, 6), (10, 5), (10, 6), (11, 5)]:  # its trail
            px[x, y] = (30, 110, 50, 255)
        px[7, 7] = (180, 255, 190, 255)  # the dish in the middle
        px[5, 4] = (255, 80, 60, 255)    # blips: hostile, peaceful, a player
        px[10, 9] = (90, 220, 255, 255)
        px[5, 9] = (255, 240, 140, 255)
    return img

blocks = {
    "ticker_tape": ticker_tape(),
    "ticker_book_cover": ticker_book_cover(),
    "ticker_book_pages": ticker_book_pages(),
    **{f"radio_battery_side_{n}": battery_side(n) for n in range(5)},
    "radio_battery_top": battery_top(),
    "solar_panel_top": solar_top(),
    "solar_panel_side": solar_side(),
    "dish_rotator_side": rotator_side(),
    "dish_rotator_front": rotator_front(),
    "dish_rotator_top": rotator_top(),
    "radio_telegraph_top": telegraph_top(),
    "radio_telegraph_side": telegraph_side(),
    "radio_telegraph_brass": telegraph_brass(),
    "radio_telegraph_knob": telegraph_knob(),
    "radio_telegraph_sounder": telegraph_sounder(),
    "radio_telegraph_lamp": telegraph_lamp(False),
    "radio_telegraph_lamp_on": telegraph_lamp(True),
    "compact_radio_receiver": compact_receiver(False),
    "compact_radio_receiver_on": compact_receiver(True),
    "compact_radio_transmitter": compact_transmitter(False),
    "compact_radio_transmitter_on": compact_transmitter(True),
    "code_keypad": code_keypad(False),
    "radio_motion_sensor": motion_sensor(False),
    "radio_motion_sensor_on": motion_sensor(True),
    "code_keypad_on": code_keypad(True),
    "radio_transmitter_side": transmitter_side(False),
    "radio_transmitter_side_on": transmitter_side(True),
    "radio_transmitter_top": top(False, receiver=False),
    "radio_transmitter_top_on": top(True, receiver=False),
    "radio_toggle_receiver_side": toggle_receiver_side(False),
    "radio_toggle_receiver_side_on": toggle_receiver_side(True),
    "radio_inverted_receiver_side": inverted_receiver_side(False),
    "radio_logic_receiver_side": logic_receiver_side(False),
    "radio_speaker_side": speaker_side(False),
    "radio_speaker_side_on": speaker_side(True),
    "radio_status_board_front": board_front(),
    "radio_status_board_side": board_side(),
    "radio_counter_receiver_side": counter_receiver_side(False),
    "radio_counter_receiver_side_on": counter_receiver_side(True),
    "radio_signal_recorder_side": recorder_side(False),
    "radio_signal_recorder_side_on": recorder_side(True),
    "radio_signal_recorder_top": top(False, receiver=False),
    "radio_signal_recorder_top_on": top(True, receiver=False),
    "radio_jammer_side": jammer_side(False),
    "radio_jammer_side_on": jammer_side(True),
    "radio_jammer_top": top(False, receiver=False, palette=MAGENTA_OFF),
    "radio_jammer_top_on": top(True, receiver=False, palette=MAGENTA_ON),
    "jammer_tip": antenna_tip(False, magenta=True),
    "jammer_tip_on": antenna_tip(True, magenta=True),
    "radio_lamp_frame": lamp_frame(),
    "radio_lamp_glass": lamp_glass(),
    "aviation_beacon_red": beacon_dome("red"),
    "aviation_beacon_white": beacon_dome("white"),
    "aviation_beacon_base": beacon_base(),
    "bundled_cable": bundled_cable(),  # v0.54.0
    "bundled_transmitter_side": bundled_side(False, receiver=False),
    "bundled_transmitter_side_on": bundled_side(True, receiver=False),
    "bundled_transmitter_top": top(False, receiver=False),
    "bundled_transmitter_top_on": top(True, receiver=False),
    "bundled_receiver_side": bundled_side(False, receiver=True),
    "bundled_receiver_side_on": bundled_side(True, receiver=True),
    "cable_tap_in": cable_tap_side(False),
    "cable_tap_out": cable_tap_side(True),
    "cable_tap_band": cable_tap_band(),
    "radio_jukebox_side": jukebox_side(False),  # v0.60.0
    "radio_jukebox_side_on": jukebox_side(True),
    "radio_jukebox_top": top(False, receiver=False),
    "radio_jukebox_top_on": top(True, receiver=False),
    "radio_weather_station_side": weather_station_side(False),  # v0.53.0
    "radio_weather_station_side_on": weather_station_side(True),
    "radio_weather_station_top": top(False, receiver=False),
    "radio_weather_station_top_on": top(True, receiver=False),
    "radio_timer_transmitter_side": timer_transmitter_side(False),
    "radio_timer_transmitter_side_on": timer_transmitter_side(True),
    "radio_timer_transmitter_top": top(False, receiver=False),
    "radio_timer_transmitter_top_on": top(True, receiver=False),
    "radio_delay_receiver_side": delay_receiver_side(False),
    "radio_delay_receiver_side_on": delay_receiver_side(True),
    "radio_logic_receiver_side_on": logic_receiver_side(True),
    "radio_inverted_receiver_side_on": inverted_receiver_side(True),
    "radio_receiver_side": receiver_side(False),
    "radio_receiver_side_on": receiver_side(True),
    "radio_receiver_top": top(False, receiver=True),
    "radio_receiver_top_on": top(True, receiver=True),
    "radio_bottom": bottom(),
    "radio_mast_post": mast_post(),
    "radio_mast_lattice": mast_lattice(),
    "tower_platform_deck": platform_deck(),
    "tower_platform_rail": platform_rail(),
    "antenna_tip": antenna_tip(False),
    "antenna_tip_on": antenna_tip(True),
    "radio_repeater_side": repeater_side(False),
    "radio_repeater_side_on": repeater_side(True),
    "radio_repeater_top": top(False, receiver=False, palette=AMBER_OFF),
    "radio_repeater_top_on": top(True, receiver=False, palette=AMBER_ON),
    "repeater_tip": antenna_tip(False, amber=True),
    "repeater_tip_on": antenna_tip(True, amber=True),
    "radio_converter_side": repeater_side(False, palette=(TEAL_OFF, TEAL_ON)),
    "radio_converter_side_on": repeater_side(True, palette=(TEAL_OFF, TEAL_ON)),
    "radio_converter_top": top(False, receiver=False, palette=TEAL_OFF),
    "radio_converter_top_on": top(True, receiver=False, palette=TEAL_ON),
    "converter_tip": antenna_tip(False, teal=True),
    "converter_tip_on": antenna_tip(True, teal=True),
    "launch_pad_top": launch_pad_top(),  # v0.61.0
    "launch_pad_side": launch_pad_side(),
    "satellite_panel": satellite_panel(),
    "satellite_foil": satellite_foil(),
    "radar_dish_side": radar_dish_side(False),  # v0.65.0
    "radar_dish_side_on": radar_dish_side(True),
    "radar_dish_top": radar_dish_top(),
    "radar_reflector": radar_reflector(),
    "radar_reflector_back": radar_reflector_back(),
    "radar_screen_front": radar_screen_front(),
    "radar_screen_front_item": radar_screen_front(lit=True),
}
# v0.65.0: the items' textures are painted by tools/make_item_models.py (3D models), not here.

for folder, textures in ((BLOCK, blocks),):
    folder.mkdir(parents=True, exist_ok=True)
    for name, img in textures.items():
        img.save(folder / f"{name}.png")
GUI = ITEM.parent / "gui"
GUI.mkdir(parents=True, exist_ok=True)
scanner_arrow().save(GUI / "scanner_arrow.png")
MISC = ITEM.parent / "misc"
MISC.mkdir(parents=True, exist_ok=True)
beacon_glow().save(MISC / "aviation_beacon_glow.png")

def radio_operator():
    """v0.56.0: the Radio Operator villager's overlay (64x64, the villager model's layout): headphones
    with a microphone on the hat layer, and an olive vest with a little radio on its back on the jacket."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(56)
    band, band_hi = (40, 40, 44, 255), (72, 72, 80, 255)
    cup_rim, cup, cup_hi = (26, 26, 30, 255), (56, 56, 64, 255), (92, 92, 104, 255)
    led = (214, 44, 30, 255)
    vest, vest_dark, vest_light, seam = (96, 104, 58, 255), (78, 86, 46, 255), (114, 124, 70, 255), (60, 66, 36, 255)
    pocket, flap = (110, 120, 66, 255), (66, 72, 40, 255)
    gold, gold_dark = (232, 196, 64, 255), (176, 136, 36, 255)

    # Headphones. Hat layer: top face u40-47 v0-7; right side u32-39, front u40-47, left u48-55 (v8-17).
    for u in range(40, 48):
        px[u, 3] = band_hi
        px[u, 4] = band
    for u0 in (32, 48):  # the band down each side to the ear cup
        for v in range(8, 11):
            px[u0 + 3, v] = band_hi
            px[u0 + 4, v] = band
        for u in range(u0 + 2, u0 + 6):
            for v in range(11, 15):
                edge = u in (u0 + 2, u0 + 5) or v in (11, 14)
                px[u, v] = cup_rim if edge else cup
        px[u0 + 3, 12] = cup_hi
        px[u0 + 4, 13] = led if u0 == 48 else cup_hi
    # The microphone boom, from the right ear cup down and round to the mouth.
    px[37, 15] = band
    for u in range(38, 42):
        px[u, 16] = band
    px[42, 16] = cup_rim

    # The vest. Jacket layer: top u6-13 v38-43; right u0-5, front u6-13, left u14-19, back u20-27 (v44-63).
    def fill(u, v, base=vest):
        r = rng.random()
        px[u, v] = vest_dark if r < 0.18 else vest_light if r > 0.88 else base
    for u in range(6, 14):
        for v in range(38, 44):
            if not (8 <= u <= 11 and 40 <= v <= 42):  # the neck
                fill(u, v)
    for u in range(0, 28):
        for v in range(44, 55):
            fill(u, v)
        px[u, 54] = seam  # the hem
    for v in range(44, 51):  # open down the front, to a V
        width = 2 if v < 49 else 1 if v < 50 else 0
        for u in range(10 - width, 10 + width):
            px[u, v] = (0, 0, 0, 0)
        if width:
            px[10 - width - 1, v] = seam
            px[10 + width, v] = seam
    for u0 in (7, 11):  # two pockets
        for u in (u0, u0 + 1):
            px[u, 51] = flap
            px[u, 52] = pocket
            px[u, 53] = pocket
    px[12, 46], px[12, 47], px[11, 47] = gold, gold_dark, gold  # a lightning badge
    for v in range(44, 55):  # side seams
        px[0, v] = seam
        px[19, v] = seam
    # A little radio on the back, with its light.
    for u in range(22, 26):
        for v in range(45, 51):
            px[u, v] = cup_rim if u in (22, 25) or v in (45, 50) else cup
    px[23, 46], px[24, 46] = cup_hi, led
    px[23, 48], px[24, 48] = band_hi, band_hi
    for v in range(44, 46):
        px[25, v] = band
    return img


VILLAGER = ROOT / "entity" / "villager" / "profession"
ZOMBIE_VILLAGER = ROOT / "entity" / "zombie_villager" / "profession"
for folder in (VILLAGER, ZOMBIE_VILLAGER):
    folder.mkdir(parents=True, exist_ok=True)
    radio_operator().save(folder / "radio_operator.png")
print(f"wrote {len(blocks)} block textures to {ROOT}")
