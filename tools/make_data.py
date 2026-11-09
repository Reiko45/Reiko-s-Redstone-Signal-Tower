"""Writes models, blockstates, lang, recipes, loot tables, tags, advancements and the test structure
for Reiko's Redstone Signal Tower.

Usage: python3 tools/make_data.py
Writes into src/main/resources. Re-running overwrites those files, so either keep editing this
script, or edit the JSON files directly and stop using it.
"""
import gzip
import json
import struct
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "src/main/resources"
MOD = "reikos_signal_tower"
ASSETS = RES / "assets" / MOD
DATA = RES / "data" / MOD


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def tex(name, folder="block"):
    return f"{MOD}:{folder}/{name}"


SIDES = ["north", "south", "east", "west"]
GLOW = {"block_light": 15, "sky_light": 15}

# ---------------------------------------------------------------- block models

models = ASSETS / "models" / "block"


def casing_faces():
    faces = {f: {"texture": "#side", "cullface": f} for f in SIDES}
    faces["up"] = {"texture": "#top", "cullface": "up"}
    faces["down"] = {"texture": "#bottom", "cullface": "down"}
    return faces


def casing_faces_with(texture):
    return {f: {"texture": texture, "cullface": f} for f in SIDES + ["up", "down"]}


def box(frm, to, texture, uv_sides, uv_ends, glow=False):
    faces = {f: {"texture": texture, "uv": uv_sides} for f in SIDES}
    faces["up"] = {"texture": texture, "uv": uv_ends}
    faces["down"] = {"texture": texture, "uv": uv_ends}
    element = {"from": frm, "to": to, "faces": faces}
    if glow:
        element["neoforge_data"] = GLOW
        element["shade"] = False
    return element


def transmitter_model(lit, name="radio_transmitter", tip="antenna_tip"):
    """Steel cabinet with a dipole antenna and a warning light that glows while broadcasting.
    The repeater uses the same shape with its own (amber) textures."""
    suffix = "_on" if lit else ""
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": tex(f"{name}_side{suffix}"),
            "side": tex(f"{name}_side{suffix}"),
            "top": tex(f"{name}_top{suffix}"),
            "bottom": tex("radio_bottom"),
            "rod": tex("radio_mast_post"),
            "tip": tex(f"{tip}{suffix}"),
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces()},
            # Mast rod
            box([7, 16, 7], [9, 28, 9], "#rod", [6, 0, 8, 12], [6, 6, 8, 8]),
            # Dipole arms
            box([3, 22, 7.5], [13, 23, 8.5], "#rod", [3, 4, 13, 5], [3, 4, 13, 5]),
            box([7.5, 19, 3], [8.5, 20, 13], "#rod", [3, 12, 13, 13], [3, 12, 13, 13]),
            # Warning light
            box([6.5, 28, 6.5], [9.5, 31, 9.5], "#tip", [4, 4, 12, 12], [4, 4, 12, 12], glow=lit),
        ],
        "display": {
            # The antenna makes the model twice as tall as a block, so shrink it in inventories.
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.38, 0.38, 0.38]},
        },
    }


def receiver_model(lit, side="radio_receiver_side"):
    suffix = "_on" if lit else ""
    return {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": tex(f"{side}{suffix}"),
            "north": tex(f"{side}{suffix}"),
            "south": tex(f"{side}{suffix}"),
            "east": tex(f"{side}{suffix}"),
            "west": tex(f"{side}{suffix}"),
            "up": tex(f"radio_receiver_top{suffix}"),
            "down": tex("radio_bottom"),
        },
    }


for lit in (False, True):
    suffix = "_on" if lit else ""
    write(models / f"radio_transmitter{suffix}.json", transmitter_model(lit))
    write(models / f"radio_repeater{suffix}.json", transmitter_model(lit, "radio_repeater", "repeater_tip"))
    write(models / f"radio_converter{suffix}.json", transmitter_model(lit, "radio_converter", "converter_tip"))
    write(models / f"radio_receiver{suffix}.json", receiver_model(lit))
    write(models / f"radio_toggle_receiver{suffix}.json", receiver_model(lit, "radio_toggle_receiver_side"))
    write(models / f"radio_inverted_receiver{suffix}.json", receiver_model(lit, "radio_inverted_receiver_side"))
    write(models / f"radio_logic_receiver{suffix}.json", receiver_model(lit, "radio_logic_receiver_side"))
    write(models / f"radio_delay_receiver{suffix}.json", receiver_model(lit, "radio_delay_receiver_side"))
    write(models / f"radio_counter_receiver{suffix}.json", receiver_model(lit, "radio_counter_receiver_side"))
    write(models / f"radio_speaker{suffix}.json", receiver_model(lit, "radio_speaker_side"))
    write(models / f"radio_timer_transmitter{suffix}.json", transmitter_model(lit, "radio_timer_transmitter"))
    write(models / f"radio_weather_station{suffix}.json", transmitter_model(lit, "radio_weather_station"))  # v0.53.0
    write(models / f"bundled_transmitter{suffix}.json", transmitter_model(lit, "bundled_transmitter"))  # v0.54.0
    write(models / f"bundled_receiver{suffix}.json", receiver_model(lit, "bundled_receiver_side"))
    write(models / f"radio_signal_recorder{suffix}.json", transmitter_model(lit, "radio_signal_recorder"))
    write(models / f"radio_jammer{suffix}.json", transmitter_model(lit, "radio_jammer", "jammer_tip"))


def compact_receiver_model(lit, name="compact_radio_receiver", particle="radio_receiver_side"):
    """Same shape and UV layout as a vanilla button (lying on the floor, front facing up)."""
    t = "#texture"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex(particle), "texture": tex(name + ("_on" if lit else ""))},
        "elements": [{
            "from": [5, 0, 6], "to": [11, 2, 10],
            "faces": {
                "down": {"uv": [5, 6, 11, 10], "texture": t, "cullface": "down"},
                "up": {"uv": [5, 6, 11, 10], "texture": t},
                "north": {"uv": [5, 14, 11, 16], "texture": t},
                "south": {"uv": [5, 14, 11, 16], "texture": t},
                "west": {"uv": [6, 14, 10, 16], "texture": t},
                "east": {"uv": [6, 14, 10, 16], "texture": t},
            },
        }],
    }


for lit in (False, True):
    write(models / ("compact_radio_receiver" + ("_on" if lit else "") + ".json"), compact_receiver_model(lit))
    # v0.38.0: the Compact Radio Transmitter, same shape.
    write(models / ("compact_radio_transmitter" + ("_on" if lit else "") + ".json"),
          compact_receiver_model(lit, "compact_radio_transmitter", "radio_transmitter_side"))
    # v0.39.0: the Radio Motion Sensor, same shape again.
    write(models / ("radio_motion_sensor" + ("_on" if lit else "") + ".json"),
          compact_receiver_model(lit, "radio_motion_sensor", "radio_transmitter_side"))

def keypad_model(lit):
    """An 8 x 10 plate, 1 pixel thick, lying on the floor with its front up (rotated like a button)."""
    t = "#texture"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("code_keypad"), "texture": tex("code_keypad" + ("_on" if lit else ""))},
        "elements": [{
            "from": [4, 0, 3], "to": [12, 1, 13],
            "faces": {
                "down": {"uv": [4, 3, 12, 13], "texture": t, "cullface": "down"},
                "up": {"uv": [4, 3, 12, 13], "texture": t, "rotation": 180},
                "north": {"uv": [4, 15, 12, 16], "texture": t},
                "south": {"uv": [4, 15, 12, 16], "texture": t},
                "west": {"uv": [3, 15, 13, 16], "texture": t},
                "east": {"uv": [3, 15, 13, 16], "texture": t},
            },
        }],
    }


for lit in (False, True):
    write(models / ("code_keypad" + ("_on" if lit else "") + ".json"), keypad_model(lit))
write(models / "code_keypad_inventory.json", {
    "parent": "minecraft:block/block",
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    },
    "textures": {"particle": tex("code_keypad"), "texture": tex("code_keypad")},
    "elements": [{
        "from": [4, 3, 7.5], "to": [12, 13, 8.5],
        "faces": {
            "north": {"uv": [4, 3, 12, 13], "texture": "#texture"},
            "south": {"uv": [4, 3, 12, 13], "texture": "#texture"},
            "up": {"uv": [4, 15, 12, 16], "texture": "#texture"},
            "down": {"uv": [4, 15, 12, 16], "texture": "#texture"},
            "west": {"uv": [3, 15, 4, 16], "texture": "#texture"},
            "east": {"uv": [3, 15, 4, 16], "texture": "#texture"},
        },
    }],
})

# Inventory version: stood up on its edge so the front shows, like a wall-mounted one.
def compact_inventory_model(name, particle):
    return {
        "parent": "minecraft:block/block",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        },
        "textures": {"particle": tex(particle), "texture": tex(name)},
        "elements": [{
            "from": [5, 6, 7], "to": [11, 10, 9],
            "faces": {
                "north": {"uv": [5, 6, 11, 10], "texture": "#texture"},
                "south": {"uv": [5, 6, 11, 10], "texture": "#texture"},
                "up": {"uv": [5, 14, 11, 16], "texture": "#texture"},
                "down": {"uv": [5, 14, 11, 16], "texture": "#texture"},
                "west": {"uv": [6, 14, 8, 16], "texture": "#texture", "rotation": 90},
                "east": {"uv": [6, 14, 8, 16], "texture": "#texture", "rotation": 90},
            },
        }],
    }


write(models / "compact_radio_transmitter_inventory.json",
      compact_inventory_model("compact_radio_transmitter", "radio_transmitter_side"))
write(models / "radio_motion_sensor_inventory.json",
      compact_inventory_model("radio_motion_sensor", "radio_transmitter_side"))
write(models / "compact_radio_receiver_inventory.json", {
    "parent": "minecraft:block/block",
    "display": {
        # It's tiny, so draw it bigger than a normal block in inventories and item frames.
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    },
    "textures": {"particle": tex("radio_receiver_side"), "texture": tex("compact_radio_receiver")},
    "elements": [{
        "from": [5, 6, 7], "to": [11, 10, 9],
        "faces": {
            "north": {"uv": [5, 6, 11, 10], "texture": "#texture"},
            "south": {"uv": [5, 6, 11, 10], "texture": "#texture"},
            "up": {"uv": [5, 14, 11, 16], "texture": "#texture"},
            "down": {"uv": [5, 14, 11, 16], "texture": "#texture"},
            "west": {"uv": [6, 14, 8, 16], "texture": "#texture", "rotation": 90},
            "east": {"uv": [6, 14, 8, 16], "texture": "#texture", "rotation": 90},
        },
    }],
})


# Wall Redstone, built from vanilla's own "dust running up a wall" textures and tinted by power.
# The base models sit on the north wall of their block space (facing south), like vanilla's
# redstone_dust_up model; the blockstate turns them to face other ways.
def wall_dust_plane(frm, to, uv, rotation=0):
    face = {"uv": uv, "texture": "#line", "tintindex": 0}
    if rotation:
        face["rotation"] = rotation
    return {"from": frm, "to": to, "shade": False, "faces": {"south": face}}


def wall_dust_model(element, line="minecraft:block/redstone_dust_line0"):
    return {
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": {"particle": "minecraft:block/redstone_dust_dot", "line": line},
        "elements": [element],
    }


write(models / "wall_redstone_dot.json", wall_dust_model(
    wall_dust_plane([0, 0, 0.25], [16, 16, 0.25], [0, 0, 16, 16]), "minecraft:block/redstone_dust_dot"))
write(models / "wall_redstone_up.json", wall_dust_model(wall_dust_plane([0, 8, 0.3], [16, 16, 0.3], [0, 0, 16, 8])))
write(models / "wall_redstone_down.json", wall_dust_model(wall_dust_plane([0, 0, 0.3], [16, 8, 0.3], [0, 8, 16, 16])))
# Sideways arms: the vertical line texture turned 90 degrees.
write(models / "wall_redstone_right.json", wall_dust_model(wall_dust_plane([8, 0, 0.3], [16, 16, 0.3], [0, 0, 16, 8], 90)))
write(models / "wall_redstone_left.json", wall_dust_model(wall_dust_plane([0, 0, 0.3], [8, 16, 0.3], [0, 8, 16, 16], 90)))


def post(x, z):
    return {"from": [x, 0, z], "to": [x + 2, 16, z + 2],
            "faces": {f: {"texture": "#post"} for f in SIDES + ["up", "down"]}}


def panel_ns(z):
    uv = [5, 0, 11, 16]
    return {"from": [5, 0, z], "to": [11, 16, z],
            "faces": {"north": {"uv": uv, "texture": "#lattice"}, "south": {"uv": uv, "texture": "#lattice"}}}


def panel_ew(x):
    uv = [5, 0, 11, 16]
    return {"from": [x, 0, 5], "to": [x, 16, 11],
            "faces": {"east": {"uv": uv, "texture": "#lattice"}, "west": {"uv": uv, "texture": "#lattice"}}}


write(models / "radio_mast.json", {
    "parent": "minecraft:block/block",
    "render_type": "minecraft:cutout",
    "ambientocclusion": False,
    "textures": {
        "particle": tex("radio_mast_post"),
        "post": tex("radio_mast_post"),
        "lattice": tex("radio_mast_lattice"),
    },
    "elements": [
        post(3, 3), post(11, 3), post(3, 11), post(11, 11),
        panel_ns(4), panel_ns(12), panel_ew(4), panel_ew(12),
    ],
})

# ---------------------------------------------------------------- item models

items = ASSETS / "models" / "item"
for name in ("radio_transmitter", "radio_receiver", "radio_toggle_receiver", "radio_inverted_receiver", "radio_logic_receiver",
             "radio_delay_receiver", "radio_counter_receiver", "radio_timer_transmitter", "radio_speaker", "radio_status_board",
             "radio_signal_recorder", "radio_jammer", "radio_repeater", "radio_converter", "radio_mast", "radio_weather_station",
             "bundled_transmitter", "bundled_receiver"):
    write(items / f"{name}.json", {"parent": f"{MOD}:block/{name}"})
write(items / "aviation_beacon.json", {"parent": f"{MOD}:block/aviation_beacon_floor_red"})
write(items / "radio_lamp.json", {"parent": f"{MOD}:block/radio_lamp"})
write(items / "radio_battery.json", {"parent": f"{MOD}:block/radio_battery_4"})  # v0.49.0
write(items / "solar_panel.json", {"parent": f"{MOD}:block/solar_panel"})  # v0.50.0
write(items / "radio_telegraph.json", {"parent": f"{MOD}:block/radio_telegraph"})  # v0.51.0
write(items / "dish_rotator.json", {"parent": f"{MOD}:block/dish_rotator"})  # v0.52.0
write(items / "coverage_map.json", {"parent": "minecraft:item/generated", "textures": {"layer0": tex("coverage_map", "item")}})

for tier in ("iron", "gold", "diamond", "netherite", "dish", "ender"):
    write(items / f"{tier}_antenna.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": tex(f"{tier}_antenna", "item")}})
write(items / "compact_radio_receiver.json", {"parent": f"{MOD}:block/compact_radio_receiver_inventory"})
write(items / "compact_radio_transmitter.json", {"parent": f"{MOD}:block/compact_radio_transmitter_inventory"})
write(items / "radio_motion_sensor.json", {"parent": f"{MOD}:block/radio_motion_sensor_inventory"})
write(items / "code_keypad.json", {"parent": f"{MOD}:block/code_keypad_inventory"})
write(items / "wall_redstone.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": tex("wall_redstone", "item")},
})
def remote_models(name):
    """Base model plus green/red versions, picked by the remote's "light" property after a press."""
    for variant in ("heard", "missed"):
        write(items / f"{name}_{variant}.json", {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": tex(f"{name}_{variant}", "item")},
        })
    write(items / f"{name}.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": tex(name, "item")},
        "overrides": [
            {"predicate": {f"{MOD}:light": 0.5}, "model": f"{MOD}:item/{name}_heard"},
            {"predicate": {f"{MOD}:light": 1.0}, "model": f"{MOD}:item/{name}_missed"},
        ],
    })


remote_models("remote_control")


def multi_remote_models():
    """Like remote_models, but the four buttons are separate white layers (1-4) that the game tints
    with each button's color. (Item models allow 5 layers at most: layer0 to layer4.)"""
    buttons = {f"layer{i}": tex(f"multi_remote_button_{i}", "item") for i in range(1, 5)}
    for variant in ("heard", "missed"):
        write(items / f"multi_remote_{variant}.json", {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": tex(f"multi_remote_{variant}", "item"), **buttons},
        })
    write(items / "multi_remote.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": tex("multi_remote", "item"), **buttons},
        "overrides": [
            {"predicate": {f"{MOD}:light": 0.5}, "model": f"{MOD}:item/multi_remote_heard"},
            {"predicate": {f"{MOD}:light": 1.0}, "model": f"{MOD}:item/multi_remote_missed"},
        ],
    })


multi_remote_models()
write(items / "signal_scanner.json", {
    "parent": "minecraft:item/handheld",
    "textures": {"layer0": tex("signal_scanner", "item")},
})
# v0.40.0: the Signal Meter's needle follows the meter_level property (0, 0.33, 0.66, 1).
for i in range(1, 4):
    write(items / f"signal_meter_{i}.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": tex(f"signal_meter_{i}", "item")},
    })
write(items / "signal_meter.json", {
    "parent": "minecraft:item/handheld",
    "textures": {"layer0": tex("signal_meter", "item")},
    "overrides": [{"predicate": {f"{MOD}:meter_level": v}, "model": f"{MOD}:item/signal_meter_{i}"}
                  for i, v in ((1, 0.3), (2, 0.6), (3, 0.9))],
})
write(items / "pager_on.json", {
    "parent": "minecraft:item/handheld",
    "textures": {"layer0": tex("pager_on", "item")},
})
write(items / "pager.json", {
    "parent": "minecraft:item/handheld",
    "textures": {"layer0": tex("pager", "item")},
    "overrides": [{"predicate": {f"{MOD}:pager_on": 1.0}, "model": f"{MOD}:item/pager_on"}],
})
write(items / "frequency_tuner.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": tex("frequency_tuner", "item")},
})

# ---------------------------------------------------------------- blockstates

states = ASSETS / "blockstates"
# v0.45.0: tower tops (multipart): the block lit or not, plus its antenna's fittings if it has one.
ANTENNAS = ("iron", "gold", "diamond", "netherite", "ender")
ANTENNA_TEXTURE = {"iron": "minecraft:block/iron_block", "gold": "minecraft:block/gold_block",
                   "diamond": "minecraft:block/diamond_block", "netherite": "minecraft:block/netherite_block",
                   "ender": "minecraft:block/crying_obsidian"}  # v0.50.0


def antenna_model(tier):
    """Bands round the antenna rod and caps on the dipole's ends, in the tier's metal (just over the
    rod and arms, so nothing z-fights)."""
    t = "#metal"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": ANTENNA_TEXTURE[tier], "metal": ANTENNA_TEXTURE[tier]},
        "elements": [
            box([6.5, 16, 6.5], [9.5, 17.5, 9.5], t, [6, 6, 9, 7.5], [6.5, 6.5, 9.5, 9.5]),
            box([6.6, 24, 6.6], [9.4, 25.2, 9.4], t, [6, 6, 9, 7.2], [6.6, 6.6, 9.4, 9.4]),
            box([6.6, 26.6, 6.6], [9.4, 27.8, 9.4], t, [6, 6, 9, 7.2], [6.6, 6.6, 9.4, 9.4]),
            box([2.4, 21.6, 7.1], [3.6, 23.4, 8.9], t, [0, 0, 1.2, 1.8], [0, 0, 1.2, 1.8]),
            box([12.4, 21.6, 7.1], [13.6, 23.4, 8.9], t, [0, 0, 1.2, 1.8], [0, 0, 1.2, 1.8]),
            box([7.1, 18.6, 2.4], [8.9, 20.4, 3.6], t, [0, 0, 1.8, 1.8], [0, 0, 1.8, 1.2]),
            box([7.1, 18.6, 12.4], [8.9, 20.4, 13.6], t, [0, 0, 1.8, 1.8], [0, 0, 1.8, 1.2]),
        ],
    }


for tier in ANTENNAS:
    write(models / f"antenna_{tier}.json", antenna_model(tier))


def solid(frm, to, texture):
    """A box with every face's UV the size of the face, centred in the texture (the dish sits above
    the block, where Minecraft's automatic UVs would run off the texture)."""
    w, h, d = (to[i] - frm[i] for i in range(3))

    def uv(a, b):
        return [round((16 - a) / 2, 3), round((16 - b) / 2, 3), round((16 + a) / 2, 3), round((16 + b) / 2, 3)]
    faces = {"north": uv(w, h), "south": uv(w, h), "east": uv(d, h), "west": uv(d, h), "up": uv(w, d), "down": uv(w, d)}
    return {"from": frm, "to": to, "faces": {f: {"texture": texture, "uv": v} for f, v in faces.items()}}


def dish_model(angle):
    """The Dish Antenna (v0.48.0): a round dish on the north side of the rod, facing north, with a
    feed arm and horn in front of it; turned {@code angle} degrees about the rod (blockstates turn
    it the rest of the way, in quarter turns)."""
    plate, rod = "#plate", "#rod"
    elements = [
        # the dish: three overlapping slabs, a hair apart so no faces z-fight, for a roundish outline
        solid([3.5, 22, 4.5], [12.5, 28, 5.5], plate),
        solid([5, 20.5, 4.6], [11, 29.5, 5.6], plate),
        solid([4.2, 21.2, 4.7], [11.8, 28.8, 5.7], plate),
        solid([6, 23, 5.7], [10, 27, 6.3], plate),              # its rounded back
        solid([7.2, 24.2, 6.3], [8.8, 25.8, 7], rod),           # the mount to the rod
        solid([7.6, 24.6, 1.8], [8.4, 25.4, 4.5], rod),         # the feed arm
        solid([7, 24, 1], [9, 26, 1.8], "#horn"),               # the feed horn
    ]
    if angle:
        for element in elements:
            element["rotation"] = {"origin": [8, 25, 8], "axis": "y", "angle": angle}
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "minecraft:block/iron_block", "plate": "minecraft:block/smooth_stone",
                         "rod": tex("radio_mast_post"), "horn": "minecraft:block/gray_concrete"},
            "elements": elements}


# Aims 0-15 (north, north-north-east ... clockwise): quarter turns from the blockstate, the rest
# from turning the model's elements (Minecraft only turns elements by -45, -22.5, 22.5 or 45).
DISH_ANGLES = {"0": 0, "cw22": -22.5, "cw45": -45, "ccw22": 22.5}
for key, angle in DISH_ANGLES.items():
    write(models / f"antenna_dish_{key}.json", dish_model(angle))


def dish_part(aim):
    quarter, rest = divmod(aim, 4)
    key, turns = [("0", quarter), ("cw22", quarter), ("cw45", quarter), ("ccw22", quarter + 1)][rest]
    apply = {"model": f"{MOD}:block/antenna_dish_{key}"}
    if turns % 4:
        apply["y"] = turns % 4 * 90
    return {"when": {"antenna": "dish", "rotation": str(aim)}, "apply": apply}


def tower_states(name, lit_property):
    parts = [{"when": {lit_property: "false"}, "apply": {"model": f"{MOD}:block/{name}"}},
             {"when": {lit_property: "true"}, "apply": {"model": f"{MOD}:block/{name}_on"}}]
    parts += [{"when": {"antenna": tier}, "apply": {"model": f"{MOD}:block/antenna_{tier}"}} for tier in ANTENNAS]
    parts += [dish_part(aim) for aim in range(16)]  # v0.48.0
    write(states / f"{name}.json", {"multipart": parts})


tower_states("radio_transmitter", "powered")
tower_states("radio_repeater", "lit")
tower_states("radio_converter", "lit")
tower_states("radio_signal_recorder", "powered")
tower_states("radio_timer_transmitter", "powered")
tower_states("radio_weather_station", "powered")  # v0.53.0
tower_states("bundled_transmitter", "powered")  # v0.54.0
write(states / "bundled_receiver.json", {"variants": {
    "lit=false": {"model": f"{MOD}:block/bundled_receiver"}, "lit=true": {"model": f"{MOD}:block/bundled_receiver_on"}}})

# v0.54.0: Bundled Cable, a core and an arm (pointing north) turned each way it joins.
def cable_faces(uv):
    return {f: {"texture": "#cable", "uv": uv} for f in ("north", "south", "east", "west", "up", "down")}

write(models / "bundled_cable_core.json", {
    "parent": "minecraft:block/block", "textures": {"particle": tex("bundled_cable"), "cable": tex("bundled_cable")},
    "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": cable_faces([5, 5, 11, 11])}]})
write(models / "bundled_cable_arm.json", {
    "parent": "minecraft:block/block", "textures": {"particle": tex("bundled_cable"), "cable": tex("bundled_cable")},
    "elements": [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": {
        "north": {"texture": "#cable", "uv": [5, 5, 11, 11], "cullface": "north"},
        "east": {"texture": "#cable", "uv": [0, 5, 5, 11]}, "west": {"texture": "#cable", "uv": [0, 5, 5, 11]},
        "up": {"texture": "#cable", "uv": [5, 0, 11, 5], "rotation": 90}, "down": {"texture": "#cable", "uv": [5, 0, 11, 5], "rotation": 90}}}]})
ARM_TURN = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
write(states / "bundled_cable.json", {"multipart": [{"apply": {"model": f"{MOD}:block/bundled_cable_core"}}] + [
    {"when": {side: "true"}, "apply": {"model": f"{MOD}:block/bundled_cable_arm", **turn}} for side, turn in ARM_TURN.items()]})
