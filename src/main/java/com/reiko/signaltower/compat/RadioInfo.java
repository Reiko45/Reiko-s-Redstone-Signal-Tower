package com.reiko.signaltower.compat;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

    /** The server's data for the block at {@code pos}, or null if it isn't one of the mod's radio blocks or masts. */
    public static @Nullable CompoundTag read(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        CompoundTag tag = new CompoundTag();
        if (block instanceof RadioMastBlock) {
            BlockPos.MutableBlockPos cursor = pos.mutable();
            for (int i = 0; i < MAST_SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof RadioMastBlock; i++) {
                cursor.move(Direction.UP);
            }
            Block top = level.getBlockState(cursor).getBlock();
            if ((top instanceof RadioTransmitterBlock || top instanceof RadioRepeaterBlock)
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
            tag.putInt("power", RadioHelper.readTransmitterInput(level, pos));
            tag.putInt("reach", RadioHelper.reachAt(level, pos));
        } else if (block instanceof RadioRepeaterBlock) {
            tag.putInt("power", SignalNetwork.get(level).repeaterPower(level, pos));
            tag.putInt("reach", RadioHelper.reachAt(level, pos));
            if (block instanceof RadioConverterBlock && radio instanceof RadioRepeaterBlockEntity converter) {
                tag.putInt("output", converter.getOutputFrequency());
            }
        } else if (block instanceof RadioReceiverBlock) {
            SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, frequency, pos);
            tag.putInt("power", state.getValue(RadioReceiverBlock.POWER));
            tag.putInt("towers", reception.transmittersInReach());
            if (block instanceof RadioToggleReceiverBlock && radio instanceof RadioReceiverBlockEntity toggle) {
                tag.putBoolean("toggled_on", toggle.isToggledOn());
                tag.putInt("heard", reception.power());
            }
        }
        return tag;
    }

    /**
     * The tooltip lines for a block in {@code state}. {@code data} is what {@link #read} sent, or
     * null if the server didn't send any; then only what the block state shows is listed.
     */
    public static List<Component> lines(BlockState state, @Nullable CompoundTag data) {
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
                lines.add(Component.translatable(LANG + "listens", number(data.getInt("frequency"))));
                lines.add(Component.translatable(LANG + "sends", number(data.getInt("output"))));
            } else {
                lines.add(frequencyLine(data.getInt("frequency")));
            }
            return lines;
        }
        if (!(block instanceof RadioTransmitterBlock || block instanceof RadioRepeaterBlock || block instanceof RadioReceiverBlock)) {
            return lines;
        }
        if (block instanceof RadioConverterBlock) {
            boolean on = hasData ? data.getInt("power") > 0 : state.getValue(RadioRepeaterBlock.LIT);
            if (hasData) {
                lines.add(Component.translatable(LANG + "listens", number(data.getInt("frequency"))));
                lines.add(Component.translatable(LANG + "sends", number(data.getInt("output"))));
            }
            lines.add(!on ? Component.translatable(LANG + "converting_nothing").withStyle(ChatFormatting.GRAY)
                    : hasData ? Component.translatable(LANG + "converting", value(data.getInt("power")))
                    : Component.translatable(LANG + "converting_on").withStyle(ChatFormatting.GREEN));
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
            }
            return lines;
        }
        if (hasData) {
            lines.add(frequencyLine(data.getInt("frequency")));
        }
        if (block instanceof RadioTransmitterBlock) {
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

    private static Component frequencyLine(int frequency) {
        return Component.translatable(LANG + "frequency", number(frequency));
    }

    private static Component number(int frequency) {
        return Component.literal(Integer.toString(frequency)).withStyle(ChatFormatting.AQUA);
    }

    private static Component value(int power) {
        return Component.literal(Integer.toString(power)).withStyle(ChatFormatting.GREEN);
    }
}
