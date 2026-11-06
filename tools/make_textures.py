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


def wall_redstone_item():
    """A strip of wall with redstone dust running up it and branching sideways."""
    img = blank()
    px = img.load()
    stone_l, stone, stone_d = (150, 150, 150, 255), (120, 120, 120, 255), (90, 90, 90, 255)
    for y in range(1, 15):
        for x in range(2, 6):
            px[x, y] = stone if (x + y) % 3 else stone_l
        px[5, y] = stone_d
    dust = [(170, 12, 8, 255), (230, 30, 20, 255), (255, 90, 70, 255)]
    for y in range(2, 14):
        px[9, y] = dust[1]
        px[10, y] = dust[0]
    for x in range(7, 14):
        px[x, 7] = dust[1]
        px[x, 8] = dust[0]
    px[9, 2] = px[12, 7] = dust[2]
    px[9, 7] = px[10, 7] = dust[2]
    return img


LIGHTS = {
    "idle": ((34, 52, 38, 255), (50, 74, 54, 255)),       # unlit
    "heard": ((70, 210, 70, 255), (190, 255, 170, 255)),  # green: a receiver heard the press
    "missed": ((200, 30, 20, 255), (255, 140, 120, 255)), # red: nobody in range
}


def remote_item(light="idle"):
    """A garage-door style remote: dark fob, one big red button, a status light and a key ring."""
    img = blank()
    px = img.load()
    o, body, body_l = (26, 28, 34, 255), (58, 62, 72, 255), (92, 98, 112, 255)
    # Key ring loop at the top
    for (x, y) in [(7, 0), (8, 0), (6, 1), (9, 1), (6, 2), (9, 2)]:
        px[x, y] = (170, 176, 188, 255)
    # Body with rounded corners
    for y in range(3, 16):
        for x in range(4, 12):
            corner = (y in (3, 15)) and x in (4, 11)
            if corner:
                continue
            edge = x in (4, 11) or y in (3, 15)
            px[x, y] = o if edge else body
    for y in range(4, 15):
        px[5, y] = body_l
    for x in range(5, 11):
        px[x, 4] = body_l
    # Status light (2x2, top right)
    base, shine = LIGHTS[light]
    for (x, y) in [(9, 4), (10, 4), (9, 5), (10, 5)]:
        px[x, y] = base
    px[9, 4] = shine
    # Big round red button
    red_d, red, red_l = (140, 18, 14, 255), (220, 36, 26, 255), (255, 120, 100, 255)
    for (x, y) in [(7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9)]:
        px[x, y] = red
    px[7, 7] = red_l
    px[7, 6] = red_l
    px[9, 8] = red_d
    px[8, 9] = red_d
    # Two small grey buttons
    for (x, y) in [(6, 12), (7, 12), (9, 12), (10, 12)]:
        px[x, y] = (140, 146, 158, 255)
    for (x, y) in [(6, 13), (7, 13), (9, 13), (10, 13)]:
        px[x, y] = (90, 94, 104, 255)
    return img


MULTI_BUTTONS = [(6, 7), (9, 7), (6, 10), (9, 10)]  # top-left pixel of each 2x2 button


def multi_remote_item(light="idle"):
    """Like the remote, but with room for four buttons in a 2x2 grid. The buttons themselves are
    separate layers (see multi_remote_button) so the game can color each one."""
    img = remote_item(light)
    px = img.load()
    body, socket = (58, 62, 72, 255), (36, 38, 46, 255)
    for y in range(6, 14):
        for x in range(6, 11):
            px[x, y] = body
    # A dark socket under each button, showing at its bottom-right edge
    for (x, y) in MULTI_BUTTONS:
        for (dx, dy) in [(2, 1), (1, 2), (2, 2)]:
            px[x + dx, y + dy] = socket
    return img


def multi_remote_button(index):
    """Button `index` (1-4) in white and greys: the game multiplies it by the button's dye color,
    so the top-left pixel shows the full color and the rest is shaded a little darker."""
    img = blank()
    px = img.load()
    x, y = MULTI_BUTTONS[index - 1]
    shades = {(0, 0): 255, (1, 0): 225, (0, 1): 225, (1, 1): 180}
    for (dx, dy), v in shades.items():
        px[x + dx, y + dy] = (v, v, v, 255)
    return img