write(items / "bundled_cable.json", {
    "parent": "minecraft:block/block", "textures": {"particle": tex("bundled_cable"), "cable": tex("bundled_cable")},
    "elements": [{"from": [5, 5, 0], "to": [11, 11, 16], "faces": {
        "north": {"texture": "#cable", "uv": [5, 5, 11, 11]}, "south": {"texture": "#cable", "uv": [5, 5, 11, 11]},
        "east": {"texture": "#cable", "uv": [0, 5, 16, 11]}, "west": {"texture": "#cable", "uv": [0, 5, 16, 11]},
        "up": {"texture": "#cable", "uv": [0, 5, 16, 11], "rotation": 90}, "down": {"texture": "#cable", "uv": [0, 5, 16, 11], "rotation": 90}}}],
    "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]}}})

# Cable Tap: a small casing with a colored (tinted) band and a collar up to the cable; turned like a piston.
def tap_model(output):
    side = "cable_tap_out" if output else "cable_tap_in"
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": tex(side), "side": tex(side), "end": tex("radio_bottom"), "band": tex("cable_tap_band"),
                         "cable": tex("bundled_cable")},
            "elements": [
                {"from": [3, 0, 3], "to": [13, 10, 13], "faces": {
                    **{f: {"texture": "#side", "uv": [3, 6, 13, 16]} for f in SIDES},
                    "up": {"texture": "#end", "uv": [3, 3, 13, 13]}, "down": {"texture": "#end", "uv": [3, 3, 13, 13], "cullface": "down"}}},
                {"from": [2.9, 1, 2.9], "to": [13.1, 4, 13.1], "faces": {
                    f: {"texture": "#band", "uv": [3, 0, 13, 3], "tintindex": 0} for f in SIDES}},
                {"from": [5, 10, 5], "to": [11, 16, 11], "faces": {
                    **{f: {"texture": "#cable", "uv": [5, 0, 11, 6]} for f in SIDES},
                    "up": {"texture": "#cable", "uv": [5, 5, 11, 11], "cullface": "up"}}},
            ]}

write(models / "cable_tap_in.json", tap_model(False))
write(models / "cable_tap_out.json", tap_model(True))
TAP_TURN = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
write(states / "cable_tap.json", {"variants": {
    f"facing={facing},output={'true' if out else 'false'}": {"model": f"{MOD}:block/cable_tap_{'out' if out else 'in'}", **turn}
    for facing, turn in TAP_TURN.items() for out in (False, True)}})
write(items / "cable_tap.json", {"parent": f"{MOD}:block/cable_tap_in"})

# Guy Wire Anchor: a concrete footing with an eye bolt (the wire is drawn in the game).
write(models / "guy_anchor.json", {
    "parent": "minecraft:block/block",
    "textures": {"particle": "minecraft:block/gray_concrete", "base": "minecraft:block/gray_concrete", "bolt": tex("radio_mast_post")},
    "elements": [
        {"from": [3, 0, 3], "to": [13, 3, 13], "faces": {
            **{f: {"texture": "#base", "uv": [3, 13, 13, 16]} for f in SIDES},
            "up": {"texture": "#base", "uv": [3, 3, 13, 13]}, "down": {"texture": "#base", "uv": [3, 3, 13, 13], "cullface": "down"}}},
        {"from": [7, 3, 7], "to": [9, 6, 9], "faces": {f: {"texture": "#bolt", "uv": [6, 0, 8, 3]} for f in SIDES + ["up"]}},
        {"from": [6.5, 6, 7.5], "to": [9.5, 7, 8.5], "faces": {f: {"texture": "#bolt", "uv": [6, 0, 9, 1]} for f in SIDES + ["up", "down"]}},
    ]})
write(states / "guy_anchor.json", {"variants": {"": {"model": f"{MOD}:block/guy_anchor"}}})
write(items / "guy_anchor.json", {"parent": f"{MOD}:block/guy_anchor"})

# v0.55.0 Tower Platform: a grating deck, and a rail on each side that has nothing next to it.
PLATFORM_TEXTURES = {"particle": tex("tower_platform_deck"), "deck": tex("tower_platform_deck"), "rail": tex("tower_platform_rail")}
PLATFORM_DECK = {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
    **{f: {"texture": "#deck", "uv": [0, 14, 16, 16]} for f in SIDES},
    "up": {"texture": "#deck", "uv": [0, 0, 16, 16]}, "down": {"texture": "#deck", "uv": [0, 0, 16, 16], "cullface": "down"}}}
def platform_rail(element_from, element_to):
    return {"from": element_from, "to": element_to, "faces": {
        **{f: {"texture": "#rail", "uv": [0, 2, 16, 16]} for f in SIDES},
        "up": {"texture": "#rail", "uv": [0, 2, 16, 3]}}}
write(models / "tower_platform_deck.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                             "textures": PLATFORM_TEXTURES, "elements": [PLATFORM_DECK]})
write(models / "tower_platform_rail.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                             "textures": PLATFORM_TEXTURES, "elements": [platform_rail([0, 2, 0], [16, 16, 1])]})
write(models / "tower_platform_inventory.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                  "textures": PLATFORM_TEXTURES,
                                                  "elements": [PLATFORM_DECK, platform_rail([0, 2, 0], [16, 16, 1]),
                                                               platform_rail([0, 2, 0], [1, 16, 16])]})
RAIL_TURN = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
write(states / "tower_platform.json", {"multipart": [{"apply": {"model": f"{MOD}:block/tower_platform_deck"}}] + [
    {"when": {side: "true"}, "apply": {"model": f"{MOD}:block/tower_platform_rail", **turn}} for side, turn in RAIL_TURN.items()]})
write(items / "tower_platform.json", {"parent": f"{MOD}:block/tower_platform_inventory"})
tower_states("radio_jammer", "powered")
write(states / "radio_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
write(states / "radio_toggle_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_toggle_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
# v0.43.0: the status board, a thin panel on the wall south of it when it faces north.
write(models / "radio_status_board.json", {
    "parent": "minecraft:block/block",
    "textures": {"particle": f"{MOD}:block/radio_status_board_side", "front": f"{MOD}:block/radio_status_board_front",
                 "side": f"{MOD}:block/radio_status_board_side"},
    "elements": [{"from": [1, 1, 14], "to": [15, 15, 16], "faces": {
        "north": {"uv": [1, 1, 15, 15], "texture": "#front"},
        "south": {"uv": [1, 1, 15, 15], "texture": "#side", "cullface": "south"},
        "east": {"uv": [0, 1, 2, 15], "texture": "#side"},
        "west": {"uv": [14, 1, 16, 15], "texture": "#side"},
        "up": {"uv": [1, 14, 15, 16], "texture": "#side"},
        "down": {"uv": [1, 0, 15, 2], "texture": "#side"}}}],
    "display": {
        "gui": {"rotation": [0, 180, 0], "translation": [0, 0, -3], "scale": [0.8, 0.8, 0.8]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, -6], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    },
})
write(states / "radio_status_board.json", {"variants": {
    "facing=north": {"model": f"{MOD}:block/radio_status_board"},
    "facing=east": {"model": f"{MOD}:block/radio_status_board", "y": 90},
    "facing=south": {"model": f"{MOD}:block/radio_status_board", "y": 180},
    "facing=west": {"model": f"{MOD}:block/radio_status_board", "y": 270},
}})
# v0.50.0: the Solar Panel, a three-pixel slab with cells on top
write(models / "solar_panel.json", {
    "parent": "minecraft:block/block",
    "textures": {"particle": tex("solar_panel_top"), "top": tex("solar_panel_top"), "side": tex("solar_panel_side"),
                 "bottom": tex("radio_bottom")},
    "elements": [{"from": [0, 0, 0], "to": [16, 3, 16], "faces": {
        "up": {"texture": "#top", "uv": [0, 0, 16, 16]},
        "down": {"texture": "#bottom", "uv": [0, 0, 16, 16], "cullface": "down"},
        **{face: {"texture": "#side", "uv": [0, 13, 16, 16], "cullface": face} for face in ("north", "south", "east", "west")},
    }}],
})
write(states / "solar_panel.json", {"variants": {"": {"model": f"{MOD}:block/solar_panel"}}})

# v0.51.0: the Radio Telegraph, a desk with a brass key (its knob towards the front) and a sounder
# with a lamp at the back; facing north here, turned by the blockstate.
def telegraph_model(lit):
    def box(frm, to, tex_name, faces=("north", "south", "east", "west", "up", "down"), uv=None, glow=False, cull=None):
        element = {"from": frm, "to": to, "faces": {}}
        for face in faces:
            entry = {"texture": tex_name}
            if uv and face in uv:
                entry["uv"] = uv[face]
            if cull and face in cull:
                entry["cullface"] = face
            element["faces"][face] = entry
        if glow:
            element["neoforge_data"] = GLOW
        return element
    elements = [
        # the desk: a 4-pixel slab of dark wood with a steel band
        box([0, 0, 0], [16, 4, 16], "#side", faces=("north", "south", "east", "west"),
            uv={f: [0, 12, 16, 16] for f in ("north", "south", "east", "west")}, cull=("north", "south", "east", "west")),
        box([0, 0, 0], [16, 4, 16], "#top", faces=("up",), uv={"up": [0, 0, 16, 16]}),
        box([0, 0, 0], [16, 4, 16], "#bottom", faces=("down",), cull=("down",)),
        # the key: a brass bar on a pivot, with a black knob at the front
        box([7, 4, 4], [9, 6, 11], "#brass", uv={"north": [7, 6, 9, 8], "south": [7, 6, 9, 8], "east": [4, 6, 11, 8],
                                                "west": [4, 6, 11, 8], "up": [7, 4, 9, 11], "down": [7, 4, 9, 11]}),
        box([6, 4, 8], [10, 5, 10], "#brass"),
        box([6.5, 6, 2], [9.5, 8, 5], "#knob"),
        # the sounder at the back, its lamp facing the front
        box([3, 4, 11], [13, 10, 15], "#sounder", faces=("south", "east", "west", "up"),
            uv={"south": [3, 6, 13, 12], "east": [11, 6, 15, 12], "west": [1, 6, 5, 12], "up": [3, 1, 13, 5]}),
        box([3, 4, 11], [13, 10, 15], "#lamp", faces=("north",), uv={"north": [3, 6, 13, 12]}, glow=lit),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("radio_telegraph_top"), "top": tex("radio_telegraph_top"), "side": tex("radio_telegraph_side"),
                     "bottom": tex("radio_bottom"), "brass": tex("radio_telegraph_brass"), "knob": tex("radio_telegraph_knob"),
                     "sounder": tex("radio_telegraph_sounder"), "lamp": tex("radio_telegraph_lamp" + ("_on" if lit else ""))},
        "elements": elements,
    }


write(models / "radio_telegraph.json", telegraph_model(False))
write(models / "radio_telegraph_on.json", telegraph_model(True))
write(states / "radio_telegraph.json", {"variants": {
    f"facing={facing},powered={powered}": {"model": f"{MOD}:block/radio_telegraph" + ("_on" if powered == "true" else ""),
                                            **({"y": y} if y else {})}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)) for powered in ("false", "true")
}})

# v0.49.0: the Radio Battery, its bars lit by charge
for n in range(5):
    write(models / f"radio_battery_{n}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": tex(f"radio_battery_side_{n}"), "top": tex("radio_battery_top"), "bottom": tex("radio_bottom")}})
write(states / "radio_battery.json", {"variants": {f"charge={n}": {"model": f"{MOD}:block/radio_battery_{n}"} for n in range(5)}})
write(states / "radio_speaker.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_speaker" + ("_on" if p > 0 else "")} for p in range(16)
}})
write(states / "radio_counter_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_counter_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
# v0.45.0: the Radio Lamp. A steel frame with a window, round a glass core tinted by the client (its
# dye color, brighter the stronger the signal); lit, the core is drawn full bright.
def lamp_model(lit):
    core = {"from": [0.6, 0.6, 0.6], "to": [15.4, 15.4, 15.4],
            "faces": {f: {"texture": "#glass", "tintindex": 0} for f in SIDES + ["up", "down"]}}
    if lit:
        core["neoforge_data"] = GLOW
        core["shade"] = False
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": tex("radio_lamp_frame"), "frame": tex("radio_lamp_frame"),
                     "glass": tex("radio_lamp_glass")},
        "elements": [
            core,
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": casing_faces_with("#frame")},
        ],
    }


