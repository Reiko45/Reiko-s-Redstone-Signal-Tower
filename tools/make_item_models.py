"""Builds the 3D item models (v0.65.0) for Reiko's Redstone Signal Tower: the hand-held radios, the
antennas, the Coverage Map and Wall Redstone, each a small model of boxes with its own painted texture.

Usage: python3 tools/make_item_models.py
Writes models/item/<name>.json and textures/item/model/<name>.png under src/main/resources/assets/reikos_signal_tower.
Needs Pillow. Each model is a list of boxes; every face of every box gets its own patch of the item's
texture (two pixels per model pixel), painted by a "material": a function that draws the patch as seen
from outside the face (left to right, top to bottom). The patches are packed into one texture per item.
"""
import json
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/reikos_signal_tower"
MODELS = ROOT / "models" / "item"
TEXTURES = ROOT / "textures" / "item" / "model"
MOD = "reikos_signal_tower"
DENSITY = 2  # texture pixels per model pixel
FACES = ("north", "south", "east", "west", "up", "down")
GLOW = {"block_light": 15, "sky_light": 15}


# ---------------------------------------------------------------- colors and materials

def rgb(c, a=255):
    return (c[0], c[1], c[2], a)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def solid(color, noise=0.05, edge=0.78, seed=1, bevel=True):
    """A plain surface: the color with a little noise and a darker 1-pixel rim (a bevel), lighter at the top-left."""
    def paint(w, h, face):
        rnd = random.Random(seed * 7919 + w * 31 + h)
        img = Image.new("RGBA", (w, h))
        px = img.load()
        for y in range(h):
            for x in range(w):
                f = 1.0 + rnd.uniform(-noise, noise)
                px[x, y] = rgb(shade(color, f))
        if bevel and w > 2 and h > 2:
            for x in range(w):
                px[x, h - 1] = rgb(shade(color, edge))
                px[x, 0] = rgb(shade(color, 1.0 + (1 - edge) * 0.6))
            for y in range(h):
                px[w - 1, y] = rgb(shade(color, edge))
                px[0, y] = rgb(shade(color, 1.0 + (1 - edge) * 0.4))
        return img
    return paint


def glossy(color, seed=2):
    """A screen or lens: a soft diagonal shine over the color."""
    def paint(w, h, face):
        img = solid(color, noise=0.02, edge=0.7, seed=seed)(w, h, face)
        px = img.load()
        for y in range(h):
            for x in range(w):
                if 0 < x < w - 1 and 0 < y < h - 1 and (x + y) in (2, 3) and w > 4:
                    px[x, y] = rgb(shade(color, 1.35))
        return img
    return paint


def grille(base, slot, horizontal=True, step=2, margin=1):
    """Speaker slots: dark lines across a panel."""
    def paint(w, h, face):
        img = solid(base, noise=0.03)(w, h, face)
        px = img.load()
        if horizontal:
            for y in range(margin + 1, h - margin - 1, step):
                for x in range(margin + 1, w - margin - 1):
                    px[x, y] = rgb(slot)
        else:
            for x in range(margin + 1, w - margin - 1, step):
                for y in range(margin + 1, h - margin - 1):
                    px[x, y] = rgb(slot)
        return img
    return paint


def ribbed(color, step=2):
    """Grip ridges up a side."""
    def paint(w, h, face):
        img = solid(color, noise=0.03)(w, h, face)
        px = img.load()
        for y in range(1, h - 1, step):
            for x in range(1, w - 1):
                px[x, y] = rgb(shade(color, 0.75))
        return img
    return paint


def custom(fn, base):
    """{fn} draws over a plain {base} surface: fn(draw, px, w, h)."""
    def paint(w, h, face):
        img = solid(base, noise=0.03)(w, h, face)
        fn(ImageDraw.Draw(img), img.load(), w, h)
        return img
    return paint


def texture(ref):
    """A vanilla (or the mod's) texture instead of a painted patch."""
    return ("texture", ref)


# ---------------------------------------------------------------- the model builder

