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
             "radio_signal_recorder", "radio_jammer", "radio_repeater", "radio_converter", "radio_mast"):
    write(items / f"{name}.json", {"parent": f"{MOD}:block/{name}"})
write(items / "aviation_beacon.json", {"parent": f"{MOD}:block/aviation_beacon_floor_red"})
write(items / "radio_lamp.json", {"parent": f"{MOD}:block/radio_lamp"})
for tier in ("iron", "gold", "diamond", "netherite"):
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
ANTENNAS = ("iron", "gold", "diamond", "netherite")
ANTENNA_TEXTURE = {"iron": "minecraft:block/iron_block", "gold": "minecraft:block/gold_block",
                   "diamond": "minecraft:block/diamond_block", "netherite": "minecraft:block/netherite_block"}


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


def tower_states(name, lit_property):
    parts = [{"when": {lit_property: "false"}, "apply": {"model": f"{MOD}:block/{name}"}},
             {"when": {lit_property: "true"}, "apply": {"model": f"{MOD}:block/{name}_on"}}]
    parts += [{"when": {"antenna": tier}, "apply": {"model": f"{MOD}:block/antenna_{tier}"}} for tier in ANTENNAS]
    write(states / f"{name}.json", {"multipart": parts})


tower_states("radio_transmitter", "powered")
tower_states("radio_repeater", "lit")
tower_states("radio_converter", "lit")
tower_states("radio_signal_recorder", "powered")
tower_states("radio_timer_transmitter", "powered")
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
write(states / "wall_redstone.json", {"multipart": wall_parts})
write(states / "radio_mast.json", {"variants": {"": {"model": f"{MOD}:block/radio_mast"}}})

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
recipes["iron_antenna"] = ({
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": [" I ", "III", " R "],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "R": {"item": "minecraft:redstone"}},
    "result": {"count": 1, "id": f"{MOD}:iron_antenna"},
}, "minecraft:iron_ingot")
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
             "radio_repeater", "radio_converter", "compact_radio_receiver", "compact_radio_transmitter", "radio_motion_sensor", "code_keypad", "radio_mast", "wall_redstone"):
    entry = {"type": "minecraft:item", "name": f"{MOD}:{name}"}
    if name == "aviation_beacon":
        # Its settings are in its block state: keep them on the item.
        entry["functions"] = [{"function": "minecraft:copy_state", "block": f"{MOD}:aviation_beacon",
                               "properties": ["when", "pattern", "color"]}]
    elif name not in ("radio_mast", "wall_redstone"):
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
                       + ([f"{MOD}:speaker"] if name == "radio_speaker" else [])
                       + ([f"{MOD}:recorder_tape"] if name == "radio_signal_recorder" else [])
                       + ([f"{MOD}:board_frequencies"] if name == "radio_status_board" else [])
                       + ([f"{MOD}:second_frequency", f"{MOD}:logic_mode"] if name == "radio_logic_receiver" else []),
        }]
    if name in ("radio_transmitter", "radio_timer_transmitter", "radio_signal_recorder", "radio_jammer", "radio_repeater", "radio_converter"):
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
               f"{MOD}:radio_mast"]
})

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

print("data written to", RES)