write(models / "radio_lamp.json", lamp_model(False))
write(models / "radio_lamp_on.json", lamp_model(True))
write(states / "radio_lamp.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_lamp" + ("_on" if p > 0 else "")} for p in range(16)
}})
write(states / "radio_delay_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_delay_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
write(states / "radio_logic_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_logic_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
write(states / "radio_inverted_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_inverted_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
BUTTON_ROTATIONS = {
    ("floor", "north"): {}, ("floor", "east"): {"y": 90}, ("floor", "south"): {"y": 180}, ("floor", "west"): {"y": 270},
    ("wall", "north"): {"x": 90}, ("wall", "east"): {"x": 90, "y": 90},
    ("wall", "south"): {"x": 90, "y": 180}, ("wall", "west"): {"x": 90, "y": 270},
    ("ceiling", "north"): {"x": 180, "y": 180}, ("ceiling", "east"): {"x": 180, "y": 270},
    ("ceiling", "south"): {"x": 180}, ("ceiling", "west"): {"x": 180, "y": 90},
}
compact_variants = {}
for (face, facing), rot in BUTTON_ROTATIONS.items():
    for p in range(16):
        variant = {"model": f"{MOD}:block/compact_radio_receiver" + ("_on" if p > 0 else ""), **rot}
        compact_variants[f"face={face},facing={facing},power={p}"] = variant
write(states / "compact_radio_receiver.json", {"variants": compact_variants})
compact_tx_variants = {}
for (face, facing), rot in BUTTON_ROTATIONS.items():
    for powered in ("false", "true"):
        compact_tx_variants[f"face={face},facing={facing},powered={powered}"] = {
            "model": f"{MOD}:block/compact_radio_transmitter" + ("_on" if powered == "true" else ""), **rot}
write(states / "compact_radio_transmitter.json", {"variants": compact_tx_variants})
write(states / "radio_motion_sensor.json", {"variants": {
    key: {**variant, "model": variant["model"].replace("compact_radio_transmitter", "radio_motion_sensor")}
    for key, variant in compact_tx_variants.items()
}})
keypad_variants = {}
for (face, facing), rot in BUTTON_ROTATIONS.items():
    for powered in ("false", "true"):
        keypad_variants[f"face={face},facing={facing},powered={powered}"] = {
            "model": f"{MOD}:block/code_keypad" + ("_on" if powered == "true" else ""), **rot}
write(states / "code_keypad.json", {"variants": keypad_variants})
WALL_ROTATION = {"south": 0, "west": 90, "north": 180, "east": 270}
wall_parts = []
for facing, y in WALL_ROTATION.items():
    def part(when, model):
        apply = {"model": f"{MOD}:block/{model}"}
        if y:
            apply["y"] = y
        wall_parts.append({"when": {"facing": facing, **when}, "apply": apply})
    part({}, "wall_redstone_dot")
    for arm in ("up", "down", "left", "right"):
        part({arm: "true"}, f"wall_redstone_{arm}")
# v0.52.0: on a ceiling (facing down) the same models turned up onto the underside of the block
# above; its arms then run up = south, down = north, left = west, right = east.
wall_parts.append({"when": {"facing": "down"}, "apply": {"model": f"{MOD}:block/wall_redstone_dot", "x": 270}})
for arm in ("up", "down", "left", "right"):
    wall_parts.append({"when": {"facing": "down", arm: "true"}, "apply": {"model": f"{MOD}:block/wall_redstone_{arm}", "x": 270}})
write(states / "wall_redstone.json", {"multipart": wall_parts})
write(states / "radio_mast.json", {"variants": {"": {"model": f"{MOD}:block/radio_mast"}}})
# v0.52.0: the Dish Rotator, a mast segment with a gearbox round it, its arrow (the way 0 points) on
# the front (north here) and on top.
write(models / "dish_rotator.json", {
    "parent": "minecraft:block/block",
    "render_type": "minecraft:cutout",
    "textures": {"particle": tex("dish_rotator_side"), "post": tex("radio_mast_post"), "lattice": tex("radio_mast_lattice"),
                 "side": tex("dish_rotator_side"), "front": tex("dish_rotator_front"), "top": tex("dish_rotator_top")},
    "elements": [
        post(3, 3), post(11, 3), post(3, 11), post(11, 11),
        panel_ns(4), panel_ns(12), panel_ew(4), panel_ew(12),
        {"from": [2, 4, 2], "to": [14, 12, 14], "faces": {
            "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
            "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#top"}}},
    ],
})
write(states / "dish_rotator.json", {"variants": {
    f"facing={facing}": {"model": f"{MOD}:block/dish_rotator", **({"y": y} if y else {})}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

# ---------------------------------------------------------------- lang

# v0.44.0: the Aviation Beacon. A red (or white) dome on a post: standing on a block, on a bracket
# from a wall (the bracket reaches 3 pixels into the wall's block, so it touches a mast too), or
# hanging from a ceiling. The client draws the light over the dome while it's on.
def beacon_faces(uv_side, uv_end, texture):
    faces = {f: {"texture": texture, "uv": uv_side} for f in SIDES}
    faces["up"] = {"texture": texture, "uv": uv_end}
    faces["down"] = {"texture": texture, "uv": uv_end}
    return faces


def beacon_part(frm, to, texture, uv_side=None, uv_end=None):
    w = to[0] - frm[0]
    h = to[1] - frm[1]
    uv_side = uv_side or [8 - w / 2, 8 - h / 2, 8 + w / 2, 8 + h / 2]
    uv_end = uv_end or [8 - w / 2, 8 - w / 2, 8 + w / 2, 8 + w / 2]
    return {"from": frm, "to": to, "faces": beacon_faces(uv_side, uv_end, texture)}


def beacon_model(face, color):
    if face == "floor":
        elements = [
            beacon_part([5, 0, 5], [11, 1, 11], "#base"),
            beacon_part([6.5, 1, 6.5], [9.5, 8, 9.5], "#base"),
            beacon_part([5, 8, 5], [11, 14, 11], "#dome", [5, 2, 11, 8], [5, 5, 11, 11]),
            beacon_part([6, 14, 6], [10, 15.5, 10], "#base"),
        ]
    elif face == "ceiling":
        elements = [
            beacon_part([5, 15, 5], [11, 16, 11], "#base"),
            beacon_part([7, 9, 7], [9, 15, 9], "#base"),
            beacon_part([5, 3, 5], [11, 9, 11], "#dome", [5, 2, 11, 8], [5, 5, 11, 11]),
            beacon_part([6, 1.5, 6], [10, 3, 10], "#base"),
        ]
    else:  # wall, facing north: the wall is to the south
        elements = [
            {"from": [7, 6, 7], "to": [9, 7.5, 19], "faces": beacon_faces([0, 6, 12, 7.5], [7, 0, 9, 12], "#base")},
            beacon_part([5.5, 7.5, 5.5], [10.5, 8.5, 10.5], "#base"),
            beacon_part([5, 8.5, 5], [11, 14.5, 11], "#dome", [5, 2, 11, 8], [5, 5, 11, 11]),
            beacon_part([6, 14.5, 6], [10, 15.5, 10], "#base"),
        ]
    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex(f"aviation_beacon_{color}"), "dome": tex(f"aviation_beacon_{color}"),
                     "base": tex("aviation_beacon_base")},
        "elements": elements,
    }
    if face == "floor":
        model["display"] = {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [1.0, 1.0, 1.0]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
        }
    return model


beacon_variants = {}
for color in ("red", "white"):
    for face in ("floor", "wall", "ceiling"):
        write(models / f"aviation_beacon_{face}_{color}.json", beacon_model(face, color))
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for face in ("floor", "wall", "ceiling"):
            variant = {"model": f"{MOD}:block/aviation_beacon_{face}_{color}"}
            if y:
                variant["y"] = y
            beacon_variants[f"color={color},face={face},facing={facing}"] = variant
write(states / "aviation_beacon.json", {"variants": beacon_variants})

write(ASSETS / "lang" / "en_us.json", {
    # v0.52.0: Wall Redstone on ceilings and round corners, and the Dish Rotator
    f"tooltip.{MOD}.wall_redstone": "Sticks to walls and ceilings and runs round outside corners. Loses 1 strength per block, like dust.",
    f"block.{MOD}.dish_rotator": "Dish Rotator",
    f"tooltip.{MOD}.dish_rotator": "A mast segment that turns the Dish Antenna on top: redstone 0 points it the way its arrow does, each step 22.5\u00b0 clockwise.",
    f"tooltip.{MOD}.dish_rotator_use": "Place it in the tower's mast column, or right-click the tower with it to put it in under the top; its arrow (the way you look) is where 0 points. Its redstone doesn't go up to the tower.",
    f"message.{MOD}.rotator_added": "Dish Rotator added under the %1$s (%2$s masts up, reach %3$s \u2192 %4$s blocks); redstone 0 points its dish %5$s",
    f"message.{MOD}.rotator_grown": "Dish Rotator added on top of the masts: %s tall",
    f"message.{MOD}.dish_rotated": "The Dish Rotator in its mast turns this %1$s's dish (it points %2$s now)",
    f"jade.{MOD}.rotator_power": "Redstone: %1$s (0 points %2$s)",
    f"jade.{MOD}.rotator_turns": "Points the dish on top %s",
    f"jade.{MOD}.rotator_no_dish": "No Dish Antenna on top to turn",
    f"jade.{MOD}.rotator_overruled": "A Dish Rotator higher up turns the dish instead",
    f"jade.{MOD}.dish_rotated": "Turned by a Dish Rotator (redstone %s)",
    # v0.54.0: bundled cable, guy wires and storm damage
    f"block.{MOD}.bundled_cable": "Bundled Cable",
    f"block.{MOD}.bundled_cable.tooltip": "Carries all 16 colors at once, each 0-15, with no loss along the way. Cable Taps put redstone onto a color and take it off again; a Bundled Radio Transmitter sends the whole cable over the radio.",
    f"block.{MOD}.cable_tap": "Cable Tap",
    f"block.{MOD}.cable_tap.tooltip": "Put it against a Bundled Cable. In (the default): the redstone it gets goes onto its color. Out: it gives its color out as redstone. Right-click to switch; dye it to pick the color.",
    f"block.{MOD}.bundled_transmitter": "Bundled Radio Transmitter",
    f"block.{MOD}.bundled_transmitter.tooltip": "Sends a whole Bundled Cable: tuned to F, white goes out on F, orange on F+1 ... black on F+15. Feed it with a cable beside it or beside any mast of its tower.",
    f"block.{MOD}.bundled_receiver": "Bundled Radio Receiver",
    f"block.{MOD}.bundled_receiver.tooltip": "Hears the 16 frequencies from its own on (F white ... F+15 black) and puts them on the Bundled Cables touching it.",
    f"block.{MOD}.guy_anchor": "Guy Wire Anchor",
    f"block.{MOD}.guy_anchor.tooltip": "Put it on the ground 2 to 12 blocks from a tower: a steel wire runs to its mast. Three on one mast brace the tower, so tornadoes, hurricanes and derechos (Reiko's Weather) can't blow it down.",
    f"block.{MOD}.tower_platform": "Tower Platform",
    f"block.{MOD}.tower_platform.tooltip": "Right-click a mast with these and they go in all round it at that height, with rails. You come up through it climbing the mast; sneak beside the mast to go back down.",
    f"tooltip.{MOD}.mast_climb": "Walk into a mast to climb it; let go to slide down, sneak to hold still.",
    f"jade.{MOD}.found_tower": "An old radio tower, found in the world",
    f"jade.{MOD}.village_station": "A village's radio station",
    # v0.58.0: raid alerts and computers (CC: Tweaked)
    f"jade.{MOD}.raid_coming": "Raid! The pillagers are on their way",
    f"jade.{MOD}.raid_alert": "Raid! Wave %s of %s",
    f"jade.{MOD}.computer_signal": "A computer sends: %s",
    f"block.{MOD}.village_station.short": "Village Radio",
    f"message.{MOD}.raid_start": "RAID! Pillagers are coming to the village",
    f"message.{MOD}.raid_wave": "Raid: wave %s of %s",
    f"message.{MOD}.raid_victory": "The raid is over: the village held",
    f"message.{MOD}.raid_loss": "The raid is over: the village fell",
    f"message.{MOD}.raid_ended": "The raid has ended",
    f"computer.{MOD}.sender": "Computer %s",
    f"computer.{MOD}.frequency_range": "Frequency must be %s to %s",
    # v0.59.0: the radio upgrade for turtles and pocket computers
    f"upgrade.{MOD}.radio.adjective": "Radio",
    f"computer.{MOD}.turtle": "Turtle %s",
    f"computer.{MOD}.pocket": "Pocket Computer %s",
    f"sign.{MOD}.radio_tower.station": "Radio Station",
    f"sign.{MOD}.radio_tower.frequency": "Frequency",
    # v0.56.0: advancements and the Radio Operator
    f"advancements.{MOD}.root.title": "Reiko's Redstone Signal Tower",
    f"advancements.{MOD}.root.description": "Send redstone through the air",
    f"advancements.{MOD}.first_broadcast.title": "First Broadcast",
    f"advancements.{MOD}.first_broadcast.description": "Put a Radio Transmitter on the air",
    f"advancements.{MOD}.over_the_horizon.title": "Over the Horizon",
    f"advancements.{MOD}.over_the_horizon.description": "Put a transmitter on the air that reaches 500 blocks or more",
    f"advancements.{MOD}.head_for_heights.title": "Head for Heights",
    f"advancements.{MOD}.head_for_heights.description": "Climb a mast and stand on a Tower Platform",
    f"advancements.{MOD}.weathered_the_storm.title": "Weathered the Storm",
    f"advancements.{MOD}.weathered_the_storm.description": "Watch a tall tower braced with guy wires stand through a tornado, hurricane or derecho",
    f"advancements.{MOD}.lost_signal.title": "Lost Signal",
    f"advancements.{MOD}.lost_signal.description": "Find an old radio tower out in the world",
    f"advancements.{MOD}.finders_keepers.title": "Finders Keepers",
    f"advancements.{MOD}.finders_keepers.description": "Bring an old radio tower back on the air",
    f"advancements.{MOD}.call_sign.title": "Call Sign",
    f"advancements.{MOD}.call_sign.description": "Trade with a Radio Operator",
    f"entity.minecraft.villager.{MOD}.radio_operator": "Radio Operator",
    # v0.57.0: JEI and EMI
    f"recipe_viewer.{MOD}.radio_operator_trades": "Radio Operator Trades",
    f"tooltip.{MOD}.bundle_frequencies": "Frequency %1$s (uses %2$s)",
    f"message.{MOD}.cable_status": "Bundled Cable | %1$s cables, %2$s devices | %3$s",
    f"message.{MOD}.tap_in": "Cable Tap: %1$s, in (puts %2$s onto it)",
    f"message.{MOD}.tap_out": "Cable Tap: %1$s, out (gives %2$s out)",
    f"message.{MOD}.channel": "%1$s %2$s",
    f"message.{MOD}.channels_none": "nothing on",
    f"message.{MOD}.bundled_tx_status": "Bundled Radio Transmitter | Frequency %1$s (%2$s) | Sending %3$s | Reach %4$s blocks",
    f"message.{MOD}.bundled_rx_status": "Bundled Radio Receiver | Frequency %1$s (%2$s) | Hears %3$s",
    f"message.{MOD}.anchor_none": "No Radio Mast 2 to 12 blocks from this Guy Wire Anchor to brace",
    f"message.{MOD}.anchor_braced": "Bracing the mast at %1$s, %2$s, %3$s (%4$s tall): %5$s of %6$s guy wires, it stands in any storm",
    f"message.{MOD}.anchor_more": "Bracing the mast at %1$s, %2$s, %3$s (%4$s tall): %5$s of %6$s guy wires",
    f"message.{MOD}.storm_damaged_owner": "Storm damage: %1$s blew down your %2$s at %3$s, %4$s, %5$s. Right-click it with an Iron Ingot to repair it",
    f"message.{MOD}.storm_repaired": "Repaired the %s: it's back up",
    f"message.{MOD}.storm_cause.tornado": "a tornado",
    f"message.{MOD}.storm_cause.derecho": "a derecho",
    f"message.{MOD}.storm_cause.hurricane": "a hurricane",
    f"jade.{MOD}.bundle_range": "Bundle on %s",
    f"jade.{MOD}.bundle_sending": "Sending: %s",
    f"jade.{MOD}.bundle_hears": "Hears: %s",
    f"jade.{MOD}.cable_carries": "Carries: %s",
    f"jade.{MOD}.cable_size": "%1$s cables, %2$s devices",
    f"jade.{MOD}.anchor_braces": "Braces the mast at %1$s, %2$s, %3$s (%4$s tall)",
    f"jade.{MOD}.anchor_enough": "%1$s of %2$s guy wires: braced",
    f"jade.{MOD}.anchor_short": "%1$s of %2$s guy wires",
    f"jade.{MOD}.anchor_none": "No mast to brace (2 to 12 blocks out)",
    f"jade.{MOD}.storm_damaged": "Blown down by %s: right-click it with an Iron Ingot to repair",
    f"jade.{MOD}.iced": "Iced up: reach -%1$s%% until it thaws (%2$s of %3$s s above freezing)",
    f"jade.{MOD}.braced": "Braced by %s guy wires",
    f"jade.{MOD}.not_braced": "Not braced: %1$s of %2$s guy wires (storms can blow it down)",
    f"jade.{MOD}.braces_short": "Guy wires: %s (too short to blow down)",
    f"screen.{MOD}.audit.kind_bundled_transmitter": "Bundled Transmitter",
    # v0.53.0: the Radio Weather Station, and Reiko's Weather
    f"block.{MOD}.radio_weather_station": "Radio Weather Station",
    f"block.{MOD}.radio_weather_station.short": "Weather Station",
    f"block.{MOD}.radio_weather_station.tooltip": "A transmitter that broadcasts the conditions where it stands: the weather, the time of day or the moon (more with Reiko's Weather). A redstone signal into it pauses it. Right-click it with an empty hand to pick what it reads.",
    f"tooltip.{MOD}.station_reading": "Reads: %1$s (%2$s)",
    f"tooltip.{MOD}.station_announces": "Announces weather changes",
    f"message.{MOD}.station_status": "Weather Station | Frequency %1$s | %2$s: %3$s | Sending %4$s | Reach %5$s blocks",
    f"message.{MOD}.station_set": "Radio Weather Station: %1$s, now %2$s (%3$s)",
    f"message.{MOD}.station_needs_reikos": "%s needs Reiko's Weather",
    f"message.{MOD}.station_announce": "Now: %s",
    f"message.{MOD}.station_announce_severe": "Now: %s. Severe weather!",
    f"message.{MOD}.station_wind": "%1$s km/h from the %2$s",
    f"compass.{MOD}.n": "north", f"compass.{MOD}.ne": "north-east", f"compass.{MOD}.e": "east", f"compass.{MOD}.se": "south-east",
    f"compass.{MOD}.s": "south", f"compass.{MOD}.sw": "south-west", f"compass.{MOD}.w": "west", f"compass.{MOD}.nw": "north-west",
    f"station.{MOD}.mode.weather": "Weather",
    f"station.{MOD}.mode.time": "Time of day",
    f"station.{MOD}.mode.moon": "Moon",
    f"station.{MOD}.mode.conditions": "Weather code",
    f"station.{MOD}.mode.storm": "Storm danger",
    f"station.{MOD}.mode.wind": "Wind",
    f"station.{MOD}.mode.precipitation": "Precipitation",
    f"station.{MOD}.mode.temperature": "Temperature",
    f"station.{MOD}.mode.season": "Season",
    f"station.{MOD}.mode.storm_near": "Nearest storm",
    f"station.{MOD}.scale.weather": "clear 0, rain or snow 8, thunderstorm 15",
    f"station.{MOD}.scale.time": "06:00 is 0, then 1 more every 1.5 hours: noon 4, 18:00 8, midnight 12",
    f"station.{MOD}.scale.moon": "full moon 1 ... new moon 5 ... 8; a Blood Moon 15",
    f"station.{MOD}.scale.conditions": "Reiko's Weather's code: clear 0, rain 4, thunderstorm 6, snow 11, heatwave 15...",
    f"station.{MOD}.scale.storm": "thunder or severe 15, hail or heavy rain 10, rain 5, else 0",
    f"station.{MOD}.scale.wind": "calm 0 to a gale 15",
    f"station.{MOD}.scale.precipitation": "dry 0 to a downpour 15",
    f"station.{MOD}.scale.temperature": "-10 \u00b0C 0, then 1 more every 3 \u00b0C: 20 \u00b0C 10, 35 \u00b0C 15",
    f"station.{MOD}.scale.season": "spring 1, summer 2, autumn 3, winter 4",
    f"station.{MOD}.scale.storm_near": "overhead 15, then 1 less every 100 blocks to its edge",
    f"station.{MOD}.needs_reikos_weather": "Needs Reiko's Weather",
    f"station.{MOD}.blood_moon": "Blood Moon",
    f"station.{MOD}.no_sky": "No sky here",
    f"station.{MOD}.moon.0": "Full moon",
    f"station.{MOD}.moon.1": "Waning gibbous",
    f"station.{MOD}.moon.2": "Last quarter",
    f"station.{MOD}.moon.3": "Waning crescent",
    f"station.{MOD}.moon.4": "New moon",
    f"station.{MOD}.moon.5": "Waxing crescent",
    f"station.{MOD}.moon.6": "First quarter",
    f"station.{MOD}.moon.7": "Waxing gibbous",
    f"station.{MOD}.no_seasons": "No seasons",
    f"station.{MOD}.season_day": "%1$s, day %2$s of %3$s",
    f"station.{MOD}.no_storms": "No storms",
    f"station.{MOD}.not_here": "Reiko's Weather doesn't run here",
    f"station.{MOD}.storm_overhead": "%s overhead",
    f"station.{MOD}.storm_away": "%1$s, %2$s blocks away",
    f"station.{MOD}.percent": "%1$s, %2$s%%",
    f"station.{MOD}.degrees": "%1$s \u00b0C (%2$s \u00b0F)",
    f"screen.{MOD}.station.reading": "Reading",
    f"screen.{MOD}.station.announce": "Announce weather changes",
    f"screen.{MOD}.station.announce_tip": "Sends a message on its frequency when the weather where it stands changes (\"Now: Rain\"): pagers, Radio Telegraphs and Status Boards in its reach show it.",
    f"screen.{MOD}.station.mode.weather_tip": "Rain and thunder where it stands, as towers' reach goes by. Shift-click or scroll to go back.",
    f"screen.{MOD}.station.mode.time_tip": "The time of day, in 16 steps: 8 or more is night (18:00 to 06:00).",
    f"screen.{MOD}.station.mode.moon_tip": "Tonight's moon, from full (1) to waxing gibbous (8).",
    f"screen.{MOD}.station.mode.conditions_tip": "Reiko's Weather's weather code, the same as its own Weather Station's.",
    f"screen.{MOD}.station.mode.storm_tip": "How dangerous the weather is, the same as Reiko's Weather Station's.",
    f"screen.{MOD}.station.mode.wind_tip": "How strong the wind is where it stands (higher up is windier).",
    f"screen.{MOD}.station.mode.precipitation_tip": "How hard it rains or snows where it stands.",
    f"screen.{MOD}.station.mode.temperature_tip": "The air temperature where it stands (season, height, time of day and weather count).",
    f"screen.{MOD}.station.mode.season_tip": "The season, from Reiko's Weather (or Serene Seasons through it).",
    f"screen.{MOD}.station.mode.storm_near_tip": "How close the nearest storm is, like Reiko's Weather Radar: bases can get ready before it arrives.",
    f"screen.{MOD}.station.frequency": "Sends on %s",
    f"screen.{MOD}.station.now": "Now: %1$s \u2192 %2$s",
    f"screen.{MOD}.station.needs_reikos": "This reading needs Reiko's Weather",
    f"screen.{MOD}.station.silenced": "Silent: %s is private to %s",
    f"screen.{MOD}.station.paused": "Paused: a redstone signal reaches it",
    f"screen.{MOD}.station.down": "Not sending: knocked out or out of power",
    f"screen.{MOD}.station.will_send": "Done sends %1$s on %2$s",
    f"screen.{MOD}.station.sending": "Sending %1$s on %2$s",
    f"screen.{MOD}.station.sending_zero": "Sending 0 on %s right now",
    f"screen.{MOD}.station.more_with_reikos": "Install Reiko's Weather for 7 more readings",
    f"jade.{MOD}.station_reading": "%1$s: %2$s (%3$s)",
    f"jade.{MOD}.station_needs_reikos": "%s needs Reiko's Weather",
    f"jade.{MOD}.station_scale": "Scale: %s",
    f"jade.{MOD}.station_announces": "Announces weather changes",
    f"screen.{MOD}.audit.kind_weather_station": "Weather Station",
    f"screen.{MOD}.log.kind_weather_station": "Weather Station",
    # v0.51.0: the mast builder and the Radio Telegraph
    f"tooltip.{MOD}.mast_builder": "Right-click a tower's mast or top with this to add a mast under the top. Sneak to place it normally.",
    f"message.{MOD}.mast_added": "Mast added: the %1$s is %2$s masts up, reach %3$s \u2192 %4$s blocks",
    f"message.{MOD}.mast_grown": "Mast added on top: %s masts tall",
    f"message.{MOD}.mast_no_room": "No room to raise it: %1$s is in the way at %2$s",
    f"message.{MOD}.mast_too_high": "It can't go any higher: that's the top of the world",
    f"block.{MOD}.radio_telegraph": "Radio Telegraph",
    f"block.{MOD}.radio_telegraph.tooltip": "Type short messages onto a frequency. Pagers, other telegraphs and Status Boards on it in reach read them.",
    f"tooltip.{MOD}.telegraph_use": "Right-click to open it. When a message arrives it clicks and gives a 1-second redstone pulse.",
    f"message.{MOD}.telegraph_status": "Radio Telegraph | Frequency %1$s | %2$s messages here | Reach %3$s",
    f"message.{MOD}.telegraph_empty": "Type a message first",
    f"message.{MOD}.telegraph_too_fast": "Wait a moment between messages",
    f"message.{MOD}.private_refused_telegraph": "Can't send: %s is private to %s",
    f"message.{MOD}.telegraph_sent": "Sent on %1$s (reach %2$s)",
    f"message.{MOD}.telegraph_read_none": "No pager or telegraph in range",
    f"message.{MOD}.telegraph_one_pager": "1 pager",
    f"message.{MOD}.telegraph_pagers": "%s pagers",
    f"message.{MOD}.telegraph_one_telegraph": "1 telegraph",
    f"message.{MOD}.telegraph_telegraphs": "%s telegraphs",
    f"message.{MOD}.telegraph_and": "%s and %s",
    f"message.{MOD}.telegraph_read": "Read by %s",
    f"message.{MOD}.telegraph_tuned": "Tuned to %s",
    f"message.{MOD}.pager_message": "%1$s: message on %2$s from %3$s: \u201c%4$s\u201d",
    f"message.{MOD}.pager_catchup_message": "%1$s: while you were away, a message on %2$s from %3$s (Day %4$s, %5$s): \u201c%6$s\u201d",
    f"message.{MOD}.pager_catchup_messages": "%1$s: while you were away, %3$s messages on %2$s; the last from %4$s (Day %5$s, %6$s): \u201c%7$s\u201d",
    f"message.{MOD}.intruder_telegraph": "%1$s tried to send a telegraph message on %3$s, but it's private: refused",
    f"screen.{MOD}.telegraph.frequency": "Frequency",
    f"screen.{MOD}.telegraph.directory": "Pick from the directory",
    f"screen.{MOD}.telegraph.message": "Message",
    f"screen.{MOD}.telegraph.hint": "Type a message...",
    f"screen.{MOD}.telegraph.send": "Send",
    f"screen.{MOD}.telegraph.tune": "Tune",
    f"screen.{MOD}.telegraph.reach": "Reach: %s",
    f"screen.{MOD}.telegraph.empty": "No messages have reached it yet",
    f"screen.{MOD}.telegraph.deaf": "Encrypted by %s: this telegraph can't read it",
    f"screen.{MOD}.telegraph.encrypted": "(encrypted)",
    f"screen.{MOD}.telegraph.when": "Day %s, %s (%s)",
    f"screen.{MOD}.audit.kind_telegraph": "Radio Telegraph",
    f"screen.{MOD}.log.kind_telegraph": "Telegraph",
    f"screen.{MOD}.log.kind_telegraph_text": "Telegraph: \u201c%s\u201d",
    f"screen.{MOD}.log.tip_telegraph": "Sent by %s",
    f"screen.{MOD}.log.tip_other_telegraph": "Sent by %s (not a member)",
    f"jade.{MOD}.telegraph_last": "Last message (%3$s): \u201c%1$s\u201d from %2$s",
    f"jade.{MOD}.telegraph_count": "Messages here: %s",
    f"jade.{MOD}.telegraph_none": "No messages have reached it yet",
    f"jade.{MOD}.telegraph_arrived": "A message just arrived",
    f"jade.{MOD}.board_message": "  Last message: %s",
    f"block.{MOD}.radio_transmitter": "Radio Transmitter",
    f"block.{MOD}.radio_transmitter.tooltip": "Broadcasts the redstone signal it receives. The higher it sits, the farther it reaches. Also relays nearby Remote Control presses.",
    f"block.{MOD}.radio_receiver": "Radio Receiver",
    f"block.{MOD}.radio_receiver.tooltip": "Outputs the strongest signal on its frequency.",
    f"block.{MOD}.radio_toggle_receiver": "Radio Toggle Receiver",
    f"block.{MOD}.radio_toggle_receiver.tooltip": "Flips on or off each time its frequency turns on, like a light switch. Outputs full strength while on.",
    f"block.{MOD}.radio_inverted_receiver": "Radio Inverted Receiver",
    # v0.40.0: Radio Logic Receiver and Signal Meter
    f"block.{MOD}.radio_logic_receiver": "Radio Logic Receiver",
    # v0.47.0: receiver height and lightning
    f"jade.{MOD}.lifted": "Up high: hears lower towers up to %s blocks away",
    f"jade.{MOD}.struck": "Struck by lightning: back in %s",
    f"{MOD}.configuration.receiverHeightPercent": "Receiver height reach (% of a transmitter's there; 0 = off)",
    f"{MOD}.configuration.lightningStrikesPerHour": "Lightning strikes per hour on a tower 64 high (0 = never)",
    f"{MOD}.configuration.lightningOutageSeconds": "Seconds a struck tower stays quiet",
    # v0.46.0: weather and terrain, and the Radio Jammer
    f"block.{MOD}.radio_jammer": "Radio Jammer",
    f"block.{MOD}.radio_jammer.tooltip": "A tower top that drowns out its frequency around it while redstone reaches it (or a mast under it): everything there hears that much less, and full strength (15) silences it. It reaches half as far as a transmitter at the same height; masts and antennas help.",
    f"tooltip.{MOD}.jammer_jams": "Jams: %s",
    f"message.{MOD}.private_refused_jammer": "Can't do that: it would jam %s, which is private to %s",
    f"message.{MOD}.private_silenced_jammer": "Silent: %s is private to %s, so this won't jam",
    f"message.{MOD}.jammer_status": "Radio Jammer | Jams %s | %s | Reach %s blocks",
    f"message.{MOD}.jammer_jamming": "Jamming at %s",
    f"message.{MOD}.jammer_off": "Off (no redstone)",
    f"jade.{MOD}.jams": "Jams: %s",
    f"jade.{MOD}.jamming": "Jamming: -%s",
    f"jade.{MOD}.jamming_on": "Jamming",
    f"jade.{MOD}.jammer_off": "Not jamming (no redstone)",
    f"jade.{MOD}.jammed": "Jammed: -%s",
    f"jade.{MOD}.hills": "Hills in the way: -%s",
    f"jade.{MOD}.weather": "%s: reach -%s%%",
    f"weather.{MOD}.clear": "Clear",
    f"weather.{MOD}.rain": "Rain",
    f"weather.{MOD}.thunder": "Thunderstorm",
    f"hud.{MOD}.meter_hills": "Hills -%s",
    f"hud.{MOD}.meter_jammed": "Jammed -%s",
    f"hud.{MOD}.meter_loss_both": "%s, %s",
    f"hud.{MOD}.meter_jammed_short": "Jammed",
    f"screen.{MOD}.audit.kind_jammer": "Radio Jammer",
    f"{MOD}.configuration.rainReachLoss": "Reach lost in rain or snow (%)",
    f"{MOD}.configuration.thunderReachLoss": "Reach lost in a thunderstorm (%)",
    f"{MOD}.configuration.terrainBlocksPerStrength": "Blocks of ground per 1 strength lost (0 = off)",
    f"{MOD}.configuration.jammerReachPercent": "Jammer reach (% of a transmitter's)",
    f"{MOD}.configuration.compactTransmitterReach": "Compact transmitter reach",
    f"{MOD}.configuration.ironAntennaReach": "Iron Antenna reach (times)",
    f"{MOD}.configuration.goldAntennaReach": "Gold Antenna reach (times)",
    f"{MOD}.configuration.diamondAntennaReach": "Diamond Antenna reach (times)",
    f"{MOD}.configuration.netheriteAntennaReach": "Netherite Antenna reach (times)",
    # v0.45.0: antennas and the Radio Lamp
    f"item.{MOD}.iron_antenna": "Iron Antenna",
    f"item.{MOD}.gold_antenna": "Gold Antenna",
    f"item.{MOD}.diamond_antenna": "Diamond Antenna",
    f"item.{MOD}.netherite_antenna": "Netherite Antenna",
    f"item.{MOD}.none_antenna": "No Antenna",
    f"tooltip.{MOD}.antenna_item": "Fit it to a transmitter, repeater or converter for %s its reach.",
    f"tooltip.{MOD}.antenna_hint": "Right-click a tower top with it; sneak + right-click the tower with an empty hand to take it off.",
    f"tooltip.{MOD}.antenna": "Antenna: %s (%s reach)",
    f"message.{MOD}.antenna_fitted": "%s: %s fitted, reach %s → %s blocks",
    f"message.{MOD}.antenna_already": "This %s already has a %s",
    f"message.{MOD}.antenna_taken_off": "%s taken off: reach %s → %s blocks",
    f"jade.{MOD}.antenna": "Antenna: %s (%s reach)",
    # v0.48.0: signal fade and the Dish Antenna
    f"jade.{MOD}.faded": "Faded: -%s (far out in its tower's reach)",
    # v0.50.0: the Ender Antenna and the Solar Panel
    f"item.{MOD}.ender_antenna": "Ender Antenna",
    f"tooltip.{MOD}.ender_item": "Fit it to a Radio Transmitter (plain, timer, recorder or weather station) and it also sends into another dimension, from the same spot there: into the Nether from the Overworld (at 1/8 of the coordinates) and into the Overworld from anywhere else. Use another on it to pick the next dimension. Its reach stays as with no antenna.",
    f"tooltip.{MOD}.antenna_ender": "Antenna: %s (also sends into another dimension)",
    f"jade.{MOD}.antenna_ender": "Antenna: %s (also sends into %s at %s)",
    f"message.{MOD}.ender_fitted": "%s: Ender Antenna fitted: sending into %s too (arriving at %s)",
    f"message.{MOD}.ender_relinked": "%s: Ender Antenna now sends into %s (arriving at %s)",
    f"message.{MOD}.ender_nowhere": "%s: Ender Antenna fitted, but there's no other dimension to send into",
    f"message.{MOD}.ender_transmitters_only": "Only a Radio Transmitter (plain, timer, recorder or weather station) can send into another dimension",
    f"dimension.{MOD}.overworld": "the Overworld",
    f"dimension.{MOD}.the_nether": "the Nether",
    f"dimension.{MOD}.the_end": "the End",
    f"block.{MOD}.solar_panel": "Solar Panel",
    f"tooltip.{MOD}.solar_help": "Put it on a Radio Battery: it charges it by daylight, up to %s a second at noon in clear weather (less in rain and early or late in the day, none at night or under a roof).",
    f"jade.{MOD}.solar_charging": "Charging the battery under it: %s a second",
    f"jade.{MOD}.solar_dark": "Not charging: too dark (night, or something over it)",
    f"jade.{MOD}.solar_no_battery": "No Radio Battery under it",
    f"jade.{MOD}.solar_light": "Sunlight: %s of 15",
    f"jade.{MOD}.battery_solar": "Solar Panel: +%s a second",
    f"jade.{MOD}.battery_use_covered": "Powering towers: %s a second (the Solar Panel keeps up)",
    f"{MOD}.configuration.solarChargePerSecond": "Solar Panel charge a second in full sun",
    # v0.49.0: power cost and the Coverage Map
    f"block.{MOD}.radio_battery": "Radio Battery",
    f"jade.{MOD}.battery_charge": "Charge: %s / %s (%s%%)",
    f"jade.{MOD}.battery_bars": "Charge: %s of 4 bars",
    f"jade.{MOD}.battery_use": "Powering towers: %s a second (about %s left)",
    f"jade.{MOD}.battery_idle": "Not powering anything right now",
    f"jade.{MOD}.unpowered": "Out of power: needs a charged Radio Battery next to it or its mast",
    f"jade.{MOD}.tower_battery": "Battery: %s%% (about %s left at %s a second)",
    f"jade.{MOD}.tower_battery_idle": "Battery: %s%% (uses %s a second while it sends)",
    f"message.{MOD}.battery_charged": "Radio Battery: +%s charge (%s / %s)",
    f"message.{MOD}.battery_full": "This Radio Battery is full (%s)",
    f"message.{MOD}.battery_status": "Radio Battery: %s / %s charge (%s%%)",
    f"tooltip.{MOD}.battery_item": "Charge: %s / %s",
    f"tooltip.{MOD}.battery_help": "Powers a tower next to it (or next to its mast) while the tower sends. Right-click it with Redstone Dust or Blocks of Redstone to charge it; hoppers, pipes and FE cables work too.",
    f"item.{MOD}.coverage_map": "Coverage Map",
    f"tooltip.{MOD}.coverage_zoom": "Zoom: 1:%s (%s blocks across)",
    f"tooltip.{MOD}.coverage_legend": "Green: strong, yellow: fair, orange and red: weak, blank: not heard",
    f"tooltip.{MOD}.coverage_help": "Right-click: draw it where you stand. Sneak + right-click: zoom out. Sneak + right-click a radio block: map its frequency.",
    f"message.{MOD}.coverage_drawn": "Coverage Map: %s here, 1:%s (%s blocks across)",
    f"message.{MOD}.coverage_zoom": "Coverage Map zoom: 1:%s (%s blocks across)",
    f"message.{MOD}.coverage_copied": "Coverage Map: frequency %s",
    f"message.{MOD}.coverage_deaf": "Can't map %s: it's encrypted by %s",
    f"{MOD}.configuration.towersNeedPower": "Towers need power",
    f"{MOD}.configuration.chargePerSecondPer64Blocks": "Charge a second per 64 blocks of reach",
    f"{MOD}.configuration.batteryCapacity": "Radio Battery capacity",
    f"{MOD}.configuration.chargePerRedstone": "Charge per Redstone Dust",
    f"{MOD}.configuration.fePerCharge": "FE per charge",
    f"hud.{MOD}.meter_faded": "Fade -%s",
    f"jade.{MOD}.antenna_dish": "Antenna: %s, aimed %s (%s reach that way)",
    f"jade.{MOD}.dish_reach": "Reaches %2$s blocks to the %1$s",
    f"item.{MOD}.dish_antenna": "Dish Antenna",
    f"tooltip.{MOD}.dish_item": "Fit it to a transmitter, repeater, converter or jammer for %s its reach in the direction you face as you fit it (a narrow beam); elsewhere it reaches as far as with no antenna.",
    f"tooltip.{MOD}.antenna_dish": "Antenna: %s (%s reach where it's aimed)",
    f"message.{MOD}.dish_fitted": "%s: Dish Antenna aimed %s: %s blocks that way, %s elsewhere",
    f"message.{MOD}.dish_reaimed": "%s: Dish Antenna turned to face %s (%s blocks that way)",
    f"message.{MOD}.dish_already": "This %s's dish already faces %s",
    f"{MOD}.configuration.signalFadeStartPercent": "Signal fade starts at (% of the reach)",
    f"{MOD}.configuration.signalFadeAtEdge": "Signal fade at the edge of reach",
    f"{MOD}.configuration.dishReachMultiplier": "Dish Antenna reach multiplier",
    f"{MOD}.configuration.dishConeDegrees": "Dish Antenna beam (degrees either side)",
    **{f"direction.{MOD}.{key}": name for key, name in zip(
        ("n", "nne", "ne", "ene", "e", "ese", "se", "sse", "s", "ssw", "sw", "wsw", "w", "wnw", "nw", "nnw"),
        ("north", "north-north-east", "north-east", "east-north-east", "east", "east-south-east", "south-east", "south-south-east",
         "south", "south-south-west", "south-west", "west-south-west", "west", "west-north-west", "north-west", "north-north-west"))},
    f"block.{MOD}.radio_lamp": "Radio Lamp",
    f"block.{MOD}.radio_lamp.tooltip": "A lamp with a receiver built in: it gives off as much light as the strength it hears (dim to bright), in its dye color. Right-click it with a dye to change the color. It's only a light: no redstone comes out.",
    f"tooltip.{MOD}.lamp_color": "Color: %s",
    f"message.{MOD}.lamp_status": "Radio Lamp | Frequency %s | %s | Light %s",
    f"jade.{MOD}.lamp_light": "Light: %s",
    f"jade.{MOD}.lamp_dark": "Dark",
    f"jade.{MOD}.lamp_color": "Color: %s",
    f"screen.{MOD}.audit.kind_lamp": "Radio Lamp",
    # v0.44.0: Aviation Beacon and Radio Signal Recorder
    f"block.{MOD}.aviation_beacon": "Aviation Beacon",
    f"block.{MOD}.aviation_beacon.tooltip": "A warning light for the top or side of a tower (or any block). It blinks red at night, can be seen from far away, and can blink only while its tower is broadcasting. All beacons blink together. Right-click it with an empty hand to set it.",
    f"tooltip.{MOD}.beacon_light": "%s",
    f"message.{MOD}.beacon_when_night": "at night",
    f"message.{MOD}.beacon_when_always": "all the time",
    f"message.{MOD}.beacon_when_sending": "while its tower sends",
    f"message.{MOD}.beacon_pattern_blink": "Blink",
    f"message.{MOD}.beacon_pattern_flash": "Flash",
    f"message.{MOD}.beacon_pattern_steady": "Steady",
    f"message.{MOD}.beacon_color_red": "red",
    f"message.{MOD}.beacon_color_white": "white",
    f"message.{MOD}.beacon_text_blink": "Blinks %s %s",
    f"message.{MOD}.beacon_text_flash": "Flashes %s %s",
    f"message.{MOD}.beacon_text_steady": "Glows %s %s",
    f"message.{MOD}.beacon_set": "Aviation Beacon: %s",
    f"screen.{MOD}.beacon.when": "On",
    f"screen.{MOD}.beacon.when_night_tip": "At night, and in rain and storms (always in the Nether and the End): from dusk to dawn, the hours you can sleep.",
    f"screen.{MOD}.beacon.when_always_tip": "Day and night.",
    f"screen.{MOD}.beacon.when_sending_tip": "Only while the tower it's on (the transmitter, repeater or converter it's on, or on top of its mast) is broadcasting, so you can see from afar whether the tower is on.",
    f"screen.{MOD}.beacon.pattern": "Pattern",
    f"screen.{MOD}.beacon.pattern_blink_tip": "One second on, one second off.",
    f"screen.{MOD}.beacon.pattern_flash_tip": "A short flash every second and a half, like a strobe.",
    f"screen.{MOD}.beacon.pattern_steady_tip": "On without blinking.",
    f"screen.{MOD}.beacon.color": "Color",
    f"screen.{MOD}.beacon.no_tower": "Not on a tower",
    f"screen.{MOD}.beacon.no_tower_dark": "Not on a tower: it will stay dark",
    f"screen.{MOD}.beacon.tower_sending": "On a %s (%s blocks up): sending",
    f"screen.{MOD}.beacon.tower_silent": "On a %s (%s blocks up): not sending",
    f"jade.{MOD}.beacon_on": "On now",
    f"jade.{MOD}.beacon_off": "Off now",
    f"jade.{MOD}.beacon_no_tower": "Not on a tower",
    f"jade.{MOD}.beacon_no_tower_dark": "Not on a tower: stays dark",
    f"jade.{MOD}.beacon_tower_sending": "Tower: %s on %s, %s blocks up, sending",
    f"jade.{MOD}.beacon_tower_silent": "Tower: %s on %s, %s blocks up, not sending",
    f"block.{MOD}.radio_signal_recorder": "Radio Signal Recorder",
    f"block.{MOD}.radio_signal_recorder.tooltip": "A transmitter that records up to 30 seconds of a frequency (every on, off and strength) and plays it back on its own frequency: once on each redstone pulse, or on a loop while powered. Right-click it with an empty hand to record.",
    f"tooltip.{MOD}.recorder_plays_on": "Plays on: %s",
    f"tooltip.{MOD}.recorder_records_from": "Records from: %s",
    f"tooltip.{MOD}.recorder_tape": "%s; plays %s",
    f"message.{MOD}.recorder_empty": "nothing recorded",
    f"message.{MOD}.recorder_tape": "%ss, %s changes",
    f"message.{MOD}.recorder_mode_once": "once on a redstone pulse",
    f"message.{MOD}.recorder_mode_loop": "on a loop while powered",
    f"message.{MOD}.recorder_status": "Signal Recorder | Frequency %s | %s | Sending %s | Reach %s blocks",
    f"message.{MOD}.recorder_same": "It can't record %s and play on it too: pick two different frequencies",
    f"message.{MOD}.recorder_waiting": "Radio Signal Recorder: waiting for %s to turn on...",
    f"message.{MOD}.recorder_no_listen": "Set a frequency to record from first",
    f"message.{MOD}.recorder_recorded": "Radio Signal Recorder: recorded %s",
    f"message.{MOD}.recorder_stopped_waiting": "Radio Signal Recorder: stopped waiting (the old recording is kept)",
    f"message.{MOD}.recorder_stopped": "Radio Signal Recorder: stopped",
    f"message.{MOD}.recorder_playing": "Radio Signal Recorder: playing on %s",
    f"message.{MOD}.recorder_nothing": "Radio Signal Recorder: nothing recorded",
    f"message.{MOD}.recorder_cleared": "Radio Signal Recorder: recording cleared",
    f"message.{MOD}.recorder_no_source": "nothing",
    f"message.{MOD}.recorder_set": "Radio Signal Recorder: plays on %s, records from %s, plays %s",
    f"screen.{MOD}.recorder.listen": "Records from",
    f"screen.{MOD}.recorder.listen_tip": "The frequency it records, as heard where it stands. Record waits for it to turn on, then records every change until Stop (30 seconds at most).",
    f"screen.{MOD}.recorder.listen_none": "none",
    f"screen.{MOD}.recorder.send": "Plays on",
    f"screen.{MOD}.recorder.send_tip": "The frequency it plays the recording on (it sends it like a transmitter, reaching as far as its height).",
    f"screen.{MOD}.recorder.directory": "Pick from the frequency directory",
    f"screen.{MOD}.recorder.mode": "Plays",
    f"screen.{MOD}.recorder.once_tip": "Plays the recording from the start each time a redstone signal reaches it (or a mast under it).",
    f"screen.{MOD}.recorder.loop_tip": "Plays the recording over and over while a redstone signal reaches it (or a mast under it), and stops when it goes.",
    f"screen.{MOD}.recorder.record": "Record",
    f"screen.{MOD}.recorder.record_tip": "Saves the boxes, then waits for Records from to turn on and records until Stop. The old recording stays until the new one starts.",
    f"screen.{MOD}.recorder.stop": "Stop",
    f"screen.{MOD}.recorder.play": "Play",
    f"screen.{MOD}.recorder.play_tip": "Plays the recording once now.",
    f"screen.{MOD}.recorder.clear": "Clear",
    f"screen.{MOD}.recorder.waiting": "Waiting for %s to turn on...",
    f"screen.{MOD}.recorder.recording": "Recording: %ss, %s changes",
    f"screen.{MOD}.recorder.playing": "Playing: %ss of %ss",
    f"screen.{MOD}.recorder.playing_loop": "Playing on a loop: %ss of %ss",
    f"screen.{MOD}.recorder.recorded": "Recorded: %s",
    f"screen.{MOD}.recorder.empty": "Nothing recorded yet: press Record, then send on Records from",
    f"screen.{MOD}.recorder.detail": "Hearing %s | Redstone %s",
    f"screen.{MOD}.recorder.nothing": "nothing",
    f"screen.{MOD}.recorder.nothing_set": "(no frequency set)",
    f"screen.{MOD}.recorder.redstone_on": "on",
    f"screen.{MOD}.recorder.redstone_off": "off",
    f"screen.{MOD}.recorder.deaf": "Deaf: %s is encrypted by %s",
    f"screen.{MOD}.recorder.silenced": "Silent: %s is private to %s",
    f"jade.{MOD}.recorder_records_from": "Records from: %s",
    f"jade.{MOD}.recorder_tape": "Tape: %s; plays %s",
    f"jade.{MOD}.recorder_waiting": "Waiting for a signal to record",
    f"jade.{MOD}.recorder_recording": "Recording: %ss, %s changes",
    f"jade.{MOD}.recorder_playing": "Playing: %ss of %ss",
    f"screen.{MOD}.log.kind_recorder": "Signal Recorder",
    f"screen.{MOD}.audit.kind_signal_recorder": "Signal Recorder",
    # v0.43.0: Radio Speaker and Radio Status Board
    f"block.{MOD}.radio_speaker": "Radio Speaker",
    f"block.{MOD}.radio_speaker.tooltip": "A receiver that also plays a sound when its frequency turns on: a bell, chime, doorbell and more, once or every second while it's on. Right-click it with an empty hand to pick the sound.",
    f"block.{MOD}.radio_status_board": "Radio Status Board",
    f"block.{MOD}.radio_status_board.tooltip": "A panel for a wall that shows up to four frequencies: each one's name, a light and the strength heard there, readable in the dark. Right-click it with an empty hand to pick them.",
    f"message.{MOD}.speaker_sound_bell": "Bell",
    f"message.{MOD}.speaker_sound_chime": "Chime",
    f"message.{MOD}.speaker_sound_ding": "Ding",
    f"message.{MOD}.speaker_sound_doorbell": "Doorbell",
    f"message.{MOD}.speaker_sound_pling": "Pling",
    f"message.{MOD}.speaker_sound_bass": "Bass",
    f"message.{MOD}.speaker_sound_beep": "Beep",
    f"message.{MOD}.speaker_sound_pager": "Pager",
    f"message.{MOD}.speaker_sound_static": "Static",
    f"message.{MOD}.speaker_sound_siren": "Siren",
    # v0.59.0: the mod's own sounds (tools/make_sounds.py)
    f"subtitles.{MOD}.tuner.tune": "Radio tunes in",
    f"subtitles.{MOD}.tuner.step": "Tuning dial clicks",
    f"subtitles.{MOD}.telegraph.key": "Telegraph key taps",
    f"subtitles.{MOD}.telegraph.sounder": "Telegraph clacks",
    f"subtitles.{MOD}.pager.beep": "Pager beeps",
    f"subtitles.{MOD}.meter.tick": "Signal Meter ticks",
    f"subtitles.{MOD}.tower.on": "Tower goes on the air",
    f"subtitles.{MOD}.tower.off": "Tower goes off the air",
    f"subtitles.{MOD}.station.siren": "Raid siren wails",
    f"message.{MOD}.speaker_volume_quiet": "quiet",
    f"message.{MOD}.speaker_volume_normal": "normal",
    f"message.{MOD}.speaker_volume_loud": "loud",
    f"message.{MOD}.speaker_text_once": "%s, %s, %s, once",
    f"message.{MOD}.speaker_text_repeat": "%s, %s, %s, every second while on",
    f"message.{MOD}.speaker_status": "Speaker | Frequency %s | %s | Output %s",
    f"message.{MOD}.speaker_set": "Radio Speaker: %s",
    f"message.{MOD}.board_set": "Radio Status Board: showing %s frequencies",
    f"tooltip.{MOD}.speaker_plays": "Plays: %s",
    f"tooltip.{MOD}.board_row": "Shows: %s",
    f"jade.{MOD}.speaker_plays": "Plays: %s",
    f"jade.{MOD}.board_empty": "No frequencies yet: right-click it to pick up to four",
    f"jade.{MOD}.board_row_on": "%s: %s",
    f"jade.{MOD}.board_row_off": "%s: off",
    f"jade.{MOD}.board_row_deaf": "%s: encrypted by %s",
    f"screen.{MOD}.audit.kind_speaker": "Speaker",
    f"screen.{MOD}.speaker.frequency": "Plays when %s turns on",
    f"screen.{MOD}.speaker.sound": "Sound",
    f"screen.{MOD}.speaker.sound_tip": "What it plays. Shift-click or scroll to go back.",
    f"screen.{MOD}.speaker.pitch": "Pitch",
    f"screen.{MOD}.speaker.pitch_tip": "The note, like a note block's (F♯4 is the sound's own pitch). Shift-click or scroll to go back.",
    f"screen.{MOD}.speaker.volume": "Volume",
    f"screen.{MOD}.speaker.volume_tip": "Quiet and normal are heard within 16 blocks, loud within 48.",
    f"screen.{MOD}.speaker.repeat": "Plays",
    f"screen.{MOD}.speaker.repeat_off": "Once when it turns on",
    f"screen.{MOD}.speaker.repeat_on": "Every second while on",
    f"screen.{MOD}.speaker.repeat_tip": "Once each time its frequency turns on, or every second for as long as it stays on (an alarm).",
    f"screen.{MOD}.speaker.test": "Test",
    f"screen.{MOD}.speaker.test_tip": "Plays the sound for you only",
    f"screen.{MOD}.speaker.hearing": "Hearing %s",
    f"screen.{MOD}.speaker.hearing_nothing": "Hearing nothing right now",
    f"screen.{MOD}.speaker.deaf": "Deaf: %s is encrypted by %s",
    f"screen.{MOD}.board.row": "Row %s",
    f"screen.{MOD}.board.empty": "empty",
    f"screen.{MOD}.board.directory": "Pick from the frequency directory",
    f"screen.{MOD}.board.explain": "Shows up to four frequencies, as heard here",
    f"screen.{MOD}.board.not_saved": "(not saved yet)",
    f"screen.{MOD}.board.encrypted": "encrypted",
    f"screen.{MOD}.board.off": "off",
    f"screen.{MOD}.board.enc": "enc",
    f"screen.{MOD}.board.no_rows": "Right-click to set up",
    # v0.42.0: Radio Timer Transmitter and Radio Counter Receiver
    f"block.{MOD}.radio_timer_transmitter": "Radio Timer Transmitter",
    f"block.{MOD}.radio_timer_transmitter.tooltip": "A transmitter with a clock: it sends full strength every few seconds or minutes, or between two times of day. A redstone signal into it pauses it. Right-click it with an empty hand to set its schedule.",
    f"block.{MOD}.radio_counter_receiver": "Radio Counter Receiver",
    f"block.{MOD}.radio_counter_receiver.tooltip": "Counts how many times its frequency turns on, and turns on at a set number: for a 1-second pulse, or until its reset frequency turns on. Right-click it with an empty hand to set it up.",
    f"message.{MOD}.time_minutes": "%s min",
    f"message.{MOD}.timer_interval": "every %s, %s pulse",
    f"message.{MOD}.timer_daily": "on %s to %s",
    f"message.{MOD}.timer_status": "Timer Transmitter | Frequency %s | %s | Sending %s | Reach %s blocks",
    f"message.{MOD}.timer_set": "Radio Timer Transmitter: %s",
    f"message.{MOD}.counter_text_pulse": "on at %s turn-ons, then a 1s pulse",
    f"message.{MOD}.counter_text_stay": "on at %s turn-ons, until reset",
    f"message.{MOD}.counter_text_forget": "; forgets after %s",
    f"message.{MOD}.counter_status": "Counter Receiver | Frequency %s | Count %s of %s | %s",
    f"message.{MOD}.counter_set": "Radio Counter Receiver: %s",
    f"message.{MOD}.counter_cleared": "Radio Counter Receiver: count back to 0",
    f"tooltip.{MOD}.timer_schedule": "Timer: %s",
    f"tooltip.{MOD}.counter_settings": "Counter: %s",
    f"tooltip.{MOD}.counter_reset": "Reset by: %s",
    f"jade.{MOD}.timer_schedule": "Timer: %s",
    f"jade.{MOD}.timer_paused": "Paused: a redstone signal reaches it",
    f"jade.{MOD}.timer_next_pulse": "Next pulse in %s",
    f"jade.{MOD}.timer_pulse_ends": "Pulse ends in %s",
    f"jade.{MOD}.timer_on_until": "On until %s",
    f"jade.{MOD}.timer_turns_on_at": "Turns on at %s",
    f"jade.{MOD}.counter_counts": "Counts: %s",
    f"jade.{MOD}.counter_reset": "Reset by: %s",
    f"jade.{MOD}.counter_count": "Count: %s of %s",
    f"jade.{MOD}.counter_reached_pulse": "Reached %s: pulsing",
    f"jade.{MOD}.counter_reached_stay": "Reached %s: on until reset",
    f"jade.{MOD}.counter_settings": "Set: %s",
    f"jade.{MOD}.counter_forgets_in": "Forgets the count in %s",
    f"jade.{MOD}.counter_reset_deaf": "Can't hear its reset frequency: encrypted by %s",
    f"screen.{MOD}.log.kind_timer": "Timer",
    f"screen.{MOD}.audit.kind_timer_transmitter": "Timer Transmitter",
    f"screen.{MOD}.audit.kind_counter_receiver": "Counter Receiver",
    f"screen.{MOD}.timer.mode": "Mode",
    f"screen.{MOD}.timer.mode_interval": "Repeating",
    f"screen.{MOD}.timer.mode_daily": "Daily",
    f"screen.{MOD}.timer.mode_interval_tip": "Sends a pulse again and again, a set time apart.",
    f"screen.{MOD}.timer.mode_daily_tip": "On between two in-game times every day (18:00 to 06:00 is every night).",
    f"screen.{MOD}.timer.interval": "Every",
    f"screen.{MOD}.timer.interval_tip": "How long from one pulse to the next. Shift-click or scroll to go back.",
    f"screen.{MOD}.timer.pulse": "Pulse",
    f"screen.{MOD}.timer.pulse_tip": "How long each pulse lasts (at most half the time between pulses).",
    f"screen.{MOD}.timer.on_hour": "On at",
    f"screen.{MOD}.timer.on_hour_tip": "The in-game time it turns on. Shift-click or scroll to go back.",
    f"screen.{MOD}.timer.off_hour": "Off at",
    f"screen.{MOD}.timer.off_hour_tip": "The in-game time it turns off. Shift-click or scroll to go back.",
    f"screen.{MOD}.timer.frequency": "Sends on %s",
    f"screen.{MOD}.timer.sending": "Sending 15 on %s",
    f"screen.{MOD}.timer.not_sending": "Not sending right now",
    f"screen.{MOD}.timer.paused": "Paused: a redstone signal reaches it",
    f"screen.{MOD}.timer.silenced": "Silent: %s is private to %s",
    f"screen.{MOD}.timer.next_pulse": "Next pulse in %s",
    f"screen.{MOD}.timer.pulse_ends": "Pulse ends in %s",
    f"screen.{MOD}.timer.clock_on": "It's %s: on until %s",
    f"screen.{MOD}.timer.clock_off": "It's %s: turns on at %s",
    f"screen.{MOD}.timer.never": "On and off at the same time: never on",
    f"screen.{MOD}.timer.preview": "Once saved: %s, starting now",
    f"screen.{MOD}.timer.preview_daily": "Once saved: %s (%s hours a day)",
    f"screen.{MOD}.timer.hint": "A redstone signal into it (or its masts) pauses it",
    f"screen.{MOD}.counter.frequency": "Counts turn-ons of %s",
    f"screen.{MOD}.counter.target": "Turn on at",
    f"screen.{MOD}.counter.target_tip": "How many turn-ons of its frequency it counts before it turns on. Shift-click or scroll to go back.",
    f"screen.{MOD}.counter.latch": "Then",
    f"screen.{MOD}.counter.latch_pulse": "1s pulse, count again",
    f"screen.{MOD}.counter.latch_stay": "Stay on until reset",
    f"screen.{MOD}.counter.latch_pulse_tip": "Outputs 15 for a second, then counts from 0 again.",
    f"screen.{MOD}.counter.latch_stay_tip": "Outputs 15 until its reset frequency turns on (or Reset count is pressed).",
    f"screen.{MOD}.counter.forget": "Forget after",
    f"screen.{MOD}.counter.forget_tip": "Starts the count over after this long without a turn-on, for a quick knock. None: never.",
    f"screen.{MOD}.counter.reset": "Reset by",
    f"screen.{MOD}.counter.reset_none": "none",
    f"screen.{MOD}.counter.reset_tip": "A turn-on of this frequency sets the count back to 0 and switches it off. Leave it empty for none.",
    f"screen.{MOD}.counter.directory": "Pick from the frequency directory",
    f"screen.{MOD}.counter.clear": "Reset count",
    f"screen.{MOD}.counter.clear_tip": "Saves, and sets the count back to 0",
    f"screen.{MOD}.counter.count": "Count: %s of %s | hearing %s",
    f"screen.{MOD}.counter.reached": "Reached %s: on | hearing %s",
    f"screen.{MOD}.counter.nothing": "nothing",
    f"screen.{MOD}.counter.deaf": "Deaf: %s is encrypted by %s",
    f"screen.{MOD}.counter.forgets_in": "Forgets the count in %s",
    # v0.41.0: signal gate and Radio Delay Receiver
    f"block.{MOD}.radio_delay_receiver": "Radio Delay Receiver",
    f"block.{MOD}.radio_delay_receiver.tooltip": "A receiver with a timer: it turns on and off its delay after its frequency does, and stays on for its hold after it turns off. Right-click it to set them.",
    f"tooltip.{MOD}.delay_timing": "Delay: %s, hold: %s",
    f"message.{MOD}.time_none": "none",
    f"message.{MOD}.time_seconds": "%ss",
    f"message.{MOD}.delay_status": "Delay Receiver | Frequency %s | Delay %s, hold %s | Hearing %s | %s",
    f"message.{MOD}.delay_set": "Radio Delay Receiver: delay %s, hold %s",
    f"screen.{MOD}.audit.kind_delay_receiver": "Delay Receiver",
    f"screen.{MOD}.delay.frequency": "Frequency %s",
    f"screen.{MOD}.delay.delay": "Delay",
    f"screen.{MOD}.delay.hold": "Hold",
    f"screen.{MOD}.delay.delay_tip": "It turns on and off this long after its frequency does, like a long redstone repeater. Shift-click or scroll to go back.",
    f"screen.{MOD}.delay.hold_tip": "It stays on this long after that turns off, so a short pulse keeps it on for a while.",
    f"screen.{MOD}.delay.explain": "Outputs 15 while on",
    f"screen.{MOD}.delay.status": "Hearing %s, output %s",
    f"screen.{MOD}.delay.nothing": "nothing",
    f"screen.{MOD}.delay.on": "on",
    f"screen.{MOD}.delay.off": "off",
    f"screen.{MOD}.delay.turns_on": "Turns on in %s",
    f"screen.{MOD}.delay.turns_off": "Turns off in %s",
    f"screen.{MOD}.delay.deaf": "Deaf: %s is encrypted by %s, so it hears nothing",
    f"jade.{MOD}.delay_timing": "Delay: %s, hold: %s",
    f"jade.{MOD}.delay_turns_on": "Turns on in %s",
    f"jade.{MOD}.delay_turns_off": "Turns off in %s",
    f"jade.{MOD}.gate": "Gate: %s (hearing %s)",
    f"message.{MOD}.converter_set_gate": "Radio Converter: listens on %s, sends on %s, %s",
    f"message.{MOD}.gate_at_least": "only at %s or more",
    f"message.{MOD}.gate_below": "15 while below %s",
    f"message.{MOD}.gate_not": "15 while it hears nothing",
    f"tooltip.{MOD}.converter_gate": "Gate: %s",
    f"screen.{MOD}.converter.gate": "Gate",
    f"screen.{MOD}.converter.gate_0": "Pass",
    f"screen.{MOD}.converter.gate_1": "At least",
    f"screen.{MOD}.converter.gate_2": "Below",
    f"screen.{MOD}.converter.gate_0_tip": "Pass: sends what it hears, at the same strength. Click for At least (Shift-click goes back).",
    f"screen.{MOD}.converter.gate_1_tip": "At least: sends what it hears only when it's at least the level on the right, like a comparator. Click for Below.",
    f"screen.{MOD}.converter.gate_2_tip": "Below: sends 15 while what it hears is below the level on the right. At 1, while it hears nothing: a NOT gate. Click for Pass.",
    f"screen.{MOD}.converter.threshold": "Level",
    f"screen.{MOD}.converter.threshold_tip": "The gate's level, 1 to 15. Click for more, Shift-click for less.",
    f"screen.{MOD}.converter.gate_preview": "Once saved: sends %s on %s",
    f"screen.{MOD}.converter.gate_preview_closed": "Once saved: nothing sent on %s",
    f"screen.{MOD}.converter.gate_sending": "Sending %s on %s",
    f"screen.{MOD}.converter.gate_closed": "Gate closed: nothing sent on %s",
    f"screen.{MOD}.converter.reach_gate": "Reach: %s blocks | hearing %s",
    f"screen.{MOD}.converter.heard_nothing": "nothing",
    f"screen.{MOD}.log.kind_gate": "Converter gate",
    f"block.{MOD}.radio_logic_receiver.tooltip": "Listens on two frequencies and outputs when both are on (AND), either is on (OR) or exactly one is on (XOR). Right-click it to set them.",
    f"tooltip.{MOD}.logic_inputs": "Listens on: %s %s %s",
    f"message.{MOD}.logic_and": "AND",
    f"message.{MOD}.logic_or": "OR",
    f"message.{MOD}.logic_xor": "XOR",
    f"message.{MOD}.logic_status": "Logic Receiver | %s %s %s | Hearing %s and %s | Output %s",
    f"message.{MOD}.logic_set": "Radio Logic Receiver: %s %s %s",
    f"screen.{MOD}.audit.kind_logic_receiver": "Logic Receiver",
    f"screen.{MOD}.logic.explain": "Outputs when its two frequencies match the mode",
    f"screen.{MOD}.logic.first": "First frequency",
    f"screen.{MOD}.logic.second": "Second frequency",
    f"screen.{MOD}.logic.directory_first": "Pick the first frequency from the frequency directory",
    f"screen.{MOD}.logic.directory_second": "Pick the second frequency from the frequency directory",
    f"screen.{MOD}.logic.mode": "Mode",
    f"screen.{MOD}.logic.mode_and": "AND",
    f"screen.{MOD}.logic.mode_or": "OR",
    f"screen.{MOD}.logic.mode_xor": "XOR",
    f"screen.{MOD}.logic.mode_and_tip": "AND: on while both frequencies are on, at the weaker strength. Click for OR (Shift-click goes back).",
    f"screen.{MOD}.logic.mode_or_tip": "OR: on while either frequency is on, at the stronger strength. Click for XOR.",
    f"screen.{MOD}.logic.mode_xor_tip": "XOR: on while exactly one of them is on, at its strength. Click for AND.",
    f"screen.{MOD}.logic.hearing": "Hearing %s on %s and %s on %s",
    f"screen.{MOD}.logic.nothing": "nothing",
    f"screen.{MOD}.logic.output": "Output: %s",
    f"screen.{MOD}.logic.output_off": "Output: off",
    f"screen.{MOD}.logic.deaf": "Deaf: %s is encrypted by %s, so it hears nothing there",
    f"jade.{MOD}.input_first": "First: %s",
    f"jade.{MOD}.input_second": "Second: %s",
    f"jade.{MOD}.logic_mode": "Mode: %s",
    f"jade.{MOD}.logic_and": "AND",
    f"jade.{MOD}.logic_or": "OR",
    f"jade.{MOD}.logic_xor": "XOR",
    f"jade.{MOD}.logic_hearing": "Hearing: %s and %s",
    f"jade.{MOD}.nothing": "nothing",
    f"jade.{MOD}.deaf_second": "Second deaf: encrypted by %s",
    f"item.{MOD}.signal_meter": "Signal Meter",
    f"message.{MOD}.meter_sound_on": "Signal Meter: tick ON",
    f"message.{MOD}.meter_sound_off": "Signal Meter: tick OFF",
    f"message.{MOD}.frequency_copied_meter": "Signal Meter now measures frequency %s",
    f"tooltip.{MOD}.meter_ticking": "Tick: on",
    f"tooltip.{MOD}.meter_silent": "Tick: off",
    f"tooltip.{MOD}.meter_help": "Hold it to see the strength where you stand. Right-click: tick on/off. Sneak + right-click: frequency",
    f"screen.{MOD}.tuner.meter_sound": "Tick",
    f"screen.{MOD}.tuner.meter_sound_tooltip": "Ticks like a Geiger counter while you hold it: faster and higher the stronger the signal",
    f"hud.{MOD}.meter_frequency": "Meter: %s",
    f"hud.{MOD}.meter_off": "off",
    f"hud.{MOD}.meter_detail": "%s, %s",
    f"hud.{MOD}.meter_towers_one": "1 tower",
    f"hud.{MOD}.meter_towers": "%s towers",
    f"hud.{MOD}.meter_edge_blocks": "%s blocks to the edge",
    f"hud.{MOD}.meter_edge_block": "1 block to the edge",
    f"hud.{MOD}.meter_edge_at": "right at the edge",
    f"hud.{MOD}.meter_out_of_reach": "Out of every tower's reach",
    f"hud.{MOD}.meter_encrypted": "Encrypted by %s",
    f"hud.{MOD}.meter_encrypted_hint": "It can't measure this frequency",
    f"block.{MOD}.radio_inverted_receiver.tooltip": "Outputs full strength while its frequency is silent, and nothing while it's on.",
    f"block.{MOD}.radio_repeater": "Radio Repeater",
    f"block.{MOD}.radio_repeater.tooltip": "Picks up its frequency and broadcasts it again from its own height, to chain towers across long distances. Needs no redstone.",
    f"block.{MOD}.radio_converter": "Radio Converter",
    f"block.{MOD}.radio_converter.tooltip": "Picks up one frequency and broadcasts it on another, from its own height. Needs no redstone.",
    f"tooltip.{MOD}.converter_listens": "Listens on: %s",
    f"tooltip.{MOD}.converter_sends": "Sends on: %s",
    f"tooltip.{MOD}.converter_hint": "Right-click it with an empty hand to set both frequencies and its gate. The tuner sets the one it listens on",
    f"screen.{MOD}.converter.listens": "Listens on",
    f"screen.{MOD}.converter.sends": "Sends on",
    f"screen.{MOD}.converter.range": "Frequencies %s to %s",
    f"screen.{MOD}.converter.converting": "Hearing %s on %s, sending it on %s",
    f"screen.{MOD}.converter.quiet": "Nothing to convert: %s is quiet here",
    f"screen.{MOD}.converter.reach": "Reach: %s blocks",
    f"message.{MOD}.converter_set": "Radio Converter: listens on %s, sends on %s",
    f"message.{MOD}.converter_status": "Converter | Listens on %s | Sends on %s | %s | Reach %s blocks",
    f"message.{MOD}.converter_converting": "Converting %s",
    f"message.{MOD}.converter_quiet": "Nothing to convert",
    f"block.{MOD}.compact_radio_receiver": "Compact Radio Receiver",
    f"block.{MOD}.compact_radio_receiver.tooltip": "A button-sized receiver. Sticks to walls, floors and ceilings, and powers the block it's on.",
    f"block.{MOD}.wall_redstone": "Wall Redstone",
    f"block.{MOD}.radio_mast": "Radio Mast",
    f"block.{MOD}.radio_mast.tooltip": "Tower segment. Carries a redstone signal up to the transmitter on top.",
    f"item.{MOD}.frequency_tuner": "Frequency Tuner",
    f"item.{MOD}.remote_control": "Remote Control",
    f"item.{MOD}.multi_remote": "Multi-Button Remote",
    f"item.{MOD}.pager": "Radio Pager",
    f"item.{MOD}.signal_scanner": "Signal Scanner",
    f"screen.{MOD}.tuner.favorites": "Favorites",
    # Frequency directory
    f"screen.{MOD}.tuner.directory": "Directory",
    f"screen.{MOD}.tuner.directory_tooltip": "Every named and private frequency in the world. Click one to use it here",
    f"screen.{MOD}.directory.title": "Frequency Directory",
    f"screen.{MOD}.directory.search": "Search",
    f"screen.{MOD}.directory.search_hint": "Search by name, note, number or owner",
    f"screen.{MOD}.directory.filter": "Show: %s",
    f"screen.{MOD}.directory.filter_all": "All",
    f"screen.{MOD}.directory.filter_named": "Named",
    f"screen.{MOD}.directory.filter_private": "Private",
    f"screen.{MOD}.directory.filter_mine": "Mine",
    f"screen.{MOD}.directory.filter_on": "On now",
    f"screen.{MOD}.directory.filter_tooltip": "Click for the next filter, sneak-click for the previous one. Mine: private frequencies you own or are a friend on. On now: on anywhere you're allowed to see, or heard where you stand",
    f"screen.{MOD}.directory.heading_frequency": "Frequency",
    f"screen.{MOD}.directory.heading_name": "Name",
    f"screen.{MOD}.directory.heading_owner": "Who sends",
    f"screen.{MOD}.directory.heading_now": "Now",
    f"screen.{MOD}.directory.public": "Anyone",
    f"screen.{MOD}.directory.yours": "You",
    f"screen.{MOD}.directory.yours_friends": "You + %s",
    f"screen.{MOD}.directory.friend": "%s + you",
    f"screen.{MOD}.directory.on": "On %s",
    f"screen.{MOD}.directory.on_here": "On %s, here",
    f"screen.{MOD}.directory.here": "Here %s",
    f"screen.{MOD}.directory.off": "Off",
    f"screen.{MOD}.directory.encrypted": "Encrypted",
    f"screen.{MOD}.directory.tip_frequency": "Frequency %s",
    f"screen.{MOD}.directory.tip_public": "Public: anyone can send on it",
    f"screen.{MOD}.directory.tip_yours": "Private: yours",
    f"screen.{MOD}.directory.tip_friend": "Private to %s: you're a friend, so you can send",
    f"screen.{MOD}.directory.tip_friends": "Friends: %s",
    f"screen.{MOD}.directory.tip_other": "Private to %s: you can't send on it",
    f"screen.{MOD}.directory.tip_encrypted": "Encrypted: only its owner's and friends' receivers, pagers and scanners hear it, so nothing about it is shown",
    f"screen.{MOD}.directory.tip_encrypted_member": "Encrypted: only its owner's and friends' receivers, pagers and scanners hear it",
    f"screen.{MOD}.directory.tip_on": "On somewhere in this dimension (strength %s), not where you stand",
    f"screen.{MOD}.directory.tip_on_here": "On (strength %s), and you hear %s where you stand",
    f"screen.{MOD}.directory.tip_off": "Off: nothing is sending on it in this dimension",
    f"screen.{MOD}.directory.tip_hidden": "Someone else's private frequency: only shown when you can hear it where you stand",
    f"screen.{MOD}.directory.tip_hidden_here": "Someone else's private frequency: you hear %2$s where you stand",
    f"screen.{MOD}.directory.tip_click": "Click to use this frequency",
    f"screen.{MOD}.directory.tip_audit": "Sneak-click to list every block on it",
    f"screen.{MOD}.audit.title": "Blocks on frequency %s",
    f"screen.{MOD}.audit.subtitle": "Every block on it, in every dimension. Click one to light it up",
    f"screen.{MOD}.audit.heading_block": "Block",
    f"screen.{MOD}.audit.heading_where": "Where",
    f"screen.{MOD}.audit.heading_tuner": "Tuned by",
    f"screen.{MOD}.audit.heading_status": "Status",
    f"screen.{MOD}.audit.kind_transmitter": "Transmitter",
    f"screen.{MOD}.audit.kind_repeater": "Repeater",
    f"screen.{MOD}.audit.kind_converter_sends": "Converter (sends)",
    f"screen.{MOD}.audit.kind_converter_listens": "Converter (listens)",
    f"screen.{MOD}.audit.kind_receiver": "Receiver",
    f"screen.{MOD}.audit.kind_toggle_receiver": "Toggle Receiver",
    f"screen.{MOD}.audit.kind_inverted_receiver": "Inverted Receiver",
    f"screen.{MOD}.audit.kind_compact_receiver": "Compact Receiver",
    f"screen.{MOD}.audit.kind_count": "%s \u00d7%s",
    f"screen.{MOD}.audit.dim_overworld": "Overworld",
    f"screen.{MOD}.audit.dim_nether": "Nether",
    f"screen.{MOD}.audit.dim_end": "End",
    f"screen.{MOD}.audit.other_dimension": "%s: %s",
    f"screen.{MOD}.audit.hidden": "(not shown)",
    f"screen.{MOD}.audit.no_tuner": "No record",
    f"screen.{MOD}.audit.on": "On %s",
    f"screen.{MOD}.audit.off": "Off",
    f"screen.{MOD}.audit.working": "Works",
    f"screen.{MOD}.audit.silent": "Silent",
    f"screen.{MOD}.audit.deaf": "Deaf",
    f"screen.{MOD}.audit.tip_where": "At %s, %s, %s: %s blocks %s",
    f"screen.{MOD}.audit.tip_here": "At %s, %s, %s: right here",
    f"screen.{MOD}.audit.tip_where_dimension": "At %s, %s, %s in the %s",
    f"screen.{MOD}.audit.tip_owner": "Tuned by %s, the owner",
    f"screen.{MOD}.audit.tip_member": "Tuned by %s, a friend",
    f"screen.{MOD}.audit.tip_unknown": "No recorded tuner: set up before version 0.25 (or by a command), so it still works here. Tune it again with the Frequency Tuner to make it yours",
    f"screen.{MOD}.audit.tip_other": "Tuned by %s, who isn't a friend. Where it is isn't shown",
    f"screen.{MOD}.audit.tip_other_many": "Tuned by %s, who isn't a friend. Where they are isn't shown",
    f"screen.{MOD}.audit.tip_sends": "Works: it sends on this frequency",
    f"screen.{MOD}.audit.tip_hears": "Works: it hears this frequency",
    f"screen.{MOD}.audit.tip_silent": "Silent: its tuner can't send on this private frequency, so it doesn't broadcast",
    f"screen.{MOD}.audit.tip_deaf": "Deaf: its tuner can't hear this encrypted frequency, so it hears nothing",
    f"screen.{MOD}.audit.summary": "%s blocks",
    f"screen.{MOD}.audit.summary_one": "1 block",
    f"screen.{MOD}.audit.summary_silent": "%s silent",
    f"screen.{MOD}.audit.summary_deaf": "%s deaf",
    f"screen.{MOD}.audit.summary_unknown": "%s with no recorded tuner",
    f"screen.{MOD}.audit.loading": "Looking...",
    f"screen.{MOD}.audit.empty": "No blocks on it",
    f"screen.{MOD}.audit.not_yours": "Only %s can list this frequency's blocks",
    f"screen.{MOD}.audit.not_private": "It isn't private any more",
    f"screen.{MOD}.audit.receivers_loaded": "Receivers only show while their chunk is loaded",
    f"screen.{MOD}.audit.close": "Close",
    f"screen.{MOD}.audit.close_tooltip": "Close every screen, to look around for the beams",
    f"screen.{MOD}.audit.tip_beam": "Click to put a beam of light over it for a minute",
    f"screen.{MOD}.audit.tip_beam_dimension": "Click to put a beam of light over it for a minute (you'll see it in %s)",
    f"screen.{MOD}.audit.tip_beam_on": "Beam on for %s more seconds. Click to turn it off",
    f"screen.{MOD}.audit.beam_on": "Beam on: %s at %s, %s, %s, for a minute. Close to see it",
    f"screen.{MOD}.audit.beam_on_dimension": "Beam on: %s at %s, %s, %s (%s), for a minute",
    f"screen.{MOD}.audit.beam_off": "Beam off: %s at %s, %s, %s",
    f"screen.{MOD}.audit.hand_over": "Hand over...",
    f"screen.{MOD}.audit.hand_over_tooltip": "Give this frequency to one of its friends or group members. You stay on it as their friend",
    f"screen.{MOD}.audit.log": "Log",
    f"screen.{MOD}.audit.log_tooltip": "Its last turn-ons: when, and what or who turned it on",
    f"screen.{MOD}.log.title": "Turn-ons of frequency %s",
    f"screen.{MOD}.log.subtitle": "Newest first, in every dimension. Click a transmitter to light it up",
    f"screen.{MOD}.log.heading_when": "When",
    f"screen.{MOD}.log.heading_what": "What",
    f"screen.{MOD}.log.heading_who": "Who",
    f"screen.{MOD}.log.heading_where": "Where",
    f"screen.{MOD}.log.when": "Day %s, %s",
    f"screen.{MOD}.log.kind_transmitter": "Transmitter",
    f"screen.{MOD}.log.kind_remote_pulse": "Remote pulse",
    f"screen.{MOD}.log.kind_remote_toggle": "Remote toggle",
    f"screen.{MOD}.log.kind_unknown": "Turned on",
    f"screen.{MOD}.log.kind_from": "%s on %s",
    f"screen.{MOD}.log.tip_when": "Day %s at %s (%s)",
    f"screen.{MOD}.log.tip_from": "Carried over from %s by a converter",
    f"screen.{MOD}.log.tip_unknown": "From before version 0.35, when the cause wasn't kept",
    f"screen.{MOD}.log.tip_no_tuner": "No recorded tuner: an old transmitter that still works here",
    f"screen.{MOD}.log.tip_other_transmitter": "Tuned by %s, who isn't on it now (its position isn't shown)",
    f"screen.{MOD}.log.tip_remote": "Pressed by %s",
    f"screen.{MOD}.log.tip_other_remote": "Pressed by %s, who isn't on it now",
    f"screen.{MOD}.log.tip_remote_where": "Where they stood isn't kept",
    f"screen.{MOD}.log.ago_now": "just now",
    f"screen.{MOD}.log.ago_seconds": "%s seconds ago",
    f"screen.{MOD}.log.ago_minutes": "%s minutes ago",
    f"screen.{MOD}.log.ago_hours": "%s hours ago",
    f"screen.{MOD}.log.ago_days": "%s days ago",
    f"screen.{MOD}.log.summary": "%s turn-ons, the last one %s",
    f"screen.{MOD}.log.summary_one": "%s turn-on, %s",
    f"screen.{MOD}.log.kept": "Each dimension keeps the last %s. Times are in-game, while the world ran",
    f"screen.{MOD}.log.empty": "Nothing has turned it on yet",
    f"screen.{MOD}.log.not_allowed": "Only its owner and server operators can see its log",
    f"screen.{MOD}.audit.guests": "Guests...",
    f"screen.{MOD}.audit.guests_tooltip": "Let someone send on it for an hour, a day or a week",
    f"screen.{MOD}.guests.title": "Guests on frequency %s",
    f"screen.{MOD}.guests.subtitle": "Guests can send on it (and hear it, if encrypted) until their pass runs out",
    f"screen.{MOD}.guests.heading": "Guests (%s of %s)",
    f"screen.{MOD}.guests.name": "Player name",
    f"screen.{MOD}.guests.name_hint": "Player name",
    f"screen.{MOD}.guests.give_tooltip": "Give them a guest pass for %s (or renew theirs)",
    f"screen.{MOD}.guests.remove": "Remove",
    f"screen.{MOD}.guests.none": "No guests",
    f"screen.{MOD}.guests.hint": "Type a player's name (they must be online), then pick how long",
    f"screen.{MOD}.guests.hint_selected": "Pick a time to renew %s's pass, or Remove to end it now",
    f"screen.{MOD}.guests.left_days": "%s days left",
    f"screen.{MOD}.guests.left_hours": "%s hours left",
    f"screen.{MOD}.guests.left_minutes": "%s minutes left",
    f"screen.{MOD}.guests.left_seconds": "%s seconds left",
    f"message.{MOD}.guest_for_3600": "1 hour",
    f"message.{MOD}.guest_for_86400": "1 day",
    f"message.{MOD}.guest_for_604800": "1 week",
    f"message.{MOD}.guest_added": "%s is a guest on %s for %s",
    f"message.{MOD}.guest_renewed": "%s's pass on %s now runs for %s",
    f"message.{MOD}.guest_welcome": "%s made you a guest on %s for %s: you can send on it until then",
    f"message.{MOD}.guest_removed": "%s is no longer a guest on %s",
    f"message.{MOD}.guest_taken_back": "%s ended your guest pass on %s",
    f"message.{MOD}.guest_ran_out_owner": "%s's guest pass on %s ran out",
    f"message.{MOD}.guest_ran_out": "Your guest pass on %s ran out",
    f"message.{MOD}.guest_public": "%s is public: anyone can send on it already",
    f"message.{MOD}.guest_not_owner": "Only its owner can give guest passes on %s (it's %s's)",
    f"message.{MOD}.guest_not_guest": "%s isn't a guest on %s",
    f"message.{MOD}.guest_no_name": "Type a player's name first",
    f"message.{MOD}.guest_offline": "%s isn't online. A guest must be online when first added",
    f"message.{MOD}.guest_already_member": "%s is already on %s (as a friend or group member)",
    f"message.{MOD}.guest_full": "%s already has %s guests",
    f"message.{MOD}.intruder_prefix": "Radio alert: ",
    f"message.{MOD}.intruder_remote": "%1$s pressed a remote on %3$s, but it's private: refused",
    f"message.{MOD}.intruder_tune": "%1$s tried to tune a %2$s onto %3$s: refused",
    f"message.{MOD}.intruder_silent": "%1$s placed a %2$s on %3$s: it stays silent",
    f"message.{MOD}.intruder_deaf": "%1$s set a %2$s to %3$s: it can't hear it (encrypted)",
    f"message.{MOD}.intruder_missed": "While you were away, someone tried your private frequencies or keypads (%s times):",
    f"message.{MOD}.intruder_missed_more": "...and %s more",
    f"message.{MOD}.intruder_missed_one": "While you were away, someone tried one of your private frequencies or keypads:",
    f"screen.{MOD}.hand_over.title": "Hand over frequency %s",
    f"screen.{MOD}.hand_over.subtitle": "Pick who will own it. You stay on it as their friend",
    f"screen.{MOD}.hand_over.heading": "Its friends and group members",
    f"screen.{MOD}.hand_over.role_friend": "Friend",
    f"screen.{MOD}.hand_over.role_group": "Group member",
    f"screen.{MOD}.hand_over.button": "Hand over",
    f"screen.{MOD}.hand_over.confirm": "Click again",
    f"screen.{MOD}.hand_over.pick": "Click the player who should own it",
    f"screen.{MOD}.hand_over.what": "%s will own it. Everyone else on it, you too, stays on as a friend, so nothing goes silent",
    f"screen.{MOD}.hand_over.groups": "Groups belong to their owner, so their members become single friends",
    f"screen.{MOD}.hand_over.none": "Nobody else is on it. Add friends in the Frequency Tuner first",
    f"screen.{MOD}.hand_over.too_many": "It has %s members, but a frequency can have only %s friends. Take some off first",
    f"message.{MOD}.hand_over_done": "%s now belongs to %s. You stay on it as their friend",
    f"message.{MOD}.hand_over_received": "%s handed %s over to you: it's yours now",
    f"message.{MOD}.hand_over_public": "%s is public: there's nothing to hand over",
    f"message.{MOD}.hand_over_not_owner": "Only its owner can hand %s over (it's %s's)",
    f"message.{MOD}.hand_over_not_member": "%s isn't on %s. Hand it to one of its friends or group members",
    f"message.{MOD}.hand_over_limit": "%s already has the most private frequencies a player may have (%s)",
    f"message.{MOD}.hand_over_too_many": "%s has %s members, but a frequency can have only %s friends. Take some off first",
    f"screen.{MOD}.directory.empty": "No named or private frequencies yet",
    f"screen.{MOD}.directory.empty_hint": "Name one, give it a note, or make it private, in the Frequency Tuner's screen",
    f"screen.{MOD}.directory.no_match": "Nothing matches",
    f"screen.{MOD}.directory.count": "%s frequencies",
    f"screen.{MOD}.directory.count_one": "1 frequency",
    f"screen.{MOD}.directory.shown_of": "%s of %s frequencies",
    f"screen.{MOD}.directory.showing": "%s-%s of %s (scroll for more)",
    f"screen.{MOD}.tuner.name": "Name",
    f"screen.{MOD}.tuner.name_hint": "No name",
    f"screen.{MOD}.tuner.note": "Note",
    f"screen.{MOD}.tuner.note_tooltip": "Note: %s",
    f"screen.{MOD}.tuner.note_tooltip_members": "Note (members only): %s",
    f"screen.{MOD}.tuner.note_tooltip_empty": "Add a short note on what this frequency is for, like \"opens the barn door\". Everyone can see it",
    f"screen.{MOD}.note.title": "Note for frequency %s",
    f"screen.{MOD}.note.box": "Note",
    f"screen.{MOD}.note.hint": "What it's for, like: opens the barn door",
    f"screen.{MOD}.note.save": "Save",
    f"screen.{MOD}.note.shared": "Up to %s characters. Everyone sees it in the directory, Jade and tooltips",
    f"screen.{MOD}.note.locked": "Private to %s: only they and their friends can change its note",
    f"screen.{MOD}.note.who": "Who sees it: %s",
    f"screen.{MOD}.note.who_everyone": "Everyone",
    f"screen.{MOD}.note.who_members": "Members only",
    f"screen.{MOD}.note.who_tooltip": "Members only: just the owner, their friends, the members of groups it uses and server operators get this note. Nobody else's game even receives it",
    f"screen.{MOD}.note.who_tooltip_public": "Only a private frequency's note can be kept to its members. Make the frequency private first",
    f"screen.{MOD}.note.shared_members": "Up to %s characters. Only members see it in the directory, Jade and tooltips",
    f"frequency.{MOD}.named": "%s (%s)",
    f"message.{MOD}.name_set": "Frequency %s is now called %s, for everyone",
    f"message.{MOD}.name_removed": "Frequency %s has no name now",
    f"message.{MOD}.name_full": "Can't name more frequencies: this world already has %s names",
    f"message.{MOD}.note_set": "Note for frequency %s saved: %s",
    f"message.{MOD}.note_set_members": "Note for frequency %s saved, for members only: %s",
    f"message.{MOD}.note_removed": "Frequency %s has no note now",
    f"message.{MOD}.note_full": "Can't add more notes: this world already has %s",
    # Private frequencies
    f"screen.{MOD}.tuner.private_on": "Private",
    f"screen.{MOD}.tuner.encrypted_on": "Encrypted",
    f"screen.{MOD}.tuner.private_off": "Public",
    f"screen.{MOD}.tuner.private_tooltip": "Click: Public, Private, Encrypted (sneak-click goes back). Private: only you and your friends can send on this frequency, with remotes, transmitters, repeaters and converters; anyone can still listen. Encrypted: also only your and your friends' receivers, pagers, scanners and converters can hear it",
    f"screen.{MOD}.tuner.friends": "Friends",
    f"screen.{MOD}.tuner.friends_hint": "Names, #group",
    f"screen.{MOD}.tuner.friends_tooltip": "Players who may send on it too, separated by commas (up to %s), and your friend groups as #name. Players must be online when you first add them",
    f"screen.{MOD}.tuner.groups": "Groups",
    f"screen.{MOD}.tuner.groups_tooltip": "Friend groups: named lists of players you can use on several private frequencies at once",
    f"screen.{MOD}.tuner.privacy_public": "Public: anyone can send on it",
    f"screen.{MOD}.tuner.privacy_mine": "Private: only you can send on it",
    f"screen.{MOD}.tuner.privacy_mine_one_friend": "Private: you and 1 friend can send on it",
    f"screen.{MOD}.tuner.privacy_mine_friends": "Private: you and %s friends can send on it",
    f"screen.{MOD}.tuner.privacy_friend": "Private to %s: you're a friend, so you can send",
    f"screen.{MOD}.tuner.privacy_other": "Private to %s: you can't send on it",
    f"screen.{MOD}.tuner.privacy_mine_encrypted": "Encrypted: only you can send on it or hear it",
    f"screen.{MOD}.tuner.privacy_mine_one_friend_encrypted": "Encrypted: you and 1 friend can send and hear",
    f"screen.{MOD}.tuner.privacy_mine_friends_encrypted": "Encrypted: you and %s friends can send and hear",
    f"screen.{MOD}.tuner.privacy_friend_encrypted": "Encrypted by %s: you're a friend, so you can send and hear",
    f"screen.{MOD}.tuner.privacy_other_encrypted": "Encrypted by %s: you can't send on it or hear it",
    f"screen.{MOD}.tuner.privacy_will_claim": "Done makes it private",
    f"screen.{MOD}.tuner.privacy_will_claim_encrypted": "Done makes it encrypted",
    f"screen.{MOD}.tuner.privacy_will_encrypt": "Done encrypts it: only members will hear it",
    f"screen.{MOD}.tuner.privacy_will_decrypt": "Done stops encrypting it: anyone can listen again",
    f"screen.{MOD}.tuner.privacy_will_update": "Done changes its friends",
    f"screen.{MOD}.tuner.privacy_will_release": "Done makes it public again",
    f"screen.{MOD}.tuner.privacy_will_release_note": "Done makes it public again, and its note visible to everyone",
    f"message.{MOD}.private_set": "%s is now private: only you can send on it",
    f"message.{MOD}.private_set_friends": "%s is now private: only you and %s can send on it",
    f"message.{MOD}.encrypted_set": "%s is now encrypted: only you can send on it or hear it",
    f"message.{MOD}.encrypted_set_friends": "%s is now encrypted: only you and %s can send on it or hear it",
    f"message.{MOD}.encrypted_deaf": "Deaf: %s is encrypted by %s, so this won't hear it",
    f"message.{MOD}.private_removed": "%s is public again: anyone can send on it",
    f"message.{MOD}.private_not_yours": "%s is private to %s: only they can change that",
    f"message.{MOD}.private_disabled": "Private frequencies are turned off on this server",
    f"message.{MOD}.private_limit": "You already have %s private frequencies, the most this server allows",
    f"message.{MOD}.private_full": "This world can't have more than %s private frequencies",
    f"message.{MOD}.private_in_use": "Can't make %s private: other players' blocks already send on it (%s). Pick a frequency nobody else uses",
    f"message.{MOD}.private_friends_missing": "Not added as friends (they must be online the first time): %s",
    f"message.{MOD}.private_friends_too_many": "Only the first %s friends were added",
    f"message.{MOD}.private_refused_send": "Can't send: %s is private to %s",
    f"message.{MOD}.private_refused_block": "Can't do that: it would send on %s, which is private to %s",
    f"message.{MOD}.private_refused_name": "Only %s and their friends can rename frequency %s",
    f"message.{MOD}.private_refused_note": "Only %s and their friends can change the note on frequency %s",
    f"message.{MOD}.private_silenced": "Silent: %s is private to %s, so this won't broadcast",
    f"screen.{MOD}.converter.silenced": "Silent: %s is someone else's private frequency",
    f"screen.{MOD}.converter.deaf": "Deaf: %s is encrypted by %s, so this can't hear it",
    f"screen.{MOD}.converter.directory_listens": "Pick the frequency it listens on from the frequency directory",
    f"screen.{MOD}.converter.directory_sends": "Pick the frequency it sends on from the frequency directory",
    f"screen.{MOD}.multi_remote.directory": "Pick button %s's frequency from the frequency directory",
    # Friend groups
    f"screen.{MOD}.groups.title": "Friend Groups",
    f"screen.{MOD}.groups.new": "+ New group",
    f"screen.{MOD}.groups.full_row": "(no room for more)",
    f"screen.{MOD}.groups.name": "Name",
    f"screen.{MOD}.groups.name_hint": "Base crew",
    f"screen.{MOD}.groups.members": "Members (names, separated by commas)",
    f"screen.{MOD}.groups.members_hint": "Alex, Sam",
    f"screen.{MOD}.groups.used_by": "Used by: %s",
    f"screen.{MOD}.groups.unused": "Not used by any frequency yet. Use here adds it to the one in the tuner",
    f"screen.{MOD}.groups.none": "No groups yet. Type a name and members, then Save",
    f"screen.{MOD}.groups.full": "You have %s groups, the most you can have",
    f"screen.{MOD}.groups.save": "Save",
    f"screen.{MOD}.groups.use": "Use here",
    f"screen.{MOD}.groups.use_tooltip": "Add this group to the frequency in the tuner's Friends box (#name), and make it private",
    f"screen.{MOD}.groups.use_unsaved": "Save the group first",
    f"screen.{MOD}.groups.delete": "Delete",
    f"screen.{MOD}.groups.delete_confirm": "Sure?",
    f"screen.{MOD}.groups.delete_tooltip": "Deletes the group: every frequency using it loses its members. Click twice",
    f"screen.{MOD}.groups.status_new": "Save creates this group",
    f"screen.{MOD}.groups.status_changed": "Save changes this group, and who can send on every frequency using it",
    f"screen.{MOD}.groups.status_count": "%s members",
    f"screen.{MOD}.groups.status_count_one": "1 member",
    f"screen.{MOD}.directory.tip_groups": "Groups: %s",
    f"message.{MOD}.group_created": "Group %s created with %s members",
    f"message.{MOD}.group_created_one": "Group %s created with 1 member",
    f"message.{MOD}.group_saved": "Group %s saved: %s members, used by %s frequencies",
    f"message.{MOD}.group_saved_one": "Group %1$s saved: 1 member, used by %3$s frequencies",
    f"message.{MOD}.group_deleted": "Group %s deleted (%s frequencies used it)",
    f"message.{MOD}.group_name_needed": "A group needs a name",
    f"message.{MOD}.group_name_taken": "You already have a group called %s",
    f"message.{MOD}.group_limit": "You already have %s groups, the most you can have",
    f"message.{MOD}.group_members_missing": "Not added to the group (they must be online the first time): %s",
    f"message.{MOD}.group_members_too_many": "Only the first %s members were added",
    f"message.{MOD}.private_no_group": "You have no group called: %s",
    f"message.{MOD}.private_too_many_groups": "Only the first %s groups were used",
    f"screen.{MOD}.tuner.favorite": "Favorite %s: %s. Click to use, sneak-click to replace, right-click to clear",
    f"screen.{MOD}.tuner.favorite_empty": "Favorite %s is empty. Click to save the frequency above",
    f"tooltip.{MOD}.favorites": "Favorites: %s",
    f"tooltip.{MOD}.frequency_scroll": "Sneak + scroll to step the frequency; add Ctrl to jump between favorites",
    f"message.{MOD}.frequency_scrolled": "%s: frequency %s",
    f"message.{MOD}.frequency_scrolled_favorite": "%s: frequency %s (favorite %s)",
    f"message.{MOD}.no_favorites": "No favorites yet. Save some in its frequency screen",
    f"tooltip.{MOD}.scanner_use": "Lists every frequency you can hear where you stand",
    f"tooltip.{MOD}.scanner_open": "Right-click to open it",
    f"tooltip.{MOD}.scanner_tune": "Its Tune buttons set the tuner, remote, pager or meter in your other hand",
    f"screen.{MOD}.scanner.scanning": "Scanning...",
    f"screen.{MOD}.scanner.nothing": "Nothing heard here",
    f"screen.{MOD}.scanner.heard_one": "1 frequency heard here",
    f"screen.{MOD}.scanner.heard_many": "%s frequencies heard here",
    f"screen.{MOD}.scanner.heading_frequency": "Frequency",
    f"screen.{MOD}.scanner.heading_signal": "Signal",
    f"screen.{MOD}.scanner.heading_source": "Strongest source",
    f"screen.{MOD}.scanner.on": "ON %s",
    f"screen.{MOD}.scanner.off": "off",
    f"screen.{MOD}.scanner.transmitter": "Transmitter, %s",
    f"screen.{MOD}.scanner.transmitters": "Transmitter, %s (+%s more)",
    f"screen.{MOD}.scanner.repeater": "Repeater, %s",
    f"screen.{MOD}.scanner.repeaters": "Repeater, %s (+%s more)",
    f"screen.{MOD}.scanner.converter": "Converter, %s",
    f"screen.{MOD}.scanner.converters": "Converter, %s (+%s more)",
    f"screen.{MOD}.scanner.remote_pulse": "Remote pulse",
    f"screen.{MOD}.scanner.remote_toggle": "Remote toggle",
    f"screen.{MOD}.scanner.plus_one": "%s, plus 1 tower",
    f"screen.{MOD}.scanner.plus_many": "%s, plus %s towers",
    f"screen.{MOD}.scanner.tune": "Tune",
    f"screen.{MOD}.scanner.tune_hint": "Hold a tuner, remote, pager or meter in your other hand to tune it",
    f"screen.{MOD}.scanner.tune_tooltip": "Set %s to frequency %s",
    f"screen.{MOD}.scanner.tuned": "%s tuned to frequency %s",
    f"screen.{MOD}.scanner.more": "Showing %s-%s of %s, scroll for more",
    f"screen.{MOD}.scanner.track_hint": "Click a transmitter, repeater or converter row to track it with an arrow",
    f"screen.{MOD}.scanner.tracking": "Tracking frequency %s. Click its row again to stop",
    f"screen.{MOD}.scanner.row_track": "Click to track this tower with an arrow on screen",
    f"screen.{MOD}.scanner.row_stop": "Click to stop tracking",
    f"screen.{MOD}.scanner.row_remote": "Remote presses can't be tracked",
    f"message.{MOD}.scanner_tracking": "Tracking frequency %s",
    f"message.{MOD}.scanner_stopped": "Stopped tracking",
    f"message.{MOD}.scanner_lost": "Lost track of frequency %s: its tower is gone",
    f"message.{MOD}.scanner_lost_encrypted": "Lost track of frequency %s: it's encrypted, so you can't hear it",
    f"message.{MOD}.scanner_cant_track": "No transmitter, repeater or converter on frequency %s reaches you here",
    f"hud.{MOD}.scanner_distance": "Frequency %s: %s blocks",
    f"hud.{MOD}.scanner_distance_up": "Frequency %s: %s blocks, %s up",
    f"hud.{MOD}.scanner_distance_down": "Frequency %s: %s blocks, %s down",
    f"hud.{MOD}.scanner_here": "Frequency %s: you're there",
    f"hud.{MOD}.scanner_other_dimension": "Frequency %s: in another dimension",
    f"tooltip.{MOD}.scanner_tracking": "Tracking frequency %s",
    f"tooltip.{MOD}.scanner_track": "Click a transmitter, repeater or converter row to track it with an arrow while you hold the scanner",
    f"message.{MOD}.pager_alert": "%s: frequency %s is ON (strength %s)",
    f"message.{MOD}.pager_sound_on": "Pager sound ON",
    f"message.{MOD}.pager_sound_off": "Pager sound OFF (messages only)",
    f"message.{MOD}.frequency_copied_pager": "Pager tuned to frequency %s",
    f"tooltip.{MOD}.pager_status_on": "Signal: ON (strength %s)",
    f"tooltip.{MOD}.pager_status_quiet": "Signal: quiet",
    f"tooltip.{MOD}.pager_encrypted": "Encrypted by %s: this pager can't hear it",
    f"tooltip.{MOD}.pager_beeping": "Sound: on",
    f"tooltip.{MOD}.pager_silent": "Sound: off (messages only)",
    f"tooltip.{MOD}.pager_chat_on": "Chat log: on (alerts also go in chat)",
    f"tooltip.{MOD}.pager_chat_off": "Chat log: off",
    f"message.{MOD}.pager_log": "[Day %s, %s] ",
    f"message.{MOD}.pager_catchup_once": "%s: while you were away, frequency %s turned on once, on Day %s at %s",
    f"message.{MOD}.pager_catchup_many": "%s: while you were away, frequency %s turned on %s times, last on Day %s at %s",
    f"message.{MOD}.pager_catchup_lots": "%s: while you were away, frequency %s turned on at least %s times, last on Day %s at %s",
    f"message.{MOD}.pager_catchup_none_one": "Your pager heard nothing while you were away",
    f"message.{MOD}.pager_catchup_none_many": "Your %s pagers heard nothing while you were away",
    f"screen.{MOD}.tuner.pager_sound": "Sound",
    f"screen.{MOD}.tuner.pager_chat": "Chat log",
    f"screen.{MOD}.tuner.pager_chat_tooltip": "Also post alerts in chat, with the in-game day and time, so you can scroll back to see what you missed",
    f"tooltip.{MOD}.pager_use": "Beeps when its frequency turns on near you, from anywhere in your inventory",
    f"tooltip.{MOD}.pager_toggle": "Right-click to switch the sound on or off",
    f"tooltip.{MOD}.pager_configure": "Sneak + right-click the air to change its frequency, sound and chat log",
    f"tooltip.{MOD}.pager_copy": "Sneak + right-click a transmitter or receiver to copy its frequency",
    f"tooltip.{MOD}.remote_button_line": "%s: frequency %s, %s",
    f"tooltip.{MOD}.remote_button_line_selected": "\u00bb %s: frequency %s, %s",
    f"tooltip.{MOD}.remote_button_default_name": "Button %s",
    f"tooltip.{MOD}.multi_remote_use": "Right-click to press the selected button",
    f"tooltip.{MOD}.multi_remote_select": "Sneak + scroll to choose the selected button",
    f"tooltip.{MOD}.multi_remote_configure": "Sneak + right-click the air to open the keypad",
    f"tooltip.{MOD}.multi_remote_copy": "Sneak + right-click a transmitter or receiver to copy its frequency into the selected button",
    f"message.{MOD}.remote_button": "%s: %s",
    f"message.{MOD}.remote_button_selected": "%s selected (frequency %s, %s)",
    f"message.{MOD}.frequency_copied_button": "Copied frequency %s to %s",
    f"screen.{MOD}.multi_remote.frequency": "Button %s frequency",
    f"screen.{MOD}.multi_remote.press": "Press",
    f"screen.{MOD}.multi_remote.hint": "Click a number to pick the right-click button. Sneak-click a color or length to go back.",
    f"screen.{MOD}.multi_remote.color": "Color: %s",
    f"screen.{MOD}.multi_remote.name": "Button %s name",
    f"screen.{MOD}.multi_remote.heading_name": "Name",
    f"screen.{MOD}.multi_remote.heading_frequency": "Frequency",
    f"screen.{MOD}.multi_remote.heading_mode": "Mode",
    f"screen.{MOD}.multi_remote.heading_length": "Length",
    f"screen.{MOD}.locator.checking": "Checking for receivers...",
    f"screen.{MOD}.locator.here": "right next to you",
    f"screen.{MOD}.locator.distance": "%s blocks %s",
    f"screen.{MOD}.locator.in_range_one": "1 receiver in range, %s",
    f"screen.{MOD}.locator.in_range_many": "%s receivers in range, nearest %s",
    f"screen.{MOD}.locator.out_of_range": "None in range. Nearest is %s",
    f"screen.{MOD}.locator.none": "No receivers within %s blocks",
    f"screen.{MOD}.locator.none_towers": "No receivers within %s blocks or in tower range",
    f"screen.{MOD}.locator.reach": "Your reach: %s blocks",
    f"screen.{MOD}.locator.reach_one_tower": "Your reach: %s blocks, plus 1 tower relaying",
    f"screen.{MOD}.locator.reach_towers": "Your reach: %s blocks, plus %s towers relaying",
    f"screen.{MOD}.locator.dir_n": "north",
    f"screen.{MOD}.locator.dir_ne": "north-east",
    f"screen.{MOD}.locator.dir_e": "east",
    f"screen.{MOD}.locator.dir_se": "south-east",
    f"screen.{MOD}.locator.dir_s": "south",
    f"screen.{MOD}.locator.dir_sw": "south-west",
    f"screen.{MOD}.locator.dir_w": "west",
    f"screen.{MOD}.locator.dir_nw": "north-west",
    f"tooltip.{MOD}.remote_mode": "Mode: %s",
    f"tooltip.{MOD}.remote_mode_pulse": "Pulse",
    f"tooltip.{MOD}.remote_mode_toggle": "Toggle",
    f"tooltip.{MOD}.remote_mode_pulse_length": "Pulse %s",
    f"screen.{MOD}.tuner.pulse_length": "Pulse length: %s",
    f"tooltip.{MOD}.remote_use": "Right-click to press the button",
    f"tooltip.{MOD}.remote_configure": "Sneak + right-click the air to change frequency and mode",
    f"tooltip.{MOD}.remote_copy": "Sneak + right-click a transmitter or receiver to copy its frequency",
    f"message.{MOD}.frequency_copied_remote": "Copied frequency %s to the remote",
    f"message.{MOD}.remote_pulse": "%s pulse sent on frequency %s (reach %s)",
    f"message.{MOD}.remote_on": "Frequency %s switched ON (reach %s)",
    f"message.{MOD}.reach_blocks": "%s blocks",
    f"message.{MOD}.reach_one_tower": "%s blocks + 1 tower",
    f"message.{MOD}.reach_towers": "%s blocks + %s towers",
    f"message.{MOD}.reach_plus_one_repeater": "%s + 1 repeater",
    f"message.{MOD}.reach_plus_repeaters": "%s + %s repeaters",
    f"message.{MOD}.reach_plus_one_converter": "%s + 1 converter",
    f"message.{MOD}.reach_plus_converters": "%s + %s converters",
    f"message.{MOD}.remote_off": "Frequency %s switched OFF",
    f"message.{MOD}.remote_result": "%s | %s",
    f"message.{MOD}.remote_heard_one": "Heard by 1 receiver",
    f"message.{MOD}.remote_heard_many": "Heard by %s receivers",
    f"message.{MOD}.remote_not_heard": "No receiver in range",

    f"tooltip.{MOD}.frequency": "Frequency: %s",
    f"tooltip.{MOD}.note": "\u201c%s\u201d",
    f"tooltip.{MOD}.note_members_only": " (members only)",
    f"tooltip.{MOD}.tune_hint": "Use a Frequency Tuner to change its frequency.",
    f"tooltip.{MOD}.tuner_use_air": "Right-click the air to type a frequency, or give it a name",
    f"tooltip.{MOD}.tuner_use_block": "Right-click a transmitter or receiver to tune it",
    f"tooltip.{MOD}.tuner_copy": "Sneak + right-click one to copy its frequency",

    f"message.{MOD}.frequency_set": "Tuned to frequency %s",
    f"message.{MOD}.frequency_copied": "Copied frequency %s to the tuner",
    f"message.{MOD}.transmitter_status": "Transmitter | Frequency %s | Sending %s | %s | Reach %s blocks",
    f"message.{MOD}.repeater_status": "Repeater | Frequency %s | %s | %s | Reach %s blocks",
    f"message.{MOD}.repeater_repeating": "Repeating %s",
    f"message.{MOD}.repeater_quiet": "Nothing to repeat",
    f"message.{MOD}.receiver_status": "Receiver | Frequency %s | Output %s | Towers in range: %s",
    f"message.{MOD}.toggle_status": "Toggle Receiver | Frequency %s | %s | Hearing %s | Towers in range: %s",
    f"message.{MOD}.toggle_on": "ON",
    f"message.{MOD}.inverted_status": "Inverted Receiver | Frequency %s | Hearing %s | Output %s | Towers in range: %s",
    f"message.{MOD}.toggle_off": "OFF",
    f"message.{MOD}.height_above": "%s blocks above sea level",
    f"message.{MOD}.height_below": "At or below sea level",

    f"screen.{MOD}.tuner.title": "Frequency Tuner",
    f"screen.{MOD}.tuner.input": "Frequency",
    f"screen.{MOD}.tuner.range": "Any whole number from %s to %s",

    f"{MOD}.configuration.title": "Reiko's Redstone Signal Tower",
    f"{MOD}.configuration.section.{MOD}.server.toml": "Server settings",
    f"{MOD}.configuration.section.{MOD}.server.toml.title": "Server settings",
    f"{MOD}.configuration.baseReach": "Base reach",
    f"{MOD}.configuration.reachPerBlockOfHeight": "Reach per block of height",
    f"{MOD}.configuration.maxReach": "Max reach (0 = no limit)",
    f"{MOD}.configuration.towersRelayRemotes": "Transmitters and repeaters relay remote presses",
    f"{MOD}.configuration.privateFrequenciesPerPlayer": "Private frequencies per player (0 = none)",
    # Jade integration (shown when looking at a block, if Jade is installed)
    f"config.jade.plugin_{MOD}.radio": "Radio frequency, signal, reach",
    f"jade.{MOD}.frequency": "Frequency: %s",
    f"jade.{MOD}.sending": "Sending: %s",
    f"jade.{MOD}.sending_nothing": "Sending: nothing",
    f"jade.{MOD}.broadcasting": "Broadcasting",
    f"jade.{MOD}.repeating": "Repeating: %s",
    f"jade.{MOD}.repeating_on": "Repeating",
    f"jade.{MOD}.repeating_nothing": "Nothing to repeat",
    f"jade.{MOD}.output": "Output: %s",
    f"jade.{MOD}.output_off": "Output: off",
    f"jade.{MOD}.towers": "Towers in range: %s",
    f"jade.{MOD}.reach": "Reach: %s blocks",
    f"jade.{MOD}.mast_top": "Top: %s, %s blocks up",
    f"jade.{MOD}.mast_no_top": "No transmitter, repeater or converter on top",
    f"jade.{MOD}.listens": "Listens on: %s",
    f"jade.{MOD}.switched_on": "Switched on",
    f"jade.{MOD}.switched_off": "Switched off",
    f"jade.{MOD}.inverted_on": "On (frequency silent)",
    f"jade.{MOD}.inverted_off": "Off (frequency on)",
    f"jade.{MOD}.hearing": "Hearing: %s",
    f"jade.{MOD}.hearing_nothing": "Hearing nothing",
    f"jade.{MOD}.sends": "Sends on: %s",
    f"jade.{MOD}.converting": "Converting: %s",
    f"jade.{MOD}.converting_on": "Converting",
    f"jade.{MOD}.converting_nothing": "Nothing to convert",
    f"jade.{MOD}.gate_closed": "Gate closed: sending nothing",
    f"jade.{MOD}.private": "Private to %s",
    f"jade.{MOD}.encrypted": "Encrypted by %s",
    f"jade.{MOD}.deaf": "Deaf: encrypted by %s",
    f"jade.{MOD}.silenced": "Silent: private to %s",
    # v0.37.0: the reach preview ring and the Code Keypad
    f"hud.{MOD}.reach_ring": "Reach ring: %s on %s, %s blocks",
    f"hud.{MOD}.reach_ring_dish": "Reach ring: %s on %s, %s blocks (%s to the %s)",
    f"hud.{MOD}.reach_ring_inside": "You're inside: %s blocks to the edge",
    f"hud.{MOD}.reach_ring_edge": "You're right at the edge",
    f"hud.{MOD}.reach_ring_outside": "You're %s blocks outside",
    f"tooltip.{MOD}.tuner_ring": "Hold it and look at a transmitter, repeater, converter or keypad: a ring on the ground shows its reach",
    f"block.{MOD}.code_keypad": "Code Keypad",
    f"block.{MOD}.code_keypad.tooltip": "Goes on a wall, floor or ceiling. The right code sends a pulse on its frequency and powers the block it's on.",
    f"message.{MOD}.keypad_placed": "Code Keypad placed: right-click it to set its code",
    f"message.{MOD}.keypad_not_yours": "This Code Keypad is %s's: only they can change it",
    f"message.{MOD}.keypad_not_yours_tune": "This Code Keypad is %s's: only they can tune it",
    f"message.{MOD}.keypad_bad_code": "A code is %s to %s digits",
    f"message.{MOD}.keypad_code_saved": "New code saved (%s digits). Pulse: %s",
    f"message.{MOD}.keypad_pulse_saved": "Pulse length: %s",
    f"message.{MOD}.keypad_nothing_changed": "Nothing changed",
    f"message.{MOD}.keypad_locked": "Locked: try again in %s seconds",
    f"message.{MOD}.keypad_no_code": "No code set yet: %s hasn't set one",
    f"message.{MOD}.keypad_wrong": "Wrong code (%s of %s tries)",
    f"message.{MOD}.keypad_wrong_locked": "Wrong code: locked for %s seconds",
    f"message.{MOD}.keypad_silent": "Silent: this keypad can't send on that frequency",
    f"message.{MOD}.keypad_accepted": "Code accepted: %1$s pulse on %2$s",
    f"message.{MOD}.intruder_keypad": "%1$s typed the wrong code 3 times on your %2$s at %4$s, on %3$s: locked out for 30 seconds",
    f"screen.{MOD}.keypad.set_first": "No code yet: type %s to %s digits, then Set code",
    f"screen.{MOD}.keypad.no_code": "No code set yet",
    f"screen.{MOD}.keypad.type_code": "Type the code, then OK",
    f"screen.{MOD}.keypad.clear": "Clear",
    f"screen.{MOD}.keypad.ok": "Send the code (Enter)",
    f"screen.{MOD}.keypad.set_code": "Set code",
    f"screen.{MOD}.keypad.set_code_tooltip": "Saves the digits typed (%s to %s) as the new code. Only you can change it",
    f"screen.{MOD}.keypad.pulse": "Pulse: %s",
    f"screen.{MOD}.keypad.pulse_tooltip": "How long the pulse (and the power to the block it's on) lasts. Sneak-click: shorter",
    f"screen.{MOD}.keypad.frequency": "Frequency %s",
    f"screen.{MOD}.keypad.frequency_owner": "Frequency %s, %s's keypad",
    f"screen.{MOD}.keypad.display_empty": "- - - -",
    f"screen.{MOD}.keypad.display_locked": "LOCKED",
    f"screen.{MOD}.log.kind_keypad": "Code Keypad",
    f"screen.{MOD}.log.tip_keypad": "Code typed by %s",
    f"screen.{MOD}.log.tip_other_keypad": "Code typed by %s, who isn't on it",
    f"jade.{MOD}.keypad_owner": "Owner: %s",
    f"jade.{MOD}.keypad_no_code": "No code set yet",
    f"jade.{MOD}.keypad_open": "Code accepted: on",
    f"jade.{MOD}.keypad_waiting": "Waiting for the code",
    # v0.38.0: the Compact Radio Transmitter and keypad guest codes
    f"block.{MOD}.compact_radio_transmitter": "Compact Radio Transmitter",
    f"block.{MOD}.compact_radio_transmitter.tooltip": "A button-sized transmitter. Sticks to walls, floors and ceilings and broadcasts the redstone signal around it, but only over a short reach (16 blocks by default), whatever its height.",
    f"message.{MOD}.compact_transmitter_status": "Compact Transmitter | Frequency %s | Sending %s | Reach %s blocks",
    f"screen.{MOD}.audit.kind_compact_transmitter": "Compact Transmitter",
    # v0.39.0: Radio Motion Sensor
    f"block.{MOD}.radio_motion_sensor": "Radio Motion Sensor",
    f"block.{MOD}.radio_motion_sensor.tooltip": "Sends on its frequency while it sees a player nearby, over a compact transmitter's short reach. Its owner picks the range and whether it reacts to everyone or only strangers.",
    f"message.{MOD}.sensor_status": "Motion Sensor | Frequency %s | Owner: %s | Reach %s blocks",
    f"message.{MOD}.sensor_not_yours": "This motion sensor is %s's: only they can change it",
    f"message.{MOD}.sensor_not_yours_tune": "This motion sensor is %s's: only they can re-tune it",
    f"message.{MOD}.sensor_set": "Motion sensor set: looks %s blocks, reacts to %s",
    f"message.{MOD}.sensor_mode_everyone": "everyone",
    f"message.{MOD}.sensor_mode_strangers": "strangers only",
    f"screen.{MOD}.audit.kind_motion_sensor": "Motion Sensor",
    f"screen.{MOD}.log.kind_sensor": "Motion sensor",
    f"screen.{MOD}.log.tip_sensor": "%s was seen by the sensor",
    f"screen.{MOD}.log.tip_other_sensor": "%s (not a member) was seen by the sensor",
    f"screen.{MOD}.log.tip_sensor_nobody": "Nobody is recorded",
    f"screen.{MOD}.sensor.frequency": "Frequency %s | Owner: %s",
    f"screen.{MOD}.sensor.range": "Range",
    f"screen.{MOD}.sensor.blocks": "%s blocks",
    f"screen.{MOD}.sensor.range_tip": "How far it looks, to the middle of a player. Shift-click or scroll to go back.",
    f"screen.{MOD}.sensor.mode": "Reacts to",
    f"screen.{MOD}.sensor.mode_everyone": "Everyone",
    f"screen.{MOD}.sensor.mode_strangers": "Strangers only",
    f"screen.{MOD}.sensor.mode_everyone_tip": "Any player it can see, you included.",
    f"screen.{MOD}.sensor.mode_strangers_tip": "Not you, and on a private frequency not its owner or members (friends, groups and guests).",
    f"screen.{MOD}.sensor.explain_everyone": "Sends while it sees any player in range",
    f"screen.{MOD}.sensor.explain_strangers": "Ignores you and the frequency's members",
    f"screen.{MOD}.sensor.sees": "Sees %s right now",
    f"screen.{MOD}.sensor.sees_nobody": "Sees nobody right now",
    f"screen.{MOD}.sensor.silenced": "Silent: %s is someone else's private frequency",
    f"screen.{MOD}.sensor.reach": "Reach: %s blocks",
    f"jade.{MOD}.sensor_owner": "Owner: %s",
    f"jade.{MOD}.sensor_settings": "Looks %s blocks, reacts to %s",
    f"jade.{MOD}.sensor_mode_everyone": "everyone",
    f"jade.{MOD}.sensor_mode_strangers": "strangers only",
    f"jade.{MOD}.sensor_idle": "Sees nobody",
    f"jade.{MOD}.sensor_on": "Sees someone: sending",
    f"jade.{MOD}.sensor_sees": "Sees %s: sending",
    f"message.{MOD}.keypad_guest_accepted": "Guest code \"%1$s\" accepted: %2$s pulse on %3$s",
    f"message.{MOD}.keypad_guest_used_up": "That guest code is used up",
    f"message.{MOD}.keypad_guest_expired": "That guest code has expired",
    f"message.{MOD}.keypad_guest_used": "%1$s at %2$s: %3$s used the guest code \"%4$s\" (%5$s)",
    f"message.{MOD}.keypad_guest_left_many": "%s uses left",
    f"message.{MOD}.keypad_guest_left_one": "1 use left",
    f"message.{MOD}.keypad_guest_left_none": "now used up",
    f"message.{MOD}.keypad_guest_left_unlimited": "no limit",
    f"message.{MOD}.keypad_guest_added": "Guest code \"%1$s\" added: %2$s, %3$s",
    f"message.{MOD}.keypad_guest_uses_many": "%s uses",
    f"message.{MOD}.keypad_guest_uses_one": "1 use",
    f"message.{MOD}.keypad_guest_uses_unlimited": "any number of uses",
    f"message.{MOD}.keypad_guest_for": "for %s",
    f"message.{MOD}.keypad_guest_no_time": "no time limit",
    f"message.{MOD}.keypad_guest_removed": "Guest code \"%s\" removed",
    f"message.{MOD}.keypad_guest_bad_label": "Give the guest code a name (up to 16 characters)",
    f"message.{MOD}.keypad_guest_code_taken": "That code is already used on this keypad",
    f"message.{MOD}.keypad_guest_label_taken": "There's already a guest code called \"%s\"",
    f"message.{MOD}.keypad_guest_full": "A keypad holds at most %s guest codes",
    f"message.{MOD}.keypad_guest_unknown": "No guest code called \"%s\"",
    f"duration.{MOD}.hour": "1 hour",
    f"duration.{MOD}.day": "1 day",
    f"duration.{MOD}.week": "1 week",
    f"screen.{MOD}.keypad.guest_codes": "Guest codes...",
    f"screen.{MOD}.keypad.guest_codes_tooltip": "Extra codes for visitors, each working a set number of times or for a set time",
    f"screen.{MOD}.keypad_guests.title": "Guest codes",
    f"screen.{MOD}.keypad_guests.subtitle": "Extra codes for visitors: each works a set number of times, or until its time runs out",
    f"screen.{MOD}.keypad_guests.col_name": "Name",
    f"screen.{MOD}.keypad_guests.col_code": "Code",
    f"screen.{MOD}.keypad_guests.col_uses": "Uses left",
    f"screen.{MOD}.keypad_guests.col_time": "Time left",
    f"screen.{MOD}.keypad_guests.no_limit": "No limit",
    f"screen.{MOD}.keypad_guests.any": "Any",
    f"screen.{MOD}.keypad_guests.used_up": "Used up",
    f"screen.{MOD}.keypad_guests.expired": "Expired",
    f"screen.{MOD}.keypad_guests.days": "%sd %sh",
    f"screen.{MOD}.keypad_guests.hours": "%sh %sm",
    f"screen.{MOD}.keypad_guests.minutes": "%s min",
    f"screen.{MOD}.keypad_guests.name_hint": "Name",
    f"screen.{MOD}.keypad_guests.code_hint": "Code",
    f"screen.{MOD}.keypad_guests.uses": "Uses: %s",
    f"screen.{MOD}.keypad_guests.time": "Time: %s",
    f"screen.{MOD}.keypad_guests.add": "Add",
    f"screen.{MOD}.keypad_guests.remove": "Remove",
    f"screen.{MOD}.keypad_guests.empty": "No guest codes yet",
    f"screen.{MOD}.keypad_guests.hint": "Type a name and a 4 to 8 digit code, pick the uses and time, then Add",
    f"screen.{MOD}.keypad_guests.selected": "\"%s\" selected: Remove ends it now",
    f"screen.{MOD}.keypad_guests.tip_used": "Used %s times, last by %s",
    f"screen.{MOD}.keypad_guests.tip_unused": "Not used yet",
    f"screen.{MOD}.log.kind_keypad_guest": "Guest code: %s",
    f"screen.{MOD}.log.tip_keypad_guest": "Guest code \"%s\", typed by %s",
})

# ---------------------------------------------------------------- recipes

def shaped(pattern, key, result, count=1, category="redstone"):
    return {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "key": {k: {"item": v} for k, v in key.items()},
        "pattern": pattern,
        "result": {"count": count, "id": f"{MOD}:{result}"},
    }


recipes = {
    "radio_transmitter": (shaped([" L ", "IEI", "ITI"], {
        "L": "minecraft:lightning_rod", "I": "minecraft:iron_ingot",
        "E": "minecraft:ender_pearl", "T": "minecraft:redstone_torch"}, "radio_transmitter"),
        "minecraft:ender_pearl"),
    "radio_receiver": (shaped([" L ", "IEI", "IPI"], {
        "L": "minecraft:lightning_rod", "I": "minecraft:iron_ingot",
        "E": "minecraft:ender_pearl", "P": "minecraft:repeater"}, "radio_receiver"),
        "minecraft:ender_pearl"),
    "radio_mast": (shaped(["B B", "BIB", "B B"], {
        "B": "minecraft:iron_bars", "I": "minecraft:iron_ingot"}, "radio_mast", count=6),
        "minecraft:iron_bars"),
    "frequency_tuner": (shaped([" T ", "IGI", "IRI"], {
        "T": "minecraft:redstone_torch", "I": "minecraft:iron_ingot",
        "G": "minecraft:glass_pane", "R": "minecraft:redstone"}, "frequency_tuner", category="equipment"),
        "minecraft:redstone"),
}
def shapeless(ingredient, result):
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [{"item": f"{MOD}:{ingredient}"}],
        "result": {"count": 1, "id": f"{MOD}:{result}"},
    }


def shapeless_any(ingredient, result):
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [{"item": ingredient}],
        "result": {"count": 1, "id": result},
    }


recipes["wall_redstone"] = (shapeless_any("minecraft:redstone", f"{MOD}:wall_redstone"), "minecraft:redstone")
recipes["redstone_from_wall_redstone"] = (shapeless_any(f"{MOD}:wall_redstone", "minecraft:redstone"), f"{MOD}:wall_redstone")
recipes["compact_radio_receiver"] = (shapeless("radio_receiver", "compact_radio_receiver"), f"{MOD}:radio_receiver")
recipes["radio_receiver_from_compact"] = (shapeless("compact_radio_receiver", "radio_receiver"), f"{MOD}:compact_radio_receiver")
recipes["compact_radio_transmitter"] = (shapeless("radio_transmitter", "compact_radio_transmitter"), f"{MOD}:radio_transmitter")
recipes["radio_transmitter_from_compact"] = (shapeless("compact_radio_transmitter", "radio_transmitter"), f"{MOD}:compact_radio_transmitter")
recipes["radio_motion_sensor"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:compact_radio_transmitter"}, {"item": "minecraft:observer"}],
    "result": {"count": 1, "id": f"{MOD}:radio_motion_sensor"},
}, f"{MOD}:compact_radio_transmitter")