class Model:
    def __init__(self, name):
        self.name = name
        self.elements = []

    def box(self, frm, to, material, faces=FACES, glow=False, tint=None, rotation=None, shade_faces=True, **per_face):
        """A box from {frm} to {to}; {material} for every face unless {per_face} names one (face=material, or None for none)."""
        element = {"from": list(frm), "to": list(to), "faces": {}}
        for face in faces:
            mat = per_face.get(face, material)
            if mat is None:
                continue
            w, h = face_size(frm, to, face)
            if w <= 0 or h <= 0:
                continue
            element["faces"][face] = {"mat": mat, "size": (w, h), "tint": tint}
        if glow:
            element["neoforge_data"] = GLOW
            element["shade"] = False
        if rotation:
            element["rotation"] = rotation
        if not shade_faces:
            element["shade"] = False
        self.elements.append(element)
        return element

    def build(self, display, gui_light="side", textures=None, overrides=None, write=True):
        """Packs every painted face into one texture and returns the model JSON (writes both)."""
        patches = []
        for element in self.elements:
            for face, entry in element["faces"].items():
                if isinstance(entry["mat"], tuple):
                    continue
                w = max(1, int(math.ceil(entry["size"][0] * DENSITY - 1e-6)))
                h = max(1, int(math.ceil(entry["size"][1] * DENSITY - 1e-6)))
                patches.append((element, face, entry, w, h))
        size = 16
        while not pack(patches, size):
            size *= 2
        atlas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        for element, face, entry, w, h in patches:
            x, y = entry["at"]
            atlas.paste(entry["mat"](w, h, face), (x, y))
        tex = f"{MOD}:item/model/{self.name}"
        refs = {"0": tex, "particle": tex}
        extra = {}
        out_elements = []
        for element in self.elements:
            out = {k: v for k, v in element.items() if k != "faces"}
            out["faces"] = {}
            for face, entry in element["faces"].items():
                if isinstance(entry["mat"], tuple):
                    ref = entry["mat"][1]
                    key = extra.setdefault(ref, f"t{len(extra) + 1}")
                    w, h = entry["size"]
                    face_json = {"texture": "#" + key, "uv": [0, 0, min(16, w), min(16, h)]}
                else:
                    x, y = entry["at"]
                    w = max(1, int(math.ceil(entry["size"][0] * DENSITY - 1e-6)))
                    h = max(1, int(math.ceil(entry["size"][1] * DENSITY - 1e-6)))
                    k = 16 / size
                    face_json = {"texture": "#0", "uv": [round(x * k, 4), round(y * k, 4), round((x + w) * k, 4), round((y + h) * k, 4)]}
                if entry["tint"] is not None:
                    face_json["tintindex"] = entry["tint"]
                out["faces"][face] = face_json
            out_elements.append(out)
        for ref, key in extra.items():
            refs[key] = ref
        model = {"gui_light": gui_light, "textures": refs, "elements": out_elements, "display": display}
        if textures:
            model["textures"].update(textures)
        if overrides:
            model["overrides"] = overrides
        if write:
            TEXTURES.mkdir(parents=True, exist_ok=True)
            atlas.save(TEXTURES / f"{self.name}.png")
            MODELS.mkdir(parents=True, exist_ok=True)
            (MODELS / f"{self.name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        return model


def face_size(frm, to, face):
    dx, dy, dz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def pack(patches, size):
    """Shelf packing, tallest first. Returns False if they don't fit in {size}."""
    order = sorted(patches, key=lambda p: (-p[4], -p[3]))
    x = y = shelf = 0
    for element, face, entry, w, h in order:
        if w > size:
            return False
        if x + w > size:
            x = 0
            y += shelf
            shelf = 0
        if y + h > size:
            return False
        entry["at"] = (x, y)
        x += w
        shelf = max(shelf, h)
    return True


# ---------------------------------------------------------------- how they're held

def device(gui_scale=1.05, hand=0.5, first=0.45, lift=0.0):
    """A hand-held radio: held upright with its face out (third person) or towards you (first person); in the
    inventory turned a little to show its depth."""
    return {
        "gui": {"rotation": [18, -28, 0], "translation": [0, lift, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45] * 3},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.95] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [70, 180, 0], "translation": [0, 3, 1], "scale": [hand] * 3},
        "thirdperson_lefthand": {"rotation": [70, 180, 0], "translation": [0, 3, 1], "scale": [hand] * 3},
        "firstperson_righthand": {"rotation": [-5, -25, 0], "translation": [1.0, 2.5, 0], "scale": [first] * 3},
        "firstperson_lefthand": {"rotation": [-5, -25, 0], "translation": [1.0, 2.5, 0], "scale": [first] * 3},
    }


def tool(gui_scale=1.05):
    """An antenna: held like a tool (on a slant), upright in the inventory."""
    return {
        "gui": {"rotation": [20, -35, 0], "translation": [0, 0, 0], "scale": [gui_scale] * 3},  # (tool)
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45] * 3},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.95] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.8] * 3},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.8] * 3},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.65] * 3},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.65] * 3},
    }


def flat_thing(gui_scale=0.95):
    """A sheet or a small object: like a vanilla item, but with depth."""
    return {
        "gui": {"rotation": [18, -28, 0], "translation": [0, 0, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5] * 3},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55] * 3},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55] * 3},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
        "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
    }


# ---------------------------------------------------------------- palette

STEEL = (88, 92, 101)
STEEL_DARK = (58, 62, 70)
STEEL_LIGHT = (128, 133, 144)
BLACK = (34, 36, 41)
CHARCOAL = (48, 50, 56)
RUBBER = (28, 29, 32)
LCD_OFF = (52, 70, 56)
LCD = (120, 214, 120)
LCD_DARK = (24, 44, 30)
RED = (196, 34, 28)
RED_LIT = (255, 70, 50)
GREEN_LIT = (80, 255, 110)
AMBER = (240, 170, 40)
GOLD = (230, 184, 52)
YELLOW = (236, 196, 46)
TEAL = (40, 142, 150)
WHITE = (232, 232, 226)
CREAM = (238, 226, 190)


def led(color):
    return solid(color, noise=0.0, edge=0.85, bevel=False)


# ---------------------------------------------------------------- the Frequency Tuner

