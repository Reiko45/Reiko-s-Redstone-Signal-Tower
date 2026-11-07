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

    /** The frequency a Radio Converter sends on, kept on the item when it's broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OUTPUT_FREQUENCY =
            DATA_COMPONENTS.registerComponentType("output_frequency", builder -> builder
                    .persistent(Codec.intRange(Frequency.MIN, Frequency.MAX))
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
    public static final DeferredItem<BlockItem> RADIO_REPEATER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_repeater", RADIO_REPEATER);
    public static final DeferredItem<BlockItem> RADIO_CONVERTER_ITEM =
            ITEMS.registerSimpleBlockItem("radio_converter", RADIO_CONVERTER);
    public static final DeferredItem<BlockItem> COMPACT_RADIO_RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("compact_radio_receiver", COMPACT_RADIO_RECEIVER);
    public static final DeferredItem<BlockItem> COMPACT_RADIO_TRANSMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("compact_radio_transmitter", COMPACT_RADIO_TRANSMITTER);
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
    public static final DeferredItem<SignalScannerItem> SIGNAL_SCANNER =
            ITEMS.register("signal_scanner", () -> new SignalScannerItem(new Item.Properties().stacksTo(1)));

    // ---- Block entities ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioTransmitterBlockEntity>> RADIO_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("radio_transmitter",
                    () -> BlockEntityType.Builder.of(RadioTransmitterBlockEntity::new, RADIO_TRANSMITTER.get(), COMPACT_RADIO_TRANSMITTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioReceiverBlockEntity>> RADIO_RECEIVER_BE =
            BLOCK_ENTITIES.register("radio_receiver",
                    () -> BlockEntityType.Builder.of(RadioReceiverBlockEntity::new,
                            RADIO_RECEIVER.get(), COMPACT_RADIO_RECEIVER.get(), RADIO_TOGGLE_RECEIVER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioRepeaterBlockEntity>> RADIO_REPEATER_BE =
            BLOCK_ENTITIES.register("radio_repeater",
                    () -> BlockEntityType.Builder.of(RadioRepeaterBlockEntity::new, RADIO_REPEATER.get(), RADIO_CONVERTER.get()).build(null));

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