recipes["remote_control"] = (shaped([" T ", "IBI", "IEI"], {
    "T": "minecraft:redstone_torch", "I": "minecraft:iron_ingot",
    "B": "minecraft:stone_button", "E": "minecraft:ender_pearl"}, "remote_control", category="equipment"),
    "minecraft:ender_pearl")

recipes["multi_remote"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "equipment",
    "ingredients": [{"item": f"{MOD}:remote_control"}] + [{"item": "minecraft:stone_button"}] * 3,
    "result": {"count": 1, "id": f"{MOD}:multi_remote"},
}, f"{MOD}:remote_control")

recipes["radio_repeater"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": f"{MOD}:radio_transmitter"}],
    "result": {"count": 1, "id": f"{MOD}:radio_repeater"},
}, f"{MOD}:radio_transmitter")

recipes["radio_toggle_receiver"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:lever"}],
    "result": {"count": 1, "id": f"{MOD}:radio_toggle_receiver"},
}, f"{MOD}:radio_receiver")
recipes["radio_receiver_from_toggle"] = (shapeless("radio_toggle_receiver", "radio_receiver"), f"{MOD}:radio_toggle_receiver")

recipes["radio_speaker"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:note_block"}],
    "result": {"count": 1, "id": f"{MOD}:radio_speaker"},
}, f"{MOD}:radio_receiver")
recipes["radio_receiver_from_speaker"] = (shapeless("radio_speaker", "radio_receiver"), f"{MOD}:radio_speaker")
recipes["radio_status_board"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:redstone_lamp"}, {"item": "minecraft:glass_pane"}],
    "result": {"count": 1, "id": f"{MOD}:radio_status_board"},
}, f"{MOD}:radio_receiver")
recipes["radio_counter_receiver"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:hopper"}],
    "result": {"count": 1, "id": f"{MOD}:radio_counter_receiver"},
}, f"{MOD}:radio_receiver")
recipes["radio_receiver_from_counter"] = (shapeless("radio_counter_receiver", "radio_receiver"), f"{MOD}:radio_counter_receiver")
recipes["radio_timer_transmitter"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": "minecraft:clock"}],
    "result": {"count": 1, "id": f"{MOD}:radio_timer_transmitter"},
}, f"{MOD}:radio_transmitter")
recipes["radio_transmitter_from_timer"] = (shapeless("radio_timer_transmitter", "radio_transmitter"), f"{MOD}:radio_timer_transmitter")
recipes["radio_weather_station"] = ({  # v0.53.0
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": "minecraft:daylight_detector"}, {"item": "minecraft:lightning_rod"}],
    "result": {"count": 1, "id": f"{MOD}:radio_weather_station"},
}, f"{MOD}:radio_transmitter")
# With Reiko's Weather: its own Weather Station works too (only loaded when it's installed).
REIKOS = [{"type": "neoforge:mod_loaded", "modid": "reikos_weather"}]
recipes["radio_weather_station_from_reikos_weather"] = ({
    "neoforge:conditions": REIKOS,
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": "reikos_weather:weather_station"}],
    "result": {"count": 1, "id": f"{MOD}:radio_weather_station"},
}, f"{MOD}:radio_transmitter")
# v0.54.0: bundled cable and the bundled radio pair, and guy wires.
recipes["bundled_cable"] = ({
    "type": "minecraft:crafting_shaped", "category": "redstone",
    "pattern": ["SSS", "RRR", "SSS"],
    "key": {"S": {"item": "minecraft:string"}, "R": {"item": "minecraft:redstone"}},
    "result": {"count": 6, "id": f"{MOD}:bundled_cable"},
}, "minecraft:redstone")
recipes["cable_tap"] = ({
    "type": "minecraft:crafting_shapeless", "category": "redstone",
    "ingredients": [{"item": f"{MOD}:bundled_cable"}, {"item": "minecraft:redstone_torch"}, {"item": "minecraft:iron_nugget"}],
    "result": {"count": 1, "id": f"{MOD}:cable_tap"},
}, f"{MOD}:bundled_cable")
recipes["bundled_transmitter"] = ({
    "type": "minecraft:crafting_shapeless", "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": f"{MOD}:bundled_cable"}],
    "result": {"count": 1, "id": f"{MOD}:bundled_transmitter"},
}, f"{MOD}:bundled_cable")
recipes["bundled_receiver"] = ({
    "type": "minecraft:crafting_shapeless", "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": f"{MOD}:bundled_cable"}],
    "result": {"count": 1, "id": f"{MOD}:bundled_receiver"},
}, f"{MOD}:bundled_cable")
recipes["radio_transmitter_from_bundled"] = (shapeless("bundled_transmitter", "radio_transmitter"), f"{MOD}:bundled_transmitter")
recipes["radio_receiver_from_bundled"] = (shapeless("bundled_receiver", "radio_receiver"), f"{MOD}:bundled_receiver")
recipes["guy_anchor"] = ({
    "type": "minecraft:crafting_shaped", "category": "building",
    "pattern": [" C ", " I ", "SSS"],
    "key": {"C": {"item": "minecraft:chain"}, "I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:stone"}},
    "result": {"count": 2, "id": f"{MOD}:guy_anchor"},
}, f"{MOD}:radio_mast")
recipes["tower_platform"] = ({
    "type": "minecraft:crafting_shaped", "category": "building",
    "pattern": ["BBB", "III"],
    "key": {"B": {"item": "minecraft:iron_bars"}, "I": {"item": "minecraft:iron_ingot"}},
    "result": {"count": 8, "id": f"{MOD}:tower_platform"},
}, f"{MOD}:radio_mast")
recipes["radio_transmitter_from_weather_station"] = (shapeless("radio_weather_station", "radio_transmitter"), f"{MOD}:radio_weather_station")
recipes["iron_antenna"] = ({
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": [" I ", "III", " R "],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "R": {"item": "minecraft:redstone"}},
    "result": {"count": 1, "id": f"{MOD}:iron_antenna"},
}, "minecraft:iron_ingot")
recipes["dish_antenna"] = ({  # v0.48.0
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": ["I I", " I ", " A "],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "A": {"item": f"{MOD}:iron_antenna"}},
    "result": {"count": 1, "id": f"{MOD}:dish_antenna"},
}, f"{MOD}:iron_antenna")
recipes["radio_battery"] = ({  # v0.49.0
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": ["ICI", "CBC", "ICI"],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "C": {"item": "minecraft:copper_ingot"}, "B": {"item": "minecraft:redstone_block"}},
    "result": {"count": 1, "id": f"{MOD}:radio_battery"},
}, "minecraft:redstone_block")
recipes["ender_antenna"] = ({  # v0.50.0
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:iron_antenna"}, {"item": "minecraft:ender_eye"}, {"item": "minecraft:obsidian"}],
    "result": {"count": 1, "id": f"{MOD}:ender_antenna"},
}, f"{MOD}:iron_antenna")
recipes["solar_panel"] = ({  # v0.50.0
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": ["GGG", "CDC"],
    "key": {"G": {"item": "minecraft:glass_pane"}, "C": {"item": "minecraft:copper_ingot"}, "D": {"item": "minecraft:daylight_detector"}},
    "result": {"count": 1, "id": f"{MOD}:solar_panel"},
}, "minecraft:daylight_detector")
recipes["dish_rotator"] = ({  # v0.52.0
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_mast"}, {"item": "minecraft:comparator"}, {"item": "minecraft:copper_ingot"}],
    "result": {"count": 1, "id": f"{MOD}:dish_rotator"},
}, f"{MOD}:radio_mast")
recipes["radio_telegraph"] = ({  # v0.51.0
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:writable_book"}],
    "result": {"count": 1, "id": f"{MOD}:radio_telegraph"},
}, f"{MOD}:radio_transmitter")
recipes["coverage_map"] = ({  # v0.49.0
    "type": "minecraft:crafting_shapeless",
    "category": "misc",
    "ingredients": [{"item": "minecraft:map"}, {"item": f"{MOD}:compact_radio_receiver"}],
    "result": {"count": 1, "id": f"{MOD}:coverage_map"},
}, f"{MOD}:compact_radio_receiver")
recipes["gold_antenna"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:iron_antenna"}, {"item": "minecraft:gold_ingot"}, {"item": "minecraft:gold_ingot"},
                    {"item": "minecraft:redstone"}],
    "result": {"count": 1, "id": f"{MOD}:gold_antenna"},
}, f"{MOD}:iron_antenna")
recipes["diamond_antenna"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:gold_antenna"}, {"item": "minecraft:diamond"}, {"item": "minecraft:diamond"}],
    "result": {"count": 1, "id": f"{MOD}:diamond_antenna"},
}, f"{MOD}:gold_antenna")
recipes["netherite_antenna"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:diamond_antenna"}, {"item": "minecraft:netherite_ingot"}],
    "result": {"count": 1, "id": f"{MOD}:netherite_antenna"},
}, f"{MOD}:diamond_antenna")
recipes["radio_lamp"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:redstone_lamp"}],
    "result": {"count": 1, "id": f"{MOD}:radio_lamp"},
}, f"{MOD}:radio_receiver")
recipes["radio_signal_recorder"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:book"}],
    "result": {"count": 1, "id": f"{MOD}:radio_signal_recorder"},
}, f"{MOD}:radio_transmitter")
recipes["radio_jammer"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_transmitter"}, {"item": "minecraft:note_block"}, {"item": "minecraft:redstone"}],
    "result": {"count": 1, "id": f"{MOD}:radio_jammer"},
}, f"{MOD}:radio_transmitter")
recipes["aviation_beacon"] = ({
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": [" G ", "RLR", " I "],
    "key": {"G": {"item": "minecraft:red_stained_glass"}, "R": {"item": "minecraft:redstone"},
            "L": {"item": "minecraft:redstone_lamp"}, "I": {"item": "minecraft:iron_ingot"}},
    "result": {"count": 2, "id": f"{MOD}:aviation_beacon"},
}, "minecraft:redstone_lamp")
recipes["radio_delay_receiver"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:repeater"}],
    "result": {"count": 1, "id": f"{MOD}:radio_delay_receiver"},
}, f"{MOD}:radio_receiver")
recipes["radio_receiver_from_delay"] = (shapeless("radio_delay_receiver", "radio_receiver"), f"{MOD}:radio_delay_receiver")
recipes["radio_logic_receiver"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:quartz"}],
    "result": {"count": 1, "id": f"{MOD}:radio_logic_receiver"},
}, f"{MOD}:radio_receiver")
recipes["signal_meter"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "tools",
    "ingredients": [{"item": f"{MOD}:compact_radio_receiver"}, {"item": "minecraft:comparator"}, {"item": "minecraft:iron_ingot"}],
    "result": {"count": 1, "id": f"{MOD}:signal_meter"},
}, f"{MOD}:compact_radio_receiver")
recipes["radio_inverted_receiver"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:redstone_torch"}],
    "result": {"count": 1, "id": f"{MOD}:radio_inverted_receiver"},
}, f"{MOD}:radio_receiver")
recipes["radio_receiver_from_inverted"] = (shapeless("radio_inverted_receiver", "radio_receiver"), f"{MOD}:radio_inverted_receiver")