def tuner():
    m = Model("frequency_tuner")
    body = solid(STEEL, seed=11)

    def front(d, px, w, h):
        # the keypad: a 3x3 grid of small keys under the display (the display and dial are boxes)
        for row in range(3):
            for col in range(3):
                x0, y0 = 2 + col * 3, h - 10 + row * 3
                d.rectangle([x0, y0, x0 + 1, y0 + 1], fill=rgb(STEEL_LIGHT))
        d.line([1, h - 12, w - 2, h - 12], fill=rgb(STEEL_DARK))

    m.box((4.5, 0.5, 6.5), (11.5, 12, 9.5), body, south=custom(front, STEEL), east=ribbed(STEEL), west=ribbed(STEEL))
    m.box((5, 12, 7), (11, 12.5, 9), solid(STEEL_DARK))                            # the top cap
    # the display, set in a dark bezel
    m.box((5.25, 8, 9.5), (10.75, 11.25, 9.8), solid(BLACK))

    def digits(d, px, w, h):
        for i, x in enumerate(range(2, w - 2, 3)):
            d.rectangle([x, 2, x + 1, h - 3], outline=rgb(shade(LCD, 0.55)))
        d.line([2, h // 2, w - 3, h // 2], fill=rgb(LCD))
    m.box((5.75, 8.5, 9.8), (10.25, 10.75, 9.85), None, faces=("south",), south=custom(digits, LCD), glow=True)
    # the tuning dial: a knurled knob standing out of the face
    knob = solid(BLACK, seed=12)
    m.box((8.6, 4.6, 9.5), (10.9, 6.9, 10.6), knob, south=custom(lambda d, px, w, h: d.line([w // 2, 1, w // 2, 2], fill=rgb(WHITE)), BLACK))
    m.box((8.2, 5.2, 9.5), (11.3, 6.3, 10.3), knob)
    m.box((9.2, 4.2, 9.5), (10.3, 7.3, 10.3), knob)
    # the red Set button
    m.box((5.4, 5.0, 9.5), (7.2, 6.6, 10.1), solid(RED, seed=13))
    # the antenna
    m.box((9.6, 12.5, 7.4), (10.6, 15.5, 8.4), solid(STEEL_LIGHT), up=solid(STEEL_LIGHT))
    m.box((9.35, 15.5, 7.15), (10.85, 16.5, 8.65), led(RED_LIT), glow=True)
    return m.build(device())


# ---------------------------------------------------------------- the Remote Control

def remote(variant="idle"):
    name = "remote_control" + ("" if variant == "idle" else "_" + variant)
    m = Model(name)
    body = solid(CHARCOAL, seed=21)

    def front(d, px, w, h):
        for i in range(3):  # three small grey keys near the bottom
            x0 = 1 + i * 3
            d.rectangle([x0, h - 4, x0 + 1, h - 3], fill=rgb(STEEL_LIGHT))
        d.rectangle([2, 2, w - 3, 3], fill=rgb(BLACK))  # the IR window's edge
    m.box((5.5, 0.5, 7), (10.5, 12.5, 9.5), body, south=custom(front, CHARCOAL), east=ribbed(CHARCOAL), west=ribbed(CHARCOAL))
    m.box((6, 12.5, 7.5), (10, 13.2, 9), solid(BLACK))                            # the nose
    m.box((6.5, 12.6, 9.0), (9.5, 13.0, 9.6), glossy(RUBBER))                     # the IR window
    # the big red button (a cross, as on the old icon)
    red = solid(RED, seed=22)
    m.box((7.0, 6.2, 9.5), (9.0, 9.0, 10.4), red)
    m.box((6.3, 6.9, 9.5), (9.7, 8.3, 10.4), red)
    # the light: dark, or green/red after a press
    color = {"idle": (60, 30, 28), "heard": GREEN_LIT, "missed": RED_LIT}[variant]
    m.box((8.6, 10.6, 9.5), (9.6, 11.6, 9.9), led(color), glow=variant != "idle")
    # a little loop antenna on top, like the old icon's
    m.box((6.8, 13.2, 7.9), (7.4, 15.4, 8.5), solid(STEEL_LIGHT))
    m.box((8.6, 13.2, 7.9), (9.2, 15.4, 8.5), solid(STEEL_LIGHT))
    m.box((6.8, 15.4, 7.9), (9.2, 16.0, 8.5), solid(STEEL_LIGHT))
    overrides = None
    if variant == "idle":
        overrides = [{"predicate": {f"{MOD}:light": 0.5}, "model": f"{MOD}:item/remote_control_heard"},
                     {"predicate": {f"{MOD}:light": 1.0}, "model": f"{MOD}:item/remote_control_missed"}]
    return m.build(device(), overrides=overrides)


def multi_remote(variant="idle"):
    name = "multi_remote" + ("" if variant == "idle" else "_" + variant)
    m = Model(name)
    body = solid(STEEL_DARK, seed=31)

    def front(d, px, w, h):
        d.line([1, h - 5, w - 2, h - 5], fill=rgb(BLACK))
        for i in range(4):
            d.point([2 + i * 2, h - 3], fill=rgb(STEEL_LIGHT))
    m.box((4.5, 0.5, 7), (11.5, 13, 9.5), body, south=custom(front, STEEL_DARK), east=ribbed(STEEL_DARK), west=ribbed(STEEL_DARK))
    m.box((5, 13, 7.5), (11, 13.6, 9), solid(BLACK))
    # four buttons, white, tinted by the game with each button's color (tint 1-4)
    white = solid((236, 236, 236), noise=0.02, edge=0.8, seed=32)
    for i, (x, y) in enumerate(((5.3, 8.4), (8.2, 8.4), (5.3, 5.3), (8.2, 5.3))):
        m.box((x, y, 9.5), (x + 2.5, y + 2.5, 10.3), white, tint=i + 1)
    color = {"idle": (60, 30, 28), "heard": GREEN_LIT, "missed": RED_LIT}[variant]
    m.box((9.6, 11.4, 9.5), (10.6, 12.4, 9.9), led(color), glow=variant != "idle")
    m.box((5.6, 11.6, 9.5), (8.6, 12.2, 9.7), solid(STEEL_LIGHT))                 # the label strip
    m.box((7.6, 13.6, 7.9), (8.4, 16.0, 8.7), solid(STEEL_LIGHT))                 # the antenna
    overrides = None
    if variant == "idle":
        overrides = [{"predicate": {f"{MOD}:light": 0.5}, "model": f"{MOD}:item/multi_remote_heard"},
                     {"predicate": {f"{MOD}:light": 1.0}, "model": f"{MOD}:item/multi_remote_missed"}]
    return m.build(device(), overrides=overrides)


# ---------------------------------------------------------------- the Radio Pager

def pager(on=False):
    m = Model("pager_on" if on else "pager")
    body = solid(BLACK, seed=41)
    m.box((2.5, 3.5, 7), (13.5, 11.5, 10), body, east=ribbed(BLACK), west=ribbed(BLACK))
    m.box((3.5, 7.2, 10), (12.5, 10.6, 10.2), solid(CHARCOAL))                    # the bezel

    def text(d, px, w, h):
        d.line([2, 2, w - 6, 2], fill=rgb(shade(LCD, 0.45) if on else LCD_DARK))
        d.line([2, 4, w - 3, 4], fill=rgb(shade(LCD, 0.45) if on else LCD_DARK))
    m.box((4, 7.6, 10.2), (12, 10.2, 10.25), None, faces=("south",), south=custom(text, LCD if on else LCD_OFF), glow=on)
    keys = solid(STEEL, seed=42)
    for i in range(3):
        x = 4.2 + i * 2.7
        m.box((x, 4.4, 10), (x + 2.0, 5.8, 10.5), keys)
    m.box((11.8, 11.5, 8), (12.8, 13.5, 9), solid(CHARCOAL))                      # the stub antenna
    m.box((11.6, 13.5, 7.8), (13.0, 14.3, 9.2), led(RED_LIT if on else (110, 30, 26)), glow=on)
    m.box((5, 4.5, 6), (11, 10, 7), solid(STEEL_DARK))                            # the belt clip on the back
    overrides = None if on else [{"predicate": {f"{MOD}:pager_on": 1.0}, "model": f"{MOD}:item/pager_on"}]
    return m.build(device(gui_scale=1.05, first=0.4), overrides=overrides)


# ---------------------------------------------------------------- the Signal Scanner

def scanner():
    m = Model("signal_scanner")
    frame = solid(GOLD, seed=51)
    m.box((2.5, 5, 7), (13.5, 14.5, 9.5), frame, east=ribbed(GOLD), west=ribbed(GOLD))

    def scope(d, px, w, h):
        cx, cy = w / 2 - 0.5, h / 2 - 0.5
        r = min(w, h) / 2 - 1.5
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=rgb(shade(LCD, 0.55)))
        d.ellipse([cx - r / 2, cy - r / 2, cx + r / 2, cy + r / 2], outline=rgb(shade(LCD, 0.4)))
        d.line([cx, cy, cx + r * 0.8, cy - r * 0.6], fill=rgb(LCD))
        d.rectangle([cx - r * 0.5, cy + r * 0.2, cx - r * 0.5 + 1, cy + r * 0.2 + 1], fill=rgb((255, 240, 140)))
    m.box((3.5, 6.2, 9.5), (12.5, 13.6, 9.7), solid(BLACK))                       # the bezel
    m.box((4.2, 6.8, 9.7), (11.8, 13.0, 9.75), None, faces=("south",), south=custom(scope, LCD_DARK), glow=True)
    m.box((6.5, 0, 7.3), (9.5, 5, 9.2), ribbed(RUBBER), up=solid(RUBBER), down=solid(RUBBER))   # the handle
    m.box((6, 4.2, 7), (10, 5, 9.5), solid(STEEL_DARK))
    m.box((11.6, 14.5, 7.8), (12.4, 16.5, 8.6), solid(STEEL_LIGHT))               # the antenna
    m.box((4, 14.5, 8), (5.6, 15.1, 9.4), solid(RED, seed=52))                     # the Tune button
    return m.build(device(gui_scale=1.0, first=0.4))


# ---------------------------------------------------------------- the Signal Meter

NEEDLE_ANGLES = (45, 22.5, -22.5, -45)


def meter(level=0):
    name = "signal_meter" + ("" if level == 0 else f"_{level}")
    m = Model(name)
    case = solid(TEAL, seed=61)
    m.box((2, 1.5, 6.5), (14, 13, 10), case, east=ribbed(TEAL), west=ribbed(TEAL))

    def dial(d, px, w, h):
        # the scale: a band of color from grey through yellow to red, round the needle's pivot at the bottom middle
        cx, cy = w / 2, h - 1.5
        for y in range(h):
            for x in range(w):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                r = math.hypot(dx, dy)
                a = math.degrees(math.atan2(dx, -dy))  # -90 (left) .. 90 (right)
                if h - 5.2 <= r <= h - 3.2 and -58 <= a <= 58:
                    px[x, y] = rgb((110, 110, 110) if a < -15 else (226, 186, 44) if a < 25 else (214, 52, 40))
                elif h - 3.2 < r <= h - 2.4 and -58 <= a <= 58 and int(a + 60) % 12 < 3:
                    px[x, y] = rgb((60, 60, 60))
    m.box((3, 4.5, 10), (13, 11.5, 10.1), solid(BLACK))                           # the window frame
    m.box((3.5, 5, 10.1), (12.5, 11, 10.15), None, faces=("south",), south=custom(dial, CREAM))
    angle = NEEDLE_ANGLES[level]
    m.box((7.75, 5.4, 10.2), (8.25, 10.4, 10.35), solid((30, 30, 30), noise=0, bevel=False),
          rotation={"origin": [8, 5.6, 10.25], "axis": "z", "angle": angle})
    m.box((7.4, 5.1, 10.15), (8.6, 6.1, 10.45), solid(BLACK))                     # the pivot
    for i, x in enumerate((4, 6.5, 9)):
        m.box((x, 2.3, 10), (x + 1.6, 3.6, 10.5), solid(STEEL_LIGHT if i < 2 else RED, seed=62 + i))
    m.box((3.2, 13, 7.6), (4.0, 15.6, 8.4), solid(STEEL_LIGHT))                  # the antenna
    m.box((3.0, 15.6, 7.4), (4.2, 16.4, 8.6), led(RED_LIT), glow=True)
    m.box((2.5, 0.5, 7), (4, 1.5, 9.5), solid(BLACK))                              # feet
    m.box((12, 0.5, 7), (13.5, 1.5, 9.5), solid(BLACK))
    overrides = None
    if level == 0:
        overrides = [{"predicate": {f"{MOD}:meter_level": v}, "model": f"{MOD}:item/signal_meter_{i}"}
                     for i, v in ((1, 0.3), (2, 0.6), (3, 0.9))]
    return m.build(device(gui_scale=1.0, first=0.36), overrides=overrides)


# ---------------------------------------------------------------- the Walkie-Talkie

def walkie():
    m = Model("walkie_talkie")
    shell = solid(YELLOW, seed=71)

    def back(d, px, w, h):
        d.rectangle([2, 3, w - 3, h - 4], outline=rgb(shade(YELLOW, 0.75)))
    m.box((5, 0.5, 6.5), (11, 12, 9.5), shell, north=custom(back, YELLOW))
    m.box((5.5, 1.2, 9.5), (10.5, 7.6, 9.8), grille(RUBBER, (10, 10, 12)))       # the speaker grille

    def screen(d, px, w, h):
        d.line([1, 1, w - 3, 1], fill=rgb(shade(LCD, 0.45)))
        d.rectangle([w - 4, h - 3, w - 2, h - 2], fill=rgb(shade(LCD, 0.45)))
    m.box((5.7, 8.4, 9.5), (10.3, 11.2, 9.7), solid(BLACK))
    m.box((6.1, 8.8, 9.7), (9.9, 10.8, 9.75), None, faces=("south",), south=custom(screen, LCD), glow=True)
    m.box((4.3, 4.5, 7.2), (5, 9, 8.8), solid(RUBBER), west=ribbed(RUBBER))     # the push-to-talk button
    m.box((6, 12, 7.2), (7.6, 13.2, 8.8), ribbed(RUBBER), up=solid(CHARCOAL))   # the knob
    m.box((8.6, 12, 7.3), (10.2, 13, 8.7), solid(RUBBER))                      # the antenna's foot
    m.box((8.9, 13, 7.6), (9.9, 17.5, 8.4), solid(RUBBER, seed=72), up=solid(CHARCOAL))   # the rubber antenna
    m.box((6.4, 11.4, 9.5), (7.2, 11.9, 9.7), led((90, 30, 26)))              # the busy light
    return m.build(device(gui_scale=1.1, lift=-0.5))


# ---------------------------------------------------------------- antennas

TIER = {
    "iron": ("minecraft:block/iron_block", None),
    "gold": ("minecraft:block/gold_block", "minecraft:block/gold_block"),
    "diamond": ("minecraft:block/iron_block", "minecraft:block/diamond_block"),
    "netherite": ("minecraft:block/netherite_block", "minecraft:block/netherite_block"),
    "ender": ("minecraft:block/obsidian", "minecraft:block/crying_obsidian"),
}


def antenna(tier):
    m = Model(f"{tier}_antenna")
    rod_ref, accent_ref = TIER[tier]
    rod = texture(rod_ref)
    thick = 0.6 if tier == "netherite" else 0.45
    # the mounting plate and the mast
    m.box((5.5, 0, 5.5), (10.5, 1, 10.5), solid(STEEL_DARK, seed=81))
    m.box((6.5, 1, 6.5), (9.5, 2, 9.5), solid(STEEL, seed=82))
    m.box((8 - thick, 2, 8 - thick), (8 + thick, 14, 8 + thick), rod)
    # crossed dipoles
    for y in (9.5,) + ((6.5,) if tier in ("diamond", "netherite") else ()):
        m.box((2, y, 8 - thick * 0.8), (14, y + 0.9, 8 + thick * 0.8), rod)
        m.box((8 - thick * 0.8, y - 1.5, 2), (8 + thick * 0.8, y - 0.6, 14), rod)
    if tier == "gold":
        m.box((7, 5, 7), (9, 7.5, 9), texture(accent_ref))                         # the loading coil
        m.box((7.3, 7.5, 7.3), (8.7, 8, 8.7), solid(STEEL_DARK))
    if tier in ("diamond", "netherite"):
        for x, z in ((2, 8), (14, 8)):
            m.box((x - 0.8, 9.2, z - 0.8), (x + 0.8, 10.7, z + 0.8), texture(accent_ref))
    if tier == "ender":
        m.box((6.8, 12.5, 6.8), (9.2, 14.9, 9.2), texture("minecraft:block/crying_obsidian"))
        for x, z in ((2, 8), (14, 8)):
            m.box((x - 0.6, 9.2, z - 0.6), (x + 0.6, 10.6, z + 0.6), led((90, 230, 210)), glow=True)
        m.box((7.2, 14.9, 7.2), (8.8, 16.2, 8.8), led((200, 90, 255)), glow=True)
    else:
        m.box((7, 14, 7), (9, 16, 9), led(RED_LIT), glow=True)                     # the warning light
    return m.build(tool())


def disc_rows(r_out, r_in=0.0, step=0.5):
    """A round disc (or a ring, with a hole of {r_in}) as rows {step} apart: [(a1, a2, b1, b2), ...] where a
    runs along the rows and b across them, both from the middle; rows with the same ends are merged."""
    rows = []
    b = -r_out
    while b < r_out - 1e-9:
        mid = b + step / 2
        out = math.floor(math.sqrt(max(0.0, r_out * r_out - mid * mid)) / step + 0.5) * step
        spans = []
        if out > 0:
            if r_in > 0 and abs(mid) < r_in:
                inner = math.floor(math.sqrt(r_in * r_in - mid * mid) / step + 0.5) * step
                if inner < out:
                    spans = [(-out, -inner), (inner, out)]
            else:
                spans = [(-out, out)]
        rows.append((b, b + step, spans))
        b += step
    merged = []
    for b1, b2, spans in rows:
        if merged and merged[-1][2] == spans:
            merged[-1] = (merged[-1][0], b2, spans)
        else:
            merged.append((b1, b2, spans))
    return [(a1, a2, b1, b2) for b1, b2, spans in merged for a1, a2 in spans]


def dish_antenna():
    m = Model("dish_antenna")
    bowl = solid(WHITE, noise=0.03, edge=0.88, seed=91)
    rim = solid((206, 208, 212), noise=0.03, seed=92)
    back = solid((150, 154, 160), seed=93)
    tilt = {"origin": [8, 8, 8], "axis": "x", "angle": -22.5}
    cx, cy = 8, 9
    # a bowl facing the front (south), tilted up: its middle set back, each ring a step further out
    for r_out, r_in, z0 in ((2.5, 0, 6.6), (4.5, 2.5, 7.1), (6.5, 4.5, 7.6)):
        for a1, a2, b1, b2 in disc_rows(r_out, r_in):
            m.box((cx + a1, cy + b1, z0), (cx + a2, cy + b2, z0 + 0.6), bowl if r_out < 6.5 else rim,
                  north=back, rotation=tilt)
    for a1, a2, b1, b2 in disc_rows(6.5, 2.5):
        m.box((cx + a1, cy + b1, 6.0), (cx + a2, cy + b2, 6.6), back, rotation=tilt)   # the back of the bowl
    m.box((7, 8, 5.2), (9, 10, 6.0), solid(STEEL_DARK), rotation=tilt)                # the hub
    m.box((7.7, 4.0, 7.0), (8.3, 4.6, 12.8), solid(STEEL_LIGHT), rotation=tilt)        # the feed arm
    m.box((7.7, 4.6, 12.2), (8.3, 8.4, 12.8), solid(STEEL_LIGHT), rotation=tilt)
    m.box((7.1, 8.2, 11.8), (8.9, 10.0, 13.6), solid(BLACK), rotation=tilt)            # the feed horn
    m.box((7.5, 8.6, 13.6), (8.5, 9.6, 13.8), led(RED_LIT), glow=True, rotation=tilt)
    m.box((7.6, 0.6, 7.4), (8.4, 6.0, 8.2), solid(STEEL, seed=94))                    # the post
    m.box((5.8, 0, 5.8), (10.2, 0.8, 10.2), solid(STEEL_DARK, seed=95))               # its foot
    return m.build(tool(gui_scale=1.0))


def satellite_dish():
    m = Model("satellite_dish")
    bowl = solid(WHITE, noise=0.03, edge=0.88, seed=101)
    rim = solid((206, 208, 212), noise=0.03, seed=102)
    under = solid((160, 164, 170), seed=103)
    # a big bowl looking up: its middle low, each ring a step higher out to the rim
    for r_out, r_in, y0 in ((3.0, 0, 7.0), (5.2, 3.0, 7.6), (7.5, 5.2, 8.2)):
        for a1, a2, b1, b2 in disc_rows(r_out, r_in):
            m.box((8 + a1, y0, 8 + b1), (8 + a2, y0 + 0.6, 8 + b2), bowl if r_out < 7.5 else rim, down=under)
    m.box((6.8, 6.2, 6.8), (9.2, 7.0, 9.2), solid(STEEL, seed=104))                    # the hub under it
    m.box((7.5, 1.1, 7.5), (8.5, 6.2, 8.5), solid(STEEL, seed=105))                    # the post
    leg = solid(STEEL_DARK, seed=106)                                                  # the tripod and its feet
    m.box((3, 0.5, 7.7), (7.5, 1.1, 8.3), leg)
    m.box((8.5, 0.5, 7.7), (13, 1.1, 8.3), leg)
    m.box((7.7, 0.5, 8.5), (8.3, 1.1, 13), leg)
    for x, z in ((3, 8), (13, 8), (8, 13)):
        m.box((x - 0.5, 0, z - 0.5), (x + 0.5, 1.2, z + 0.5), solid(BLACK))
    # the feed on three thin legs over the middle, with its ender-purple tip
    for x, z in ((4.0, 8), (12.0, 8), (8, 4.0)):
        m.box((x - 0.25, 8.8, z - 0.25), (x + 0.25, 13.2, z + 0.25), solid(STEEL_LIGHT))
    m.box((6.6, 13.2, 6.6), (9.4, 14.0, 9.4), solid(STEEL_DARK))
    m.box((7.3, 14.0, 7.3), (8.7, 15.4, 8.7), led((190, 90, 250)), glow=True)
    return m.build(tool(gui_scale=1.0))


# ---------------------------------------------------------------- the Coverage Map

def coverage_map():
    m = Model("coverage_map")

    def sheet(d, px, w, h):
        # rings of signal strength round a tower: red in the middle through orange, yellow and green
        cx, cy = w / 2 - 0.5, h / 2 - 0.5
        for r, col in ((w * 0.42, (90, 170, 70)), (w * 0.32, (220, 200, 60)), (w * 0.22, (232, 132, 44)), (w * 0.12, (200, 40, 34))):
            d.ellipse([cx - r, cy - r * 0.9, cx + r, cy + r * 0.9], fill=rgb(col))
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=rgb((60, 20, 20)))
        d.rectangle([0, 0, w - 1, h - 1], outline=rgb((150, 120, 80)))
        for x in range(3, w - 3, 6):
            d.line([x, 1, x, h - 2], fill=(140, 120, 90, 90))
    paper = solid(CREAM, noise=0.04, seed=111)
    m.box((2, 1, 7.6), (14, 15, 8.2), paper, south=custom(sheet, CREAM), north=paper)
    # rolled at the left and the right, like a scroll
    roll = solid(shade(CREAM, 0.9), seed=112)
    m.box((0.8, 0.6, 7.2), (2.4, 15.4, 8.8), roll)
    m.box((13.6, 0.6, 7.2), (15.2, 15.4, 8.8), roll)
    m.box((1.2, 0.2, 7.6), (2.0, 15.8, 8.4), solid((120, 84, 50), seed=113))      # the rods
    m.box((14.0, 0.2, 7.6), (14.8, 15.8, 8.4), solid((120, 84, 50), seed=114))
    return m.build(flat_thing())


# ---------------------------------------------------------------- Wall Redstone

def wall_redstone():
    m = Model("wall_redstone")
    stone = texture("minecraft:block/stone")
    m.box((3, 0, 6), (13, 16, 8.5), stone)                                        # a piece of wall
    dust = solid((190, 20, 14), noise=0.12, edge=0.7, seed=121)
    m.box((7.4, 1, 8.5), (8.6, 15, 9.0), dust)                                    # the trace running up it
    m.box((3.6, 7.4, 8.5), (12.4, 8.6, 9.0), dust)                                # and across
    for x, y in ((8, 15), (8, 1), (3.6, 8), (12.4, 8)):
        m.box((x - 0.9, y - 0.9, 8.5), (x + 0.9, y + 0.9, 9.2), solid((230, 40, 26), noise=0.1, seed=122))
    m.box((7.2, 7.2, 8.5), (8.8, 8.8, 9.3), solid((255, 70, 40), noise=0.05, seed=123), glow=True)
    return m.build(flat_thing())


# ---------------------------------------------------------------- v0.67.0: the Radio Drone and its controller

DRONE_ARM = 4.5  # the rotors' distance from the middle, along x and z (DroneRenderer.ARM)
DRONE_SHELL = (70, 74, 82)
DRONE_SHELL_DARK = (44, 47, 54)
BLADE = (206, 208, 214)


def drone_body(m):
    """The drone's frame, looking south (+z), its middle at (8, 0, 8): a hull with a battery pack on top,
    a camera under its nose, four arms out to the motors, two skids, and its navigation lights (red on its
    left, green on its right)."""
    shell = solid(DRONE_SHELL, seed=301)

    def top(d, px, w, h):
        d.line([1, h // 2, w - 2, h // 2], fill=rgb(shade(DRONE_SHELL, 0.8)))
        d.rectangle([w // 2 - 1, 1, w // 2, 2], fill=rgb(TEAL))
    m.box((5.5, 1.5, 4.5), (10.5, 4.5, 11.5), shell, up=custom(top, DRONE_SHELL))           # the hull

    def battery(d, px, w, h):
        d.rectangle([0, h - 3, w - 1, h - 2], fill=rgb(GOLD))
    m.box((6, 4.5, 5.2), (10, 5.4, 9.4), solid(DRONE_SHELL_DARK, seed=302),
          north=custom(battery, DRONE_SHELL_DARK), east=custom(battery, DRONE_SHELL_DARK), west=custom(battery, DRONE_SHELL_DARK))
    # the camera: a dark gimbal under the nose, its lens looking forward
    m.box((6.5, 0.6, 11.5), (9.5, 3.4, 13.4), solid(BLACK, seed=303))
    m.box((7.1, 1.2, 13.4), (8.9, 3.0, 14.0), glossy((44, 70, 112), seed=304))
    # four arms, corner to corner, and a motor at the end of each
    arm = solid(DRONE_SHELL_DARK, seed=305)
    half = DRONE_ARM * math.sqrt(2)
    for angle in (45, -45):
        m.box((8 - half, 2.7, 7.4), (8 + half, 3.5, 8.6), arm, rotation={"origin": [8, 3, 8], "axis": "y", "angle": angle})
    motor = solid(STEEL, seed=306)
    for cx in (8 - DRONE_ARM, 8 + DRONE_ARM):
        for cz in (8 - DRONE_ARM, 8 + DRONE_ARM):
            m.box((cx - 1, 2.4, cz - 1), (cx + 1, 4.4, cz + 1), motor)
    # the skids and their struts
    rubber = solid(RUBBER, seed=307)
    for x0 in (5.0, 10.0):
        m.box((x0, 0, 5), (x0 + 1, 0.6, 11), rubber)
        for z0 in (6.5, 9.0):
            m.box((x0 + 0.25, 0.6, z0), (x0 + 0.75, 1.5, z0 + 0.5), solid(STEEL_DARK))
    # the lights: red on its left (+x as it looks south), green on its right
    m.box((9.6, 2.9, 12.0), (10.4, 3.7, 12.8), led(RED_LIT), glow=True)
    m.box((5.6, 2.9, 12.0), (6.4, 3.7, 12.8), led(GREEN_LIT), glow=True)


def drone_rotor(m, cx=8.0, cz=8.0, along_x=True):
    """One rotor: a black hub and a two-bladed propeller, its middle at ({cx}, {cz})."""
    m.box((cx - 0.6, 4.4, cz - 0.6), (cx + 0.6, 5.2, cz + 0.6), solid(BLACK, seed=311))
    blade = solid(BLADE, noise=0.02, seed=312, bevel=False)
    if along_x:
        m.box((cx - 3.6, 4.7, cz - 0.5), (cx + 3.6, 4.95, cz + 0.5), blade)
    else:
        m.box((cx - 0.5, 4.7, cz - 3.6), (cx + 0.5, 4.95, cz + 3.6), blade)


def drone():
    # What the entity renderer draws: the body, and one rotor it turns and moves to each motor.
    body = Model("radio_drone_body")
    drone_body(body)
    body.build(flat_thing())
    rotor = Model("radio_drone_rotor")
    drone_rotor(rotor)
    rotor.build(flat_thing())
    # The item: the same drone with its rotors still.
    m = Model("radio_drone")
    drone_body(m)
    for i, (cx, cz) in enumerate(((8 - DRONE_ARM, 8 - DRONE_ARM), (8 + DRONE_ARM, 8 - DRONE_ARM),
                                  (8 - DRONE_ARM, 8 + DRONE_ARM), (8 + DRONE_ARM, 8 + DRONE_ARM))):
        drone_rotor(m, cx, cz, along_x=i in (0, 3))
    return m.build({
        "gui": {"rotation": [30, 160, 0], "translation": [0, 3, 0], "scale": [1.15] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5] * 3},
        "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -2], "scale": [1.0] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 14, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [10, 180, 0], "translation": [0, 4, 1], "scale": [0.55] * 3},
        "thirdperson_lefthand": {"rotation": [10, 180, 0], "translation": [0, 4, 1], "scale": [0.55] * 3},
        "firstperson_righthand": {"rotation": [10, 160, 0], "translation": [1.5, 5, 0], "scale": [0.6] * 3},
        "firstperson_lefthand": {"rotation": [10, 160, 0], "translation": [1.5, 5, 0], "scale": [0.6] * 3},
    })


def drone_controller():
    """The Drone Controller: a two-stick transmitter with grips, a little screen between the sticks, a
    compass rose under it and an antenna with a red tip."""
    m = Model("drone_controller")
    body = solid(CHARCOAL, seed=321)

    def face(d, px, w, h):
        d.rectangle([1, 1, w - 2, 2], fill=rgb(shade(CHARCOAL, 0.8)))
    m.box((3.5, 1, 7), (12.5, 8.5, 9.5), body, south=custom(face, CHARCOAL))
    grip = solid(RUBBER, seed=322)
    m.box((2.4, 0, 7.3), (4.4, 5, 9.3), grip)
    m.box((11.6, 0, 7.3), (13.6, 5, 9.3), grip)
    for cx in (5.8, 10.2):
        m.box((cx - 1.1, 3.0, 9.5), (cx + 1.1, 5.2, 9.9), solid(BLACK, seed=323))         # the stick's well
        m.box((cx - 0.3, 3.8, 9.9), (cx + 0.3, 4.4, 11.0), solid(STEEL_LIGHT))           # the stick
        m.box((cx - 0.6, 3.5, 11.0), (cx + 0.6, 4.7, 11.6), solid(BLACK, seed=324))      # its knob
    m.box((6.6, 5.6, 9.5), (9.4, 7.9, 9.7), solid(BLACK))
    m.box((6.9, 5.9, 9.7), (9.1, 7.6, 9.75), None, faces=("south",), south=custom(
        lambda d, px, w, h: d.line([1, h - 2, w - 2, 1], fill=rgb(shade(LCD, 0.6))), LCD), glow=True)

    def rose(d, px, w, h):
        d.line([w // 2, 0, w // 2, h - 1], fill=rgb(RED))
    m.box((7.4, 1.6, 9.5), (8.6, 2.8, 9.8), solid(CREAM, seed=325), south=custom(rose, CREAM))
    m.box((10.9, 8.5, 7.9), (11.6, 14.5, 8.6), solid(STEEL_LIGHT), up=solid(STEEL_LIGHT))
    m.box((10.75, 14.5, 7.75), (11.75, 15.4, 8.75), led(RED_LIT), glow=True)
    return m.build(device(gui_scale=1.0))


# ---------------------------------------------------------------- all of them

if __name__ == "__main__":
    tuner()
    for v in ("idle", "heard", "missed"):
        remote(v)
        multi_remote(v)
    pager(False)
    pager(True)
    scanner()
    for lvl in range(4):
        meter(lvl)
    walkie()
    for t in TIER:
        antenna(t)
    dish_antenna()
    satellite_dish()
    coverage_map()
    wall_redstone()
    drone()
    drone_controller()
    print("wrote", len(list(MODELS.glob("*.json"))), "item models;", len(list(TEXTURES.glob("*.png"))), "model textures")
