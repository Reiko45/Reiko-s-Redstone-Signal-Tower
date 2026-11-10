"""Draws the mod's icon (v0.62.0): a 64 x 64 pixel-art radio tower under a night sky, its red light
on top sending out signal waves, scaled up 8 times (no smoothing) to 512 x 512. Written to
src/main/resources/reikos_signal_tower_logo.png (the mod list's logo, `logoFile` in
neoforge.mods.toml) and docs/images/icon.png (for the README and mod pages). Needs Pillow."""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
N = 64
SCALE = 8

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()
rng = random.Random(6201)


def put(x, y, color):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = color


def blend(x, y, color, alpha):
    if 0 <= x < N and 0 <= y < N and alpha > 0:
        r, g, b, a = px[x, y]
        cr, cg, cb = color
        px[x, y] = (round(r + (cr - r) * alpha), round(g + (cg - g) * alpha), round(b + (cb - b) * alpha), max(a, 255))


def inside(x, y, radius=9):
    """In the rounded square (corner radius 9)."""
    cx = min(max(x, radius), N - 1 - radius)
    cy = min(max(y, radius), N - 1 - radius)
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius + 2


# The sky: deep blue at the top to violet near the ground.
for y in range(N):
    t = y / (N - 1)
    sky = (round(14 + 34 * t), round(20 + 14 * t), round(52 + 40 * t), 255)
    for x in range(N):
        if inside(x, y):
            put(x, y, sky)

# Stars.
for _ in range(34):
    x, y = rng.randrange(4, N - 4), rng.randrange(4, 44)
    if inside(x, y) and abs(x - 32) > 6:
        put(x, y, (200, 210, 255, 255) if rng.random() < 0.7 else (255, 244, 200, 255))

# Signal waves from the light at the top of the tower, on both sides: bright red near, dimmer out.
LIGHT = (32.0, 8.0)
for radius, color, strength in ((7, (255, 70, 50), 1.0), (12, (255, 120, 60), 0.85), (17, (255, 170, 80), 0.65), (22, (255, 200, 110), 0.45)):
    for x in range(N):
        for y in range(N):
            if not inside(x, y):
                continue
            dx, dy = x + 0.5 - LIGHT[0], y + 0.5 - LIGHT[1]
            d = math.hypot(dx, dy)
            angle = math.degrees(math.atan2(dy, abs(dx)))  # 0 = level, + = below
            if abs(d - radius) < 0.75 and -40 <= angle <= 40 and abs(dx) > 3:
                fade = 1 - max(0, (abs(angle) - 24) / 16)
                fade *= min(1.0, max(0.0, (y - 2) / 4))  # thinning out toward the top edge
                blend(x, y, color, strength * max(0.0, min(1.0, fade)))

# The ground: a grass edge over dirt and stone.
for x in range(N):
    for y in range(54, N):
        if not inside(x, y):
            continue
        if y == 54:
            c = (96, 168, 60, 255) if (x * 7) % 5 else (120, 190, 72, 255)
        elif y == 55:
            c = (76, 140, 48, 255) if x % 3 else (96, 168, 60, 255)
        else:
            c = rng.choice([(122, 86, 58, 255), (106, 74, 50, 255), (134, 96, 64, 255)])
        put(x, y, c)

# The tower: a lattice mast tapering from 20 wide at the ground to 4 under the transmitter.
TOP, BOTTOM = 21, 54
LEFT_DARK, LIGHT_IRON, MID_IRON = (70, 74, 82, 255), (196, 200, 206, 255), (140, 146, 154, 255)


def half_width(y):
    return 2 + (y - TOP) / (BOTTOM - TOP) * 8


for y in range(TOP, BOTTOM):
    w = half_width(y)
    left, right = round(32 - w - 0.5), round(31 + w + 0.5)
    put(left, y, LIGHT_IRON)
    put(right, y, LEFT_DARK)
# Cross-bracing between rungs.
rungs = [21, 27, 33, 40, 47, 54]
for a, b in zip(rungs, rungs[1:]):
    la, ra = 32 - half_width(a) - 0.5, 31 + half_width(a) + 0.5
    lb, rb = 32 - half_width(b) - 0.5, 31 + half_width(b) + 0.5
    for x in range(round(la), round(ra) + 1):
        put(x, a, MID_IRON)
    steps = (b - a) * 3
    for i in range(steps + 1):
        t = i / steps
        y = a + (b - a) * t
        put(round(la + (rb - la) * t), round(y), MID_IRON)
        put(round(ra + (lb - ra) * t), round(y), MID_IRON)

# The transmitter on top: a dark box with a red lamp and a rivet.
for x in range(28, 36):
    for y in range(16, 21):
        edge = x in (28, 35) or y in (16, 20)
        put(x, y, (52, 54, 60, 255) if edge else (88, 92, 100, 255))
put(30, 18, (255, 60, 40, 255))
put(31, 18, (255, 110, 70, 255))
put(33, 18, (170, 176, 184, 255))
# The rod up to the light.
for y in range(9, 16):
    put(31, y, (170, 176, 184, 255))
    put(32, y, (110, 116, 124, 255))

# The red light, with its glow.
for x in range(N):
    for y in range(N):
        d = math.hypot(x + 0.5 - 32, y + 0.5 - 8)
        if d < 7 and inside(x, y):
            blend(x, y, (255, 80, 60), 0.55 * (1 - d / 7) ** 1.6)
for (x, y) in ((31, 7), (32, 7), (31, 8), (32, 8)):
    put(x, y, (255, 70, 50, 255))
put(31, 7, (255, 210, 190, 255))

# A light rim round the square.
for x in range(N):
    for y in range(N):
        if inside(x, y) and not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            blend(x, y, (210, 220, 255), 0.35)

big = img.resize((N * SCALE, N * SCALE), Image.NEAREST)
for out in (ROOT / "src/main/resources/reikos_signal_tower_logo.png", ROOT / "docs/images/icon.png"):
    out.parent.mkdir(parents=True, exist_ok=True)
    big.save(out, optimize=True)
    print("wrote", out.relative_to(ROOT))
