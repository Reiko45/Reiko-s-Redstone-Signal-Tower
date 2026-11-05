"""Draws the 16x16 textures for Reiko's Redstone Signal Tower.

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


def top(lit, receiver):
    r = ON if lit else OFF
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


def antenna_tip(lit):
    """Aviation warning light at the top of the transmitter's antenna."""
    img = blank()
    px = img.load()
    if lit:
        core, mid, rim = (255, 200, 170, 255), (255, 60, 40, 255), (200, 20, 12, 255)
    else:
        core, mid, rim = (150, 40, 34, 255), (110, 18, 14, 255), (70, 10, 8, 255)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = core if d < 2.5 else mid if d < 5.5 else rim
    return img


def tuner_item():
    """A handheld radio with a stubby antenna, green display and a red tuning knob."""
    img = blank()
    px = img.load()
    body_o, body, body_l = (30, 32, 38, 255), (70, 74, 84, 255), (104, 110, 122, 255)
    # Antenna
    for y in range(0, 5):
        px[4, y] = (150, 156, 168, 255)
        px[5, y] = (90, 94, 104, 255)
    px[4, 0] = px[5, 0] = (220, 50, 40, 255)
    # Body outline and fill
    for y in range(4, 16):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (4, 15)
            px[x, y] = body_o if edge else body
    for y in range(5, 15):
        px[4, y] = body_l
    for x in range(4, 12):
        px[x, 5] = body_l
    # Display
    for y in range(6, 10):
        for x in range(5, 12):
            px[x, y] = (40, 70, 44, 255) if y in (6, 9) or x in (5, 11) else (120, 220, 110, 255)
    for x in (6, 8, 10):
        px[x, 8] = (40, 110, 44, 255)
    # Speaker grille and tuning knob
    for x in (5, 7):
        px[x, 11] = body_o
        px[x, 13] = body_o
    for (x, y) in [(9, 11), (10, 11), (9, 12), (10, 12)]:
        px[x, y] = (210, 40, 30, 255)
    px[9, 11] = (255, 130, 110, 255)
    px[10, 13] = (40, 42, 48, 255)
    return img


blocks = {
    "radio_transmitter_side": transmitter_side(False),
    "radio_transmitter_side_on": transmitter_side(True),
    "radio_transmitter_top": top(False, receiver=False),
    "radio_transmitter_top_on": top(True, receiver=False),
    "radio_receiver_side": receiver_side(False),
    "radio_receiver_side_on": receiver_side(True),
    "radio_receiver_top": top(False, receiver=True),
    "radio_receiver_top_on": top(True, receiver=True),
    "radio_bottom": bottom(),
    "radio_mast_post": mast_post(),
    "radio_mast_lattice": mast_lattice(),
    "antenna_tip": antenna_tip(False),
    "antenna_tip_on": antenna_tip(True),
}
items = {"frequency_tuner": tuner_item()}

for folder, textures in ((BLOCK, blocks), (ITEM, items)):
    folder.mkdir(parents=True, exist_ok=True)
    for name, img in textures.items():
        img.save(folder / f"{name}.png")
print(f"wrote {len(blocks)} block and {len(items)} item textures to {ROOT}")