recipes["radio_converter"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:radio_repeater"}, {"item": "minecraft:comparator"}],
    "result": {"count": 1, "id": f"{MOD}:radio_converter"},
}, f"{MOD}:radio_repeater")

recipes["signal_scanner"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "equipment",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:compass"},
                    {"item": "minecraft:iron_ingot"}],
    "result": {"count": 1, "id": f"{MOD}:signal_scanner"},
}, f"{MOD}:radio_receiver")

recipes["code_keypad"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "redstone",
    "ingredients": [{"item": f"{MOD}:remote_control"}, {"item": "minecraft:iron_ingot"}, {"item": "minecraft:stone_button"}],
    "result": {"count": 1, "id": f"{MOD}:code_keypad"},
}, f"{MOD}:remote_control")

recipes["pager"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "equipment",
    "ingredients": [{"item": f"{MOD}:compact_radio_receiver"}, {"item": "minecraft:note_block"},
                    {"item": "minecraft:iron_ingot"}],
    "result": {"count": 1, "id": f"{MOD}:pager"},
}, f"{MOD}:compact_radio_receiver")

for name, (recipe, trigger) in recipes.items():
    write(DATA / "recipe" / f"{name}.json", recipe)
    # Unlocks the recipe in the recipe book once the player picks up the trigger item.
    write(DATA / "advancement" / "recipes" / "redstone" / f"{name}.json", {
        **({"neoforge:conditions": recipe["neoforge:conditions"]} if "neoforge:conditions" in recipe else {}),
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"trigger": "minecraft:inventory_changed",
                         "conditions": {"items": [{"items": trigger}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                               "conditions": {"recipe": f"{MOD}:{name}"}},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": [f"{MOD}:{name}"]},
    })

# ---------------------------------------------------------------- loot tables

for name in ("radio_transmitter", "radio_receiver", "radio_toggle_receiver", "radio_inverted_receiver", "radio_logic_receiver",
             "radio_delay_receiver", "radio_counter_receiver", "radio_timer_transmitter", "radio_speaker", "radio_status_board",
             "radio_signal_recorder", "radio_jammer", "aviation_beacon", "radio_lamp",
             "radio_repeater", "radio_converter", "compact_radio_receiver", "compact_radio_transmitter", "radio_motion_sensor", "code_keypad", "radio_mast", "wall_redstone",
             "radio_battery", "solar_panel", "radio_telegraph", "dish_rotator", "radio_weather_station",
             "bundled_cable", "cable_tap", "bundled_transmitter", "bundled_receiver", "guy_anchor", "tower_platform"):
    entry = {"type": "minecraft:item", "name": f"{MOD}:{name}"}
    if name == "radio_battery":
        # v0.49.0: it keeps its charge.
        entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity", "include": [f"{MOD}:battery_charge"]}]
    elif name == "aviation_beacon":
        # Its settings are in its block state: keep them on the item.
        entry["functions"] = [{"function": "minecraft:copy_state", "block": f"{MOD}:aviation_beacon",
                               "properties": ["when", "pattern", "color"]}]
    elif name not in ("radio_mast", "wall_redstone", "solar_panel", "dish_rotator", "bundled_cable", "cable_tap", "guy_anchor", "tower_platform"):
        # Copy the block's frequency (and a converter's output frequency) onto the dropped item so it keeps its tuning.
        entry["functions"] = [{
            "function": "minecraft:copy_components",
            "source": "block_entity",
            "include": [f"{MOD}:frequency"] + ([f"{MOD}:output_frequency", f"{MOD}:converter_gate"] if name == "radio_converter" else [])
                       + ([f"{MOD}:delay_ticks", f"{MOD}:hold_ticks"] if name == "radio_delay_receiver" else [])
                       + ([f"{MOD}:counter_target", f"{MOD}:counter_latch", f"{MOD}:counter_forget", f"{MOD}:reset_frequency"]
                          if name == "radio_counter_receiver" else [])
                       + ([f"{MOD}:timer_mode", f"{MOD}:timer_interval", f"{MOD}:timer_pulse", f"{MOD}:timer_hours"]
                          if name == "radio_timer_transmitter" else [])
                       + ([f"{MOD}:station_mode", f"{MOD}:station_announce"] if name == "radio_weather_station" else [])
                       + ([f"{MOD}:speaker"] if name == "radio_speaker" else [])
                       + ([f"{MOD}:recorder_tape"] if name == "radio_signal_recorder" else [])
                       + ([f"{MOD}:board_frequencies"] if name == "radio_status_board" else [])
                       + ([f"{MOD}:second_frequency", f"{MOD}:logic_mode"] if name == "radio_logic_receiver" else []),
        }]
    if name in ("radio_transmitter", "radio_timer_transmitter", "radio_signal_recorder", "radio_jammer", "radio_repeater", "radio_converter",
                "radio_weather_station", "bundled_transmitter"):
        # v0.45.0: its antenna (only if it has one, so plain ones still stack with older items).
        entry["functions"].append({"function": "minecraft:copy_state", "block": f"{MOD}:{name}", "properties": ["antenna"],
                                   "conditions": [{"condition": "minecraft:inverted", "term": {
                                       "condition": "minecraft:block_state_property", "block": f"{MOD}:{name}",
                                       "properties": {"antenna": "none"}}}]})
    if name == "radio_lamp":
        entry["functions"].append({"function": "minecraft:copy_state", "block": f"{MOD}:radio_lamp", "properties": ["color"]})
    write(DATA / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0,
            "rolls": 1.0,
            "entries": [entry],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"{MOD}:blocks/{name}",
    })

