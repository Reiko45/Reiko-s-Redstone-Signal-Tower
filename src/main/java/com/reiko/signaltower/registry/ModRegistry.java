package com.reiko.signaltower.registry;

import com.mojang.serialization.Codec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CodeKeypadBlock;
import com.reiko.signaltower.block.CompactRadioReceiverBlock;
import com.reiko.signaltower.block.CompactRadioTransmitterBlock;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioToggleReceiverBlock;
import com.reiko.signaltower.block.RadioInvertedReceiverBlock;
import com.reiko.signaltower.block.RadioMotionSensorBlock;
import com.reiko.signaltower.blockentity.MotionSensorBlockEntity;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.block.WallRedstoneBlock;
import com.reiko.signaltower.blockentity.CodeKeypadBlockEntity;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.ScannerTarget;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.item.SignalMeterItem;
import com.reiko.signaltower.block.RadioLogicReceiverBlock;
import com.reiko.signaltower.block.RadioDelayReceiverBlock;
import com.reiko.signaltower.item.TunerPresets;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.item.RemoteLight;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.Frequency;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SignalTower.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SignalTower.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SignalTower.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SignalTower.MOD_ID);

    // ---- Data components ----

    /** The frequency stored on a Frequency Tuner, or on a transmitter/receiver item that was broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FREQUENCY =
            DATA_COMPONENTS.registerComponentType("frequency", builder -> builder
                    .persistent(Codec.intRange(Frequency.MIN, Frequency.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** v0.49.0: the charge in a Radio Battery, kept on the item when it's broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY_CHARGE =
            DATA_COMPONENTS.registerComponentType("battery_charge", builder -> builder
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** v0.49.0: a Coverage Map's zoom, 0 (1:1) to 4 (1:16), like a vanilla map's scale. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COVERAGE_ZOOM =
            DATA_COMPONENTS.registerComponentType("coverage_zoom", builder -> builder
                    .persistent(Codec.intRange(0, 4))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The frequency a Radio Converter sends on, kept on the item when it's broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OUTPUT_FREQUENCY =
            DATA_COMPONENTS.registerComponentType("output_frequency", builder -> builder
                    .persistent(Codec.intRange(Frequency.MIN, Frequency.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Converter's signal gate, kept on the item (v0.41.0): mode × 16 + threshold (mode 1 at least, 2 below; threshold 1-15). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CONVERTER_GATE =
            DATA_COMPONENTS.registerComponentType("converter_gate", builder -> builder
                    .persistent(Codec.intRange(0, 47))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Delay Receiver's delay and hold, in ticks, kept on the item (v0.41.0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DELAY_TICKS =
            DATA_COMPONENTS.registerComponentType("delay_ticks", builder -> builder
                    .persistent(Codec.intRange(0, 1200))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HOLD_TICKS =
            DATA_COMPONENTS.registerComponentType("hold_ticks", builder -> builder
                    .persistent(Codec.intRange(0, 1200))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Weather Station's reading (by name) and whether it announces, kept on the item (v0.53.0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STATION_MODE =
            DATA_COMPONENTS.registerComponentType("station_mode", builder -> builder
                    .persistent(Codec.string(1, 32))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> STATION_ANNOUNCE =
            DATA_COMPONENTS.registerComponentType("station_announce", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** A Radio Timer Transmitter's schedule, kept on the item (v0.42.0): mode (0 interval, 1 daily), interval and pulse in ticks, and on hour × 24 + off hour. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIMER_MODE =
            DATA_COMPONENTS.registerComponentType("timer_mode", builder -> builder
                    .persistent(Codec.intRange(0, 1))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIMER_INTERVAL =
            DATA_COMPONENTS.registerComponentType("timer_interval", builder -> builder
                    .persistent(Codec.intRange(1, 24000))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIMER_PULSE =
            DATA_COMPONENTS.registerComponentType("timer_pulse", builder -> builder
                    .persistent(Codec.intRange(1, 200))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIMER_HOURS =
            DATA_COMPONENTS.registerComponentType("timer_hours", builder -> builder
                    .persistent(Codec.intRange(0, 575))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Counter Receiver's settings, kept on the item (v0.42.0): its target, whether it stays on, when it forgets (ticks) and its reset frequency (0: none). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COUNTER_TARGET =
            DATA_COMPONENTS.registerComponentType("counter_target", builder -> builder
                    .persistent(Codec.intRange(1, 64))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COUNTER_LATCH =
            DATA_COMPONENTS.registerComponentType("counter_latch", builder -> builder
                    .persistent(Codec.intRange(0, 1))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COUNTER_FORGET =
            DATA_COMPONENTS.registerComponentType("counter_forget", builder -> builder
                    .persistent(Codec.intRange(0, 1200))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RESET_FREQUENCY =
            DATA_COMPONENTS.registerComponentType("reset_frequency", builder -> builder
                    .persistent(Codec.intRange(0, Frequency.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Speaker's settings, kept on the item (v0.43.0): sound + 16 × volume + 64 × repeat + 128 × note. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPEAKER =
            DATA_COMPONENTS.registerComponentType("speaker", builder -> builder
                    .persistent(Codec.intRange(0, 4095))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Status Board's rows, kept on the item (v0.43.0): up to four frequencies, 0 for an empty row. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<Integer>>> BOARD_FREQUENCIES =
            DATA_COMPONENTS.registerComponentType("board_frequencies", builder -> builder
                    .persistent(Codec.intRange(0, Frequency.MAX).listOf(0, 4))
                    .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4))));

    /** A Radio Signal Recorder's recording and settings, kept on the item when it's broken (v0.44.0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.reiko.signaltower.radio.RecorderTape>> RECORDER_TAPE =
            DATA_COMPONENTS.registerComponentType("recorder_tape", builder -> builder
                    .persistent(com.reiko.signaltower.radio.RecorderTape.CODEC)
                    .networkSynchronized(com.reiko.signaltower.radio.RecorderTape.STREAM_CODEC));

    /** A Radio Logic Receiver's second frequency, kept on the item when it's broken (v0.40.0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SECOND_FREQUENCY =
            DATA_COMPONENTS.registerComponentType("second_frequency", builder -> builder
                    .persistent(Codec.intRange(Frequency.MIN, Frequency.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Radio Logic Receiver's mode (0 AND, 1 OR, 2 XOR), kept on the item when it's broken (v0.40.0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LOGIC_MODE =
            DATA_COMPONENTS.registerComponentType("logic_mode", builder -> builder
                    .persistent(Codec.intRange(0, 2))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Whether a Remote Control is in toggle mode (true) or pulse mode (false, the default). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> REMOTE_TOGGLE_MODE =
            DATA_COMPONENTS.registerComponentType("remote_toggle_mode", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** How long a Remote Control's pulse lasts, in ticks. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REMOTE_PULSE_TICKS =
            DATA_COMPONENTS.registerComponentType("remote_pulse_ticks", builder -> builder
                    .persistent(Codec.intRange(PulseLength.MIN_TICKS, PulseLength.MAX_TICKS))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A remote's status light after a press. Synced to the client so the model can change; never saved. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RemoteLight>> REMOTE_LIGHT =
            DATA_COMPONENTS.registerComponentType("remote_light", builder -> builder
                    .networkSynchronized(RemoteLight.STREAM_CODEC));

    /** The four buttons of a Multi-Button Remote, and which one is selected. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RemoteButtons>> REMOTE_BUTTONS =
            DATA_COMPONENTS.registerComponentType("remote_buttons", builder -> builder
                    .persistent(RemoteButtons.CODEC)
                    .networkSynchronized(RemoteButtons.STREAM_CODEC));

    /** The signal strength a Radio Pager heard last (absent means quiet). Saved, so reloading doesn't re-alert. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PAGER_SIGNAL =
            DATA_COMPONENTS.registerComponentType("pager_signal", builder -> builder
                    .persistent(Codec.intRange(0, 15))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Beeps left in a Radio Pager's current alert. Never saved. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PAGER_BEEPS =
            DATA_COMPONENTS.registerComponentType("pager_beeps", builder -> builder
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** True if a Radio Pager only shows messages, without beeping. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PAGER_SILENT =
            DATA_COMPONENTS.registerComponentType("pager_silent", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** False if a Radio Pager shouldn't post its alerts in chat (absent means it does). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PAGER_CHAT_LOG =
            DATA_COMPONENTS.registerComponentType("pager_chat_log", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** The game time a Radio Pager's owner logged out, for the catch-up at the next login. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> PAGER_AWAY_SINCE =
            DATA_COMPONENTS.registerComponentType("pager_away_since", builder -> builder
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** The transmitter a Signal Scanner is tracking with its on-screen arrow. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ScannerTarget>> SCANNER_TARGET =
            DATA_COMPONENTS.registerComponentType("scanner_target", builder -> builder
                    .persistent(ScannerTarget.CODEC)
                    .networkSynchronized(ScannerTarget.STREAM_CODEC));

    /** The Frequency Tuner's favorite frequencies. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TunerPresets>> TUNER_PRESETS =
            DATA_COMPONENTS.registerComponentType("tuner_presets", builder -> builder
                    .persistent(TunerPresets.CODEC)
                    .networkSynchronized(TunerPresets.STREAM_CODEC));

    // ---- Blocks ----

    public static final DeferredBlock<RadioTransmitterBlock> RADIO_TRANSMITTER = BLOCKS.register("radio_transmitter",
            () -> new RadioTransmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));

    public static final DeferredBlock<RadioReceiverBlock> RADIO_RECEIVER = BLOCKS.register("radio_receiver",
            () -> new RadioReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** A receiver that flips on and off each time its frequency turns on. */
    public static final DeferredBlock<RadioToggleReceiverBlock> RADIO_TOGGLE_RECEIVER = BLOCKS.register("radio_toggle_receiver",
            () -> new RadioToggleReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** A receiver that's on while its frequency is silent and off while it's on (v0.39.0). */
    public static final DeferredBlock<RadioInvertedReceiverBlock> RADIO_INVERTED_RECEIVER = BLOCKS.register("radio_inverted_receiver",
            () -> new RadioInvertedReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** A receiver on two frequencies that outputs on AND, OR or XOR (v0.40.0). */
    public static final DeferredBlock<RadioLogicReceiverBlock> RADIO_LOGIC_RECEIVER = BLOCKS.register("radio_logic_receiver",
            () -> new RadioLogicReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** A receiver that counts its frequency's turn-ons and turns on at a set number (v0.42.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioCounterReceiverBlock> RADIO_COUNTER_RECEIVER = BLOCKS.register("radio_counter_receiver",
            () -> new com.reiko.signaltower.block.RadioCounterReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** A receiver that plays a sound when its frequency turns on (v0.43.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioSpeakerBlock> RADIO_SPEAKER = BLOCKS.register("radio_speaker",
            () -> new com.reiko.signaltower.block.RadioSpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** A wall panel showing up to four frequencies (v0.43.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.StatusBoardBlock> RADIO_STATUS_BOARD = BLOCKS.register("radio_status_board",
            () -> new com.reiko.signaltower.block.StatusBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 3)));

    /** A lamp with a receiver built in (v0.45.0): light level = the strength it hears, in a dye color. */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioLampBlock> RADIO_LAMP = BLOCKS.register("radio_lamp",
            () -> new com.reiko.signaltower.block.RadioLampBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(com.reiko.signaltower.block.RadioLampBlock::lightOf)));

    /** v0.49.0: powers the towers next to it (or next to their mast) while they send. */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioBatteryBlock> RADIO_BATTERY = BLOCKS.register("radio_battery",
            () -> new com.reiko.signaltower.block.RadioBatteryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(com.reiko.signaltower.block.RadioBatteryBlock.CHARGE) > 0 ? 3 : 0)));

    /** v0.50.0: charges the Radio Battery under it by daylight. */
    public static final DeferredBlock<com.reiko.signaltower.block.SolarPanelBlock> SOLAR_PANEL = BLOCKS.register("solar_panel",
            () -> new com.reiko.signaltower.block.SolarPanelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** v0.61.0: sends a Satellite up to orbit on eight Firework Rockets and a redstone pulse. */
    public static final DeferredBlock<com.reiko.signaltower.block.LaunchPadBlock> LAUNCH_PAD = BLOCKS.register("launch_pad",
            () -> new com.reiko.signaltower.block.LaunchPadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F, 9.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** v0.51.0: a desk to type short messages onto a frequency. */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioTelegraphBlock> RADIO_TELEGRAPH = BLOCKS.register("radio_telegraph",
            () -> new com.reiko.signaltower.block.RadioTelegraphBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED) ? 7 : 0)));

    /** v0.63.0: prints the messages on its frequency into a Book and Quill. */
    public static final DeferredBlock<com.reiko.signaltower.block.TickerPrinterBlock> TICKER_PRINTER = BLOCKS.register("ticker_printer",
            () -> new com.reiko.signaltower.block.TickerPrinterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** v0.62.0: a straight key that sends Morse on its frequency. */
    public static final DeferredBlock<com.reiko.signaltower.block.MorseKeyBlock> MORSE_KEY = BLOCKS.register("morse_key",
            () -> new com.reiko.signaltower.block.MorseKeyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    /** A transmitter that records a frequency and plays it back (v0.44.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioSignalRecorderBlock> RADIO_SIGNAL_RECORDER = BLOCKS.register("radio_signal_recorder",
            () -> new com.reiko.signaltower.block.RadioSignalRecorderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));

    /** A tower top that drowns out one frequency around it while powered (v0.46.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioJammerBlock> RADIO_JAMMER = BLOCKS.register("radio_jammer",
            () -> new com.reiko.signaltower.block.RadioJammerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));

    /** A warning light for towers (v0.44.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.AviationBeaconBlock> AVIATION_BEACON = BLOCKS.register("aviation_beacon",
            () -> new com.reiko.signaltower.block.AviationBeaconBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    /** A transmitter with a built-in clock (v0.42.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioTimerTransmitterBlock> RADIO_TIMER_TRANSMITTER = BLOCKS.register("radio_timer_transmitter",
            () -> new com.reiko.signaltower.block.RadioTimerTransmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));

    /** v0.54.0: Bundled Cable, Cable Tap, and the bundled radio pair. */
    public static final DeferredBlock<com.reiko.signaltower.block.BundledCableBlock> BUNDLED_CABLE = BLOCKS.register("bundled_cable",
            () -> new com.reiko.signaltower.block.BundledCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.3F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));
    public static final DeferredBlock<com.reiko.signaltower.block.CableTapBlock> CABLE_TAP = BLOCKS.register("cable_tap",
            () -> new com.reiko.signaltower.block.CableTapBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final DeferredBlock<com.reiko.signaltower.block.BundledTransmitterBlock> BUNDLED_TRANSMITTER = BLOCKS.register("bundled_transmitter",
            () -> new com.reiko.signaltower.block.BundledTransmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));
    public static final DeferredBlock<com.reiko.signaltower.block.BundledReceiverBlock> BUNDLED_RECEIVER = BLOCKS.register("bundled_receiver",
            () -> new com.reiko.signaltower.block.BundledReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.reiko.signaltower.block.BundledReceiverBlock.LIT) ? 5 : 0)));

    /** v0.55.0: a grating to stand on up a tower, put in all round a mast at once. */
    public static final DeferredBlock<com.reiko.signaltower.block.TowerPlatformBlock> TOWER_PLATFORM = BLOCKS.register("tower_platform",
            () -> new com.reiko.signaltower.block.TowerPlatformBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    /** v0.54.0: braces a tower's mast against storms. */
    public static final DeferredBlock<com.reiko.signaltower.block.GuyAnchorBlock> GUY_ANCHOR = BLOCKS.register("guy_anchor",
            () -> new com.reiko.signaltower.block.GuyAnchorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    /** A transmitter that broadcasts the weather, the time or the moon where it stands (v0.53.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioWeatherStationBlock> RADIO_WEATHER_STATION = BLOCKS.register("radio_weather_station",
            () -> new com.reiko.signaltower.block.RadioWeatherStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));

    /** A transmitter with a record player, broadcasting the music disc in it (v0.60.0). */
    public static final DeferredBlock<com.reiko.signaltower.block.RadioJukeboxBlock> RADIO_JUKEBOX = BLOCKS.register("radio_jukebox",
            () -> new com.reiko.signaltower.block.RadioJukeboxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioTransmitterBlock.POWERED) ? 7 : 0)));

    /** A receiver with a delay and a hold (v0.41.0). */
    public static final DeferredBlock<RadioDelayReceiverBlock> RADIO_DELAY_RECEIVER = BLOCKS.register("radio_delay_receiver",
            () -> new RadioDelayReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 5 : 0)));

    /** Listens for its frequency and broadcasts it again from its own height. */
    public static final DeferredBlock<RadioRepeaterBlock> RADIO_REPEATER = BLOCKS.register("radio_repeater",
            () -> new RadioRepeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioRepeaterBlock.LIT) ? 7 : 0)));

    /** A repeater that sends what it hears on another frequency. */
    public static final DeferredBlock<RadioConverterBlock> RADIO_CONVERTER = BLOCKS.register("radio_converter",
            () -> new RadioConverterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RadioRepeaterBlock.LIT) ? 7 : 0)));

    /** Button-sized receiver. Breaks quickly like a button and gets knocked off by pistons. */
    public static final DeferredBlock<CompactRadioReceiverBlock> COMPACT_RADIO_RECEIVER = BLOCKS.register("compact_radio_receiver",
            () -> new CompactRadioReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(RadioReceiverBlock.POWER) > 0 ? 3 : 0)));

    /** Button-sized transmitter with a short fixed reach (v0.38.0). Breaks quickly like a button and gets knocked off by pistons. */
    public static final DeferredBlock<CompactRadioTransmitterBlock> COMPACT_RADIO_TRANSMITTER = BLOCKS.register("compact_radio_transmitter",
            () -> new CompactRadioTransmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(CompactRadioTransmitterBlock.POWERED) ? 3 : 0)));

    /** A button-sized eye that sends while it sees a player nearby (v0.39.0). Knocked off by pistons, like a button. */
    public static final DeferredBlock<RadioMotionSensorBlock> RADIO_MOTION_SENSOR = BLOCKS.register("radio_motion_sensor",
            () -> new RadioMotionSensorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(CompactRadioTransmitterBlock.POWERED) ? 3 : 0)));

    /** A thin keypad panel: the right code sends a pulse on its frequency (v0.37.0). Knocked off by pistons, like a button. */
    public static final DeferredBlock<CodeKeypadBlock> CODE_KEYPAD = BLOCKS.register("code_keypad",
            () -> new CodeKeypadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(CodeKeypadBlock.POWERED) ? 4 : 0)));

    public static final DeferredBlock<RadioMastBlock> RADIO_MAST = BLOCKS.register("radio_mast",
            () -> new RadioMastBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.CHAIN)
                    .noOcclusion()));

    /** v0.52.0: a mast segment whose redstone turns the Dish Antenna on top. */
    public static final DeferredBlock<com.reiko.signaltower.block.DishRotatorBlock> DISH_ROTATOR = BLOCKS.register("dish_rotator",
            () -> new com.reiko.signaltower.block.DishRotatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Redstone dust that sticks to walls. Breaks instantly and gets knocked off by pistons, like dust. */
    public static final DeferredBlock<WallRedstoneBlock> WALL_REDSTONE = BLOCKS.register("wall_redstone",
            () -> new WallRedstoneBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    // ---- Items ----

    public static final DeferredItem<BlockItem> RADIO_TRANSMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_transmitter", RADIO_TRANSMITTER);
    public static final DeferredItem<BlockItem> RADIO_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_receiver", RADIO_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_TOGGLE_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_toggle_receiver", RADIO_TOGGLE_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_INVERTED_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_inverted_receiver", RADIO_INVERTED_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_LOGIC_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_logic_receiver", RADIO_LOGIC_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_DELAY_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_delay_receiver", RADIO_DELAY_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_SPEAKER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_speaker", RADIO_SPEAKER);
    public static final DeferredItem<BlockItem> RADIO_STATUS_BOARD_ITEM =
            ITEMS.registerSimpleBlockItem("radio_status_board", RADIO_STATUS_BOARD);
    public static final DeferredItem<BlockItem> RADIO_COUNTER_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_counter_receiver", RADIO_COUNTER_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_TIMER_TRANSMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_timer_transmitter", RADIO_TIMER_TRANSMITTER);
    public static final DeferredItem<BlockItem> GUY_ANCHOR_ITEM = ITEMS.registerSimpleBlockItem("guy_anchor", GUY_ANCHOR);
    public static final DeferredItem<BlockItem> TOWER_PLATFORM_ITEM = ITEMS.registerSimpleBlockItem("tower_platform", TOWER_PLATFORM);
    public static final DeferredItem<BlockItem> BUNDLED_CABLE_ITEM = ITEMS.registerSimpleBlockItem("bundled_cable", BUNDLED_CABLE);
    public static final DeferredItem<BlockItem> CABLE_TAP_ITEM = ITEMS.registerSimpleBlockItem("cable_tap", CABLE_TAP);
    public static final DeferredItem<BlockItem> BUNDLED_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem("bundled_transmitter", BUNDLED_TRANSMITTER);
    public static final DeferredItem<BlockItem> BUNDLED_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("bundled_receiver", BUNDLED_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_WEATHER_STATION_ITEM =
            ITEMS.registerSimpleBlockItem("radio_weather_station", RADIO_WEATHER_STATION);
    public static final DeferredItem<BlockItem> RADIO_SIGNAL_RECORDER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_signal_recorder", RADIO_SIGNAL_RECORDER);
    public static final DeferredItem<BlockItem> RADIO_JUKEBOX_ITEM =
            ITEMS.registerSimpleBlockItem("radio_jukebox", RADIO_JUKEBOX); // v0.60.0
    public static final DeferredItem<BlockItem> RADIO_JAMMER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_jammer", RADIO_JAMMER);
    public static final DeferredItem<BlockItem> AVIATION_BEACON_ITEM =
            ITEMS.registerSimpleBlockItem("aviation_beacon", AVIATION_BEACON);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM =
            ITEMS.registerSimpleBlockItem("solar_panel", SOLAR_PANEL);
    public static final DeferredItem<BlockItem> DISH_ROTATOR_ITEM =
            ITEMS.registerSimpleBlockItem("dish_rotator", DISH_ROTATOR);
    public static final DeferredItem<BlockItem> RADIO_TELEGRAPH_ITEM =
            ITEMS.registerSimpleBlockItem("radio_telegraph", RADIO_TELEGRAPH);
    public static final DeferredItem<BlockItem> MORSE_KEY_ITEM =
            ITEMS.registerSimpleBlockItem("morse_key", MORSE_KEY); // v0.62.0
    public static final DeferredItem<BlockItem> TICKER_PRINTER_ITEM =
            ITEMS.registerSimpleBlockItem("ticker_printer", TICKER_PRINTER); // v0.63.0
    /** v0.50.0: on a transmitter, also sends into another dimension. */
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> ENDER_ANTENNA = ITEMS.register("ender_antenna",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.ENDER, new Item.Properties()));
    /** v0.61.0: what goes up from a Launch Pad; and the dish that talks to it. */
    public static final DeferredItem<BlockItem> LAUNCH_PAD_ITEM =
            ITEMS.registerSimpleBlockItem("launch_pad", LAUNCH_PAD);
    public static final DeferredItem<com.reiko.signaltower.item.SatelliteItem> SATELLITE = ITEMS.register("satellite",
            () -> new com.reiko.signaltower.item.SatelliteItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> SATELLITE_DISH = ITEMS.register("satellite_dish",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.SATELLITE, new Item.Properties()));
    public static final DeferredItem<BlockItem> RADIO_BATTERY_ITEM =
            ITEMS.registerSimpleBlockItem("radio_battery", RADIO_BATTERY);
    /** v0.49.0: a map that paints where a frequency can be heard. */
    public static final DeferredItem<com.reiko.signaltower.item.CoverageMapItem> COVERAGE_MAP = ITEMS.register("coverage_map",
            () -> new com.reiko.signaltower.item.CoverageMapItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> RADIO_LAMP_ITEM =
            ITEMS.registerSimpleBlockItem("radio_lamp", RADIO_LAMP);
    /** Antenna upgrades for tower tops (v0.45.0). */
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> IRON_ANTENNA = ITEMS.register("iron_antenna",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.IRON, new Item.Properties()));
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> GOLD_ANTENNA = ITEMS.register("gold_antenna",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.GOLD, new Item.Properties()));
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> DIAMOND_ANTENNA = ITEMS.register("diamond_antenna",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.DIAMOND, new Item.Properties()));
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> NETHERITE_ANTENNA = ITEMS.register("netherite_antenna",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.NETHERITE,
                    new Item.Properties().fireResistant()));
    /** v0.48.0: sends much farther, but only in the direction it's aimed. */
    public static final DeferredItem<com.reiko.signaltower.item.AntennaItem> DISH_ANTENNA = ITEMS.register("dish_antenna",
            () -> new com.reiko.signaltower.item.AntennaItem(com.reiko.signaltower.radio.AntennaTier.DISH, new Item.Properties()));
    public static final DeferredItem<BlockItem> RADIO_REPEATER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_repeater", RADIO_REPEATER);
    public static final DeferredItem<BlockItem> RADIO_CONVERTER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_converter", RADIO_CONVERTER);
    public static final DeferredItem<BlockItem> COMPACT_RADIO_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("compact_radio_receiver", COMPACT_RADIO_RECEIVER);
    public static final DeferredItem<BlockItem> COMPACT_RADIO_TRANSMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("compact_radio_transmitter", COMPACT_RADIO_TRANSMITTER);
    public static final DeferredItem<BlockItem> RADIO_MOTION_SENSOR_ITEM =
            ITEMS.registerSimpleBlockItem("radio_motion_sensor", RADIO_MOTION_SENSOR);
    public static final DeferredItem<BlockItem> CODE_KEYPAD_ITEM =
            ITEMS.registerSimpleBlockItem("code_keypad", CODE_KEYPAD);
    public static final DeferredItem<BlockItem> RADIO_MAST_ITEM =
            ITEMS.registerSimpleBlockItem("radio_mast", RADIO_MAST);
    public static final DeferredItem<BlockItem> WALL_REDSTONE_ITEM =
            ITEMS.registerSimpleBlockItem("wall_redstone", WALL_REDSTONE);
    public static final DeferredItem<FrequencyTunerItem> FREQUENCY_TUNER =
            ITEMS.register("frequency_tuner", () -> new FrequencyTunerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RemoteControlItem> REMOTE_CONTROL =
            ITEMS.register("remote_control", () -> new RemoteControlItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<MultiRemoteItem> MULTI_REMOTE =
            ITEMS.register("multi_remote", () -> new MultiRemoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PagerItem> PAGER =
            ITEMS.register("pager", () -> new PagerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SignalMeterItem> SIGNAL_METER =
            ITEMS.register("signal_meter", () -> new SignalMeterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SignalScannerItem> SIGNAL_SCANNER =
            ITEMS.register("signal_scanner", () -> new SignalScannerItem(new Item.Properties().stacksTo(1)));

    // ---- Block entities ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioTransmitterBlockEntity>> RADIO_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("radio_transmitter",
                    () -> BlockEntityType.Builder.of(RadioTransmitterBlockEntity::new, RADIO_TRANSMITTER.get(), COMPACT_RADIO_TRANSMITTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioReceiverBlockEntity>> RADIO_RECEIVER_BE =
            BLOCK_ENTITIES.register("radio_receiver",
                    () -> BlockEntityType.Builder.of(RadioReceiverBlockEntity::new,
                            RADIO_RECEIVER.get(), COMPACT_RADIO_RECEIVER.get(), RADIO_TOGGLE_RECEIVER.get(),
                            RADIO_INVERTED_RECEIVER.get(), RADIO_LOGIC_RECEIVER.get(), RADIO_DELAY_RECEIVER.get(),
                            RADIO_COUNTER_RECEIVER.get(), RADIO_SPEAKER.get(), RADIO_LAMP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioRepeaterBlockEntity>> RADIO_REPEATER_BE =
            BLOCK_ENTITIES.register("radio_repeater",
                    () -> BlockEntityType.Builder.of(RadioRepeaterBlockEntity::new, RADIO_REPEATER.get(), RADIO_CONVERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotionSensorBlockEntity>> MOTION_SENSOR_BE =
            BLOCK_ENTITIES.register("radio_motion_sensor",
                    () -> BlockEntityType.Builder.of(MotionSensorBlockEntity::new, RADIO_MOTION_SENSOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity>> TIMER_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("radio_timer_transmitter",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity::new, RADIO_TIMER_TRANSMITTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.GuyAnchorBlockEntity>> GUY_ANCHOR_BE =
            BLOCK_ENTITIES.register("guy_anchor",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.GuyAnchorBlockEntity::new, GUY_ANCHOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.BundledTransmitterBlockEntity>> BUNDLED_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("bundled_transmitter",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.BundledTransmitterBlockEntity::new, BUNDLED_TRANSMITTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.BundledReceiverBlockEntity>> BUNDLED_RECEIVER_BE =
            BLOCK_ENTITIES.register("bundled_receiver",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.BundledReceiverBlockEntity::new, BUNDLED_RECEIVER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.WeatherStationBlockEntity>> WEATHER_STATION_BE =
            BLOCK_ENTITIES.register("radio_weather_station",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.WeatherStationBlockEntity::new, RADIO_WEATHER_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.SignalRecorderBlockEntity>> SIGNAL_RECORDER_BE =
            BLOCK_ENTITIES.register("radio_signal_recorder",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.SignalRecorderBlockEntity::new, RADIO_SIGNAL_RECORDER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.RadioJukeboxBlockEntity>> RADIO_JUKEBOX_BE =
            BLOCK_ENTITIES.register("radio_jukebox",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.RadioJukeboxBlockEntity::new, RADIO_JUKEBOX.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.RadioJammerBlockEntity>> RADIO_JAMMER_BE =
            BLOCK_ENTITIES.register("radio_jammer",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.RadioJammerBlockEntity::new, RADIO_JAMMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.AviationBeaconBlockEntity>> AVIATION_BEACON_BE =
            BLOCK_ENTITIES.register("aviation_beacon",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.AviationBeaconBlockEntity::new, AVIATION_BEACON.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.StatusBoardBlockEntity>> STATUS_BOARD_BE =
            BLOCK_ENTITIES.register("radio_status_board",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.StatusBoardBlockEntity::new, RADIO_STATUS_BOARD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.RadioBatteryBlockEntity>> RADIO_BATTERY_BE =
            BLOCK_ENTITIES.register("radio_battery",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.RadioBatteryBlockEntity::new, RADIO_BATTERY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.SolarPanelBlockEntity>> SOLAR_PANEL_BE =
            BLOCK_ENTITIES.register("solar_panel",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.SolarPanelBlockEntity::new, SOLAR_PANEL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.LaunchPadBlockEntity>> LAUNCH_PAD_BE =
            BLOCK_ENTITIES.register("launch_pad",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.LaunchPadBlockEntity::new, LAUNCH_PAD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.TickerPrinterBlockEntity>> TICKER_PRINTER_BE =
            BLOCK_ENTITIES.register("ticker_printer",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.TickerPrinterBlockEntity::new, TICKER_PRINTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.MorseKeyBlockEntity>> MORSE_KEY_BE =
            BLOCK_ENTITIES.register("morse_key",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.MorseKeyBlockEntity::new, MORSE_KEY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.reiko.signaltower.blockentity.TelegraphBlockEntity>> TELEGRAPH_BE =
            BLOCK_ENTITIES.register("radio_telegraph",
                    () -> BlockEntityType.Builder.of(com.reiko.signaltower.blockentity.TelegraphBlockEntity::new, RADIO_TELEGRAPH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CodeKeypadBlockEntity>> CODE_KEYPAD_BE =
            BLOCK_ENTITIES.register("code_keypad",
                    () -> BlockEntityType.Builder.of(CodeKeypadBlockEntity::new, CODE_KEYPAD.get()).build(null));

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    private ModRegistry() {}
}
