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
             "radio_repeater", "radio_converter", "radio_mast"):
    write(items / f"{name}.json", {"parent": f"{MOD}:block/{name}"})
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
write(states / "radio_transmitter.json", {"variants": {
    "powered=false": {"model": f"{MOD}:block/radio_transmitter"},
    "powered=true": {"model": f"{MOD}:block/radio_transmitter_on"},
}})
write(states / "radio_repeater.json", {"variants": {
    "lit=false": {"model": f"{MOD}:block/radio_repeater"},
    "lit=true": {"model": f"{MOD}:block/radio_repeater_on"},
}})
write(states / "radio_converter.json", {"variants": {
    "lit=false": {"model": f"{MOD}:block/radio_converter"},
    "lit=true": {"model": f"{MOD}:block/radio_converter_on"},
}})
write(states / "radio_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_receiver" + ("_on" if p > 0 else "")} for p in range(16)
}})
write(states / "radio_toggle_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_toggle_receiver" + ("_on" if p > 0 else "")} for p in range(16)
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
    f"tooltip.{MOD}.converter_hint": "Right-click it with an empty hand to set both frequencies. The tuner sets the one it listens on",
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
             "radio_repeater", "radio_converter", "compact_radio_receiver", "compact_radio_transmitter", "radio_motion_sensor", "code_keypad", "radio_mast", "wall_redstone"):
    entry = {"type": "minecraft:item", "name": f"{MOD}:{name}"}
    if name not in ("radio_mast", "wall_redstone"):
        # Copy the block's frequency (and a converter's output frequency) onto the dropped item so it keeps its tuning.
        entry["functions"] = [{
            "function": "minecraft:copy_components",
            "source": "block_entity",
            "include": [f"{MOD}:frequency"] + ([f"{MOD}:output_frequency"] if name == "radio_converter" else [])
                       + ([f"{MOD}:second_frequency", f"{MOD}:logic_mode"] if name == "radio_logic_receiver" else []),
        }]
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