# ---------------------------------------------------------------- tags

write(RES / "data" / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json", {
    "values": [f"{MOD}:radio_transmitter", f"{MOD}:radio_receiver", f"{MOD}:radio_toggle_receiver", f"{MOD}:radio_inverted_receiver", f"{MOD}:radio_logic_receiver",
               f"{MOD}:radio_delay_receiver", f"{MOD}:radio_counter_receiver", f"{MOD}:radio_timer_transmitter",
               f"{MOD}:radio_speaker", f"{MOD}:radio_status_board", f"{MOD}:radio_signal_recorder", f"{MOD}:radio_jammer", f"{MOD}:aviation_beacon", f"{MOD}:radio_lamp",
               f"{MOD}:radio_repeater", f"{MOD}:radio_converter",
               f"{MOD}:compact_radio_receiver", f"{MOD}:compact_radio_transmitter", f"{MOD}:radio_motion_sensor", f"{MOD}:code_keypad",
               f"{MOD}:radio_mast", f"{MOD}:radio_battery", f"{MOD}:solar_panel", f"{MOD}:radio_telegraph", f"{MOD}:dish_rotator",
               f"{MOD}:radio_weather_station", f"{MOD}:cable_tap", f"{MOD}:bundled_transmitter", f"{MOD}:bundled_receiver",
               f"{MOD}:guy_anchor", f"{MOD}:tower_platform"]
})

