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
        if (level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.StatusBoardBlockEntity board) {
            return readBoard(level, board);
        }
        if (level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.RadioBatteryBlockEntity battery) {
            // v0.49.0: how full it is, and how fast towers are using it.
            CompoundTag tag = new CompoundTag();
            tag.putInt("battery_charge", battery.getCharge());
            tag.putInt("battery_capacity", battery.getCapacity());
            tag.putDouble("battery_use", battery.usePerSecond(level.getGameTime()));
            tag.putDouble("battery_gain", battery.gainPerSecond(level.getGameTime()));
            return tag;
        }
        if (level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.SolarPanelBlockEntity) {
            // v0.50.0: how much sun it gets, and whether there's a battery under it to charge.
            CompoundTag tag = new CompoundTag();
            int daylight = com.reiko.signaltower.blockentity.SolarPanelBlockEntity.daylight(level, pos);
            tag.putInt("solar_daylight", daylight);
            tag.putDouble("solar_rate", com.reiko.signaltower.blockentity.SolarPanelBlockEntity.rateFor(daylight));
            tag.putBoolean("solar_battery", com.reiko.signaltower.blockentity.SolarPanelBlockEntity.batteryUnder(level, pos) != null);
            return tag;
        }
        if (level.getBlockState(pos).getBlock() instanceof com.reiko.signaltower.block.AviationBeaconBlock) {
            return readBeacon(level, pos);
        }
        if (level.getBlockState(pos).getBlock() instanceof com.reiko.signaltower.block.BundledCableBlock
                || level.getBlockState(pos).getBlock() instanceof com.reiko.signaltower.block.CableTapBlock) {
            // v0.54.0: what the cable network carries.
            com.reiko.signaltower.radio.BundledCables.Net net = com.reiko.signaltower.radio.BundledCables.scan(level, pos);
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("cable_channels", net.channels());
            tag.putInt("cable_count", net.cables());
            tag.putInt("cable_devices", net.devices().size());
            return tag;
        }
        if (level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.GuyAnchorBlockEntity anchor) {
            // v0.54.0: the mast it braces, how tall, and how many anchors brace it.
            CompoundTag tag = new CompoundTag();
            BlockPos base = anchor.getBase();
            if (base != null) {
                tag.putLong("anchor_base", base.asLong());
                tag.putInt("anchor_height", com.reiko.signaltower.blockentity.GuyAnchorBlockEntity.columnHeight(level, base));
                tag.putInt("anchor_braces", SignalNetwork.get(level).bracesOf(base));
            }
            tag.putInt("anchor_needed", com.reiko.signaltower.Config.GUY_WIRES_TO_BRACE.get());
            return tag;
        }
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
                        && (radio instanceof RadioReceiverBlockEntity || radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter()
                            || radio instanceof com.reiko.signaltower.blockentity.TelegraphBlockEntity)) {
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
                        : top instanceof RadioRepeaterBlock ? "repeater"
                        : top instanceof com.reiko.signaltower.block.RadioTimerTransmitterBlock ? "timer_transmitter"
                        : top instanceof com.reiko.signaltower.block.RadioWeatherStationBlock ? "weather_station"
                        : top instanceof com.reiko.signaltower.block.RadioSignalRecorderBlock ? "signal_recorder"
                        : top instanceof com.reiko.signaltower.block.RadioJammerBlock ? "jammer" : "transmitter");
                tag.putInt("above", cursor.getY() - pos.getY());
                com.reiko.signaltower.radio.AntennaTier antenna = com.reiko.signaltower.radio.AntennaTier.of(level.getBlockState(cursor));
                if (antenna != com.reiko.signaltower.radio.AntennaTier.NONE) {
                    tag.putString("top_antenna", antenna.getSerializedName());
                    int aim = com.reiko.signaltower.radio.AntennaTier.dishAim(level.getBlockState(cursor));
                    if (aim >= 0) {
                        tag.putInt("top_aim", aim);
                    }
                }
                tag.putInt("frequency", radio.getFrequency());
                if (top instanceof RadioConverterBlock && radio instanceof RadioRepeaterBlockEntity converter) {
                    tag.putInt("output", converter.getOutputFrequency());
                }
            }
            if (block instanceof com.reiko.signaltower.block.DishRotatorBlock) {
                // v0.52.0: its redstone, its arrow, where that points the dish, and whether it's the one in charge.
                int power = com.reiko.signaltower.block.DishRotatorBlock.power(level, pos);
                tag.putBoolean("rotator", true);
                tag.putInt("rotator_power", power);
                tag.putInt("rotator_home", com.reiko.signaltower.block.DishRotatorBlock.segment(state.getValue(com.reiko.signaltower.block.DishRotatorBlock.FACING)));
                tag.putInt("rotator_aim", com.reiko.signaltower.block.DishRotatorBlock.aim(state, power));
                BlockPos towerTop = com.reiko.signaltower.block.DishRotatorBlock.topAbove(level, pos);
                tag.putBoolean("rotator_dish", towerTop != null
                        && com.reiko.signaltower.radio.AntennaTier.dishAim(level.getBlockState(towerTop)) >= 0);
                tag.putBoolean("rotator_overruled", towerTop != null
                        && !pos.equals(com.reiko.signaltower.block.DishRotatorBlock.rotatorUnder(level, towerTop)));
            }
            return tag;
        }
        if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            return null;
        }
        int frequency = radio.getFrequency();
        tag.putInt("frequency", frequency);
        if (com.reiko.signaltower.radio.AntennaTier.dishAim(state) >= 0) {
            BlockPos rotator = com.reiko.signaltower.block.DishRotatorBlock.rotatorUnder(level, pos);
            if (rotator != null) {
                tag.putInt("rotated_by", com.reiko.signaltower.block.DishRotatorBlock.power(level, rotator)); // v0.52.0
            }
        }
        if (block instanceof RadioTransmitterBlock) {
            // A compact one (v0.38.0) reads only what's around it and has its fixed reach.
            tag.putInt("power", com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.inputOf(level, pos, state));
            tag.putInt("reach", com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachNow(level, pos, state));
            if (!(block instanceof com.reiko.signaltower.block.CompactRadioTransmitterBlock)) {
                putWeather(level, pos, tag);
            }
            if (block instanceof com.reiko.signaltower.block.RadioJammerBlock) {
                // v0.46.0: how strongly it jams (its redstone), unless it's kept quiet.
                tag.putBoolean("jammer", true);
            }
            if (radio instanceof com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity timer) {
                // v0.42.0: its schedule, whether redstone pauses it, and its interval's next change.
                tag.putBoolean("timer_daily", timer.isDaily());
                tag.putInt("timer_interval", timer.getInterval());
                tag.putInt("timer_pulse", timer.getPulse());
                tag.putInt("timer_on", timer.getOnHour());
                tag.putInt("timer_off", timer.getOffHour());
                tag.putBoolean("timer_paused", timer.isPaused(level));
                tag.putBoolean("timer_clock", timer.clockOn(level));
                tag.putLong("change_in", timer.changeIn(level));
            }
            if (radio instanceof com.reiko.signaltower.blockentity.BundledTransmitterBlockEntity bundled) {
                tag.putIntArray("bundle_channels", bundled.getChannels()); // v0.54.0
            }
            if (radio instanceof com.reiko.signaltower.blockentity.WeatherStationBlockEntity station) {
                // v0.53.0: what it reads, what that says now, whether redstone pauses it and whether it announces.
                com.reiko.signaltower.radio.StationReading.Value value = station.reading(level);
                tag.putInt("station_mode", station.getMode().ordinal());
                tag.putInt("station_strength", value.strength());
                putComponent(tag, "station_label", value.label());
                tag.putBoolean("station_paused", station.isPaused(level));
                tag.putBoolean("station_announce", station.isAnnouncing());
                tag.putBoolean("station_offered", station.getMode().offered());
            }
            if (radio instanceof com.reiko.signaltower.blockentity.SignalRecorderBlockEntity recorder) {
                // v0.44.0: what it records from (as "second", so it gets a name and a note), its tape and what it's doing.
                if (recorder.getListenFrequency() > 0) {
                    tag.putInt("second", recorder.getListenFrequency());
                }
                tag.putBoolean("recorder_loop", recorder.isLoop());
                tag.putInt("recorder_length", recorder.getLength());
                tag.putInt("recorder_changes", recorder.getChanges().length);
                tag.putInt("recorder_phase", recorder.getPhase().ordinal());
                tag.putInt("recorder_elapsed", recorder.elapsed(level.getGameTime()));
                tag.putInt("recorder_taking", recorder.getTaking().size());
            }
        } else if (block instanceof RadioRepeaterBlock) {
            tag.putInt("power", SignalNetwork.get(level).repeaterPower(level, pos));
            tag.putInt("reach", RadioHelper.towerReachNow(level, pos, state));
            putWeather(level, pos, tag);
            if (block instanceof RadioConverterBlock && radio instanceof RadioRepeaterBlockEntity converter) {
                tag.putInt("output", converter.getOutputFrequency());
                if (converter.getGate() != 0) {
                    // v0.41.0: its signal gate and what it hears on the frequency it listens on.
                    tag.putInt("gate", converter.getGate());
                    tag.putInt("threshold", converter.getThreshold());
                    tag.putInt("gate_heard", converter.gateHeard(level));
                }
            }
        } else if (radio instanceof com.reiko.signaltower.blockentity.TelegraphBlockEntity telegraph) {
            // v0.51.0: how far it sends, how many messages reached it, and the last one (unless it can't read them).
            tag.putInt("reach", RadioHelper.reachAt(level, pos));
            tag.putBoolean("telegraph", true);
            List<SignalNetwork.Message> heard = SignalNetwork.get(level).messagesAt(frequency, pos);
            tag.putInt("telegraph_count", heard.size());
            if (!heard.isEmpty() && FrequencyClaims.mayHear(level, frequency, telegraph.getTunedBy())) {
                SignalNetwork.Message last = heard.get(heard.size() - 1);
                tag.putString("telegraph_from", last.name());
                tag.putString("telegraph_text", last.text());
                tag.putLong("telegraph_ago", Math.max(0, (level.getGameTime() - last.gameTime()) / 20));
            }
        } else if (radio instanceof com.reiko.signaltower.blockentity.BundledReceiverBlockEntity bundled) {
            tag.putIntArray("bundle_heard", bundled.getHeard()); // v0.54.0
        } else if (block instanceof CodeKeypadBlock && radio instanceof CodeKeypadBlockEntity keypad) {
            // v0.37.0: whose it is, whether it has a code (never the code) and how far its pulse reaches.
            tag.putInt("reach", RadioHelper.reachAt(level, pos));
            tag.putString("keypad_owner", keypad.getOwnerName());
            tag.putBoolean("has_code", keypad.hasCode());
        } else if (block instanceof RadioReceiverBlock) {
            SignalNetwork.Reception reception = SignalNetwork.get(level).listen(level, frequency, pos, radio.getTunedBy());
            tag.putInt("power", state.getValue(RadioReceiverBlock.POWER));
            tag.putInt("towers", reception.transmittersInReach());
            // v0.46.0: what hills took off the signal it hears, and how strongly it's jammed.
            if (reception.hills() > 0) {
                tag.putInt("hills", reception.hills());
            }
            if (reception.jammed() > 0) {
                tag.putInt("jammed", reception.jammed());
            }
            // v0.47.0: it only hears its tower because it stands higher up.
            if (reception.lifted()) {
                tag.putInt("lifted", SignalNetwork.get(level).ear(level, pos));
            }
            // v0.48.0: how much the signal faded, far out in its tower's reach.
            if (reception.faded() > 0) {
                tag.putInt("faded", reception.faded());
            }
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
            } else if (block instanceof com.reiko.signaltower.block.RadioSpeakerBlock && radio instanceof RadioReceiverBlockEntity speaker) {
                // v0.43.0: what it plays, and what it hears.
                tag.putInt("speaker", speaker.packedSpeaker());
                tag.putInt("heard", reception.power());
            } else if (block instanceof com.reiko.signaltower.block.RadioCounterReceiverBlock && radio instanceof RadioReceiverBlockEntity counter) {
                // v0.42.0: the count, its settings and its reset frequency (as "second", so it gets a name, note and deafness).
                tag.putInt("count", counter.getCount());
                tag.putInt("counter_target", counter.getCounterTarget());
                tag.putBoolean("counter_latch", counter.isCounterLatch());
                tag.putInt("counter_forget", counter.getForgetTicks());
                tag.putBoolean("counter_on", counter.isCounterOn());
                tag.putLong("forget_in", counter.forgetIn(level.getGameTime()));
                tag.putInt("heard", reception.power());
                if (counter.getResetFrequency() > 0) {
                    tag.putInt("second", counter.getResetFrequency());
                }
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
     * v0.46.0: the weather a tower is in, if it shortens its reach ("weather", "weather_loss" in
     * percent); v0.47.0: how long until it's back if lightning knocked it out ("struck", ticks).
     */
    private static void putWeather(ServerLevel level, BlockPos pos, CompoundTag tag) {
        BlockState state = level.getBlockState(pos);
        if (com.reiko.signaltower.radio.AntennaTier.dishAim(state) >= 0) {
            // v0.48.0: how far its dish reaches, the way it's aimed (in the weather now).
            tag.putInt("dish_reach", com.reiko.signaltower.item.AntennaItem.dishReachShown(level, pos, state));
        }
        var target = SignalNetwork.get(level).linkTarget(pos);
        ServerLevel there = target == null ? null : level.getServer().getLevel(target);
        if (there != null) {
            // v0.50.0: where its Ender Antenna sends into, and where it arrives there.
            tag.putString("link_dimension", target.location().toString());
            tag.putLong("link_at", com.reiko.signaltower.radio.CrossDimension.arrival(level, there, pos).asLong());
        }
        // v0.54.0: storm damage, ice, and how braced a tall tower is.
        SignalNetwork storms = SignalNetwork.get(level);
        String cause = storms.damageCause(pos);
        if (cause != null) {
            tag.putString("storm_damaged", cause);
            tag.putInt("power", 0);
        }
        if (storms.isIced(pos)) {
            tag.putInt("iced_thawed", storms.thawed(pos) / 20);
            tag.putInt("iced_needed", com.reiko.signaltower.Config.ICE_THAW_SECONDS.get());
            tag.putInt("iced_loss", com.reiko.signaltower.Config.ICE_REACH_LOSS_PERCENT.get());
        }
        com.reiko.signaltower.radio.StormDamage.Assessment assessment = com.reiko.signaltower.radio.StormDamage.assess(level, storms, pos);
        if (assessment.braces() > 0 || assessment.tall() && com.reiko.signaltower.Config.STORM_DAMAGE.get() && ReikosWeatherCompat.available()) {
            tag.putInt("braces", assessment.braces());
            tag.putInt("braces_needed", com.reiko.signaltower.Config.GUY_WIRES_TO_BRACE.get());
            tag.putBoolean("braces_tall", assessment.tall());
        }
        if (level.getBlockEntity(pos) instanceof com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity found && found.isFoundTower()) {
            // v0.55.0: an old tower generated in the world; v0.57.0: or a village's radio shack
            tag.putBoolean(found.isVillageStation() ? "village_station" : "found_tower", true);
        }
        long struck = SignalNetwork.get(level).outageTicks(pos);
        if (struck > 0) {
            tag.putLong("struck", struck);
            tag.putInt("power", 0); // it sends (relays, jams) nothing until it's back
        }
        putPower(level, pos, tag);
        com.reiko.signaltower.radio.Weather weather = SignalNetwork.get(level).weatherAt(level, pos);
        if (weather.loss() > 0) {
            tag.putInt("weather", weather.ordinal());
            tag.putInt("weather_loss", weather.loss());
            // v0.53.0: Reiko's Weather's own name for it ("Supercell"), when it agrees.
            ReikosWeatherCompat.Local local = ReikosWeatherCompat.local(level, pos);
            if (local != null && local.weather() == weather) {
                tag.putString("weather_reikos", local.id());
            }
        }
    }

    /** v0.53.0: a text that's worked out on the server (a weather station's reading), kept as it is. */
    private static void putComponent(CompoundTag tag, String key, Component component) {
        net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, component)
                .result().ifPresent(encoded -> tag.put(key, encoded));
    }

    private static Component getComponent(CompoundTag tag, String key) {
        net.minecraft.nbt.Tag encoded = tag.get(key);
        return encoded == null ? Component.empty()
                : net.minecraft.network.chat.ComponentSerialization.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .result().orElse(Component.empty());
    }

    /**
     * v0.49.0: a tower's power, if power cost is on: "unpowered" (out of power: it sends nothing), or
     * its batteries' charge ("tower_charge", "tower_capacity") and what it uses a second while it
     * sends ("tower_rate"; "tower_sending": it's sending, so using it now).
     */
    private static void putPower(ServerLevel level, BlockPos pos, CompoundTag tag) {
        if (!com.reiko.signaltower.radio.PowerCost.enabled() || !com.reiko.signaltower.radio.PowerCost.pays(level, pos)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(level);
        if (network.isDrained(pos)) {
            tag.putBoolean("unpowered", true);
            tag.putInt("power", 0);
            return;
        }
        var batteries = com.reiko.signaltower.radio.PowerCost.batteries(level, pos);
        if (batteries.isEmpty()) {
            return;
        }
        long capacity = 0;
        for (var battery : batteries) {
            capacity += battery.getCapacity();
        }
        tag.putLong("tower_charge", com.reiko.signaltower.radio.PowerCost.charge(batteries));
        tag.putLong("tower_capacity", capacity);
        tag.putDouble("tower_rate", com.reiko.signaltower.radio.PowerCost.rateFor(network, pos));
        tag.putBoolean("tower_sending", network.wantsToSend(level, pos));
    }

    /**
     * The tooltip lines for a block in {@code state}. {@code data} is what {@link #read} sent, or
     * null if the server didn't send any; then only what the block state shows is listed.
     */
    public static List<Component> lines(BlockState state, @Nullable CompoundTag data) {
        List<Component> lines = blockLines(state, data);
        com.reiko.signaltower.radio.AntennaTier antenna = com.reiko.signaltower.radio.AntennaTier.of(state);
        if (antenna == com.reiko.signaltower.radio.AntennaTier.DISH) {
            // v0.48.0: where its dish points, and how far it reaches that way.
            int aim = com.reiko.signaltower.radio.AntennaTier.aimOf(state);
            lines.add(Component.translatable(LANG + "antenna_dish", antenna.label(), com.reiko.signaltower.radio.AntennaTier.aimLabel(aim),
                    antenna.multiplierText()).withStyle(ChatFormatting.GOLD));
            if (data != null && data.contains("dish_reach")) {
                lines.add(Component.translatable(LANG + "dish_reach", com.reiko.signaltower.radio.AntennaTier.aimLabel(aim),
                        data.getInt("dish_reach")).withStyle(ChatFormatting.GOLD));
            }
            if (data != null && data.contains("rotated_by")) {
                lines.add(Component.translatable(LANG + "dish_rotated", value(data.getInt("rotated_by"))).withStyle(ChatFormatting.GOLD));
            }
        } else if (antenna == com.reiko.signaltower.radio.AntennaTier.ENDER && data != null && data.contains("link_dimension")) {
            // v0.50.0: the dimension its Ender Antenna sends into, and where.
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(data.getString("link_dimension"));
            Component dimension = id == null ? Component.literal(data.getString("link_dimension"))
                    : com.reiko.signaltower.radio.CrossDimension.name(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
            lines.add(Component.translatable(LANG + "antenna_ender", antenna.label(), dimension,
                    com.reiko.signaltower.radio.CrossDimension.coords(BlockPos.of(data.getLong("link_at")))).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (antenna != com.reiko.signaltower.radio.AntennaTier.NONE) {
            // v0.45.0: its antenna, and how much farther that makes it reach.
            lines.add(Component.translatable(LANG + "antenna", antenna.label(), antenna.multiplierText()).withStyle(ChatFormatting.GOLD));
        }
        if (data != null && data.contains("weather")) {
            // v0.46.0: rain or a thunderstorm where a tower stands shortens its reach (the reach shown already is).
            lines.add(Component.translatable(LANG + "weather", data.contains("weather_reikos")
                            ? ReikosWeatherCompat.weatherName(data.getString("weather_reikos"))
                            : com.reiko.signaltower.radio.Weather.of(data.getInt("weather")).label(),
                    data.getInt("weather_loss")).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (data != null && data.getBoolean("found_tower")) {
            lines.add(Component.translatable(LANG + "found_tower").withStyle(ChatFormatting.DARK_GREEN));
        }
        if (data != null && data.getBoolean("village_station")) {
            lines.add(Component.translatable(LANG + "village_station").withStyle(ChatFormatting.DARK_GREEN));
        }
        if (data != null && data.contains("storm_damaged")) {
            // v0.54.0
            lines.add(Component.translatable(LANG + "storm_damaged", com.reiko.signaltower.radio.StormDamage.causeName(data.getString("storm_damaged")))
                    .withStyle(ChatFormatting.RED));
        }
        if (data != null && data.contains("iced_thawed")) {
            lines.add(Component.translatable(LANG + "iced", data.getInt("iced_loss"), data.getInt("iced_thawed"), data.getInt("iced_needed"))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (data != null && data.contains("braces")) {
            int braces = data.getInt("braces");
            int needed = data.getInt("braces_needed");
            lines.add(braces >= needed ? Component.translatable(LANG + "braced", braces).withStyle(ChatFormatting.GREEN)
                    : data.getBoolean("braces_tall") ? Component.translatable(LANG + "not_braced", braces, needed).withStyle(ChatFormatting.GOLD)
                    : Component.translatable(LANG + "braces_short", braces).withStyle(ChatFormatting.GRAY));
        }
        if (data != null && data.contains("struck")) {
            lines.add(Component.translatable(LANG + "struck", RadioReceiverBlockEntity.countdownLabel(data.getLong("struck")))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (data != null && data.getBoolean("unpowered")) {
            lines.add(Component.translatable(LANG + "unpowered").withStyle(ChatFormatting.RED));
        } else if (data != null && data.contains("tower_charge")) {
            // v0.49.0: its batteries, and how long they last while it sends.
            long charge = data.getLong("tower_charge");
            double rate = data.getDouble("tower_rate");
            int percent = com.reiko.signaltower.block.RadioBatteryBlock.percent(charge, data.getLong("tower_capacity"));
            String perSecond = com.reiko.signaltower.radio.PowerCost.rateLabel(rate);
            lines.add((data.getBoolean("tower_sending") && rate > 0
                    ? Component.translatable(LANG + "tower_battery", percent, com.reiko.signaltower.radio.PowerCost.durationLabel(charge / rate), perSecond)
                    : Component.translatable(LANG + "tower_battery_idle", percent, perSecond)).withStyle(ChatFormatting.RED));
        }
        if (data != null && data.contains("lifted")) {
            lines.add(Component.translatable(LANG + "lifted", data.getInt("lifted")).withStyle(ChatFormatting.GREEN));
        }
        if (data != null && data.contains("faded")) {
            lines.add(Component.translatable(LANG + "faded", data.getInt("faded")).withStyle(ChatFormatting.GRAY));
        }
        if (data != null && data.contains("hills")) {
            lines.add(Component.translatable(LANG + "hills", data.getInt("hills")).withStyle(ChatFormatting.GOLD));
        }
        if (data != null && data.contains("jammed")) {
            lines.add(Component.translatable(LANG + "jammed", data.getInt("jammed")).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        boolean deaf = data != null && data.contains("deaf_to");
        boolean receiver = state.getBlock() instanceof RadioReceiverBlock || state.getBlock() instanceof com.reiko.signaltower.block.RadioTelegraphBlock;
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

    /** v0.49.0: "Charge: 45,000 / 100,000 (45%)", then what towers are using, or that nothing is. */
    private static List<Component> batteryLines(BlockState state, @Nullable CompoundTag data) {
        List<Component> lines = new ArrayList<>();
        if (data == null) {
            lines.add(Component.translatable(LANG + "battery_bars", state.getValue(com.reiko.signaltower.block.RadioBatteryBlock.CHARGE))
                    .withStyle(ChatFormatting.RED));
            return lines;
        }
        long charge = data.getInt("battery_charge");
        long capacity = data.getInt("battery_capacity");
        lines.add(Component.translatable(LANG + "battery_charge", com.reiko.signaltower.radio.PowerCost.number(charge),
                com.reiko.signaltower.radio.PowerCost.number(capacity), com.reiko.signaltower.block.RadioBatteryBlock.percent(charge, capacity))
                .withStyle(ChatFormatting.RED));
        double gain = data.getDouble("battery_gain");
        if (gain > 0) {
            lines.add(Component.translatable(LANG + "battery_solar", com.reiko.signaltower.radio.PowerCost.rateLabel(gain)).withStyle(ChatFormatting.YELLOW));
        }
        double use = data.getDouble("battery_use");
        double net = use - gain; // v0.50.0: what a Solar Panel puts in comes off
        lines.add(use <= 0 ? Component.translatable(LANG + "battery_idle").withStyle(ChatFormatting.GRAY)
                : net <= 0 ? Component.translatable(LANG + "battery_use_covered", com.reiko.signaltower.radio.PowerCost.rateLabel(use)).withStyle(ChatFormatting.GOLD)
                : Component.translatable(LANG + "battery_use", com.reiko.signaltower.radio.PowerCost.rateLabel(use),
                        com.reiko.signaltower.radio.PowerCost.durationLabel(charge / net)).withStyle(ChatFormatting.GOLD));
        return lines;
    }

    private static List<Component> blockLines(BlockState state, @Nullable CompoundTag data) {
        Block block = state.getBlock();
        if (block instanceof com.reiko.signaltower.block.StatusBoardBlock) {
            return boardLines(data);
        }
        if (block instanceof com.reiko.signaltower.block.RadioBatteryBlock) {
            return batteryLines(state, data);
        }
        if (block instanceof com.reiko.signaltower.block.SolarPanelBlock) {
            // v0.50.0
            List<Component> lines = new ArrayList<>();
            if (data != null) {
                double rate = data.getDouble("solar_rate");
                lines.add(!data.getBoolean("solar_battery") ? Component.translatable(LANG + "solar_no_battery").withStyle(ChatFormatting.GRAY)
                        : rate > 0 ? Component.translatable(LANG + "solar_charging", com.reiko.signaltower.radio.PowerCost.rateLabel(rate)).withStyle(ChatFormatting.YELLOW)
                        : Component.translatable(LANG + "solar_dark").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(LANG + "solar_light", data.getInt("solar_daylight")).withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (block instanceof com.reiko.signaltower.block.AviationBeaconBlock) {
            return beaconLines(state, data);
        }
        if (block instanceof com.reiko.signaltower.block.CableTapBlock || block instanceof com.reiko.signaltower.block.BundledCableBlock) {
            // v0.54.0
            List<Component> lines = new ArrayList<>();
            if (block instanceof com.reiko.signaltower.block.CableTapBlock) {
                lines.add(com.reiko.signaltower.block.CableTapBlock.describe(state).withStyle(ChatFormatting.AQUA));
            }
            if (data != null && data.contains("cable_channels")) {
                lines.add(Component.translatable(LANG + "cable_carries",
                        com.reiko.signaltower.block.CableTapBlock.channelsText(data.getIntArray("cable_channels"))).withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable(LANG + "cable_size", data.getInt("cable_count"), data.getInt("cable_devices"))
                        .withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (block instanceof com.reiko.signaltower.block.GuyAnchorBlock) {
            List<Component> lines = new ArrayList<>();
            if (data != null && data.contains("anchor_base")) {
                BlockPos base = BlockPos.of(data.getLong("anchor_base"));
                int braces = data.getInt("anchor_braces");
                int needed = data.getInt("anchor_needed");
                lines.add(Component.translatable(LANG + "anchor_braces", base.getX(), base.getY(), base.getZ(), data.getInt("anchor_height"))
                        .withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(LANG + (braces >= needed ? "anchor_enough" : "anchor_short"), braces, needed)
                        .withStyle(braces >= needed ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            } else if (data != null) {
                lines.add(Component.translatable(LANG + "anchor_none").withStyle(ChatFormatting.GOLD));
            }
            return lines;
        }
        List<Component> lines = new ArrayList<>();
        boolean hasData = data != null && data.contains("frequency");
        if (block instanceof RadioMastBlock) {
            if (data == null) {
                return lines;
            }
            if (data.getBoolean("rotator")) {
                // v0.52.0: the Dish Rotator's own lines, then the tower's.
                int power = data.getInt("rotator_power");
                lines.add(Component.translatable(LANG + "rotator_power", value(power),
                        com.reiko.signaltower.radio.AntennaTier.aimLabel(data.getInt("rotator_home"))).withStyle(ChatFormatting.GRAY));
                lines.add(!data.getBoolean("rotator_dish") ? Component.translatable(LANG + "rotator_no_dish").withStyle(ChatFormatting.GOLD)
                        : data.getBoolean("rotator_overruled") ? Component.translatable(LANG + "rotator_overruled").withStyle(ChatFormatting.GOLD)
                        : Component.translatable(LANG + "rotator_turns", com.reiko.signaltower.radio.AntennaTier.aimLabel(data.getInt("rotator_aim")))
                                .withStyle(ChatFormatting.AQUA));
            }
            if (!data.contains("top")) {
                lines.add(Component.translatable(LANG + "mast_no_top").withStyle(ChatFormatting.GRAY));
                return lines;
            }
            Component top = Component.translatable("block." + SignalTower.MOD_ID + ".radio_" + data.getString("top"));
            lines.add(Component.translatable(LANG + "mast_top", top, data.getInt("above")));
            if (data.contains("top_antenna")) {
                Component antennaName = Component.translatable("item." + SignalTower.MOD_ID + "." + data.getString("top_antenna") + "_antenna");
                String multiplier = com.reiko.signaltower.radio.AntennaTier.valueOf(data.getString("top_antenna").toUpperCase(java.util.Locale.ROOT)).multiplierText();
                lines.add((data.contains("top_aim")
                        ? Component.translatable(LANG + "antenna_dish", antennaName,
                                com.reiko.signaltower.radio.AntennaTier.aimLabel(data.getInt("top_aim")), multiplier)
                        : Component.translatable(LANG + "antenna", antennaName, multiplier)).withStyle(ChatFormatting.GOLD));
            }
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
        if (block instanceof com.reiko.signaltower.block.RadioTelegraphBlock) {
            if (hasData) {
                lines.add(frequencyLine(data));
                addNoteLine(lines, data, "note");
                if (data.contains("telegraph_text")) {
                    lines.add(Component.translatable(LANG + "telegraph_last", Component.literal(data.getString("telegraph_text")),
                            Component.literal(data.getString("telegraph_from")),
                            com.reiko.signaltower.radio.Telegraph.ago(data.getLong("telegraph_ago"))).withStyle(ChatFormatting.YELLOW));
                    lines.add(Component.translatable(LANG + "telegraph_count", data.getInt("telegraph_count")).withStyle(ChatFormatting.GRAY));
                } else if (data.getInt("telegraph_count") == 0) {
                    lines.add(Component.translatable(LANG + "telegraph_none").withStyle(ChatFormatting.GRAY));
                }
            }
            if (state.getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED)) {
                lines.add(Component.translatable(LANG + "telegraph_arrived").withStyle(ChatFormatting.GREEN));
            }
            if (hasData) {
                lines.add(Component.translatable(LANG + "reach", data.getInt("reach")));
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
        if (!(block instanceof RadioTransmitterBlock || block instanceof RadioRepeaterBlock || block instanceof RadioReceiverBlock
                || block instanceof com.reiko.signaltower.block.BundledReceiverBlock)) {
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
        if (block instanceof com.reiko.signaltower.block.RadioCounterReceiverBlock) {
            // v0.42.0: what it counts and what resets it, the count, its settings, then the output.
            if (hasData) {
                lines.add(Component.translatable(LANG + "counter_counts", number(data, "frequency", "name")));
                addNoteLine(lines, data, "note");
                if (data.contains("second")) {
                    lines.add(Component.translatable(LANG + "counter_reset", number(data, "second", "second_name")));
                    addNoteLine(lines, data, "second_note");
                }
                int target = data.getInt("counter_target");
                lines.add(data.getBoolean("counter_on")
                        ? Component.translatable(LANG + (data.getBoolean("counter_latch") ? "counter_reached_stay" : "counter_reached_pulse"),
                                value(target)).withStyle(ChatFormatting.GREEN)
                        : Component.translatable(LANG + "counter_count", value(data.getInt("count")), target));
                lines.add(Component.translatable(LANG + "counter_settings", RadioReceiverBlockEntity.counterText(target,
                        data.getBoolean("counter_latch"), data.getInt("counter_forget"))).withStyle(ChatFormatting.GRAY));
                long forget = data.getLong("forget_in");
                if (forget > 0) {
                    lines.add(Component.translatable(LANG + "counter_forgets_in", RadioReceiverBlockEntity.countdownLabel(forget))
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
            int power = state.getValue(RadioReceiverBlock.POWER);
            lines.add(power > 0 ? Component.translatable(LANG + "switched_on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(LANG + "switched_off").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                lines.add(Component.translatable(LANG + "towers", data.getInt("towers")));
                if (data.contains("deaf_b_to")) {
                    lines.add(Component.translatable(LANG + "counter_reset_deaf", Component.literal(data.getString("deaf_b_to")))
                            .withStyle(ChatFormatting.RED));
                }
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
        if (block instanceof com.reiko.signaltower.block.RadioJammerBlock) {
            // v0.46.0: the frequency it jams, how strongly (its redstone) and how far.
            if (hasData) {
                lines.add(Component.translatable(LANG + "jams", number(data, "frequency", "name")));
                addNoteLine(lines, data, "note");
            }
            boolean on = hasData ? data.getInt("power") > 0 : state.getValue(RadioTransmitterBlock.POWERED);
            if (!on && hasData && data.contains("struck")) {
                // Knocked out by lightning (v0.47.0): the "struck" line says why, not "no redstone".
            } else lines.add(!on ? Component.translatable(LANG + "jammer_off").withStyle(ChatFormatting.GRAY)
                    : hasData ? Component.translatable(LANG + "jamming", Component.literal(Integer.toString(data.getInt("power")))
                            .withStyle(ChatFormatting.LIGHT_PURPLE))
                    : Component.translatable(LANG + "jamming_on").withStyle(ChatFormatting.LIGHT_PURPLE));
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
        } else if (block instanceof com.reiko.signaltower.block.BundledReceiverBlock) {
            // v0.54.0: the frequencies it hears its bundle on, and what's on each color.
            if (hasData && data.contains("bundle_heard")) {
                lines.add(Component.translatable(LANG + "bundle_range",
                        com.reiko.signaltower.block.BundledTransmitterBlock.bundleRange(data.getInt("frequency"))).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(LANG + "bundle_hears",
                        com.reiko.signaltower.block.CableTapBlock.channelsText(data.getIntArray("bundle_heard"))).withStyle(ChatFormatting.YELLOW));
            }
        } else if (block instanceof RadioTransmitterBlock) {
            boolean on = hasData ? data.getInt("power") > 0 : state.getValue(RadioTransmitterBlock.POWERED);
            lines.add(!on ? Component.translatable(LANG + "sending_nothing").withStyle(ChatFormatting.GRAY)
                    : hasData ? Component.translatable(LANG + "sending", value(data.getInt("power")))
                    : Component.translatable(LANG + "broadcasting").withStyle(ChatFormatting.GREEN));
            if (hasData && data.contains("recorder_phase")) {
                recorderLines(lines, data);
            }
            if (hasData && data.contains("bundle_channels")) {
                // v0.54.0: the frequencies its bundle goes out on, and what's on each color.
                lines.add(Component.translatable(LANG + "bundle_range",
                        com.reiko.signaltower.block.BundledTransmitterBlock.bundleRange(data.getInt("frequency"))).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable(LANG + "bundle_sending",
                        com.reiko.signaltower.block.CableTapBlock.channelsText(data.getIntArray("bundle_channels"))).withStyle(ChatFormatting.YELLOW));
            }
            if (hasData && data.contains("station_mode")) {
                // v0.53.0: a weather station's reading, then a pause, and whether it announces.
                com.reiko.signaltower.radio.StationReading mode = com.reiko.signaltower.radio.StationReading.of(data.getInt("station_mode"));
                lines.add(data.getBoolean("station_offered")
                        ? Component.translatable(LANG + "station_reading", mode.label(), getComponent(data, "station_label"),
                                value(data.getInt("station_strength"))).withStyle(ChatFormatting.YELLOW)
                        : Component.translatable(LANG + "station_needs_reikos", mode.label()).withStyle(ChatFormatting.RED));
                lines.add(Component.translatable(LANG + "station_scale", mode.scale()).withStyle(ChatFormatting.GRAY));
                if (data.getBoolean("station_paused")) {
                    lines.add(Component.translatable(LANG + "timer_paused").withStyle(ChatFormatting.GOLD));
                }
                if (data.getBoolean("station_announce")) {
                    lines.add(Component.translatable(LANG + "station_announces").withStyle(ChatFormatting.AQUA));
                }
            }
            if (hasData && data.contains("timer_daily")) {
                // v0.42.0: a timer's schedule, then a pause or what happens next.
                boolean daily = data.getBoolean("timer_daily");
                lines.add(Component.translatable(LANG + "timer_schedule", com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.scheduleText(
                        daily, data.getInt("timer_interval"), data.getInt("timer_pulse"), data.getInt("timer_on"), data.getInt("timer_off")))
                        .withStyle(ChatFormatting.YELLOW));
                long change = data.getLong("change_in");
                if (data.getBoolean("timer_paused")) {
                    lines.add(Component.translatable(LANG + "timer_paused").withStyle(ChatFormatting.GOLD));
                } else if (!daily && change != 0) {
                    lines.add(Component.translatable(LANG + (change > 0 ? "timer_next_pulse" : "timer_pulse_ends"),
                            RadioReceiverBlockEntity.countdownLabel(Math.abs(change))).withStyle(ChatFormatting.YELLOW));
                } else if (daily && data.getInt("timer_on") != data.getInt("timer_off")) {
                    boolean clock = data.getBoolean("timer_clock");
                    lines.add(Component.translatable(LANG + (clock ? "timer_on_until" : "timer_turns_on_at"),
                            com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourLabel(
                                    clock ? data.getInt("timer_off") : data.getInt("timer_on"))).withStyle(ChatFormatting.YELLOW));
                }
            }
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
        } else if (block instanceof com.reiko.signaltower.block.RadioLampBlock) {
            // v0.45.0: its light (the strength it hears) and color, from the block state.
            int light = state.getValue(RadioReceiverBlock.POWER);
            lines.add(light > 0 ? Component.translatable(LANG + "lamp_light", value(light))
                    : Component.translatable(LANG + "lamp_dark").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(LANG + "lamp_color",
                    com.reiko.signaltower.block.RadioLampBlock.colorLabel(state.getValue(com.reiko.signaltower.block.RadioLampBlock.COLOR)))
                    .withStyle(ChatFormatting.YELLOW));
            if (hasData) {
                lines.add(Component.translatable(LANG + "towers", data.getInt("towers")));
            }
        } else if (block instanceof com.reiko.signaltower.block.RadioSpeakerBlock) {
            // v0.43.0: its output like a receiver's, then what it plays.
            int power = state.getValue(RadioReceiverBlock.POWER);
            lines.add(power > 0 ? Component.translatable(LANG + "output", value(power))
                    : Component.translatable(LANG + "output_off").withStyle(ChatFormatting.GRAY));
            if (hasData) {
                lines.add(Component.translatable(LANG + "speaker_plays", RadioReceiverBlockEntity.speakerText(data.getInt("speaker")))
                        .withStyle(ChatFormatting.YELLOW));
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

    /**
     * A status board's rows (v0.43.0) as "rows": per row its frequency, its name if it has one, the
     * strength heard there, and the owner if the board is deaf to it ("deaf_to").
     */
    private static CompoundTag readBoard(ServerLevel level, com.reiko.signaltower.blockentity.StatusBoardBlockEntity board) {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag rows = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < com.reiko.signaltower.blockentity.StatusBoardBlockEntity.ROWS; i++) {
            int frequency = board.getFrequency(i);
            if (frequency <= 0) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putInt("frequency", frequency);
            String name = FrequencyNames.nameOf(level, frequency);
            if (name != null) {
                row.putString("name", name);
            }
            row.putInt("strength", board.getStrength(i));
            if (!board.getMessage(i).isEmpty()) {
                row.putString("message", board.getMessage(i)); // v0.51.0
            }
            if (board.isDeaf(i)) {
                FrequencyClaims.Claim claim = FrequencyClaims.get(level.getServer()).get(frequency);
                row.putString("deaf_to", claim != null ? claim.ownerName() : "?");
            }
            rows.add(row);
        }
        tag.put("rows", rows);
        return tag;
    }

    /** A recorder's lines (v0.44.0): what it records from, its tape and mode, and what it's doing. */
    private static void recorderLines(List<Component> lines, CompoundTag data) {
        if (data.contains("second")) {
            lines.add(Component.translatable(LANG + "recorder_records_from", number(data, "second", "second_name")));
            addNoteLine(lines, data, "second_note");
        }
        int length = data.getInt("recorder_length");
        lines.add(Component.translatable(LANG + "recorder_tape",
                com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.tapeText(length, data.getInt("recorder_changes")),
                com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.modeText(data.getBoolean("recorder_loop")))
                .withStyle(ChatFormatting.YELLOW));
        int elapsed = data.getInt("recorder_elapsed");
        switch (com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.Phase.of(data.getInt("recorder_phase"))) {
            case WAITING -> lines.add(Component.translatable(LANG + "recorder_waiting").withStyle(ChatFormatting.GOLD));
            case RECORDING -> lines.add(Component.translatable(LANG + "recorder_recording",
                    com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.seconds(elapsed), data.getInt("recorder_taking"))
                    .withStyle(ChatFormatting.RED));
            case PLAYING -> lines.add(Component.translatable(LANG + "recorder_playing",
                    com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.seconds(length > 0 ? elapsed % length : 0),
                    com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.seconds(length)).withStyle(ChatFormatting.GREEN));
            default -> {
            }
        }
    }

    /**
     * An Aviation Beacon's data (v0.44.0): the tower it's on ("beacon_tower", its block's id path),
     * how far up that is, whether it's sending, and its frequency (and name).
     */
    private static CompoundTag readBeacon(ServerLevel level, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        BlockState state = level.getBlockState(pos);
        BlockPos tower = com.reiko.signaltower.block.AviationBeaconBlock.towerOf(level, pos, state);
        if (tower != null) {
            BlockState top = level.getBlockState(tower);
            tag.putString("beacon_tower", com.reiko.signaltower.radio.AviationLight.towerName(top));
            tag.putInt("beacon_above", Math.max(0, tower.getY() - pos.getY()));
            tag.putBoolean("beacon_sending", com.reiko.signaltower.radio.AviationLight.isSending(top));
            if (level.getBlockEntity(tower) instanceof RadioBlockEntity radio) {
                int frequency = radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter()
                        ? relay.getOutputFrequency() : radio.getFrequency();
                tag.putInt("beacon_frequency", frequency);
                addName(level, tag, "beacon_frequency", "beacon_name");
            }
        }
        tag.putBoolean("beacon_lit", com.reiko.signaltower.block.AviationBeaconBlock.isActive(level, pos, state));
        return tag;
    }

    /** An Aviation Beacon's lines: how it blinks (from its block state), whether it's on now, and its tower. */
    private static List<Component> beaconLines(BlockState state, @Nullable CompoundTag data) {
        List<Component> lines = new ArrayList<>();
        lines.add(com.reiko.signaltower.block.AviationBeaconBlock.describe(state).copy().withStyle(ChatFormatting.YELLOW));
        if (data == null) {
            return lines;
        }
        lines.add(data.getBoolean("beacon_lit") ? Component.translatable(LANG + "beacon_on").withStyle(ChatFormatting.GREEN)
                : Component.translatable(LANG + "beacon_off").withStyle(ChatFormatting.GRAY));
        if (!data.contains("beacon_tower")) {
            boolean sendingMode = state.getValue(com.reiko.signaltower.block.AviationBeaconBlock.WHEN)
                    == com.reiko.signaltower.radio.AviationLight.When.SENDING;
            lines.add(Component.translatable(LANG + (sendingMode ? "beacon_no_tower_dark" : "beacon_no_tower"))
                    .withStyle(sendingMode ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            return lines;
        }
        Component tower = Component.translatable("block." + SignalTower.MOD_ID + "." + data.getString("beacon_tower"));
        Component frequency = data.contains("beacon_frequency") ? number(data, "beacon_frequency", "beacon_name") : Component.literal("?");
        lines.add(Component.translatable(LANG + (data.getBoolean("beacon_sending") ? "beacon_tower_sending" : "beacon_tower_silent"),
                tower, frequency, data.getInt("beacon_above")).withStyle(data.getBoolean("beacon_sending") ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return lines;
    }

    /** A status board's lines: one per row, "2026 (Front Gate): 15", "off" or "encrypted by Dev". */
    private static List<Component> boardLines(@Nullable CompoundTag data) {
        List<Component> lines = new ArrayList<>();
        if (data == null) {
            return lines;
        }
        net.minecraft.nbt.ListTag rows = data.getList("rows", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (rows.isEmpty()) {
            lines.add(Component.translatable(LANG + "board_empty").withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            Component frequency = number(row, "frequency", "name");
            int strength = row.getInt("strength");
            lines.add(row.contains("deaf_to")
                    ? Component.translatable(LANG + "board_row_deaf", frequency, Component.literal(row.getString("deaf_to"))).withStyle(ChatFormatting.RED)
                    : strength > 0 ? Component.translatable(LANG + "board_row_on", frequency, value(strength))
                    : Component.translatable(LANG + "board_row_off", frequency).withStyle(ChatFormatting.GRAY));
            if (row.contains("message")) {
                lines.add(Component.translatable(LANG + "board_message", Component.literal(row.getString("message"))).withStyle(ChatFormatting.YELLOW));
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
