package com.reiko.signaltower.compat;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CodeKeypadBlock;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a "what am I looking at" overlay (Jade) shows for the mod's blocks. Kept apart from Jade's
 * API so it can be tested without Jade: {@link #read} runs on the server and gathers what only the
 * server knows (frequency, what it hears, reach); {@link #lines} runs on the client and turns that
 * into tooltip lines. Without server data (Jade only on the client) the lines fall back to what the
 * block state shows.
 */
public final class RadioInfo {
    /** The key the server data is stored under, so it can't clash with other Jade providers. */
    public static final String KEY = SignalTower.MOD_ID;

    private static final String LANG = "jade." + SignalTower.MOD_ID + ".";
    /** How far up a mast column is followed to find its top. */
    private static final int MAST_SCAN_LIMIT = 4096;

    private RadioInfo() {}

    /**
     * The server's data for the block at {@code pos}, or null if it isn't one of the mod's radio
     * blocks or masts. The frequencies' names (if they have any) come along as "name" and
     * "output_name", since the client might not know them, and their notes as "note" and
     * "output_note". If the frequency it sends on (or a
     * receiver's) is private, its owner comes along as "private_to", "encrypted" says whether only
     * members hear it, and "silenced" says whether this block is kept quiet because its tuner
     * isn't the owner or a friend. A receiver or converter that can't hear the frequency it
     * listens on (someone else's encrypted one) gets its owner as "deaf_to".
     */
    public static @Nullable CompoundTag read(ServerLevel level, BlockPos pos) {
        return read(level, pos, null);
    }

    /**
     * {@link #read(ServerLevel, BlockPos)} for {@code viewer}: a note kept to a private
     * frequency's members only comes along if they may see it ({@code null}: open notes only).
     */
    public static @Nullable CompoundTag read(ServerLevel level, BlockPos pos, @Nullable Player viewer) {
        CompoundTag tag = gather(level, pos);
        if (tag != null && level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.MotionSensorBlockEntity sensor) {
            // v0.39.0: whose it is and whether it sees someone; its range, mode and who it saw only for its owner.
            tag.putString("sensor_owner", sensor.getOwnerName());
            tag.putBoolean("sensing", sensor.isSensing());
            if (viewer != null && sensor.mayConfigure(viewer)) {
                tag.putInt("sensor_range", sensor.getRange());
                tag.putString("sensor_mode", sensor.getMode().key());
                if (sensor.isSensing()) {
                    tag.putString("sensor_sees", sensor.getSeenName());
                }
            }
        }
        if (tag != null) {
            addName(level, tag, "frequency", "name");
            addName(level, tag, "output", "output_name");
            addNote(level, tag, "frequency", "note", viewer);
            addNote(level, tag, "output", "output_note", viewer);
            addName(level, tag, "second", "second_name");
            addNote(level, tag, "second", "second_note", viewer);
            if (!tag.contains("top") && tag.contains("frequency")) {
                int frequency = tag.contains("output") ? tag.getInt("output") : tag.getInt("frequency");
                FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(frequency);
                if (claim != null) {
                    tag.putString("private_to", claim.ownerName());
                    if (claim.encrypted()) {
                        tag.putBoolean("encrypted", true);
                    }
                    boolean silentKeypad = level.getBlockEntity(pos) instanceof CodeKeypadBlockEntity keypad
                            && !FrequencyClaims.maySend(level, frequency, keypad.getTunedBy());
                    if (SignalNetwork.get(level).isSilenced(level, pos) || silentKeypad) {
                        tag.putBoolean("silenced", true);
                    }
                }
                if (level.getBlockEntity(pos) instanceof RadioBlockEntity radio
                        && (radio instanceof RadioReceiverBlockEntity || radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter())) {
                    FrequencyClaims.Claim heard = FrequencyClaims.get(level.getServer()).get(radio.getFrequency());
                    if (heard != null && !heard.hears(radio.getTunedBy()) && radio.getTunedBy() != null) {
                        tag.putString("deaf_to", heard.ownerName());
                    }
                    if (tag.contains("second") && radio.getTunedBy() != null) {
                        FrequencyClaims.Claim second = FrequencyClaims.get(level.getServer()).get(tag.getInt("second"));
                        if (second != null && !second.hears(radio.getTunedBy())) {
                            tag.putString("deaf_b_to", second.ownerName());
                        }
                    }
                }
            }
        }
        return tag;
    }

    private static void addName(ServerLevel level, CompoundTag tag, String frequencyKey, String nameKey) {
        if (tag.contains(frequencyKey)) {
            String name = FrequencyNames.nameOf(level, tag.getInt(frequencyKey));
            if (name != null) {
                tag.putString(nameKey, name);
            }
        }
    }

    private static @Nullable CompoundTag gather(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        CompoundTag tag = new CompoundTag();
        if (block instanceof RadioMastBlock) {
            BlockPos.MutableBlockPos cursor = pos.mutable();
            for (int i = 0; i < MAST_SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof RadioMastBlock; i++) {
                cursor.move(Direction.UP);
            }
            Block top = level.getBlockState(cursor).getBlock();
            if ((top instanceof RadioTransmitterBlock && !(top instanceof com.reiko.signaltower.block.CompactRadioTransmitterBlock)
                    || top instanceof RadioRepeaterBlock)
                    && level.getBlockEntity(cursor) instanceof RadioBlockEntity radio) {
                tag.putString("top", top instanceof RadioConverterBlock ? "converter"
                        : top instanceof RadioRepeaterBlock ? "repeater" : "transmitter");
                tag.putInt("above", cursor.getY() - pos.getY());
                tag.putInt("frequency", radio.getFrequency());
                if (top instanceof RadioConverterBlock && radio instanceof RadioRepeaterBlockEntity converter) {
                    tag.putInt("output", converter.getOutputFrequency());
                }
            }
            return tag;
        }
        if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            return null;
        }
        int frequency = radio.getFrequency();
        tag.putInt("frequency", frequency);
        if (block instanceof RadioTransmitterBlock) {
            // A compact one (v0.38.0) reads only what's around it and has its fixed reach.
            tag.putInt("power", com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.inputOf(level, pos, state));
            tag.putInt("reach", com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachOf(level, pos, state));
        } else if (block instanceof RadioRepeaterBlock) {
            tag.putInt("power", SignalNetwork.get(level).repeaterPower(level, pos));
            tag.putInt("reach", RadioHelper.reachAt(level, pos));
            if (block instanceof RadioConverterBlock && radio instanceof RadioRepeaterBlockEntity converter) {
                tag.putInt("output", converter.getOutputFrequency());
                if (converter.getGate() != 0) {
                    // v0.41.0: its signal gate and what it hears on the frequency it listens on.
                    tag.putInt("gate", converter.getGate());
                    tag.putInt("threshold", converter.getThreshold());
                    tag.putInt("gate_heard", converter.gateHeard(level));
                }
            }
        } else if (block instanceof CodeKeypadBlock && radio instanceof CodeKeypadBlockEntity keypad) {
            // v0.37.0: whose it is, whether it has a code (never the code) and how far its pulse reaches.
            tag.putInt("reach", RadioHelper.reachAt(level, pos));
            tag.putString("keypad_owner", keypad.getOwnerName());
            tag.putBoolean("has_code", keypad.hasCode());
        } else if (block instanceof RadioReceiverBlock) {
            SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, frequency, pos, radio.getTunedBy());
            tag.putInt("power", state.getValue(RadioReceiverBlock.POWER));
            tag.putInt("towers", reception.transmittersInReach());
            if (block instanceof RadioToggleReceiverBlock && radio instanceof RadioReceiverBlockEntity toggle) {
                tag.putBoolean("toggled_on", toggle.isToggledOn());
                tag.putInt("heard", reception.power());
            } else if (block instanceof com.reiko.signaltower.block.RadioInvertedReceiverBlock) {
                tag.putInt("heard", reception.power());
            } else if (block instanceof com.reiko.signaltower.block.RadioDelayReceiverBlock && radio instanceof RadioReceiverBlockEntity delay) {
                // v0.41.0: its timings, what it hears and its next change.
                tag.putInt("delay", delay.getDelayTicks());
                tag.putInt("hold", delay.getHoldTicks());
                tag.putInt("heard", reception.power());
                tag.putLong("change_in", delay.changeIn(level.getGameTime()));
            } else if (block instanceof com.reiko.signaltower.block.RadioLogicReceiverBlock && radio instanceof RadioReceiverBlockEntity logic) {
                // v0.40.0: both frequencies, the mode and what it hears on each.
                tag.putInt("second", logic.getSecondFrequency());
                tag.putString("logic_mode", logic.getLogicMode().key());
                tag.putInt("heard", logic.heardFirst());
                tag.putInt("heard_b", logic.heardSecond());
            }
        }
        return tag;
    }

    /**
     * The tooltip lines for a block in {@code state}. {@code data} is what {@link #read} sent, or
     * null if the server didn't send any; then only what the block state shows is listed.
     */
    public static List<Component> lines(BlockState state, @Nullable CompoundTag data) {
        List<Component> lines = blockLines(state, data);
        boolean deaf = data != null && data.contains("deaf_to");
        boolean receiver = state.getBlock() instanceof RadioReceiverBlock;
        // A deaf receiver's own frequency is the one it can't hear: one line says both.
        if (data != null && data.contains("private_to") && !(deaf && receiver)) {
            Component owner = Component.literal(data.getString("private_to"));
            lines.add(data.getBoolean("silenced")
                    ? Component.translatable(LANG + "silenced", owner).withStyle(ChatFormatting.RED)
                    : data.getBoolean("encrypted")
                    ? Component.translatable(LANG + "encrypted", owner).withStyle(ChatFormatting.GOLD)
                    : Component.translatable(LANG + "private", owner).withStyle(ChatFormatting.GOLD));
        }
        if (deaf) {
            lines.add(Component.translatable(LANG + "deaf", Component.literal(data.getString("deaf_to"))).withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private static List<Component> blockLines(BlockState state, @Nullable CompoundTag data) {
        Block block = state.getBlock();
        List<Component> lines = new ArrayList<>();
        boolean hasData = data != null && data.contains("frequency");
        if (block instanceof RadioMastBlock) {
            if (data == null) {
                return lines;
            }
            if (!data.contains("top")) {
                lines.add(Component.translatable(LANG + "mast_no_top").withStyle(ChatFormatting.GRAY));
                return lines;
            }
            Component top = Component.translatable("block." + SignalTower.MOD_ID + ".radio_" + data.getString("top"));
            lines.add(Component.translatable(LANG + "mast_top", top, data.getInt("above")));
            if (data.contains("output")) {
                lines.add(Component.translatable(LANG + "listens", number(data, "frequency", "name")));
                addNoteLine(lines, data, "note");
                lines.add(Component.translatable(LANG + "sends", number(data, "output", "output_name")));
                addNoteLine(lines, data, "output_note");
            } else {
                lines.add(frequencyLine(data));
                addNoteLine(lines, data, "note");
            }
            return lines;
        }
        if (block instanceof CodeKeypadBlock) {
            if (hasData) {
                lines.add(frequencyLine(data));
                addNoteLine(lines, data, "note");
                String owner = data.getString("keypad_owner");
                if (!owner.isEmpty()) {
                    lines.add(Component.translatable(LANG + "keypad_owner", Component.literal(owner)).withStyle(ChatFormatting.GRAY));
                }
                if (!data.getBoolean("has_code")) {
                    lines.add(Component.translatable(LANG + "keypad_no_code").withStyle(ChatFormatting.YELLOW));
                }
            }
            lines.add(state.getValue(CodeKeypadBlock.POWERED)
                    ? Component.translatable(LANG + "keypad_open").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(LANG + "keypad_waiting").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
            }
            return lines;
        }
        if (!(block instanceof RadioTransmitterBlock || block instanceof RadioRepeaterBlock || block instanceof RadioReceiverBlock)) {
            return lines;
        }
        if (block instanceof com.reiko.signaltower.block.RadioLogicReceiverBlock) {
            // v0.40.0: both frequencies, the mode, what it hears on each, then the output.
            if (hasData) {
                lines.add(Component.translatable(LANG + "input_first", number(data, "frequency", "name")));
                addNoteLine(lines, data, "note");
                lines.add(Component.translatable(LANG + "input_second", number(data, "second", "second_name")));
                addNoteLine(lines, data, "second_note");
                lines.add(Component.translatable(LANG + "logic_mode",
                        Component.translatable(LANG + "logic_" + data.getString("logic_mode")).withStyle(ChatFormatting.YELLOW)));
                lines.add(Component.translatable(LANG + "logic_hearing", heardValue(data.getInt("heard")), heardValue(data.getInt("heard_b"))));
            }
            int power = state.getValue(RadioReceiverBlock.POWER);
            lines.add(power > 0 ? Component.translatable(LANG + "output", value(power))
                    : Component.translatable(LANG + "output_off").withStyle(ChatFormatting.GRAY));
            if (hasData && data.contains("deaf_b_to")) {
                lines.add(Component.translatable(LANG + "deaf_second", Component.literal(data.getString("deaf_b_to"))).withStyle(ChatFormatting.RED));
            }
            return lines;
        }
        if (block instanceof RadioConverterBlock) {
            boolean on = hasData ? data.getInt("power") > 0 : state.getValue(RadioRepeaterBlock.LIT);
            if (hasData) {
                lines.add(Component.translatable(LANG + "listens", number(data, "frequency", "name")));
                addNoteLine(lines, data, "note");
                lines.add(Component.translatable(LANG + "sends", number(data, "output", "output_name")));
                addNoteLine(lines, data, "output_note");
            }
            boolean gated = hasData && data.contains("gate");
            lines.add(!on && gated ? Component.translatable(LANG + "gate_closed").withStyle(ChatFormatting.GRAY)
                    : !on ? Component.translatable(LANG + "converting_nothing").withStyle(ChatFormatting.GRAY)
                    : gated ? Component.translatable(LANG + "sending", value(data.getInt("power")))
                    : hasData ? Component.translatable(LANG + "converting", value(data.getInt("power")))
                    : Component.translatable(LANG + "converting_on").withStyle(ChatFormatting.GREEN));
            if (hasData && data.contains("gate")) {
                lines.add(Component.translatable(LANG + "gate",
                        com.reiko.signaltower.network.ConfigureConverterPayload.gateText(data.getInt("gate"), data.getInt("threshold")),
                        heardValue(data.getInt("gate_heard"))).withStyle(ChatFormatting.YELLOW));
            }
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
            }
            return lines;
        }
        if (hasData) {
            lines.add(frequencyLine(data));
            addNoteLine(lines, data, "note");
        }
        if (block instanceof com.reiko.signaltower.block.RadioMotionSensorBlock) {
            boolean on = state.getValue(RadioTransmitterBlock.POWERED);
            if (hasData) {
                String owner = data.getString("sensor_owner");
                if (!owner.isEmpty()) {
                    lines.add(Component.translatable(LANG + "sensor_owner", Component.literal(owner)).withStyle(ChatFormatting.GRAY));
                }
                if (data.contains("sensor_range")) {
                    lines.add(Component.translatable(LANG + "sensor_settings", data.getInt("sensor_range"),
                            Component.translatable(LANG + "sensor_mode_" + data.getString("sensor_mode"))));
                }
            }
            String sees = hasData ? data.getString("sensor_sees") : "";
            lines.add(!on ? Component.translatable(LANG + "sensor_idle").withStyle(ChatFormatting.GRAY)
                    : !sees.isEmpty() ? Component.translatable(LANG + "sensor_sees", Component.literal(sees)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(LANG + "sensor_on").withStyle(ChatFormatting.GREEN));
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
            }
        } else if (block instanceof RadioTransmitterBlock) {
            boolean on = hasData ? data.getInt("power") > 0 : state.getValue(RadioTransmitterBlock.POWERED);
            lines.add(!on ? Component.translatable(LANG + "sending_nothing").withStyle(ChatFormatting.GRAY)
                    : hasData ? Component.translatable(LANG + "sending", value(data.getInt("power")))
                    : Component.translatable(LANG + "broadcasting").withStyle(ChatFormatting.GREEN));
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
            }
        } else if (block instanceof RadioRepeaterBlock) {
            boolean on = hasData ? data.getInt("power") > 0 : state.getValue(RadioRepeaterBlock.LIT);
            lines.add(!on ? Component.translatable(LANG + "repeating_nothing").withStyle(ChatFormatting.GRAY)
                    : hasData ? Component.translatable(LANG + "repeating", value(data.getInt("power")))
                    : Component.translatable(LANG + "repeating_on").withStyle(ChatFormatting.GREEN));
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
            }
        } else if (block instanceof RadioToggleReceiverBlock) {
            // The block state's output says whether it's on (it's 15 or 0), with or without server data.
            boolean on = state.getValue(RadioReceiverBlock.POWER) > 0;
            lines.add(on ? Component.translatable(LANG + "switched_on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(LANG + "switched_off").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                int heard = data.getInt("heard");
                lines.add(heard > 0 ? Component.translatable(LANG + "hearing", value(heard))
                        : Component.translatable(LANG + "hearing_nothing").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(LANG + "towers", data.getInt("towers")));
            }
        } else if (block instanceof com.reiko.signaltower.block.RadioDelayReceiverBlock) {
            // v0.41.0: on or off (the block state says which), its timings, what it hears and its next change.
            boolean on = state.getValue(RadioReceiverBlock.POWER) > 0;
            lines.add(on ? Component.translatable(LANG + "switched_on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(LANG + "switched_off").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                lines.add(Component.translatable(LANG + "delay_timing", RadioReceiverBlockEntity.timeLabel(data.getInt("delay")),
                        RadioReceiverBlockEntity.timeLabel(data.getInt("hold"))));
                int heard = data.getInt("heard");
                lines.add(heard > 0 ? Component.translatable(LANG + "hearing", value(heard))
                        : Component.translatable(LANG + "hearing_nothing").withStyle(ChatFormatting.GRAY));
                long change = data.getLong("change_in");
                if (change != 0) {
                    lines.add(Component.translatable(LANG + (change > 0 ? "delay_turns_on" : "delay_turns_off"),
                            RadioReceiverBlockEntity.countdownLabel(Math.abs(change))).withStyle(ChatFormatting.YELLOW));
                }
                lines.add(Component.translatable(LANG + "towers", data.getInt("towers")));
            }
        } else if (block instanceof com.reiko.signaltower.block.RadioInvertedReceiverBlock) {
            // v0.39.0: on while silent. The block state's output says which, with or without server data.
            boolean on = state.getValue(RadioReceiverBlock.POWER) > 0;
            lines.add(on ? Component.translatable(LANG + "inverted_on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(LANG + "inverted_off").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                int heard = data.getInt("heard");
                lines.add(heard > 0 ? Component.translatable(LANG + "hearing", value(heard))
                        : Component.translatable(LANG + "hearing_nothing").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(LANG + "towers", data.getInt("towers")));
            }
        } else if (block instanceof RadioReceiverBlock) {
            int power = state.getValue(RadioReceiverBlock.POWER);
            lines.add(power > 0 ? Component.translatable(LANG + "output", value(power))
                    : Component.translatable(LANG + "output_off").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                lines.add(Component.translatable(LANG + "towers", data.getInt("towers")));
            }
        }
        return lines;
    }

    /** A strength for the logic receiver's "Hearing" line: the number (green) or "nothing" (grey). */
    private static Component heardValue(int heard) {
        return heard > 0 ? value(heard) : Component.translatable(LANG + "nothing").withStyle(ChatFormatting.GRAY);
    }

    private static Component frequencyLine(CompoundTag data) {
        return Component.translatable(LANG + "frequency", number(data, "frequency", "name"));
    }

    /** Puts the note on the frequency under {@code frequencyKey} (if it has one that {@code viewer} may see) under {@code noteKey}. */
    private static void addNote(ServerLevel level, CompoundTag tag, String frequencyKey, String noteKey, @Nullable Player viewer) {
        if (tag.contains(frequencyKey)) {
            int frequency = tag.getInt(frequencyKey);
            String note = FrequencyNotes.noteFor(level.getServer(), frequency, viewer);
            if (note != null) {
                tag.putString(noteKey, note);
                if (FrequencyNotes.get(level.getServer()).isMembersOnly(frequency)) {
                    tag.putBoolean(noteKey + "_members", true);
                }
            }
        }
    }

    /** Adds the note under {@code noteKey} (if there is one) as a grey line. */
    private static void addNoteLine(List<Component> lines, CompoundTag data, String noteKey) {
        if (data.contains(noteKey)) {
            lines.add(FrequencyNotes.line(data.getString(noteKey), data.getBoolean(noteKey + "_members")));
        }
    }

    /** "2026", or "2026 (Front Gate)" if the server sent a name, in aqua. */
    private static Component number(CompoundTag data, String frequencyKey, String nameKey) {
        Component number = number(data.getInt(frequencyKey));
        return data.contains(nameKey)
                ? Component.translatable("frequency." + SignalTower.MOD_ID + ".named", number,
                        Component.literal(data.getString(nameKey)).withStyle(ChatFormatting.AQUA))
                : number;
    }

    private static Component number(int frequency) {
        return Component.literal(Integer.toString(frequency)).withStyle(ChatFormatting.AQUA);
    }

    private static Component value(int power) {
        return Component.literal(Integer.toString(power)).withStyle(ChatFormatting.GREEN);
    }
}