# ---------------------------------------------------------------- v0.55.0: old radio towers in the world

write(DATA / "worldgen" / "structure" / "radio_tower.json", {
    "type": f"{MOD}:radio_tower",
    "biomes": f"#{MOD}:has_structure/radio_tower",
    "step": "surface_structures",
    "spawn_overrides": {},
    "terrain_adaptation": "beard_thin",
})
write(DATA / "worldgen" / "structure_set" / "radio_towers.json", {
    "structures": [{"structure": f"{MOD}:radio_tower", "weight": 1}],
    "placement": {"type": "minecraft:random_spread", "spacing": 24, "separation": 8, "salt": 1408155011},
})
write(DATA / "tags" / "worldgen" / "biome" / "has_structure" / "radio_tower.json", {"values": [
    "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:cherry_grove", "minecraft:savanna",
    "minecraft:savanna_plateau", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_savanna",
    "minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_plains",
    "minecraft:snowy_taiga", "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
    "minecraft:sparse_jungle", "minecraft:grove"]})

def loot_item(name, weight, low=1, high=1, mod=True):
    entry = {"type": "minecraft:item", "name": f"{MOD}:{name}" if mod else f"minecraft:{name}", "weight": weight}
    if high > 1:
        entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]
    return entry

write(DATA / "loot_table" / "chests" / "radio_tower.json", {
    "type": "minecraft:chest",
    "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "bonus_rolls": 0.0, "entries": [
            loot_item("radio_mast", 20, 2, 6), loot_item("redstone", 15, 2, 8, mod=False), loot_item("iron_ingot", 10, 1, 4, mod=False),
            loot_item("copper_ingot", 10, 2, 6, mod=False), loot_item("guy_anchor", 8, 1, 3), loot_item("tower_platform", 6, 2, 6),
            loot_item("frequency_tuner", 5), loot_item("radio_receiver", 5), loot_item("compact_radio_receiver", 4),
            loot_item("iron_antenna", 5), loot_item("radio_battery", 4), loot_item("solar_panel", 4), loot_item("gold_antenna", 2)]},
        {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
            {"type": "minecraft:empty", "weight": 9}, loot_item("diamond_antenna", 1)]},
    ],
    "random_sequence": f"{MOD}:chests/radio_tower",
})