def pager_item(on=False):
    """A pocket pager: black body, a stubby antenna, a belt-clip ridge, a small LCD (lit green with
    a message while its frequency is on) and three keys."""
    img = blank()
    px = img.load()
    o, body, body_l = (20, 22, 26, 255), (44, 47, 54, 255), (74, 79, 90, 255)
    # Antenna stub, top left
    for y in (2, 3, 4):
        px[3, y] = (110, 114, 124, 255)
    px[3, 2] = (150, 154, 164, 255)
    # Body with rounded corners
    for y in range(5, 14):
        for x in range(2, 14):
            if (y in (5, 13)) and x in (2, 13):
                continue
            px[x, y] = o if (x in (2, 13) or y in (5, 13)) else body
    for x in range(3, 13):
        px[x, 6] = body_l
    # LCD, 8x3
    lcd_bg = (130, 225, 115, 255) if on else (46, 64, 48, 255)
    lcd_px = (24, 60, 26, 255) if on else (40, 54, 42, 255)
    for y in range(7, 10):
        for x in range(4, 12):
            px[x, y] = lcd_bg
    if on:
        # One short line of "text" across the middle of the screen
        for x in (5, 6, 8, 9, 10):
            px[x, 8] = lcd_px
    # Status LED, right of the LCD
    px[12, 7] = (255, 90, 60, 255) if on else (70, 40, 38, 255)
    # Three keys
    for x in (4, 5, 7, 8, 10, 11):
        px[x, 11] = (150, 156, 168, 255)
        px[x, 12] = (96, 100, 110, 255)
    return img


def scanner_item():
    """A yellow handheld direction-finder: a round radar screen with a sweep line and blips, an
    antenna with a red tip, and a grip."""
    img = blank()
    px = img.load()
    o = (52, 38, 8, 255)
    body, body_d, body_l = (226, 178, 40, 255), (178, 132, 22, 255), (248, 214, 96, 255)
    grip, grip_l = (46, 48, 54, 255), (78, 82, 92, 255)
    # Antenna, top right, with a red tip
    for y in (1, 2, 3):
        px[12, y] = (128, 132, 142, 255)
    px[12, 0] = (230, 50, 40, 255)
    # Grip
    for y in range(11, 16):
        for x in range(6, 10):
            px[x, y] = o if x in (6, 9) or y == 15 else (grip_l if x == 7 else grip)
    # Body
    for y in range(3, 12):
        for x in range(2, 14):
            if (y in (3, 11)) and x in (2, 13):
                continue
            edge = x in (2, 13) or y in (3, 11)
            px[x, y] = o if edge else (body_l if y == 4 else body_d if y == 10 else body)
    # Round radar screen
    screen, ring, sweep, blip = (18, 46, 26, 255), (40, 86, 48, 255), (110, 240, 120, 255), (200, 255, 190, 255)
    cx, cy = 7.5, 6.5
    for y in range(4, 10):
        for x in range(4, 12):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 3.2:
                px[x, y] = ring if d > 2.4 else screen
    for (x, y) in [(8, 6), (9, 5), (10, 4)]:
        px[x, y] = sweep
    px[5, 7] = blip
    px[9, 8] = blip
    px[7, 6] = (60, 160, 70, 255)
    return img


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


blocks = {
    "compact_radio_receiver": compact_receiver(False),
    "compact_radio_receiver_on": compact_receiver(True),
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
items = {"frequency_tuner": tuner_item(), "wall_redstone": wall_redstone_item(), "remote_control": remote_item(),
         "remote_control_heard": remote_item("heard"), "remote_control_missed": remote_item("missed"),
         "multi_remote": multi_remote_item(),
         "multi_remote_heard": multi_remote_item("heard"), "multi_remote_missed": multi_remote_item("missed"),
         **{f"multi_remote_button_{i}": multi_remote_button(i) for i in range(1, 5)},
         "pager": pager_item(), "pager_on": pager_item(True), "signal_scanner": scanner_item()}

for folder, textures in ((BLOCK, blocks), (ITEM, items)):
    folder.mkdir(parents=True, exist_ok=True)
    for name, img in textures.items():
        img.save(folder / f"{name}.png")
GUI = ITEM.parent / "gui"
GUI.mkdir(parents=True, exist_ok=True)
scanner_arrow().save(GUI / "scanner_arrow.png")
print(f"wrote {len(blocks)} block and {len(items)} item textures to {ROOT}")
