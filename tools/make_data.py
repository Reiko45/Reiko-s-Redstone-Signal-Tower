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


def transmitter_model(lit):
    """Steel cabinet with a dipole antenna and a warning light that glows while broadcasting."""
    suffix = "_on" if lit else ""
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": tex(f"radio_transmitter_side{suffix}"),
            "side": tex(f"radio_transmitter_side{suffix}"),
            "top": tex(f"radio_transmitter_top{suffix}"),
            "bottom": tex("radio_bottom"),
            "rod": tex("radio_mast_post"),
            "tip": tex(f"antenna_tip{suffix}"),
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


def receiver_model(lit):
    suffix = "_on" if lit else ""
    return {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": tex(f"radio_receiver_side{suffix}"),
            "north": tex(f"radio_receiver_side{suffix}"),
            "south": tex(f"radio_receiver_side{suffix}"),
            "east": tex(f"radio_receiver_side{suffix}"),
            "west": tex(f"radio_receiver_side{suffix}"),
            "up": tex(f"radio_receiver_top{suffix}"),
            "down": tex("radio_bottom"),
        },
    }


for lit in (False, True):
    suffix = "_on" if lit else ""
    write(models / f"radio_transmitter{suffix}.json", transmitter_model(lit))
    write(models / f"radio_receiver{suffix}.json", receiver_model(lit))


def compact_receiver_model(lit):
    """Same shape and UV layout as a vanilla button (lying on the floor, front facing up)."""
    t = "#texture"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("radio_receiver_side"), "texture": tex("compact_radio_receiver" + ("_on" if lit else ""))},
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

# Inventory version: stood up on its edge so the front shows, like a wall-mounted one.
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
for name in ("radio_transmitter", "radio_receiver", "radio_mast"):
    write(items / f"{name}.json", {"parent": f"{MOD}:block/{name}"})
write(items / "compact_radio_receiver.json", {"parent": f"{MOD}:block/compact_radio_receiver_inventory"})
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
write(states / "radio_receiver.json", {"variants": {
    f"power={p}": {"model": f"{MOD}:block/radio_receiver" + ("_on" if p > 0 else "")} for p in range(16)
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
    f"screen.{MOD}.tuner.favorite": "Favorite %s: %s. Click to use, sneak-click to replace, right-click to clear",
    f"screen.{MOD}.tuner.favorite_empty": "Favorite %s is empty. Click to save the frequency above",
    f"tooltip.{MOD}.favorites": "Favorites: %s",
    f"tooltip.{MOD}.frequency_scroll": "Sneak + scroll to step the frequency; add Ctrl to jump between favorites",
    f"message.{MOD}.frequency_scrolled": "%s: frequency %s",
    f"message.{MOD}.frequency_scrolled_favorite": "%s: frequency %s (favorite %s)",
    f"message.{MOD}.no_favorites": "No favorites yet. Save some in its frequency screen",
    f"tooltip.{MOD}.scanner_use": "Lists every frequency you can hear where you stand",
    f"tooltip.{MOD}.scanner_open": "Right-click to open it",
    f"tooltip.{MOD}.scanner_tune": "Its Tune buttons set the tuner, remote or pager in your other hand",
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
    f"screen.{MOD}.scanner.remote_pulse": "Remote pulse",
    f"screen.{MOD}.scanner.remote_toggle": "Remote toggle",
    f"screen.{MOD}.scanner.plus_one": "%s, plus 1 transmitter",
    f"screen.{MOD}.scanner.plus_many": "%s, plus %s transmitters",
    f"screen.{MOD}.scanner.tune": "Tune",
    f"screen.{MOD}.scanner.tune_hint": "Hold a tuner, remote or pager in your other hand to tune it",
    f"screen.{MOD}.scanner.tune_tooltip": "Set %s to frequency %s",
    f"screen.{MOD}.scanner.tuned": "%s tuned to frequency %s",
    f"screen.{MOD}.scanner.more": "Showing %s-%s of %s, scroll for more",
    f"message.{MOD}.pager_alert": "%s: frequency %s is ON (strength %s)",
    f"message.{MOD}.pager_sound_on": "Pager sound ON",
    f"message.{MOD}.pager_sound_off": "Pager sound OFF (messages only)",
    f"message.{MOD}.frequency_copied_pager": "Pager tuned to frequency %s",
    f"tooltip.{MOD}.pager_status_on": "Signal: ON (strength %s)",
    f"tooltip.{MOD}.pager_status_quiet": "Signal: quiet",
    f"tooltip.{MOD}.pager_beeping": "Sound: on",
    f"tooltip.{MOD}.pager_silent": "Sound: off (messages only)",
    f"tooltip.{MOD}.pager_chat_on": "Chat log: on (alerts also go in chat)",
    f"tooltip.{MOD}.pager_chat_off": "Chat log: off",
    f"message.{MOD}.pager_log": "[Day %s, %s] ",
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
    f"message.{MOD}.remote_off": "Frequency %s switched OFF",
    f"message.{MOD}.remote_result": "%s | %s",
    f"message.{MOD}.remote_heard_one": "Heard by 1 receiver",
    f"message.{MOD}.remote_heard_many": "Heard by %s receivers",
    f"message.{MOD}.remote_not_heard": "No receiver in range",

    f"tooltip.{MOD}.frequency": "Frequency: %s",
    f"tooltip.{MOD}.tune_hint": "Use a Frequency Tuner to change its frequency.",
    f"tooltip.{MOD}.tuner_use_air": "Right-click the air to type a frequency",
    f"tooltip.{MOD}.tuner_use_block": "Right-click a transmitter or receiver to tune it",
    f"tooltip.{MOD}.tuner_copy": "Sneak + right-click one to copy its frequency",

    f"message.{MOD}.frequency_set": "Tuned to frequency %s",
    f"message.{MOD}.frequency_copied": "Copied frequency %s to the tuner",
    f"message.{MOD}.transmitter_status": "Transmitter | Frequency %s | Sending %s | %s | Reach %s blocks",
    f"message.{MOD}.receiver_status": "Receiver | Frequency %s | Output %s | Transmitters in range: %s",
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
    f"{MOD}.configuration.towersRelayRemotes": "Transmitters relay remote presses",
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

recipes["signal_scanner"] = ({
    "type": "minecraft:crafting_shapeless",
    "category": "equipment",
    "ingredients": [{"item": f"{MOD}:radio_receiver"}, {"item": "minecraft:compass"},
                    {"item": "minecraft:iron_ingot"}],
    "result": {"count": 1, "id": f"{MOD}:signal_scanner"},
}, f"{MOD}:radio_receiver")

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

for name in ("radio_transmitter", "radio_receiver", "compact_radio_receiver", "radio_mast", "wall_redstone"):
    entry = {"type": "minecraft:item", "name": f"{MOD}:{name}"}
    if name not in ("radio_mast", "wall_redstone"):
        # Copy the block's frequency onto the dropped item so it keeps its tuning.
        entry["functions"] = [{
            "function": "minecraft:copy_components",
            "source": "block_entity",
            "include": [f"{MOD}:frequency"],
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
    "values": [f"{MOD}:radio_transmitter", f"{MOD}:radio_receiver", f"{MOD}:compact_radio_receiver", f"{MOD}:radio_mast"]
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