# ---------------------------------------------------------------- v0.57.0: JEI / EMI information pages

ANTENNA_INFO = ("Right-click the top of a full-size tower (a transmitter, repeater, converter or jammer) with it to fit it: "
                "reach x{}. Fitting another gives this one back; sneak and right-click with an empty hand to take it off.")
INFO = {
    "radio_transmitter": "Sends the redstone signal it gets (0-15) on its frequency. Stand it on Radio Masts: it reaches 32 blocks, plus 8 for every block its top is above sea level. Give it redstone beside the top or beside any mast under it. With power cost on (the default) it also needs a charged Radio Battery beside the top or a mast. Tune it with a Frequency Tuner.",
    "radio_receiver": "Gives out the strongest signal it hears on its frequency, from any transmitter whose reach covers it (higher up, it hears farther). Tune it with a Frequency Tuner.",
    "radio_toggle_receiver": "Turns on, or off again, each time its frequency turns on: a wireless T flip-flop.",
    "radio_inverted_receiver": "Gives out 15 while its frequency is silent, and 0 while it hears it.",
    "radio_logic_receiver": "Listens on two frequencies and puts them together: AND (both on), OR (either) or XOR (just one). Right-click it to set them.",
    "radio_delay_receiver": "A receiver that holds every change back, by up to 60 seconds, and can keep its output on for a while after.",
    "radio_speaker": "A receiver that plays a sound (a bell, a chime, a doorbell and more) each time its frequency turns on.",
    "radio_status_board": "A panel for walls that shows up to four frequencies: their names, strengths and the last telegraph message.",
    "radio_counter_receiver": "Counts the times its frequency turns on; at the number you set it gives a pulse, or stays on until its reset frequency turns on.",
    "radio_timer_transmitter": "Sends 15 on a timer: a short pulse every so often, or every day between two hours. Redstone pauses it.",
    "guy_anchor": "Put three on the ground 2 to 12 blocks from the foot of a tall tower (4 masts or more) to brace it with guy wires: then storms (with Reiko's Weather) can't blow it down.",
    "tower_platform": "Right-click a mast with Tower Platforms to put 8 round it, with rails on the outside. You can stand on them; sneak by the mast to drop through.",
    "bundled_cable": "Carries 16 colored channels at once, with no loss. Cable Taps put signals on and take them off; the Bundled Radio Transmitter and Receiver send all 16 by radio.",
    "cable_tap": "Puts a redstone signal onto its color's channel of the Bundled Cable it faces, or gives that channel out. Right-click with an empty hand to switch between in and out, with a dye to change its color.",
    "bundled_transmitter": "A tower top that sends the 16 channels of the Bundled Cable touching it: color c on its frequency + c.",
    "bundled_receiver": "Listens on its frequency and the 15 after it, and puts what it hears onto the Bundled Cable touching it, one channel each.",
    "radio_weather_station": "A transmitter that sends a reading where it stands instead of redstone: the weather, the time of day or the moon (and more with Reiko's Weather). It can also tell pagers when the weather changes.",
    "radio_signal_recorder": "Records up to 30 seconds of a frequency, then plays it back on its own frequency: once for each redstone pulse, or over and over while powered.",
    "radio_jammer": "Stand it on masts like a transmitter: anything in its reach hears its frequency weaker by the redstone strength it gets (15 silences it).",
    "aviation_beacon": "A red or white warning light for the top or side of a tower: on at night, always, or while its tower sends; blinking, flashing or steady. Right-click it to set it.",
    "solar_panel": "Put it on top of a Radio Battery to charge the battery in daylight.",
    "dish_rotator": "A mast that turns the Dish Antenna on top of its tower by redstone: each strength turns it a sixteenth of the way round.",
    "radio_telegraph": "Type a short message (up to 64 characters) onto a frequency: pagers, other telegraphs and Status Boards in reach get it. A villager with no job takes it as a workstation and becomes a Radio Operator.",
    "ender_antenna": "Fit it on a transmitter's tower: the tower also sends into another dimension, from the matching place (the Nether from the Overworld, or the Overworld from the others). It doubles the power the tower uses.",
    "radio_battery": "Powers a tower when power cost is on: put it beside the tower's top or one of its masts. Charge it with redstone (dust or blocks), with Forge Energy, or with a Solar Panel on top.",
    "coverage_map": "A map tuned to a frequency: it shows where that frequency can be heard, from strong to weak, with its towers marked.",
    "radio_lamp": "A receiver whose light shows how strong a signal it hears. Comes in all 16 dye colors.",
    "iron_antenna": ANTENNA_INFO.format("1.5"),
    "gold_antenna": ANTENNA_INFO.format("2"),
    "diamond_antenna": ANTENNA_INFO.format("3"),
    "netherite_antenna": ANTENNA_INFO.format("4"),
    "dish_antenna": "Fit it on a tower top and it faces the way you look: reach x3 in a narrow beam that way (15 degrees either side), and plain reach everywhere else.",
    "radio_repeater": "Stand it on masts: it hears its frequency where it stands and sends it again with its own reach, to carry a signal farther.",
    "radio_converter": "A repeater that listens on one frequency and sends on another. Its gate can make it a wireless comparator or NOT gate. Right-click it to set it.",
    "compact_radio_receiver": "A receiver the size of a button, for walls, floors and ceilings. It strongly powers the block it's on.",
    "compact_radio_transmitter": "A transmitter the size of a button: it sends the signal around it over a fixed 16 blocks, at any height, with no masts or battery.",
    "radio_motion_sensor": "An eye the size of a button: it sends 15 over 16 blocks while it sees a player in range.",
    "code_keypad": "The right code pulses its frequency and powers the block it's on; three wrong codes lock it for 30 seconds and tell its owner. Its owner sets the code, and can hand out guest codes.",
    "radio_mast": "A lattice section for towers: stack masts under a transmitter (or repeater or jammer) to raise it, and redstone beside any of them reaches the top. Right-click a tower with a mast to add one under the top. Walk into a mast to climb it.",
    "wall_redstone": "Redstone dust for the sides of blocks and for ceilings: it runs up, down and sideways, and powers the block it's on.",
    "frequency_tuner": "Right-click the air to choose a frequency (1 to 999,999,999), name it or make it private. Right-click a radio block to tune it; sneak and right-click to copy its frequency. Sneak and scroll to step the frequency.",
    "remote_control": "A transmitter in your hand: right-click to send a pulse or a toggle on its frequency, as far as a transmitter at your height would reach (towers in reach pass it on). Sneak and right-click the air to set it.",
    "multi_remote": "A remote with 4 buttons, each with its own name, color, frequency and mode. Sneak and scroll to pick a button; sneak and right-click the air to set them.",
    "pager": "Keep it anywhere in your inventory: it beeps and tells you when its frequency turns on where you are, and shows telegraph messages. Sneak and right-click the air to set its frequency.",
    "signal_meter": "Hold it to see how strong its frequency is where you stand, how many towers reach you, and how far it is to the edge.",
    "signal_scanner": "Lists everything a receiver where you stand would hear: each frequency, its strength, and the strongest sender with its distance and direction. Click a tower in the list for an arrow to it.",
}
_lang_path = ASSETS / "lang" / "en_us.json"
_lang = json.loads(_lang_path.read_text(encoding="utf-8"))
for item, text in INFO.items():
    _lang[f"info.{MOD}.{item}"] = text
write(_lang_path, _lang)

# ---------------------------------------------------------------- v0.56.0: advancements

def advancement(name, parent, icon, criteria, frame="task", hidden=False, xp=0, background=None, toast=True, conditions=None):
    display = {
        "icon": {"id": icon if ":" in icon else f"{MOD}:{icon}"},
        "title": {"translate": f"advancements.{MOD}.{name}.title"},
        "description": {"translate": f"advancements.{MOD}.{name}.description"},
        "frame": frame,
        "show_toast": toast,
        "announce_to_chat": toast,
        "hidden": hidden,
    }
    if background:
        display["background"] = background
    obj = {}
    if conditions:
        obj["neoforge:conditions"] = conditions
    if parent:
        obj["parent"] = f"{MOD}:{parent}"
    obj["display"] = display
    obj["criteria"] = criteria
    obj["requirements"] = [list(criteria)]
    if xp:
        obj["rewards"] = {"experience": xp}
    write(DATA / "advancement" / f"{name}.json", obj)


def tower_event(event, **value):
    conditions = {"event": event}
    if value:
        conditions["value"] = value
    return {"trigger": f"{MOD}:tower", "conditions": conditions}


advancement("root", None, "radio_transmitter", {
    "has_radio_part": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [
        f"{MOD}:radio_mast", f"{MOD}:radio_transmitter", f"{MOD}:radio_receiver", f"{MOD}:frequency_tuner",
        f"{MOD}:compact_radio_transmitter", f"{MOD}:compact_radio_receiver"]}]}}},
    background="minecraft:textures/block/stone_bricks.png", toast=False)
advancement("first_broadcast", "root", "radio_mast", {"on_air": tower_event("on_air")})
advancement("over_the_horizon", "first_broadcast", "netherite_antenna", {"far_reach": tower_event("on_air", min=500)},
            frame="challenge", xp=50)
advancement("head_for_heights", "first_broadcast", "tower_platform", {"on_platform": {
    "trigger": "minecraft:location",
    "conditions": {"player": {"location": {"block": {"blocks": f"{MOD}:tower_platform"}}}}}})
advancement("weathered_the_storm", "head_for_heights", "guy_anchor", {"stood_through_a_storm": tower_event("storm_survived")},
            frame="challenge", xp=50, conditions=[{"type": "neoforge:mod_loaded", "modid": "reikos_weather"}])
advancement("lost_signal", "root", "minecraft:compass", {"found_a_tower": {
    "trigger": "minecraft:location",
    "conditions": {"player": {"location": {"structures": f"{MOD}:radio_tower"}}}}})
advancement("finders_keepers", "lost_signal", "radio_battery", {"brought_back": tower_event("found_tower_revived")}, frame="goal")
advancement("call_sign", "root", "radio_telegraph", {"traded": {
    "trigger": "minecraft:villager_trade",
    "conditions": {"villager": {"nbt": f'{{VillagerData:{{profession:"{MOD}:radio_operator"}}}}'}}}})

# ---------------------------------------------------------------- v0.56.0: Radio Operator villagers

# Any villager with no job may take a Radio Telegraph as their workstation.
write(RES / "data" / "minecraft" / "tags" / "point_of_interest_type" / "acquirable_job_site.json", {
    "replace": False, "values": [f"{MOD}:radio_operator"]})

# ---------------------------------------------------------------- game test structure (NBT)

def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag(type_id, name, payload):
    return bytes([type_id]) + nbt_string(name) + payload


def int_list(values):
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(items):
    if not items:
        return bytes([0]) + struct.pack(">i", 0)
    return bytes([10]) + struct.pack(">i", len(items)) + b"".join(items)


# An empty box: the game test framework clears it to air. Tests place any support blocks they need.
SIZE = [130, 14, 3]
root = (
    tag(3, "DataVersion", struct.pack(">i", 3955))  # 1.21.1
    + tag(9, "size", int_list(SIZE))
    + tag(9, "palette", compound_list([tag(8, "Name", nbt_string("minecraft:air")) + b"\x00"]))
    + tag(9, "blocks", compound_list([]))
    + tag(9, "entities", compound_list([]))
)
structure = DATA / "structure" / "test_platform.nbt"
structure.parent.mkdir(parents=True, exist_ok=True)
structure.write_bytes(gzip.compress(bytes([10]) + nbt_string("") + root + b"\x00", mtime=0))

# v0.55.0: a 16-block cube for building a whole old radio tower in a test.
site = (
    tag(3, "DataVersion", struct.pack(">i", 3955))
    + tag(9, "size", int_list([16, 16, 16]))
    + tag(9, "palette", compound_list([tag(8, "Name", nbt_string("minecraft:air")) + b"\x00"]))
    + tag(9, "blocks", compound_list([]))
    + tag(9, "entities", compound_list([]))
)
(DATA / "structure" / "tower_site.nbt").write_bytes(gzip.compress(bytes([10]) + nbt_string("") + site + b"\x00", mtime=0))

# ---------------------------------------------------------------- v0.57.0: village radio shacks

class B:
    """An NBT byte (a bool in the game)."""
    def __init__(self, v):
        self.v = v


def nbt_payload(value):
    """(type id, payload bytes) for a Python value: dict, str, int, B, list."""
    if isinstance(value, B):
        return 1, struct.pack(">b", int(value.v))
    if isinstance(value, bool):
        return 1, struct.pack(">b", int(value))
    if isinstance(value, int):
        return 3, struct.pack(">i", value)
    if isinstance(value, str):
        return 8, nbt_string(value)
    if isinstance(value, dict):
        out = b""
        for k, v in value.items():
            t, pl = nbt_payload(v)
            out += bytes([t]) + nbt_string(k) + pl
        return 10, out + b"\x00"
    if isinstance(value, list):
        if not value:
            return 9, bytes([0]) + struct.pack(">i", 0)
        items = [nbt_payload(v) for v in value]
        return 9, bytes([items[0][0]]) + struct.pack(">i", len(items)) + b"".join(pl for _, pl in items)
    raise TypeError(value)


def write_structure(path, size, blocks, entities=()):
    """blocks: (x, y, z, block name, {property: value}, block entity nbt or None)."""
    palette, index = [], {}
    rows = []
    for x, y, z, name, props, nbt in blocks:
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            state = {"Name": name}
            if props:
                state["Properties"] = dict(props)
            palette.append(state)
        row = {"pos": [x, y, z], "state": index[key]}
        if nbt is not None:
            row["nbt"] = nbt
        rows.append(row)
    root = {"DataVersion": 3955, "size": list(size), "palette": palette, "blocks": rows, "entities": list(entities)}
    _, payload = nbt_payload(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(bytes([10]) + nbt_string("") + payload, mtime=0))


def jigsaw(name, target, pool, final_state, joint):
    return {"id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool, "final_state": final_state,
            "joint": joint, "selection_priority": 0, "placement_priority": 0}


def sign_text(lines):
    return {"messages": [json.dumps(line) for line in lines], "color": "black", "has_glowing_text": B(False)}


SHACK_STYLES = {
    # foundation, floor, walls, corners, roof (slab or block), door, bed, wall sign
    "plains": ("minecraft:cobblestone", "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_log",
               "minecraft:oak_slab", "minecraft:oak_door", "minecraft:white_bed", "minecraft:oak_wall_sign"),
    "desert": ("minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:smooth_sandstone", "minecraft:cut_sandstone",
               "minecraft:smooth_sandstone_slab", "minecraft:jungle_door", "minecraft:cyan_bed", "minecraft:jungle_wall_sign"),
    "savanna": ("minecraft:cobblestone", "minecraft:acacia_planks", "minecraft:acacia_planks", "minecraft:acacia_log",
                "minecraft:acacia_slab", "minecraft:acacia_door", "minecraft:orange_bed", "minecraft:acacia_wall_sign"),
    "snowy": ("minecraft:stone_bricks", "minecraft:spruce_planks", "minecraft:spruce_planks", "minecraft:stripped_spruce_log",
              "minecraft:spruce_planks", "minecraft:spruce_door", "minecraft:light_blue_bed", "minecraft:spruce_wall_sign"),
    "taiga": ("minecraft:cobblestone", "minecraft:spruce_planks", "minecraft:spruce_planks", "minecraft:spruce_log",
              "minecraft:spruce_slab", "minecraft:spruce_door", "minecraft:brown_bed", "minecraft:spruce_wall_sign"),
}


def radio_shack(kind):
    """A 5x5 shack (x 1..5, z 1..5) with its door on the west (street) side at z 3, and a 4-mast tower in
    the yard behind it (x 6..7). y 0 is the ground; the street's jigsaw meets ours at (0, 1, 3)."""
    foundation, floor, wall, corner, roof, door, bed, wall_sign = SHACK_STYLES[kind]
    blocks = []

    def put(x, y, z, name, props=None, nbt=None):
        blocks.append((x, y, z, name, props or {}, nbt))

    for x in range(1, 6):
        for z in range(1, 6):
            edge = x in (1, 5) or z in (1, 5)
            if (x, z) == (3, 3):
                # a villager comes to live here (the jigsaw turns into floor)
                put(x, 0, z, "minecraft:jigsaw", {"orientation": "up_north"},
                    jigsaw("minecraft:bottom", "minecraft:bottom", f"minecraft:village/{kind}/villagers", floor, "rollable"))
            else:
                put(x, 0, z, foundation if edge else floor)
            if not edge:
                continue
            for y in range(1, 4):
                if x in (1, 5) and z in (1, 5):
                    put(x, y, z, corner, {"axis": "y"} if corner.endswith("_log") else {})
                elif (x, z) == (1, 3) and y < 3:
                    continue  # the door
                elif (x, z) in ((3, 1), (3, 5)) and y == 2:
                    put(x, y, z, "minecraft:glass_pane", {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
                else:
                    put(x, y, z, wall)
    for x in range(1, 6):
        for z in range(0, 7):
            if roof.endswith("_slab"):
                put(x, 4, z, roof, {"type": "bottom", "waterlogged": "false"})
            else:
                put(x, 4, z, roof)
                if z in range(1, 6):
                    put(x, 5, z, "minecraft:snow", {"layers": "1"})
    put(1, 1, 3, door, {"facing": "east", "half": "lower", "hinge": "right", "open": "false", "powered": "false"})
    put(1, 2, 3, door, {"facing": "east", "half": "upper", "hinge": "right", "open": "false", "powered": "false"})
    # The street meets the shack here.
    put(0, 1, 3, "minecraft:jigsaw", {"orientation": "west_up"},
        jigsaw("minecraft:building_entrance", "minecraft:building_entrance", f"minecraft:village/{kind}/streets", "minecraft:structure_void", "aligned"))
    # Inside: the operator's telegraph, a bed and a lantern.
    put(4, 1, 2, f"{MOD}:radio_telegraph", {"facing": "west"})
    put(3, 1, 4, bed, {"facing": "east", "part": "foot", "occupied": "false"})
    put(4, 1, 4, bed, {"facing": "east", "part": "head", "occupied": "false"})
    put(3, 3, 3, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    # The sign by the door; the transmitter writes its frequency on line 3.
    put(0, 2, 2, wall_sign, {"facing": "west", "waterlogged": "false"}, {
        "id": "minecraft:sign", "is_waxed": B(True),
        "front_text": sign_text([{"translate": f"sign.{MOD}.radio_tower.station"}, {"translate": f"sign.{MOD}.radio_tower.frequency"}, "", ""]),
        "back_text": sign_text(["", "", "", ""])})
    # The yard: a pad, 4 masts with the transmitter and a beacon on top, a battery with a solar panel,
    # and a redstone torch keeping it on the air.
    for x in (6, 7):
        for z in (2, 3, 4):
            put(x, 0, z, foundation)
    for y in range(1, 5):
        put(6, y, 3, f"{MOD}:radio_mast")
    put(6, 5, 3, f"{MOD}:radio_transmitter", {}, {
        "id": f"{MOD}:radio_transmitter", "frequency": 1, "found_tower": B(True), "village_station": B(True)})
    put(6, 6, 3, f"{MOD}:aviation_beacon", {"face": "floor", "facing": "north"})
    put(7, 1, 3, f"{MOD}:radio_battery", {"charge": "4"}, {"id": f"{MOD}:radio_battery", "charge": 80000})
    put(7, 2, 3, f"{MOD}:solar_panel")
    put(6, 1, 2, "minecraft:redstone_torch", {"lit": "true"})
    write_structure(DATA / "structure" / "village" / kind / "radio_shack.nbt", (8, 7, 7), blocks)


for _kind in SHACK_STYLES:
    radio_shack(_kind)

# v0.59.0: the CC: Tweaked radio upgrade (a Radio Pager in a turtle or pocket computer). Only read
# when CC: Tweaked is installed; its type is registered by compat/cct/RadioUpgrades.
for _kind in ("turtle_upgrade", "pocket_upgrade"):
    write(RES / "data" / MOD / "computercraft" / _kind / "radio.json", {"type": f"{MOD}:radio", "item": f"{MOD}:pager"})

print("data written to", RES)
