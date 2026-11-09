package com.reiko.signaltower.gametest;

import com.mojang.authlib.GameProfile;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CompactRadioReceiverBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.WallRedstoneBlock;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RadioFavorites;
import com.reiko.signaltower.item.ScannerTarget;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.item.TunerPresets;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.item.RemoteLight;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Automated in-game tests. Run them with {@code ./gradlew runGameTestServer}.
 * They only load in dev runs, where {@code neoforge.enabledGameTestNamespaces} includes this mod.
 * <p>
 * The "test_platform" structure is an empty 130x14x3 box. All tests run at the same time, side by
 * side in one world, so every test uses its own frequency (otherwise a receiver would, correctly,
 * pick up the neighbouring test's transmitter).
 */
@GameTestHolder(SignalTower.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SignalTowerGameTests {
    private static final String PLATFORM = "test_platform";
    /** v0.48.0: the tests that check signal fade run in a batch of their own, with it on (the default config). */
    private static final String FADE = "signal_fade";

    /** v0.49.0: the power cost tests, in a batch of their own with it on (the default config). */
    private static final String POWER = "power_cost";

    /**
     * Signal fade and power cost are off for every other test, which check other things at exact
     * strengths with no batteries (batches run one after another).
     */
    @net.minecraft.gametest.framework.BeforeBatch(batch = "defaultBatch")
    public static void withoutFade(ServerLevel level) {
        SignalNetwork.fadeForTests(false);
        com.reiko.signaltower.radio.PowerCost.powerForTests(false);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = FADE)
    public static void withFade(ServerLevel level) {
        SignalNetwork.fadeForTests(null);
        com.reiko.signaltower.radio.PowerCost.powerForTests(false);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = POWER)
    public static void withPower(ServerLevel level) {
        SignalNetwork.fadeForTests(false);
        com.reiko.signaltower.radio.PowerCost.powerForTests(null);
    }

    /** v0.53.0: the Radio Weather Station tests that set the weather, in a batch of their own (the weather is the whole dimension's). */
    private static final String STATION = "station_weather";
    /** v0.53.0: the Reiko's Weather test, in a batch of its own: it changes that mod's weather and storm cells. */
    private static final String REIKOS = "reikos_weather";

    @net.minecraft.gametest.framework.BeforeBatch(batch = STATION)
    public static void beforeStation(ServerLevel level) {
        withoutFade(level);
        SignalNetwork.get(level).forceWeather(com.reiko.signaltower.radio.Weather.CLEAR);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = STATION)
    public static void afterStation(ServerLevel level) {
        SignalNetwork.get(level).forceWeather(null);
    }

    /** v0.54.0: storm damage with Reiko's Weather, in a batch of its own (its storm cells reach the tests beside it). */
    private static final String STORMS = "reikos_storms";

    @net.minecraft.gametest.framework.BeforeBatch(batch = STORMS)
    public static void beforeStorms(ServerLevel level) {
        withoutFade(level);
        SignalNetwork.get(level).forceWeather(null);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = STORMS)
    public static void afterStorms(ServerLevel level) {
        com.reiko.signaltower.radio.StormDamage.chanceForTests(null);
        afterReikos(level);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = REIKOS)
    public static void beforeReikos(ServerLevel level) {
        withoutFade(level);
        SignalNetwork.get(level).forceWeather(null);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = REIKOS)
    public static void afterReikos(ServerLevel level) {
        com.reiko.signaltower.compat.ReikosWeatherCompat.forTests(null);
        try {
            if (com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
                reikosCells(level).clear();
            }
        } catch (ReflectiveOperationException ignored) {
            // nothing to tidy up
        }
        setWeatherNow(level, false, false);
    }

    /** Sets vanilla's weather and skips its fade (rain takes a second or so to set in, thunder longer). */
    private static void setWeatherNow(ServerLevel level, boolean rain, boolean thunder) {
        level.setWeatherParameters(rain ? 0 : 6000, rain ? 6000 : 0, rain, thunder);
        level.setRainLevel(rain ? 1.0F : 0.0F);
        level.setThunderLevel(thunder ? 1.0F : 0.0F);
    }

    // ---- helpers ----

    private static void place(GameTestHelper helper, BlockPos pos, Block block, int frequency) {
        helper.setBlock(pos, block);
        setFrequency(helper, pos, frequency);
    }

    private static void setFrequency(GameTestHelper helper, BlockPos pos, int frequency) {
        RadioBlockEntity radio = helper.getBlockEntity(pos);
        radio.setFrequency(frequency);
    }

    private static int frequencyAt(GameTestHelper helper, BlockPos pos) {
        RadioBlockEntity radio = helper.getBlockEntity(pos);
        return radio.getFrequency();
    }

    /**
     * Where to put a receiver so it is {@code wanted} blocks east of {@code tx}, or closer if the
     * configured reach is smaller, so the tests pass with any reasonable config.
     */
    private static BlockPos inReach(GameTestHelper helper, BlockPos tx, int wanted) {
        int reach = RadioHelper.reachAt(helper.getLevel(), helper.absolutePos(tx));
        if (reach < 3) {
            throw new GameTestAssertException("Reach " + reach + " is too small to test; check the config");
        }
        return tx.east(Math.min(wanted, reach - 1)).atY(1);
    }

    private static void assertOutput(GameTestHelper helper, BlockPos rx, int expected) {
        helper.assertBlockProperty(rx, RadioReceiverBlock.POWER, expected);
    }

    // ---- tests ----

    /** Power in, power out 20 blocks away (or less if reach is configured lower); power off, power off; transmitter broken, signal gone. */
    @GameTest(template = PLATFORM)
    public static void sameFrequencyLinks(GameTestHelper helper) {
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos rx = inReach(helper, tx, 20);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 101);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 101);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.AIR))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> helper.destroyBlock(tx))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /** Signal strength is passed through exactly: a wire carrying 14 in means 14 out. */
    @GameTest(template = PLATFORM)
    public static void signalStrengthIsKept(GameTestHelper helper) {
        BlockPos tx = new BlockPos(4, 1, 1);
        BlockPos rx = inReach(helper, tx, 10);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 102);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 102);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE); // wire needs something to sit on
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_WIRE); // 15
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.REDSTONE_WIRE); // 14 -> into the transmitter

        helper.succeedWhen(() -> assertOutput(helper, rx, 14));
    }

    /** A receiver on another frequency ignores the signal until it is re-tuned to match. */
    @GameTest(template = PLATFORM)
    public static void frequenciesAreSeparate(GameTestHelper helper) {
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos rx = inReach(helper, tx, 9);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 103);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 104);
        helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> setFrequency(helper, rx, 103))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenSucceed();
    }

    /**
     * A receiver just out of reach goes live once the transmitter is lifted 4 blocks higher on masts.
     * The power source stays at ground level next to the bottom mast, so this also checks that masts
     * carry the signal up.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void higherTransmitterReachesFarther(GameTestHelper helper) {
        BlockPos ground = new BlockPos(1, 1, 1);
        BlockPos raised = ground.above(4);
        int lowReach = RadioHelper.reachAt(helper.getLevel(), helper.absolutePos(ground));
        int highReach = RadioHelper.reachAt(helper.getLevel(), helper.absolutePos(raised));
        int distance = lowReach + 2;
        if (highReach < distance) {
            throw new GameTestAssertException("Raising 4 blocks only grew reach from " + lowReach + " to "
                    + highReach + "; check the reach config");
        }
        if (ground.getX() + distance > 128) {
            throw new GameTestAssertException("Reach " + lowReach + " is too big for the test platform");
        }
        BlockPos rx = new BlockPos(ground.getX() + distance, 1, 1);

        place(helper, ground, ModRegistry.RADIO_TRANSMITTER.get(), 105);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 105);
        helper.setBlock(ground.north(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> {
                    // Replace the transmitter with a 4-high mast column and put it back on top.
                    for (int y = 0; y < 4; y++) {
                        helper.setBlock(ground.above(y), ModRegistry.RADIO_MAST.get());
                    }
                    place(helper, raised, ModRegistry.RADIO_TRANSMITTER.get(), 105);
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenSucceed();
    }

    /** Power any mast in the column and the transmitter on top sends it. Cutting the column cuts it. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void mastsCarryTheSignalUp(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 1);
        for (int y = 0; y < 6; y++) {
            helper.setBlock(base.above(y), ModRegistry.RADIO_MAST.get());
        }
        BlockPos tx = base.above(6);
        BlockPos rx = inReach(helper, tx, 24);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 106);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 106);

        BlockPos bottomSide = base.east();
        BlockPos middleSide = base.above(3).east();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> helper.setBlock(bottomSide, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> helper.setBlock(bottomSide, Blocks.AIR))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> helper.setBlock(middleSide, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                // Break the mast just under the transmitter: the powered mast is no longer connected.
                .thenExecute(() -> helper.setBlock(base.above(5), Blocks.AIR))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /** The tuner tunes a block on right-click and copies a block's frequency on sneak + right-click. */
    @GameTest(template = PLATFORM)
    public static void tunerSetsAndCopiesFrequencies(GameTestHelper helper) {
        BlockPos rx = new BlockPos(1, 1, 1);
        BlockPos tx = new BlockPos(3, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 777_000_123);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
        FrequencyTunerItem.setFrequency(tuner, 4242);
        player.setItemInHand(InteractionHand.MAIN_HAND, tuner);

        useTunerOn(helper, player, rx);
        if (frequencyAt(helper, rx) != 4242) {
            throw new GameTestAssertException("Tuning set the receiver to " + frequencyAt(helper, rx) + ", expected 4242");
        }

        player.setShiftKeyDown(true);
        useTunerOn(helper, player, tx);
        int copied = FrequencyTunerItem.getFrequency(player.getMainHandItem());
        if (copied != 777_000_123) {
            throw new GameTestAssertException("Sneak-click copied " + copied + ", expected 777000123");
        }
        if (frequencyAt(helper, tx) != 777_000_123) {
            throw new GameTestAssertException("Copying must not change the transmitter");
        }
        helper.succeed();
    }

    private static void useTunerOn(GameTestHelper helper, Player player, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** Breaking a tuned transmitter drops an item that remembers its frequency. */
    @GameTest(template = PLATFORM)
    public static void brokenBlockKeepsFrequency(GameTestHelper helper) {
        BlockPos tx = new BlockPos(2, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 31337);
        helper.getLevel().destroyBlock(helper.absolutePos(tx), true);

        helper.succeedWhen(() -> {
            boolean found = helper.getEntities(EntityType.ITEM).stream()
                    .map(ItemEntity::getItem)
                    .anyMatch(stack -> stack.is(ModRegistry.RADIO_TRANSMITTER_ITEM.get())
                            && Integer.valueOf(31337).equals(stack.get(ModRegistry.FREQUENCY.get())));
            if (!found) {
                throw new GameTestAssertException("No dropped transmitter with frequency 31337");
            }
        });
    }

    /** A compact receiver stuck on a wall powers that wall block, which lights a lamp on the far side. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void compactReceiverPowersThroughItsWall(GameTestHelper helper) {
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos wall = new BlockPos(6, 1, 1);
        BlockPos compact = wall.west();  // on the west face of the wall, pointing west
        BlockPos lamp = wall.east();     // touching the wall only, not the receiver
        helper.setBlock(tx, ModRegistry.RADIO_TRANSMITTER.get());
        setFrequency(helper, tx, 110);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(compact, compactOnWall(Direction.WEST));
        setFrequency(helper, compact, 110);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false))
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, compact, 15))
                .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true))
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false))
                .thenSucceed();
    }

    /** Removing the wall knocks the compact receiver off, and the dropped item keeps its frequency. */
    @GameTest(template = PLATFORM)
    public static void compactReceiverPopsOffAndKeepsFrequency(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3, 1, 1);
        BlockPos compact = wall.west();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(compact, compactOnWall(Direction.WEST));
        setFrequency(helper, compact, 24680);
        helper.setBlock(wall, Blocks.AIR);

        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ModRegistry.COMPACT_RADIO_RECEIVER.get(), compact);
            boolean found = helper.getEntities(EntityType.ITEM).stream()
                    .map(ItemEntity::getItem)
                    .anyMatch(stack -> stack.is(ModRegistry.COMPACT_RADIO_RECEIVER_ITEM.get())
                            && Integer.valueOf(24680).equals(stack.get(ModRegistry.FREQUENCY.get())));
            if (!found) {
                throw new GameTestAssertException("No dropped compact receiver with frequency 24680");
            }
        });
    }

    private static BlockState compactOnWall(Direction pointing) {
        return ModRegistry.COMPACT_RADIO_RECEIVER.get().defaultBlockState()
                .setValue(CompactRadioReceiverBlock.FACE, AttachFace.WALL)
                .setValue(CompactRadioReceiverBlock.FACING, pointing);
    }

    // ---- Wall Redstone ----

    private static BlockState wallRedstone(Direction facing) {
        return ModRegistry.WALL_REDSTONE.get().defaultBlockState().setValue(WallRedstoneBlock.FACING, facing);
    }

    private static void assertWallPower(GameTestHelper helper, BlockPos pos, int expected) {
        helper.assertBlockProperty(pos, WallRedstoneBlock.POWER, expected);
    }

    /** A line up the side of a pillar: 15 at the bottom, one less per block, and it lights a lamp at the top. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void wallRedstoneClimbsAWall(GameTestHelper helper) {
        int height = 8;
        for (int y = 1; y <= height; y++) {
            helper.setBlock(new BlockPos(3, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(2, y, 1), wallRedstone(Direction.WEST));
        }
        BlockPos source = new BlockPos(1, 1, 1);  // in front of the bottom piece
        BlockPos lamp = new BlockPos(2, height + 1, 1);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    for (int y = 1; y <= height; y++) {
                        assertWallPower(helper, new BlockPos(2, y, 1), 16 - y);
                    }
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
                })
                .thenExecute(() -> helper.setBlock(source, Blocks.AIR))
                .thenWaitUntil(() -> {
                    for (int y = 1; y <= height; y++) {
                        assertWallPower(helper, new BlockPos(2, y, 1), 0);
                    }
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                })
                .thenSucceed();
    }

    /**
     * Normal dust feeds Wall Redstone and gets powered by it, and when the source goes away everything
     * turns off again. Includes the tricky loop where dust touches the block the wall wire powers.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void wallRedstoneWorksWithNormalDust(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 0, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.STONE);
        }
        BlockPos source = new BlockPos(0, 1, 1);
        BlockPos dustA = new BlockPos(1, 1, 1);
        BlockPos dustB = new BlockPos(2, 1, 1);           // in front of the bottom wall piece
        BlockPos loopDust = new BlockPos(4, 1, 0);        // touches the pillar the wall wire powers
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(4, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(3, y, 1), wallRedstone(Direction.WEST));
        }
        helper.setBlock(dustA, Blocks.REDSTONE_WIRE);
        helper.setBlock(dustB, Blocks.REDSTONE_WIRE);
        helper.setBlock(loopDust, Blocks.REDSTONE_WIRE);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    helper.assertBlockProperty(dustB, RedStoneWireBlock.POWER, 14);
                    assertWallPower(helper, new BlockPos(3, 1, 1), 13);
                    assertWallPower(helper, new BlockPos(3, 3, 1), 11);
                    int loop = helper.getBlockState(loopDust).getValue(RedStoneWireBlock.POWER);
                    if (loop == 0) throw new GameTestAssertException("Dust next to the powered pillar should be on");
                })
                .thenExecute(() -> helper.setBlock(source, Blocks.AIR))
                .thenWaitUntil(() -> {
                    helper.assertBlockProperty(dustA, RedStoneWireBlock.POWER, 0);
                    helper.assertBlockProperty(dustB, RedStoneWireBlock.POWER, 0);
                    helper.assertBlockProperty(loopDust, RedStoneWireBlock.POWER, 0);
                    for (int y = 1; y <= 3; y++) {
                        assertWallPower(helper, new BlockPos(3, y, 1), 0);
                    }
                })
                .thenSucceed();
    }

    /** Like dust on a floor, Wall Redstone powers the block it's on, so a lamp behind the wall lights. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void wallRedstonePowersTheBlockItIsOn(GameTestHelper helper) {
        BlockPos wire = new BlockPos(3, 1, 1);
        BlockPos wall = wire.east();
        BlockPos lamp = wall.east();
        BlockPos source = wire.west();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(wire, wallRedstone(Direction.WEST));
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(source, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true))
                .thenExecute(() -> helper.setBlock(source, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false))
                .thenSucceed();
    }

    /** Removing the wall knocks the Wall Redstone off and drops it. */
    @GameTest(template = PLATFORM)
    public static void wallRedstonePopsOff(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3, 1, 1);
        BlockPos wire = wall.west();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(wire, wallRedstone(Direction.WEST));
        helper.setBlock(wall, Blocks.AIR);

        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ModRegistry.WALL_REDSTONE.get(), wire);
            helper.assertItemEntityPresent(ModRegistry.WALL_REDSTONE_ITEM.get(), wire, 2.0);
        });
    }

    // ---- Remote Control ----

    private static Player playerWithRemote(GameTestHelper helper, BlockPos standAt, int frequency, boolean toggle) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(standAt));
        player.moveTo(feet.x, feet.y, feet.z);
        ItemStack remote = new ItemStack(ModRegistry.REMOTE_CONTROL.get());
        FrequencyTunerItem.setFrequency(remote, frequency);
        RemoteControlItem.setToggleMode(remote, toggle);
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        return player;
    }

    private static void pressRemote(GameTestHelper helper, Player player) {
        player.getMainHandItem().getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    /**
     * Presses the remote with tower relays left out. Tests that check a far receiver hears nothing
     * use this: all tests run side by side, so a neighbouring test's transmitter would relay the press.
     */
    private static void pressWithoutRelays(GameTestHelper helper, Player player) {
        ItemStack remote = player.getMainHandItem();
        BlockPos origin = player.blockPosition();
        RemoteControlItem.sendSignal(helper.getLevel(), player, remote, FrequencyTunerItem.getFrequency(remote),
                RemoteControlItem.isToggleMode(remote), RemoteControlItem.getPulseTicks(remote),
                SignalNetwork.ownCoverage(origin, RadioHelper.reachAt(helper.getLevel(), origin)));
    }

    /** Pulse mode: one press turns the receiver on for about a second, then it turns itself off. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void remotePulseFlashesReceiver(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 201);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 201, false);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(RemoteControlItem.PULSE_TICKS / 2)
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /** Toggle mode: on until the next press, however long that takes. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void remoteToggleStaysOnUntilPressedAgain(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 202);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 202, true);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(RemoteControlItem.PULSE_TICKS * 3)
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> pressRemote(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /** The remote's own reach follows the tower rule: a receiver just past it hears nothing (no relays). */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void remoteRespectsReach(GameTestHelper helper) {
        BlockPos standAt = new BlockPos(1, 1, 1);
        int reach = RadioHelper.reachAt(helper.getLevel(), helper.absolutePos(standAt));
        if (standAt.getX() + reach + 3 > 128) {
            throw new GameTestAssertException("Reach " + reach + " is too big for the test platform");
        }
        BlockPos near = new BlockPos(4, 1, 1);
        BlockPos far = new BlockPos(standAt.getX() + reach + 3, 1, 1);
        place(helper, near, ModRegistry.RADIO_RECEIVER.get(), 203);
        place(helper, far, ModRegistry.RADIO_RECEIVER.get(), 203);
        Player player = playerWithRemote(helper, standAt, 203, true);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressWithoutRelays(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, near, 15))
                .thenIdle(5)
                .thenExecute(() -> assertOutput(helper, far, 0))
                .thenExecute(() -> pressWithoutRelays(helper, player)) // switch it back off
                .thenWaitUntil(() -> assertOutput(helper, near, 0))
                .thenSucceed();
    }

    /** Sneak + right-click on a transmitter copies its frequency into the remote. */
    @GameTest(template = PLATFORM)
    public static void remoteCopiesFrequency(GameTestHelper helper) {
        BlockPos tx = new BlockPos(3, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 404);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 1, false);
        player.setShiftKeyDown(true);
        useTunerOn(helper, player, tx);

        int copied = FrequencyTunerItem.getFrequency(player.getMainHandItem());
        if (copied != 404) {
            throw new GameTestAssertException("Remote copied " + copied + ", expected 404");
        }
        helper.succeed();
    }

    // ---- Multi-Button Remote ----

    private static Player playerWithMultiRemote(GameTestHelper helper, RemoteButtons buttons) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)));
        player.moveTo(feet.x, feet.y, feet.z);
        ItemStack remote = new ItemStack(ModRegistry.MULTI_REMOTE.get());
        MultiRemoteItem.setButtons(remote, buttons);
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        return player;
    }

    /**
     * Each button drives its own frequency with its own mode: button 1 pulses one receiver, then
     * sneak-scrolling to button 2 toggles the other one on and off, without touching the first.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void multiRemoteButtonsAreSeparate(GameTestHelper helper) {
        BlockPos rxPulse = new BlockPos(5, 1, 1);
        BlockPos rxToggle = new BlockPos(8, 1, 1);
        place(helper, rxPulse, ModRegistry.RADIO_RECEIVER.get(), 301);
        place(helper, rxToggle, ModRegistry.RADIO_RECEIVER.get(), 302);
        Player player = playerWithMultiRemote(helper, new RemoteButtons(List.of(
                new RemoteButtons.Button(301, false), new RemoteButtons.Button(302, true),
                new RemoteButtons.Button(3, false), new RemoteButtons.Button(4, false)), 0));

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))          // button 1: pulse
                .thenWaitUntil(() -> assertOutput(helper, rxPulse, 15))
                .thenExecute(() -> assertOutput(helper, rxToggle, 0))
                .thenExecute(() -> MultiRemoteItem.cycleSelection(player, player.getMainHandItem(), 1))
                .thenExecute(() -> pressRemote(helper, player))          // button 2: toggle on
                .thenWaitUntil(() -> assertOutput(helper, rxToggle, 15))
                .thenWaitUntil(() -> assertOutput(helper, rxPulse, 0))   // the pulse ended by itself
                .thenIdle(RemoteControlItem.PULSE_TICKS * 2)
                .thenExecute(() -> assertOutput(helper, rxToggle, 15))   // the toggle didn't
                .thenExecute(() -> pressRemote(helper, player))          // button 2: toggle off
                .thenWaitUntil(() -> assertOutput(helper, rxToggle, 0))
                .thenExecute(() -> assertOutput(helper, rxPulse, 0))
                .thenSucceed();
    }

    /** Sneak + right-click copies a block's frequency into the selected button only. */
    @GameTest(template = PLATFORM)
    public static void multiRemoteCopiesIntoSelectedButton(GameTestHelper helper) {
        BlockPos tx = new BlockPos(3, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 777);
        Player player = playerWithMultiRemote(helper, RemoteButtons.DEFAULT.withSelected(2));
        player.setShiftKeyDown(true);
        useTunerOn(helper, player, tx);

        RemoteButtons buttons = MultiRemoteItem.getButtons(player.getMainHandItem());
        int[] expected = {1, 2, 777, 4};
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            if (buttons.button(i).frequency() != expected[i]) {
                throw new GameTestAssertException("Button " + (i + 1) + " is " + buttons.button(i).frequency()
                        + ", expected " + expected[i]);
            }
        }
        helper.succeed();
    }

    /** Settings from a packet or command are tidied up: always 4 buttons, a valid selection, valid frequencies. */
    @GameTest(template = PLATFORM)
    public static void remoteButtonsAreAlwaysValid(GameTestHelper helper) {
        RemoteButtons odd = new RemoteButtons(List.of(new RemoteButtons.Button(0, true), new RemoteButtons.Button(2_000_000_000, false)), 7);
        if (odd.buttons().size() != RemoteButtons.COUNT) throw new GameTestAssertException("Expected 4 buttons");
        if (odd.selected() != 3) throw new GameTestAssertException("Selection 7 should wrap to 3, got " + odd.selected());
        if (odd.button(0).frequency() != 1) throw new GameTestAssertException("Frequency 0 should clamp to 1");
        if (odd.button(1).frequency() != 999_999_999) throw new GameTestAssertException("Huge frequency should clamp");
        if (RemoteButtons.DEFAULT.withSelected(-1).selected() != 3) throw new GameTestAssertException("Scrolling back from 1 should wrap to 4");
        helper.succeed();
    }

    // ---- Pulse length ----

    /** A Remote Control set to a 5-second pulse is still on after 3 seconds, then turns itself off. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void remoteLongPulseLastsLonger(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 501);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 501, false);
        RemoteControlItem.setPulseTicks(player.getMainHandItem(), 100);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(60)
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /** A Multi-Button Remote button set to a 0.25-second pulse is off again well before a normal 1-second pulse would be. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void multiRemoteShortPulseEndsSooner(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 502);
        Player player = playerWithMultiRemote(helper,
                RemoteButtons.DEFAULT.withButton(0, new RemoteButtons.Button(502, false, 5)));

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(12)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /** Presets step and wrap correctly, lengths are kept in range, and labels read nicely. */
    @GameTest(template = PLATFORM)
    public static void pulseLengthPresetsAndLimits(GameTestHelper helper) {
        check(PulseLength.next(20, false) == 30, "1s should step up to 1.5s");
        check(PulseLength.next(20, true) == 10, "1s should step down to 0.5s");
        check(PulseLength.next(200, false) == 2, "10s should wrap up to 0.1s");
        check(PulseLength.next(2, true) == 200, "0.1s should wrap down to 10s");
        check(PulseLength.next(7, false) == 10, "An odd length should step to the next preset");
        check(PulseLength.clamp(0) == PulseLength.MIN_TICKS, "Too short should clamp");
        check(PulseLength.clamp(999) == PulseLength.MAX_TICKS, "Too long should clamp");
        check(PulseLength.format(5).equals("0.25s"), "5 ticks should read 0.25s, got " + PulseLength.format(5));
        check(PulseLength.format(20).equals("1s"), "20 ticks should read 1s, got " + PulseLength.format(20));
        check(PulseLength.format(30).equals("1.5s"), "30 ticks should read 1.5s, got " + PulseLength.format(30));
        check(new RemoteButtons.Button(1, false, 0).pulseTicks() == PulseLength.MIN_TICKS, "Button pulse should clamp");
        check(new RemoteButtons.Button(1, false).pulseTicks() == PulseLength.DEFAULT_TICKS, "Default should be 1s");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    // ---- Remote signal light ----

    private static void assertLight(GameTestHelper helper, Player player, boolean expectedHeard) {
        RemoteLight light = player.getMainHandItem().get(ModRegistry.REMOTE_LIGHT.get());
        if (light == null) {
            throw new GameTestAssertException("The remote's light didn't turn on after a press");
        }
        if (light.heard() != expectedHeard) {
            throw new GameTestAssertException("Light should be " + (expectedHeard ? "green" : "red"));
        }
        if (light.untilGameTime() <= helper.getLevel().getGameTime()) {
            throw new GameTestAssertException("Light should still be showing right after the press");
        }
    }

    /** A receiver in range on the same frequency turns the light green. */
    @GameTest(template = PLATFORM)
    public static void remoteLightGreenWhenHeard(GameTestHelper helper) {
        place(helper, new BlockPos(6, 1, 1), ModRegistry.RADIO_RECEIVER.get(), 601);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 601, false);
        helper.startSequence()
                .thenIdle(3) // receivers check in on their first tick
                .thenExecute(() -> pressRemote(helper, player))
                .thenExecute(() -> assertLight(helper, player, true))
                .thenSucceed();
    }

    /** Receivers out of reach, or on another frequency, don't count: the light goes red. */
    @GameTest(template = PLATFORM)
    public static void remoteLightRedWhenNobodyHears(GameTestHelper helper) {
        BlockPos standAt = new BlockPos(1, 1, 1);
        int reach = RadioHelper.reachAt(helper.getLevel(), helper.absolutePos(standAt));
        if (standAt.getX() + reach + 3 > 128) {
            throw new GameTestAssertException("Reach " + reach + " is too big for the test platform");
        }
        place(helper, new BlockPos(standAt.getX() + reach + 3, 1, 1), ModRegistry.RADIO_RECEIVER.get(), 602); // too far
        place(helper, new BlockPos(5, 1, 1), ModRegistry.RADIO_RECEIVER.get(), 603); // close, but the wrong frequency
        Player player = playerWithRemote(helper, standAt, 602, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressWithoutRelays(helper, player))
                .thenExecute(() -> assertLight(helper, player, false))
                .thenSucceed();
    }

    /** A receiver re-tuned to another frequency stops counting for the old one. */
    @GameTest(template = PLATFORM)
    public static void retunedReceiverStopsCounting(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 604);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 604, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenExecute(() -> assertLight(helper, player, true))
                .thenExecute(() -> setFrequency(helper, rx, 605))
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenExecute(() -> assertLight(helper, player, false))
                .thenSucceed();
    }

    /** A broken receiver stops counting straight away. */
    @GameTest(template = PLATFORM)
    public static void brokenReceiverStopsCounting(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 606);
        Player player = playerWithMultiRemote(helper, RemoteButtons.DEFAULT.withButton(0, new RemoteButtons.Button(606, false)));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, player))
                .thenExecute(() -> assertLight(helper, player, true))
                .thenExecute(() -> helper.destroyBlock(rx))
                .thenExecute(() -> pressRemote(helper, player))
                .thenExecute(() -> assertLight(helper, player, false))
                .thenSucceed();
    }

    // ---- Button names ----

    /** Names lose colour codes, control characters and surrounding spaces, and are cut to 24 characters. */
    @GameTest(template = PLATFORM)
    public static void buttonNamesAreCleaned(GameTestHelper helper) {
        String cleaned = RemoteButtons.cleanName("  \u00a7cFront\tDoor  ");
        check(!cleaned.contains("\u00a7"), "Colour codes should be removed, got '" + cleaned + "'");
        check(!cleaned.contains("\t"), "Control characters should be removed, got '" + cleaned + "'");
        check(!cleaned.startsWith(" ") && !cleaned.endsWith(" "), "Spaces around the name should go, got '" + cleaned + "'");
        check(RemoteButtons.cleanName("A very long name for a remote button indeed").length() <= RemoteButtons.MAX_NAME_LENGTH,
                "Names should be cut to " + RemoteButtons.MAX_NAME_LENGTH + " characters");
        check(RemoteButtons.cleanName(null).isEmpty(), "A missing name should become empty");
        check(new RemoteButtons.Button(1, false, 20, "   ").name().isEmpty(), "A blank name means unnamed");
        helper.succeed();
    }

    /** A named button shows its name; an unnamed one shows "Button N". */
    @GameTest(template = PLATFORM)
    public static void buttonLabelsUseNames(GameTestHelper helper) {
        check(new RemoteButtons.Button(1, false, 20, "Front Door").label(0).getString().equals("Front Door"),
                "A named button should be labelled with its name");
        var unnamed = new RemoteButtons.Button(1, false).label(1);
        check(unnamed.getContents() instanceof TranslatableContents t
                        && t.getKey().endsWith(".remote_button_default_name") && Integer.valueOf(2).equals(t.getArgs()[0]),
                "An unnamed second button should be labelled 'Button 2'");
        helper.succeed();
    }

    /** Names survive saving and network sync, and remotes saved before names existed still load. */
    @GameTest(template = PLATFORM)
    public static void buttonNamesSurviveSaveAndSync(GameTestHelper helper) {
        RemoteButtons original = RemoteButtons.DEFAULT
                .withButton(0, new RemoteButtons.Button(101, false, 10, "Front Door"))
                .withButton(3, new RemoteButtons.Button(404, true, 20, "Farm Lights"))
                .withSelected(3);

        var saved = RemoteButtons.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        RemoteButtons loaded = RemoteButtons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        check(loaded.equals(original), "Saving and loading changed the buttons: " + loaded);

        var buffer = Unpooled.buffer();
        RemoteButtons.STREAM_CODEC.encode(buffer, original);
        RemoteButtons synced = RemoteButtons.STREAM_CODEC.decode(buffer);
        check(synced.equals(original), "Network sync changed the buttons: " + synced);

        var oldFormat = com.google.gson.JsonParser.parseString(
                "{\"buttons\":[{\"frequency\":7},{\"frequency\":8,\"toggle\":true}],\"selected\":1}");
        RemoteButtons old = RemoteButtons.CODEC.parse(JsonOps.INSTANCE, oldFormat).getOrThrow();
        check(old.button(0).frequency() == 7 && old.button(0).name().isEmpty(), "Old remotes should load with unnamed buttons");
        check(old.button(1).toggle() && old.selected() == 1, "Old remotes should keep their settings");
        helper.succeed();
    }

    /** Copying a frequency into a named button keeps its name. */
    @GameTest(template = PLATFORM)
    public static void copyingFrequencyKeepsButtonName(GameTestHelper helper) {
        BlockPos tx = new BlockPos(3, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 888);
        Player player = playerWithMultiRemote(helper, RemoteButtons.DEFAULT
                .withButton(1, new RemoteButtons.Button(2, false, 20, "Garage")).withSelected(1));
        player.setShiftKeyDown(true);
        useTunerOn(helper, player, tx);

        RemoteButtons.Button button = MultiRemoteItem.getButtons(player.getMainHandItem()).button(1);
        check(button.frequency() == 888, "Frequency should be copied, got " + button.frequency());
        check(button.name().equals("Garage"), "The name should be kept, got '" + button.name() + "'");
        helper.succeed();
    }

    // ---- Remote locator ----

    private static SignalNetwork.ReceiverScan scanFrom(GameTestHelper helper, BlockPos relativeOrigin, int frequency) {
        BlockPos origin = helper.absolutePos(relativeOrigin);
        return SignalNetwork.get(helper.getLevel())
                .scanReceivers(frequency, origin, RadioHelper.reachAt(helper.getLevel(), origin));
    }

    /** Counts receivers in range and points at the nearest one. */
    @GameTest(template = PLATFORM)
    public static void locatorCountsAndPointsToNearest(GameTestHelper helper) {
        BlockPos standAt = new BlockPos(1, 1, 1);
        place(helper, new BlockPos(11, 1, 1), ModRegistry.RADIO_RECEIVER.get(), 701);         // 10 east
        place(helper, new BlockPos(4, 1, 0), ModRegistry.RADIO_RECEIVER.get(), 701);          // ~3 east (and 1 north)
        place(helper, new BlockPos(2, 1, 1), ModRegistry.RADIO_RECEIVER.get(), 799);          // other frequency
        helper.startSequence()
                .thenIdle(3) // receivers check in on their first tick
                .thenExecute(() -> {
                    SignalNetwork.ReceiverScan scan = scanFrom(helper, standAt, 701);
                    check(scan.inRange() == 2, "Expected 2 receivers in range, got " + scan.inRange());
                    check(scan.nearestDistance() == 3, "Nearest should be 3 blocks away, got " + scan.nearestDistance());
                    check(scan.direction() == 2, "Nearest should be east (2), got " + scan.direction());
                    check(scan.nearestInRange(), "Nearest should be in range");
                })
                .thenSucceed();
    }

    /** Nothing in range, but one a little too far: the locator says where it is. */
    @GameTest(template = PLATFORM)
    public static void locatorHintsWhenJustOutOfRange(GameTestHelper helper) {
        BlockPos standAt = new BlockPos(1, 1, 1);
        int reach = RadioHelper.reachAt(helper.getLevel(), helper.absolutePos(standAt));
        if (standAt.getX() + reach + 3 > 128) {
            throw new GameTestAssertException("Reach " + reach + " is too big for the test platform");
        }
        place(helper, new BlockPos(standAt.getX() + reach + 3, 1, 1), ModRegistry.RADIO_RECEIVER.get(), 702);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SignalNetwork.ReceiverScan scan = scanFrom(helper, standAt, 702);
                    check(scan.inRange() == 0, "Nothing should be in range, got " + scan.inRange());
                    check(scan.nearestDistance() == reach + 3, "Nearest should be " + (reach + 3) + " away, got " + scan.nearestDistance());
                    check(!scan.nearestInRange(), "Nearest should be marked as out of range");
                })
                .thenSucceed();
    }

    /** Receivers more than twice the reach away aren't revealed. */
    @GameTest(template = PLATFORM)
    public static void locatorIgnoresFarReceivers(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        int reach = RadioHelper.reachAt(helper.getLevel(), origin);
        SignalNetwork network = SignalNetwork.get(helper.getLevel());
        BlockPos far = origin.east(reach * 2 + 5);
        network.trackReceiver(far, 703);
        SignalNetwork.ReceiverScan scan = network.scanReceivers(703, origin, reach);
        network.untrackReceiver(far);
        check(scan.inRange() == 0 && scan.nearestDistance() == -1, "A receiver that far away should stay hidden");
        helper.succeed();
    }

    /** The eight compass points, north being -Z. */
    @GameTest(template = PLATFORM)
    public static void locatorCompassDirections(GameTestHelper helper) {
        int[][] cases = {{0, -5, 0}, {5, -5, 1}, {5, 0, 2}, {5, 5, 3}, {0, 5, 4}, {-5, 5, 5}, {-5, 0, 6}, {-5, -5, 7}, {0, 0, -1}};
        for (int[] c : cases) {
            int got = SignalNetwork.compassDirection(c[0], c[1]);
            check(got == c[2], "Offset (" + c[0] + ", " + c[1] + ") should be direction " + c[2] + ", got " + got);
        }
        helper.succeed();
    }

    // ---- Tower relays ----

    /**
     * A Radio Transmitter on a 4-mast tower next to the player relays a remote press, on another
     * frequency and with no redstone input, to a receiver beyond the player's own reach. The light
     * goes green and the message counts the tower.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void towerRelaysRemotePress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos standAt = new BlockPos(1, 1, 1);
        BlockPos base = new BlockPos(3, 1, 1);
        BlockPos tower = base.above(4);
        int ownReach = RadioHelper.reachAt(level, helper.absolutePos(standAt));
        int towerReach = RadioHelper.reachAt(level, helper.absolutePos(tower));
        BlockPos rx = new BlockPos(standAt.getX() + ownReach + 3, 1, 1);
        if (ownReach < 3 || rx.getX() > 128 || rx.getX() - tower.getX() > towerReach) {
            throw new GameTestAssertException("Reach " + ownReach + " / " + towerReach + " doesn't fit the test platform");
        }
        for (int y = 0; y < 4; y++) {
            helper.setBlock(base.above(y), ModRegistry.RADIO_MAST.get());
        }
        place(helper, tower, ModRegistry.RADIO_TRANSMITTER.get(), 902);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 901);
        Player player = playerWithRemote(helper, standAt, 901, false);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos towerAbs = helper.absolutePos(tower);
                    check(RemoteControlItem.coverageFor(level, player).stream()
                            .anyMatch(area -> area.tower() && area.origin().equals(towerAbs)), "The tower should relay the remote");
                })
                .thenExecute(() -> pressRemote(helper, player))
                .thenExecute(() -> assertLight(helper, player, true))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0)) // still a pulse: it ends by itself
                .thenExecute(() -> {
                    var message = (TranslatableContents) RemoteControlItem.sendSignal(level, player,
                            player.getMainHandItem(), 901, false, PulseLength.MIN_TICKS).getContents();
                    var result = (TranslatableContents) ((Component) message.getArgs()[0]).getContents();
                    var reach = (TranslatableContents) ((Component) result.getArgs()[2]).getContents();
                    check(!reach.getKey().endsWith("reach_blocks"), "The message should count the tower, got " + reach.getKey());
                })
                .thenSucceed();
    }

    /** A tower farther away than the player's own reach doesn't hear the press, so it doesn't relay it. */
    @GameTest(template = PLATFORM)
    public static void farTowerDoesNotRelay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        int reach = RadioHelper.reachAt(level, origin);
        BlockPos near = new BlockPos(4, 1, 1);
        BlockPos far = new BlockPos(1 + reach + 3, 1, 1);
        if (far.getX() > 128) {
            throw new GameTestAssertException("Reach " + reach + " is too big for the test platform");
        }
        place(helper, near, ModRegistry.RADIO_TRANSMITTER.get(), 903);
        place(helper, far, ModRegistry.RADIO_TRANSMITTER.get(), 904);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    List<SignalNetwork.Coverage> coverage = SignalNetwork.get(level).remoteCoverage(level, origin, reach, true);
                    check(coverage.get(0).origin().equals(origin) && !coverage.get(0).tower(), "The player's own area comes first");
                    check(coverage.stream().anyMatch(area -> area.origin().equals(helper.absolutePos(near))), "The near tower should relay");
                    check(coverage.stream().noneMatch(area -> area.origin().equals(helper.absolutePos(far))), "The far tower shouldn't relay");
                })
                .thenSucceed();
    }

    /** With relaying switched off in the config, only the player's own reach counts. */
    @GameTest(template = PLATFORM)
    public static void relayCanBeSwitchedOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        int reach = RadioHelper.reachAt(level, origin);
        place(helper, new BlockPos(3, 1, 1), ModRegistry.RADIO_TRANSMITTER.get(), 910);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SignalNetwork network = SignalNetwork.get(level);
                    check(network.remoteCoverage(level, origin, reach, true).size() > 1, "With relays on, the tower should relay");
                    List<SignalNetwork.Coverage> off = network.remoteCoverage(level, origin, reach, false);
                    check(off.equals(SignalNetwork.ownCoverage(origin, reach)), "With relays off, only the player's own area counts");
                })
                .thenSucceed();
    }

    /**
     * A toggle switched on through a tower stays on while the tower stands, and the far receiver
     * goes off when the tower is broken. (The coverage is given directly so neighbouring tests'
     * transmitters can't relay it.)
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void brokenTowerStopsRelayedToggle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos standAt = new BlockPos(1, 1, 1);
        BlockPos base = new BlockPos(3, 1, 1);
        BlockPos tower = base.above(4);
        BlockPos origin = helper.absolutePos(standAt);
        int ownReach = RadioHelper.reachAt(level, origin);
        int towerReach = RadioHelper.reachAt(level, helper.absolutePos(tower));
        BlockPos rx = new BlockPos(standAt.getX() + ownReach + 3, 1, 1);
        if (ownReach < 3 || rx.getX() > 128 || rx.getX() - tower.getX() > towerReach) {
            throw new GameTestAssertException("Reach " + ownReach + " / " + towerReach + " doesn't fit the test platform");
        }
        for (int y = 0; y < 4; y++) {
            helper.setBlock(base.above(y), ModRegistry.RADIO_MAST.get());
        }
        place(helper, tower, ModRegistry.RADIO_TRANSMITTER.get(), 906);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 905);
        List<SignalNetwork.Coverage> coverage = List.of(
                new SignalNetwork.Coverage(origin, ownReach, false),
                new SignalNetwork.Coverage(helper.absolutePos(tower), towerReach, true));
        SignalNetwork network = SignalNetwork.get(level);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> check(network.toggleLatch(905, coverage), "The toggle should switch on"))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(RemoteControlItem.PULSE_TICKS * 2)
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> helper.setBlock(tower, Blocks.AIR))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> check(!network.toggleLatch(905, coverage), "The toggle should switch off"))
                .thenSucceed();
    }

    /** A toggle's tower areas are saved with the world, along with the old pos/reach keys. */
    @GameTest(template = PLATFORM)
    public static void relayedToggleSurvivesSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(5_000_000, 64, 5_000_000); // unloaded, so the tower counts as standing
        SignalNetwork network = new SignalNetwork();
        network.toggleLatch(907, List.of(
                new SignalNetwork.Coverage(far, 10, false),
                new SignalNetwork.Coverage(far.east(100), 30, true)));
        CompoundTag tag = network.save(new CompoundTag(), level.registryAccess());
        CompoundTag entry = tag.getList("remote_latches", Tag.TAG_COMPOUND).getCompound(0);
        check(entry.getLong("pos") == far.asLong() && entry.getInt("reach") == 10,
                "The player's own area should also be saved the old way, for older versions");

        SignalNetwork loaded = SignalNetwork.load(tag, level.registryAccess());
        check(loaded.listen(level, 907, far.east(5)).power() == 15, "The player's area should survive loading");
        check(loaded.listen(level, 907, far.east(120)).power() == 15, "The tower's area should survive loading");
        check(loaded.listen(level, 907, far.east(60)).power() == 0, "The gap between them should stay quiet");
        helper.succeed();
    }

    /** Toggles saved by version 0.10.0 and older (one pos and reach) still load. */
    @GameTest(template = PLATFORM)
    public static void oldToggleSavesStillLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(-5_000_000, 64, 5_000_000);
        CompoundTag entry = new CompoundTag();
        entry.putInt("frequency", 908);
        entry.putLong("pos", far.asLong());
        entry.putInt("reach", 20);
        ListTag latches = new ListTag();
        latches.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("transmitters", new ListTag());
        tag.put("remote_latches", latches);

        SignalNetwork loaded = SignalNetwork.load(tag, level.registryAccess());
        check(loaded.listen(level, 908, far.east(10)).power() == 15, "An old toggle should still be on after loading");
        check(loaded.listen(level, 908, far.east(30)).power() == 0, "An old toggle should keep its reach");
        helper.succeed();
    }

    /** Receivers a tower reaches count as in range for the locator, however far away they are. */
    @GameTest(template = PLATFORM)
    public static void locatorCountsRelayedReceivers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        int reach = RadioHelper.reachAt(level, origin);
        SignalNetwork network = SignalNetwork.get(level);
        BlockPos rx = origin.east(reach * 3);
        network.trackReceiver(rx, 909);
        List<SignalNetwork.Coverage> withTower = List.of(
                new SignalNetwork.Coverage(origin, reach, false),
                new SignalNetwork.Coverage(rx.west(10), 20, true));
        SignalNetwork.ReceiverScan alone = network.scanReceivers(909, origin, reach, SignalNetwork.ownCoverage(origin, reach));
        SignalNetwork.ReceiverScan relayed = network.scanReceivers(909, origin, reach, withTower);
        int heard = network.countReceivers(909, withTower);
        network.untrackReceiver(rx);
        check(alone.inRange() == 0 && alone.nearestDistance() == -1, "Without the tower it's too far to show");
        check(relayed.inRange() == 1, "The tower should put it in range, got " + relayed.inRange());
        check(relayed.nearestDistance() == reach * 3, "Distance should be " + reach * 3 + ", got " + relayed.nearestDistance());
        check(relayed.direction() == 2 && relayed.nearestInRange(), "It should be east and in range");
        check(heard == 1, "The light should count it, got " + heard);
        helper.succeed();
    }

    // ---- Button colors ----

    /** New remotes, old remotes and loose buttons get red, lime, blue and yellow; colors cycle through all 16 dyes. */
    @GameTest(template = PLATFORM)
    public static void buttonColorsHaveDefaults(GameTestHelper helper) {
        DyeColor[] expected = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE, DyeColor.YELLOW};
        RemoteButtons fromLooseButtons = new RemoteButtons(List.of(
                new RemoteButtons.Button(1, false), new RemoteButtons.Button(2, false)), 0);
        for (int i = 0; i < RemoteButtons.COUNT; i++) {
            check(RemoteButtons.DEFAULT.button(i).color() == expected[i], "New remote button " + (i + 1) + " should be " + expected[i]);
            check(fromLooseButtons.button(i).color() == expected[i], "Button " + (i + 1) + " should get its slot's default color");
        }
        check(new RemoteButtons.Button(1, false).colorOr(2) == DyeColor.BLUE, "A loose button uses its slot's default");
        check(RemoteButtons.DEFAULT.withButton(1, RemoteButtons.DEFAULT.button(1).withColor(DyeColor.PURPLE))
                .button(1).color() == DyeColor.PURPLE, "A chosen color should stick");

        DyeColor color = DyeColor.WHITE;
        java.util.Set<DyeColor> seen = java.util.EnumSet.noneOf(DyeColor.class);
        for (int i = 0; i < 16; i++) {
            seen.add(color);
            color = RemoteButtons.nextColor(color, false);
        }
        check(seen.size() == 16 && color == DyeColor.WHITE, "Clicking should go through all 16 colors and back to white");
        check(RemoteButtons.nextColor(DyeColor.WHITE, true) == DyeColor.BLACK, "Sneak-clicking white should go back to black");
        helper.succeed();
    }

    /** Colors survive saving and network sync, and remotes saved before colors existed load with the defaults. */
    @GameTest(template = PLATFORM)
    public static void buttonColorsSurviveSaveAndSync(GameTestHelper helper) {
        RemoteButtons original = RemoteButtons.DEFAULT
                .withButton(0, new RemoteButtons.Button(101, false, 20, "Front Door", DyeColor.PURPLE))
                .withButton(3, new RemoteButtons.Button(404, true, 20, "", DyeColor.CYAN));

        var saved = RemoteButtons.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        RemoteButtons loaded = RemoteButtons.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        check(loaded.equals(original), "Saving and loading changed the colors: " + loaded);

        var buffer = Unpooled.buffer();
        RemoteButtons.STREAM_CODEC.encode(buffer, original);
        RemoteButtons synced = RemoteButtons.STREAM_CODEC.decode(buffer);
        check(synced.equals(original), "Network sync changed the colors: " + synced);

        var oldFormat = com.google.gson.JsonParser.parseString(
                "{\"buttons\":[{\"frequency\":7,\"name\":\"Gate\"},{\"frequency\":8}],\"selected\":0}");
        RemoteButtons old = RemoteButtons.CODEC.parse(JsonOps.INSTANCE, oldFormat).getOrThrow();
        check(old.button(0).color() == DyeColor.RED && old.button(1).color() == DyeColor.LIME,
                "Old remotes should load with red, lime, blue and yellow buttons");
        check(old.button(0).name().equals("Gate"), "Old remotes should keep their names");
        helper.succeed();
    }

    /** The item's button layers are tinted with each button's color, and labels are drawn in it. */
    @GameTest(template = PLATFORM)
    public static void remoteShowsButtonColors(GameTestHelper helper) {
        ItemStack remote = new ItemStack(ModRegistry.MULTI_REMOTE.get());
        MultiRemoteItem.setButtons(remote, RemoteButtons.DEFAULT
                .withButton(1, RemoteButtons.DEFAULT.button(1).withColor(DyeColor.PURPLE)));
        int purple = 0xFF000000 | DyeColor.PURPLE.getTextureDiffuseColor();
        int red = 0xFF000000 | DyeColor.RED.getTextureDiffuseColor();
        check(MultiRemoteItem.tintColor(remote, 2) == purple, "Layer 2 (button 2) should be purple");
        check(MultiRemoteItem.tintColor(remote, 1) == red, "Layer 1 (button 1) should keep its default red");
        check(MultiRemoteItem.tintColor(remote, 0) == -1 && MultiRemoteItem.tintColor(remote, 5) == -1,
                "The body and anything else shouldn't be tinted");

        var label = MultiRemoteItem.getButtons(remote).button(1).label(1);
        check(label.getStyle().getColor() != null
                        && label.getStyle().getColor().getValue() == RemoteButtons.textColor(DyeColor.PURPLE),
                "The button's label should be drawn in its color");
        for (DyeColor dye : DyeColor.values()) {
            int rgb = RemoteButtons.textColor(dye);
            double brightness = (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF)) / 255.0;
            check(brightness >= 0.39, dye + " labels would be too dark to read on a tooltip");
        }
        helper.succeed();
    }

    // ---- Radio Pager ----

    private static Player mockPlayerAt(GameTestHelper helper, BlockPos standAt) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(standAt));
        player.moveTo(feet.x, feet.y, feet.z);
        return player;
    }

    /** Puts a pager on {@code frequency} in the player's inventory (not their hand) and returns it. */
    private static ItemStack pagerFor(Player player, int frequency) {
        ItemStack pager = new ItemStack(ModRegistry.PAGER.get());
        PagerItem.setFrequency(pager, frequency);
        player.getInventory().setItem(5, pager);
        return player.getInventory().getItem(5);
    }

    /**
     * A pager in the inventory alerts once when a transmitter in reach turns on, says which frequency
     * and how strong, starts beeping, goes quiet when the signal stops, and alerts again next time.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void pagerAlertsWhenSignalTurnsOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(4, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 950);
        Player player = mockPlayerAt(helper, new BlockPos(1, 1, 1));
        ItemStack pager = pagerFor(player, 950);
        AtomicReference<Component> alert = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> check(PagerItem.checkSignal(level, player, pager) == null && PagerItem.lastSignal(pager) == 0,
                        "The pager should be quiet before the transmitter is powered"))
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    Component got = PagerItem.checkSignal(level, player, pager);
                    if (got != null) alert.set(got);
                    check(alert.get() != null, "No alert yet");
                })
                .thenExecute(() -> {
                    check(PagerItem.lastSignal(pager) == 15, "The pager should remember strength 15");
                    check(pager.getOrDefault(ModRegistry.PAGER_BEEPS.get(), 0) == PagerItem.BEEPS - 1, "The pager should start beeping");
                    var message = (TranslatableContents) alert.get().getContents();
                    check(message.getKey().endsWith("pager_alert") && Integer.valueOf(950).equals(message.getArgs()[1])
                            && Integer.valueOf(15).equals(message.getArgs()[2]), "The alert should name the frequency and strength");
                    check(PagerItem.checkSignal(level, player, pager) == null, "It should only alert once while the signal stays on");
                })
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.AIR))
                .thenWaitUntil(() -> {
                    PagerItem.checkSignal(level, player, pager);
                    check(PagerItem.lastSignal(pager) == 0, "The pager should go quiet");
                })
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(PagerItem.checkSignal(level, player, pager) != null, "It should alert again next time"))
                .thenSucceed();
    }

    /** A pager hears a Remote Control press made near its owner. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void pagerHearsRemotePress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player presser = playerWithRemote(helper, new BlockPos(1, 1, 1), 951, false);
        Player holder = mockPlayerAt(helper, new BlockPos(6, 1, 1));
        ItemStack pager = pagerFor(holder, 951);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressRemote(helper, presser))
                .thenWaitUntil(() -> check(PagerItem.checkSignal(level, holder, pager) != null, "The pager should alert"))
                .thenSucceed();
    }

    /** A powered transmitter whose reach doesn't cover the player leaves the pager quiet. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void pagerOutOfReachStaysQuiet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos standAt = new BlockPos(1, 1, 1);
        int reach = RadioHelper.reachAt(level, helper.absolutePos(standAt));
        BlockPos tx = new BlockPos(standAt.getX() + reach + 3, 1, 1);
        if (tx.getX() > 128) {
            throw new GameTestAssertException("Reach " + reach + " is too big for the test platform");
        }
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 952);
        helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK);
        Player player = mockPlayerAt(helper, standAt);
        ItemStack pager = pagerFor(player, 952);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> check(PagerItem.checkSignal(level, player, pager) == null && PagerItem.lastSignal(pager) == 0,
                        "A transmitter out of reach shouldn't set the pager off"))
                .thenSucceed();
    }

    /** A pager renamed in an anvil uses its name in the alert, and in silent mode it shows the message without beeping. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void pagerUsesItsNameAndCanBeSilenced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(4, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 953);
        helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK);
        Player player = mockPlayerAt(helper, new BlockPos(1, 1, 1));
        ItemStack pager = pagerFor(player, 953);
        pager.set(DataComponents.CUSTOM_NAME, Component.literal("Front Door"));
        PagerItem.setSilent(pager, true);
        AtomicReference<Component> alert = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Component got = PagerItem.checkSignal(level, player, pager);
                    if (got != null) alert.set(got);
                    check(alert.get() != null, "No alert yet");
                })
                .thenExecute(() -> {
                    var message = (TranslatableContents) alert.get().getContents();
                    check(((Component) message.getArgs()[0]).getString().equals("Front Door"), "The alert should use the pager's name");
                    check(!pager.has(ModRegistry.PAGER_BEEPS.get()), "A silent pager shouldn't beep");
                })
                .thenSucceed();
    }

    /** Pagers count toward a remote's "Heard by" when their owner is in range, and re-tuning re-arms the alert. */
    @GameTest(template = PLATFORM)
    public static void pagerCountsAsListenerAndRetuneReArms(GameTestHelper helper) {
        Player player = mockPlayerAt(helper, new BlockPos(1, 1, 1));
        ItemStack pager = pagerFor(player, 954);
        BlockPos origin = player.blockPosition();
        check(PagerItem.pagersHeard(player, 954, SignalNetwork.ownCoverage(origin, 10)) == 1, "A pager in range should count");
        check(PagerItem.pagersHeard(player, 955, SignalNetwork.ownCoverage(origin, 10)) == 0, "Another frequency shouldn't count");
        check(PagerItem.pagersHeard(player, 954, SignalNetwork.ownCoverage(origin.east(100), 10)) == 0,
                "A pager out of range shouldn't count");
        pager.set(ModRegistry.PAGER_SIGNAL.get(), 15);
        PagerItem.setFrequency(pager, 956);
        check(PagerItem.lastSignal(pager) == 0 && FrequencyTunerItem.getFrequency(pager) == 956,
                "Re-tuning should forget the old signal, so the new frequency alerts if it's already on");
        helper.succeed();
    }

    // ---- Signal Scanner ----

    /** Finds one frequency in a scan, or fails. */
    private static SignalNetwork.HeardFrequency heardOn(List<SignalNetwork.HeardFrequency> heard, int frequency) {
        return heard.stream().filter(h -> h.frequency() == frequency).findFirst()
                .orElseThrow(() -> new GameTestAssertException("Frequency " + frequency + " should be in the scan: " + heard));
    }

    /**
     * The scanner lists what a receiver where you stand would hear: transmitters whose reach covers
     * you (silent ones too, with "off"), with the distance and direction to them, strongest first.
     * A powered transmitter whose reach doesn't cover you isn't listed. (A fresh network far away,
     * so other tests can't add to it.)
     */
    @GameTest(template = PLATFORM)
    public static void scannerListsWhatYouCanHear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(5_000_000, 64, -5_000_000);
        network.updateTransmitter(here.north(10), 971, 0, 20); // silent, 10 blocks north
        network.updateTransmitter(here.east(30), 970, 15, 50); // on, 30 blocks east
        network.updateTransmitter(here.west(80), 972, 15, 50); // on, but too far for its reach
        List<SignalNetwork.HeardFrequency> heard = network.scanAt(level, here);
        check(heard.size() == 2, "Two frequencies should be heard, got " + heard);
        check(heard.get(0).equals(new SignalNetwork.HeardFrequency(970, 15, SignalNetwork.Source.TRANSMITTER, 1, 30, 2)),
                "The strongest (970, 30 blocks east) should come first, got " + heard.get(0));
        check(heard.get(1).equals(new SignalNetwork.HeardFrequency(971, 0, SignalNetwork.Source.TRANSMITTER, 1, 10, 0)),
                "The silent one (971, 10 blocks north) should come last, got " + heard.get(1));
        helper.succeed();
    }

    /** With several transmitters on one frequency, the scanner points to the strongest, and of those the nearest. */
    @GameTest(template = PLATFORM)
    public static void scannerPointsToStrongestThenNearest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(5_000_000, 64, -5_000_000);
        network.updateTransmitter(here.south(40), 973, 15, 100);
        network.updateTransmitter(here.west(5), 973, 7, 100);
        network.updateTransmitter(here.south(20).east(20), 973, 15, 100);
        SignalNetwork.HeardFrequency entry = heardOn(network.scanAt(level, here), 973);
        check(entry.power() == 15 && entry.transmitters() == 3, "Should be strength 15 from 3 transmitters, got " + entry);
        check(entry.distance() == 28 && entry.direction() == 3, "Should point 28 blocks south-east, got " + entry);
        helper.succeed();
    }

    /** Remote signals show as "remote", with no distance or direction, so the scanner never points at players. */
    @GameTest(template = PLATFORM)
    public static void scannerShowsRemotesWithoutPointingAtPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(5_000_000, 64, -5_000_000);
        network.toggleLatch(974, SignalNetwork.ownCoverage(here.east(4), 10));
        network.updateTransmitter(here.north(3), 974, 0, 20);
        network.pulse(975, SignalNetwork.ownCoverage(here.west(4), 10), level.getGameTime() + 100);
        network.toggleLatch(976, SignalNetwork.ownCoverage(here.east(500), 10)); // doesn't cover us
        List<SignalNetwork.HeardFrequency> heard = network.scanAt(level, here);
        check(heard.equals(List.of(
                        new SignalNetwork.HeardFrequency(974, 15, SignalNetwork.Source.REMOTE_TOGGLE, 1, -1, -1),
                        new SignalNetwork.HeardFrequency(975, 15, SignalNetwork.Source.REMOTE_PULSE, 0, -1, -1))),
                "Should hear the toggle (with its transmitter) and the pulse, nothing else: " + heard);
        helper.succeed();
    }

    /** A real placed transmitter shows up in a scan of the world, with the right distance and direction. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void scannerSeesPlacedTransmitter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(4, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 977);
        helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    SignalNetwork.HeardFrequency entry = heardOn(
                            SignalNetwork.get(level).scanAt(level, helper.absolutePos(new BlockPos(1, 1, 1))), 977);
                    check(entry.power() == 15 && entry.distance() == 3 && entry.direction() == 2,
                            "Should hear strength 15 from 3 blocks east, got " + entry);
                })
                .thenSucceed();
    }

    /** The Tune buttons work on the tuner, both remotes and the pager, and keep a button's other settings. */
    @GameTest(template = PLATFORM)
    public static void scannerTunesRadioItems(GameTestHelper helper) {
        check(SignalScannerItem.canTune(new ItemStack(ModRegistry.FREQUENCY_TUNER.get())), "Tuner");
        check(SignalScannerItem.canTune(new ItemStack(ModRegistry.REMOTE_CONTROL.get())), "Remote Control");
        check(SignalScannerItem.canTune(new ItemStack(ModRegistry.MULTI_REMOTE.get())), "Multi-Button Remote");
        check(SignalScannerItem.canTune(new ItemStack(ModRegistry.PAGER.get())), "Pager");
        check(!SignalScannerItem.canTune(new ItemStack(ModRegistry.SIGNAL_SCANNER.get()))
                && !SignalScannerItem.canTune(new ItemStack(Items.STICK)) && !SignalScannerItem.canTune(ItemStack.EMPTY),
                "Other items can't be tuned");
        RemoteButtons buttons = RemoteButtons.DEFAULT
                .withButton(2, new RemoteButtons.Button(3, true, 40, "Gate", DyeColor.PINK))
                .withSelected(2);
        RemoteButtons tuned = SignalScannerItem.retuneSelected(buttons, 978);
        check(tuned.button(2).equals(new RemoteButtons.Button(978, true, 40, "Gate", DyeColor.PINK)),
                "Only the selected button's frequency should change, got " + tuned.button(2));
        check(tuned.button(0).equals(buttons.button(0)), "Other buttons shouldn't change");
        helper.succeed();
    }

    // ---- Tuner favorites and scrolling ----

    /** Favorites always have exactly 5 slots; 0 means empty, and bad numbers are fixed. */
    @GameTest(template = PLATFORM)
    public static void tunerFavoritesKeepFiveSlots(GameTestHelper helper) {
        check(TunerPresets.EMPTY.slots().equals(List.of(0, 0, 0, 0, 0)) && !TunerPresets.EMPTY.hasAny(), "Empty favorites");
        TunerPresets messy = new TunerPresets(List.of(42, -7, Integer.MAX_VALUE, 0, 5, 99, 100));
        check(messy.slots().equals(List.of(42, 0, Frequency.MAX, 0, 5)),
                "Should keep 5 slots, empty the bad one and cap the huge one, got " + messy.slots());
        TunerPresets short1 = new TunerPresets(List.of(8));
        check(short1.slots().equals(List.of(8, 0, 0, 0, 0)), "Missing slots should be empty, got " + short1.slots());
        TunerPresets edited = TunerPresets.EMPTY.with(2, 77).with(4, 5).cleared(4);
        check(edited.slots().equals(List.of(0, 0, 77, 0, 0)) && edited.indexOf(77) == 2 && edited.indexOf(5) == -1,
                "Saving and clearing slots, got " + edited.slots());
        helper.succeed();
    }

    /** Jumping between favorites goes in slot order, skips empty slots and wraps around, both ways. */
    @GameTest(template = PLATFORM)
    public static void tunerFavoritesCycle(GameTestHelper helper) {
        TunerPresets presets = new TunerPresets(List.of(0, 42, 0, 7, 300));
        check(presets.next(42, false).getAsInt() == 7, "After 42 comes 7");
        check(presets.next(7, false).getAsInt() == 300, "After 7 comes 300");
        check(presets.next(300, false).getAsInt() == 42, "After 300 it wraps to 42");
        check(presets.next(42, true).getAsInt() == 300, "Before 42 (backwards) is 300");
        check(presets.next(1234, false).getAsInt() == 42, "From a frequency that isn't a favorite, down goes to the first");
        check(presets.next(1234, true).getAsInt() == 300, "From a frequency that isn't a favorite, up goes to the last");
        check(TunerPresets.EMPTY.next(42, false).isEmpty(), "No favorites, nowhere to jump");
        helper.succeed();
    }

    /** Favorites survive saving and network sync, and a tuner with none saved has no extra data. */
    @GameTest(template = PLATFORM)
    public static void tunerFavoritesSurviveSaveAndSync(GameTestHelper helper) {
        TunerPresets original = new TunerPresets(List.of(5, 0, 1_200_000, 0, 42));
        var saved = TunerPresets.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        check(TunerPresets.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().equals(original), "Saving changed the favorites");
        var buffer = Unpooled.buffer();
        TunerPresets.STREAM_CODEC.encode(buffer, original);
        check(TunerPresets.STREAM_CODEC.decode(buffer).equals(original), "Network sync changed the favorites");

        ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
        check(RadioFavorites.get(tuner).equals(TunerPresets.EMPTY), "A new tuner has no favorites");
        RadioFavorites.set(tuner, original);
        check(RadioFavorites.get(tuner).equals(original), "The tuner should keep its favorites");
        RadioFavorites.set(tuner, TunerPresets.EMPTY);
        check(!tuner.has(ModRegistry.TUNER_PRESETS.get()), "Clearing every favorite should remove the data");
        helper.succeed();
    }

    /** Sneak + scroll steps by 1 and stops at the ends; with Ctrl it jumps between favorites. */
    @GameTest(template = PLATFORM)
    public static void tunerScrollStepsAndJumps(GameTestHelper helper) {
        ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
        FrequencyTunerItem.setFrequency(tuner, 41);
        RadioFavorites.scroll(tuner, true, false);
        check(FrequencyTunerItem.getFrequency(tuner) == 42, "Scrolling up should go to 42");
        RadioFavorites.scroll(tuner, false, false);
        RadioFavorites.scroll(tuner, false, false);
        check(FrequencyTunerItem.getFrequency(tuner) == 40, "Scrolling down twice should go to 40");
        FrequencyTunerItem.setFrequency(tuner, Frequency.MIN);
        RadioFavorites.scroll(tuner, false, false);
        check(FrequencyTunerItem.getFrequency(tuner) == Frequency.MIN, "It shouldn't go below " + Frequency.MIN);
        FrequencyTunerItem.setFrequency(tuner, Frequency.MAX);
        RadioFavorites.scroll(tuner, true, false);
        check(FrequencyTunerItem.getFrequency(tuner) == Frequency.MAX, "It shouldn't go above " + Frequency.MAX);

        var none = (TranslatableContents) RadioFavorites.scroll(tuner, false, true).getContents();
        check(none.getKey().endsWith("no_favorites") && FrequencyTunerItem.getFrequency(tuner) == Frequency.MAX,
                "With no favorites, Ctrl-scrolling should say so and change nothing");

        RadioFavorites.set(tuner, new TunerPresets(List.of(5, 0, 77)));
        var jumped = (TranslatableContents) RadioFavorites.scroll(tuner, false, true).getContents();
        check(FrequencyTunerItem.getFrequency(tuner) == 5, "Ctrl-scrolling down should jump to the first favorite");
        check(jumped.getKey().endsWith("frequency_scrolled_favorite") && Integer.valueOf(1).equals(jumped.getArgs()[2]),
                "The message should say it's favorite 1");
        RadioFavorites.scroll(tuner, false, true);
        check(FrequencyTunerItem.getFrequency(tuner) == 77, "Next favorite after 5 is 77");
        RadioFavorites.scroll(tuner, true, false);
        var stepped = (TranslatableContents) RadioFavorites.scroll(tuner, false, false).getContents();
        check(FrequencyTunerItem.getFrequency(tuner) == 77 && stepped.getKey().endsWith("frequency_scrolled_favorite"),
                "Stepping back onto a favorite should say so");
        helper.succeed();
    }

    /**
     * Sneak + scroll and favorites work the same on the Remote Control and the pager: the remote
     * keeps its mode and pulse length, the pager re-arms its alert, and messages name the item.
     * The Multi-Button Remote isn't included (sneak + scroll picks its button).
     */
    @GameTest(template = PLATFORM)
    public static void remoteAndPagerScrollAndFavorites(GameTestHelper helper) {
        check(RadioFavorites.isScrollable(new ItemStack(ModRegistry.FREQUENCY_TUNER.get()))
                && RadioFavorites.isScrollable(new ItemStack(ModRegistry.REMOTE_CONTROL.get()))
                && RadioFavorites.isScrollable(new ItemStack(ModRegistry.PAGER.get())), "Tuner, remote and pager scroll");
        check(!RadioFavorites.isScrollable(new ItemStack(ModRegistry.MULTI_REMOTE.get()))
                && !RadioFavorites.isScrollable(new ItemStack(ModRegistry.SIGNAL_SCANNER.get())),
                "The Multi-Button Remote and scanner don't");

        ItemStack remote = new ItemStack(ModRegistry.REMOTE_CONTROL.get());
        FrequencyTunerItem.setFrequency(remote, 9);
        RemoteControlItem.setToggleMode(remote, true);
        RemoteControlItem.setPulseTicks(remote, 40);
        var message = (TranslatableContents) RadioFavorites.scroll(remote, true, false).getContents();
        check(FrequencyTunerItem.getFrequency(remote) == 10, "The remote should step up to 10");
        check(message.getKey().endsWith("frequency_scrolled")
                        && ((Component) message.getArgs()[0]).getString().equals(remote.getHoverName().getString()),
                "The message should name the remote");
        RadioFavorites.set(remote, new TunerPresets(List.of(0, 300, 0, 0, 12)));
        RadioFavorites.scroll(remote, false, true);
        check(FrequencyTunerItem.getFrequency(remote) == 300, "Ctrl-scrolling the remote should jump to its first favorite");
        RadioFavorites.scroll(remote, true, true);
        check(FrequencyTunerItem.getFrequency(remote) == 12, "Ctrl-scrolling up from 300 wraps to 12");
        check(RemoteControlItem.isToggleMode(remote) && RemoteControlItem.getPulseTicks(remote) == 40,
                "Scrolling shouldn't change the remote's mode or pulse length");

        ItemStack pager = new ItemStack(ModRegistry.PAGER.get());
        PagerItem.setFrequency(pager, 5);
        pager.set(ModRegistry.PAGER_SIGNAL.get(), 15);
        RadioFavorites.scroll(pager, false, false);
        check(FrequencyTunerItem.getFrequency(pager) == 4 && PagerItem.lastSignal(pager) == 0,
                "Scrolling the pager should step to 4 and forget the old signal, so it alerts if 4 is on");
        RadioFavorites.set(pager, new TunerPresets(List.of(77)));
        RadioFavorites.scroll(pager, false, true);
        check(FrequencyTunerItem.getFrequency(pager) == 77, "Ctrl-scrolling the pager should jump to 77");
        helper.succeed();
    }

    /** The remote's and pager's tooltips list their favorites under the frequency and explain scrolling. */
    @GameTest(template = PLATFORM)
    public static void remoteAndPagerTooltipsShowFavorites(GameTestHelper helper) {
        for (ItemStack stack : List.of(new ItemStack(ModRegistry.REMOTE_CONTROL.get()), new ItemStack(ModRegistry.PAGER.get()),
                new ItemStack(ModRegistry.FREQUENCY_TUNER.get()))) {
            RadioFavorites.set(stack, new TunerPresets(List.of(5, 0, 42)));
            List<Component> lines = new java.util.ArrayList<>();
            stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, net.minecraft.world.item.TooltipFlag.NORMAL);
            List<String> keys = lines.stream().map(line -> line.getContents() instanceof TranslatableContents t ? t.getKey() : "").toList();
            int frequency = keys.indexOf("tooltip." + SignalTower.MOD_ID + ".frequency");
            int favorites = keys.indexOf("tooltip." + SignalTower.MOD_ID + ".favorites");
            check(frequency >= 0 && favorites == frequency + 1, stack.getHoverName().getString() + ": favorites should be right under the frequency, got " + keys);
            check(lines.get(favorites).getString().contains("5, 42"), stack.getHoverName().getString() + ": should list 5, 42");
            check(keys.contains("tooltip." + SignalTower.MOD_ID + ".frequency_scroll"), stack.getHoverName().getString() + ": should explain scrolling");
        }
        helper.succeed();
    }

    // ---- Pager chat log ----

    /** A test player that keeps the messages sent to it: action-bar ones in one list, chat ones in the other. */
    private static Player listeningPlayer(GameTestHelper helper, BlockPos standAt, List<Component> actionBar, List<Component> chat) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "pager-test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isLocalPlayer() {
                return true;
            }

            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                (overlay ? actionBar : chat).add(message);
            }
        };
        Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(standAt));
        player.moveTo(feet.x, feet.y, feet.z);
        return player;
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /**
     * A pager posts its alert in chat (with the day and time in front) as well as above the hotbar;
     * one with its chat log switched off only shows it above the hotbar.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void pagerPostsAlertsInChat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        place(helper, new BlockPos(4, 1, 1), ModRegistry.RADIO_TRANSMITTER.get(), 957);
        helper.setBlock(new BlockPos(4, 1, 0), Blocks.REDSTONE_BLOCK);
        place(helper, new BlockPos(6, 1, 1), ModRegistry.RADIO_TRANSMITTER.get(), 958);
        helper.setBlock(new BlockPos(6, 1, 0), Blocks.REDSTONE_BLOCK);
        List<Component> barA = new java.util.ArrayList<>(), chatA = new java.util.ArrayList<>();
        List<Component> barB = new java.util.ArrayList<>(), chatB = new java.util.ArrayList<>();
        Player playerA = listeningPlayer(helper, new BlockPos(1, 1, 1), barA, chatA);
        Player playerB = listeningPlayer(helper, new BlockPos(1, 1, 1), barB, chatB);
        ItemStack logging = pagerFor(playerA, 957);
        ItemStack quiet = pagerFor(playerB, 958);
        PagerItem.setChatLog(quiet, false);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    PagerItem.checkSignal(level, playerA, logging);
                    PagerItem.checkSignal(level, playerB, quiet);
                    check(!barA.isEmpty() && !barB.isEmpty(), "Both pagers should alert");
                })
                .thenExecute(() -> {
                    check(barA.size() == 1 && keyOf(barA.get(0)).endsWith("pager_alert"), "The alert should show above the hotbar");
                    check(chatA.size() == 1, "The alert should also go in chat once, got " + chatA.size());
                    Component line = chatA.get(0);
                    check(keyOf(line).endsWith("pager_log") && keyOf(line.getSiblings().get(0)).endsWith("pager_alert"),
                            "The chat line should be the day and time, then the alert: " + line.getString());
                    check(barB.size() == 1 && chatB.isEmpty(), "With the chat log off, nothing should go in chat");
                })
                .thenSucceed();
    }

    /** The chat line's day and time come from the world's day time, with days starting at midnight. */
    @GameTest(template = PLATFORM)
    public static void pagerChatLineShowsDayAndTime(GameTestHelper helper) {
        Component alert = Component.literal("Front Door: frequency 5 is ON (strength 15)");
        long[][] cases = {{0, 1, 600}, {18000, 2, 0}, {23999, 2, 559}, {4 * 24000 + 6500, 5, 1230}, {6000, 1, 1200}};
        for (long[] c : cases) {
            var stamp = (TranslatableContents) PagerItem.chatLine(c[0], alert).getContents();
            String time = String.format(java.util.Locale.ROOT, "%02d:%02d", c[2] / 100, c[2] % 100);
            check(String.valueOf(stamp.getArgs()[0]).equals(String.valueOf(c[1])) && time.equals(stamp.getArgs()[1]),
                    "Day time " + c[0] + " should be Day " + c[1] + ", " + time + ", got Day " + stamp.getArgs()[0] + ", " + stamp.getArgs()[1]);
        }
        check(PagerItem.chatLine(0, alert).getString().endsWith("Front Door: frequency 5 is ON (strength 15)"),
                "The alert should follow the time stamp");
        helper.succeed();
    }

    /** The chat log is on by default, can be switched off and on, and the tooltip says which. */
    @GameTest(template = PLATFORM)
    public static void pagerChatLogSettingAndTooltip(GameTestHelper helper) {
        ItemStack pager = new ItemStack(ModRegistry.PAGER.get());
        check(PagerItem.isChatLog(pager) && !pager.has(ModRegistry.PAGER_CHAT_LOG.get()), "New pagers log to chat, with no extra data");
        check(tooltipKeys(pager).contains("tooltip." + SignalTower.MOD_ID + ".pager_chat_on"), "The tooltip should say the chat log is on");
        PagerItem.setChatLog(pager, false);
        check(!PagerItem.isChatLog(pager) && tooltipKeys(pager).contains("tooltip." + SignalTower.MOD_ID + ".pager_chat_off"),
                "Switched off, and the tooltip should say so");
        PagerItem.setChatLog(pager, true);
        check(PagerItem.isChatLog(pager) && !pager.has(ModRegistry.PAGER_CHAT_LOG.get()), "Switched back on, with the data removed");
        helper.succeed();
    }

    private static List<String> tooltipKeys(ItemStack stack) {
        List<Component> lines = new java.util.ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, net.minecraft.world.item.TooltipFlag.NORMAL);
        return lines.stream().map(SignalTowerGameTests::keyOf).toList();
    }

    // ---- Scanner tracking arrow ----

    /** The scanner tracks the same transmitter it points to: the strongest reaching you, then the nearest. */
    @GameTest(template = PLATFORM)
    public static void scannerPicksTransmitterToTrack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(5_000_000, 64, -5_000_000);
        network.updateTransmitter(here.south(40), 984, 15, 100);
        network.updateTransmitter(here.west(5), 984, 7, 100);
        network.updateTransmitter(here.east(20), 984, 15, 100);
        network.updateTransmitter(here.north(90), 985, 15, 50); // doesn't reach
        check(network.strongestTransmitter(level, here, 984).equals(java.util.Optional.of(here.east(20))),
                "Should pick the strongest, nearest one (20 blocks east)");
        check(network.strongestTransmitter(level, here, 985).isEmpty(), "One whose reach doesn't cover you can't be tracked");
        check(network.strongestTransmitter(level, here, 986).isEmpty(), "Nothing on that frequency");
        check(network.transmitterFrequencyAt(here.west(5)).getAsInt() == 984 && network.transmitterFrequencyAt(here).isEmpty(),
                "Looking up a transmitter's frequency by position");
        helper.succeed();
    }

    /**
     * Tracking a placed transmitter stores where it is; re-tuning it to another frequency or
     * breaking it loses the track. Remote-only signals and empty frequencies can't be tracked.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void scannerTracksAndLosesTransmitter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(4, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 980);
        Player player = mockPlayerAt(helper, new BlockPos(1, 1, 1));
        ItemStack scanner = new ItemStack(ModRegistry.SIGNAL_SCANNER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, scanner);
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> remoteOnly = SignalNetwork.ownCoverage(player.blockPosition(), 10);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    check(keyOf(SignalScannerItem.track(level, player, scanner, 980)).endsWith("scanner_tracking"), "Should start tracking");
                    ScannerTarget target = SignalScannerItem.getTarget(scanner);
                    check(target != null && target.pos().equals(helper.absolutePos(tx)) && target.frequency() == 980
                            && target.dimension().equals(level.dimension()), "Should remember the transmitter's position: " + target);
                    check(SignalScannerItem.checkTarget(level, scanner) == null, "It's still there");
                })
                .thenExecute(() -> setFrequency(helper, tx, 981))
                .thenWaitUntil(() -> check(SignalScannerItem.checkTarget(level, scanner) != null, "Re-tuning it should lose the track"))
                .thenExecute(() -> {
                    check(SignalScannerItem.getTarget(scanner) == null, "The target should be gone");
                    SignalScannerItem.track(level, player, scanner, 981);
                    check(SignalScannerItem.getTarget(scanner) != null, "Tracking the new frequency");
                    helper.setBlock(tx, Blocks.AIR);
                    Component lost = SignalScannerItem.checkTarget(level, scanner);
                    check(lost != null && keyOf(lost).endsWith("scanner_lost") && SignalScannerItem.getTarget(scanner) == null,
                            "Breaking it should lose the track");

                    network.toggleLatch(983, remoteOnly);
                    check(keyOf(SignalScannerItem.track(level, player, scanner, 983)).endsWith("scanner_cant_track")
                            && SignalScannerItem.getTarget(scanner) == null, "A remote toggle has nothing to track");
                    network.toggleLatch(983, remoteOnly);
                    check(keyOf(SignalScannerItem.track(level, player, scanner, 982)).endsWith("scanner_cant_track"),
                            "An empty frequency has nothing to track");

                    scanner.set(ModRegistry.SCANNER_TARGET.get(), new ScannerTarget(1, helper.absolutePos(tx), level.dimension()));
                    SignalScannerItem.stopTracking(scanner);
                    check(SignalScannerItem.getTarget(scanner) == null, "Stopping should clear the target");
                })
                .thenSucceed();
    }

    /** The target survives saving and sync, and the arrow points the right way for each facing. */
    @GameTest(template = PLATFORM)
    public static void scannerTargetSavesAndArrowPoints(GameTestHelper helper) {
        ScannerTarget original = new ScannerTarget(42, new BlockPos(-120, 70, 3000), Level.NETHER);
        var saved = ScannerTarget.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        check(ScannerTarget.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().equals(original), "Saving changed the target");
        var buffer = Unpooled.buffer();
        ScannerTarget.STREAM_CODEC.encode(buffer, original);
        check(ScannerTarget.STREAM_CODEC.decode(buffer).equals(original), "Sync changed the target");

        // Standing at block (0, 0), facing south (yaw 0) unless noted.
        Object[][] cases = {
                {new BlockPos(0, 64, 10), 0F, 0F, "south while facing south: straight ahead"},
                {new BlockPos(10, 64, 0), 0F, -90F, "east while facing south: to the left"},
                {new BlockPos(-10, 64, 0), 0F, 90F, "west while facing south: to the right"},
                {new BlockPos(10, 64, 0), -90F, 0F, "east while facing east: straight ahead"},
                {new BlockPos(10, 64, 0), 270F, 0F, "east while facing east (yaw 270): straight ahead"},
                {new BlockPos(0, 64, 10), 90F, -90F, "south while facing west: to the left"},
        };
        for (Object[] c : cases) {
            float angle = ScannerTarget.relativeAngle(0.5, 0.5, (Float) c[1], (BlockPos) c[0]);
            check(Math.abs(angle - (Float) c[2]) < 0.5F, c[3] + ": expected " + c[2] + ", got " + angle);
        }
        float behind = ScannerTarget.relativeAngle(0.5, 0.5, 0F, new BlockPos(0, 64, -10));
        check(Math.abs(Math.abs(behind) - 180F) < 0.5F, "north while facing south: behind, got " + behind);
        helper.succeed();
    }

    // ---- Pager catch-up ----

    /**
     * The network remembers each time a frequency turns on: a transmitter going from off to on, a
     * remote pulse, a toggle switched on. Staying on, changing strength or switching off doesn't
     * count. It keeps the last 32 per frequency.
     */
    @GameTest(template = PLATFORM)
    public static void networkRemembersTurnOns(GameTestHelper helper) {
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(5_000_000, 64, -5_000_000);
        BlockPos tx = here.east(10);
        network.tick(100, 1000);
        network.updateTransmitter(tx, 991, 0, 50);  // placed, silent
        network.updateTransmitter(tx, 991, 15, 50); // on: 1
        network.updateTransmitter(tx, 991, 9, 50);  // still on, weaker
        network.tick(200, 1100);
        network.updateTransmitter(tx, 991, 0, 50);  // off
        network.updateTransmitter(tx, 991, 15, 50); // on again: 2
        network.pulse(991, SignalNetwork.ownCoverage(here, 10), 300); // 3
        check(network.toggleLatch(991, SignalNetwork.ownCoverage(here, 10)), "toggle on"); // 4
        check(!network.toggleLatch(991, SignalNetwork.ownCoverage(here, 10)), "toggle off");
        List<SignalNetwork.TurnOn> all = network.turnOnsSince(991, 0, here);
        check(all.size() == 4, "Should remember 4 turn-ons, got " + all.size());
        check(all.get(0).gameTime() == 100 && all.get(0).dayTime() == 1000 && all.get(1).gameTime() == 200,
                "Each should be stamped with the time it happened");
        check(network.turnOnsSince(991, 100, here).size() == 3, "Only the ones after the given time");
        check(network.turnOnsSince(991, 0, here.west(500)).isEmpty(), "Somewhere none of them reached");
        check(network.turnOnsSince(992, 0, here).isEmpty(), "Another frequency");
        for (int i = 0; i < 40; i++) {
            network.pulse(995, SignalNetwork.ownCoverage(here, 10), 300);
        }
        check(network.turnOnsSince(995, 0, here).size() == SignalNetwork.MAX_HISTORY, "Only the last 32 are kept");
        helper.succeed();
    }

    /** The remembered turn-ons are saved with the world. */
    @GameTest(template = PLATFORM)
    public static void turnOnsSurviveSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(5_000_000, 64, -5_000_000);
        network.tick(500, 7000);
        network.updateTransmitter(here.north(20), 996, 15, 40);
        network.tick(600, 7100);
        network.pulse(996, List.of(new SignalNetwork.Coverage(here, 10, false), new SignalNetwork.Coverage(here.south(30), 25, false)), 700);
        SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.turnOnsSince(996, 0, here).equals(network.turnOnsSince(996, 0, here)),
                "Loading changed the turn-ons: " + loaded.turnOnsSince(996, 0, here));
        check(loaded.turnOnsSince(996, 0, here.south(50)).size() == 1, "The pulse's second area should still reach 50 blocks south");
        helper.succeed();
    }

    /**
     * At login, each pager says how often its frequency turned on where the player is while they
     * were away, and when the last time was. If none heard anything, one line says so (but not in
     * a singleplayer world, which is paused while you're away).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void pagerCatchUpReportsWhatTurnedOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Component> bar = new java.util.ArrayList<>(), chat = new java.util.ArrayList<>();
        Player player = listeningPlayer(helper, new BlockPos(1, 1, 1), bar, chat);
        ItemStack heard = pagerFor(player, 993);
        heard.set(DataComponents.CUSTOM_NAME, Component.literal("Front Door"));
        ItemStack quiet = new ItemStack(ModRegistry.PAGER.get());
        PagerItem.setFrequency(quiet, 994);
        player.getInventory().setItem(6, quiet);
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> here = SignalNetwork.ownCoverage(player.blockPosition(), 10);

        helper.startSequence()
                .thenExecute(() -> PagerItem.markAway(player, level.getGameTime()))
                .thenIdle(2)
                .thenExecute(() -> {
                    network.pulse(993, here, level.getGameTime() + 2);
                    network.pulse(993, here, level.getGameTime() + 2);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    List<Component> lines = PagerItem.catchUp(level, player, true);
                    check(lines.size() == 1, "Only the pager that heard something should report, got " + lines.size());
                    var line = (TranslatableContents) lines.get(0).getContents();
                    check(line.getKey().endsWith("pager_catchup_many") && ((Component) line.getArgs()[0]).getString().equals("Front Door")
                                    && Integer.valueOf(993).equals(line.getArgs()[1]) && Integer.valueOf(2).equals(line.getArgs()[2]),
                            "Should say Front Door's frequency 993 turned on 2 times: " + lines.get(0).getString());
                    check(chat.size() == 1, "The report should go in chat");
                    check(!heard.has(ModRegistry.PAGER_AWAY_SINCE.get()) && !quiet.has(ModRegistry.PAGER_AWAY_SINCE.get()),
                            "Both pagers should forget the logout time");
                    check(PagerItem.catchUp(level, player, true).isEmpty(), "A second login check has nothing new");
                    PagerItem.markAway(player, level.getGameTime());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    List<Component> none = PagerItem.catchUp(level, player, true);
                    check(none.size() == 1 && keyOf(none.get(0)).endsWith("pager_catchup_none_many"),
                            "Nothing happened, so one line should say so");
                    PagerItem.markAway(player, level.getGameTime());
                    check(PagerItem.catchUp(level, player, false).isEmpty(), "In singleplayer, nothing is said");
                    PagerItem.markAway(player, level.getGameTime());
                })
                .thenIdle(2)
                .thenExecute(() -> network.pulse(993, here, level.getGameTime() + 2))
                .thenIdle(1)
                .thenExecute(() -> {
                    List<Component> once = PagerItem.catchUp(level, player, true);
                    check(once.size() == 1 && keyOf(once.get(0)).endsWith("pager_catchup_once"), "A single turn-on says \"once\"");
                })
                .thenSucceed();
    }

    /** Logging out stamps every pager in the inventory (off-hand too), and nothing else. */
    @GameTest(template = PLATFORM)
    public static void logoutStampsEveryPager(GameTestHelper helper) {
        Player player = mockPlayerAt(helper, new BlockPos(1, 1, 1));
        ItemStack inPocket = pagerFor(player, 1);
        ItemStack inOffhand = new ItemStack(ModRegistry.PAGER.get());
        player.setItemInHand(InteractionHand.OFF_HAND, inOffhand);
        ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
        player.getInventory().setItem(7, tuner);
        PagerItem.markAway(player, 12345L);
        check(Long.valueOf(12345L).equals(inPocket.get(ModRegistry.PAGER_AWAY_SINCE.get()))
                && Long.valueOf(12345L).equals(player.getOffhandItem().get(ModRegistry.PAGER_AWAY_SINCE.get())),
                "Both pagers should note the logout time");
        check(!tuner.has(ModRegistry.PAGER_AWAY_SINCE.get()), "Other items aren't touched");
        helper.succeed();
    }

    // ---- Radio Repeater ----

    /**
     * A repeater in a transmitter's reach sends the signal on with its own reach, at the same
     * strength; a second repeater carries it further. Only its own frequency is repeated.
     */
    @GameTest(template = PLATFORM)
    public static void repeaterPassesSignalAlong(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(7_000_000, 64, 7_000_000); // far away and unloaded: entries count as standing
        network.updateTransmitter(tx, 1301, 9, 50);
        check(network.listen(level, 1301, tx.east(90)).power() == 0, "Too far for the transmitter alone");
        network.updateRepeater(tx.east(40), 1301, 60);
        check(network.repeaterPower(level, tx.east(40)) == 9, "The repeater should hear the transmitter");
        SignalNetwork.Reception far = network.listen(level, 1301, tx.east(90));
        check(far.power() == 9 && far.transmittersInReach() == 1, "90 blocks east should hear 9 through the repeater, got " + far);
        check(network.listen(level, 1301, tx.east(130)).power() == 0, "Past the repeater's reach");
        network.updateRepeater(tx.east(95), 1301, 60);
        check(network.listen(level, 1301, tx.east(150)).power() == 9, "A second repeater carries it further");
        network.updateRepeater(tx.east(200), 1301, 60);
        check(network.repeaterPower(level, tx.east(200)) == 0, "A repeater nothing reaches stays quiet");
        network.updateRepeater(tx.east(45), 1302, 500);
        check(network.listen(level, 1302, tx.east(300)).power() == 0, "A repeater on another frequency doesn't pick it up");
        network.updateTransmitter(tx, 1301, 15, 50);
        check(network.listen(level, 1301, tx.east(150)).power() == 15, "A stronger signal is passed on as it is");
        network.updateTransmitter(tx, 1301, 0, 50);
        check(network.listen(level, 1301, tx.east(150)).power() == 0 && network.repeaterPower(level, tx.east(40)) == 0,
                "Switching the transmitter off switches the chain off");
        network.updateTransmitter(tx, 1301, 15, 50);
        network.removeRepeater(tx.east(40));
        check(network.listen(level, 1301, tx.east(150)).power() == 0, "Removing the first repeater breaks the chain");
        check(network.repeaterFrequencyAt(tx.east(95)).getAsInt() == 1301 && network.repeaterFrequencyAt(tx.east(40)).isEmpty()
                && network.towerFrequencyAt(tx).getAsInt() == 1301, "Looking up repeaters by position");
        helper.succeed();
    }

    /**
     * Two repeaters in each other's reach pass a signal back and forth only while a real source
     * feeds them: when it goes quiet, both go quiet. Each repeats the strongest signal it gets.
     */
    @GameTest(template = PLATFORM)
    public static void repeatersCantKeepEachOtherOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos a = new BlockPos(-7_000_000, 64, 7_000_000);
        BlockPos b = a.east(50);
        network.updateRepeater(a, 1303, 60);
        network.updateRepeater(b, 1303, 60);
        check(network.repeaterPower(level, a) == 0 && network.repeaterPower(level, b) == 0, "Nothing to repeat yet");
        network.updateTransmitter(a.west(30), 1303, 15, 40);
        check(network.repeaterPower(level, a) == 15 && network.repeaterPower(level, b) == 15, "The source reaches A, A reaches B");
        network.updateTransmitter(a.west(30), 1303, 0, 40);
        check(network.repeaterPower(level, a) == 0 && network.repeaterPower(level, b) == 0,
                "With the source quiet, the two must not keep each other on");

        network.tick(100, 0);
        network.pulse(1303, SignalNetwork.ownCoverage(b.east(20), 30), 104);
        check(network.repeaterPower(level, a) == 15 && network.listen(level, 1303, a.west(55)).power() == 15,
                "A remote pulse near B goes through B and A");
        network.tick(104, 0);
        check(network.repeaterPower(level, a) == 0 && network.repeaterPower(level, b) == 0, "When the pulse ends, so does the repeating");
        check(network.toggleLatch(1303, SignalNetwork.ownCoverage(b.east(20), 30)), "Toggle on");
        check(network.repeaterPower(level, a) == 15, "A toggle is repeated too");
        check(!network.toggleLatch(1303, SignalNetwork.ownCoverage(b.east(20), 30)), "Toggle off");
        check(network.repeaterPower(level, a) == 0, "And stops when switched off");

        network.updateTransmitter(a.west(30), 1303, 5, 40);  // reaches A only
        network.updateTransmitter(b.east(30), 1303, 12, 40); // reaches B only
        check(network.repeaterPower(level, a) == 12 && network.repeaterPower(level, b) == 12,
                "A gets the stronger 12 from B rather than its own 5");
        helper.succeed();
    }

    /** Repeaters are saved with the world. */
    @GameTest(template = PLATFORM)
    public static void repeatersSurviveSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(-7_000_000, 64, -7_000_000);
        network.updateTransmitter(tx, 1304, 11, 40);
        network.updateRepeater(tx.north(30), 1304, 70);
        SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.repeaterFrequencyAt(tx.north(30)).getAsInt() == 1304, "The repeater should be loaded back");
        check(loaded.listen(level, 1304, tx.north(95)).power() == 11, "And still repeat after loading");
        helper.succeed();
    }

    /**
     * A remote press counts the receivers that repeaters on its frequency carry it to, and the
     * message names them. Repeaters on other frequencies, or already relaying as towers, aren't added.
     */
    @GameTest(template = PLATFORM)
    public static void remoteReachesThroughRepeaters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(7_000_000, 64, -7_000_000);
        List<SignalNetwork.Coverage> own = SignalNetwork.ownCoverage(here, 30);
        network.updateRepeater(here.east(20), 1305, 50);
        network.updateRepeater(here.east(65), 1305, 50);
        network.updateRepeater(here.west(20), 1306, 50);
        network.trackReceiver(here.east(110), 1305);
        check(network.countReceivers(1305, own) == 0, "Out of the remote's own reach");
        List<SignalNetwork.Coverage> reached = network.withRepeaters(level, 1305, own);
        check(reached.size() == 3, "Both repeaters on 1305 should be added, got " + reached);
        check(network.countReceivers(1305, reached) == 1, "The far receiver hears it through two repeaters");
        List<SignalNetwork.Coverage> asTower = List.of(own.get(0), new SignalNetwork.Coverage(here.east(20), 50, true));
        check(network.withRepeaters(level, 1305, asTower).size() == 3, "A repeater already relaying as a tower isn't added twice");
        check(network.withRepeaters(level, 1307, own) == own, "No repeaters on that frequency: unchanged");
        check(keyOf(RemoteControlItem.describeReach(30, 1, 2)).endsWith("reach_plus_repeaters")
                && keyOf(RemoteControlItem.describeReach(30, 0, 1)).endsWith("reach_plus_one_repeater")
                && keyOf(RemoteControlItem.describeReach(30, 2, 0)).endsWith("reach_towers"), "Reach wording");
        helper.succeed();
    }

    /** A turn-on is remembered over the repeaters it reached, so a pager there catches up on it. */
    @GameTest(template = PLATFORM)
    public static void turnOnsReachThroughRepeaters(GameTestHelper helper) {
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(6_000_000, 64, 6_000_000);
        network.updateRepeater(tx.south(40), 1308, 60);
        network.updateTransmitter(tx, 1308, 15, 50);
        check(network.turnOnsSince(1308, -1, tx.south(90)).size() == 1, "90 blocks south heard it through the repeater");
        check(network.turnOnsSince(1308, -1, tx.south(110)).isEmpty(), "Past the repeater it didn't");
        helper.succeed();
    }

    /**
     * The Signal Scanner names a repeater as the source, with its distance and direction, can
     * track it, and lists a quiet repeater as off. An equally strong, equally near transmitter wins.
     */
    @GameTest(template = PLATFORM)
    public static void scannerShowsRepeaters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(6_000_000, 64, -6_000_000);
        network.updateTransmitter(tx, 1309, 13, 50);
        network.updateRepeater(tx.east(40), 1309, 60);
        network.updateRepeater(tx.west(500), 1310, 60);
        BlockPos here = tx.east(80);
        List<SignalNetwork.HeardFrequency> heard = network.scanAt(level, here);
        SignalNetwork.HeardFrequency entry = heard.stream().filter(h -> h.frequency() == 1309).findFirst().orElseThrow();
        check(entry.source() == SignalNetwork.Source.REPEATER && entry.power() == 13 && entry.distance() == 40
                && entry.direction() == 6 && entry.transmitters() == 1, "Should point 40 blocks west to the repeater, got " + entry);
        check(network.strongestTransmitter(level, here, 1309).equals(java.util.Optional.of(tx.east(40))), "The repeater can be tracked");
        SignalNetwork.HeardFrequency quiet = network.scanAt(level, tx.west(520)).stream()
                .filter(h -> h.frequency() == 1310).findFirst().orElseThrow();
        check(quiet.source() == SignalNetwork.Source.REPEATER && quiet.power() == 0, "A quiet repeater is listed as off");
        network.updateTransmitter(tx.east(120), 1309, 13, 50); // as strong and as near as the repeater
        check(network.strongestTransmitter(level, here, 1309).equals(java.util.Optional.of(tx.east(120))), "A transmitter wins a tie");
        helper.succeed();
    }

    /**
     * A placed Radio Repeater checks in with the network, lights up while it repeats, carries the
     * signal to a receiver the transmitter can't reach, relays remote presses as a tower, and stops
     * when re-tuned or broken.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void repeaterBlockRepeats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(1, 1, 1);
        int reach = RadioHelper.reachAt(level, helper.absolutePos(tx));
        BlockPos repeater = new BlockPos(tx.getX() + reach - 1, 1, 1);
        BlockPos rx = new BlockPos(tx.getX() + reach + 3, 1, 1);
        if (reach < 8 || rx.getX() > 128) {
            throw new GameTestAssertException("Reach " + reach + " doesn't fit the test platform");
        }
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1311);
        place(helper, repeater, ModRegistry.RADIO_REPEATER.get(), 1311);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1311);
        BlockPos repeaterAbs = helper.absolutePos(repeater);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SignalNetwork network = SignalNetwork.get(level);
                    check(network.repeaterFrequencyAt(repeaterAbs).orElse(-1) == 1311, "The repeater should check in");
                    helper.assertBlockProperty(repeater, com.reiko.signaltower.block.RadioRepeaterBlock.LIT, false);
                    assertOutput(helper, rx, 0);
                    BlockPos origin = helper.absolutePos(repeater.west(2));
                    check(network.remoteCoverage(level, origin, 5, true).stream()
                            .anyMatch(area -> area.tower() && area.origin().equals(repeaterAbs)), "A repeater relays remote presses like a tower");
                })
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenWaitUntil(() -> helper.assertBlockProperty(repeater, com.reiko.signaltower.block.RadioRepeaterBlock.LIT, true))
                .thenExecute(() -> setFrequency(helper, repeater, 1312))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenWaitUntil(() -> helper.assertBlockProperty(repeater, com.reiko.signaltower.block.RadioRepeaterBlock.LIT, false))
                .thenExecute(() -> setFrequency(helper, repeater, 1311))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> level.destroyBlock(repeaterAbs, true))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> check(SignalNetwork.get(level).repeaterFrequencyAt(repeaterAbs).isEmpty(), "Breaking it removes it"))
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_REPEATER_ITEM.get())
                                && Integer.valueOf(1311).equals(stack.get(ModRegistry.FREQUENCY.get()))),
                        "The dropped repeater should keep frequency 1311"))
                .thenSucceed();
    }

    /** A remote press with no towers relaying is carried by a repeater on its frequency, and the message counts it. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void repeaterCarriesRemotePress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos standAt = new BlockPos(1, 1, 1);
        int reach = RadioHelper.reachAt(level, helper.absolutePos(standAt));
        BlockPos repeater = new BlockPos(standAt.getX() + reach - 2, 1, 1);
        BlockPos rx = new BlockPos(standAt.getX() + reach + 3, 1, 1);
        if (reach < 8 || rx.getX() > 128) {
            throw new GameTestAssertException("Reach " + reach + " doesn't fit the test platform");
        }
        place(helper, repeater, ModRegistry.RADIO_REPEATER.get(), 1313);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1313);
        Player player = playerWithRemote(helper, standAt, 1313, true);
        AtomicReference<Component> message = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ItemStack remote = player.getMainHandItem();
                    BlockPos origin = player.blockPosition();
                    message.set(RemoteControlItem.sendSignal(level, player, remote, 1313, true, PulseLength.MIN_TICKS,
                            SignalNetwork.ownCoverage(origin, RadioHelper.reachAt(level, origin))));
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> {
                    var outer = (TranslatableContents) message.get().getContents();
                    var result = (TranslatableContents) ((Component) outer.getArgs()[0]).getContents();
                    check(keyOf((Component) result.getArgs()[1]).endsWith("reach_plus_one_repeater"),
                            "The message should count the repeater, got " + keyOf((Component) result.getArgs()[1]));
                    check(keyOf((Component) outer.getArgs()[1]).endsWith("remote_heard_one"), "Heard by the far receiver");
                    assertLight(helper, player, true);
                })
                .thenExecute(() -> pressWithoutRelays(helper, player)) // toggle off
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    // ---- Jade tooltip ----

    private static List<String> lineKeys(List<Component> lines) {
        return lines.stream().map(SignalTowerGameTests::keyOf).map(key -> key.substring(key.lastIndexOf('.') + 1)).toList();
    }

    private static String firstArg(Component line) {
        Object arg = ((TranslatableContents) line.getContents()).getArgs()[0];
        return arg instanceof Component component ? component.getString() : String.valueOf(arg);
    }

    /**
     * What Jade shows: the server reads each block's frequency, signal, reach and towers in range
     * (and, for a mast, what's on top of its column), and the client turns that into lines.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void jadeInfoForRadioBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos mast = new BlockPos(3, 1, 1);
        BlockPos tx = mast.above(3);
        BlockPos rx = new BlockPos(6, 1, 1);
        BlockPos repeater = new BlockPos(9, 1, 1);
        BlockPos bareMast = new BlockPos(12, 1, 1);
        for (int y = 0; y < 3; y++) {
            helper.setBlock(mast.above(y), ModRegistry.RADIO_MAST.get());
        }
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1401);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1401);
        place(helper, repeater, ModRegistry.RADIO_REPEATER.get(), 1401);
        helper.setBlock(bareMast, ModRegistry.RADIO_MAST.get());
        helper.setBlock(mast.west(), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenWaitUntil(() -> helper.assertBlockProperty(repeater, com.reiko.signaltower.block.RadioRepeaterBlock.LIT, true))
                .thenExecute(() -> {
                    CompoundTag txInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx));
                    check(txInfo != null && txInfo.getInt("frequency") == 1401 && txInfo.getInt("power") == 15
                            && txInfo.getInt("reach") == RadioHelper.reachAt(level, helper.absolutePos(tx)), "Transmitter data: " + txInfo);
                    List<Component> txLines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), txInfo);
                    check(lineKeys(txLines).equals(List.of("frequency", "sending", "reach")), "Transmitter lines: " + lineKeys(txLines));
                    check(firstArg(txLines.get(0)).equals("1401") && firstArg(txLines.get(1)).equals("15"), "Transmitter values");

                    CompoundTag rxInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(rx));
                    check(rxInfo != null && rxInfo.getInt("power") == 15 && rxInfo.getInt("towers") == 2,
                            "Receiver data (the transmitter and the repeater reach it): " + rxInfo);
                    List<Component> rxLines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), rxInfo);
                    check(lineKeys(rxLines).equals(List.of("frequency", "output", "towers")), "Receiver lines: " + lineKeys(rxLines));

                    CompoundTag repInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(repeater));
                    check(repInfo != null && repInfo.getInt("power") == 15 && repInfo.getInt("frequency") == 1401, "Repeater data: " + repInfo);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(repeater), repInfo))
                            .equals(List.of("frequency", "repeating", "reach")), "Repeater lines");

                    CompoundTag mastInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(mast));
                    check(mastInfo != null && mastInfo.getString("top").equals("transmitter") && mastInfo.getInt("above") == 3
                            && mastInfo.getInt("frequency") == 1401, "Mast data: " + mastInfo);
                    List<Component> mastLines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(mast), mastInfo);
                    check(lineKeys(mastLines).equals(List.of("mast_top", "frequency")), "Mast lines: " + lineKeys(mastLines));

                    CompoundTag bare = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(bareMast));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(bareMast), bare))
                            .equals(List.of("mast_no_top")), "A mast with nothing on top says so");
                    check(com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(mast.west())) == null,
                            "Other blocks get nothing");

                    // Without server data (Jade only on the client): just what the block state shows.
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), null)).equals(List.of("broadcasting")),
                            "Transmitter without server data");
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), null)).equals(List.of("output")),
                            "Receiver without server data");
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(repeater), null)).equals(List.of("repeating_on")),
                            "Repeater without server data");
                    check(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(mast), null).isEmpty(), "Mast without server data");
                })
                .thenSucceed();
    }

    /** Quiet blocks: "Sending: nothing", "Output: off" and "Nothing to repeat", with or without server data. */
    @GameTest(template = PLATFORM)
    public static void jadeLinesWhenQuiet(GameTestHelper helper) {
        CompoundTag data = new CompoundTag();
        data.putInt("frequency", 5);
        data.putInt("power", 0);
        data.putInt("reach", 40);
        data.putInt("towers", 0);
        BlockState tx = ModRegistry.RADIO_TRANSMITTER.get().defaultBlockState();
        BlockState rx = ModRegistry.RADIO_RECEIVER.get().defaultBlockState();
        BlockState repeater = ModRegistry.RADIO_REPEATER.get().defaultBlockState();
        BlockState compact = ModRegistry.COMPACT_RADIO_RECEIVER.get().defaultBlockState();
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(tx, data)).equals(List.of("frequency", "sending_nothing", "reach")), "Quiet transmitter");
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(rx, data)).equals(List.of("frequency", "output_off", "towers")), "Quiet receiver");
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(compact, data)).equals(List.of("frequency", "output_off", "towers")),
                "The compact receiver shows the same as the full one");
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(repeater, data)).equals(List.of("frequency", "repeating_nothing", "reach")), "Quiet repeater");
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(tx, null)).equals(List.of("sending_nothing")), "Quiet transmitter, no server data");
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(repeater, null)).equals(List.of("repeating_nothing")), "Quiet repeater, no server data");
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(Blocks.STONE.defaultBlockState(), data)).isEmpty(), "Not a radio block");
        helper.succeed();
    }

    // ---- Radio Converter ----

    /**
     * A converter hears one frequency and sends it on another, at the same strength. It isn't
     * heard on the frequency it listens on, and receivers on its output frequency re-scan as soon
     * as its input changes (their revision goes up too).
     */
    @GameTest(template = PLATFORM)
    public static void converterChangesFrequency(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(8_000_000, 64, 8_000_000);
        network.updateTransmitter(tx, 1501, 9, 50);
        network.updateRepeater(tx.east(40), 1501, 1502, 60);
        check(network.listen(level, 1502, tx.east(90)).power() == 9, "90 blocks east should hear 9 on 1502");
        check(network.listen(level, 1502, tx.east(20)).power() == 9, "Anywhere in the converter's reach");
        check(network.listen(level, 1501, tx.east(90)).power() == 0, "It doesn't send on the frequency it listens on");
        SignalNetwork.Repeater converter = network.repeaterAt(tx.east(40));
        check(converter != null && converter.converts() && converter.output() == 1502, "Stored as a converter: " + converter);
        check(network.repeaterFrequencyAt(tx.east(40)).getAsInt() == 1501 && network.towerFrequencyAt(tx.east(40)).getAsInt() == 1502,
                "Listens on 1501, sends on 1502");
        long before = network.revision(1502);
        network.updateTransmitter(tx, 1501, 0, 50);
        check(network.revision(1502) > before, "A change on 1501 should bump 1502, so receivers there re-scan");
        check(network.listen(level, 1502, tx.east(90)).power() == 0 && network.repeaterPower(level, tx.east(40)) == 0,
                "Source off: nothing to convert");
        network.updateRepeater(tx.east(40), 1501, 1501, 60); // turned back into a repeater
        network.updateTransmitter(tx, 1501, 9, 50);
        check(network.listen(level, 1501, tx.east(90)).power() == 9 && network.listen(level, 1502, tx.east(90)).power() == 0,
                "Re-set to send on its own frequency it repeats instead");
        helper.succeed();
    }

    /**
     * Two converters feeding each other (1503 to 1504 and back) only stay on while a real source
     * feeds them, and a repeater in the chain passes the converted signal on.
     */
    @GameTest(template = PLATFORM)
    public static void converterLoopsCantLatch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos a = new BlockPos(-8_000_000, 64, 8_000_000);
        BlockPos b = a.east(50);
        network.updateRepeater(a, 1503, 1504, 60);
        network.updateRepeater(b, 1504, 1503, 60);
        network.updateRepeater(b.east(5), 1504, 60); // a repeater on 1504, in A's reach (B sends on 1503)
        network.updateTransmitter(a.west(30), 1503, 15, 40); // reaches A only
        check(network.repeaterPower(level, a) == 15 && network.repeaterPower(level, b) == 15, "Source to A (1503), A to B (1504)");
        check(network.listen(level, 1503, b.east(40)).power() == 15, "B sends it back on 1503");
        check(network.listen(level, 1504, b.east(60)).power() == 15, "The repeater carries 1504 further");
        network.updateTransmitter(a.west(30), 1503, 0, 40);
        check(network.repeaterPower(level, a) == 0 && network.repeaterPower(level, b) == 0
                        && network.repeaterPower(level, b.east(5)) == 0,
                "With the source quiet, the loop must not keep itself on");
        check(network.listen(level, 1503, b.east(40)).power() == 0 && network.listen(level, 1504, b.east(60)).power() == 0,
                "Nothing heard anywhere");
        helper.succeed();
    }

    /** A remote press is carried across a converter: its receivers on the other frequency count, and the message names it. */
    @GameTest(template = PLATFORM)
    public static void remoteReachesThroughConverters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(8_000_000, 64, -8_000_000);
        List<SignalNetwork.Coverage> own = SignalNetwork.ownCoverage(here, 30);
        network.updateRepeater(here.east(20), 1507, 1508, 50);
        network.updateRepeater(here.east(65), 1508, 50); // repeats 1508
        network.trackReceiver(here.east(110), 1508);
        network.trackReceiver(here.east(10), 1507);
        SignalNetwork.Carried carried = network.carried(level, 1507, own);
        check(carried.converters() == 1 && carried.repeaters() == 1, "1 converter and 1 repeater, got " + carried);
        check(carried.on(1507).equals(own), "Nothing extra on 1507");
        check(carried.on(1508).size() == 2, "The converter's and the repeater's areas on 1508: " + carried.on(1508));
        check(network.countReceivers(1508, carried.on(1508)) == 1 && network.countReceivers(1507, carried.on(1507)) == 1,
                "One receiver on each frequency");
        check(network.withRepeaters(level, 1507, own).equals(own), "withRepeaters stays on its own frequency");
        check(keyOf(RemoteControlItem.describeReach(30, 0, 0, 1)).endsWith("reach_plus_one_converter")
                && keyOf(RemoteControlItem.describeReach(30, 0, 1, 2)).endsWith("reach_plus_converters"), "Reach wording");
        helper.succeed();
    }

    /** A turn-on carried across a converter is remembered on the converted frequency too, for the pager catch-up. */
    @GameTest(template = PLATFORM)
    public static void turnOnsReachThroughConverters(GameTestHelper helper) {
        SignalNetwork network = new SignalNetwork();
        BlockPos here = new BlockPos(-8_000_000, 64, -8_000_000);
        network.updateRepeater(here.south(20), 1509, 1510, 60);
        network.pulse(1509, SignalNetwork.ownCoverage(here, 30), 100);
        check(network.turnOnsSince(1509, -1, here).size() == 1, "Remembered on 1509");
        check(network.turnOnsSince(1510, -1, here.south(70)).size() == 1, "And on 1510, where the converter sent it");
        check(network.turnOnsSince(1510, -1, here.south(90)).isEmpty(), "Not past the converter's reach");
        helper.succeed();
    }

    /** The scanner lists a converter under the frequency it sends on, as "Converter", and can track it. */
    @GameTest(template = PLATFORM)
    public static void scannerShowsConverters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(9_000_000, 64, 9_000_000);
        network.updateTransmitter(tx, 1511, 12, 50);
        network.updateRepeater(tx.east(40), 1511, 1512, 60);
        BlockPos here = tx.east(90);
        List<SignalNetwork.HeardFrequency> heard = network.scanAt(level, here);
        check(heard.size() == 1, "Only 1512 is heard here, got " + heard);
        SignalNetwork.HeardFrequency entry = heard.get(0);
        check(entry.frequency() == 1512 && entry.source() == SignalNetwork.Source.CONVERTER && entry.power() == 12
                && entry.distance() == 50 && entry.direction() == 6, "Should point 50 blocks west to the converter, got " + entry);
        check(network.strongestTransmitter(level, here, 1512).equals(java.util.Optional.of(tx.east(40))), "It can be tracked");
        helper.succeed();
    }

    /** Converters are saved with their output frequency; repeaters saved by 0.20.0 (no output) still load as repeaters. */
    @GameTest(template = PLATFORM)
    public static void convertersSurviveSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork();
        BlockPos tx = new BlockPos(-9_000_000, 64, 9_000_000);
        network.updateTransmitter(tx, 1516, 11, 40);
        network.updateRepeater(tx.north(30), 1516, 1517, 70);
        CompoundTag saved = network.save(new CompoundTag(), level.registryAccess());
        SignalNetwork loaded = SignalNetwork.load(saved, level.registryAccess());
        check(loaded.towerFrequencyAt(tx.north(30)).getAsInt() == 1517, "The output frequency should be loaded back");
        check(loaded.listen(level, 1517, tx.north(95)).power() == 11, "And still convert after loading");

        CompoundTag old = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putLong("pos", tx.south(30).asLong());
        entry.putInt("frequency", 1516);
        entry.putInt("reach", 70);
        list.add(entry);
        old.put("repeaters", list);
        SignalNetwork fromOld = SignalNetwork.load(old, level.registryAccess());
        SignalNetwork.Repeater repeater = fromOld.repeaterAt(tx.south(30));
        check(repeater != null && !repeater.converts() && repeater.output() == 1516, "A 0.20.0 repeater loads as a repeater: " + repeater);
        helper.succeed();
    }

    /**
     * A placed Radio Converter on a mast: set from its screen's packet (only by a player who can
     * reach it), it lights a receiver on its output frequency, shows in Jade (and on the mast
     * under it), and keeps both frequencies on the dropped item and when placed again.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void converterBlockConverts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos mast = new BlockPos(5, 1, 1);
        BlockPos converter = mast.above();
        BlockPos rx = new BlockPos(9, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1513);
        helper.setBlock(mast, ModRegistry.RADIO_MAST.get());
        place(helper, converter, ModRegistry.RADIO_CONVERTER.get(), 1513);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1514);
        helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK);
        BlockPos converterAbs = helper.absolutePos(converter);
        Player near = mockPlayerAt(helper, converter.west(2).below());
        Player far = mockPlayerAt(helper, new BlockPos(120, 1, 1));

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    assertOutput(helper, rx, 0); // a new converter sends on its default frequency
                    check(!com.reiko.signaltower.network.ConfigureConverterPayload.apply(far,
                            new com.reiko.signaltower.network.ConfigureConverterPayload(converterAbs, 1513, 1514)), "Too far away to set it");
                    check(com.reiko.signaltower.network.ConfigureConverterPayload.apply(near,
                            new com.reiko.signaltower.network.ConfigureConverterPayload(converterAbs, 1513, 1514)), "Set from nearby");
                    check(!com.reiko.signaltower.network.ConfigureConverterPayload.apply(near,
                            new com.reiko.signaltower.network.ConfigureConverterPayload(helper.absolutePos(rx), 1513, 1514)),
                            "Only a converter can be set this way");
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenWaitUntil(() -> helper.assertBlockProperty(converter, com.reiko.signaltower.block.RadioRepeaterBlock.LIT, true))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, converterAbs);
                    check(info != null && info.getInt("frequency") == 1513 && info.getInt("output") == 1514 && info.getInt("power") == 15,
                            "Converter data: " + info);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(converter), info))
                            .equals(List.of("listens", "sends", "converting", "reach")), "Converter lines");
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(converter), null))
                            .equals(List.of("converting_on")), "Converter without server data");
                    CompoundTag mastInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(mast));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(mast), mastInfo))
                            .equals(List.of("mast_top", "listens", "sends")) && mastInfo.getString("top").equals("converter"),
                            "Mast under a converter: " + mastInfo);
                })
                .thenExecute(() -> com.reiko.signaltower.network.ConfigureConverterPayload.apply(near,
                        new com.reiko.signaltower.network.ConfigureConverterPayload(converterAbs, 1513, 1515)))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> level.destroyBlock(converterAbs, true))
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_CONVERTER_ITEM.get())
                                && Integer.valueOf(1513).equals(stack.get(ModRegistry.FREQUENCY.get()))
                                && Integer.valueOf(1515).equals(stack.get(ModRegistry.OUTPUT_FREQUENCY.get()))),
                        "The dropped converter should keep 1513 and 1515"))
                .thenExecute(() -> {
                    ItemStack dropped = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                            .filter(stack -> stack.is(ModRegistry.RADIO_CONVERTER_ITEM.get())).findFirst().orElseThrow();
                    BlockPos again = new BlockPos(12, 1, 1);
                    helper.setBlock(again, ModRegistry.RADIO_CONVERTER.get());
                    com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity be = helper.getBlockEntity(again);
                    be.applyComponentsFromItemStack(dropped);
                    check(be.getFrequency() == 1513 && be.getOutputFrequency() == 1515 && be.getSendFrequency() == 1515,
                            "Placed again, it keeps both frequencies");
                    com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity plain = helper.getBlockEntity(new BlockPos(12, 1, 1));
                    check(plain.isConverter(), "It's a converter");
                })
                .thenSucceed();
    }

    // ---- Radio Toggle Receiver ----

    /**
     * Each time the transmitter turns on, the toggle receiver flips; turning off does nothing. Its
     * state is saved, and Jade shows it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void toggleReceiverFlipsOnEachTurnOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos rx = inReach(helper, tx, 10);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1601);
        place(helper, rx, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 1601);
        BlockPos power = tx.north();

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK)) // on: flips on
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> helper.setBlock(power, Blocks.AIR))            // off: stays on
                .thenIdle(5)
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> {
                    com.reiko.signaltower.blockentity.RadioReceiverBlockEntity be = helper.getBlockEntity(rx);
                    check(be.isToggle() && be.isToggledOn(), "Should be switched on");
                    CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
                    check(saved.getBoolean("toggled_on"), "The state should be saved: " + saved);
                    var copy = new com.reiko.signaltower.blockentity.RadioReceiverBlockEntity(helper.absolutePos(rx), helper.getBlockState(rx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.isToggledOn() && copy.getFrequency() == 1601, "And loaded back");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(rx));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info))
                            .equals(List.of("frequency", "switched_on", "hearing_nothing", "towers")), "Jade lines while on, input quiet");
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), null))
                            .equals(List.of("switched_on")), "Jade without server data");
                })
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK)) // on: flips off
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(rx));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info))
                            .equals(List.of("frequency", "switched_off", "hearing", "towers")), "Jade lines while off, hearing 15");
                    com.reiko.signaltower.blockentity.RadioReceiverBlockEntity be = helper.getBlockEntity(rx);
                    check(!be.isToggledOn() && !be.saveWithFullMetadata(level.registryAccess()).contains("toggled_on"), "Off isn't stored");
                })
                .thenExecute(() -> helper.setBlock(power, Blocks.AIR))            // off: stays off
                .thenIdle(5)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK)) // on: flips on again
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenSucceed();
    }

    /** A weak signal (14) still switches it fully on (15). */
    @GameTest(template = PLATFORM)
    public static void toggleReceiverOutputsFullStrength(GameTestHelper helper) {
        BlockPos tx = new BlockPos(4, 1, 1);
        BlockPos rx = inReach(helper, tx, 10);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1602);
        place(helper, rx, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 1602);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(1, 1, 1), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_WIRE); // 15
                    helper.setBlock(new BlockPos(3, 1, 1), Blocks.REDSTONE_WIRE); // 14 -> into the transmitter
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenSucceed();
    }

    /** Remote pulses switch it on with one press and off with the next, and the press counts it as heard. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void toggleReceiverFromRemotePulses(GameTestHelper helper) {
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 1603);
        Player player = playerWithRemote(helper, new BlockPos(1, 1, 1), 1603, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> pressWithoutRelays(helper, player))
                .thenExecute(() -> assertLight(helper, player, true))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(30) // the 1s pulse ends; it stays on
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> pressWithoutRelays(helper, player))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenIdle(30)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    /**
     * A frequency that's already on when the toggle receiver is placed, or re-tuned to it, isn't a
     * turn-on: it stays off until the next one.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void toggleReceiverIgnoresAlreadyOn(GameTestHelper helper) {
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos placedLater = inReach(helper, tx, 6);
        BlockPos retuned = inReach(helper, tx, 9);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1604);
        helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK);
        place(helper, retuned, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 1605); // quiet frequency for now
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> place(helper, placedLater, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 1604))
                .thenExecute(() -> setFrequency(helper, retuned, 1604))
                .thenIdle(10)
                .thenExecute(() -> {
                    assertOutput(helper, placedLater, 0);
                    assertOutput(helper, retuned, 0);
                })
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.AIR))
                .thenIdle(3)
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    assertOutput(helper, placedLater, 15);
                    assertOutput(helper, retuned, 15);
                })
                .thenSucceed();
    }

    // ---- Frequency names ----

    /** Names are tidied (spaces, formatting codes, length), removed by an empty name, capped in number, and saved. */
    @GameTest(template = PLATFORM)
    public static void frequencyNamesAreCleanedAndSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        com.reiko.signaltower.radio.FrequencyNames names = new com.reiko.signaltower.radio.FrequencyNames();
        check("Front Gate".equals(names.set(1701, "  Front Gate  ")), "Spaces trimmed");
        check(!names.set(1702, "§cRed Door").contains("§"), "Formatting codes dropped");
        check(names.set(1703, "A very long name that goes on and on").length() == com.reiko.signaltower.radio.FrequencyNames.MAX_LENGTH,
                "Cut to the maximum length");
        check("".equals(names.set(1702, "   ")) && names.get(1702) == null, "An empty name removes it");
        check(names.get(1701).equals("Front Gate"), "Kept");
        var loaded = com.reiko.signaltower.radio.FrequencyNames.load(names.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check("Front Gate".equals(loaded.get(1701)) && loaded.get(1702) == null && loaded.all().size() == 2, "Saved and loaded: " + loaded.all());
        var full = new com.reiko.signaltower.radio.FrequencyNames();
        for (int i = 0; i < com.reiko.signaltower.radio.FrequencyNames.MAX_NAMES; i++) {
            full.set(10_000 + i, "n" + i);
        }
        check(full.set(9_999, "one too many") == null, "No more than the maximum number of names");
        check("renamed".equals(full.set(10_000, "renamed")), "Renaming an existing one is still fine");

        var buffer = Unpooled.buffer();
        var payload = new com.reiko.signaltower.network.FrequencyNamesPayload(java.util.Map.of(1701, "Front Gate", 42, "Barn"));
        com.reiko.signaltower.network.FrequencyNamesPayload.STREAM_CODEC.encode(buffer, payload);
        check(com.reiko.signaltower.network.FrequencyNamesPayload.STREAM_CODEC.decode(buffer).names().equals(payload.names()), "The list survives the network");
        helper.succeed();
    }

    /** The client's copy: filled by the server's packets, changed one at a time, cleared on leaving. */
    @GameTest(template = PLATFORM)
    public static void frequencyNamesClientCopy(GameTestHelper helper) {
        com.reiko.signaltower.radio.FrequencyNames.clientReplaceAll(java.util.Map.of(1799, "Shed"));
        check("Shed".equals(com.reiko.signaltower.radio.FrequencyNames.nameOf(null, 1799)), "From the full list");
        check(keyOf((Component) com.reiko.signaltower.radio.FrequencyNames.label(null, 1799)).endsWith("named"), "Labels use it");
        com.reiko.signaltower.radio.FrequencyNames.clientSet(1798, "Barn");
        com.reiko.signaltower.radio.FrequencyNames.clientSet(1799, "");
        check(com.reiko.signaltower.radio.FrequencyNames.nameOf(null, 1799) == null
                && "Barn".equals(com.reiko.signaltower.radio.FrequencyNames.nameOf(null, 1798)), "One change at a time");
        com.reiko.signaltower.radio.FrequencyNames.clientClear();
        check(com.reiko.signaltower.radio.FrequencyNames.nameOf(null, 1798) == null, "Cleared on leaving");
        helper.succeed();
    }

    /**
     * Naming a frequency (only while holding a tuner) shows the name in messages built on the
     * server: the remote's press message, a pager's alert, the tuner's "tuned" message and Jade.
     * A frequency without a name shows just its number, as before.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void frequencyNameShowsInMessages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(1, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1704);
        Player player = mockPlayerAt(helper, new BlockPos(3, 1, 1));
        check(!com.reiko.signaltower.network.SetFrequencyNamePayload.apply(player,
                new com.reiko.signaltower.network.SetFrequencyNamePayload(1704, "Front Gate")), "Only with a tuner in hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
        check(com.reiko.signaltower.network.SetFrequencyNamePayload.apply(player,
                new com.reiko.signaltower.network.SetFrequencyNamePayload(1704, "Front Gate")), "Named with a tuner");
        check(!com.reiko.signaltower.network.SetFrequencyNamePayload.apply(player,
                new com.reiko.signaltower.network.SetFrequencyNamePayload(1704, "Front Gate")), "Same name again: nothing changes");
        check("Front Gate".equals(com.reiko.signaltower.radio.FrequencyNames.nameOf(level, 1704)), "Stored for the world");
        check(Integer.valueOf(1705).equals(com.reiko.signaltower.radio.FrequencyNames.label(level, 1705)), "No name: just the number");

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    try {
                        ItemStack remote = new ItemStack(ModRegistry.REMOTE_CONTROL.get());
                        FrequencyTunerItem.setFrequency(remote, 1704);
                        var outer = (TranslatableContents) RemoteControlItem.sendSignal(level, player, remote, 1704, false, PulseLength.MIN_TICKS,
                                SignalNetwork.ownCoverage(player.blockPosition(), 5)).getContents();
                        var result = (TranslatableContents) ((Component) outer.getArgs()[0]).getContents();
                        Component freq = (Component) result.getArgs()[1];
                        check(keyOf(freq).endsWith("named") && freq.getString().equals("1704 (Front Gate)"),
                                "The press message names it: " + freq.getString());

                        ItemStack pager = pagerFor(player, 1704);
                        Component alert = PagerItem.checkSignal(level, player, pager);
                        check(alert != null && alert.getString().contains("1704 (Front Gate)"), "The pager's alert names it: " + alert);

                        CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx));
                        check(info != null && "Front Gate".equals(info.getString("name")), "Jade's data carries the name");
                        Component line = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info).get(0);
                        check(line.getString().equals("Frequency: 1704 (Front Gate)"), "Jade line: " + line.getString());

                        BlockPos rxPos = new BlockPos(6, 1, 1);
                        place(helper, rxPos, ModRegistry.RADIO_RECEIVER.get(), 1);
                        ItemStack tuner = player.getMainHandItem();
                        FrequencyTunerItem.setFrequency(tuner, 1704);
                        List<Component> bar = new java.util.ArrayList<>();
                        Player listener = listeningPlayer(helper, new BlockPos(5, 1, 1), bar, new java.util.ArrayList<>());
                        listener.setItemInHand(InteractionHand.MAIN_HAND, tuner.copy());
                        BlockPos abs = helper.absolutePos(rxPos);
                        listener.getMainHandItem().getItem().useOn(new UseOnContext(listener, InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
                        check(!bar.isEmpty() && bar.get(bar.size() - 1).getString().contains("1704 (Front Gate)"),
                                "The tuner's message names it: " + bar);
                    } finally {
                        com.reiko.signaltower.network.SetFrequencyNamePayload.apply(player,
                                new com.reiko.signaltower.network.SetFrequencyNamePayload(1704, ""));
                    }
                    check(com.reiko.signaltower.radio.FrequencyNames.nameOf(level, 1704) == null, "Removed again");
                })
                .thenSucceed();
    }

    // ---- Private frequencies ----

    /** A test player with a chosen UUID and name (and operator or not) that keeps its messages, like {@link #listeningPlayer}. */
    private static Player namedPlayer(GameTestHelper helper, BlockPos standAt, UUID uuid, String name, boolean op,
                                      List<Component> actionBar, List<Component> chat) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(uuid, name)) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean hasPermissions(int level) {
                return op;
            }

            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                (overlay ? actionBar : chat).add(message);
            }
        };
        Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(standAt));
        player.moveTo(feet.x, feet.y, feet.z);
        return player;
    }

    private static String lastKey(List<Component> messages) {
        return messages.isEmpty() ? "(none)" : keyOf(messages.get(messages.size() - 1));
    }

    /**
     * Claims: owner and friends are allowed, the owner isn't their own friend, friends are capped,
     * and claims survive saving, the network (both packets) and the client's copy. The friends box
     * is split on commas and spaces without repeats.
     */
    @GameTest(template = PLATFORM)
    public static void privateFrequencyClaimsSavedAndSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        var claims = new com.reiko.signaltower.radio.FrequencyClaims();
        java.util.Map<UUID, String> many = new java.util.LinkedHashMap<>();
        many.put(owner, "Owner"); // left out: the owner isn't their own friend
        many.put(friend, "Friend");
        for (int i = 0; i < 30; i++) {
            many.put(UUID.randomUUID(), "F" + i);
        }
        var claim = claims.put(1801, owner, "Owner", many);
        check(claim.friends().size() == com.reiko.signaltower.radio.FrequencyClaims.MAX_FRIENDS, "Friends capped: " + claim.friends().size());
        check(!claim.friends().containsKey(owner) && claim.friendNames().get(0).equals("Friend"), "Owner left out, order kept");
        check(claims.allows(1801, owner) && claims.allows(1801, friend) && !claims.allows(1801, UUID.randomUUID()), "Owner and friend only");
        check(claims.allows(1802, UUID.randomUUID()) && claims.allows(1802, null), "A public frequency allows anyone");
        check(!claims.allows(1801, null), "Nobody (a press with no player) isn't a member");
        check(claims.countOwnedBy(owner) == 1 && claims.countOwnedBy(friend) == 0, "Counted by owner");

        var loaded = com.reiko.signaltower.radio.FrequencyClaims.load(claims.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.get(1801) != null && loaded.get(1801).equals(claims.get(1801)), "Saved and loaded");

        var info = com.reiko.signaltower.radio.FrequencyClaims.ClaimInfo.of(claim);
        var buffer = Unpooled.buffer();
        var all = new com.reiko.signaltower.network.FrequencyClaimsPayload(java.util.Map.of(1801, info));
        com.reiko.signaltower.network.FrequencyClaimsPayload.STREAM_CODEC.encode(buffer, all);
        check(com.reiko.signaltower.network.FrequencyClaimsPayload.STREAM_CODEC.decode(buffer).claims().equals(all.claims()), "The full list survives the network");
        var one = new com.reiko.signaltower.network.FrequencyClaimPayload(1801, java.util.Optional.empty());
        com.reiko.signaltower.network.FrequencyClaimPayload.STREAM_CODEC.encode(buffer, one);
        check(com.reiko.signaltower.network.FrequencyClaimPayload.STREAM_CODEC.decode(buffer).equals(one), "So does 'public again'");

        com.reiko.signaltower.radio.FrequencyClaims.clientReplaceAll(java.util.Map.of(1801, info));
        check("Owner".equals(com.reiko.signaltower.radio.FrequencyClaims.clientInfo(1801).ownerName()), "Client copy filled");
        com.reiko.signaltower.radio.FrequencyClaims.clientSet(1801, null);
        check(com.reiko.signaltower.radio.FrequencyClaims.clientInfo(1801) == null, "Client copy: public again");
        com.reiko.signaltower.radio.FrequencyClaims.clientClear();

        check(com.reiko.signaltower.network.SetFrequencyPrivacyPayload.parseNames(" Alex, sam  alex,,Bo ")
                .equals(List.of("Alex", "sam", "Bo")), "Names split and de-duplicated");
        helper.succeed();
    }

    /**
     * On a private frequency, transmitters, repeaters and converters tuned by someone else stay
     * quiet (receivers and the scanner don't hear them, and their turn-ons aren't remembered);
     * the owner's, friends' and old blocks with no recorded tuner still work. Taking a friend off
     * silences their blocks at once, and making it public brings everyone back.
     */
    @GameTest(template = PLATFORM)
    public static void privateFrequencySilencesOtherPlayersBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos here = new BlockPos(61_000, 100, 61_000); // far away and never loaded
        claims.put(1803, owner, "Owner", java.util.Map.of(friend, "Friend"));
        network.invalidate(1803);
        try {
            network.updateTransmitter(here, 1803, 9, 30, stranger);
            check(network.listen(level, 1803, here.east(5)).power() == 0
                    && network.listen(level, 1803, here.east(5)).transmittersInReach() == 0, "A stranger's transmitter is silent");
            check(network.isSilenced(level, here), "And says so");
            network.updateTransmitter(here.south(3), 1803, 7, 30, friend);
            network.updateTransmitter(here.north(3), 1803, 5, 30, null);
            var heard = network.listen(level, 1803, here.east(5));
            check(heard.power() == 7 && heard.transmittersInReach() == 2, "A friend's and an old one count: " + heard);
            check(!network.isSilenced(level, here.south(3)) && !network.isSilenced(level, here.north(3)), "Not silenced");
            var scan = network.scanAt(level, here.east(5)).stream().filter(h -> h.frequency() == 1803).findFirst().orElseThrow();
            check(scan.power() == 7 && scan.transmitters() == 2, "The scanner leaves the stranger out: " + scan);
            check(network.turnOnsSince(1803, -1, here).size() == 2, "Only the friend's and the old one's turn-ons are remembered");

            // A stranger's repeater listening on 1803 and a stranger's converter sending on it stay quiet too.
            BlockPos repeater = here.south(3).east(25);
            network.updateRepeater(repeater, 1803, 1803, 30, stranger);
            check(network.listen(level, 1803, repeater.east(25)).power() == 0, "The stranger's repeater doesn't pass it on");
            check(network.repeaterPower(level, repeater) == 0, "And isn't lit");
            BlockPos source = here.east(1000);
            network.updateTransmitter(source, 1804, 12, 30, stranger); // 1804 is public: fine
            BlockPos converter = source.east(20);
            network.updateRepeater(converter, 1804, 1803, 30, stranger);
            check(network.listen(level, 1803, converter.east(25)).power() == 0, "Nor does the stranger's converter");
            check(network.carried(level, 1804, SignalNetwork.ownCoverage(source, 30)).on(1803).isEmpty(),
                    "A press on 1804 isn't carried onto 1803 by it");

            var loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).attachTo(level);
            check(loaded.isSilenced(level, here) && loaded.isSilenced(level, converter) && !loaded.isSilenced(level, here.south(3)),
                    "Who tuned them is saved");

            network.updateRepeater(converter, 1804, 1803, 30, friend);
            network.updateRepeater(repeater, 1803, 1803, 30, friend);
            check(network.listen(level, 1803, converter.east(25)).power() == 12, "The friend's converter sends");
            check(network.listen(level, 1803, repeater.east(25)).power() == 7, "The friend's repeater repeats");

            claims.put(1803, owner, "Owner", java.util.Map.of());
            network.invalidate(1803);
            check(network.listen(level, 1803, here.east(5)).power() == 5, "Friend taken off: only the old one is left");
            check(network.listen(level, 1803, converter.east(25)).power() == 0, "Their converter went quiet at once");

            claims.remove(1803);
            network.invalidate(1803);
            check(network.listen(level, 1803, here.east(5)).power() == 9, "Public again: everyone counts");
            check(!network.isSilenced(level, here), "Nobody is silenced");
        } finally {
            claims.remove(1803);
        }
        helper.succeed();
    }

    /** Remote presses on a private frequency only count from the owner and friends; anyone else's is refused. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void privateFrequencyRefusesOtherPlayersRemotes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1805);
        claims.put(1805, ownerId, "Owner", java.util.Map.of(friendId, "Friend"));
        List<Component> bar = new java.util.ArrayList<>();
        Player stranger = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Stranger", false, bar, new java.util.ArrayList<>());
        Player owner = namedPlayer(helper, new BlockPos(1, 1, 1), ownerId, "Owner", false, bar, new java.util.ArrayList<>());
        Player friend = namedPlayer(helper, new BlockPos(1, 1, 1), friendId, "Friend", false, bar, new java.util.ArrayList<>());
        ItemStack remote = new ItemStack(ModRegistry.REMOTE_CONTROL.get());
        FrequencyTunerItem.setFrequency(remote, 1805);
        RemoteControlItem.setToggleMode(remote, true);
        AtomicReference<Component> answer = new AtomicReference<>();
        java.util.function.Consumer<Player> press = player -> answer.set(RemoteControlItem.sendSignal(level, player, remote, 1805, true,
                PulseLength.MIN_TICKS, SignalNetwork.ownCoverage(player.blockPosition(), 10)));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> press.accept(stranger))
                .thenExecute(() -> {
                    check(keyOf(answer.get()).endsWith("private_refused_send"), "Refused: " + answer.get().getString());
                    check(answer.get().getString().contains("Owner"), "Says whose it is: " + answer.get().getString());
                    RemoteLight light = remote.get(ModRegistry.REMOTE_LIGHT.get());
                    check(light != null && !light.heard(), "Red light");
                })
                .thenIdle(5)
                .thenExecute(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> press.accept(owner))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> press.accept(stranger)) // can't switch it off either
                .thenIdle(5)
                .thenExecute(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> press.accept(friend))
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> claims.remove(1805))
                .thenSucceed();
    }

    /**
     * The tuner screen's Private button and Friends box: only with a tuner in hand; only the owner
     * changes a private frequency, though an operator can make it public; offline friends are kept
     * by name, unknown names are reported; the per-player limit; no claiming a frequency other
     * players' blocks already send on. Renaming a private frequency is for members only.
     */
    @GameTest(template = PLATFORM)
    public static void privacyButtonClaimsAndReleases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        List<Component> chat = new java.util.ArrayList<>();
        Player alice = namedPlayer(helper, new BlockPos(1, 1, 1), aliceId, "Alice", false, bar, chat);
        Player bob = namedPlayer(helper, new BlockPos(1, 1, 1), bobId, "Bob", false, bar, chat);
        Player op = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Admin", true, bar, chat);
        Player dave = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Dave", false, bar, chat);
        java.util.function.BiFunction<Player, com.reiko.signaltower.network.SetFrequencyPrivacyPayload, Boolean> apply =
                com.reiko.signaltower.network.SetFrequencyPrivacyPayload::apply;
        BlockPos far = new BlockPos(62_000, 100, 62_000);
        int limit = com.reiko.signaltower.Config.PRIVATE_FREQUENCIES_PER_PLAYER.get();
        try {
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "")), "Only with a tuner in hand");
            for (Player player : List.of(alice, bob, op, dave)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
            }
            check(apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "")), "Alice makes it private");
            check(lastKey(bar).endsWith("private_set") && claims.get(1806).owner().equals(aliceId), "Hers: " + lastKey(bar));
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "")), "Again: nothing changes");

            check(!apply.apply(bob, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, false, "")), "Bob can't make it public");
            check(lastKey(bar).endsWith("private_not_yours"), "Told it's hers: " + lastKey(bar));
            check(!apply.apply(bob, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "Bob")), "Nor take it over");

            check(!com.reiko.signaltower.network.SetFrequencyNamePayload.apply(bob,
                    new com.reiko.signaltower.network.SetFrequencyNamePayload(1806, "Bob's")), "Bob can't rename it");
            check(lastKey(bar).endsWith("private_refused_name"), "Told why: " + lastKey(bar));
            check(com.reiko.signaltower.network.SetFrequencyNamePayload.apply(alice,
                    new com.reiko.signaltower.network.SetFrequencyNamePayload(1806, "Alice's Gate")), "Alice can");

            chat.clear();
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "Zed, alice")),
                    "Nobody online called Zed, and Alice isn't her own friend: nothing changes");
            check(lastKey(chat).endsWith("private_friends_missing") && chat.get(0).getString().contains("Zed"), "Zed reported: " + chat);

            claims.put(1806, aliceId, "Alice", java.util.Map.of(bobId, "Bob"));
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "bob")),
                    "Bob is offline but already a friend: kept by name");
            check(claims.get(1806).friends().containsKey(bobId), "Still a friend");
            check(com.reiko.signaltower.network.SetFrequencyNamePayload.apply(bob,
                    new com.reiko.signaltower.network.SetFrequencyNamePayload(1806, "Our Gate")), "A friend can rename it");
            check(apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, true, "")), "Bob taken off");
            check(claims.get(1806).friends().isEmpty(), "No friends now");

            check(apply.apply(op, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, false, "")), "An operator makes it public");
            check(claims.get(1806) == null && lastKey(bar).endsWith("private_removed"), "Public: " + lastKey(bar));
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1806, false, "")), "Already public");

            for (int i = 0; i < limit; i++) {
                claims.put(19_000 + i, dave.getUUID(), "Dave", java.util.Map.of());
            }
            check(!apply.apply(dave, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1807, true, "")), "Over the limit");
            check(lastKey(bar).endsWith(limit == 0 ? "private_disabled" : "private_limit"), "Told about the limit: " + lastKey(bar));

            SignalNetwork.get(level).updateTransmitter(far, 1808, 0, 10, bobId);
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1808, true, "")),
                    "Bob's transmitter already sends on 1808");
            check(lastKey(bar).endsWith("private_in_use"), "Told it's in use: " + lastKey(bar));
            check(com.reiko.signaltower.network.SetFrequencyPrivacyPayload.otherSenders(level.getServer(), 1808, aliceId) == 1
                    && com.reiko.signaltower.network.SetFrequencyPrivacyPayload.otherSenders(level.getServer(), 1808, bobId) == 0,
                    "Counted per player, in every dimension (friends-to-be count too, so listing them can't get round it)");
            if (limit > 0) {
                check(apply.apply(bob, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(1808, true, "")), "Bob himself can claim it");
            }
        } finally {
            for (int i = 0; i < limit; i++) {
                claims.remove(19_000 + i);
            }
            claims.remove(1806);
            claims.remove(1808);
            com.reiko.signaltower.radio.FrequencyNames.get(level.getServer()).set(1806, "");
            SignalNetwork.get(level).removeTransmitter(far);
        }
        helper.succeed();
    }

    /**
     * Tuning and placing: someone else can't tune a transmitter or converter onto a private
     * frequency (a receiver is fine, it only listens). A block placed with a private frequency
     * on it by someone else stays silent and says so, in its readout and in Jade. Whoever tunes or
     * places a block becomes its tuner, saved with it.
     */
    @GameTest(template = PLATFORM)
    public static void privateFrequencyTunerAndPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID aliceId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        Player alice = namedPlayer(helper, new BlockPos(2, 1, 2), aliceId, "Alice", false, bar, new java.util.ArrayList<>());
        Player bob = namedPlayer(helper, new BlockPos(2, 1, 2), UUID.randomUUID(), "Bob", false, bar, new java.util.ArrayList<>());
        claims.put(1810, aliceId, "Alice", java.util.Map.of());
        try {
            BlockPos tx = new BlockPos(1, 1, 1);
            BlockPos rx = new BlockPos(4, 1, 1);
            BlockPos conv = new BlockPos(1, 1, 3);
            place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 1811);
            place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 1811);
            place(helper, conv, ModRegistry.RADIO_CONVERTER.get(), 1811);
            java.util.function.BiConsumer<Player, BlockPos> tune = (player, pos) -> {
                ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
                FrequencyTunerItem.setFrequency(tuner, 1810);
                player.setItemInHand(InteractionHand.MAIN_HAND, tuner);
                BlockPos abs = helper.absolutePos(pos);
                tuner.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
            };
            tune.accept(bob, tx);
            check(frequencyAt(helper, tx) == 1811 && lastKey(bar).endsWith("private_refused_block"), "Bob can't tune the transmitter: " + lastKey(bar));
            tune.accept(bob, rx);
            check(frequencyAt(helper, rx) == 1810, "But can tune a receiver to listen");
            tune.accept(alice, tx);
            RadioBlockEntity transmitter = helper.getBlockEntity(tx);
            check(frequencyAt(helper, tx) == 1810 && aliceId.equals(transmitter.getTunedBy()), "Alice can, and is its tuner now");
            CompoundTag saved = transmitter.saveWithoutMetadata(level.registryAccess());
            check(saved.hasUUID("tuned_by") && saved.getUUID("tuned_by").equals(aliceId), "Saved with the block: " + saved);

            check(!com.reiko.signaltower.network.ConfigureConverterPayload.apply(bob,
                    new com.reiko.signaltower.network.ConfigureConverterPayload(helper.absolutePos(conv), 1811, 1810)), "Bob can't make a converter send on it");
            check(lastKey(bar).endsWith("private_refused_block"), "Told why: " + lastKey(bar));
            check(com.reiko.signaltower.network.ConfigureConverterPayload.apply(alice,
                    new com.reiko.signaltower.network.ConfigureConverterPayload(helper.absolutePos(conv), 1811, 1810)), "Alice can");

            // Bob places a transmitter that already carries 1810 (as if from its item): it's silent and says so.
            BlockPos placed = new BlockPos(1, 1, 5);
            place(helper, placed, ModRegistry.RADIO_TRANSMITTER.get(), 1810);
            BlockPos abs = helper.absolutePos(placed);
            bar.clear();
            helper.getBlockState(placed).getBlock().setPlacedBy(level, abs, helper.getBlockState(placed), bob, ItemStack.EMPTY);
            RadioBlockEntity placedBe = helper.getBlockEntity(placed);
            check(placedBe.getTunedBy() != null && placedBe.getTunedBy().equals(bob.getUUID()), "Bob is its tuner");
            check(SignalNetwork.get(level).isSilenced(level, abs), "Silenced in the network");
            check(lastKey(bar).endsWith("private_silenced"), "Bob is told: " + lastKey(bar));
            CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
            check(info != null && "Alice".equals(info.getString("private_to")) && info.getBoolean("silenced"), "Jade's data: " + info);
            List<Component> lines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(placed), info);
            check(keyOf(lines.get(lines.size() - 1)).equals("jade." + SignalTower.MOD_ID + ".silenced"), "Jade's last line: " + lineKeys(lines));
            CompoundTag aliceInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx));
            List<Component> aliceLines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), aliceInfo);
            check(keyOf(aliceLines.get(aliceLines.size() - 1)).endsWith(".private")
                    && aliceLines.get(aliceLines.size() - 1).getString().equals("Private to Alice"), "Alice's: " + lineKeys(aliceLines));
        } finally {
            claims.remove(1810);
            SignalNetwork.get(level).invalidate(1810);
        }
        helper.succeed();
    }

    // ---- Frequency directory ----

    /** The directory's answer survives the network; an entry knows whether it's hidden and whether it's on. */
    @GameTest(template = PLATFORM)
    public static void directoryResultSurvivesNetwork(GameTestHelper helper) {
        var result = new com.reiko.signaltower.network.DirectoryResultPayload(List.of(
                new com.reiko.signaltower.network.DirectoryResultPayload.Entry(2026, 15, 0),
                new com.reiko.signaltower.network.DirectoryResultPayload.Entry(7777, -1, 12),
                new com.reiko.signaltower.network.DirectoryResultPayload.Entry(999_999_999, 0, 0)));
        var buffer = Unpooled.buffer();
        com.reiko.signaltower.network.DirectoryResultPayload.STREAM_CODEC.encode(buffer, result);
        check(com.reiko.signaltower.network.DirectoryResultPayload.STREAM_CODEC.decode(buffer).equals(result), "Same after the network");
        var on = result.entries().get(0);
        var hiddenHere = result.entries().get(1);
        var off = result.entries().get(2);
        check(on.on() && !on.hidden(), "On anywhere");
        check(hiddenHere.hidden() && hiddenHere.on(), "Hidden, but heard here");
        check(!off.on() && !off.hidden(), "Off");
        check(com.reiko.signaltower.network.DirectoryResultPayload.MAX_ENTRIES >= 8192, "Room for every name and claim");
        helper.succeed();
    }

    /**
     * "Strongest anywhere" counts every sender on the frequency in the dimension: transmitters
     * (not ones silenced on a private frequency), repeaters and converters sending on it, and
     * remote presses.
     */
    @GameTest(template = PLATFORM)
    public static void directoryStrongestAnywhere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos here = new BlockPos(63_000, 100, 63_000);
        UUID owner = UUID.randomUUID();
        try {
            check(network.strongestAnywhere(level, 1901) == 0, "Nothing on it");
            network.updateTransmitter(here, 1901, 7, 30, null);
            check(network.strongestAnywhere(level, 1901) == 7, "A transmitter far from everything still counts");
            claims.put(1901, owner, "Owner", java.util.Map.of());
            network.invalidate(1901);
            network.updateTransmitter(here.east(500), 1901, 12, 30, UUID.randomUUID());
            check(network.strongestAnywhere(level, 1901) == 7, "A silenced one doesn't");
            network.updateTransmitter(here.east(1000), 1902, 11, 30, owner);
            network.updateRepeater(here.east(1020), 1902, 1901, 30, owner);
            check(network.strongestAnywhere(level, 1901) == 11, "A converter sending on it does");
            network.toggleLatch(1901, SignalNetwork.ownCoverage(here.south(2000), 10));
            check(network.strongestAnywhere(level, 1901) == 15, "So does a remote toggle");
            check(network.strongestAnywhere(level, 1902) == 11, "The converter's input frequency is on too");
        } finally {
            claims.remove(1901);
        }
        helper.succeed();
    }

    /**
     * The directory lists every named and private frequency in order, only for a player holding a
     * radio item. Someone else's private frequency only says whether it's heard where the player
     * stands; their own, a public one and one they're a friend on say whether it's on anywhere.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void directoryEntriesHideOtherPlayersPrivateFrequencies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        var names = com.reiko.signaltower.radio.FrequencyNames.get(server);
        UUID meId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Player me = namedPlayer(helper, new BlockPos(1, 1, 1), meId, "Me", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        BlockPos far = new BlockPos(64_000, 100, 64_000);
        SignalNetwork network = SignalNetwork.get(level);
        names.set(1903, "Directory Test");
        claims.put(1904, otherId, "Other", java.util.Map.of());
        claims.put(1905, meId, "Me", java.util.Map.of());
        claims.put(1906, otherId, "Other", java.util.Map.of(meId, "Me"));
        network.updateTransmitter(far, 1903, 5, 30, null);
        network.updateTransmitter(far.east(100), 1904, 9, 30, otherId);
        network.updateTransmitter(far.east(200), 1906, 4, 30, otherId);
        BlockPos near = new BlockPos(3, 1, 1);
        helper.startSequence()
                .thenExecute(() -> {
                    check(!com.reiko.signaltower.network.DirectoryRequestPayload.holdsRadioItem(me), "Nothing in hand");
                    me.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
                    check(com.reiko.signaltower.network.DirectoryRequestPayload.holdsRadioItem(me), "A tuner counts");
                    me.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    me.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.PAGER.get()));
                    check(com.reiko.signaltower.network.DirectoryRequestPayload.holdsRadioItem(me), "So does a pager in the off-hand");

                    var entries = com.reiko.signaltower.network.DirectoryRequestPayload.entriesFor(me);
                    List<Integer> order = entries.stream().map(com.reiko.signaltower.network.DirectoryResultPayload.Entry::frequency)
                            .filter(f -> f >= 1903 && f <= 1906).toList();
                    check(order.equals(List.of(1903, 1904, 1905, 1906)), "Named and private, in order: " + order);
                    var byFrequency = entries.stream().collect(java.util.stream.Collectors.toMap(
                            com.reiko.signaltower.network.DirectoryResultPayload.Entry::frequency, e -> e));
                    check(byFrequency.get(1903).anywhere() == 5 && byFrequency.get(1903).here() == 0, "Named and public: on anywhere, not here");
                    check(byFrequency.get(1904).hidden() && byFrequency.get(1904).here() == 0, "Someone else's: hidden");
                    check(byFrequency.get(1905).anywhere() == 0 && !byFrequency.get(1905).hidden(), "Mine: off, and shown");
                    check(byFrequency.get(1906).anywhere() == 4, "One I'm a friend on: shown");

                    // Someone else's transmitter right next to me: I can hear it, so that much is shown.
                    place(helper, near, ModRegistry.RADIO_TRANSMITTER.get(), 1904);
                    RadioBlockEntity transmitter = helper.getBlockEntity(near);
                    transmitter.setTunedBy(otherId);
                    helper.setBlock(near.above(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    var entry = com.reiko.signaltower.network.DirectoryRequestPayload.entriesFor(me).stream()
                            .filter(e -> e.frequency() == 1904).findFirst().orElseThrow();
                    check(entry.hidden() && entry.here() == 15, "Still hidden, but heard here: " + entry);
                })
                .thenExecute(() -> {
                    claims.remove(1904);
                    claims.remove(1905);
                    claims.remove(1906);
                    names.set(1903, "");
                    network.removeTransmitter(far);
                    network.removeTransmitter(far.east(100));
                    network.removeTransmitter(far.east(200));
                })
                .thenSucceed();
    }

    // ---- Friend groups ----

    /**
     * Groups: the owner isn't their own member, members are capped, a claim uses its owner's groups
     * by name (ignoring case, unknown ones left out) and lets their members send; renaming keeps the
     * frequencies using it, changing members or deleting the group changes who can send. Everything
     * survives saving and the network, and the Friends box tells "#groups" from names.
     */
    @GameTest(template = PLATFORM)
    public static void friendGroupsSavedAndSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        var claims = new com.reiko.signaltower.radio.FrequencyClaims();
        java.util.Map<UUID, String> members = new java.util.LinkedHashMap<>();
        members.put(owner, "Owner");
        members.put(alex, "Alex");
        members.put(bob, "Bob");
        for (int i = 0; i < 40; i++) {
            members.put(UUID.randomUUID(), "M" + i);
        }
        claims.putGroup(owner, null, "Base crew", members);
        var group = claims.group(owner, "base CREW");
        check(group != null && group.name().equals("Base crew"), "Found ignoring case");
        check(group.members().size() == com.reiko.signaltower.radio.FrequencyClaims.MAX_GROUP_MEMBERS
                && !group.members().containsKey(owner), "Capped, owner left out: " + group.members().size());

        var claim = claims.put(2001, owner, "Owner", java.util.Map.of(), List.of("base crew", "Nope"));
        check(claim.groups().equals(List.of("Base crew")), "Uses the group by its own name, unknown ones left out: " + claim.groups());
        check(claims.allows(2001, alex) && claims.allows(2001, bob) && !claims.allows(2001, UUID.randomUUID()), "Members can send");
        var info = com.reiko.signaltower.radio.FrequencyClaims.ClaimInfo.of(claim);
        check(info.hasMember("alex") && info.usesGroup("BASE CREW") && info.friends().isEmpty(), "What clients are told: " + info.groups());

        var loaded = com.reiko.signaltower.radio.FrequencyClaims.load(claims.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.allows(2001, alex) && loaded.groupsOf(owner).size() == 1 && loaded.get(2001).groups().equals(List.of("Base crew")),
                "Saved and loaded");

        var buffer = Unpooled.buffer();
        var groupsPayload = new com.reiko.signaltower.network.GroupsPayload(claims.groupInfo(owner));
        com.reiko.signaltower.network.GroupsPayload.STREAM_CODEC.encode(buffer, groupsPayload);
        check(com.reiko.signaltower.network.GroupsPayload.STREAM_CODEC.decode(buffer).equals(groupsPayload), "Groups survive the network");
        var claimsPayload = new com.reiko.signaltower.network.FrequencyClaimsPayload(java.util.Map.of(2001, info));
        com.reiko.signaltower.network.FrequencyClaimsPayload.STREAM_CODEC.encode(buffer, claimsPayload);
        check(com.reiko.signaltower.network.FrequencyClaimsPayload.STREAM_CODEC.decode(buffer).equals(claimsPayload), "So do claims with groups");

        var affected = claims.putGroup(owner, "Base crew", "Crew", java.util.Map.of(alex, "Alex"));
        check(affected.equals(java.util.Set.of(2001)), "Renaming changes the frequency using it: " + affected);
        check(claims.get(2001).groups().equals(List.of("Crew")) && claims.allows(2001, alex) && !claims.allows(2001, bob),
                "Renamed, and Bob taken off");
        check(claims.removeGroup(owner, "CREW").equals(java.util.Set.of(2001)), "Deleting it changes the frequency too");
        check(claims.get(2001).groups().isEmpty() && !claims.allows(2001, alex), "No group, no Alex");

        check(com.reiko.signaltower.radio.FrequencyClaims.friendsText(List.of("Crew"), List.of("Sam")).equals("#Crew, Sam"), "Friends box text");
        String typed = "#Base crew, Sam Jo, # Night shift ,#base crew";
        check(com.reiko.signaltower.network.SetFrequencyPrivacyPayload.parseGroups(typed).equals(List.of("Base crew", "Night shift")),
                "Groups: " + com.reiko.signaltower.network.SetFrequencyPrivacyPayload.parseGroups(typed));
        check(com.reiko.signaltower.network.SetFrequencyPrivacyPayload.parseNames(typed).equals(List.of("Sam", "Jo")),
                "Names: " + com.reiko.signaltower.network.SetFrequencyPrivacyPayload.parseNames(typed));
        check(com.reiko.signaltower.network.EditGroupPayload.cleanName("  #Night, shift ").equals("Night shift"), "Group names lose # and commas");
        helper.succeed();
    }

    /** A group member's transmitter broadcasts on a frequency using the group, and goes quiet when they're taken off. */
    @GameTest(template = PLATFORM)
    public static void friendGroupChangesSilenceBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos here = new BlockPos(65_000, 100, 65_000);
        try {
            claims.putGroup(owner, null, "Night shift", java.util.Map.of(member, "Member"));
            claims.put(2002, owner, "Owner", java.util.Map.of(), List.of("Night shift"));
            network.invalidate(2002);
            network.updateTransmitter(here, 2002, 6, 30, member);
            check(network.listen(level, 2002, here.east(5)).power() == 6, "The member's transmitter broadcasts");
            var affected = claims.putGroup(owner, "Night shift", "Night shift", java.util.Map.of());
            check(affected.contains(2002), "The frequency is affected: " + affected);
            network.invalidate(2002);
            check(network.listen(level, 2002, here.east(5)).power() == 0 && network.isSilenced(level, here), "Taken off: silent at once");
            claims.putGroup(owner, "Night shift", "Night shift", java.util.Map.of(member, "Member"));
            network.invalidate(2002);
            check(network.listen(level, 2002, here.east(5)).power() == 6, "Back in the group: broadcasting again");
        } finally {
            claims.remove(2002);
            claims.removeGroup(owner, "Night shift");
        }
        helper.succeed();
    }

    /**
     * The Groups screen's packet: only with a tuner in hand; a name is needed and can't repeat one
     * of the player's own; unknown members are reported, offline ones kept by name; renaming carries
     * the frequencies using it along; the per-player limit; deleting. The Friends box takes
     * "#group" and reports unknown groups.
     */
    @GameTest(template = PLATFORM)
    public static void editGroupPayloadRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        List<Component> chat = new java.util.ArrayList<>();
        Player alice = namedPlayer(helper, new BlockPos(1, 1, 1), aliceId, "Alice", false, bar, chat);
        java.util.function.Function<com.reiko.signaltower.network.EditGroupPayload, Boolean> edit =
                payload -> com.reiko.signaltower.network.EditGroupPayload.apply(alice, payload);
        try {
            check(!edit.apply(new com.reiko.signaltower.network.EditGroupPayload("", "Base crew", "", false)), "Only with a tuner in hand");
            alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
            chat.clear();
            check(edit.apply(new com.reiko.signaltower.network.EditGroupPayload("", "Base crew", "Zed", false)), "Created");
            check(lastKey(bar).endsWith("group_created") && claims.group(aliceId, "Base crew").members().isEmpty(), "Empty: " + lastKey(bar));
            check(lastKey(chat).endsWith("group_members_missing"), "Zed isn't online: " + lastKey(chat));
            check(!edit.apply(new com.reiko.signaltower.network.EditGroupPayload("", "BASE CREW", "", false)), "Same name again");
            check(lastKey(bar).endsWith("group_name_taken"), "Told: " + lastKey(bar));
            check(!edit.apply(new com.reiko.signaltower.network.EditGroupPayload("", " #, ", "", false)), "No name");
            check(lastKey(bar).endsWith("group_name_needed"), "Told: " + lastKey(bar));

            // Alice uses it on a frequency through the Friends box.
            check(com.reiko.signaltower.network.SetFrequencyPrivacyPayload.apply(alice,
                    new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2003, true, "#base crew, #Nope")), "Private with a group");
            check(claims.get(2003).groups().equals(List.of("Base crew")), "Uses it: " + claims.get(2003).groups());
            check(lastKey(chat).endsWith("private_no_group") && chat.get(chat.size() - 1).getString().contains("Nope"), "Nope reported: " + lastKey(chat));

            // Bob is a member already (as if added while online); he's kept by name while offline.
            claims.putGroup(aliceId, "Base crew", "Base crew", java.util.Map.of(bobId, "Bob"));
            check(claims.allows(2003, bobId), "Bob can send on 2003");
            check(!edit.apply(new com.reiko.signaltower.network.EditGroupPayload("Base crew", "Base crew", "bob", false)), "Unchanged");
            check(edit.apply(new com.reiko.signaltower.network.EditGroupPayload("Base crew", "Crew", "bob", false)), "Renamed");
            check(claims.get(2003).groups().equals(List.of("Crew")) && claims.allows(2003, bobId), "2003 follows the new name");
            check(edit.apply(new com.reiko.signaltower.network.EditGroupPayload("Crew", "Crew", "", false)), "Bob taken off");
            check(!claims.allows(2003, bobId) && lastKey(bar).endsWith("group_saved"), "Bob can't send now: " + lastKey(bar));

            for (int i = claims.groupsOf(aliceId).size(); i < com.reiko.signaltower.radio.FrequencyClaims.MAX_GROUPS; i++) {
                claims.putGroup(aliceId, null, "Extra " + i, java.util.Map.of());
            }
            check(!edit.apply(new com.reiko.signaltower.network.EditGroupPayload("", "One too many", "", false)), "Over the limit");
            check(lastKey(bar).endsWith("group_limit"), "Told: " + lastKey(bar));

            check(edit.apply(new com.reiko.signaltower.network.EditGroupPayload("Crew", "", "", true)), "Deleted");
            check(lastKey(bar).endsWith("group_deleted") && claims.group(aliceId, "Crew") == null && claims.get(2003).groups().isEmpty(),
                    "Gone, and 2003 doesn't use it: " + lastKey(bar));
            check(!edit.apply(new com.reiko.signaltower.network.EditGroupPayload("Crew", "", "", true)), "Already gone");
        } finally {
            claims.remove(2003);
            for (var group : claims.groupsOf(aliceId)) {
                claims.removeGroup(aliceId, group.name());
            }
        }
        helper.succeed();
    }

    /**
     * The directory answers a player with nothing in hand if they opened it from a converter's
     * screen and are within reach of that converter, but not for a converter out of reach or a
     * block that isn't a converter. The request survives the network with and without a converter.
     */
    @GameTest(template = PLATFORM)
    public static void directoryFromConverterScreen(GameTestHelper helper) {
        BlockPos converter = new BlockPos(2, 1, 2);
        BlockPos receiver = new BlockPos(4, 1, 2);
        place(helper, converter, ModRegistry.RADIO_CONVERTER.get(), 2101);
        place(helper, receiver, ModRegistry.RADIO_RECEIVER.get(), 2101);
        Player near = namedPlayer(helper, new BlockPos(3, 1, 3), UUID.randomUUID(), "Near", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = mockPlayerAt(helper, new BlockPos(2, 1, 2).south(40));
        var fromConverter = new com.reiko.signaltower.network.DirectoryRequestPayload(java.util.Optional.of(helper.absolutePos(converter)));
        var fromReceiver = new com.reiko.signaltower.network.DirectoryRequestPayload(java.util.Optional.of(helper.absolutePos(receiver)));
        check(!com.reiko.signaltower.network.DirectoryRequestPayload.mayAsk(near, com.reiko.signaltower.network.DirectoryRequestPayload.FROM_ITEM),
                "Nothing in hand and no converter: no answer");
        check(com.reiko.signaltower.network.DirectoryRequestPayload.mayAsk(near, fromConverter), "Next to the converter: answered");
        check(!com.reiko.signaltower.network.DirectoryRequestPayload.mayAsk(far, fromConverter), "Too far from it: no answer");
        check(!com.reiko.signaltower.network.DirectoryRequestPayload.mayAsk(near, fromReceiver), "A receiver isn't a converter");
        near.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.MULTI_REMOTE.get()));
        check(com.reiko.signaltower.network.DirectoryRequestPayload.mayAsk(near, com.reiko.signaltower.network.DirectoryRequestPayload.FROM_ITEM),
                "With a Multi-Button Remote in hand: answered");
        var buffer = Unpooled.buffer();
        for (var payload : List.of(fromConverter, com.reiko.signaltower.network.DirectoryRequestPayload.FROM_ITEM)) {
            com.reiko.signaltower.network.DirectoryRequestPayload.STREAM_CODEC.encode(buffer, payload);
            check(com.reiko.signaltower.network.DirectoryRequestPayload.STREAM_CODEC.decode(buffer).equals(payload), "Same after the network: " + payload);
        }
        helper.succeed();
    }

    // ---- Encrypted frequencies ----

    /**
     * An encrypted claim: only its owner and members hear it (a block with no recorded tuner does
     * too); it's saved, survives the network (claim info and the tuner's packet), and changing or
     * renaming a group it uses keeps it encrypted.
     */
    @GameTest(template = PLATFORM)
    public static void encryptedClaimsSavedAndSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var claims = new com.reiko.signaltower.radio.FrequencyClaims();
        claims.putGroup(owner, null, "Crew", java.util.Map.of(member, "Member"));
        var claim = claims.put(2201, owner, "Owner", java.util.Map.of(friend, "Friend"), List.of("crew"), true);
        check(claim.encrypted() && claim.groups().equals(List.of("Crew")), "Encrypted, with its group: " + claim);
        check(claim.hears(owner) && claim.hears(friend) && claim.hears(member), "Owner, friend and group member hear it");
        check(!claim.hears(UUID.randomUUID()) && !claim.hears(null), "Nobody else (the claim itself counts null as nobody)");
        var plain = claims.put(2202, owner, "Owner", java.util.Map.of());
        check(!plain.encrypted() && plain.hears(UUID.randomUUID()), "A private one that isn't encrypted: anyone hears it");

        claims.putGroup(owner, "Crew", "Base crew", java.util.Map.of(member, "Member"));
        check(claims.get(2201).encrypted() && claims.get(2201).groups().equals(List.of("Base crew")), "Renaming the group keeps it encrypted");
        claims.removeGroup(owner, "Base crew");
        check(claims.get(2201).encrypted() && !claims.get(2201).hears(member), "Deleting it too, and its member can't hear any more");

        var loaded = com.reiko.signaltower.radio.FrequencyClaims.load(claims.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.get(2201).encrypted() && !loaded.get(2202).encrypted(), "Saved and loaded");
        CompoundTag old = claims.save(new CompoundTag(), level.registryAccess());
        old.getList("claims", net.minecraft.nbt.Tag.TAG_COMPOUND).forEach(t -> ((CompoundTag) t).remove("encrypted"));
        check(!com.reiko.signaltower.radio.FrequencyClaims.load(old, level.registryAccess()).get(2201).encrypted(),
                "A claim saved before v0.29.0 isn't encrypted");

        var info = com.reiko.signaltower.radio.FrequencyClaims.ClaimInfo.of(claims.get(2201));
        check(info.encrypted() && info.isMember(owner, "Owner") && info.isMember(UUID.randomUUID(), "friend")
                && !info.isMember(UUID.randomUUID(), "Zed"), "Client info: encrypted, members by UUID or name");
        var buffer = Unpooled.buffer();
        var one = new com.reiko.signaltower.network.FrequencyClaimPayload(2201, java.util.Optional.of(info));
        com.reiko.signaltower.network.FrequencyClaimPayload.STREAM_CODEC.encode(buffer, one);
        check(com.reiko.signaltower.network.FrequencyClaimPayload.STREAM_CODEC.decode(buffer).equals(one), "Claim info survives the network");
        var packet = new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2201, true, "Friend", true);
        com.reiko.signaltower.network.SetFrequencyPrivacyPayload.STREAM_CODEC.encode(buffer, packet);
        check(com.reiko.signaltower.network.SetFrequencyPrivacyPayload.STREAM_CODEC.decode(buffer).equals(packet), "So does the tuner's packet");
        check(!new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2201, true, "").encrypted(), "Not encrypted unless asked");
        helper.succeed();
    }

    /**
     * On an encrypted frequency, listeners tuned by someone who isn't a member hear nothing at all:
     * receivers (no signal, no towers), the scanner (the frequency isn't listed), converters (they
     * don't pass it on, not even a remote press) and the remotes' count and locator (their
     * receivers don't count). Members' and old blocks hear it. Just private: everyone hears it.
     */
    @GameTest(template = PLATFORM)
    public static void encryptedFrequencyDeafensOtherPlayersListeners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos here = new BlockPos(65_000, 100, 65_000); // far away and never loaded
        claims.put(2203, owner, "Owner", java.util.Map.of(friend, "Friend"), List.of(), true);
        network.invalidate(2203);
        try {
            network.updateTransmitter(here, 2203, 9, 30, owner);
            BlockPos spot = here.east(5);
            var strangers = network.listen(level, 2203, spot, stranger);
            check(strangers.power() == 0 && strangers.transmittersInReach() == 0, "A stranger's receiver hears nothing: " + strangers);
            check(network.listen(level, 2203, spot, friend).power() == 9, "A friend's hears it");
            check(network.listen(level, 2203, spot, null).power() == 9 && network.listen(level, 2203, spot).power() == 9,
                    "So does an old one with no tuner");
            check(com.reiko.signaltower.radio.FrequencyClaims.mayHear(level, 2203, friend)
                    && !com.reiko.signaltower.radio.FrequencyClaims.mayHear(level, 2203, stranger)
                    && com.reiko.signaltower.radio.FrequencyClaims.mayHear(level, 2203, null)
                    && com.reiko.signaltower.radio.FrequencyClaims.mayHear(level, 2204, stranger), "mayHear");
            check(network.scanAt(level, spot, stranger).stream().noneMatch(h -> h.frequency() == 2203), "The stranger's scanner doesn't list it");
            check(network.scanAt(level, spot, friend).stream().anyMatch(h -> h.frequency() == 2203 && h.power() == 9), "The friend's does");

            // A stranger's converter listening on it (sending on public 2204) hears nothing; a friend's passes it on.
            BlockPos converter = here.east(20);
            network.updateRepeater(converter, 2203, 2204, 30, stranger);
            check(network.isDeaf(level, converter) && network.repeaterPower(level, converter) == 0, "The stranger's converter is deaf");
            check(network.listen(level, 2204, converter.east(25)).power() == 0, "So 2204 stays quiet");
            List<SignalNetwork.Coverage> press = SignalNetwork.ownCoverage(here, 30);
            check(network.carried(level, 2203, press).on(2204).isEmpty(), "A press on 2203 isn't carried onto 2204 by it");
            network.updateRepeater(converter, 2203, 2204, 30, friend);
            check(!network.isDeaf(level, converter) && network.listen(level, 2204, converter.east(25)).power() == 9, "The friend's converter passes it on");
            check(!network.carried(level, 2203, press).on(2204).isEmpty(), "And carries presses");

            // Remotes count only receivers that can hear it.
            network.trackReceiver(here.south(2), 2203, stranger);
            network.trackReceiver(here.south(4), 2203, friend);
            network.trackReceiver(here.south(6), 2203, null);
            check(network.countReceivers(2203, press) == 2, "The stranger's receiver isn't counted: " + network.countReceivers(2203, press));
            check(network.scanReceivers(2203, here, 30).inRange() == 2, "Nor shown by the locator");

            // Only private (not encrypted): everyone hears it again.
            claims.put(2203, owner, "Owner", java.util.Map.of(friend, "Friend"));
            network.invalidate(2203);
            network.updateRepeater(converter, 2203, 2204, 30, stranger);
            check(network.listen(level, 2203, spot, stranger).power() == 9, "Private only: the stranger hears it");
            check(network.listen(level, 2204, converter.east(25)).power() == 9, "And their converter passes it on");
            check(network.countReceivers(2203, press) == 3, "All three receivers count");
            check(network.scanAt(level, spot, stranger).stream().anyMatch(h -> h.frequency() == 2203), "The scanner lists it");
        } finally {
            claims.remove(2203);
        }
        helper.succeed();
    }

    /**
     * Real blocks: a stranger's receiver and toggle receiver on an encrypted frequency stay off and
     * say why (readout, the tuner's message, Jade); the friend's and an old one light up. Making it
     * just private lets the stranger's hear it, and becoming able to hear isn't a turn-on for the
     * toggle receiver.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void encryptedFrequencyReceiverBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        Player stranger = namedPlayer(helper, new BlockPos(2, 1, 2), UUID.randomUUID(), "Stranger", false, bar, new java.util.ArrayList<>());
        claims.put(2205, ownerId, "Owner", java.util.Map.of(friendId, "Friend"), List.of(), true);
        SignalNetwork.get(level).invalidate(2205);
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos strangers = new BlockPos(3, 1, 1);
        BlockPos friends = new BlockPos(5, 1, 1);
        BlockPos old = new BlockPos(7, 1, 1);
        BlockPos toggle = new BlockPos(3, 1, 3);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 2205);
        ((RadioBlockEntity) helper.getBlockEntity(tx)).setTunedBy(ownerId);
        place(helper, friends, ModRegistry.RADIO_RECEIVER.get(), 2205);
        ((RadioBlockEntity) helper.getBlockEntity(friends)).setTunedBy(friendId);
        place(helper, old, ModRegistry.RADIO_RECEIVER.get(), 2205);
        place(helper, strangers, ModRegistry.RADIO_RECEIVER.get(), 1);
        place(helper, toggle, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 2205);
        ((RadioBlockEntity) helper.getBlockEntity(toggle)).setTunedBy(stranger.getUUID());
        helper.setBlock(tx.above(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenExecute(() -> {
                    // The stranger tunes their receiver to it: tuned, but told it won't hear anything.
                    ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
                    FrequencyTunerItem.setFrequency(tuner, 2205);
                    stranger.setItemInHand(InteractionHand.MAIN_HAND, tuner);
                    BlockPos abs = helper.absolutePos(strangers);
                    tuner.getItem().useOn(new UseOnContext(stranger, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
                    check(frequencyAt(helper, strangers) == 2205 && lastKey(bar).endsWith("encrypted_deaf"), "Tuned, and told: " + lastKey(bar));
                })
                .thenWaitUntil(() -> {
                    assertOutput(helper, friends, 15);
                    assertOutput(helper, old, 15);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    assertOutput(helper, strangers, 0);
                    assertOutput(helper, toggle, 0);
                    BlockPos abs = helper.absolutePos(strangers);
                    Component notice = com.reiko.signaltower.block.RadioBlock.quietNotice(level, abs);
                    check(notice != null && keyOf(notice).endsWith("encrypted_deaf") && notice.getString().contains("Owner"), "Readout says why: " + notice);
                    check(com.reiko.signaltower.block.RadioBlock.quietNotice(level, helper.absolutePos(friends)) == null, "The friend's is fine");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(info != null && "Owner".equals(info.getString("deaf_to")) && info.getBoolean("encrypted") && info.getInt("towers") == 0,
                            "Jade's data: " + info);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(strangers), info));
                    check(keys.get(keys.size() - 1).equals("deaf") && !keys.contains("encrypted"),
                            "One line says it's deaf: " + keys);
                    CompoundTag txInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx));
                    List<String> txKeys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), txInfo));
                    check(txKeys.get(txKeys.size() - 1).equals("encrypted"), "The transmitter says it's encrypted: " + txKeys);

                    claims.put(2205, ownerId, "Owner", java.util.Map.of(friendId, "Friend"));
                    SignalNetwork.get(level).invalidate(2205);
                })
                .thenWaitUntil(() -> assertOutput(helper, strangers, 15))
                .thenIdle(3)
                .thenExecute(() -> {
                    assertOutput(helper, toggle, 0); // it can hear the frequency now, but that isn't a turn-on
                    check(com.reiko.signaltower.block.RadioBlock.quietNotice(level, helper.absolutePos(strangers)) == null, "No longer deaf");
                    helper.setBlock(tx.above(), Blocks.AIR);
                })
                .thenWaitUntil(() -> assertOutput(helper, strangers, 0))
                .thenIdle(3) // the toggle receiver has heard it go off too (it may check later in the same tick)
                .thenExecute(() -> helper.setBlock(tx.above(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, toggle, 15)) // a real turn-on flips it
                .thenExecute(() -> {
                    claims.remove(2205);
                    SignalNetwork.get(level).invalidate(2205);
                })
                .thenSucceed();
    }

    /**
     * Pagers and scanners carried by someone who isn't a member don't hear an encrypted frequency:
     * no alert, no "Heard by" count, nothing in the catch-up, nothing to track (and a target is
     * dropped once it's encrypted), and the directory shows nothing about it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void encryptedFrequencyPagersScannerAndDirectory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        Player stranger = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Stranger", false, bar, new java.util.ArrayList<>());
        Player friend = namedPlayer(helper, new BlockPos(1, 1, 1), friendId, "Friend", false, bar, new java.util.ArrayList<>());
        BlockPos far = new BlockPos(66_000, 100, 66_000);
        for (Player player : List.of(stranger, friend)) {
            player.moveTo(far.getX() + 3.5, far.getY(), far.getZ() + 0.5);
        }
        ItemStack strangersPager = pagerFor(stranger, 2206);
        ItemStack friendsPager = pagerFor(friend, 2206);
        SignalNetwork network = SignalNetwork.get(level);
        claims.put(2206, ownerId, "Owner", java.util.Map.of(friendId, "Friend"), List.of(), true);
        network.invalidate(2206);
        helper.startSequence()
                .thenExecute(() -> {
                    PagerItem.markAway(stranger, level.getGameTime());
                    PagerItem.markAway(friend, level.getGameTime());
                })
                .thenIdle(2)
                .thenExecute(() -> network.updateTransmitter(far, 2206, 12, 30, ownerId))
                .thenIdle(1)
                .thenExecute(() -> {
                    check(PagerItem.checkSignal(level, stranger, strangersPager) == null && PagerItem.lastSignal(strangersPager) == 0,
                            "The stranger's pager stays quiet");
                    check(PagerItem.checkSignal(level, friend, friendsPager) != null && PagerItem.lastSignal(friendsPager) == 12,
                            "The friend's goes off");
                    List<SignalNetwork.Coverage> coverage = SignalNetwork.ownCoverage(far, 30);
                    check(PagerItem.pagersHeard(stranger, 2206, coverage) == 0 && PagerItem.pagersHeard(friend, 2206, coverage) == 1,
                            "Only the friend's counts for Heard by");
                    List<Component> strangersCatchUp = PagerItem.catchUp(level, stranger, true);
                    check(strangersCatchUp.size() == 1 && keyOf(strangersCatchUp.get(0)).endsWith("pager_catchup_none_one"),
                            "The stranger's catch-up heard nothing: " + strangersCatchUp);
                    List<Component> friendsCatchUp = PagerItem.catchUp(level, friend, true);
                    check(friendsCatchUp.size() == 1 && keyOf(friendsCatchUp.get(0)).endsWith("pager_catchup_once"),
                            "The friend's heard it once: " + friendsCatchUp);

                    ItemStack scanner = new ItemStack(ModRegistry.SIGNAL_SCANNER.get());
                    check(keyOf(SignalScannerItem.track(level, stranger, scanner, 2206)).endsWith("scanner_cant_track")
                            && SignalScannerItem.getTarget(scanner) == null, "The stranger can't track it");
                    check(keyOf(SignalScannerItem.track(level, friend, scanner, 2206)).endsWith("scanner_tracking"), "The friend can");
                    check(SignalScannerItem.checkTarget(level, scanner, friendId) == null, "And keeps it");
                    Component lost = SignalScannerItem.checkTarget(level, scanner, stranger.getUUID());
                    check(lost != null && keyOf(lost).endsWith("scanner_lost_encrypted") && SignalScannerItem.getTarget(scanner) == null,
                            "In a non-member's hands it's dropped: " + lost);

                    for (Player player : List.of(stranger, friend)) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
                    }
                    var strangersEntry = com.reiko.signaltower.network.DirectoryRequestPayload.entriesFor(stranger).stream()
                            .filter(e -> e.frequency() == 2206).findFirst().orElseThrow();
                    check(strangersEntry.hidden() && strangersEntry.encrypted() && strangersEntry.here() == -1 && !strangersEntry.on(),
                            "The stranger's directory shows nothing, even standing next to it: " + strangersEntry);
                    var friendsEntry = com.reiko.signaltower.network.DirectoryRequestPayload.entriesFor(friend).stream()
                            .filter(e -> e.frequency() == 2206).findFirst().orElseThrow();
                    check(friendsEntry.anywhere() == 12 && friendsEntry.here() == 12 && !friendsEntry.encrypted(), "The friend's shows it: " + friendsEntry);
                })
                .thenExecute(() -> {
                    claims.remove(2206);
                    network.removeTransmitter(far);
                })
                .thenSucceed();
    }

    /**
     * The tuner's privacy packet: Encrypted makes a new claim encrypted (or encrypts or decrypts
     * an existing one), and says so; asking for what it already is changes nothing; only the owner
     * changes it, though an operator can still make it public.
     */
    @GameTest(template = PLATFORM)
    public static void encryptButtonInTunerPacket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID aliceId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        Player alice = namedPlayer(helper, new BlockPos(1, 1, 1), aliceId, "Alice", false, bar, new java.util.ArrayList<>());
        Player bob = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Bob", false, bar, new java.util.ArrayList<>());
        Player op = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Admin", true, bar, new java.util.ArrayList<>());
        for (Player player : List.of(alice, bob, op)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
        }
        java.util.function.BiFunction<Player, com.reiko.signaltower.network.SetFrequencyPrivacyPayload, Boolean> apply =
                com.reiko.signaltower.network.SetFrequencyPrivacyPayload::apply;
        try {
            if (com.reiko.signaltower.Config.PRIVATE_FREQUENCIES_PER_PLAYER.get() <= 0) {
                helper.succeed(); // private frequencies are switched off in this config
                return;
            }
            check(apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, true, "", true)), "Alice encrypts 2207");
            check(claims.get(2207).encrypted() && lastKey(bar).endsWith("encrypted_set"), "Encrypted: " + lastKey(bar));
            check(!apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, true, "", true)), "Again: nothing changes");
            check(!apply.apply(bob, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, true, "", false)), "Bob can't decrypt it");
            check(claims.get(2207).encrypted(), "Still encrypted");
            check(apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, true, "", false)), "Alice decrypts it");
            check(!claims.get(2207).encrypted() && claims.get(2207).owner().equals(aliceId) && lastKey(bar).endsWith("private_set"),
                    "Still private, not encrypted: " + lastKey(bar));
            check(apply.apply(alice, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, true, "", true)), "And encrypts it again");
            check(!apply.apply(op, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, true, "", false)),
                    "An operator can't just decrypt it");
            check(apply.apply(op, new com.reiko.signaltower.network.SetFrequencyPrivacyPayload(2207, false, "", false)), "But can make it public");
            check(claims.get(2207) == null, "Public");
        } finally {
            claims.remove(2207);
        }
        helper.succeed();
    }

    // ---- Frequency notes ----

    /**
     * Notes are tidied (formatting codes gone, spaces trimmed, cut to 64 characters), an empty
     * one removes the note, a world keeps at most 4,096, they're saved, and the packets and the
     * client's copy (with its tooltip line) work like names'.
     */
    @GameTest(template = PLATFORM)
    public static void frequencyNotesStoredSavedAndSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var notes = new com.reiko.signaltower.radio.FrequencyNotes();
        check("opens the barn door".equals(notes.set(2301, "  opens the barn door  ")), "Spaces trimmed: " + notes.get(2301));
        check(!notes.set(2302, "§cred door").contains("§"), "Formatting codes dropped");
        String longNote = notes.set(2302, "x".repeat(100));
        check(longNote.length() == com.reiko.signaltower.radio.FrequencyNotes.MAX_LENGTH, "Cut to 64: " + longNote.length());
        check("".equals(notes.set(2302, "   ")) && notes.get(2302) == null, "Empty removes it");
        check(notes.set(0, "low").equals("low") && notes.get(1) != null, "Frequencies are clamped like everywhere else");
        notes.set(1, "");

        var loaded = com.reiko.signaltower.radio.FrequencyNotes.load(notes.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check("opens the barn door".equals(loaded.get(2301)) && loaded.all().size() == 1, "Saved and loaded: " + loaded.all());

        var full = new com.reiko.signaltower.radio.FrequencyNotes();
        for (int i = 0; i < com.reiko.signaltower.radio.FrequencyNotes.MAX_NOTES; i++) {
            full.set(100_000 + i, "n");
        }
        check(full.set(99, "one more") == null && full.get(99) == null, "Full: refused");
        check("changed".equals(full.set(100_000, "changed")) && "".equals(full.set(100_001, "")), "But existing ones can still change or go");

        var buffer = Unpooled.buffer();
        var list = new com.reiko.signaltower.network.FrequencyNotesPayload(java.util.Map.of(2301, "opens the barn door", 42, "farm"));
        com.reiko.signaltower.network.FrequencyNotesPayload.STREAM_CODEC.encode(buffer, list);
        check(com.reiko.signaltower.network.FrequencyNotesPayload.STREAM_CODEC.decode(buffer).notes().equals(list.notes()), "The list survives the network");
        var one = new com.reiko.signaltower.network.FrequencyNotePayload(2301, "x".repeat(64));
        com.reiko.signaltower.network.FrequencyNotePayload.STREAM_CODEC.encode(buffer, one);
        check(com.reiko.signaltower.network.FrequencyNotePayload.STREAM_CODEC.decode(buffer).equals(one), "One change survives it");
        var set = new com.reiko.signaltower.network.SetFrequencyNotePayload(2301, "opens the barn door");
        com.reiko.signaltower.network.SetFrequencyNotePayload.STREAM_CODEC.encode(buffer, set);
        check(com.reiko.signaltower.network.SetFrequencyNotePayload.STREAM_CODEC.decode(buffer).equals(set), "So does the tuner's");

        try {
            com.reiko.signaltower.radio.FrequencyNotes.clientReplaceAll(java.util.Map.of(2303, "gate"));
            check("gate".equals(com.reiko.signaltower.radio.FrequencyNotes.noteOf(null, 2303)), "From the full list");
            com.reiko.signaltower.radio.FrequencyNotes.clientSet(2304, "bell");
            com.reiko.signaltower.radio.FrequencyNotes.clientSet(2303, "");
            check(com.reiko.signaltower.radio.FrequencyNotes.noteOf(null, 2303) == null
                    && com.reiko.signaltower.radio.FrequencyNotes.clientFrequencies().equals(java.util.Set.of(2304)), "One change at a time");
            List<Component> tooltip = new java.util.ArrayList<>();
            com.reiko.signaltower.radio.FrequencyNotes.addTooltipLine(2303, tooltip);
            com.reiko.signaltower.radio.FrequencyNotes.addTooltipLine(2304, tooltip);
            check(tooltip.size() == 1 && keyOf(tooltip.get(0)).endsWith("tooltip.reikos_signal_tower.note")
                    && tooltip.get(0).getString().contains("bell"), "A tooltip line only for a noted frequency: " + tooltip);
            com.reiko.signaltower.radio.FrequencyNotes.clientClear();
            check(com.reiko.signaltower.radio.FrequencyNotes.noteOf(null, 2304) == null, "Cleared on leaving");
        } finally {
            com.reiko.signaltower.radio.FrequencyNotes.clientClear();
        }
        helper.succeed();
    }

    /**
     * The Note screen's packet: needs a tuner in hand, sets and removes a note (saying so), does
     * nothing when nothing changes, and on a private frequency only lets members and operators
     * change it.
     */
    @GameTest(template = PLATFORM)
    public static void noteThroughTheTunerPacket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var notes = com.reiko.signaltower.radio.FrequencyNotes.get(level.getServer());
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        Player player = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Writer", false, bar, new java.util.ArrayList<>());
        Player friend = namedPlayer(helper, new BlockPos(1, 1, 1), friendId, "Friend", false, bar, new java.util.ArrayList<>());
        Player op = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Admin", true, bar, new java.util.ArrayList<>());
        java.util.function.BiFunction<Player, com.reiko.signaltower.network.SetFrequencyNotePayload, Boolean> apply =
                com.reiko.signaltower.network.SetFrequencyNotePayload::apply;
        try {
            check(!apply.apply(player, new com.reiko.signaltower.network.SetFrequencyNotePayload(2305, "barn")), "No tuner in hand: refused");
            check(notes.get(2305) == null, "Nothing stored");
            for (Player p : List.of(player, friend, op)) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
            }
            check(apply.apply(player, new com.reiko.signaltower.network.SetFrequencyNotePayload(2305, "opens the barn door")), "Set");
            check("opens the barn door".equals(com.reiko.signaltower.radio.FrequencyNotes.noteOf(level, 2305))
                    && lastKey(bar).endsWith("note_set"), "Stored, and said: " + lastKey(bar));
            check(!apply.apply(player, new com.reiko.signaltower.network.SetFrequencyNotePayload(2305, " opens the barn door ")),
                    "The same note again changes nothing");
            check(apply.apply(player, new com.reiko.signaltower.network.SetFrequencyNotePayload(2305, "")), "Removed");
            check(notes.get(2305) == null && lastKey(bar).endsWith("note_removed"), "Gone: " + lastKey(bar));

            claims.put(2306, ownerId, "Owner", java.util.Map.of(friendId, "Friend"));
            check(!apply.apply(player, new com.reiko.signaltower.network.SetFrequencyNotePayload(2306, "mine now")), "Not a member: refused");
            check(notes.get(2306) == null && lastKey(bar).endsWith("private_refused_note"), "Told why: " + lastKey(bar));
            check(apply.apply(friend, new com.reiko.signaltower.network.SetFrequencyNotePayload(2306, "vault door")), "A friend may");
            check(apply.apply(op, new com.reiko.signaltower.network.SetFrequencyNotePayload(2306, "checked by an admin")), "So may an operator");
            check("checked by an admin".equals(notes.get(2306)), "Stored");
        } finally {
            notes.set(2305, "");
            notes.set(2306, "");
            claims.remove(2306);
        }
        helper.succeed();
    }

    /**
     * Notes in Jade (under the frequency line, and under both of a converter's) and in the
     * directory, which lists a frequency that only has a note.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void notesInJadeAndDirectory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var notes = com.reiko.signaltower.radio.FrequencyNotes.get(level.getServer());
        notes.set(2307, "opens the barn door");
        notes.set(2309, "farm lights");
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos converter = new BlockPos(3, 1, 1);
        BlockPos plain = new BlockPos(5, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 2307);
        place(helper, converter, ModRegistry.RADIO_CONVERTER.get(), 2307);
        ((com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity) helper.getBlockEntity(converter)).setFrequencies(2307, 2309);
        place(helper, plain, ModRegistry.RADIO_RECEIVER.get(), 2308);
        Player player = mockPlayerAt(helper, new BlockPos(1, 1, 3));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    try {
                        CompoundTag txInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx));
                        check(txInfo != null && "opens the barn door".equals(txInfo.getString("note")), "Jade's data has the note: " + txInfo);
                        List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), txInfo));
                        check(keys.size() > 1 && keys.get(0).equals("frequency") && keys.get(1).equals("note"), "Right under the frequency: " + keys);

                        CompoundTag convInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(converter));
                        check(convInfo != null && "farm lights".equals(convInfo.getString("output_note")), "The converter's output note: " + convInfo);
                        List<String> convKeys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(converter), convInfo));
                        check(convKeys.subList(0, 4).equals(List.of("listens", "note", "sends", "note")), "Under both of its frequencies: " + convKeys);

                        CompoundTag plainInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(plain));
                        check(plainInfo != null && !plainInfo.contains("note")
                                && !lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(plain), plainInfo)).contains("note"),
                                "No note, no line");

                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
                        var listed = com.reiko.signaltower.network.DirectoryRequestPayload.entriesFor(player).stream()
                                .map(com.reiko.signaltower.network.DirectoryResultPayload.Entry::frequency).toList();
                        check(listed.contains(2307) && listed.contains(2309) && !listed.contains(2308), "The directory lists noted frequencies: " + listed);
                    } finally {
                        notes.set(2307, "");
                        notes.set(2309, "");
                    }
                })
                .thenSucceed();
    }

    // ---- Members-only notes ----

    /**
     * A note can be kept to members: the switch is saved (old saves load without it), removing the
     * note or saving it without the switch drops it, and it survives the network (all three packets)
     * and the client's copy, whose tooltip line says "(members only)".
     */
    @GameTest(template = PLATFORM)
    public static void membersOnlyNotesStoredAndSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var notes = new com.reiko.signaltower.radio.FrequencyNotes();
        notes.set(2311, "vault door", true);
        check(notes.isMembersOnly(2311) && "vault door".equals(notes.get(2311)), "Kept to members");
        notes.set(2311, "vault door");
        check(!notes.isMembersOnly(2311), "Saved without the switch: open again");
        notes.set(2311, "vault door", true);
        notes.set(2311, "");
        check(notes.get(2311) == null && !notes.isMembersOnly(2311), "Removing the note drops the switch");
        notes.set(2312, "vault door", true);
        notes.set(2313, "barn");
        CompoundTag saved = notes.save(new CompoundTag(), level.registryAccess());
        var loaded = com.reiko.signaltower.radio.FrequencyNotes.load(saved, level.registryAccess());
        check(loaded.isMembersOnly(2312) && !loaded.isMembersOnly(2313) && loaded.all().size() == 2, "Saved and loaded");
        saved.remove("members_only");
        var old = com.reiko.signaltower.radio.FrequencyNotes.load(saved, level.registryAccess());
        check(!old.isMembersOnly(2312) && "vault door".equals(old.get(2312)), "A save from before v0.32.0 loads open");
        loaded.release(2312);
        check(!loaded.isMembersOnly(2312) && "vault door".equals(loaded.get(2312)), "Released, the note stays");

        var buffer = Unpooled.buffer();
        var one = new com.reiko.signaltower.network.FrequencyNotePayload(2312, "vault door", true);
        com.reiko.signaltower.network.FrequencyNotePayload.STREAM_CODEC.encode(buffer, one);
        check(com.reiko.signaltower.network.FrequencyNotePayload.STREAM_CODEC.decode(buffer).equals(one), "One note survives the network");
        var list = new com.reiko.signaltower.network.FrequencyNotesPayload(java.util.Map.of(2312, "vault door", 2313, "barn"), List.of(2312));
        com.reiko.signaltower.network.FrequencyNotesPayload.STREAM_CODEC.encode(buffer, list);
        var listBack = com.reiko.signaltower.network.FrequencyNotesPayload.STREAM_CODEC.decode(buffer);
        check(listBack.notes().equals(list.notes()) && listBack.membersOnly().equals(List.of(2312)), "So does the list");
        var set = new com.reiko.signaltower.network.SetFrequencyNotePayload(2312, "vault door", true);
        com.reiko.signaltower.network.SetFrequencyNotePayload.STREAM_CODEC.encode(buffer, set);
        check(com.reiko.signaltower.network.SetFrequencyNotePayload.STREAM_CODEC.decode(buffer).equals(set), "And the tuner's packet");
        check(!new com.reiko.signaltower.network.SetFrequencyNotePayload(2312, "barn").membersOnly(), "Open unless asked");

        try {
            com.reiko.signaltower.radio.FrequencyNotes.clientReplaceAll(java.util.Map.of(2312, "vault door", 2313, "barn"), List.of(2312));
            check(com.reiko.signaltower.radio.FrequencyNotes.clientMembersOnly(2312)
                    && !com.reiko.signaltower.radio.FrequencyNotes.clientMembersOnly(2313), "From the full list");
            List<Component> tooltip = new java.util.ArrayList<>();
            com.reiko.signaltower.radio.FrequencyNotes.addTooltipLine(2312, tooltip);
            com.reiko.signaltower.radio.FrequencyNotes.addTooltipLine(2313, tooltip);
            check(tooltip.size() == 2 && tooltip.get(0).getSiblings().stream().anyMatch(c -> keyOf(c).endsWith("note_members_only"))
                    && tooltip.get(1).getSiblings().isEmpty(), "The tooltip says so: " + tooltip);
            com.reiko.signaltower.radio.FrequencyNotes.clientSet(2312, "vault door", false);
            check(!com.reiko.signaltower.radio.FrequencyNotes.clientMembersOnly(2312), "One change at a time");
            com.reiko.signaltower.radio.FrequencyNotes.clientSet(2313, "barn", true);
            com.reiko.signaltower.radio.FrequencyNotes.clientSet(2313, "", false);
            check(com.reiko.signaltower.radio.FrequencyNotes.noteOf(null, 2313) == null
                    && !com.reiko.signaltower.radio.FrequencyNotes.clientMembersOnly(2313), "Taken away (no longer a member)");
        } finally {
            com.reiko.signaltower.radio.FrequencyNotes.clientClear();
        }
        check(!com.reiko.signaltower.radio.FrequencyNotes.clientMembersOnly(2312), "Cleared on leaving");
        helper.succeed();
    }

    /**
     * Who gets a members-only note: the owner, a friend, a member of a group the frequency uses
     * and an operator; not a stranger, nor nobody. The stranger's join list and one-note packet
     * leave it out. Made public, everyone sees it, and claiming it again doesn't hide it.
     */
    @GameTest(template = PLATFORM)
    public static void membersOnlyNoteVisibility(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var notes = com.reiko.signaltower.radio.FrequencyNotes.get(server);
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UUID crewId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        BlockPos at = new BlockPos(1, 1, 1);
        Player owner = namedPlayer(helper, at, ownerId, "Owner", false, bar, new java.util.ArrayList<>());
        Player friend = namedPlayer(helper, at, friendId, "Friend", false, bar, new java.util.ArrayList<>());
        Player crew = namedPlayer(helper, at, crewId, "Crewmate", false, bar, new java.util.ArrayList<>());
        Player stranger = namedPlayer(helper, at, UUID.randomUUID(), "Stranger", false, bar, new java.util.ArrayList<>());
        Player op = namedPlayer(helper, at, UUID.randomUUID(), "Admin", true, bar, new java.util.ArrayList<>());
        try {
            claims.putGroup(ownerId, null, "Vault crew", java.util.Map.of(crewId, "Crewmate"));
            claims.put(2315, ownerId, "Owner", java.util.Map.of(friendId, "Friend"), List.of("Vault crew"));
            notes.set(2315, "vault door", true);
            notes.set(2316, "barn");
            for (Player p : List.of(owner, friend, crew, op)) {
                check(com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2315, p), p.getName().getString() + " sees it");
            }
            check(!com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2315, stranger)
                    && !com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2315, null)
                    && com.reiko.signaltower.radio.FrequencyNotes.noteFor(server, 2315, stranger) == null, "A stranger doesn't");
            check(com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2316, stranger)
                    && com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2316, null), "Open notes are everyone's");

            var toStranger = com.reiko.signaltower.radio.FrequencyNotes.payloadFor(server, 2315, stranger);
            check(toStranger.note().isEmpty() && !toStranger.membersOnly(), "The stranger's packet says nothing: " + toStranger);
            var toFriend = com.reiko.signaltower.radio.FrequencyNotes.payloadFor(server, 2315, friend);
            check("vault door".equals(toFriend.note()) && toFriend.membersOnly(), "The friend's has it: " + toFriend);
            var joinStranger = com.reiko.signaltower.radio.FrequencyNotes.allFor(server, stranger);
            check(!joinStranger.notes().containsKey(2315) && "barn".equals(joinStranger.notes().get(2316))
                    && joinStranger.membersOnly().isEmpty(), "The stranger joins without it: " + joinStranger);
            var joinCrew = com.reiko.signaltower.radio.FrequencyNotes.allFor(server, crew);
            check("vault door".equals(joinCrew.notes().get(2315)) && joinCrew.membersOnly().contains(2315)
                    && !joinCrew.membersOnly().contains(2316), "The crewmate joins with it: " + joinCrew);

            claims.remove(2315);
            com.reiko.signaltower.network.SetFrequencyPrivacyPayload.changed(server, 2315, null);
            check(!notes.isMembersOnly(2315) && com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2315, stranger),
                    "Made public, everyone sees it");
            var reclaimed = claims.put(2315, ownerId, "Owner", java.util.Map.of());
            com.reiko.signaltower.network.SetFrequencyPrivacyPayload.changed(server, 2315, reclaimed);
            check(com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2315, stranger), "Claimed again, it stays open");
        } finally {
            notes.set(2315, "");
            notes.set(2316, "");
            claims.remove(2315);
            claims.removeGroup(ownerId, "Vault crew");
        }
        helper.succeed();
    }

    /**
     * The tuner's packet keeps a note to members on a private frequency (a switch-only change
     * counts), ignores the switch on a public one, and Jade's data leaves the note out for a
     * stranger looking at a block on it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void membersOnlyNoteThroughTheTunerAndJade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var notes = com.reiko.signaltower.radio.FrequencyNotes.get(server);
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID ownerId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        BlockPos at = new BlockPos(1, 1, 3);
        Player owner = namedPlayer(helper, at, ownerId, "Owner", false, bar, new java.util.ArrayList<>());
        Player stranger = namedPlayer(helper, at, UUID.randomUUID(), "Stranger", false, bar, new java.util.ArrayList<>());
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
        BlockPos tx = new BlockPos(1, 1, 1);
        claims.put(2317, ownerId, "Owner", java.util.Map.of());
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 2317);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    try {
                        var apply = (java.util.function.BiFunction<Player, com.reiko.signaltower.network.SetFrequencyNotePayload, Boolean>)
                                com.reiko.signaltower.network.SetFrequencyNotePayload::apply;
                        check(apply.apply(owner, new com.reiko.signaltower.network.SetFrequencyNotePayload(2317, "vault door", true)), "Set");
                        check(notes.isMembersOnly(2317) && lastKey(bar).endsWith("note_set_members"), "Kept to members: " + lastKey(bar));
                        check(!apply.apply(owner, new com.reiko.signaltower.network.SetFrequencyNotePayload(2317, "vault door", true)),
                                "The same again changes nothing");

                        CompoundTag forStranger = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx), stranger);
                        check(forStranger != null && !forStranger.contains("note")
                                && !lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), forStranger)).contains("note"),
                                "Jade shows the stranger no note: " + forStranger);
                        CompoundTag forOwner = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx), owner);
                        check(forOwner != null && "vault door".equals(forOwner.getString("note")) && forOwner.getBoolean("note_members"),
                                "The owner's has it, marked: " + forOwner);
                        List<Component> lines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), forOwner);
                        check(lines.stream().anyMatch(line -> keyOf(line).endsWith(".note")
                                && line.getSiblings().stream().anyMatch(c -> keyOf(c).endsWith("note_members_only"))), "Its line says members only");

                        check(apply.apply(owner, new com.reiko.signaltower.network.SetFrequencyNotePayload(2317, "vault door", false)),
                                "Flipping only the switch counts");
                        check(!notes.isMembersOnly(2317) && lastKey(bar).endsWith("note_set"), "Open again: " + lastKey(bar));
                        CompoundTag openAgain = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(tx), stranger);
                        check(openAgain != null && "vault door".equals(openAgain.getString("note")) && !openAgain.getBoolean("note_members"),
                                "Now the stranger sees it too");

                        check(apply.apply(owner, new com.reiko.signaltower.network.SetFrequencyNotePayload(2318, "barn", true)), "Public frequency");
                        check("barn".equals(notes.get(2318)) && !notes.isMembersOnly(2318) && lastKey(bar).endsWith("note_set"),
                                "The switch means nothing there: " + lastKey(bar));
                    } finally {
                        notes.set(2317, "");
                        notes.set(2318, "");
                        claims.remove(2317);
                    }
                })
                .thenSucceed();
    }

    // ---- Frequency audit ----

    /**
     * The audit lists every block on a private frequency: the owner's, a friend's and an old one
     * with no recorded tuner with their positions; another player's only counted (per player, kind
     * and status), with no position. Silent senders, deaf listeners (receivers and a converter on
     * an encrypted frequency) and what each sends or outputs are reported; the owner's come first.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void auditListsBlocksAndHidesOthers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        claims.put(2401, ownerId, "Owner", java.util.Map.of(friendId, "Friend"), List.of(), true);
        SignalNetwork.get(level).invalidate(2401);
        BlockPos ownTx = new BlockPos(1, 1, 1);
        BlockPos strangerTx = new BlockPos(3, 1, 1);
        BlockPos oldTx = new BlockPos(5, 1, 1);
        BlockPos friendRx = new BlockPos(7, 1, 1);
        BlockPos strangerRx1 = new BlockPos(9, 1, 1);
        BlockPos strangerRx2 = new BlockPos(11, 1, 1);
        BlockPos ownToggle = new BlockPos(13, 1, 1);
        BlockPos ownConverter = new BlockPos(15, 1, 1);
        BlockPos strangerConverter = new BlockPos(17, 1, 1);
        java.util.Map<BlockPos, UUID> tuners = new java.util.LinkedHashMap<>();
        tuners.put(ownTx, ownerId);
        tuners.put(strangerTx, strangerId);
        tuners.put(friendRx, friendId);
        tuners.put(strangerRx1, strangerId);
        tuners.put(strangerRx2, strangerId);
        tuners.put(ownToggle, ownerId);
        tuners.put(ownConverter, ownerId);
        tuners.put(strangerConverter, strangerId);
        place(helper, ownTx, ModRegistry.RADIO_TRANSMITTER.get(), 2401);
        place(helper, strangerTx, ModRegistry.RADIO_TRANSMITTER.get(), 2401);
        place(helper, oldTx, ModRegistry.RADIO_TRANSMITTER.get(), 2401);
        place(helper, friendRx, ModRegistry.RADIO_RECEIVER.get(), 2401);
        place(helper, strangerRx1, ModRegistry.RADIO_RECEIVER.get(), 2401);
        place(helper, strangerRx2, ModRegistry.RADIO_RECEIVER.get(), 2401);
        place(helper, ownToggle, ModRegistry.RADIO_TOGGLE_RECEIVER.get(), 2401);
        place(helper, ownConverter, ModRegistry.RADIO_CONVERTER.get(), 2401);
        place(helper, strangerConverter, ModRegistry.RADIO_CONVERTER.get(), 2401);
        ((com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity) helper.getBlockEntity(ownConverter)).setFrequencies(2401, 2402);
        ((com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity) helper.getBlockEntity(strangerConverter)).setFrequencies(2401, 2403);
        tuners.forEach((pos, uuid) -> ((RadioBlockEntity) helper.getBlockEntity(pos)).setTunedBy(uuid));
        helper.setBlock(ownTx.north(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> assertOutput(helper, friendRx, 15))
                .thenExecute(() -> {
                    try {
                        var rows = com.reiko.signaltower.radio.FrequencyAudit.audit(level.getServer(), 2401, null, 512);
                        java.util.function.Function<BlockPos, com.reiko.signaltower.radio.FrequencyAudit.Entry> at = pos -> rows.stream()
                                .filter(e -> e.pos().isPresent() && e.pos().get().equals(helper.absolutePos(pos))).findFirst().orElse(null);
                        var own = at.apply(ownTx);
                        check(own != null && own.who() == com.reiko.signaltower.radio.FrequencyAudit.Who.OWNER && "Owner".equals(own.tuner())
                                && own.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.TRANSMITTER && own.power() == 15
                                && own.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.WORKING
                                && own.dimension().equals("minecraft:overworld"), "The owner's transmitter: " + own);
                        var old = at.apply(oldTx);
                        check(old != null && old.who() == com.reiko.signaltower.radio.FrequencyAudit.Who.UNKNOWN && old.tuner().isEmpty()
                                && old.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.WORKING, "The old one, with its position: " + old);
                        var friend = at.apply(friendRx);
                        check(friend != null && friend.who() == com.reiko.signaltower.radio.FrequencyAudit.Who.MEMBER && "Friend".equals(friend.tuner())
                                && friend.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.RECEIVER && friend.power() == 15,
                                "The friend's receiver: " + friend);
                        var toggle = at.apply(ownToggle);
                        check(toggle != null && toggle.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.TOGGLE_RECEIVER, "The toggle receiver: " + toggle);
                        var converter = at.apply(ownConverter);
                        check(converter != null && converter.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.CONVERTER_LISTENS
                                && converter.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.WORKING, "The owner's converter: " + converter);
                        for (BlockPos hidden : List.of(strangerTx, strangerRx1, strangerRx2, strangerConverter)) {
                            check(at.apply(hidden) == null, "A stranger's block has no position: " + hidden);
                        }
                        var others = rows.stream().filter(e -> e.who() == com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER).toList();
                        check(others.stream().allMatch(e -> e.pos().isEmpty() && e.dimension().isEmpty() && e.power() == -1
                                && e.tuner().equals(strangerId.toString().substring(0, 8))), "Others: counted, no position or power: " + others);
                        check(others.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.TRANSMITTER
                                && e.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.SILENT && e.count() == 1), "The silent transmitter: " + others);
                        check(others.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.RECEIVER
                                && e.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.DEAF && e.count() == 2), "Two deaf receivers in one row: " + others);
                        check(others.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.CONVERTER_LISTENS
                                && e.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.DEAF), "The deaf converter: " + others);
                        check(rows.size() == 9 - 4 + 3, "Rows: " + rows.size());
                        check(rows.get(0).who() == com.reiko.signaltower.radio.FrequencyAudit.Who.OWNER
                                && rows.get(rows.size() - 1).who() == com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER, "The owner's first, others last");
                        check(com.reiko.signaltower.radio.FrequencyAudit.audit(level.getServer(), 2402, null, 512).isEmpty(), "A public frequency has no audit");
                        check(com.reiko.signaltower.radio.FrequencyAudit.audit(level.getServer(), 2401, null, 2).size() == 2, "The limit holds");
                    } finally {
                        claims.remove(2401);
                        SignalNetwork.get(level).invalidate(2401);
                    }
                })
                .thenSucceed();
    }

    /**
     * Only the owner (or an operator) may audit a frequency; a friend, a stranger and anyone on a
     * public frequency get "not allowed". The answer survives the network.
     */
    @GameTest(template = PLATFORM)
    public static void auditOnlyForTheOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID ownerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        Player owner = namedPlayer(helper, new BlockPos(1, 1, 1), ownerId, "Owner", false, bar, new java.util.ArrayList<>());
        Player friend = namedPlayer(helper, new BlockPos(1, 1, 1), friendId, "Friend", false, bar, new java.util.ArrayList<>());
        Player op = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Admin", true, bar, new java.util.ArrayList<>());
        claims.put(2404, ownerId, "Owner", java.util.Map.of(friendId, "Friend"));
        try {
            check(com.reiko.signaltower.network.AuditRequestPayload.answer(owner, 2404).allowed(), "The owner may");
            check(com.reiko.signaltower.network.AuditRequestPayload.answer(op, 2404).allowed(), "An operator may");
            var refused = com.reiko.signaltower.network.AuditRequestPayload.answer(friend, 2404);
            check(!refused.allowed() && refused.entries().isEmpty(), "A friend may not");
            check(!com.reiko.signaltower.network.AuditRequestPayload.answer(owner, 2405).allowed(), "Nor anyone on a public frequency");
            check(!com.reiko.signaltower.network.AuditRequestPayload.answer(op, 2405).allowed(), "Not even an operator");

            var payload = new com.reiko.signaltower.network.AuditResultPayload(2404, true, List.of(
                    new com.reiko.signaltower.radio.FrequencyAudit.Entry(com.reiko.signaltower.radio.FrequencyAudit.Kind.CONVERTER_SENDS,
                            "minecraft:the_nether", java.util.Optional.of(new BlockPos(1, -60, 3)), "Owner",
                            com.reiko.signaltower.radio.FrequencyAudit.Who.OWNER, com.reiko.signaltower.radio.FrequencyAudit.Status.WORKING, 1, 15),
                    new com.reiko.signaltower.radio.FrequencyAudit.Entry(com.reiko.signaltower.radio.FrequencyAudit.Kind.RECEIVER,
                            "", java.util.Optional.empty(), "Zed", com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER,
                            com.reiko.signaltower.radio.FrequencyAudit.Status.DEAF, 3, -1)));
            var buffer = Unpooled.buffer();
            com.reiko.signaltower.network.AuditResultPayload.STREAM_CODEC.encode(buffer, payload);
            check(com.reiko.signaltower.network.AuditResultPayload.STREAM_CODEC.decode(buffer).equals(payload), "The answer survives the network");
            var request = new com.reiko.signaltower.network.AuditRequestPayload(2404);
            com.reiko.signaltower.network.AuditRequestPayload.STREAM_CODEC.encode(buffer, request);
            check(com.reiko.signaltower.network.AuditRequestPayload.STREAM_CODEC.decode(buffer).equals(request), "So does the request");
        } finally {
            claims.remove(2404);
        }
        helper.succeed();
    }

    /**
     * Audit beams: clicking a row turns its beam on for a minute (in that dimension only) and
     * clicking again turns it off; beams burn down tick by tick and go when spent; a ninth beam
     * drops the one with the least time left; leaving the world clears them.
     */
    @GameTest(template = PLATFORM)
    public static void auditBeamsOnOffAndBurnDown(GameTestHelper helper) {
        String overworld = "minecraft:overworld";
        String nether = "minecraft:the_nether";
        BlockPos spot = new BlockPos(2501, -60, 7);
        try {
            com.reiko.signaltower.client.AuditBeams.clear();
            check(com.reiko.signaltower.client.AuditBeams.toggle(overworld, spot), "On");
            check(com.reiko.signaltower.client.AuditBeams.secondsLeft(overworld, spot) == 60
                    && !com.reiko.signaltower.client.AuditBeams.isOn(nether, spot), "A minute, in its own dimension");
            check(com.reiko.signaltower.client.AuditBeams.toggle(nether, spot), "The same spot in the Nether is another beam");
            check(com.reiko.signaltower.client.AuditBeams.in(overworld).size() == 1
                    && com.reiko.signaltower.client.AuditBeams.in(nether).size() == 1, "Each drawn in its own dimension");
            check(!com.reiko.signaltower.client.AuditBeams.toggle(overworld, spot)
                    && !com.reiko.signaltower.client.AuditBeams.isOn(overworld, spot)
                    && com.reiko.signaltower.client.AuditBeams.count() == 1, "Clicked again: off");

            for (int i = 0; i < 20; i++) {
                com.reiko.signaltower.client.AuditBeams.tick();
            }
            check(com.reiko.signaltower.client.AuditBeams.secondsLeft(nether, spot) == 59, "A second burnt: "
                    + com.reiko.signaltower.client.AuditBeams.secondsLeft(nether, spot));
            for (int i = 20; i < com.reiko.signaltower.client.AuditBeams.DURATION_TICKS - 1; i++) {
                com.reiko.signaltower.client.AuditBeams.tick();
            }
            check(com.reiko.signaltower.client.AuditBeams.secondsLeft(nether, spot) == 1, "The last tick still shows");
            com.reiko.signaltower.client.AuditBeams.tick();
            check(com.reiko.signaltower.client.AuditBeams.count() == 0, "Gone after a minute");

            for (int i = 0; i < com.reiko.signaltower.client.AuditBeams.MAX_BEAMS; i++) {
                com.reiko.signaltower.client.AuditBeams.toggle(overworld, new BlockPos(2500 + i, -60, 0));
                com.reiko.signaltower.client.AuditBeams.tick();
            }
            check(com.reiko.signaltower.client.AuditBeams.toggle(overworld, new BlockPos(2600, -60, 0)), "A ninth");
            check(com.reiko.signaltower.client.AuditBeams.count() == com.reiko.signaltower.client.AuditBeams.MAX_BEAMS
                    && !com.reiko.signaltower.client.AuditBeams.isOn(overworld, new BlockPos(2500, -60, 0))
                    && com.reiko.signaltower.client.AuditBeams.isOn(overworld, new BlockPos(2501, -60, 0)), "The oldest made room");
            com.reiko.signaltower.client.AuditBeams.clear();
            check(com.reiko.signaltower.client.AuditBeams.count() == 0, "Cleared on leaving");
        } finally {
            com.reiko.signaltower.client.AuditBeams.clear();
        }
        helper.succeed();
    }

    // ---- Handing a frequency over ----

    /**
     * Handing over: only the owner may (not a friend, not an operator), only to one of its members
     * (by name, ignoring case; a group member counts), and not to someone at their limit. The new
     * owner gets it with everyone else on it, the old owner first, as single friends (groups
     * flattened), still encrypted, with its note; the old owner's blocks may still send on it, a
     * stranger's may not. A public frequency has nothing to hand over; the packet survives the network.
     */
    @GameTest(template = PLATFORM)
    public static void handOverFrequency(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        var notes = com.reiko.signaltower.radio.FrequencyNotes.get(server);
        UUID devId = UUID.randomUUID();
        UUID steveId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();
        UUID zedId = UUID.randomUUID();
        List<Component> chat = new java.util.ArrayList<>(); // hand-over messages go to chat
        BlockPos at = new BlockPos(1, 1, 1);
        Player dev = namedPlayer(helper, at, devId, "Dev", false, new java.util.ArrayList<>(), chat);
        Player steve = namedPlayer(helper, at, steveId, "Steve", false, new java.util.ArrayList<>(), chat);
        Player op = namedPlayer(helper, at, UUID.randomUUID(), "Admin", true, new java.util.ArrayList<>(), chat);
        java.util.function.BiFunction<Player, com.reiko.signaltower.network.HandOverFrequencyPayload, Boolean> apply =
                com.reiko.signaltower.network.HandOverFrequencyPayload::apply;
        int limit = com.reiko.signaltower.Config.PRIVATE_FREQUENCIES_PER_PLAYER.get();
        try {
            claims.putGroup(devId, null, "Crew 26", java.util.Map.of(alexId, "Alex"));
            claims.put(2601, devId, "Dev", java.util.Map.of(steveId, "Steve"), List.of("Crew 26"), true);
            notes.set(2601, "vault door", true);

            check(!apply.apply(steve, new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, "Steve")), "A friend can't");
            check(lastKey(chat).endsWith("hand_over_not_owner") && claims.get(2601).owner().equals(devId), "Told why: " + lastKey(chat));
            check(!apply.apply(op, new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, "Steve")), "Nor an operator");
            check(!apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, "Zed")), "Not to a stranger");
            check(lastKey(chat).endsWith("hand_over_not_member"), "Told why: " + lastKey(chat));
            check(!apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, "Dev")), "Not to themselves");

            check(apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, " alex ")), "To a group member");
            var handed = claims.get(2601);
            check(handed.owner().equals(alexId) && "Alex".equals(handed.ownerName()) && lastKey(chat).endsWith("hand_over_done"),
                    "Alex owns it: " + handed.ownerName() + " / " + lastKey(chat));
            check(handed.friendNames().equals(List.of("Dev", "Steve")) && handed.groups().isEmpty(),
                    "Everyone else as single friends, the old owner first: " + handed.friendNames() + " " + handed.groups());
            check(handed.encrypted() && handed.allows(devId) && handed.allows(steveId) && !handed.allows(zedId), "Still encrypted, same members");
            check(com.reiko.signaltower.radio.FrequencyClaims.maySend(helper.getLevel(), 2601, devId)
                    && !com.reiko.signaltower.radio.FrequencyClaims.maySend(helper.getLevel(), 2601, zedId), "Dev's blocks still send");
            check(notes.isMembersOnly(2601) && com.reiko.signaltower.radio.FrequencyNotes.visibleTo(server, 2601, dev),
                    "Its note stays, and Dev still sees it");
            check(!apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, "Steve")), "Dev can't give it away again");

            check(!apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2602, "Steve")), "Public: nothing to hand over");
            check(lastKey(chat).endsWith("hand_over_public"), "Told why: " + lastKey(chat));

            if (limit > 0) {
                for (int i = 0; i < limit; i++) {
                    claims.put(2700 + i, steveId, "Steve", java.util.Map.of());
                }
                claims.put(2602, devId, "Dev", java.util.Map.of(steveId, "Steve"));
                check(!apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2602, "Steve")), "Steve is at his limit");
                check(lastKey(chat).endsWith("hand_over_limit") && claims.get(2602).owner().equals(devId), "Told why: " + lastKey(chat));
            }

            java.util.Map<UUID, String> many = new java.util.LinkedHashMap<>();
            for (int i = 0; i < com.reiko.signaltower.radio.FrequencyClaims.MAX_FRIENDS - 2; i++) {
                many.put(UUID.randomUUID(), "Friend" + i);
            }
            java.util.Map<UUID, String> big = new java.util.LinkedHashMap<>();
            for (int i = 0; i < 5; i++) {
                big.put(UUID.randomUUID(), "Crew" + i);
            }
            claims.putGroup(devId, null, "Big 26", big);
            claims.put(2603, devId, "Dev", many, List.of("Big 26"));
            check(com.reiko.signaltower.radio.FrequencyClaims.friendsAfterHandOver(claims.get(2603), many.keySet().iterator().next()).size()
                    == claims.get(2603).memberNames().size(), "Its member count is what the screen checks");
            check(!apply.apply(dev, new com.reiko.signaltower.network.HandOverFrequencyPayload(2603, "Friend0")), "Too many to flatten");
            check(lastKey(chat).endsWith("hand_over_too_many") && claims.get(2603).owner().equals(devId), "Told why: " + lastKey(chat));

            var buffer = Unpooled.buffer();
            var payload = new com.reiko.signaltower.network.HandOverFrequencyPayload(2601, "Steve");
            com.reiko.signaltower.network.HandOverFrequencyPayload.STREAM_CODEC.encode(buffer, payload);
            check(com.reiko.signaltower.network.HandOverFrequencyPayload.STREAM_CODEC.decode(buffer).equals(payload), "Survives the network");
        } finally {
            notes.set(2601, "");
            for (int f : new int[]{2601, 2602, 2603}) {
                claims.remove(f);
            }
            for (int i = 0; i < Math.max(limit, 0); i++) {
                claims.remove(2700 + i);
            }
            claims.removeGroup(devId, "Crew 26");
            claims.removeGroup(devId, "Big 26");
        }
        helper.succeed();
    }

    // ---- Frequency log ----

    /**
     * The frequency log: a transmitter's turn-on (the owner's, with its position), one with no
     * recorded tuner (position shown), a friend's remote pulse and a stranger's toggle (named, no
     * position), newest first; a silenced stranger's transmitter isn't a turn-on; another
     * player's transmitter is named without a position; a converter's output frequency shows them
     * as carried over. Only the owner and operators get it. Causes survive saving; old saves
     * load without one. The answer survives the network.
     */
    @GameTest(template = PLATFORM)
    public static void frequencyLogShowsTurnOns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        SignalNetwork network = SignalNetwork.get(level);
        UUID devId = UUID.randomUUID();
        UUID steveId = UUID.randomUUID();
        UUID zedId = UUID.randomUUID();
        BlockPos at = new BlockPos(1, 1, 1);
        Player dev = namedPlayer(helper, at, devId, "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player steve = namedPlayer(helper, at, steveId, "Steve", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player op = namedPlayer(helper, at, UUID.randomUUID(), "Admin", true, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        BlockPos tx = new BlockPos(8_100_000, 64, 8_100_000);
        BlockPos strangers = tx.south(3);
        BlockPos old = tx.north(3);
        BlockPos converter = tx.east(10);
        BlockPos steves = tx.west(3);
        List<SignalNetwork.Coverage> here = List.of(new SignalNetwork.Coverage(tx, 32, false));
        try {
            claims.put(2801, devId, "Dev", java.util.Map.of(steveId, "Steve"));
            claims.put(2802, devId, "Dev", java.util.Map.of());
            network.updateRepeater(converter, 2801, 2802, 40, devId);
            network.updateTransmitter(tx, 2801, 15, 32, devId);
            network.updateTransmitter(strangers, 2801, 15, 32, zedId); // silenced: not a turn-on
            network.updateTransmitter(old, 2801, 15, 32, null);
            network.pulse(2801, here, level.getGameTime() + 20, SignalNetwork.Cause.remote(false, steveId, "Steve"));
            network.toggleLatch(2801, here, SignalNetwork.Cause.remote(true, zedId, "Zed"));

            var log = com.reiko.signaltower.radio.FrequencyLog.log(server, 2801);
            check(log.size() == 4, "Four turn-ons: " + log);
            check(log.get(0).kind() == SignalNetwork.CauseKind.REMOTE_TOGGLE && "Zed".equals(log.get(0).who())
                    && log.get(0).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER && log.get(0).pos().isEmpty(),
                    "Newest first: the stranger's toggle, named, no position: " + log.get(0));
            check(log.get(1).kind() == SignalNetwork.CauseKind.REMOTE_PULSE && "Steve".equals(log.get(1).who())
                    && log.get(1).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.MEMBER && log.get(1).pos().isEmpty(),
                    "The friend's pulse: " + log.get(1));
            check(log.get(2).kind() == SignalNetwork.CauseKind.TRANSMITTER && log.get(2).who().isEmpty()
                    && log.get(2).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.UNKNOWN
                    && log.get(2).pos().equals(java.util.Optional.of(old)), "The old transmitter, with its position: " + log.get(2));
            check(log.get(3).kind() == SignalNetwork.CauseKind.TRANSMITTER && "Dev".equals(log.get(3).who())
                    && log.get(3).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.OWNER
                    && log.get(3).pos().equals(java.util.Optional.of(tx)) && log.get(3).from() == 0, "Dev's transmitter: " + log.get(3));
            check(log.stream().allMatch(e -> "minecraft:overworld".equals(e.dimension())), "In the Overworld");

            var carried = com.reiko.signaltower.radio.FrequencyLog.log(server, 2802);
            check(carried.size() == 4 && carried.stream().allMatch(e -> e.from() == 2801)
                    && carried.get(3).pos().equals(java.util.Optional.of(tx)), "The converter's output shows them carried over: " + carried);

            claims.put(2803, devId, "Dev", java.util.Map.of(steveId, "Steve"));
            network.updateTransmitter(steves, 2803, 15, 32, steveId);
            claims.put(2803, devId, "Dev", java.util.Map.of()); // Steve taken off
            var former = com.reiko.signaltower.radio.FrequencyLog.log(server, 2803);
            // No longer in the claim's records and never seen by this test server's name cache: the start of the UUID.
            check(former.size() == 1 && steveId.toString().substring(0, 8).equals(former.get(0).who()) && former.get(0).pos().isEmpty()
                    && former.get(0).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER,
                    "Someone no longer on it: named, not located: " + former);

            var forDev = com.reiko.signaltower.network.LogRequestPayload.answer(dev, 2801);
            check(forDev.allowed() && forDev.entries().size() == 4 && forDev.gameTime() == level.getGameTime(), "The owner gets it");
            var forSteve = com.reiko.signaltower.network.LogRequestPayload.answer(steve, 2801);
            check(!forSteve.allowed() && forSteve.entries().isEmpty(), "A friend doesn't");
            check(com.reiko.signaltower.network.LogRequestPayload.answer(op, 2801).allowed(), "An operator does");
            check(!com.reiko.signaltower.network.LogRequestPayload.answer(dev, 2804).allowed(), "A public frequency has no log");

            var buffer = Unpooled.buffer();
            com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC.encode(buffer, forDev);
            check(com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC.decode(buffer).equals(forDev), "Survives the network");

            SignalNetwork fresh = new SignalNetwork().attachTo(level);
            BlockPos far = new BlockPos(8_200_000, 64, 8_200_000);
            fresh.updateTransmitter(far, 2805, 15, 32, devId);
            fresh.pulse(2805, List.of(new SignalNetwork.Coverage(far, 32, false)), 20, SignalNetwork.Cause.remote(false, steveId, "Steve"));
            CompoundTag saved = fresh.save(new CompoundTag(), level.registryAccess());
            var loaded = SignalNetwork.load(saved, level.registryAccess()).history(2805);
            check(loaded.size() == 2 && loaded.get(0).cause().equals(SignalNetwork.Cause.transmitter(far, devId))
                    && loaded.get(1).cause().equals(SignalNetwork.Cause.remote(false, steveId, "Steve"))
                    && loaded.get(1).from() == 2805, "Causes are saved: " + loaded);
            var turnOns = saved.getList("turn_ons", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < turnOns.size(); i++) {
                turnOns.getCompound(i).remove("cause");
                turnOns.getCompound(i).remove("from");
            }
            var oldSave = SignalNetwork.load(saved, level.registryAccess()).history(2805);
            check(oldSave.size() == 2 && oldSave.stream().allMatch(t -> t.cause().equals(SignalNetwork.Cause.UNKNOWN) && t.from() == 0),
                    "A save from before v0.35.0 loads without causes");
        } finally {
            network.toggleLatch(2801, here); // off again
            for (BlockPos pos : List.of(tx, strangers, old, steves)) {
                network.removeTransmitter(pos);
            }
            network.removeRepeater(converter);
            for (int f = 2801; f <= 2803; f++) {
                claims.remove(f);
            }
        }
        helper.succeed();
    }

    // ---- Guest passes and intruder alerts ----

    /**
     * Guest passes: a guest may send on a private frequency (and hear it, encrypted) until their
     * pass runs out, then drops off; never the owner, a friend, or a ninth guest (a renewal still
     * works when full). The owner's packet renews and removes guests and refuses an offline name,
     * a wrong length, a non-owner and a public frequency. Editing the friends or handing it over
     * keeps the guests (not one who became a friend or the owner); they're saved and sent to clients.
     */
    @GameTest(template = PLATFORM)
    public static void guestPasses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID devId = UUID.randomUUID();
        UUID steveId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        BlockPos at = new BlockPos(1, 1, 1);
        Player dev = namedPlayer(helper, at, devId, "Dev", false, bar, new java.util.ArrayList<>());
        Player steve = namedPlayer(helper, at, steveId, "Steve", false, bar, new java.util.ArrayList<>());
        long now = 1_800_000_000_000L;
        try {
            claims.put(2901, devId, "Dev", java.util.Map.of(steveId, "Steve"), List.of(), true);
            check(claims.putGuest(2901, alexId, "Alex", now + 3_600_000L) != null, "Alex is a guest");
            var claim = claims.get(2901);
            check(claim.allows(alexId) && claim.hears(alexId) && com.reiko.signaltower.radio.FrequencyClaims.maySend(level, 2901, alexId),
                    "A guest sends and hears");
            check(claims.putGuest(2901, steveId, "Steve", now + 1000) == null && claims.putGuest(2901, devId, "Dev", now + 1000) == null,
                    "Not a friend or the owner");
            for (int i = 0; i < com.reiko.signaltower.radio.FrequencyClaims.MAX_GUESTS - 1; i++) {
                claims.putGuest(2901, UUID.randomUUID(), "Guest" + i, now + 3_600_000L);
            }
            check(claims.putGuest(2901, UUID.randomUUID(), "OneTooMany", now + 1000) == null, "At most 8 guests");
            check(claims.putGuest(2901, alexId, "Alex", now + 7_200_000L) != null, "A renewal works when full");

            java.util.function.BiFunction<Player, com.reiko.signaltower.network.GuestPayload, Boolean> apply =
                    (p, payload) -> com.reiko.signaltower.network.GuestPayload.apply(p, payload, now);
            check(apply.apply(dev, new com.reiko.signaltower.network.GuestPayload(2901, " alex ", 86400)), "Renewed for a day");
            check(claims.get(2901).guests().get(alexId).untilMillis() == now + 86_400_000L && lastKey(bar).endsWith("guest_renewed"),
                    "Until a day from now: " + lastKey(bar));
            check(!apply.apply(dev, new com.reiko.signaltower.network.GuestPayload(2901, "Alex", 100)), "Only an hour, a day or a week");
            check(!apply.apply(dev, new com.reiko.signaltower.network.GuestPayload(2901, "Zed", 3600)), "Someone offline can't be added");
            check(lastKey(bar).endsWith("guest_offline"), "Told why: " + lastKey(bar));
            check(!apply.apply(steve, new com.reiko.signaltower.network.GuestPayload(2901, "Alex", 0)), "A friend can't");
            check(lastKey(bar).endsWith("guest_not_owner"), "Told why: " + lastKey(bar));
            check(!apply.apply(dev, new com.reiko.signaltower.network.GuestPayload(2902, "Alex", 3600)), "Public: nothing to do");
            check(lastKey(bar).endsWith("guest_public"), "Told why: " + lastKey(bar));

            claims.put(2901, devId, "Dev", java.util.Map.of(steveId, "Steve"), List.of(), true);
            check(claims.get(2901).guests().containsKey(alexId) && claims.get(2901).guests().size() == com.reiko.signaltower.radio.FrequencyClaims.MAX_GUESTS,
                    "Editing its friends keeps the guests");
            var info = com.reiko.signaltower.radio.FrequencyClaims.ClaimInfo.of(claims.get(2901));
            check(info.guest("alex") != null && info.isMember(alexId, "Alex") && !info.members().contains("Alex"),
                    "Clients see Alex as a guest (and so a member)");
            var buffer = Unpooled.buffer();
            com.reiko.signaltower.radio.FrequencyClaims.ClaimInfo.STREAM_CODEC.encode(buffer, info);
            check(com.reiko.signaltower.radio.FrequencyClaims.ClaimInfo.STREAM_CODEC.decode(buffer).equals(info), "Guests survive the network");
            var loaded = com.reiko.signaltower.radio.FrequencyClaims.load(claims.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(loaded.get(2901).guests().equals(claims.get(2901).guests()), "And saving");

            check(apply.apply(dev, new com.reiko.signaltower.network.GuestPayload(2901, "Alex", 0)), "Removed");
            check(!claims.get(2901).allows(alexId) && lastKey(bar).endsWith("guest_removed"), "Alex is off: " + lastKey(bar));
            check(!apply.apply(dev, new com.reiko.signaltower.network.GuestPayload(2901, "Alex", 0)), "Not a guest any more");

            claims.putGuest(2901, alexId, "Alex", now + 1000);
            com.reiko.signaltower.network.GuestPayload.expire(server, now);
            check(claims.get(2901).allows(alexId), "Not yet");
            com.reiko.signaltower.network.GuestPayload.expire(server, now + 1000);
            check(!claims.get(2901).allows(alexId) && !com.reiko.signaltower.radio.FrequencyClaims.maySend(level, 2901, alexId),
                    "The pass ran out: Alex's blocks go silent");

            claims.putGuest(2901, alexId, "Alex", now + 3_600_000L);
            claims.put(2901, devId, "Dev", java.util.Map.of(steveId, "Steve", alexId, "Alex"), List.of(), true);
            check(!claims.get(2901).guests().containsKey(alexId) && claims.get(2901).allows(alexId), "Made a friend: no longer a guest");
            claims.put(2901, devId, "Dev", java.util.Map.of(steveId, "Steve"), List.of(), true);
            claims.putGuest(2901, alexId, "Alex", now + 3_600_000L);
            var handed = claims.handOver(2901, steveId, "Steve");
            check(handed != null && handed.owner().equals(steveId) && handed.guests().containsKey(alexId), "Handed over: Alex stays a guest");
        } finally {
            claims.remove(2901);
        }
        helper.succeed();
    }

    /**
     * Intruder alerts: a stranger's refused remote press (through the real remote code), a sender
     * they place that stays silent (through the real placement) and a tune attempt are kept for the
     * owner while they're away, one per intruder, frequency and kind a minute; members and public
     * frequencies don't alert. At login the owner gets how many and the last five; older ones are
     * only counted. Kept alerts survive saving.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void intruderAlerts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        var alerts = com.reiko.signaltower.radio.IntruderAlerts.get(server);
        UUID devId = UUID.randomUUID();
        UUID steveId = UUID.randomUUID();
        Player zed = namedPlayer(helper, new BlockPos(1, 1, 1), UUID.randomUUID(), "Zed", false,
                new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player steve = namedPlayer(helper, new BlockPos(1, 1, 1), steveId, "Steve", false,
                new java.util.ArrayList<>(), new java.util.ArrayList<>());
        BlockPos tx = new BlockPos(3, 1, 1);
        try {
            alerts.resetQuiet();
            claims.put(2903, devId, "Dev", java.util.Map.of(steveId, "Steve"));
            RemoteControlItem.sendSignal(level, zed, new ItemStack(ModRegistry.REMOTE_CONTROL.get()), 2903, false, 20);
            var kept = alerts.missedFor(devId);
            check(kept.size() == 1 && kept.get(0).kind() == com.reiko.signaltower.radio.IntruderAlerts.Kind.REMOTE
                    && "Zed".equals(kept.get(0).intruder()) && kept.get(0).frequency() == 2903, "A refused press, kept for Dev: " + kept);
            RemoteControlItem.sendSignal(level, zed, new ItemStack(ModRegistry.REMOTE_CONTROL.get()), 2903, false, 20);
            check(alerts.missedFor(devId).size() == 1, "Not again within a minute");

            place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 2903);
            BlockState state = helper.getBlockState(tx);
            state.getBlock().setPlacedBy(level, helper.absolutePos(tx), state, zed, new ItemStack(ModRegistry.RADIO_TRANSMITTER_ITEM.get()));
            kept = alerts.missedFor(devId);
            check(kept.size() == 2 && kept.get(1).kind() == com.reiko.signaltower.radio.IntruderAlerts.Kind.SILENT
                    && kept.get(1).block().equals(ModRegistry.RADIO_TRANSMITTER.get().getDescriptionId()), "A silent transmitter: " + kept);

            com.reiko.signaltower.radio.IntruderAlerts.report(server, 2903, steve, com.reiko.signaltower.radio.IntruderAlerts.Kind.TUNE, null);
            com.reiko.signaltower.radio.IntruderAlerts.report(server, 2904, zed, com.reiko.signaltower.radio.IntruderAlerts.Kind.TUNE, null);
            check(alerts.missedFor(devId).size() == 2, "Not for a friend, nor a public frequency");

            var loaded = com.reiko.signaltower.radio.IntruderAlerts.load(alerts.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(loaded.missedFor(devId).equals(alerts.missedFor(devId)), "Kept alerts survive saving");

            var lines = com.reiko.signaltower.radio.IntruderAlerts.missedLines(server, devId);
            check(lines.size() == 3 && keyOf(lines.get(0)).endsWith("intruder_missed")
                    && lines.get(1).getSiblings().stream().anyMatch(c -> keyOf(c).endsWith("intruder_remote"))
                    && lines.get(2).getSiblings().stream().anyMatch(c -> keyOf(c).endsWith("intruder_silent")),
                    "At login: how many, then each: " + lines);
            check(alerts.missedFor(devId).isEmpty() && com.reiko.signaltower.radio.IntruderAlerts.missedLines(server, devId).isEmpty(),
                    "Then they're forgotten");

            for (int i = 0; i < 20; i++) {
                alerts.resetQuiet();
                com.reiko.signaltower.radio.IntruderAlerts.report(server, 2903, zed, com.reiko.signaltower.radio.IntruderAlerts.Kind.TUNE,
                        ModRegistry.RADIO_TRANSMITTER.get());
            }
            check(alerts.missedFor(devId).size() == com.reiko.signaltower.radio.IntruderAlerts.MAX_STORED, "The last 16 are kept");
            lines = com.reiko.signaltower.radio.IntruderAlerts.missedLines(server, devId);
            check(lines.size() == 1 + com.reiko.signaltower.radio.IntruderAlerts.SHOWN_AT_LOGIN + 1
                    && firstArg(lines.get(0)).equals("20") && keyOf(lines.get(lines.size() - 1)).endsWith("intruder_missed_more"),
                    "20 in all, the last 5 shown, then how many more: " + lines);
        } finally {
            claims.remove(2903);
            com.reiko.signaltower.radio.IntruderAlerts.missedLines(server, devId);
            alerts.resetQuiet();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- v0.37.0: reach preview ring

    /**
     * The reach preview: the server answers a player holding a tuner (either hand) with the reach
     * of the transmitter, repeater, converter (on the frequency it sends on) or keypad they look
     * at; anything else, no tuner, or too far away gets "nothing to show". The answer survives
     * the network.
     */
    @GameTest(template = PLATFORM)
    public static void reachPreviewAnswers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos repeater = new BlockPos(4, 1, 1);
        BlockPos converter = new BlockPos(6, 1, 1);
        BlockPos keypad = new BlockPos(8, 1, 1);
        BlockPos receiver = new BlockPos(10, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 3101);
        place(helper, repeater, ModRegistry.RADIO_REPEATER.get(), 3102);
        place(helper, converter, ModRegistry.RADIO_CONVERTER.get(), 3103);
        ((com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity) helper.getBlockEntity(converter)).setFrequencies(3103, 3104);
        helper.setBlock(keypad.south(), Blocks.STONE);
        helper.setBlock(keypad, ModRegistry.CODE_KEYPAD.get().defaultBlockState());
        setFrequency(helper, keypad, 3105);
        place(helper, receiver, ModRegistry.RADIO_RECEIVER.get(), 3106);
        Player player = namedPlayer(helper, new BlockPos(1, 1, 0), UUID.randomUUID(), "Ringo", false,
                new java.util.ArrayList<>(), new java.util.ArrayList<>());
        var answer = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(tx));
        check(answer.reach() == -1, "No tuner in hand: nothing to show");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
        int reach = RadioHelper.reachAt(level, helper.absolutePos(tx));
        answer = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(tx));
        check(answer.reach() == reach && answer.kind() == com.reiko.signaltower.network.ReachPreviewPayload.TRANSMITTER
                && answer.frequency() == 3101 && answer.pos().equals(helper.absolutePos(tx)), "Transmitter: " + answer);
        answer = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(repeater));
        check(answer.reach() == reach && answer.kind() == com.reiko.signaltower.network.ReachPreviewPayload.REPEATER
                && answer.frequency() == 3102, "Repeater: " + answer);
        answer = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(converter));
        check(answer.kind() == com.reiko.signaltower.network.ReachPreviewPayload.CONVERTER && answer.frequency() == 3104,
                "Converter, on the frequency it sends on: " + answer);
        answer = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(keypad));
        check(answer.reach() == reach && answer.kind() == com.reiko.signaltower.network.ReachPreviewPayload.KEYPAD
                && answer.frequency() == 3105, "Keypad: " + answer);
        check(com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(receiver)).reach() == -1,
                "A receiver has no reach");
        check(com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(keypad.south())).reach() == -1,
                "Nor a stone block");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
        check(com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(tx)).reach() == reach,
                "A tuner in the off-hand works too");
        Vec3 far = helper.absoluteVec(new Vec3(1, 1, 0)).add(com.reiko.signaltower.network.ReachPreviewRequestPayload.MAX_DISTANCE + 10, 0, 0);
        player.moveTo(far.x, far.y, far.z);
        check(com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(player, helper.absolutePos(tx)).reach() == -1,
                "Too far away: nothing to show");

        var original = new com.reiko.signaltower.network.ReachPreviewPayload(helper.absolutePos(tx), reach, 2, 3104);
        var buffer = Unpooled.buffer();
        com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC.encode(buffer, original);
        check(com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC.decode(buffer).equals(original), "Survives the network");
        var none = new com.reiko.signaltower.network.ReachPreviewPayload(helper.absolutePos(tx), -1, 0, 0);
        buffer = Unpooled.buffer();
        com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC.encode(buffer, none);
        check(com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC.decode(buffer).reach() == -1, "So does \"nothing to show\"");
        helper.succeed();
    }

    /**
     * The ring's shape: "inside" is the network's own rule, the edge distance counts down to 0 at
     * the edge and goes negative outside, and the dots sit 1.5 blocks apart, half a block outside
     * the reach.
     */
    @GameTest(template = PLATFORM)
    public static void reachRingShape(GameTestHelper helper) {
        int[][] offsets = {{0, 0}, {88, 0}, {89, 0}, {0, -88}, {62, 62}, {63, 62}, {-40, 77}, {-50, 80}};
        for (int reach : new int[]{0, 32, 56, 88, 2080}) {
            SignalNetwork.Coverage area = new SignalNetwork.Coverage(BlockPos.ZERO, reach, false);
            for (int[] offset : offsets) {
                check(com.reiko.signaltower.client.ReachRingShape.inside(offset[0], offset[1], reach)
                                == area.reaches(new BlockPos(offset[0], 70, offset[1])),
                        "Same rule as the network for " + offset[0] + "," + offset[1] + " at reach " + reach);
            }
        }
        check(com.reiko.signaltower.client.ReachRingShape.edgeDistance(0, 0, 88) == 88, "At the sender: 88 to the edge");
        check(com.reiko.signaltower.client.ReachRingShape.edgeDistance(88, 0, 88) == 0, "At the last block: at the edge");
        check(com.reiko.signaltower.client.ReachRingShape.edgeDistance(89, 0, 88) == -1, "One block out");
        check(com.reiko.signaltower.client.ReachRingShape.edgeDistance(100, 0, 88) == -12, "Twelve out");
        check(com.reiko.signaltower.client.ReachRingShape.edgeDistance(62, 62, 88) >= 0
                && com.reiko.signaltower.client.ReachRingShape.edgeDistance(63, 62, 88) < 0, "Diagonals follow the circle");
        int count = com.reiko.signaltower.client.ReachRingShape.dotCount(88);
        check(count == (int) Math.ceil(2 * Math.PI * 88.5 / com.reiko.signaltower.client.ReachRingShape.SPACING), "Dots 1.5 blocks apart: " + count);
        for (int i = 0; i < count; i += 37) {
            double[] dot = com.reiko.signaltower.client.ReachRingShape.dot(88, i, count);
            check(Math.abs(Math.hypot(dot[0], dot[1]) - 88.5) < 1e-6, "Each dot half a block outside the reach");
        }
        check(com.reiko.signaltower.client.ReachRingShape.dotCount(0) >= 16, "Even a tiny ring has some dots");
        check(com.reiko.signaltower.client.ReachRingShape.dotCount(1_000_000) == com.reiko.signaltower.client.ReachRingShape.MAX_DOTS,
                "A huge one is capped");
        helper.succeed();
    }

    // ---------------------------------------------------------------- v0.37.0: Code Keypad

    /** A keypad on the north face of a stone block at {@code at.south()}, tuned to {@code frequency}, owned by Dev. */
    private static com.reiko.signaltower.blockentity.CodeKeypadBlockEntity keypadAt(GameTestHelper helper, BlockPos at, int frequency,
                                                                                    UUID owner, String ownerName) {
        helper.setBlock(at.south(), Blocks.STONE);
        helper.setBlock(at, ModRegistry.CODE_KEYPAD.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.CodeKeypadBlock.FACE, AttachFace.WALL)
                .setValue(com.reiko.signaltower.block.CodeKeypadBlock.FACING, Direction.NORTH));
        com.reiko.signaltower.blockentity.CodeKeypadBlockEntity keypad = helper.getBlockEntity(at);
        keypad.setFrequency(frequency);
        keypad.setTunedBy(owner);
        keypad.setOwner(owner, ownerName);
        return keypad;
    }

    /**
     * The Code Keypad: only its owner sets a code (4 to 8 digits) and the pulse; three wrong codes
     * lock a player out for 30 seconds and alert the owner (with where the keypad is); the right
     * code sends a pulse from the keypad (heard there, recorded with who typed it) and powers the
     * block it's on, until the pulse ends. The code is saved, but never put on the item nor sent to
     * a client.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void codeKeypad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var alerts = com.reiko.signaltower.radio.IntruderAlerts.get(server);
        UUID devId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();
        BlockPos at = new BlockPos(2, 1, 1);
        BlockPos absolute = helper.absolutePos(at);
        List<Component> alexBar = new java.util.ArrayList<>();
        Player dev = namedPlayer(helper, new BlockPos(1, 1, 0), devId, "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player alex = namedPlayer(helper, new BlockPos(3, 1, 0), alexId, "Alex", false, alexBar, new java.util.ArrayList<>());
        Player op = namedPlayer(helper, new BlockPos(3, 1, 0), UUID.randomUUID(), "Opal", true, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        var keypad = keypadAt(helper, at, 3201, devId, "Dev");
        alerts.resetQuiet();
        try {
            var enter = (java.util.function.BiFunction<Player, String, com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Result>) (player, digits) ->
                    com.reiko.signaltower.network.KeypadEnterPayload.apply(player, new com.reiko.signaltower.network.KeypadEnterPayload(absolute, digits));
            var result = enter.apply(alex, "1234");
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.NO_CODE, "No code yet: " + result);

            var configure = (java.util.function.BiFunction<Player, String, com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Result>) (player, code) ->
                    com.reiko.signaltower.network.KeypadConfigurePayload.apply(player, new com.reiko.signaltower.network.KeypadConfigurePayload(absolute, code, 40));
            check(configure.apply(alex, "4242").outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.REFUSED,
                    "Only the owner sets the code");
            for (String bad : new String[]{"12", "123456789", "12a4"}) {
                check(!com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.validCode(bad), "Not a code: " + bad);
            }
            check(configure.apply(dev, "123").outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.REFUSED, "Too short");
            result = configure.apply(dev, "4242");
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.SAVED
                    && keyOf(result.message()).endsWith("keypad_code_saved") && firstArg(result.message()).equals("4")
                    && !result.message().getString().contains("4242"), "Saved, without repeating the code: " + result.message().getString());
            check(keypad.hasCode() && keypad.getPulseTicks() == 40, "Code and a 2-second pulse");

            // A remote (or anything but the tuner) in hand still opens the keypad; only its owner re-tunes it.
            BlockState keypadState = helper.getBlockState(at);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
            check(keypadState.useItemOn(new ItemStack(ModRegistry.REMOTE_CONTROL.get()), level, alex, InteractionHand.MAIN_HAND, hit)
                    == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "A remote in hand opens the keypad");
            check(keypadState.useItemOn(new ItemStack(ModRegistry.FREQUENCY_TUNER.get()), level, alex, InteractionHand.MAIN_HAND, hit)
                    == net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "The tuner tunes it instead");
            ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
            FrequencyTunerItem.setFrequency(tuner, 3299);
            alex.setItemInHand(InteractionHand.MAIN_HAND, tuner);
            tuner.useOn(new UseOnContext(alex, InteractionHand.MAIN_HAND, hit));
            check(keypad.getFrequency() == 3201 && lastKey(alexBar).endsWith("keypad_not_yours_tune"),
                    "Alex can't re-tune Dev's keypad: " + lastKey(alexBar));
            alex.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(keypad.mayConfigure(op) && !keypad.mayConfigure(alex), "Operators may change it too");

            var open = com.reiko.signaltower.network.OpenKeypadScreenPayload.of(keypad, alex, level.getGameTime());
            check(open.hasCode() && !open.mayConfigure() && open.ownerName().equals("Dev") && open.pulseTicks() == 40
                    && open.lockedSeconds() == 0, "Alex's screen: " + open);
            var buffer = Unpooled.buffer();
            com.reiko.signaltower.network.OpenKeypadScreenPayload.STREAM_CODEC.encode(buffer, open);
            check(com.reiko.signaltower.network.OpenKeypadScreenPayload.STREAM_CODEC.decode(buffer).equals(open), "Survives the network");

            check(enter.apply(alex, "1111").outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.WRONG, "Wrong once");
            result = enter.apply(alex, "2222");
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.WRONG
                    && firstArg(result.message()).equals("2"), "Twice: " + result.message().getString());
            check(alerts.missedFor(devId).isEmpty(), "No alert yet");
            result = enter.apply(alex, "3333");
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.LOCKED && result.lockedSeconds() == 30,
                    "Three times: locked for 30 seconds: " + result);
            var kept = alerts.missedFor(devId);
            check(kept.size() == 1 && kept.get(0).kind() == com.reiko.signaltower.radio.IntruderAlerts.Kind.KEYPAD
                    && kept.get(0).intruder().equals("Alex") && absolute.equals(kept.get(0).at()) && kept.get(0).frequency() == 3201
                    && kept.get(0).block().equals(ModRegistry.CODE_KEYPAD.get().getDescriptionId()), "Dev is told, with where: " + kept);
            var line = com.reiko.signaltower.radio.IntruderAlerts.line(level, kept.get(0), false);
            check(line.getSiblings().stream().anyMatch(c -> keyOf(c).endsWith("intruder_keypad"))
                    && line.getString().contains(absolute.getX() + ", " + absolute.getY() + ", " + absolute.getZ()), "The alert line: " + line.getString());
            var loaded = com.reiko.signaltower.radio.IntruderAlerts.load(alerts.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(loaded.missedFor(devId).equals(kept), "Kept with where it is");
            check(enter.apply(alex, "4242").outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.LOCKED,
                    "Even the right code while locked");
            check(com.reiko.signaltower.network.OpenKeypadScreenPayload.of(keypad, alex, level.getGameTime()).lockedSeconds() == 30,
                    "Alex's screen says how long");
            check(enter.apply(dev, "4242").outcome() != com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.LOCKED,
                    "Only Alex is locked out");

            // Dev's own wrong codes don't alert Dev.
            for (int i = 0; i < 3; i++) {
                keypad.enter(level, dev, "0000", level.getGameTime() + 2000);
            }
            check(alerts.missedFor(devId).size() == 1, "No alert for the owner's own mistakes");

            long later = level.getGameTime() + com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.LOCK_TICKS + 1;
            result = keypad.enter(level, alex, "4242", later);
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.ACCEPTED
                    && keyOf(result.message()).endsWith("remote_result"),
                    "After 30 seconds the right code works: " + result.message().getString());
            check(SignalNetwork.get(level).listen(level, 3201, helper.absolutePos(new BlockPos(20, 1, 1)), null).power() == 15,
                    "The pulse is heard nearby");
            SignalNetwork.TurnOn last = SignalNetwork.get(level).history(3201).get(SignalNetwork.get(level).history(3201).size() - 1);
            check(last.cause().kind() == SignalNetwork.CauseKind.KEYPAD && absolute.equals(last.cause().pos())
                    && alexId.equals(last.cause().who()) && last.cause().name().equals("Alex"), "Recorded as the keypad, typed by Alex: " + last.cause());
            check(helper.getBlockState(at).getValue(com.reiko.signaltower.block.CodeKeypadBlock.POWERED), "Lit");
            check(level.getDirectSignalTo(helper.absolutePos(at.south())) == 15, "Strongly powers the block it's on");

            CompoundTag saved = keypad.saveWithoutMetadata(level.registryAccess());
            check(saved.getString("code").equals("4242") && saved.getUUID("owner").equals(devId) && saved.getInt("pulse_ticks") == 40,
                    "Saved with the world: " + saved);
            var components = keypad.collectComponents();
            check(components.size() == 1 && components.has(ModRegistry.FREQUENCY.get()), "Only the frequency goes on the item: " + components);
        } finally {
            com.reiko.signaltower.radio.IntruderAlerts.missedLines(server, devId);
            alerts.resetQuiet();
        }
        // The pulse is 2 seconds: then it goes dark again.
        helper.runAfterDelay(50, () -> {
            check(!helper.getBlockState(at).getValue(com.reiko.signaltower.block.CodeKeypadBlock.POWERED), "Dark again after the pulse");
            helper.succeed();
        });
    }

    /**
     * A keypad on someone else's private frequency: placing it says it's silent (and alerts that
     * frequency's owner), and the right code then sends nothing. A member's keypad shows in the
     * frequency log (with where it is and who typed the code), and Jade shows the owner, whether a
     * code is set (never the code) and its reach.
     */
    @GameTest(template = PLATFORM)
    public static void codeKeypadPrivate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        var alerts = com.reiko.signaltower.radio.IntruderAlerts.get(server);
        UUID zedId = UUID.randomUUID();
        UUID devId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();
        List<Component> devBar = new java.util.ArrayList<>();
        Player dev = namedPlayer(helper, new BlockPos(1, 1, 0), devId, "Dev", false, devBar, new java.util.ArrayList<>());
        Player alex = namedPlayer(helper, new BlockPos(5, 1, 0), alexId, "Alex", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        BlockPos silent = new BlockPos(2, 1, 1);
        BlockPos member = new BlockPos(6, 1, 1);
        alerts.resetQuiet();
        try {
            claims.put(3202, zedId, "Zed", java.util.Map.of());
            claims.put(3203, devId, "Dev", java.util.Map.of());
            helper.setBlock(silent.south(), Blocks.STONE);
            BlockState state = ModRegistry.CODE_KEYPAD.get().defaultBlockState();
            helper.setBlock(silent, state);
            setFrequency(helper, silent, 3202);
            state.getBlock().setPlacedBy(level, helper.absolutePos(silent), helper.getBlockState(silent), dev,
                    new ItemStack(ModRegistry.CODE_KEYPAD_ITEM.get()));
            com.reiko.signaltower.blockentity.CodeKeypadBlockEntity keypad = helper.getBlockEntity(silent);
            check(devId.equals(keypad.getOwner()) && devId.equals(keypad.getTunedBy()) && keypad.getOwnerName().equals("Dev"),
                    "Whoever places it owns it");
            check(lastKey(devBar).endsWith("private_silenced"), "Dev is told it's silent: " + lastKey(devBar));
            var kept = alerts.missedFor(zedId);
            check(kept.size() == 1 && kept.get(0).kind() == com.reiko.signaltower.radio.IntruderAlerts.Kind.SILENT, "Zed is told: " + kept);
            keypad.configure(dev, "1357", 20);
            var result = keypad.enter(level, dev, "1357", level.getGameTime());
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.SILENT, "Right code, but silent: " + result);
            check(SignalNetwork.get(level).listen(level, 3202, helper.absolutePos(silent), null).power() == 0, "Nothing sent");
            CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(silent));
            check(info != null && info.getBoolean("silenced") && info.getString("private_to").equals("Zed"), "Jade says silent: " + info);

            var memberKeypad = keypadAt(helper, member, 3203, devId, "Dev");
            info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(member));
            check(info != null && info.getString("keypad_owner").equals("Dev") && !info.getBoolean("has_code")
                    && info.getInt("reach") == RadioHelper.reachAt(level, helper.absolutePos(member)) && !info.getBoolean("silenced"),
                    "Jade data: " + info);
            List<Component> lines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(member), info);
            check(lineKeys(lines).equals(List.of("frequency", "keypad_owner", "keypad_no_code", "keypad_waiting", "reach", "private")),
                    "Jade lines: " + lineKeys(lines));
            memberKeypad.configure(dev, "24680", 20);
            info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(member));
            check(info.getBoolean("has_code") && !info.toString().contains("24680"), "Never the code itself: " + info);
            result = memberKeypad.enter(level, alex, "24680", level.getGameTime());
            check(result.outcome() == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.ACCEPTED,
                    "Alex knows the code: Dev's keypad sends on Dev's frequency: " + result.message().getString());
            var log = com.reiko.signaltower.radio.FrequencyLog.log(server, 3203);
            check(!log.isEmpty() && log.get(0).kind() == SignalNetwork.CauseKind.KEYPAD
                    && log.get(0).pos().equals(java.util.Optional.of(helper.absolutePos(member)))
                    && log.get(0).who().equals("Alex") && log.get(0).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER,
                    "In the log: the keypad, typed by Alex: " + log);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            var logResult = new com.reiko.signaltower.network.LogResultPayload(3203, true, log, level.getGameTime());
            com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC.encode(buffer, logResult);
            var decoded = com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC.decode(buffer);
            check(decoded.entries().get(0).kind() == SignalNetwork.CauseKind.KEYPAD, "The log's kind survives the network");
        } finally {
            claims.remove(3202);
            claims.remove(3203);
            com.reiko.signaltower.radio.IntruderAlerts.missedLines(server, zedId);
            alerts.resetQuiet();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- v0.38.0: Compact Radio Transmitter

    /**
     * The Compact Radio Transmitter: stuck to a wall, it broadcasts the signal around it over its
     * short fixed reach (heard inside, not outside), whatever its height; it isn't a relay tower;
     * the ring, Jade and the audit know it; its flag survives saving; and it pops off with its
     * frequency when its wall goes.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void compactTransmitter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID devId = UUID.randomUUID();
        BlockPos at = new BlockPos(2, 1, 1);
        BlockPos big = new BlockPos(8, 1, 1);
        helper.setBlock(at.south(), Blocks.STONE);
        helper.setBlock(at, ModRegistry.COMPACT_RADIO_TRANSMITTER.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.FACE, AttachFace.WALL)
                .setValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.FACING, Direction.NORTH));
        com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity tx = helper.getBlockEntity(at);
        tx.setTunedBy(devId);
        tx.setFrequency(3301);
        place(helper, big, ModRegistry.RADIO_TRANSMITTER.get(), 3302);
        try {
            claims.put(3301, devId, "Dev", java.util.Map.of());
            helper.setBlock(at.west(), Blocks.REDSTONE_BLOCK);
            tx.refresh();
            BlockPos absolute = helper.absolutePos(at);
            SignalNetwork network = SignalNetwork.get(level);
            int reach = com.reiko.signaltower.Config.COMPACT_TRANSMITTER_REACH.get();
            check(reach == 16, "16 blocks by default: " + reach);
            check(helper.getBlockState(at).getValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.POWERED), "Lit while sending");
            check(network.listen(level, 3301, absolute.offset(reach, 0, 0), null).power() == 15, "Heard at the edge of its reach");
            check(network.listen(level, 3301, absolute.offset(reach + 1, 0, 0), null).power() == 0, "Not a block past it");
            check(network.listen(level, 3301, absolute.offset(0, 200, reach), null).power() == 15, "At any height");
            check(RadioHelper.reachAt(level, absolute) > reach, "A normal transmitter there would reach farther");

            List<SignalNetwork.Coverage> towers = network.remoteCoverage(level, absolute.offset(1, 0, 0), 32, true);
            check(towers.stream().noneMatch(c -> c.origin().equals(absolute)), "Not a relay tower");
            check(towers.stream().anyMatch(c -> c.origin().equals(helper.absolutePos(big))), "A normal transmitter next to it is");

            Player dev = namedPlayer(helper, new BlockPos(1, 1, 0), devId, "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
            dev.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
            var answer = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(dev, absolute);
            check(answer.reach() == reach && answer.kind() == com.reiko.signaltower.network.ReachPreviewPayload.COMPACT_TRANSMITTER
                    && answer.frequency() == 3301, "The ring: " + answer);
            CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absolute);
            check(info != null && info.getInt("reach") == reach && info.getInt("power") == 15, "Jade data: " + info);
            check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(at), info))
                    .equals(List.of("frequency", "sending", "reach", "private")), "Jade lines");
            var audit = com.reiko.signaltower.radio.FrequencyAudit.audit(server, 3301, dev, 512);
            check(audit.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.COMPACT_TRANSMITTER
                    && e.pos().equals(java.util.Optional.of(absolute))), "In the audit as a compact transmitter: " + audit);

            SignalNetwork fresh = new SignalNetwork().attachTo(level);
            BlockPos far = new BlockPos(8_300_000, 70, 0);
            fresh.updateTransmitter(far, 3303, 15, reach, null, true);
            SignalNetwork loaded = SignalNetwork.load(fresh.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).attachTo(level);
            check(loaded.remoteCoverage(level, far.offset(1, 0, 0), 32, true).size() == 1, "Still not a tower after saving");
            check(loaded.listen(level, 3303, far.offset(reach, 0, 0), null).power() == 15, "And still sending");

            helper.setBlock(at.south(), Blocks.AIR);
        } finally {
            claims.remove(3301);
        }
        helper.succeedWhen(() -> {
            check(helper.getBlockState(at).isAir(), "Pops off without its wall");
            check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                    .anyMatch(stack -> stack.is(ModRegistry.COMPACT_RADIO_TRANSMITTER_ITEM.get())
                            && Integer.valueOf(3301).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Dropped with its frequency");
        });
    }

    // ---------------------------------------------------------------- v0.38.0: keypad guest codes

    /**
     * Guest codes: only the owner adds them (a name, 4 to 8 digits, not a code already on the keypad
     * nor a name already used, at most 8); each works its number of uses or until its time runs
     * out, then says it has run out without counting as a wrong code; one with no limits keeps
     * working; removing one makes it a wrong code. The list packet carries them (never the main
     * code), they're saved, and the log names the guest code used.
     */
    @GameTest(template = PLATFORM)
    public static void keypadGuestCodes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID devId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();
        BlockPos at = new BlockPos(2, 1, 1);
        BlockPos absolute = helper.absolutePos(at);
        Player dev = namedPlayer(helper, new BlockPos(1, 1, 0), devId, "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player alex = namedPlayer(helper, new BlockPos(3, 1, 0), alexId, "Alex", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        var keypad = keypadAt(helper, at, 3401, devId, "Dev");
        try {
            claims.put(3401, devId, "Dev", java.util.Map.of());
            keypad.configure(dev, "4242", 20);
            long now = 1_000_000_000L;
            var refused = com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.REFUSED;
            var saved = com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.SAVED;
            check(keypad.addGuestCode(alex, "Me", "1111", 1, 0, now).outcome() == refused, "Only the owner adds them");
            check(keypad.addGuestCode(dev, "  ", "1111", 1, 0, now).outcome() == refused, "A name is needed");
            check(keypad.addGuestCode(dev, "Bad", "12", 1, 0, now).outcome() == refused, "4 to 8 digits");
            check(keypad.addGuestCode(dev, "Main", "4242", 1, 0, now).outcome() == refused, "Not the keypad's own code");
            var added = keypad.addGuestCode(dev, "Plumber", "1357", 3, 86400, now);
            check(added.outcome() == saved && keyOf(added.message()).endsWith("keypad_guest_added")
                    && !added.message().getString().contains("1357"), "Added: " + added.message().getString());
            check(keypad.addGuestCode(dev, "Other", "1357", 1, 0, now).outcome() == refused, "Not a code already used");
            check(keypad.addGuestCode(dev, "plumber", "2222", 1, 0, now).outcome() == refused, "Nor a name already used");
            check(keypad.configure(dev, "1357", 20).outcome() == refused, "The main code can't become a guest code");
            check(keypad.addGuestCode(dev, "Hourly", "2468", -1, 3600, now).outcome() == saved, "Unlimited uses for an hour");
            check(keypad.addGuestCode(dev, "Family", "9999", -1, 0, now).outcome() == saved, "No limits at all");

            var list = com.reiko.signaltower.network.KeypadGuestCodesPayload.apply(dev,
                    com.reiko.signaltower.network.KeypadGuestCodesPayload.list(absolute), now);
            check(list != null && list.codes().size() == 3 && list.codes().get(0).label().equals("Plumber")
                    && list.codes().get(0).usesLeft() == 3 && list.codes().get(0).secondsLeft() == 86400
                    && list.codes().get(2).secondsLeft() == -1 && list.codes().stream().noneMatch(c -> c.code().equals("4242")),
                    "The owner's list (never the main code): " + list);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            com.reiko.signaltower.network.KeypadGuestListPayload.STREAM_CODEC.encode(buffer, list);
            check(com.reiko.signaltower.network.KeypadGuestListPayload.STREAM_CODEC.decode(buffer).codes().equals(list.codes()),
                    "The list survives the network");
            var alexList = com.reiko.signaltower.network.KeypadGuestCodesPayload.apply(alex,
                    com.reiko.signaltower.network.KeypadGuestCodesPayload.list(absolute), now);
            check(alexList != null && alexList.codes().isEmpty(), "Alex gets nothing");

            var accepted = com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.ACCEPTED;
            var runOut = com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.RUN_OUT;
            var result = keypad.enter(level, alex, "1357", level.getGameTime(), now + 1000);
            Component sent = (Component) ((TranslatableContents) result.message().getContents()).getArgs()[0];
            check(result.outcome() == accepted && keyOf(sent).endsWith("keypad_guest_accepted") && firstArg(sent).equals("Plumber"),
                    "Plumber's code works: " + result.message().getString());
            var guest = keypad.guestCodes().get(0);
            check(guest.usesLeft() == 2 && guest.used() == 1 && guest.lastUser().equals("Alex"), "One use counted: " + guest);
            SignalNetwork.TurnOn last = SignalNetwork.get(level).history(3401).get(SignalNetwork.get(level).history(3401).size() - 1);
            check(last.cause().kind() == SignalNetwork.CauseKind.KEYPAD && last.cause().detail().equals("Plumber"), "Recorded with its name");
            var log = com.reiko.signaltower.radio.FrequencyLog.log(server, 3401);
            check(log.get(0).detail().equals("Plumber") && log.get(0).who().equals("Alex"), "In the log: " + log.get(0));
            var logBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC.encode(logBuffer,
                    new com.reiko.signaltower.network.LogResultPayload(3401, true, log, level.getGameTime()));
            check(com.reiko.signaltower.network.LogResultPayload.STREAM_CODEC.decode(logBuffer).entries().get(0).detail().equals("Plumber"),
                    "The name survives the network");
            CompoundTag savedTag = SignalNetwork.get(level).save(new CompoundTag(), level.registryAccess());
            var reloaded = SignalNetwork.load(savedTag, level.registryAccess());
            var reloadedHistory = reloaded.history(3401);
            check(reloadedHistory.get(reloadedHistory.size() - 1).cause().detail().equals("Plumber"), "And saving");

            keypad.enter(level, alex, "1357", level.getGameTime(), now + 2000);
            keypad.enter(level, alex, "1357", level.getGameTime(), now + 3000);
            check(keypad.guestCodes().get(0).usedUp(), "Three uses, then used up");
            for (int i = 0; i < 4; i++) {
                result = keypad.enter(level, alex, "1357", level.getGameTime(), now + 4000);
                check(result.outcome() == runOut && keyOf(result.message()).endsWith("keypad_guest_used_up"), "Used up: " + result.message().getString());
            }
            check(keypad.lockedSeconds(alexId, level.getGameTime()) == 0, "Not counted as wrong codes");

            check(keypad.enter(level, alex, "2468", level.getGameTime(), now + 3_599_000).outcome() == accepted, "The hourly one within the hour");
            result = keypad.enter(level, alex, "2468", level.getGameTime(), now + 3_600_000);
            check(result.outcome() == runOut && keyOf(result.message()).endsWith("keypad_guest_expired"), "Expired after it");
            for (int i = 0; i < 12; i++) {
                check(keypad.enter(level, alex, "9999", level.getGameTime(), now + 1_000_000_000L).outcome() == accepted, "Family's works every time");
            }
            check(keypad.guestCodes().get(2).used() == 12 && keypad.guestCodes().get(2).usesLeft() == -1, "Counted, still no limit");

            CompoundTag keypadTag = keypad.saveWithoutMetadata(level.registryAccess());
            var copy = new com.reiko.signaltower.blockentity.CodeKeypadBlockEntity(absolute, helper.getBlockState(at));
            copy.loadWithComponents(keypadTag, level.registryAccess());
            check(copy.guestCodes().equals(keypad.guestCodes()), "Saved with the keypad");

            for (int i = 0; i < 5; i++) {
                check(keypad.addGuestCode(dev, "Guest " + i, "50" + i + "0", 1, 0, now).outcome() == saved, "Room for 8");
            }
            check(keypad.addGuestCode(dev, "Ninth", "8888", 1, 0, now).outcome() == refused, "Not a ninth");
            var removed = com.reiko.signaltower.network.KeypadGuestCodesPayload.apply(dev,
                    new com.reiko.signaltower.network.KeypadGuestCodesPayload(absolute, com.reiko.signaltower.network.KeypadGuestCodesPayload.REMOVE,
                            "plumber", "", 0, 0), now);
            check(removed != null && removed.codes().size() == 7 && keyOf(removed.notice()).endsWith("keypad_guest_removed"),
                    "Removed (name ignoring case): " + removed);
            check(keypad.enter(level, alex, "1357", level.getGameTime(), now).outcome()
                    == com.reiko.signaltower.blockentity.CodeKeypadBlockEntity.Outcome.WRONG, "Then it's just a wrong code");
        } finally {
            claims.remove(3401);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- v0.39.0: inverted receiver

    /**
     * The inverted receiver outputs 15 while its frequency is silent (a lamp next to it is lit) and
     * 0 while it's on at any strength, and 15 again once it's silent; on someone else's encrypted
     * frequency it hears nothing, so it stays on. Jade, the readout and the audit know it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void invertedReceiver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID devId = UUID.randomUUID();
        UUID rexId = UUID.randomUUID();
        BlockPos rx = new BlockPos(2, 1, 1);
        BlockPos lamp = new BlockPos(1, 1, 1);
        BlockPos tx = new BlockPos(5, 1, 1);
        BlockPos input = new BlockPos(5, 1, 0);
        BlockPos deaf = new BlockPos(10, 1, 1);
        BlockPos tx2 = new BlockPos(13, 1, 1);
        List<Component> devBar = new java.util.ArrayList<>();
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), devId, "Dev", false, devBar, new java.util.ArrayList<>());
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        place(helper, rx, ModRegistry.RADIO_INVERTED_RECEIVER.get(), 3501);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 3501);
        claims.put(3502, devId, "Dev", java.util.Map.of(), List.of(), true);
        place(helper, deaf, ModRegistry.RADIO_INVERTED_RECEIVER.get(), 3502);
        ((RadioBlockEntity) helper.getBlockEntity(deaf)).setTunedBy(rexId);
        place(helper, tx2, ModRegistry.RADIO_TRANSMITTER.get(), 3502);
        ((RadioBlockEntity) helper.getBlockEntity(tx2)).setTunedBy(devId);
        helper.setBlock(tx2.north(), Blocks.REDSTONE_BLOCK);
        BlockPos absolute = helper.absolutePos(rx);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "On while 3501 is silent");
                    check(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "So the lamp next to it is lit");
                    check(SignalNetwork.get(level).listen(level, 3502, helper.absolutePos(deaf), null).power() == 15, "3502 is on there");
                    check(helper.getBlockState(deaf).getValue(RadioReceiverBlock.POWER) == 15,
                            "Someone else's encrypted frequency: it hears nothing, so it stays on");
                })
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absolute);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info))
                            .equals(List.of("frequency", "inverted_on", "hearing_nothing", "towers")), "Jade while silent");
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), null))
                            .equals(List.of("inverted_on")), "Jade without server data");
                    helper.getBlockState(rx).useWithoutItem(level, dev, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false));
                    check(lastKey(devBar).endsWith("inverted_status"), "The readout: " + lastKey(devBar));
                    helper.setBlock(input, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "Off while 3501 is on");
                    check(!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "So the lamp goes out");
                })
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absolute);
                    check(info != null && info.getInt("heard") == 15 && info.getInt("power") == 0, "Jade data: " + info);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info))
                            .equals(List.of("frequency", "inverted_off", "hearing", "towers")), "Jade while on");
                    claims.put(3501, devId, "Dev", java.util.Map.of());
                    var audit = com.reiko.signaltower.radio.FrequencyAudit.audit(server, 3501, dev, 512);
                    check(audit.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.INVERTED_RECEIVER
                            && e.pos().equals(java.util.Optional.of(absolute))), "In the audit as an inverted receiver: " + audit);
                    claims.remove(3501);
                    helper.setBlock(input, Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "On again once 3501 is silent");
                    check(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "And the lamp with it");
                })
                .thenExecute(() -> claims.remove(3502))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.39.0: motion sensor

    /**
     * The motion sensor: whoever places it owns it (and tunes it); it sees the nearest player within
     * its range (6 by default) with a clear line from its face, never a spectator or someone behind
     * a block; strangers-only mode ignores its owner and the private frequency's members. While it
     * sees someone it sends full strength over the compact reach (not a relay tower), the log names
     * who it saw, and it keeps sending for 2 seconds after; the redstone around it does nothing.
     * Only its owner changes its settings (a range it doesn't offer becomes the default) or
     * re-tunes it; the screen packet, Jade (settings for the owner only), the audit and the reach
     * ring know it; it keeps its settings when saved, and its frequency when it pops off.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void motionSensor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID devId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();
        UUID rexId = UUID.randomUUID();
        List<Component> devBar = new java.util.ArrayList<>();
        List<Component> alexBar = new java.util.ArrayList<>();
        List<Component> chat = new java.util.ArrayList<>();
        BlockPos at = new BlockPos(10, 1, 1);
        helper.setBlock(at.south(), Blocks.STONE);
        helper.setBlock(at, ModRegistry.RADIO_MOTION_SENSOR.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.FACE, AttachFace.WALL)
                .setValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.FACING, Direction.NORTH));
        BlockPos absolute = helper.absolutePos(at);
        Player dev = namedPlayer(helper, new BlockPos(10, 1, 0), devId, "Dev", false, devBar, chat);
        Player alex = namedPlayer(helper, new BlockPos(13, 1, 0), alexId, "Alex", false, alexBar, chat);
        Player rex = namedPlayer(helper, new BlockPos(12, 1, 0), rexId, "Rex", false, new java.util.ArrayList<>(), chat);
        Player far = namedPlayer(helper, new BlockPos(0, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), chat);
        Player hidden = namedPlayer(helper, new BlockPos(15, 1, 1), UUID.randomUUID(), "Hidden", false, new java.util.ArrayList<>(), chat);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(14, y, 1), Blocks.STONE); // a pillar between the sensor and Hidden
        }
        Player ghost = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "Ghost")) {
            @Override
            public boolean isSpectator() {
                return true;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Vec3 ghostFeet = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(11, 1, 0)));
        ghost.moveTo(ghostFeet.x, ghostFeet.y, ghostFeet.z);

        ModRegistry.RADIO_MOTION_SENSOR.get().setPlacedBy(level, absolute, helper.getBlockState(at), dev,
                new ItemStack(ModRegistry.RADIO_MOTION_SENSOR_ITEM.get()));
        com.reiko.signaltower.blockentity.MotionSensorBlockEntity sensor = helper.getBlockEntity(at);
        sensor.setFrequency(3601);
        int reach = com.reiko.signaltower.Config.COMPACT_TRANSMITTER_REACH.get();
        SignalNetwork network = SignalNetwork.get(level);
        var everyone = com.reiko.signaltower.blockentity.MotionSensorBlockEntity.Mode.EVERYONE;
        var strangers = com.reiko.signaltower.blockentity.MotionSensorBlockEntity.Mode.STRANGERS;
        long[] since = new long[1];

        helper.startSequence()
                .thenExecute(() -> {
                    check(devId.equals(sensor.getOwner()) && "Dev".equals(sensor.getOwnerName()) && devId.equals(sensor.getTunedBy()),
                            "Placed by Dev: theirs and tuned by them");
                    check(sensor.getRange() == 6 && sensor.getMode() == everyone, "6 blocks, everyone, by default");
                    check(sensor.nearestSeen(level, List.of(alex, rex, dev, far, hidden, ghost)) == dev, "The nearest: Dev, its owner");
                    check(sensor.nearestSeen(level, List.of(alex, far, hidden, ghost)) == alex, "Then Alex");
                    check(sensor.nearestSeen(level, List.of(far, hidden, ghost)) == null,
                            "Not someone too far, behind a block, or a spectator");

                    check(keyOf(sensor.configure(dev, 12, everyone)).endsWith("sensor_set") && sensor.getRange() == 12, "Dev sets 12 blocks");
                    check(sensor.nearestSeen(level, List.of(far)) == far, "Now it sees Far");
                    Component refused = com.reiko.signaltower.network.ConfigureSensorPayload.apply(alex,
                            new com.reiko.signaltower.network.ConfigureSensorPayload(absolute, 2, strangers.ordinal()));
                    check(refused != null && keyOf(refused).endsWith("sensor_not_yours") && sensor.getRange() == 12
                            && sensor.getMode() == everyone, "Alex can't change it");
                    com.reiko.signaltower.network.ConfigureSensorPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureSensorPayload(absolute, 7, strangers.ordinal()));
                    check(lastKey(devBar).endsWith("sensor_set") && sensor.getRange() == 6 && sensor.getMode() == strangers,
                            "Dev sets strangers only (and 7, which isn't offered, becomes 6)");

                    check(sensor.nearestSeen(level, List.of(dev, alex)) == alex, "Strangers only on a public frequency: not Dev");
                    claims.put(3601, devId, "Dev", java.util.Map.of(alexId, "Alex"));
                    check(sensor.nearestSeen(level, List.of(dev, alex)) == null, "Nor Alex once he's a friend on its private frequency");
                    check(sensor.nearestSeen(level, List.of(dev, alex, rex)) == rex, "But Rex");

                    // Redstone around it does nothing.
                    helper.setBlock(at.below(), Blocks.REDSTONE_BLOCK);
                    check(!helper.getBlockState(at).getValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.POWERED)
                            && network.listen(level, 3601, absolute.offset(1, 0, 0), null).power() == 0, "Redstone doesn't make it send");

                    since[0] = level.getGameTime();
                    check(sensor.lookAround(level, since[0], List.of(dev, alex, rex)) == rex && sensor.isSensing(), "It sees Rex");
                    check(helper.getBlockState(at).getValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.POWERED), "Lit");
                    check(network.listen(level, 3601, absolute.offset(reach, 0, 0), null).power() == 15, "Sending at the edge of its reach");
                    check(network.listen(level, 3601, absolute.offset(reach + 1, 0, 0), null).power() == 0, "Not past it");
                    check(network.remoteCoverage(level, absolute.offset(1, 0, 0), 32, true).stream().noneMatch(c -> c.origin().equals(absolute)),
                            "Not a relay tower");
                    var log = com.reiko.signaltower.radio.FrequencyLog.log(server, 3601);
                    check(!log.isEmpty() && log.get(0).kind() == SignalNetwork.CauseKind.SENSOR && log.get(0).who().equals("Rex")
                            && log.get(0).whoKind() == com.reiko.signaltower.radio.FrequencyAudit.Who.OTHER
                            && log.get(0).pos().equals(java.util.Optional.of(absolute)), "The log names Rex at the sensor: " + log);

                    CompoundTag devInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absolute, dev);
                    List<Component> devLines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(at), devInfo);
                    check(lineKeys(devLines).equals(List.of("frequency", "sensor_owner", "sensor_settings", "sensor_sees", "reach", "private")),
                            "Jade for Dev: " + lineKeys(devLines));
                    check(firstArg(devLines.get(3)).equals("Rex"), "Dev sees who it saw");
                    CompoundTag alexInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absolute, alex);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(at), alexInfo))
                            .equals(List.of("frequency", "sensor_owner", "sensor_on", "reach", "private")), "Jade for Alex: no settings, no name");

                    var screen = com.reiko.signaltower.network.OpenSensorScreenPayload.of(level, sensor, false);
                    check(screen.frequency() == 3601 && screen.ownerName().equals("Dev") && screen.range() == 6 && screen.modeValue() == strangers
                            && screen.sensing() && screen.seenName().equals("Rex") && screen.reach() == reach && !screen.silenced(), "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenSensorScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenSensorScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");

                    var audit = com.reiko.signaltower.radio.FrequencyAudit.audit(server, 3601, dev, 512);
                    check(audit.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.MOTION_SENSOR
                            && e.pos().equals(java.util.Optional.of(absolute))), "In the audit as a motion sensor: " + audit);
                    dev.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
                    var ring = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(dev, absolute);
                    check(ring.kind() == com.reiko.signaltower.network.ReachPreviewPayload.MOTION_SENSOR && ring.reach() == reach, "The ring: " + ring);

                    helper.getBlockState(at).useWithoutItem(level, alex, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false));
                    check(lastKey(alexBar).endsWith("sensor_status"), "Alex gets the readout: " + lastKey(alexBar));
                })
                .thenIdle(20)
                .thenExecute(() -> check(sensor.isSensing(), "Still sending a second later with nobody in sight"))
                .thenWaitUntil(() -> check(!sensor.isSensing(), "Then it stops"))
                .thenExecute(() -> {
                    long held = level.getGameTime() - since[0];
                    check(held >= com.reiko.signaltower.blockentity.MotionSensorBlockEntity.HOLD_TICKS && held <= 45, "Held for 2 seconds: " + held);
                    check(!helper.getBlockState(at).getValue(com.reiko.signaltower.block.CompactRadioTransmitterBlock.POWERED)
                            && network.listen(level, 3601, absolute.offset(1, 0, 0), null).power() == 0, "Off, sending nothing");
                    CompoundTag devInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absolute, dev);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(at), devInfo))
                            .contains("sensor_idle"), "Jade: sees nobody");

                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
                    ItemStack tuner = new ItemStack(ModRegistry.FREQUENCY_TUNER.get());
                    FrequencyTunerItem.setFrequency(tuner, 3699);
                    alex.setItemInHand(InteractionHand.MAIN_HAND, tuner);
                    tuner.useOn(new UseOnContext(alex, InteractionHand.MAIN_HAND, hit));
                    check(sensor.getFrequency() == 3601 && lastKey(alexBar).endsWith("sensor_not_yours_tune"), "Only Dev re-tunes it: " + lastKey(alexBar));

                    CompoundTag saved = sensor.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.MotionSensorBlockEntity(absolute, helper.getBlockState(at));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(devId.equals(copy.getOwner()) && copy.getOwnerName().equals("Dev") && copy.getRange() == 6
                            && copy.getMode() == strangers && copy.getFrequency() == 3601, "Saved");
                    claims.remove(3601);
                    helper.setBlock(at.south(), Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    check(helper.getBlockState(at).isAir(), "Pops off without its wall");
                    check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                            .anyMatch(stack -> stack.is(ModRegistry.RADIO_MOTION_SENSOR_ITEM.get())
                                    && Integer.valueOf(3601).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Dropped with its frequency");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.40.0: logic receiver

    /**
     * The logic receiver: AND, OR and XOR of its two frequencies (AND the weaker, OR the stronger,
     * XOR the one that's on), switched in its screen's packet (refused out of reach); it counts as a
     * receiver on both frequencies, and re-tuning the second moves that; Jade, the screen packet
     * (round trip) and the audit know it; on someone else's encrypted second frequency it's deaf
     * there; it keeps both frequencies and its mode when saved, broken (on the item) and placed again.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void logicReceiver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(server);
        UUID devId = UUID.randomUUID();
        UUID rexId = UUID.randomUUID();
        var and = com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.LogicMode.AND;
        var or = com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.LogicMode.OR;
        var xor = com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.LogicMode.XOR;
        check(and.combine(15, 9) == 9 && or.combine(15, 9) == 15 && xor.combine(15, 9) == 0 && xor.combine(0, 9) == 9
                && and.combine(0, 9) == 0 && or.combine(0, 0) == 0, "AND the weaker, OR the stronger, XOR the one that's on");

        BlockPos rx = new BlockPos(2, 1, 1);
        BlockPos lamp = new BlockPos(1, 1, 1);
        BlockPos txA = new BlockPos(5, 1, 1);
        BlockPos txB = new BlockPos(8, 1, 1);
        BlockPos deaf = new BlockPos(12, 1, 1);
        BlockPos txC = new BlockPos(15, 1, 1);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(rx, ModRegistry.RADIO_LOGIC_RECEIVER.get());
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity logic = helper.getBlockEntity(rx);
        logic.setLogic(3701, 3702, and);
        place(helper, txA, ModRegistry.RADIO_TRANSMITTER.get(), 3701);
        place(helper, txB, ModRegistry.RADIO_TRANSMITTER.get(), 3702);
        claims.put(3703, devId, "Dev", java.util.Map.of(), List.of(), true);
        helper.setBlock(deaf, ModRegistry.RADIO_LOGIC_RECEIVER.get());
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity deafLogic = helper.getBlockEntity(deaf);
        deafLogic.setTunedBy(rexId);
        deafLogic.setLogic(3704, 3703, or);
        place(helper, txC, ModRegistry.RADIO_TRANSMITTER.get(), 3703);
        ((RadioBlockEntity) helper.getBlockEntity(txC)).setTunedBy(devId);
        helper.setBlock(txC.north(), Blocks.REDSTONE_BLOCK);
        BlockPos absolute = helper.absolutePos(rx);
        List<Component> devBar = new java.util.ArrayList<>();
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), devId, "Dev", false, devBar, new java.util.ArrayList<>());
        Player farAway = namedPlayer(helper, new BlockPos(40, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        SignalNetwork network = SignalNetwork.get(level);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "AND with both off: off");
                    check(network.receiversOn(3701).stream().anyMatch(r -> r.pos().equals(absolute))
                            && network.receiversOn(3702).stream().anyMatch(r -> r.pos().equals(absolute)), "A receiver on both frequencies");
                    check(network.countReceivers(3702, helper.absolutePos(txB), 16) >= 1, "Counted on the second for Heard by");
                    helper.setBlock(txA.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "AND with only the first on: still off");
                    check(logic.heardFirst() == 15 && logic.heardSecond() == 0, "It hears 15 and nothing");
                    check(com.reiko.signaltower.network.ConfigureLogicPayload.apply(farAway,
                            new com.reiko.signaltower.network.ConfigureLogicPayload(absolute, 3701, 3702, or.ordinal())) == null
                            && logic.getLogicMode() == and, "Out of reach: nothing changes");
                    Component set = com.reiko.signaltower.network.ConfigureLogicPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureLogicPayload(absolute, 3701, 3702, or.ordinal()));
                    check(set != null && keyOf(set).endsWith("logic_set") && logic.getLogicMode() == or
                            && devId.equals(logic.getTunedBy()), "Dev sets OR (and tunes it)");
                })
                .thenWaitUntil(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "OR with the first on: on");
                    check(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "The lamp next to it lights");
                })
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absolute);
                    check(info != null && info.getInt("second") == 3702 && info.getString("logic_mode").equals("or")
                            && info.getInt("heard") == 15 && info.getInt("heard_b") == 0, "Jade data: " + info);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info))
                            .equals(List.of("input_first", "input_second", "logic_mode", "logic_hearing", "output")),
                            "Jade lines: " + lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info)));
                    var screen = com.reiko.signaltower.network.OpenLogicScreenPayload.of(level, logic);
                    check(screen.first() == 3701 && screen.second() == 3702 && screen.modeValue() == or && screen.heardFirst() == 15
                            && screen.heardSecond() == 0 && screen.output() == 15 && screen.quiet() == 0, "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenLogicScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenLogicScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    logic.setLogic(3701, 3702, xor);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "XOR with only the first on: on");
                    helper.setBlock(txB.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "XOR with both on: off");
                    logic.setLogic(3701, 3702, and);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "AND with both on: on");
                    claims.put(3701, devId, "Dev", java.util.Map.of());
                    var audit = com.reiko.signaltower.radio.FrequencyAudit.audit(server, 3701, dev, 512);
                    check(audit.stream().anyMatch(e -> e.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.LOGIC_RECEIVER
                            && e.pos().equals(java.util.Optional.of(absolute))), "In the audit as a logic receiver: " + audit);
                    claims.remove(3701);

                    // Someone else's encrypted second frequency: deaf there.
                    BlockPos deafAbsolute = helper.absolutePos(deaf);
                    check(deafLogic.deafFrequency(level) == 3703 && deafLogic.heardSecond() == 0
                            && helper.getBlockState(deaf).getValue(RadioReceiverBlock.POWER) == 0, "Deaf on 3703, so OR stays off");
                    check(com.reiko.signaltower.block.RadioBlock.deafNotice(level, deafAbsolute) != null, "Its deaf notice");
                    check(com.reiko.signaltower.network.OpenLogicScreenPayload.of(level, deafLogic).quiet()
                            == com.reiko.signaltower.network.OpenLogicScreenPayload.DEAF_SECOND, "The screen says so");
                    CompoundTag deafInfo = com.reiko.signaltower.compat.RadioInfo.read(level, deafAbsolute);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(deaf), deafInfo)).contains("deaf_second"),
                            "Jade says the second is deaf");

                    CompoundTag saved = logic.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.RadioReceiverBlockEntity(absolute, helper.getBlockState(rx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.getFrequency() == 3701 && copy.getSecondFrequency() == 3702 && copy.getLogicMode() == and, "Saved");

                    logic.setLogic(3701, 3705, and);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    check(network.receiversOn(3702).stream().noneMatch(r -> r.pos().equals(absolute))
                            && network.receiversOn(3705).stream().anyMatch(r -> r.pos().equals(absolute)), "Re-tuned: on 3705, not 3702");
                    logic.setLogic(3701, 3702, xor);
                    level.destroyBlock(absolute, true);
                })
                .thenWaitUntil(() -> {
                    ItemStack drop = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                            .filter(stack -> stack.is(ModRegistry.RADIO_LOGIC_RECEIVER_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
                    check(!drop.isEmpty() && Integer.valueOf(3701).equals(drop.get(ModRegistry.FREQUENCY.get()))
                            && Integer.valueOf(3702).equals(drop.get(ModRegistry.SECOND_FREQUENCY.get()))
                            && Integer.valueOf(xor.ordinal()).equals(drop.get(ModRegistry.LOGIC_MODE.get())), "Dropped with both frequencies and XOR");
                })
                .thenExecute(() -> {
                    ItemStack drop = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                            .filter(stack -> stack.is(ModRegistry.RADIO_LOGIC_RECEIVER_ITEM.get())).findFirst().orElseThrow();
                    helper.setBlock(rx, ModRegistry.RADIO_LOGIC_RECEIVER.get());
                    com.reiko.signaltower.blockentity.RadioReceiverBlockEntity placed = helper.getBlockEntity(rx);
                    placed.applyComponentsFromItemStack(drop);
                    check(placed.getFrequency() == 3701 && placed.getSecondFrequency() == 3702 && placed.getLogicMode() == xor,
                            "Placed again from the item");
                    claims.remove(3703);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.40.0: signal meter

    /**
     * The Signal Meter's reading: strength, towers and blocks to the edge where the player stands
     * (an off transmitter still counts for the edge; out of reach is -1 and nothing); someone
     * else's encrypted frequency reads as deaf with nothing else, its members read it; the edge is
     * measured like the reach ring (exactly at the reach is 0, a block past it -1); the reading
     * packet survives the network; its settings packet sets the frequency and tick; it scrolls,
     * opens the directory and takes the scanner's Tune; sneak-clicking a converter measures what it sends.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void signalMeter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        UUID devId = UUID.randomUUID();
        UUID rexId = UUID.randomUUID();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos input = new BlockPos(2, 1, 0);
        BlockPos secret = new BlockPos(8, 1, 1);
        BlockPos converter = new BlockPos(12, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 3801);
        helper.setBlock(input, Blocks.REDSTONE_BLOCK);
        claims.put(3802, devId, "Dev", java.util.Map.of(), List.of(), true);
        place(helper, secret, ModRegistry.RADIO_TRANSMITTER.get(), 3802);
        ((RadioBlockEntity) helper.getBlockEntity(secret)).setTunedBy(devId);
        helper.setBlock(secret.north(), Blocks.REDSTONE_BLOCK);
        place(helper, converter, ModRegistry.RADIO_CONVERTER.get(), 3805);
        ((com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity) helper.getBlockEntity(converter)).setFrequencies(3805, 3806);
        Player dev = namedPlayer(helper, new BlockPos(4, 1, 1), devId, "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player rex = namedPlayer(helper, new BlockPos(6, 1, 1), rexId, "Rex", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        BlockPos absolute = helper.absolutePos(tx);
        int reach = RadioHelper.reachAt(level, absolute);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    var reading = com.reiko.signaltower.item.SignalMeterItem.read(level, dev, 3801);
                    check(reading.strength() == 15 && reading.towers() >= 1 && reading.margin() == reach - 2 && !reading.deaf(),
                            "Two blocks from the transmitter: 15, " + (reach - 2) + " to the edge: " + reading);
                    Vec3 beyond = Vec3.atBottomCenterOf(absolute.offset(reach + 2, 0, 0));
                    rex.moveTo(beyond.x, beyond.y, beyond.z);
                    var out = com.reiko.signaltower.item.SignalMeterItem.read(level, rex, 3801);
                    check(out.strength() == 0 && out.margin() == -1, "Out of reach: nothing, -1: " + out);
                    helper.setBlock(input, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    var off = com.reiko.signaltower.item.SignalMeterItem.read(level, dev, 3801);
                    check(off.strength() == 0 && off.margin() == reach - 2, "Transmitter off: 0, but still inside its reach: " + off);
                    Vec3 near = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 1, 1)));
                    rex.moveTo(near.x, near.y, near.z);
                    var deaf = com.reiko.signaltower.item.SignalMeterItem.read(level, rex, 3802);
                    check(deaf.deaf() && deaf.strength() == 0 && deaf.towers() == 0 && deaf.margin() == -1, "Encrypted for Rex: " + deaf);
                    var member = com.reiko.signaltower.item.SignalMeterItem.read(level, dev, 3802);
                    check(!member.deaf() && member.strength() == 15, "Dev hears his own: " + member);

                    SignalNetwork fresh = new SignalNetwork().attachTo(level);
                    BlockPos far = new BlockPos(8_400_000, 70, 0);
                    fresh.updateTransmitter(far, 3803, 15, 50, null);
                    check(fresh.edgeMargin(level, 3803, far.offset(30, 0, 0)) == 20, "30 blocks into a 50 reach: 20 to go");
                    check(fresh.edgeMargin(level, 3803, far.offset(30, 0, 40)) == 0, "Exactly at the reach: 0");
                    check(fresh.edgeMargin(level, 3803, far.offset(51, 0, 0)) == -1, "A block past it: -1");
                    check(fresh.edgeMargin(level, 3804, far) == -1, "Nothing on another frequency");
                    fresh.updateTransmitter(far.offset(60, 0, 0), 3803, 0, 50, null);
                    check(fresh.edgeMargin(level, 3803, far.offset(51, 0, 0)) == 41, "The off tower beside it reaches farthest past there");

                    var packet = com.reiko.signaltower.network.MeterReadingPayload.of(member);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.MeterReadingPayload.STREAM_CODEC.encode(buffer, packet);
                    check(com.reiko.signaltower.network.MeterReadingPayload.STREAM_CODEC.decode(buffer).equals(packet), "Reading packet round trip");
                    var outPacket = com.reiko.signaltower.network.MeterReadingPayload.of(new com.reiko.signaltower.item.SignalMeterItem.Reading(1, 0, 0, -1, false));
                    buffer.clear();
                    com.reiko.signaltower.network.MeterReadingPayload.STREAM_CODEC.encode(buffer, outPacket);
                    check(com.reiko.signaltower.network.MeterReadingPayload.STREAM_CODEC.decode(buffer).margin() == -1, "-1 survives the network");

                    ItemStack meter = new ItemStack(ModRegistry.SIGNAL_METER.get());
                    dev.setItemInHand(InteractionHand.MAIN_HAND, meter);
                    com.reiko.signaltower.network.ConfigurePagerPayload.apply(dev, new com.reiko.signaltower.network.ConfigurePagerPayload(false, 3804, false, true));
                    check(FrequencyTunerItem.getFrequency(meter) == 3804 && com.reiko.signaltower.item.SignalMeterItem.isSilent(meter),
                            "Its screen sets the frequency and turns the tick off");
                    check(RadioFavorites.isScrollable(meter) && SignalScannerItem.canTune(meter)
                            && com.reiko.signaltower.network.DirectoryRequestPayload.holdsRadioItem(dev), "Scrolls, takes Tune, opens the directory");
                    BlockPos converterAbsolute = helper.absolutePos(converter);
                    dev.setShiftKeyDown(true);
                    meter.useOn(new UseOnContext(dev, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(converterAbsolute), Direction.NORTH, converterAbsolute, false)));
                    dev.setShiftKeyDown(false);
                    check(FrequencyTunerItem.getFrequency(meter) == 3806, "Sneak-clicking a converter measures what it sends: "
                            + FrequencyTunerItem.getFrequency(meter));
                    claims.remove(3802);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.41.0: signal gate

    /** The strength the receiver at {@code pos} outputs. */
    private static int outputAt(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(RadioReceiverBlock.POWER);
    }

    /**
     * The converter's signal gate, with a transmitter sending 11: Pass sends 11; At least 8 sends
     * 11, At least 12 nothing; Below 12 sends 15, Below 11 nothing; Below 1 (a NOT gate) nothing,
     * and 15 once the transmitter goes quiet (remembered as the gate's turn-on). The settings packet,
     * the screen packet (round trip), Jade and the item keep the gate. A NOT gate feeding itself
     * blinks, never changing twice within 2 ticks. A gate is a source for the network (saved with
     * what it sends) and doesn't carry remote presses.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void signalGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos input = new BlockPos(8, 1, 1);
        BlockPos conv = new BlockPos(12, 1, 1);
        BlockPos rx = new BlockPos(16, 1, 1);
        BlockPos clock = new BlockPos(22, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 3901);
        for (int x = 3; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 0, 1), Blocks.STONE); // wire needs something to sit on
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.REDSTONE_WIRE);
        }
        helper.setBlock(input, Blocks.REDSTONE_BLOCK);
        place(helper, conv, ModRegistry.RADIO_CONVERTER.get(), 3901);
        com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity converter = helper.getBlockEntity(conv);
        converter.setFrequencies(3901, 3902);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 3902);
        place(helper, clock, ModRegistry.RADIO_CONVERTER.get(), 3903);
        com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity selfLoop = helper.getBlockEntity(clock);
        selfLoop.setFrequencies(3903, 3903);
        BlockPos convAbs = helper.absolutePos(conv);
        BlockPos clockAbs = helper.absolutePos(clock);
        SignalNetwork network = SignalNetwork.get(level);
        Player dev = namedPlayer(helper, new BlockPos(12, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        List<Integer> blinks = new java.util.ArrayList<>();

        helper.startSequence()
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 11, "Pass: 11 through, got " + outputAt(helper, rx)))
                .thenExecute(() -> {
                    boolean set = com.reiko.signaltower.network.ConfigureConverterPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureConverterPayload(convAbs, 3901, 3902, 1, 8));
                    check(set && converter.getGate() == 1 && converter.getThreshold() == 8, "At least 8, from the screen's packet");
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 11, "At least 8: 11 goes through"))
                .thenExecute(() -> converter.setGate(1, 12))
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "At least 12: nothing"))
                .thenExecute(() -> converter.setGate(2, 12))
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "Below 12: 15"))
                .thenExecute(() -> {
                    var history = network.history(3902);
                    check(!history.isEmpty() && history.get(history.size() - 1).cause().kind() == SignalNetwork.CauseKind.GATE
                            && convAbs.equals(history.get(history.size() - 1).cause().pos()), "The gate's turn-on is remembered");
                    converter.setGate(2, 11);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "Below 11: nothing"))
                .thenExecute(() -> converter.setGate(2, 1))
                .thenIdle(3)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 0, "NOT gate while 3901 is on: nothing");
                    helper.setBlock(input, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "NOT gate once 3901 is quiet: 15"))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, convAbs);
                    check(info != null && info.getInt("gate") == 2 && info.getInt("threshold") == 1 && info.getInt("gate_heard") == 0
                            && info.getInt("power") == 15, "Jade data: " + info);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(conv), info)).contains("gate"), "Jade's gate line");
                    var screen = new com.reiko.signaltower.network.OpenConverterScreenPayload(convAbs, 3901, 3902, 15, 50, 0,
                            converter.getGate(), converter.getThreshold(), converter.gateHeard(level));
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenConverterScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenConverterScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    var settings = new com.reiko.signaltower.network.ConfigureConverterPayload(convAbs, 3901, 3902, 2, 1);
                    buffer.clear();
                    com.reiko.signaltower.network.ConfigureConverterPayload.STREAM_CODEC.encode(buffer, settings);
                    check(com.reiko.signaltower.network.ConfigureConverterPayload.STREAM_CODEC.decode(buffer).equals(settings), "Settings packet round trip");

                    // A fresh network: a gate is a source there, saved with what it sends, and doesn't carry remote presses.
                    SignalNetwork fresh = new SignalNetwork().attachTo(level);
                    BlockPos far = new BlockPos(8_500_000, 70, 0);
                    fresh.updateRepeater(far, 3904, 3905, 50, null, 2, 1);
                    check(fresh.setGateOutput(far, 15) && !fresh.setGateOutput(far, 15), "Set once");
                    check(fresh.listen(level, 3905, far.offset(40, 0, 0), null).power() == 15, "Heard as a source");
                    SignalNetwork loaded = SignalNetwork.load(fresh.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).attachTo(level);
                    check(loaded.gateOutput(far) == 15 && loaded.repeaterAt(far).gate() == 2
                            && loaded.listen(level, 3905, far.offset(40, 0, 0), null).power() == 15, "Saved with its gate and what it sends");
                    BlockPos far2 = new BlockPos(8_600_000, 70, 0);
                    fresh.updateTransmitter(far2, 3906, 15, 50, null);
                    fresh.updateRepeater(far2.offset(10, 0, 0), 3906, 3907, 50, null, 1, 1);
                    fresh.updateRepeater(far2.offset(20, 0, 0), 3906, 3908, 50, null);
                    var carried = fresh.carried(level, 3906, List.of(new SignalNetwork.Coverage(far2, 50, false)));
                    check(carried.on(3907).isEmpty() && !carried.on(3908).isEmpty(), "A press is carried through a plain converter, not a gate");

                    selfLoop.setGate(2, 1);
                })
                .thenExecuteFor(30, () -> blinks.add(network.gateOutput(clockAbs)))
                .thenExecute(() -> {
                    check(blinks.contains(0) && blinks.contains(15), "A NOT gate feeding itself blinks: " + blinks);
                    for (int i = 1; i + 1 < blinks.size(); i++) {
                        check(!(blinks.get(i - 1).intValue() != blinks.get(i) && blinks.get(i).intValue() != blinks.get(i + 1)),
                                "Never two changes in a row: " + blinks);
                    }
                    selfLoop.setGate(0, 1);
                    CompoundTag saved = converter.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity(convAbs, helper.getBlockState(conv));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.getGate() == 2 && copy.getThreshold() == 1 && copy.getOutputFrequency() == 3902, "Its block entity saves the gate");
                    level.destroyBlock(convAbs, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_CONVERTER_ITEM.get())
                                && Integer.valueOf(2 * 16 + 1).equals(stack.get(ModRegistry.CONVERTER_GATE.get()))), "Dropped with its gate"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.41.0: delay receiver

    /**
     * The delay receiver: with a 1 s delay and a 2 s hold it turns on 1 s after its frequency does
     * and off 3 s after it goes quiet (and says when, in Jade and its screen packet); with no delay
     * and a 2 s hold, a 4-tick pulse turns it on at once and keeps it on for 2 s more. Times it
     * doesn't offer become none, out of reach changes nothing, and it keeps its timings when saved
     * and on its item.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void delayReceiver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos input = new BlockPos(2, 1, 0);
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 3951);
        helper.setBlock(rx, ModRegistry.RADIO_DELAY_RECEIVER.get());
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity delay = helper.getBlockEntity(rx);
        delay.setFrequency(3951);
        BlockPos absolute = helper.absolutePos(rx);
        Player dev = namedPlayer(helper, new BlockPos(6, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(60, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        long[] since = new long[1];

        check(firstArg(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.timeLabel(10)).equals("0.5")
                && firstArg(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.timeLabel(1200)).equals("60")
                && keyOf(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.timeLabel(0)).endsWith("time_none")
                && firstArg(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.countdownLabel(47)).equals("2.4")
                && firstArg(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.countdownLabel(40)).equals("2")
                && firstArg(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.countdownLabel(1)).equals("0.1"), "Time labels");

        helper.startSequence()
                .thenExecute(() -> {
                    check(com.reiko.signaltower.network.ConfigureDelayPayload.apply(far,
                            new com.reiko.signaltower.network.ConfigureDelayPayload(absolute, 40, 40)) == null, "Out of reach: nothing");
                    Component set = com.reiko.signaltower.network.ConfigureDelayPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureDelayPayload(absolute, 20, 40));
                    check(set != null && keyOf(set).endsWith("delay_set") && delay.getDelayTicks() == 20 && delay.getHoldTicks() == 40,
                            "1 s delay, 2 s hold");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 0, "Off while 3951 is quiet");
                    helper.setBlock(input, Blocks.REDSTONE_BLOCK);
                    since[0] = level.getGameTime();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 0, "Not yet on after 5 ticks");
                    long change = delay.changeIn(level.getGameTime());
                    check(change > 0 && change <= 16, "It turns on soon: " + change);
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absolute);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info))
                            .equals(List.of("frequency", "switched_off", "delay_timing", "hearing", "delay_turns_on", "towers")),
                            "Jade while waiting: " + lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info)));
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "On"))
                .thenExecute(() -> {
                    long waited = level.getGameTime() - since[0];
                    check(waited >= 20 && waited <= 24, "On 1 s after its frequency: " + waited);
                    helper.setBlock(input, Blocks.AIR);
                    since[0] = level.getGameTime();
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 15, "Still on 1.5 s later (delay, then hold)");
                    check(delay.changeIn(level.getGameTime()) < 0, "It says it turns off");
                    var screen = com.reiko.signaltower.network.OpenDelayScreenPayload.of(level, delay);
                    check(screen.frequency() == 3951 && screen.delay() == 20 && screen.hold() == 40 && screen.output() == 15
                            && screen.changeIn() < 0 && !screen.deaf(), "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenDelayScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenDelayScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "Off"))
                .thenExecute(() -> {
                    long waited = level.getGameTime() - since[0];
                    check(waited >= 60 && waited <= 65, "Off 3 s after its frequency went quiet (1 s delay + 2 s hold): " + waited);
                    delay.setTiming(0, 40);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.setBlock(input, Blocks.REDSTONE_BLOCK))
                .thenIdle(4)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 15, "No delay: on at once");
                    helper.setBlock(input, Blocks.AIR);
                    since[0] = level.getGameTime();
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "Then off"))
                .thenExecute(() -> {
                    long held = level.getGameTime() - since[0];
                    check(held >= 40 && held <= 44, "Held 2 s after a 4-tick pulse: " + held);
                    delay.setTiming(7, 1300);
                    check(delay.getDelayTicks() == 0 && delay.getHoldTicks() == 0, "Times it doesn't offer become none");
                    delay.setTiming(100, 600);
                    CompoundTag saved = delay.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.RadioReceiverBlockEntity(absolute, helper.getBlockState(rx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.getDelayTicks() == 100 && copy.getHoldTicks() == 600 && copy.getFrequency() == 3951, "Saved");
                    level.destroyBlock(absolute, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_DELAY_RECEIVER_ITEM.get())
                                && Integer.valueOf(100).equals(stack.get(ModRegistry.DELAY_TICKS.get()))
                                && Integer.valueOf(600).equals(stack.get(ModRegistry.HOLD_TICKS.get()))), "Dropped with its timings"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.42.0: timer transmitter

    /**
     * The timer transmitter: set to every 1 s with a 0.25 s pulse (by Dev's packet), a receiver on
     * its frequency turns on every 20 ticks for about 5, and the log names the timer; a pulse longer
     * than half the interval is cut to half; redstone into it pauses it, and it starts again once
     * that's gone; a daily timer is on during its hours and off outside them. Jade, the screen packet
     * (round trip) and the item know its schedule; a stranger can't change it on someone else's
     * private frequency, nor anyone out of reach; it's saved.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void timerTransmitter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos pause = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TIMER_TRANSMITTER.get(), 4001);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4001);
        com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity timer = helper.getBlockEntity(tx);
        BlockPos abs = helper.absolutePos(tx);
        SignalNetwork network = SignalNetwork.get(level);
        Player dev = namedPlayer(helper, new BlockPos(3, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        List<Integer> samples = new java.util.ArrayList<>();

        // The clock's arithmetic: day time 0 is 06:00, 18,000 midnight.
        check(com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourOf(0) == 6
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourOf(18_000) == 0
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourOf(23_999) == 5
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourOf(36_000) == 18, "Hours");
        check(!com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.dailyOn(0, 18, 6)
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.dailyOn(12_000, 18, 6)
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.dailyOn(18_000, 18, 6)
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.dailyOn(6_000, 9, 17)
                && !com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.dailyOn(6_000, 12, 12), "Daily hours");
        check(firstArg(com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.durationLabel(6000)).equals("5")
                && keyOf(com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.durationLabel(6000)).endsWith("time_minutes")
                && firstArg(com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.durationLabel(5)).equals("0.25")
                && com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourLabel(6).getString().equals("06:00"), "Labels");

        helper.startSequence()
                .thenExecute(() -> {
                    Component set = com.reiko.signaltower.network.ConfigureTimerPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureTimerPayload(abs, false, 20, 5, 18, 6));
                    check(set != null && keyOf(set).endsWith("timer_set"), "Set: " + set);
                    check(!timer.isDaily() && timer.getInterval() == 20 && timer.effectivePulse() == 5, "Every 1s, 0.25s pulse");
                })
                .thenExecuteFor(61, () -> samples.add(outputAt(helper, rx)))
                .thenExecute(() -> {
                    List<Integer> rises = new java.util.ArrayList<>();
                    List<Integer> falls = new java.util.ArrayList<>();
                    for (int i = 1; i < samples.size(); i++) {
                        if (samples.get(i) > 0 && samples.get(i - 1) == 0) rises.add(i);
                        if (samples.get(i) == 0 && samples.get(i - 1) > 0) falls.add(i);
                    }
                    check(rises.size() >= 2, "Pulses: " + samples);
                    for (int i = 1; i < rises.size(); i++) {
                        int gap = rises.get(i) - rises.get(i - 1);
                        check(gap >= 19 && gap <= 21, "Every 20 ticks: " + samples);
                    }
                    for (int rise : rises) {
                        falls.stream().filter(fall -> fall > rise).findFirst().ifPresent(fall ->
                                check(fall - rise >= 4 && fall - rise <= 6, "About 5 ticks on: " + samples));
                    }
                    var history = network.history(4001);
                    check(!history.isEmpty() && history.get(history.size() - 1).cause().kind() == SignalNetwork.CauseKind.TIMER
                            && abs.equals(history.get(history.size() - 1).cause().pos()), "The log names the timer");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info));
                    check(keys.contains("timer_schedule") && (keys.contains("timer_next_pulse") || keys.contains("timer_pulse_ends")), "Jade: " + keys);
                    var screen = com.reiko.signaltower.network.OpenTimerScreenPayload.of(level, timer);
                    check(screen.frequency() == 4001 && !screen.daily() && screen.interval() == 20 && screen.pulse() == 5
                            && !screen.paused() && !screen.silenced() && screen.changeIn() != 0, "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenTimerScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenTimerScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    var settings = new com.reiko.signaltower.network.ConfigureTimerPayload(abs, true, 200, 20, 18, 6);
                    buffer.clear();
                    com.reiko.signaltower.network.ConfigureTimerPayload.STREAM_CODEC.encode(buffer, settings);
                    check(com.reiko.signaltower.network.ConfigureTimerPayload.STREAM_CODEC.decode(buffer).equals(settings), "Settings packet round trip");

                    timer.setTiming(false, 20, 200, 18, 6, level.getGameTime());
                    check(timer.getPulse() == 200 && timer.effectivePulse() == 10, "A pulse is at most half the interval");
                    timer.setTiming(false, 7, 3, 18, 6, level.getGameTime());
                    check(timer.getInterval() == com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.DEFAULT_INTERVAL
                            && timer.getPulse() == com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.DEFAULT_PULSE, "Times it doesn't offer become the defaults");
                    timer.setTiming(false, 20, 10, 18, 6, level.getGameTime());
                    helper.setBlock(pause, Blocks.REDSTONE_BLOCK);
                    samples.clear();
                })
                .thenExecuteFor(45, () -> samples.add(outputAt(helper, rx)))
                .thenExecute(() -> {
                    // The first sample comes before the receiver has heard about the pause.
                    check(samples.stream().skip(2).allMatch(v -> v == 0), "Paused: " + samples);
                    check(timer.isPaused(level) && com.reiko.signaltower.network.OpenTimerScreenPayload.of(level, timer).paused(), "It knows it's paused");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("timer_paused"), "Jade says paused");
                    helper.setBlock(pause, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "It starts again"))
                .thenExecute(() -> {
                    int hour = com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourOf(level.getDayTime());
                    timer.setTiming(true, 200, 20, hour, (hour + 2) % 24, level.getGameTime());
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "Daily: on during its hours"))
                .thenIdle(25)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 15, "Daily: stays on");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("timer_on_until"), "Jade: on until");
                    int hour = com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity.hourOf(level.getDayTime());
                    timer.setTiming(true, 200, 20, (hour + 2) % 24, (hour + 3) % 24, level.getGameTime());
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "Daily: off outside its hours"))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("timer_turns_on_at"), "Jade: turns on at");
                    var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
                    claims.put(4001, UUID.randomUUID(), "Owner", java.util.Map.of());
                    try {
                        Component refused = com.reiko.signaltower.network.ConfigureTimerPayload.apply(dev,
                                new com.reiko.signaltower.network.ConfigureTimerPayload(abs, false, 20, 5, 18, 6));
                        check(refused != null && keyOf(refused).endsWith("private_refused_block") && timer.isDaily(),
                                "A stranger can't change it on someone else's private frequency");
                    } finally {
                        claims.remove(4001);
                    }
                    check(com.reiko.signaltower.network.ConfigureTimerPayload.apply(far,
                            new com.reiko.signaltower.network.ConfigureTimerPayload(abs, false, 20, 5, 18, 6)) == null && timer.isDaily(), "Out of reach");
                    timer.setTiming(true, 600, 40, 20, 4, level.getGameTime());
                    CompoundTag saved = timer.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.TimerTransmitterBlockEntity(abs, helper.getBlockState(tx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.isDaily() && copy.getInterval() == 600 && copy.getPulse() == 40 && copy.getOnHour() == 20
                            && copy.getOffHour() == 4 && copy.getFrequency() == 4001, "Saved");
                    level.destroyBlock(abs, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_TIMER_TRANSMITTER_ITEM.get())
                                && Integer.valueOf(1).equals(stack.get(ModRegistry.TIMER_MODE.get()))
                                && Integer.valueOf(600).equals(stack.get(ModRegistry.TIMER_INTERVAL.get()))
                                && Integer.valueOf(20 * 24 + 4).equals(stack.get(ModRegistry.TIMER_HOURS.get()))), "Dropped with its schedule"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.42.0: counter receiver

    /** Adds a pulse on the transmitter fed by {@code input}: on for {@code on} ticks, then off for {@code off}. */
    private static void pulseInput(GameTestHelper helper, net.minecraft.gametest.framework.GameTestSequence sequence,
                                   BlockPos input, int on, int off) {
        sequence.thenExecute(() -> helper.setBlock(input, Blocks.REDSTONE_BLOCK)).thenIdle(on)
                .thenExecute(() -> helper.setBlock(input, Blocks.AIR)).thenIdle(off);
    }

    /**
     * The counter receiver: set (by Dev's packet) to 3 with a reset frequency, it stays off for two
     * turn-ons and pulses on the third (back to 0, off again after 1 s); with a 1 s forget time, a
     * pause forgets the count; its reset frequency clears it; set to stay on at 2, it stays on through
     * more turn-ons until the reset frequency turns on. Jade, the screen packet (round trip), Reset
     * count, saving and the item keep up; nobody out of reach can set it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void counterReceiver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos txA = new BlockPos(2, 1, 1);
        BlockPos inA = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(6, 1, 1);
        BlockPos txB = new BlockPos(10, 1, 1);
        BlockPos inB = new BlockPos(11, 1, 1);
        place(helper, txA, ModRegistry.RADIO_TRANSMITTER.get(), 4051);
        place(helper, txB, ModRegistry.RADIO_TRANSMITTER.get(), 4052);
        place(helper, rx, ModRegistry.RADIO_COUNTER_RECEIVER.get(), 4051);
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity counter = helper.getBlockEntity(rx);
        BlockPos abs = helper.absolutePos(rx);
        Player dev = namedPlayer(helper, new BlockPos(6, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());

        check(keyOf(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.counterText(3, false, 0)).endsWith("counter_text_pulse")
                && keyOf(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.counterText(3, true, 20)).endsWith("counter_text_stay")
                && com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.counterText(3, true, 20).getSiblings().size() == 1
                && com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.validCount(17) == 3
                && com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.validCount(64) == 64, "Texts and counts");

        var sequence = helper.startSequence();
        sequence.thenExecute(() -> {
            Component set = com.reiko.signaltower.network.ConfigureCounterPayload.apply(dev,
                    new com.reiko.signaltower.network.ConfigureCounterPayload(abs, 3, false, 0, 4052, false));
            check(set != null && keyOf(set).endsWith("counter_set") && counter.getCounterTarget() == 3 && !counter.isCounterLatch()
                    && counter.getResetFrequency() == 4052 && dev.getUUID().equals(counter.getTunedBy()), "Set: " + set);
            check(com.reiko.signaltower.network.ConfigureCounterPayload.apply(far,
                    new com.reiko.signaltower.network.ConfigureCounterPayload(abs, 5, true, 0, 0, false)) == null
                    && counter.getCounterTarget() == 3, "Out of reach");
        }).thenIdle(3);
        pulseInput(helper, sequence, inA, 3, 3);
        pulseInput(helper, sequence, inA, 3, 3);
        sequence.thenExecute(() -> check(counter.getCount() == 2 && outputAt(helper, rx) == 0, "Two: still off, count " + counter.getCount()));
        pulseInput(helper, sequence, inA, 3, 0);
        sequence.thenExecute(() -> check(outputAt(helper, rx) == 15 && counter.isCounterOn() && counter.getCount() == 0,
                        "Three: on, counting from 0 again"))
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "Off after its pulse"))
                .thenExecute(() -> counter.setCounter(3, false, 20, 4052))
                .thenIdle(3);
        pulseInput(helper, sequence, inA, 3, 3);
        pulseInput(helper, sequence, inA, 3, 3);
        sequence.thenExecute(() -> {
                    check(counter.getCount() == 2 && counter.forgetIn(level.getGameTime()) > 0, "Two, forgetting soon");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info));
                    check(keys.equals(List.of("counter_counts", "counter_reset", "counter_count", "counter_settings", "counter_forgets_in",
                            "switched_off", "towers")), "Jade while counting: " + keys);
                })
                .thenIdle(25)
                .thenExecute(() -> check(counter.getCount() == 0 && outputAt(helper, rx) == 0, "A pause forgets the count"));
        pulseInput(helper, sequence, inA, 3, 3);
        sequence.thenExecute(() -> check(counter.getCount() == 1, "One"));
        pulseInput(helper, sequence, inB, 3, 3);
        sequence.thenExecute(() -> {
            check(counter.getCount() == 0, "Its reset frequency clears the count");
            counter.setCounter(2, true, 0, 4052);
        }).thenIdle(3);
        pulseInput(helper, sequence, inA, 3, 3);
        pulseInput(helper, sequence, inA, 3, 3);
        sequence.thenExecute(() -> check(outputAt(helper, rx) == 15 && counter.getCount() == 2, "Two: on"));
        pulseInput(helper, sequence, inA, 3, 3);
        pulseInput(helper, sequence, inA, 3, 3);
        sequence.thenIdle(25)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 15 && counter.getCount() == 2, "Stays on through more turn-ons");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info));
                    check(keys.equals(List.of("counter_counts", "counter_reset", "counter_reached_stay", "counter_settings", "switched_on",
                            "towers")), "Jade while on: " + keys);
                    var screen = com.reiko.signaltower.network.OpenCounterScreenPayload.of(level, counter);
                    check(screen.frequency() == 4051 && screen.target() == 2 && screen.latch() && screen.reset() == 4052 && screen.on()
                            && screen.count() == 2 && screen.deaf() == 0 && screen.forgetIn() == 0, "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenCounterScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenCounterScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    var settings = new com.reiko.signaltower.network.ConfigureCounterPayload(abs, 2, true, 40, 4052, true);
                    buffer.clear();
                    com.reiko.signaltower.network.ConfigureCounterPayload.STREAM_CODEC.encode(buffer, settings);
                    check(com.reiko.signaltower.network.ConfigureCounterPayload.STREAM_CODEC.decode(buffer).equals(settings), "Settings packet round trip");
                    CompoundTag saved = counter.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.RadioReceiverBlockEntity(abs, helper.getBlockState(rx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.getCount() == 2 && copy.isCounterOn() && copy.isCounterLatch() && copy.getCounterTarget() == 2
                            && copy.getResetFrequency() == 4052, "Saved with its count");
                });
        pulseInput(helper, sequence, inB, 3, 3);
        sequence.thenExecute(() -> check(outputAt(helper, rx) == 0 && counter.getCount() == 0 && !counter.isCounterOn(), "Reset: off"));
        pulseInput(helper, sequence, inA, 3, 3);
        sequence.thenExecute(() -> {
                    check(counter.getCount() == 1, "Counting again");
                    Component cleared = com.reiko.signaltower.network.ConfigureCounterPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureCounterPayload(abs, 2, true, 0, 4052, true));
                    check(cleared != null && keyOf(cleared).endsWith("counter_cleared") && counter.getCount() == 0, "Reset count: " + cleared);
                    Component none = com.reiko.signaltower.network.ConfigureCounterPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureCounterPayload(abs, 2, true, 0, 0, false));
                    check(none != null && counter.getResetFrequency() == 0, "No reset frequency");
                    counter.setCounter(5, true, 40, 4052);
                    level.destroyBlock(abs, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_COUNTER_RECEIVER_ITEM.get())
                                && Integer.valueOf(5).equals(stack.get(ModRegistry.COUNTER_TARGET.get()))
                                && Integer.valueOf(1).equals(stack.get(ModRegistry.COUNTER_LATCH.get()))
                                && Integer.valueOf(40).equals(stack.get(ModRegistry.COUNTER_FORGET.get()))
                                && Integer.valueOf(4052).equals(stack.get(ModRegistry.RESET_FREQUENCY.get()))), "Dropped with its settings"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.43.0: speaker

    /**
     * The speaker: set (by Dev's packet) to a bell played once, a short pulse plays one note and a
     * long one still only one; set to the doorbell repeating every second, 45 ticks on play three
     * rings of two notes each. One placed while its frequency is already on stays quiet, but
     * outputs. The note labels and pitches are a note block's; Jade, the screen packet (round trip),
     * saving and the item keep its settings; nobody out of reach can set it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void radioSpeaker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos input = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(6, 1, 1);
        BlockPos late = new BlockPos(9, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4101);
        place(helper, rx, ModRegistry.RADIO_SPEAKER.get(), 4101);
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity speaker = helper.getBlockEntity(rx);
        BlockPos abs = helper.absolutePos(rx);
        Player dev = namedPlayer(helper, new BlockPos(6, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        int bell = com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.packSpeaker(com.reiko.signaltower.radio.SpeakerSound.BELL, 12,
                com.reiko.signaltower.radio.SpeakerSound.Volume.NORMAL, false);
        int doorbell = com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.packSpeaker(com.reiko.signaltower.radio.SpeakerSound.DOORBELL, 18,
                com.reiko.signaltower.radio.SpeakerSound.Volume.LOUD, true);
        int[] before = new int[1];

        check(com.reiko.signaltower.radio.SpeakerSound.noteLabel(12).getString().equals("F♯4")
                && com.reiko.signaltower.radio.SpeakerSound.noteLabel(0).getString().equals("F♯3")
                && com.reiko.signaltower.radio.SpeakerSound.noteLabel(18).getString().equals("C5")
                && com.reiko.signaltower.radio.SpeakerSound.noteLabel(24).getString().equals("F♯5")
                && Math.abs(com.reiko.signaltower.radio.SpeakerSound.pitch(12) - 1.0F) < 1e-6
                && Math.abs(com.reiko.signaltower.radio.SpeakerSound.pitch(24) - 2.0F) < 1e-6
                && com.reiko.signaltower.radio.SpeakerSound.byId(15) == com.reiko.signaltower.radio.SpeakerSound.BELL, "Notes and pitches");
        check(keyOf(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.speakerText(bell)).endsWith("speaker_text_once")
                && keyOf(com.reiko.signaltower.blockentity.RadioReceiverBlockEntity.speakerText(doorbell)).endsWith("speaker_text_repeat"), "Texts");

        var sequence = helper.startSequence();
        sequence.thenExecute(() -> {
            Component set = com.reiko.signaltower.network.ConfigureSpeakerPayload.apply(dev,
                    new com.reiko.signaltower.network.ConfigureSpeakerPayload(abs, bell));
            check(set != null && keyOf(set).endsWith("speaker_set") && speaker.packedSpeaker() == bell, "Set: " + set);
            check(com.reiko.signaltower.network.ConfigureSpeakerPayload.apply(far,
                    new com.reiko.signaltower.network.ConfigureSpeakerPayload(abs, doorbell)) == null && speaker.packedSpeaker() == bell, "Out of reach");
        }).thenIdle(3).thenExecute(() -> before[0] = speaker.getNotesPlayed());
        pulseInput(helper, sequence, input, 3, 3);
        sequence.thenExecute(() -> check(speaker.getNotesPlayed() == before[0] + 1, "A pulse: one note, played " + (speaker.getNotesPlayed() - before[0])));
        sequence.thenExecute(() -> helper.setBlock(input, Blocks.REDSTONE_BLOCK))
                .thenIdle(50)
                .thenExecute(() -> {
                    check(speaker.getNotesPlayed() == before[0] + 2 && outputAt(helper, rx) == 15, "On for 50 ticks: still one note, and it outputs");
                    helper.setBlock(late, ModRegistry.RADIO_SPEAKER.get());
                    com.reiko.signaltower.blockentity.RadioReceiverBlockEntity lateSpeaker = helper.getBlockEntity(late);
                    lateSpeaker.setFrequency(4101);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    com.reiko.signaltower.blockentity.RadioReceiverBlockEntity lateSpeaker = helper.getBlockEntity(late);
                    check(lateSpeaker.getNotesPlayed() == 0 && outputAt(helper, late) == 15, "Placed while it's on: quiet, but on");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info)).contains("speaker_plays"), "Jade");
                    helper.setBlock(input, Blocks.AIR);
                    speaker.setSpeaker(com.reiko.signaltower.radio.SpeakerSound.DOORBELL, 18, com.reiko.signaltower.radio.SpeakerSound.Volume.LOUD, true);
                    check(speaker.packedSpeaker() == doorbell, "Doorbell, C5, loud, repeating");
                })
                .thenIdle(5)
                .thenExecute(() -> before[0] = speaker.getNotesPlayed());
        pulseInput(helper, sequence, input, 45, 12);
        sequence.thenExecute(() -> {
                    check(speaker.getNotesPlayed() == before[0] + 6, "45 ticks on: three rings of two notes, played " + (speaker.getNotesPlayed() - before[0]));
                    var screen = com.reiko.signaltower.network.OpenSpeakerScreenPayload.of(level, speaker);
                    check(screen.frequency() == 4101 && screen.speaker() == doorbell && !screen.deaf() && screen.heard() == 0, "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenSpeakerScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenSpeakerScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    CompoundTag saved = speaker.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.RadioReceiverBlockEntity(abs, helper.getBlockState(rx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.packedSpeaker() == doorbell && copy.getFrequency() == 4101, "Saved");
                    level.destroyBlock(abs, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_SPEAKER_ITEM.get())
                                && Integer.valueOf(doorbell).equals(stack.get(ModRegistry.SPEAKER.get()))), "Dropped with its settings"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.43.0: status board

    /**
     * The status board: set (by Dev's packet) to 4151, 4152, an empty row and 4153 on a wall, each
     * row shows the strength heard there as its transmitter turns on and off, and that reaches
     * clients in the update tag; Jade lists the rows; on someone else's encrypted frequency its row
     * is deaf; the screen packet (round trip) and saving keep up; nobody out of reach can set it;
     * taking the wall away pops it off with its rows on the item.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void statusBoard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos txA = new BlockPos(2, 1, 1);
        BlockPos inA = new BlockPos(1, 1, 1);
        BlockPos txB = new BlockPos(16, 1, 1);
        BlockPos inB = new BlockPos(17, 1, 1);
        BlockPos boardPos = new BlockPos(10, 1, 1);
        BlockPos wall = new BlockPos(10, 1, 2);
        place(helper, txA, ModRegistry.RADIO_TRANSMITTER.get(), 4151);
        place(helper, txB, ModRegistry.RADIO_TRANSMITTER.get(), 4152);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(boardPos, ModRegistry.RADIO_STATUS_BOARD.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.StatusBoardBlock.FACING, net.minecraft.core.Direction.NORTH));
        com.reiko.signaltower.blockentity.StatusBoardBlockEntity board = helper.getBlockEntity(boardPos);
        BlockPos abs = helper.absolutePos(boardPos);
        Player dev = namedPlayer(helper, new BlockPos(10, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());

        helper.startSequence()
                .thenExecute(() -> {
                    check(helper.getBlockState(boardPos).canSurvive(level, abs), "It hangs on the wall");
                    Component set = com.reiko.signaltower.network.ConfigureBoardPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureBoardPayload(abs, List.of(4151, 4152, 0, 4153)));
                    check(set != null && keyOf(set).endsWith("board_set") && firstArg(set).equals("3")
                            && board.frequencyList().equals(List.of(4151, 4152, 0, 4153)) && dev.getUUID().equals(board.getTunedBy()), "Set: " + set);
                    check(com.reiko.signaltower.network.ConfigureBoardPayload.apply(far,
                            new com.reiko.signaltower.network.ConfigureBoardPayload(abs, List.of(1, 2, 3, 4))) == null
                            && board.getFrequency(0) == 4151, "Out of reach");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(board.getStrength(0) == 0 && board.getStrength(1) == 0 && board.getStrength(3) == 0, "All off");
                    helper.setBlock(inA, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(board.getStrength(0) == 15 && board.getStrength(1) == 0, "Row 1 on"))
                .thenExecute(() -> helper.setBlock(inB, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(board.getStrength(1) == 15, "Row 2 on"))
                .thenExecute(() -> {
                    CompoundTag update = board.getUpdateTag(level.registryAccess());
                    var client = new com.reiko.signaltower.blockentity.StatusBoardBlockEntity(abs, helper.getBlockState(boardPos));
                    client.loadWithComponents(update, level.registryAccess());
                    check(client.getStrength(0) == 15 && client.getStrength(1) == 15 && client.getStrength(3) == 0
                            && client.getFrequency(3) == 4153 && client.getTunedBy() == null, "Clients get the rows and strengths (not who set it)");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(boardPos), info));
                    check(keys.equals(List.of("board_row_on", "board_row_on", "board_row_off")), "Jade: " + keys);
                    var screen = com.reiko.signaltower.network.OpenBoardScreenPayload.of(level, board);
                    check(screen.frequencies().equals(List.of(4151, 4152, 0, 4153)) && screen.strengths().equals(List.of(15, 15, 0, 0))
                            && screen.deaf() == 0, "Screen: " + screen);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.OpenBoardScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenBoardScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    helper.setBlock(inA, Blocks.AIR);
                    claims.put(4153, UUID.randomUUID(), "Owner", java.util.Map.of(), List.of(), true);
                })
                .thenWaitUntil(() -> check(board.getStrength(0) == 0, "Row 1 off again"))
                .thenWaitUntil(() -> check(board.isDeaf(3), "Row 4: deaf to someone else's encrypted frequency"))
                .thenExecute(() -> {
                    try {
                        CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                        List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(boardPos), info));
                        check(keys.equals(List.of("board_row_off", "board_row_on", "board_row_deaf")), "Jade with a deaf row: " + keys);
                        check(com.reiko.signaltower.network.OpenBoardScreenPayload.of(level, board).deaf() == 8, "The screen knows row 4 is deaf");
                    } finally {
                        claims.remove(4153);
                    }
                    CompoundTag saved = board.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.StatusBoardBlockEntity(abs, helper.getBlockState(boardPos));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.frequencyList().equals(List.of(4151, 4152, 0, 4153)) && dev.getUUID().equals(copy.getTunedBy()), "Saved");
                    helper.setBlock(wall, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(boardPos).isAir(), "Pops off without its wall"))
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_STATUS_BOARD_ITEM.get())
                                && List.of(4151, 4152, 0, 4153).equals(stack.get(ModRegistry.BOARD_FREQUENCIES.get()))), "Dropped with its rows"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.44.0: aviation beacon

    /**
     * The Aviation Beacon: its clock (dusk to dawn, blink, flash, steady) and when it's lit. On top
     * of a transmitter on masts, on a mast's side and on a plain wall: it hangs on all three but not
     * in the air; the first two find the tower, the third none. Set to "while its tower sends" it's
     * on only while the transmitter broadcasts. Jade knows its tower; the screen's packet sets it
     * (round trip), from anyone within reach; the item keeps its settings; taking the mast away
     * pops the side one off.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aviationBeacon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 4, 2);
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos top = new BlockPos(2, 5, 2);
        BlockPos side = new BlockPos(3, 2, 2);
        BlockPos stone = new BlockPos(6, 1, 2);
        BlockPos onStone = new BlockPos(6, 1, 1);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(2, y, 2), ModRegistry.RADIO_MAST.get());
        }
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4251);
        helper.setBlock(stone, Blocks.STONE);
        BlockState floor = ModRegistry.AVIATION_BEACON.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.AviationBeaconBlock.FACE, AttachFace.FLOOR);
        BlockState onMast = ModRegistry.AVIATION_BEACON.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.AviationBeaconBlock.FACE, AttachFace.WALL)
                .setValue(com.reiko.signaltower.block.AviationBeaconBlock.FACING, Direction.EAST)
                .setValue(com.reiko.signaltower.block.AviationBeaconBlock.WHEN, com.reiko.signaltower.radio.AviationLight.When.SENDING);
        BlockState plain = ModRegistry.AVIATION_BEACON.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.AviationBeaconBlock.FACE, AttachFace.WALL)
                .setValue(com.reiko.signaltower.block.AviationBeaconBlock.FACING, Direction.NORTH);
        helper.setBlock(top, floor);
        helper.setBlock(side, onMast);
        helper.setBlock(onStone, plain);
        BlockPos absTx = helper.absolutePos(tx);
        BlockPos absTop = helper.absolutePos(top);
        BlockPos absSide = helper.absolutePos(side);
        BlockPos absStone = helper.absolutePos(onStone);
        Player dev = namedPlayer(helper, new BlockPos(3, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());

        // Its clock: dusk to dawn are the hours you can sleep; the blinks go by the game time.
        check(!com.reiko.signaltower.radio.AviationLight.isNightTime(0) && !com.reiko.signaltower.radio.AviationLight.isNightTime(12_541)
                && com.reiko.signaltower.radio.AviationLight.isNightTime(12_542) && com.reiko.signaltower.radio.AviationLight.isNightTime(18_000)
                && !com.reiko.signaltower.radio.AviationLight.isNightTime(23_460) && com.reiko.signaltower.radio.AviationLight.isNightTime(42_000), "Night");
        var blink = com.reiko.signaltower.radio.AviationLight.Pattern.BLINK;
        var flash = com.reiko.signaltower.radio.AviationLight.Pattern.FLASH;
        var steady = com.reiko.signaltower.radio.AviationLight.Pattern.STEADY;
        check(blink.onAt(0) && blink.onAt(19) && !blink.onAt(20) && !blink.onAt(39) && blink.onAt(40), "Blink: 1s on, 1s off");
        check(flash.onAt(0) && flash.onAt(2) && !flash.onAt(3) && !flash.onAt(29) && flash.onAt(30), "Flash: 3 ticks every 1.5s");
        check(steady.onAt(0) && steady.onAt(17) && steady.onAt(12_345), "Steady");
        check(com.reiko.signaltower.radio.AviationLight.isLit(com.reiko.signaltower.radio.AviationLight.When.NIGHT, steady, 5, true, false)
                && !com.reiko.signaltower.radio.AviationLight.isLit(com.reiko.signaltower.radio.AviationLight.When.NIGHT, steady, 5, false, true)
                && com.reiko.signaltower.radio.AviationLight.isLit(com.reiko.signaltower.radio.AviationLight.When.ALWAYS, blink, 5, false, false)
                && !com.reiko.signaltower.radio.AviationLight.isLit(com.reiko.signaltower.radio.AviationLight.When.ALWAYS, blink, 25, false, false)
                && !com.reiko.signaltower.radio.AviationLight.isLit(com.reiko.signaltower.radio.AviationLight.When.SENDING, steady, 5, true, false)
                && com.reiko.signaltower.radio.AviationLight.isLit(com.reiko.signaltower.radio.AviationLight.When.SENDING, steady, 5, false, true), "When it's lit");
        check(keyOf(com.reiko.signaltower.block.AviationBeaconBlock.describe(floor)).endsWith("beacon_text_blink"), "Describes itself");

        helper.startSequence()
                .thenExecute(() -> {
                    check(floor.canSurvive(level, absTop), "On top of the transmitter");
                    check(onMast.canSurvive(level, absSide), "On the mast's side");
                    check(plain.canSurvive(level, absStone), "On a stone wall");
                    check(!floor.canSurvive(level, helper.absolutePos(new BlockPos(4, 4, 4))), "Not in the air: " + helper.getBlockState(new BlockPos(4, 3, 4)));
                    check(absTx.equals(com.reiko.signaltower.block.AviationBeaconBlock.towerOf(level, absTop, helper.getBlockState(top))), "Top: its tower");
                    check(absTx.equals(com.reiko.signaltower.block.AviationBeaconBlock.towerOf(level, absSide, helper.getBlockState(side))), "Side: the tower on top of the mast");
                    check(com.reiko.signaltower.block.AviationBeaconBlock.towerOf(level, absStone, helper.getBlockState(onStone)) == null, "A wall isn't a tower");
                    check(!com.reiko.signaltower.block.AviationBeaconBlock.isActive(level, absSide, helper.getBlockState(side)), "Dark while its tower is silent");
                    check(com.reiko.signaltower.block.AviationBeaconBlock.isActive(level, absTop, helper.getBlockState(top))
                            == com.reiko.signaltower.radio.AviationLight.isDark(level), "At night: by the time and weather");
                    helper.setBlock(input, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(com.reiko.signaltower.block.AviationBeaconBlock.isActive(level, absSide, helper.getBlockState(side)),
                        "On while its tower sends"))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absSide);
                    check(info != null && info.getString("beacon_tower").equals("radio_transmitter") && info.getInt("beacon_frequency") == 4251
                            && info.getInt("beacon_above") == 2 && info.getBoolean("beacon_sending") && info.getBoolean("beacon_lit"), "Jade data: " + info);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(side), info));
                    check(keys.contains("beacon_text_blink") && keys.contains("beacon_on") && keys.contains("beacon_tower_sending"), "Jade: " + keys);
                    CompoundTag none = com.reiko.signaltower.compat.RadioInfo.read(level, absStone);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(onStone), none)).contains("beacon_no_tower"), "Jade: no tower");

                    var settings = com.reiko.signaltower.network.ConfigureBeaconPayload.of(absTop, com.reiko.signaltower.radio.AviationLight.When.ALWAYS,
                            flash, com.reiko.signaltower.radio.AviationLight.LightColor.WHITE);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.ConfigureBeaconPayload.STREAM_CODEC.encode(buffer, settings);
                    check(com.reiko.signaltower.network.ConfigureBeaconPayload.STREAM_CODEC.decode(buffer).equals(settings), "Settings packet round trip");
                    check(com.reiko.signaltower.network.ConfigureBeaconPayload.apply(far, settings) == null, "Out of reach");
                    Component set = com.reiko.signaltower.network.ConfigureBeaconPayload.apply(dev, settings);
                    BlockState now = helper.getBlockState(top);
                    check(set != null && keyOf(set).endsWith("beacon_set")
                            && now.getValue(com.reiko.signaltower.block.AviationBeaconBlock.WHEN) == com.reiko.signaltower.radio.AviationLight.When.ALWAYS
                            && now.getValue(com.reiko.signaltower.block.AviationBeaconBlock.PATTERN) == flash
                            && now.getValue(com.reiko.signaltower.block.AviationBeaconBlock.COLOR) == com.reiko.signaltower.radio.AviationLight.LightColor.WHITE
                            && now.getValue(com.reiko.signaltower.block.AviationBeaconBlock.FACE) == AttachFace.FLOOR, "Set: " + set);
                    check(com.reiko.signaltower.block.AviationBeaconBlock.isActive(level, absTop, now), "Always on");
                    helper.setBlock(input, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(!com.reiko.signaltower.block.AviationBeaconBlock.isActive(level, absSide, helper.getBlockState(side)),
                        "Dark again when its tower stops"))
                .thenExecute(() -> {
                    level.destroyBlock(absTop, true);
                    helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    check(helper.getBlockState(side).isAir(), "Pops off with its mast");
                    List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                            .filter(stack -> stack.is(ModRegistry.AVIATION_BEACON_ITEM.get())).toList();
                    check(drops.stream().anyMatch(stack -> stack.get(DataComponents.BLOCK_STATE) != null
                            && stack.get(DataComponents.BLOCK_STATE).get(com.reiko.signaltower.block.AviationBeaconBlock.COLOR)
                                == com.reiko.signaltower.radio.AviationLight.LightColor.WHITE
                            && stack.get(DataComponents.BLOCK_STATE).get(com.reiko.signaltower.block.AviationBeaconBlock.PATTERN) == flash), "Dropped with its settings");
                    check(drops.stream().anyMatch(stack -> stack.get(DataComponents.BLOCK_STATE) != null
                            && stack.get(DataComponents.BLOCK_STATE).get(com.reiko.signaltower.block.AviationBeaconBlock.WHEN)
                                == com.reiko.signaltower.radio.AviationLight.When.SENDING), "The side one too");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.44.0: signal recorder

    /** Rises (off to on) in {@code samples}, counting from off before the first. */
    private static int rises(List<Integer> samples) {
        int count = 0;
        int previous = 0;
        for (int sample : samples) {
            if (sample > 0 && previous == 0) {
                count++;
            }
            previous = sample;
        }
        return count;
    }

    /**
     * The Radio Signal Recorder: with nothing to record from, or the same frequency both ways, it
     * says so. Record waits for 4201 to turn on, then records two 10-tick pulses to the tick until
     * Stop; Play sends them on 4202 once (the log names the recorder; Jade knows the tape). In Once
     * mode a redstone pulse plays it once; in Loop mode it plays over and over while powered and
     * stops when the power goes. The packets go both ways (round trips); a stranger can't change
     * it on someone else's private frequency, nor anyone out of reach; it's saved, and the item
     * keeps the recording.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 700)
    public static void signalRecorder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos src = new BlockPos(1, 1, 1);
        BlockPos srcIn = new BlockPos(0, 1, 1);
        BlockPos rec = new BlockPos(5, 1, 1);
        BlockPos recIn = new BlockPos(5, 1, 2);
        BlockPos rx = new BlockPos(9, 1, 1);
        place(helper, src, ModRegistry.RADIO_TRANSMITTER.get(), 4201);
        place(helper, rec, ModRegistry.RADIO_SIGNAL_RECORDER.get(), 4202);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4202);
        com.reiko.signaltower.blockentity.SignalRecorderBlockEntity recorder = helper.getBlockEntity(rec);
        BlockPos abs = helper.absolutePos(rec);
        SignalNetwork network = SignalNetwork.get(level);
        Player dev = namedPlayer(helper, new BlockPos(5, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        List<Integer> samples = new java.util.ArrayList<>();
        var idle = com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.Phase.IDLE;
        var waiting = com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.Phase.WAITING;
        var recording = com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.Phase.RECORDING;
        int record = com.reiko.signaltower.network.ConfigureRecorderPayload.RECORD;
        int save = com.reiko.signaltower.network.ConfigureRecorderPayload.SAVE;

        // The tape's arithmetic.
        int[] tape = {15, 10 << 4, 20 << 4 | 7};
        check(com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.strengthAt(tape, 0) == 15
                && com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.strengthAt(tape, 9) == 15
                && com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.strengthAt(tape, 10) == 0
                && com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.strengthAt(tape, 25) == 7, "Strength at a tick");
        check(java.util.Arrays.equals(com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.clean(List.of(20 << 4 | 7, 15, 700 << 4 | 3), 600),
                new int[]{15, 20 << 4 | 7}), "Changes cleaned up");
        check(com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.seconds(104).equals("5.2")
                && com.reiko.signaltower.blockentity.SignalRecorderBlockEntity.seconds(20).equals("1"), "Seconds");

        var sequence = helper.startSequence()
                .thenExecute(() -> {
                    Component nothing = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 0, false, record));
                    check(nothing != null && keyOf(nothing).endsWith("recorder_no_listen") && recorder.getPhase() == idle, "Nothing to record from: " + nothing);
                    Component same = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4202, false, save));
                    check(same != null && keyOf(same).endsWith("recorder_same"), "Same frequency both ways: " + same);
                    Component wait = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, false, record));
                    check(wait != null && keyOf(wait).endsWith("recorder_waiting") && recorder.getPhase() == waiting
                            && recorder.getListenFrequency() == 4201 && dev.getUUID().equals(recorder.getTunedBy()), "Record: " + wait);
                })
                .thenIdle(5)
                .thenExecute(() -> check(recorder.getPhase() == waiting && recorder.getLength() == 0, "Still waiting: nothing heard yet"));
        pulseInput(helper, sequence, srcIn, 10, 10);
        pulseInput(helper, sequence, srcIn, 10, 6);
        sequence.thenExecute(() -> {
                    check(recorder.getPhase() == recording, "Recording: " + recorder.getPhase());
                    Component stopped = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, false,
                                    com.reiko.signaltower.network.ConfigureRecorderPayload.STOP));
                    check(stopped != null && keyOf(stopped).endsWith("recorder_recorded") && recorder.getPhase() == idle, "Stop: " + stopped);
                    int[] changes = recorder.getChanges();
                    String seen = java.util.Arrays.toString(java.util.Arrays.stream(changes).mapToObj(c -> (c >> 4) + ":" + (c & 15)).toArray());
                    check(changes.length == 4 && (changes[0] & 15) == 15 && (changes[1] & 15) == 0 && (changes[2] & 15) == 15
                            && (changes[3] & 15) == 0, "On, off, on, off: " + seen);
                    check(changes[0] >> 4 == 0 && Math.abs((changes[1] >> 4) - 10) <= 1 && Math.abs((changes[2] >> 4) - 20) <= 1
                            && Math.abs((changes[3] >> 4) - 30) <= 1, "To the tick: " + seen);
                    check(Math.abs(recorder.getLength() - 36) <= 2, "Length: " + recorder.getLength());
                    samples.clear();
                    Component play = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, false,
                                    com.reiko.signaltower.network.ConfigureRecorderPayload.PLAY));
                    check(play != null && keyOf(play).endsWith("recorder_playing"), "Play: " + play);
                })
                .thenExecuteFor(46, () -> samples.add(outputAt(helper, rx)))
                .thenExecute(() -> {
                    check(rises(samples) == 2 && samples.get(samples.size() - 1) == 0 && recorder.getPhase() == idle, "Played once: " + samples);
                    int first = samples.indexOf(15);
                    int second = -1;
                    for (int i = first + 1; i < samples.size(); i++) {
                        if (samples.get(i) == 15 && samples.get(i - 1) == 0) {
                            second = i;
                            break;
                        }
                    }
                    check(first >= 0 && Math.abs(second - first - 20) <= 1, "Its rhythm: " + samples);
                    long on = samples.stream().filter(v -> v == 15).count();
                    check(on >= 18 && on <= 22, "As long as it was: " + samples);
                    var history = network.history(4202);
                    check(!history.isEmpty() && history.get(history.size() - 1).cause().kind() == SignalNetwork.CauseKind.RECORDER
                            && abs.equals(history.get(history.size() - 1).cause().pos()), "The log names the recorder");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rec), info));
                    check(keys.contains("recorder_tape") && keys.contains("recorder_records_from"), "Jade: " + keys);
                    var status = com.reiko.signaltower.network.RecorderStatusPayload.answer(dev, abs);
                    check(status != null && status.length() == recorder.getLength() && status.changes().size() == 4 && status.phaseValue() == idle
                            && !status.deaf() && !status.silenced(), "Status: " + status);
                    check(com.reiko.signaltower.network.RecorderStatusPayload.answer(far, abs) == null, "No status out of reach");
                    var buffer = Unpooled.buffer();
                    var open = com.reiko.signaltower.network.OpenRecorderScreenPayload.of(level, recorder);
                    com.reiko.signaltower.network.OpenRecorderScreenPayload.STREAM_CODEC.encode(buffer, open);
                    check(com.reiko.signaltower.network.OpenRecorderScreenPayload.STREAM_CODEC.decode(buffer).equals(open), "Screen packet round trip");
                    buffer.clear();
                    var settings = new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, true, save);
                    com.reiko.signaltower.network.ConfigureRecorderPayload.STREAM_CODEC.encode(buffer, settings);
                    check(com.reiko.signaltower.network.ConfigureRecorderPayload.STREAM_CODEC.decode(buffer).equals(settings), "Settings packet round trip");
                    samples.clear();
                    helper.setBlock(recIn, Blocks.REDSTONE_BLOCK); // Once: a pulse plays it once
                })
                .thenExecuteFor(60, () -> samples.add(outputAt(helper, rx)))
                .thenExecute(() -> {
                    check(rises(samples) == 2 && samples.stream().skip(45).allMatch(v -> v == 0), "A pulse plays it once: " + samples);
                    helper.setBlock(recIn, Blocks.AIR);
                    Component set = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, true, save));
                    check(set != null && keyOf(set).endsWith("recorder_set") && recorder.isLoop(), "Loop: " + set);
                    samples.clear();
                    helper.setBlock(recIn, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteFor(100, () -> samples.add(outputAt(helper, rx)))
                .thenExecute(() -> {
                    check(rises(samples) >= 5, "Loops while powered: " + samples);
                    helper.setBlock(recIn, Blocks.AIR);
                    samples.clear();
                })
                .thenExecuteFor(30, () -> samples.add(outputAt(helper, rx)))
                .thenExecute(() -> {
                    check(samples.stream().skip(3).allMatch(v -> v == 0) && recorder.getPhase() == idle, "Stops when the power goes: " + samples);
                    var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
                    claims.put(4202, UUID.randomUUID(), "Owner", java.util.Map.of());
                    try {
                        Component refused = com.reiko.signaltower.network.ConfigureRecorderPayload.apply(dev,
                                new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, false, save));
                        check(refused != null && keyOf(refused).endsWith("private_refused_block") && recorder.isLoop(),
                                "A stranger can't change it on someone else's private frequency");
                    } finally {
                        claims.remove(4202);
                    }
                    check(com.reiko.signaltower.network.ConfigureRecorderPayload.apply(far,
                            new com.reiko.signaltower.network.ConfigureRecorderPayload(abs, 4202, 4201, false, save)) == null && recorder.isLoop(), "Out of reach");
                    CompoundTag saved = recorder.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.SignalRecorderBlockEntity(abs, helper.getBlockState(rec));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.getListenFrequency() == 4201 && copy.isLoop() && copy.getLength() == recorder.getLength()
                            && java.util.Arrays.equals(copy.getChanges(), recorder.getChanges()) && copy.getFrequency() == 4202, "Saved");
                    level.destroyBlock(abs, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_SIGNAL_RECORDER_ITEM.get())
                                && stack.get(ModRegistry.RECORDER_TAPE.get()) != null
                                && stack.get(ModRegistry.RECORDER_TAPE.get()).listen() == 4201
                                && stack.get(ModRegistry.RECORDER_TAPE.get()).loop()
                                && stack.get(ModRegistry.RECORDER_TAPE.get()).changes().size() == 4
                                && Integer.valueOf(4202).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Dropped with its recording"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.45.0: antennas

    /**
     * Antennas: a Diamond Antenna fitted to a transmitter triples its reach (in the network too) and
     * uses up one antenna; fitting it again does nothing; a Netherite one replaces it (×4) and gives
     * the diamond one back; taking it off (also by sneaking with an empty hand) gives it back and
     * restores the reach. A Gold Antenna doubles a repeater's. Only full-size tower tops take one;
     * on someone else's private frequency a stranger is refused; Jade shows it; and broken, the
     * transmitter keeps its antenna on the item.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void antennaTiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos rep = new BlockPos(6, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4361);
        place(helper, rep, ModRegistry.RADIO_REPEATER.get(), 4362);
        BlockPos absTx = helper.absolutePos(tx);
        BlockPos absRep = helper.absolutePos(rep);
        SignalNetwork network = SignalNetwork.get(level);
        Player dev = namedPlayer(helper, new BlockPos(4, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        var diamond = com.reiko.signaltower.radio.AntennaTier.DIAMOND;
        var none = com.reiko.signaltower.radio.AntennaTier.NONE;

        check(com.reiko.signaltower.radio.AntennaTier.IRON.multiplierText().equals("×1.5") && diamond.multiplierText().equals("×3")
                && com.reiko.signaltower.radio.AntennaTier.NETHERITE.multiplier() == 4.0, "Multipliers");
        check(com.reiko.signaltower.radio.AntennaTier.fits(ModRegistry.RADIO_TIMER_TRANSMITTER.get().defaultBlockState())
                && com.reiko.signaltower.radio.AntennaTier.fits(ModRegistry.RADIO_SIGNAL_RECORDER.get().defaultBlockState())
                && com.reiko.signaltower.radio.AntennaTier.fits(ModRegistry.RADIO_CONVERTER.get().defaultBlockState())
                && !com.reiko.signaltower.radio.AntennaTier.fits(ModRegistry.COMPACT_RADIO_TRANSMITTER.get().defaultBlockState())
                && !com.reiko.signaltower.radio.AntennaTier.fits(ModRegistry.RADIO_MOTION_SENSOR.get().defaultBlockState())
                && !com.reiko.signaltower.radio.AntennaTier.fits(ModRegistry.RADIO_RECEIVER.get().defaultBlockState()), "Only tower tops take one");

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    int basic = RadioHelper.reachAt(level, absTx);
                    check(network.transmittersOn(level, 4361).get(absTx).reach() == basic, "No antenna: its usual reach " + basic);
                    ItemStack diamonds = new ItemStack(ModRegistry.DIAMOND_ANTENNA.get(), 2);
                    Component fitted = com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, diamond, diamonds);
                    check(fitted != null && keyOf(fitted).endsWith("antenna_fitted") && diamonds.getCount() == 1
                            && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == diamond, "Fitted: " + fitted);
                    int tripled = (int) Math.round(basic * 3.0);
                    check(network.transmittersOn(level, 4361).get(absTx).reach() == tripled
                            && com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachOf(level, absTx, helper.getBlockState(tx)) == tripled,
                            "Three times the reach: " + network.transmittersOn(level, 4361).get(absTx).reach());
                    Component again = com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, diamond, diamonds);
                    check(again != null && keyOf(again).endsWith("antenna_already") && diamonds.getCount() == 1, "Already has one");
                    ItemStack netherite = new ItemStack(ModRegistry.NETHERITE_ANTENNA.get());
                    com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, com.reiko.signaltower.radio.AntennaTier.NETHERITE, netherite);
                    check(netherite.isEmpty() && dev.getInventory().countItem(ModRegistry.DIAMOND_ANTENNA.get()) == 1
                            && network.transmittersOn(level, 4361).get(absTx).reach() == (int) Math.round(basic * 4.0), "Netherite replaces it, diamond back");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTx);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("antenna"), "Jade shows it");
                    Component off = com.reiko.signaltower.item.AntennaItem.takeOff(dev, absTx);
                    check(off != null && keyOf(off).endsWith("antenna_taken_off") && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == none
                            && dev.getInventory().countItem(ModRegistry.NETHERITE_ANTENNA.get()) == 1
                            && network.transmittersOn(level, 4361).get(absTx).reach() == basic, "Taken off: " + off);
                    check(com.reiko.signaltower.item.AntennaItem.takeOff(dev, absTx) == null, "Nothing to take off");

                    int repBasic = RadioHelper.reachAt(level, absRep);
                    com.reiko.signaltower.item.AntennaItem.fit(dev, absRep, com.reiko.signaltower.radio.AntennaTier.GOLD, new ItemStack(ModRegistry.GOLD_ANTENNA.get()));
                    check(network.relaysOn(level, 4362).get(absRep).reach() == (int) Math.round(repBasic * 2.0), "A gold one doubles a repeater's reach");

                    com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, com.reiko.signaltower.radio.AntennaTier.IRON, new ItemStack(ModRegistry.IRON_ANTENNA.get()));
                    dev.setShiftKeyDown(true);
                    check(com.reiko.signaltower.item.AntennaItem.takeOffBySneaking(helper.getBlockState(tx), level, absTx, dev)
                            && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == none
                            && dev.getInventory().countItem(ModRegistry.IRON_ANTENNA.get()) == 1, "Sneak + empty hand takes it off");
                    check(!com.reiko.signaltower.item.AntennaItem.takeOffBySneaking(helper.getBlockState(tx), level, absTx, dev), "Without one, a sneak-click is a normal click");
                    dev.setShiftKeyDown(false);

                    var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
                    claims.put(4361, UUID.randomUUID(), "Owner", java.util.Map.of());
                    try {
                        ItemStack iron = new ItemStack(ModRegistry.IRON_ANTENNA.get());
                        Component refused = com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, com.reiko.signaltower.radio.AntennaTier.IRON, iron);
                        check(refused != null && keyOf(refused).endsWith("private_refused_block") && iron.getCount() == 1
                                && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == none, "A stranger can't on someone else's private frequency");
                    } finally {
                        claims.remove(4361);
                    }
                    com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, diamond, new ItemStack(ModRegistry.DIAMOND_ANTENNA.get()));
                    level.destroyBlock(absTx, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_TRANSMITTER_ITEM.get())
                                && com.reiko.signaltower.radio.AntennaTier.of(stack) == diamond
                                && Integer.valueOf(4361).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Dropped with its antenna"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.45.0: radio lamp

    /**
     * The Radio Lamp: a transmitter fed through six blocks of dust sends 10, and the lamp gives off
     * light 10 (and outputs no redstone: a redstone lamp beside it stays dark); off when the signal
     * goes. A dye recolors it (using one dye); its tint is darker off than on; Jade shows its light
     * and color; broken, it keeps its color and frequency on the item.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void radioLamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = new BlockPos(0, 1, 1);
        BlockPos tx = new BlockPos(7, 1, 1);
        BlockPos lamp = new BlockPos(10, 1, 2);
        BlockPos plainLamp = new BlockPos(11, 1, 2);
        for (int x = 1; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 0, 1), Blocks.STONE);
        }
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4351);
        place(helper, lamp, ModRegistry.RADIO_LAMP.get(), 4351);
        helper.setBlock(new BlockPos(11, 0, 2), Blocks.STONE);
        helper.setBlock(plainLamp, Blocks.REDSTONE_LAMP);
        BlockPos absLamp = helper.absolutePos(lamp);
        Player dev = namedPlayer(helper, new BlockPos(10, 1, 1), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());

        check(com.reiko.signaltower.block.RadioLampBlock.tint(DyeColor.RED, 0) != com.reiko.signaltower.block.RadioLampBlock.tint(DyeColor.RED, 15)
                && (com.reiko.signaltower.block.RadioLampBlock.tint(DyeColor.RED, 0) >> 16 & 255)
                    < (com.reiko.signaltower.block.RadioLampBlock.tint(DyeColor.RED, 15) >> 16 & 255), "Darker off than on");

        helper.startSequence()
                .thenExecute(() -> {
                    for (int x = 1; x <= 6; x++) {
                        helper.setBlock(new BlockPos(x, 1, 1), Blocks.REDSTONE_WIRE);
                    }
                    helper.setBlock(source, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(lamp).getValue(RadioReceiverBlock.POWER) > 0, "It lights"))
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockState state = helper.getBlockState(lamp);
                    int sent = com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.inputOf(level, helper.absolutePos(tx), helper.getBlockState(tx));
                    check(sent == 10 && state.getValue(RadioReceiverBlock.POWER) == 10 && state.getLightEmission() == 10,
                            "As bright as it hears: sent " + sent + ", light " + state.getLightEmission());
                    check(!helper.getBlockState(plainLamp).getValue(RedstoneLampBlock.LIT), "No redstone out");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absLamp);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(state, info));
                    check(keys.contains("lamp_light") && keys.contains("lamp_color"), "Jade: " + keys);
                    dev.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE, 2));
                    state.useItemOn(dev.getMainHandItem(), level, dev, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(absLamp), Direction.UP, absLamp, false));
                    check(helper.getBlockState(lamp).getValue(com.reiko.signaltower.block.RadioLampBlock.COLOR) == DyeColor.RED
                            && dev.getMainHandItem().getCount() == 1, "Dyed red with one dye");
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(lamp).getValue(RadioReceiverBlock.POWER) == 0
                        && helper.getBlockState(lamp).getLightEmission() == 0, "Dark when the signal goes"))
                .thenExecute(() -> level.destroyBlock(absLamp, true))
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_LAMP_ITEM.get())
                                && com.reiko.signaltower.block.RadioLampBlock.colorOf(stack) == DyeColor.RED
                                && Integer.valueOf(4351).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Dropped red, with its frequency: "
                        + helper.getEntities(EntityType.ITEM).stream().map(e -> e.getItem() + " " + e.getItem().getComponentsPatch()).toList()))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.46.0: weather and terrain

    /**
     * Weather: rain takes 15% off a tower's reach and a thunderstorm 30% (the defaults), measured
     * where it stands; it rains on plains, never in a desert. A repeater's and a jammer's reach shrink
     * too; a compact transmitter's doesn't. Changing the weather bumps every frequency so receivers
     * re-check at once.
     */
    @GameTest(template = PLATFORM)
    public static void weatherShortensReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var rain = com.reiko.signaltower.radio.Weather.RAIN;
        var thunder = com.reiko.signaltower.radio.Weather.THUNDER;
        check(com.reiko.signaltower.radio.Weather.CLEAR.apply(100) == 100 && rain.apply(100) == 85 && thunder.apply(100) == 70
                && rain.loss() == 15 && thunder.loss() == 30, "15% in rain, 30% in a storm");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        BlockPos somewhere = helper.absolutePos(BlockPos.ZERO);
        check(com.reiko.signaltower.radio.Weather.wetBiome(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS), somewhere)
                && !com.reiko.signaltower.radio.Weather.wetBiome(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT), somewhere)
                && !com.reiko.signaltower.radio.Weather.wetBiome(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES), somewhere),
                "It rains on plains, not in a desert or the Nether");

        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos far = somewhere.offset(0, 0, 30000); // not loaded: no hills, no block checks
        network.updateTransmitter(far, 4371, 15, 100);
        network.updateRepeater(far.south(400), 4372, 100);
        network.updateTransmitter(far.south(400).west(50), 4372, 15, 100);
        network.updateTransmitter(far.north(400), 4373, 15, 16, null, true);
        check(network.listen(level, 4371, far.east(90)).power() == 15, "Clear: heard at 90");
        long before = network.revision(4371);
        network.forceWeather(rain);
        check(network.revision(4371) > before, "A change of weather bumps the frequency");
        check(network.weatherAt(level, far) == rain && network.reachNow(level, far, 100) == 85, "Rain where it stands");
        check(network.listen(level, 4371, far.east(90)).power() == 0 && network.listen(level, 4371, far.east(85)).power() == 15,
                "Rain: 85 blocks, not 90");
        check(network.edgeMargin(level, 4371, far.east(80)) == 5, "The meter's edge moves in too");
        network.forceWeather(thunder);
        check(network.listen(level, 4371, far.east(71)).power() == 0 && network.listen(level, 4371, far.east(70)).power() == 15,
                "Thunderstorm: 70 blocks");
        check(network.listen(level, 4372, far.south(400).east(70)).power() == 15 && network.listen(level, 4372, far.south(400).east(71)).power() == 0,
                "A repeater's reach shrinks too");
        check(network.listen(level, 4373, far.north(400).east(16)).power() == 15, "A compact transmitter doesn't mind the weather");
        network.updateJammer(far.east(40), 4371, 15, 40, null);
        check(network.listen(level, 4371, far.east(40 - 28)).power() == 0 && network.listen(level, 4371, far.east(40 - 29)).power() == 15,
                "A jammer's reach shrinks too (40 to 28)");
        network.forceWeather(null);
        check(network.weather(level) == com.reiko.signaltower.radio.Weather.of(level), "Back to the level's weather");
        helper.succeed();
    }

    /**
     * Terrain: a 32-block hill between a transmitter and a receiver takes 4 off (1 per 8 blocks of
     * ground, the default): the receiver outputs 11 and Jade says why. A repeater on top of the hill
     * gets round it: 15 again. A roof over the line (the test's own barrier ceiling) doesn't count;
     * the first and last 8 blocks don't either.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void hillsWeakenSignals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos rx = new BlockPos(82, 1, 1);
        BlockPos onHill = new BlockPos(45, 9, 1);
        for (int x = 30; x <= 61; x++) {
            for (int y = 0; y <= 5; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
            }
        }
        // A wall right by the receiver (inside the last 8 blocks): it doesn't count.
        for (int y = 0; y <= 3; y++) {
            helper.setBlock(new BlockPos(78, y, 1), Blocks.STONE);
        }
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4381);
        helper.setBlock(tx, helper.getBlockState(tx).setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY,
                com.reiko.signaltower.radio.AntennaTier.NETHERITE)); // reach 128 or more: the receiver is 80 away
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4381);
        BlockPos absTx = helper.absolutePos(tx);
        BlockPos absRx = helper.absolutePos(rx);
        SignalNetwork network = SignalNetwork.get(level);
        check(network.hillLoss(level, absTx, absRx) == 4, "32 blocks of hill: 4 off, got " + network.hillLoss(level, absTx, absRx));
        check(network.hillLoss(level, absTx.above(), absRx.above()) == 4, "Still under the hill one block up");
        check(network.hillLoss(level, absTx, absTx.east(15)) == 0, "Too short to count");
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 11))
                .thenIdle(3)
                .thenExecute(() -> {
                    assertOutput(helper, rx, 11);
                    var heard = network.listen(level, 4381, absRx);
                    check(heard.power() == 11 && heard.hills() == 4 && heard.jammed() == 0, "Heard 11, 4 lost to the hill: " + heard);
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absRx);
                    List<Component> lines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info);
                    check(lineKeys(lines).contains("hills") && lines.stream().anyMatch(line -> line.getString().contains("4")), "Jade: " + lineKeys(lines));
                    Player dev = namedPlayer(helper, rx.above(), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
                    var reading = com.reiko.signaltower.item.SignalMeterItem.read(level, dev, 4381);
                    check(reading.hills() == 4 && com.reiko.signaltower.network.MeterReadingPayload.of(reading).hills() == 4, "The meter too: " + reading);
                    // A repeater on top of the hill.
                    place(helper, onHill, ModRegistry.RADIO_REPEATER.get(), 4381);
                    helper.setBlock(onHill, helper.getBlockState(onHill).setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY,
                            com.reiko.signaltower.radio.AntennaTier.NETHERITE));
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> {
                    check(network.hillLoss(level, absTx, helper.absolutePos(onHill)) == 0
                            && network.hillLoss(level, helper.absolutePos(onHill), absRx) == 0, "Over the hill both ways");
                    check(network.listen(level, 4381, absRx).hills() == 0, "Through the repeater, nothing lost");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.46.0: radio jammer

    /**
     * The Radio Jammer, in the network: it takes its strength off what's heard on its frequency
     * inside its reach (5 off 12 leaves 7; 15 silences it), not outside; a repeater inside it hears
     * nothing to pass on; switched off, it's gone. Saved and loaded with the world.
     */
    @GameTest(template = PLATFORM)
    public static void jammerInTheNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(0, 0, -30000);
        network.updateTransmitter(far, 4392, 12, 200);
        network.updateJammer(far.east(100), 4392, 5, 40, null);
        var partly = network.listen(level, 4392, far.east(90));
        check(partly.power() == 7 && partly.jammed() == 5, "5 off 12: " + partly);
        check(network.listen(level, 4392, far.east(150)).power() == 12 && network.jamming(level, 4392, far.east(150)) == 0, "Outside its reach");
        check(network.listen(level, 4393, far.east(90)).jammed() == 0, "Only its own frequency");
        network.updateJammer(far.east(100), 4392, 15, 40, null);
        var silenced = network.listen(level, 4392, far.east(90));
        check(silenced.power() == 0 && silenced.jammed() == 15 && silenced.transmittersInReach() == 1, "15 silences it: " + silenced);
        check(network.scanAt(level, far.east(90)).stream().anyMatch(heard -> heard.frequency() == 4392 && heard.power() == 0), "The scanner hears nothing either");
        network.updateRepeater(far.east(110), 4392, 200);
        check(network.repeaterPower(level, far.east(110)) == 0 && network.listen(level, 4392, far.east(300)).power() == 0,
                "A repeater inside it has nothing to pass on");
        CompoundTag saved = network.save(new CompoundTag(), level.registryAccess());
        SignalNetwork loaded = SignalNetwork.load(saved, level.registryAccess()).attachTo(level);
        check(network.jammerAt(far.east(100)).equals(loaded.jammerAt(far.east(100))) && loaded.listen(level, 4392, far.east(90)).power() == 0,
                "Saved and loaded");
        network.updateJammer(far.east(100), 4392, 0, 40, null);
        check(network.listen(level, 4392, far.east(90)).power() == 12 && network.listen(level, 4392, far.east(300)).power() == 12,
                "Off: heard again, and passed on");
        network.removeJammer(far.east(100));
        check(network.jammerAt(far.east(100)) == null, "Removed");
        helper.succeed();
    }

    /**
     * A placed Radio Jammer, powered by redstone, silences a receiver near it on its frequency
     * (Jade says "Jammed", and the jammer what it jams), not one far away. On someone else's
     * private frequency a stranger's jammer does nothing (and shows in the owner's audit); broken,
     * it stops and keeps its frequency on the item.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void radioJammer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos nearRx = new BlockPos(14, 1, 1);
        BlockPos farRx = new BlockPos(110, 1, 1);
        BlockPos jammer = new BlockPos(20, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4391);
        helper.setBlock(tx, helper.getBlockState(tx).setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY,
                com.reiko.signaltower.radio.AntennaTier.NETHERITE));
        place(helper, nearRx, ModRegistry.RADIO_RECEIVER.get(), 4391);
        place(helper, farRx, ModRegistry.RADIO_RECEIVER.get(), 4391);
        place(helper, jammer, ModRegistry.RADIO_JAMMER.get(), 4391);
        BlockPos absJammer = helper.absolutePos(jammer);
        BlockPos absNear = helper.absolutePos(nearRx);
        SignalNetwork network = SignalNetwork.get(level);
        UUID stranger = UUID.randomUUID();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        check(com.reiko.signaltower.blockentity.RadioJammerBlockEntity.jamReach(level, absJammer, helper.getBlockState(jammer))
                == (int) Math.round(RadioHelper.reachAt(level, absJammer) * 0.5), "Half a transmitter's reach");
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    assertOutput(helper, nearRx, 15);
                    assertOutput(helper, farRx, 15);
                })
                .thenExecute(() -> {
                    check(!helper.getBlockState(jammer).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Not jamming yet");
                    helper.setBlock(jammer.south(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> assertOutput(helper, nearRx, 0))
                .thenIdle(3)
                .thenExecute(() -> {
                    assertOutput(helper, farRx, 15);
                    check(helper.getBlockState(jammer).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Lit while jamming");
                    check(network.jamming(level, 4391, absNear) == 15 && network.listen(level, 4391, absNear).jammed() == 15, "Jammed at 15");
                    List<String> rxLines = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(nearRx),
                            com.reiko.signaltower.compat.RadioInfo.read(level, absNear)));
                    check(rxLines.contains("jammed"), "Jade on the receiver: " + rxLines);
                    List<String> jammerLines = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(jammer),
                            com.reiko.signaltower.compat.RadioInfo.read(level, absJammer)));
                    check(jammerLines.contains("jams") && jammerLines.contains("jamming") && jammerLines.contains("reach"), "Jade on the jammer: " + jammerLines);
                    Player dev = namedPlayer(helper, nearRx.above(), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
                    var reading = com.reiko.signaltower.item.SignalMeterItem.read(level, dev, 4391);
                    check(reading.strength() == 0 && reading.jammed() == 15
                            && com.reiko.signaltower.network.MeterReadingPayload.of(reading).jammed() == 15, "The meter: " + reading);
                    check(network.towerFrequencyAt(absJammer).orElse(-1) == 4391 && network.transmittersOn(level, 4391).size() == 1,
                            "It sends nothing itself");
                    // Someone else's private frequency: a stranger's jammer does nothing.
                    claims.put(4391, UUID.randomUUID(), "Owner", java.util.Map.of());
                    com.reiko.signaltower.blockentity.RadioJammerBlockEntity be = helper.getBlockEntity(jammer);
                    be.setTunedBy(stranger);
                })
                .thenWaitUntil(() -> assertOutput(helper, nearRx, 15))
                .thenExecute(() -> {
                    try {
                        check(network.isSilenced(level, absJammer) && com.reiko.signaltower.block.RadioBlock.silencedNotice(level, absJammer) != null,
                                "Kept quiet, and says so");
                        var rows = com.reiko.signaltower.radio.FrequencyAudit.audit(level.getServer(), 4391, null, 512);
                        check(rows.stream().anyMatch(row -> row.kind() == com.reiko.signaltower.radio.FrequencyAudit.Kind.JAMMER
                                && row.status() == com.reiko.signaltower.radio.FrequencyAudit.Status.SILENT), "In the owner's audit: " + rows);
                    } finally {
                        claims.remove(4391);
                    }
                })
                .thenWaitUntil(() -> assertOutput(helper, nearRx, 0))
                .thenExecute(() -> level.destroyBlock(absJammer, true))
                .thenWaitUntil(() -> assertOutput(helper, nearRx, 15))
                .thenWaitUntil(() -> check(network.jammerAt(absJammer) == null && helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_JAMMER_ITEM.get())
                                && Integer.valueOf(4391).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Broken: gone, and dropped with its frequency"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.47.0: receiver height

    /**
     * Receiver height: a receiver higher than a tower hears it as far as a transmitter at the
     * receiver's height would reach (here 4 blocks up covers 30 more blocks), not 3 blocks up;
     * one lower than the tower gains nothing; a compact transmitter keeps its short reach; a repeater
     * up high picks the signal up and passes it on; the meter's edge and the scanner go by it. A
     * real receiver 11 blocks above a transmitter hears it from 98 blocks (beyond its 64), Jade says
     * why, and one at the transmitter's height 98 blocks away hears nothing.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void receiverHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 31000); // not loaded: no hills
        int ground = RadioHelper.reachAt(level, far);
        network.updateTransmitter(far, 4471, 15, ground);
        BlockPos spot = far.east(ground + 30);
        BlockPos high = spot.above(4);
        check(network.listen(level, 4471, spot).power() == 0, "Too far at the tower's height");
        check(RadioHelper.reachAt(level, high) >= ground + 30 && RadioHelper.reachAt(level, spot.above(3)) < ground + 30, "The heights are right");
        var lifted = network.listen(level, 4471, high);
        check(lifted.power() == 15 && lifted.lifted(), "4 blocks up it hears it: " + lifted);
        check(network.listen(level, 4471, spot.above(3)).power() == 0, "3 blocks up isn't enough");
        check(!network.listen(level, 4471, far.east(10).above(4)).lifted(), "Within the tower's own reach it isn't thanks to height");
        check(network.ear(level, high) == RadioHelper.reachAt(level, high), "Its ear: a transmitter's reach there");
        check(network.edgeMargin(level, 4471, high) == RadioHelper.reachAt(level, high) - (ground + 30), "The meter's edge goes by it");
        check(network.scanAt(level, high).stream().anyMatch(heard -> heard.frequency() == 4471 && heard.power() == 15), "So does the scanner");
        network.updateTransmitter(far.north(600).above(10), 4472, 15, 50);
        check(network.listen(level, 4472, far.north(600).east(60)).power() == 0, "Lower than the tower: nothing gained");
        network.updateTransmitter(far.south(600), 4473, 15, 16, null, true);
        check(network.listen(level, 4473, far.south(600).east(20).above(40)).power() == 0, "A compact transmitter keeps its short reach");
        network.updateRepeater(high, 4471, 100);
        check(network.repeaterPower(level, high) == 15 && network.listen(level, 4471, spot.east(90)).power() == 15,
                "A repeater up high picks it up and passes it on");

        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos upRx = new BlockPos(100, 12, 1);
        BlockPos lowRx = new BlockPos(100, 1, 2);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4474);
        place(helper, upRx, ModRegistry.RADIO_RECEIVER.get(), 4474);
        place(helper, lowRx, ModRegistry.RADIO_RECEIVER.get(), 4474);
        check(RadioHelper.reachAt(level, helper.absolutePos(tx)) < 98 && RadioHelper.reachAt(level, helper.absolutePos(upRx)) >= 98,
                "98 blocks: out of the transmitter's reach, in the high receiver's");
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(tx.north(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, upRx, 15))
                .thenIdle(3)
                .thenExecute(() -> {
                    assertOutput(helper, lowRx, 0);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(upRx),
                            com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(upRx))));
                    check(keys.contains("lifted"), "Jade says it's thanks to its height: " + keys);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.47.0: lightning

    /**
     * Lightning: the chance grows with height (none below sea level). Struck, a transmitter on masts
     * goes quiet for 5 seconds (its receiver goes off, its light out, Jade counts down) and comes
     * back by itself. A Lightning Rod next to its top takes the next hit (the rod gives its pulse)
     * and the tower stays on; one 3 blocks away is too far. A bolt that isn't the mod's (here one
     * spawned on top of it) knocks it out too. Compact transmitters can't be struck; repeaters can.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void lightningStrikesTowers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = new BlockPos(10, 4, 1);
        BlockPos rx = new BlockPos(20, 1, 1);
        BlockPos rod = new BlockPos(11, 4, 1);
        for (int y = 0; y < 4; y++) {
            helper.setBlock(new BlockPos(10, y, 1), ModRegistry.RADIO_MAST.get());
        }
        place(helper, top, ModRegistry.RADIO_TRANSMITTER.get(), 4481);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4481);
        place(helper, new BlockPos(30, 1, 1), ModRegistry.COMPACT_RADIO_TRANSMITTER.get(), 4482);
        place(helper, new BlockPos(32, 1, 1), ModRegistry.RADIO_REPEATER.get(), 4482);
        BlockPos absTop = helper.absolutePos(top);
        SignalNetwork network = SignalNetwork.get(level);
        int height = RadioHelper.heightAboveSeaLevel(level, absTop);
        check(Math.abs(com.reiko.signaltower.radio.Lightning.chancePerSecond(level, absTop) - 6.0 / 3600 * height / 64) < 1e-12
                && com.reiko.signaltower.radio.Lightning.chancePerSecond(level, absTop.below(200)) == 0, "Chance by height");
        check(!com.reiko.signaltower.radio.Lightning.isTower(level, helper.absolutePos(new BlockPos(30, 1, 1)))
                && com.reiko.signaltower.radio.Lightning.isTower(level, helper.absolutePos(new BlockPos(32, 1, 1)))
                && com.reiko.signaltower.radio.Lightning.isTower(level, absTop), "Which blocks are towers");
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(new BlockPos(11, 0, 1), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> {
                    check(com.reiko.signaltower.radio.Lightning.strike(level, absTop) == null, "No rod: the tower takes it");
                    check(network.isKnockedOut(absTop) && network.outageTicks(absTop) > 90, "Knocked out for 5 seconds");
                    check(!helper.getEntities(EntityType.LIGHTNING_BOLT).isEmpty(), "A bolt came down");
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> {
                    check(!helper.getBlockState(top).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Its light is out");
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(top),
                            com.reiko.signaltower.compat.RadioInfo.read(level, absTop)));
                    check(keys.contains("struck"), "Jade counts down: " + keys);
                })
                .thenWaitUntil(() -> {
                    assertOutput(helper, rx, 15);
                    check(!network.isKnockedOut(absTop), "Back by itself");
                    helper.assertBlockProperty(top, com.reiko.signaltower.block.RadioTransmitterBlock.POWERED, true);
                })
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(13, 4, 1), Blocks.LIGHTNING_ROD);
                    check(com.reiko.signaltower.radio.Lightning.rodNear(level, absTop) == null, "3 blocks away is too far");
                    helper.setBlock(new BlockPos(13, 4, 1), Blocks.AIR);
                    helper.setBlock(rod, Blocks.LIGHTNING_ROD);
                    check(helper.absolutePos(rod).equals(com.reiko.signaltower.radio.Lightning.strike(level, absTop)), "The rod takes it");
                    check(!network.isKnockedOut(absTop) && helper.getBlockState(rod).getValue(net.minecraft.world.level.block.LightningRodBlock.POWERED),
                            "The tower stays on; the rod gives its pulse");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    assertOutput(helper, rx, 15);
                    helper.setBlock(rod, Blocks.AIR);
                    net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    bolt.moveTo(Vec3.atBottomCenterOf(absTop.above()));
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                    check(network.isKnockedOut(absTop), "Any bolt landing on it knocks it out");
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.48.0: signal fade and the Dish Antenna

    /**
     * Signal fade (the defaults): full strength out to half a tower's reach, then weaker and weaker,
     * down to 1 at the very edge, the same in every direction; the reception says how much it lost.
     * A repeater hears the signal unfaded and sends it on at full strength; a compact transmitter
     * doesn't fade; a jammer's jamming fades too. The meter carries it to the HUD. In the world a
     * receiver three quarters of the way out outputs what's left, and Jade says how much faded.
     */
    @GameTest(template = PLATFORM, batch = FADE, timeoutTicks = 200)
    public static void signalFade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(SignalNetwork.fade(15, 0.4) == 15 && SignalNetwork.fade(15, 0.5) == 15 && SignalNetwork.fade(15, 0.75) == 8
                && SignalNetwork.fade(15, 1.0) == 1 && SignalNetwork.fade(1, 1.0) == 1 && SignalNetwork.fade(0, 0.9) == 0
                && SignalNetwork.fade(6, 0.75) == 3, "15 to half way, 8 at three quarters, 1 at the edge");

        SignalNetwork network = new SignalNetwork().attachTo(level);
        network.forceWeather(com.reiko.signaltower.radio.Weather.CLEAR); // whatever the test world's weather is doing
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 30000); // not loaded: no hills, no block checks
        network.updateTransmitter(far, 4491, 15, 100);
        SignalNetwork.Reception near = network.listen(level, 4491, far.east(40));
        check(near.power() == 15 && near.faded() == 0, "Full strength at 40 of 100: " + near);
        SignalNetwork.Reception out = network.listen(level, 4491, far.east(75));
        check(out.power() == 8 && out.faded() == 7, "8 at 75: " + out);
        SignalNetwork.Reception edge = network.listen(level, 4491, far.east(100));
        check(edge.power() == 1 && edge.faded() == 14, "1 at the very edge: " + edge);
        check(network.listen(level, 4491, far.east(101)).power() == 0, "Nothing beyond it");
        check(network.listen(level, 4491, far.north(60).east(60)).power() == SignalNetwork.fade(15, Math.sqrt(7200) / 100),
                "By distance, whichever way");

        network.updateRepeater(far.east(90), 4491, 64);
        check(network.listen(level, 4491, far.east(110)).power() == 15, "A repeater 90 out sends it on at full strength");
        check(network.listen(level, 4491, far.east(95)).power() == 15, "Near the repeater it's strong again");
        check(network.listen(level, 4491, far.east(90 + 48)).power() == 8, "And it fades out toward the repeater's edge");

        network.updateTransmitter(far.north(400), 4492, 15, 16, null, true);
        check(network.listen(level, 4492, far.north(400).east(16)).power() == 15, "A compact transmitter doesn't fade");

        BlockPos jamAt = far.south(800);
        network.updateTransmitter(jamAt.west(1), 4493, 15, 100);
        network.updateJammer(jamAt, 4493, 15, 40, null);
        check(network.listen(level, 4493, jamAt.east(10)).power() == 0, "Close to a jammer: silenced");
        SignalNetwork.Reception jammed = network.listen(level, 4493, jamAt.east(30));
        check(jammed.jammed() == 8 && jammed.power() == 7, "Three quarters out it jams 8: " + jammed);

        var packet = com.reiko.signaltower.network.MeterReadingPayload.of(
                new com.reiko.signaltower.item.SignalMeterItem.Reading(4491, 8, 1, 25, false, 2, 3, 7));
        check(packet.faded() == 7 && packet.hills() == 2 && packet.jammed() == 3, "The meter's packet carries it");

        BlockPos tx = new BlockPos(1, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4494);
        int reach = RadioHelper.towerReachNow(level, helper.absolutePos(tx), ModRegistry.RADIO_TRANSMITTER.get().defaultBlockState()); // in the weather now
        int distance = Math.min(reach * 3 / 4, 120);
        BlockPos rx = tx.east(distance);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4494);
        int expected = SignalNetwork.fade(15, distance / (double) reach);
        check(expected < 15, "Far enough out to fade: " + distance + " of " + reach);
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> assertOutput(helper, rx, expected))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(rx));
                    check(info.getInt("faded") == 15 - expected
                            && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), info)).contains("faded"),
                            "Jade says how much faded: " + info);
                })
                .thenSucceed();
    }

    /**
     * The Dish Antenna (the defaults): fitted, it's aimed the way the player looks, and the tower
     * reaches three times as far inside a beam 15° either side of that, as far as with no antenna
     * elsewhere (a jammer's dish too, and the coverage of a remote press it relays). Using a dish on
     * a dish turns it instead. Jade and the reach ring (a keyhole) show it; it survives saving.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void dishAntenna(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var dish = com.reiko.signaltower.radio.AntennaTier.DISH;
        check(com.reiko.signaltower.radio.AntennaTier.aimFor(180F) == 0 && com.reiko.signaltower.radio.AntennaTier.aimFor(-90F) == 4
                && com.reiko.signaltower.radio.AntennaTier.aimFor(0F) == 8 && com.reiko.signaltower.radio.AntennaTier.aimFor(90F) == 12
                && com.reiko.signaltower.radio.AntennaTier.aimFor(202.5F) == 1, "Looking north 0, east 4, south 8, west 12");
        check(com.reiko.signaltower.radio.AntennaTier.inBeam(0, -50, 0) && !com.reiko.signaltower.radio.AntennaTier.inBeam(0, 50, 0)
                && !com.reiko.signaltower.radio.AntennaTier.inBeam(50, 0, 0) && com.reiko.signaltower.radio.AntennaTier.inBeam(50, -13, 4)
                && !com.reiko.signaltower.radio.AntennaTier.inBeam(50, -14, 4) && com.reiko.signaltower.radio.AntennaTier.inBeam(0, 0, 9),
                "A beam 15° either side of the aim");
        check(com.reiko.signaltower.radio.AntennaTier.dishReach(64) == 192 && dish.multiplierText().equals("×3") && dish.multiplier() == 1.0,
                "×3 that way, ×1 elsewhere");

        SignalNetwork network = new SignalNetwork().attachTo(level);
        network.forceWeather(com.reiko.signaltower.radio.Weather.CLEAR);
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 30000);
        network.updateTransmitter(far, 4496, 15, 64);
        network.setDish(far, 4);
        check(Integer.valueOf(4).equals(network.dishAim(far)), "Aimed east");
        check(network.listen(level, 4496, far.east(192)).power() == 15 && network.listen(level, 4496, far.east(193)).power() == 0,
                "192 blocks east");
        check(network.listen(level, 4496, far.west(64)).power() == 15 && network.listen(level, 4496, far.west(65)).power() == 0
                && network.listen(level, 4496, far.south(100)).power() == 0, "64 every other way");
        check(network.edgeMargin(level, 4496, far.east(150)) == 42, "The meter's edge is out at 192 that way");
        SignalNetwork.Coverage relayed = new SignalNetwork.Coverage(far, 64, true, 192, 4);
        check(relayed.reaches(far.east(150)) && !relayed.reaches(far.west(150)) && relayed.reaches(far.west(60)), "A press it relays goes as far");
        SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(Integer.valueOf(4).equals(loaded.dishAim(far)), "It survives saving");
        network.setDish(far, -1);
        check(network.listen(level, 4496, far.east(150)).power() == 0 && network.dishAim(far) == null, "Without it: 64 that way too");

        BlockPos jamAt = far.south(500);
        network.updateTransmitter(jamAt.west(1), 4497, 15, 200);
        network.updateJammer(jamAt, 4497, 15, 32, null);
        network.setDish(jamAt, 0);
        check(network.listen(level, 4497, jamAt.north(90)).power() == 0 && network.listen(level, 4497, jamAt.south(40)).power() == 15,
                "A jammer's dish jams 96 north, 32 elsewhere");

        BlockPos tx = new BlockPos(1, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4498);
        BlockPos absTx = helper.absolutePos(tx);
        int reach = RadioHelper.towerReachNow(level, absTx, ModRegistry.RADIO_TRANSMITTER.get().defaultBlockState()); // in the weather now
        int beam = com.reiko.signaltower.item.AntennaItem.dishReachShown(level, absTx, ModRegistry.RADIO_TRANSMITTER.get().defaultBlockState()
                .setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY, dish).setValue(com.reiko.signaltower.radio.AntennaTier.AIM, 4));
        int east = Math.min(beam - 2, 125);
        check(east > reach, "The receiver is beyond the usual reach: " + east + " > " + reach);
        BlockPos rx = tx.east(east);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4498);
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        dev.setYRot(-90F); // looking east
        ItemStack dishes = new ItemStack(ModRegistry.DISH_ANTENNA.get());

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK))
                .thenIdle(5)
                .thenExecute(() -> {
                    assertOutput(helper, rx, 0);
                    Component fitted = com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, dish, dishes);
                    check(fitted != null && keyOf(fitted).endsWith("dish_fitted") && dishes.isEmpty()
                            && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == dish
                            && helper.getBlockState(tx).getValue(com.reiko.signaltower.radio.AntennaTier.AIM) == 4
                            && Integer.valueOf(4).equals(SignalNetwork.get(level).dishAim(absTx)), "Fitted, aimed east: " + fitted);
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTx);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info));
                    check(info.getInt("dish_reach") == beam && keys.contains("antenna_dish") && keys.contains("dish_reach"), "Jade: " + keys + " " + info);

                    dev.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FREQUENCY_TUNER.get()));
                    var ring = com.reiko.signaltower.network.ReachPreviewRequestPayload.answer(dev, absTx);
                    check(ring.hasDish() && ring.dishAim() == 4 && ring.dishCone() == 15 && ring.dishReach() == beam && ring.reach() == reach,
                            "The reach ring knows: " + ring);
                    var buffer = Unpooled.buffer();
                    com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC.encode(buffer, ring);
                    check(com.reiko.signaltower.network.ReachPreviewPayload.STREAM_CODEC.decode(buffer).equals(ring), "Survives the network");
                    int nearDots = 0;
                    int farDots = 0;
                    for (double[] dot : com.reiko.signaltower.client.ReachRingShape.keyhole(reach, beam, 4, 15)) {
                        double r = Math.hypot(dot[0], dot[1]);
                        double off = Math.toDegrees(Math.abs(Math.atan2(dot[1], dot[0]))); // east is 0
                        if (Math.abs(r - (reach + 0.5)) < 1e-6 && off > 15 - 1e-6) nearDots++;
                        else if (Math.abs(r - (beam + 0.5)) < 1e-6 && off < 15 + 1e-6) farDots++;
                        else check(Math.abs(off - 15) < 1e-6 && r >= reach + 0.5 - 1e-6 && r <= beam + 0.5, "Else on the beam's sides: " + r + " at " + off);
                    }
                    check(nearDots > 100 && farDots > 10, "A keyhole: " + nearDots + " round, " + farDots + " out in the beam");
                    check(com.reiko.signaltower.client.ReachRingShape.edgeDistance(east, 0, reach, beam, 4, 15) == beam - east
                            && com.reiko.signaltower.client.ReachRingShape.edgeDistance(-(reach + 1), 0, reach, beam, 4, 15) == -1,
                            "The ring's edge distance follows the beam");

                    dev.setYRot(90F); // now west
                    ItemStack another = new ItemStack(ModRegistry.DISH_ANTENNA.get());
                    Component turned = com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, dish, another);
                    check(turned != null && keyOf(turned).endsWith("dish_reaimed") && another.getCount() == 1
                            && helper.getBlockState(tx).getValue(com.reiko.signaltower.radio.AntennaTier.AIM) == 12, "Turned to face west: " + turned);
                    Component same = com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, dish, another);
                    check(same != null && keyOf(same).endsWith("dish_already") && another.getCount() == 1, "Already facing west");
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> {
                    com.reiko.signaltower.item.AntennaItem.fit(dev, absTx, com.reiko.signaltower.radio.AntennaTier.IRON, new ItemStack(ModRegistry.IRON_ANTENNA.get()));
                    check(dev.getInventory().countItem(ModRegistry.DISH_ANTENNA.get()) == 1 && SignalNetwork.get(level).dishAim(absTx) == null,
                            "An iron antenna replaces it; the dish comes back");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.49.0: power cost and the Coverage Map

    /**
     * Power cost (the defaults, in a batch of its own): a tower with no battery, or an empty one, is
     * out of power and sends nothing; charged (here with three Redstone Dust) it sends, using 1 a
     * second for every 64 blocks of reach, and goes quiet when the charge runs out. A battery next
     * to a mast feeds the tower on top; repeaters and jammers need one too, compact transmitters
     * don't. Hoppers (the item capability) and FE charge it, it's full at 100,000, a comparator
     * reads it, the item keeps its charge, and Jade shows both ends.
     */
    @GameTest(template = PLATFORM, batch = POWER, timeoutTicks = 400)
    public static void powerCost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(com.reiko.signaltower.radio.PowerCost.enabled(), "Power cost is on by default");
        check(com.reiko.signaltower.radio.PowerCost.perSecond(64) == 1.0 && com.reiko.signaltower.radio.PowerCost.perSecond(56) == 0.875
                && com.reiko.signaltower.radio.PowerCost.perSecond(2080) == 32.5, "1 a second per 64 blocks of reach");
        check(com.reiko.signaltower.blockentity.RadioBatteryBlockEntity.bars(0, 100000) == 0
                && com.reiko.signaltower.blockentity.RadioBatteryBlockEntity.bars(1, 100000) == 1
                && com.reiko.signaltower.blockentity.RadioBatteryBlockEntity.bars(25001, 100000) == 2
                && com.reiko.signaltower.blockentity.RadioBatteryBlockEntity.bars(100000, 100000) == 4, "Four bars");
        check(com.reiko.signaltower.radio.PowerCost.durationLabel(13 * 3600 + 20 * 60) .equals("13h 20m")
                && com.reiko.signaltower.radio.PowerCost.durationLabel(150).equals("2m")
                && com.reiko.signaltower.radio.PowerCost.rateLabel(0.875).equals("0.88") && com.reiko.signaltower.radio.PowerCost.rateLabel(1.0).equals("1")
                && com.reiko.signaltower.radio.PowerCost.number(45000).equals("45,000"), "Labels");

        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(10, 1, 1);
        BlockPos cell = new BlockPos(1, 1, 2);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4501);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4501);
        BlockPos absTx = helper.absolutePos(tx);
        BlockPos absCell = helper.absolutePos(cell);
        // A tower on two masts with a battery by its bottom mast; a repeater, a jammer and a compact transmitter with none.
        helper.setBlock(new BlockPos(20, 1, 1), ModRegistry.RADIO_MAST.get());
        helper.setBlock(new BlockPos(20, 2, 1), ModRegistry.RADIO_MAST.get());
        place(helper, new BlockPos(20, 3, 1), ModRegistry.RADIO_TRANSMITTER.get(), 4502);
        helper.setBlock(new BlockPos(21, 1, 1), ModRegistry.RADIO_BATTERY.get());
        place(helper, new BlockPos(30, 1, 1), ModRegistry.RADIO_REPEATER.get(), 4503);
        place(helper, new BlockPos(40, 1, 1), ModRegistry.RADIO_JAMMER.get(), 4504);
        helper.setBlock(new BlockPos(50, 1, 2), Blocks.STONE);
        place(helper, new BlockPos(50, 1, 1), ModRegistry.COMPACT_RADIO_TRANSMITTER.get(), 4505);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK))
                .thenIdle(25)
                .thenExecute(() -> {
                    SignalNetwork network = SignalNetwork.get(level);
                    assertOutput(helper, rx, 0);
                    check(network.isDrained(absTx) && !helper.getBlockState(tx).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED),
                            "No battery: out of power, its light off");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTx);
                    check(info.getBoolean("unpowered") && info.getInt("power") == 0
                            && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("unpowered"),
                            "Jade says so: " + info);
                    check(network.isDrained(helper.absolutePos(new BlockPos(30, 1, 1))) && network.isDrained(helper.absolutePos(new BlockPos(40, 1, 1)))
                            && !network.isDrained(helper.absolutePos(new BlockPos(50, 1, 1))), "Repeaters and jammers need one too; compact transmitters don't");
                    var batteries = com.reiko.signaltower.radio.PowerCost.batteries(level, helper.absolutePos(new BlockPos(20, 3, 1)));
                    check(batteries.size() == 1 && batteries.get(0).getBlockPos().equals(helper.absolutePos(new BlockPos(21, 1, 1))),
                            "A battery by its mast feeds the tower on top");
                    helper.setBlock(cell, ModRegistry.RADIO_BATTERY.get());
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    check(SignalNetwork.get(level).isDrained(absTx), "An empty battery doesn't help");
                    var battery = (com.reiko.signaltower.blockentity.RadioBatteryBlockEntity) level.getBlockEntity(absCell);
                    check(battery.insert(new ItemStack(net.minecraft.world.item.Items.REDSTONE, 3), false) == 3 && battery.getCharge() == 3000,
                            "Three Redstone Dust: 3,000 charge");
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenIdle(45)
                .thenExecute(() -> {
                    SignalNetwork network = SignalNetwork.get(level);
                    var battery = (com.reiko.signaltower.blockentity.RadioBatteryBlockEntity) level.getBlockEntity(absCell);
                    int reach = network.towerReach(absTx);
                    double rate = com.reiko.signaltower.radio.PowerCost.perSecond(reach);
                    check(!network.isDrained(absTx) && battery.getCharge() < 3000 && battery.getCharge() >= 3000 - 4 * rate - 1,
                            "It's using it: " + battery.getCharge() + " left at " + rate + " a second");
                    check(helper.getBlockState(cell).getValue(com.reiko.signaltower.block.RadioBatteryBlock.CHARGE) == 1, "One bar lit");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTx);
                    check(info.getLong("tower_charge") == battery.getCharge() && info.getDouble("tower_rate") == rate && info.getBoolean("tower_sending")
                            && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("tower_battery"),
                            "Jade on the tower: " + info);
                    CompoundTag cellInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absCell);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(cell), cellInfo));
                    check(cellInfo.getInt("battery_charge") == battery.getCharge() && Math.abs(cellInfo.getDouble("battery_use") - rate) < 1e-9
                            && keys.contains("battery_charge") && keys.contains("battery_use"), "Jade on the battery: " + cellInfo + " " + keys);
                    battery.setCharge(2);
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 0))
                .thenExecute(() -> {
                    SignalNetwork network = SignalNetwork.get(level);
                    var battery = (com.reiko.signaltower.blockentity.RadioBatteryBlockEntity) level.getBlockEntity(absCell);
                    check(network.isDrained(absTx) && battery.getCharge() == 0
                            && helper.getBlockState(cell).getValue(com.reiko.signaltower.block.RadioBatteryBlock.CHARGE) == 0, "Ran dry: quiet again");
                    var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, absCell, net.minecraft.core.Direction.UP);
                    check(items != null && items.insertItem(0, new ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK, 2), false).isEmpty()
                            && battery.getCharge() == 18000, "A hopper puts Blocks of Redstone in: 18,000");
                    check(items.insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIRT), true).getCount() == 1, "Nothing else");
                    var energy = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, absCell, null);
                    check(energy != null && energy.receiveEnergy(1005, false) == 1005 && battery.getCharge() == 18100 && !energy.canExtract(),
                            "1,005 FE: 100 charge (and 5 kept for the next)");
                    battery.setCharge(99000);
                    check(battery.insert(new ItemStack(net.minecraft.world.item.Items.REDSTONE, 5), false) == 1 && battery.getCharge() == 100000
                            && battery.insert(new ItemStack(net.minecraft.world.item.Items.REDSTONE), true) == 0, "Full at 100,000: only one more went in");
                    check(battery.comparatorLevel() == 15 && helper.getBlockState(cell).getValue(com.reiko.signaltower.block.RadioBatteryBlock.CHARGE) == 4,
                            "A comparator reads 15");
                    battery.setCharge(50000);
                    check(battery.comparatorLevel() == 8, "Half: 8");
                    check(Integer.valueOf(50000).equals(battery.collectComponents().get(ModRegistry.BATTERY_CHARGE.get())), "The item keeps its charge");
                })
                .thenWaitUntil(() -> assertOutput(helper, rx, 15))
                .thenSucceed();
    }

    /**
     * The Coverage Map: strong signals green, weaker ones yellow, orange and red, nothing blank; it
     * paints from the network, so a jammer leaves a hole and the reach ends where it ends. Drawing it
     * makes a real map lined up like a vanilla one at its zoom (1:4 to start), the same map again in
     * the same square, a new one at a new zoom; held, it paints itself and marks the towers on it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void coverageMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(com.reiko.signaltower.item.CoverageMapItem.colorFor(0) == 0
                && com.reiko.signaltower.item.CoverageMapItem.colorFor(15) == net.minecraft.world.level.material.MapColor.EMERALD.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.HIGH)
                && com.reiko.signaltower.item.CoverageMapItem.colorFor(13) == net.minecraft.world.level.material.MapColor.EMERALD.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOW)
                && com.reiko.signaltower.item.CoverageMapItem.colorFor(8) == net.minecraft.world.level.material.MapColor.COLOR_YELLOW.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL)
                && com.reiko.signaltower.item.CoverageMapItem.colorFor(1) == net.minecraft.world.level.material.MapColor.COLOR_RED.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOW),
                "Green, yellow, orange, red; blank for nothing");
        check(com.reiko.signaltower.item.CoverageMapItem.blocksAcross(2) == 512, "1:4 is 512 blocks across");

        SignalNetwork network = new SignalNetwork().attachTo(level);
        network.forceWeather(com.reiko.signaltower.radio.Weather.CLEAR);
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 30000);
        network.updateTransmitter(far, 4511, 15, 100);
        network.updateJammer(far.east(60), 4511, 15, 20, null);
        var data = net.minecraft.world.level.saveddata.maps.MapItemSavedData.createFresh(far.getX(), far.getZ(), (byte) 2, true, false, level.dimension());
        com.reiko.signaltower.item.CoverageMapItem.paintRows(level, network, 4511, null, data, 0, 128, far.getY());
        // Every pixel the map shows (it's lined up like a vanilla map, so part of the reach may be off it):
        // heard (green: fade is off in this batch) inside the 100-block reach, blank in the jammer's 20 or beyond.
        int heard = 0;
        int wrong = 0;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                long worldX = (data.centerX / 4 + x - 64) * 4L + 2;
                long worldZ = (data.centerZ / 4 + z - 64) * 4L + 2;
                long dx = worldX - far.getX();
                long dz = worldZ - far.getZ();
                long jx = worldX - far.getX() - 60;
                boolean shouldHear = dx * dx + dz * dz <= 100L * 100 && jx * jx + dz * dz > 20L * 20;
                byte color = data.colors[x + z * 128];
                if (color != 0) heard++;
                if (color != (shouldHear ? com.reiko.signaltower.item.CoverageMapItem.colorFor(15) : 0)) wrong++;
            }
        }
        check(heard > 300 && wrong == 0, "Painted: " + heard + " pixels heard, " + wrong + " wrong");

        BlockPos tx = new BlockPos(5, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4512);
        Player dev = namedPlayer(helper, new BlockPos(6, 1, 1), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        ItemStack map = new ItemStack(ModRegistry.COVERAGE_MAP.get());
        FrequencyTunerItem.setFrequency(map, 4512);
        dev.setItemInHand(InteractionHand.MAIN_HAND, map);
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(new BlockPos(4, 1, 1), Blocks.REDSTONE_BLOCK))
                .thenIdle(5)
                .thenExecute(() -> {
                    BlockPos at = helper.absolutePos(tx);
                    dev.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5); // so the map's square is the tower's own
                    var id = com.reiko.signaltower.item.CoverageMapItem.draw(level, dev, map);
                    var drawn = level.getMapData(id);
                    check(drawn != null && map.get(net.minecraft.core.component.DataComponents.MAP_ID).equals(id) && drawn.scale == 2
                            && drawn.dimension == level.dimension(), "A real map, 1:4");
                    check(com.reiko.signaltower.item.CoverageMapItem.draw(level, dev, map).equals(id), "Drawn again in the same square: the same map");
                    for (int i = 0; i < 128 / com.reiko.signaltower.item.CoverageMapItem.ROWS_PER_TICK; i++) {
                        map.getItem().inventoryTick(map, level, dev, 0, true);
                    }
                    int px = Math.floorDiv(at.getX(), 4) - Math.floorDiv(drawn.centerX, 4) + 64;
                    int pz = Math.floorDiv(at.getZ(), 4) - Math.floorDiv(drawn.centerZ, 4) + 64;
                    check(drawn.colors[px + pz * 128] == com.reiko.signaltower.item.CoverageMapItem.colorFor(15), "Painted green round the tower");
                    boolean marked = false;
                    for (var decoration : drawn.getDecorations()) {
                        if (decoration.type().equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_MARKER)) marked = true;
                    }
                    // Vanilla leaves markers off the outermost pixel all round, so only ask for one further in.
                    double fx = (at.getX() + 0.5 - drawn.centerX) / 4.0;
                    double fz = (at.getZ() + 0.5 - drawn.centerZ) / 4.0;
                    check(marked || Math.abs(fx) > 62.5 || Math.abs(fz) > 62.5, "A red marker on the transmitter (" + fx + ", " + fz + ")");
                    map.set(ModRegistry.COVERAGE_ZOOM.get(), 0);
                    check(!com.reiko.signaltower.item.CoverageMapItem.draw(level, dev, map).equals(id)
                            && level.getMapData(map.get(net.minecraft.core.component.DataComponents.MAP_ID)).scale == 0, "A new zoom: a new map");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.50.0: the Ender Antenna and the Solar Panel

    /**
     * The Ender Antenna: it sends into the Nether at 1/8 of the coordinates (and back, 1:1 into the
     * End), by default the Nether from the Overworld; fitted to a transmitter, the Nether hears it at
     * the spot it arrives with the tower's reach, and knows at once when it changes; Jade says where
     * it goes; another one moves it on to the next dimension (nothing used up); it's saved; taken
     * away with the tower. Repeaters can't take one.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void crossDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        ServerLevel end = level.getServer().getLevel(net.minecraft.world.level.Level.END);
        check(nether != null && end != null, "The test server has the Nether and the End");
        check(com.reiko.signaltower.radio.CrossDimension.arrival(level, nether, new BlockPos(800, 70, -160)).equals(new BlockPos(100, 70, -20))
                && com.reiko.signaltower.radio.CrossDimension.arrival(nether, level, new BlockPos(100, 70, -20)).equals(new BlockPos(800, 70, -160))
                && com.reiko.signaltower.radio.CrossDimension.arrival(level, end, new BlockPos(800, 70, -160)).equals(new BlockPos(800, 70, -160))
                && com.reiko.signaltower.radio.CrossDimension.arrival(level, nether, new BlockPos(-9, 70, 0)).getX() == -2
                && com.reiko.signaltower.radio.CrossDimension.arrival(level, nether, new BlockPos(0, -60, 0)).getY() == nether.getMinBuildHeight(),
                "1/8 into the Nether and back (rounding down), 1:1 into the End, the height kept inside the world");
        check(com.reiko.signaltower.radio.CrossDimension.defaultTarget(level) == net.minecraft.world.level.Level.NETHER
                && com.reiko.signaltower.radio.CrossDimension.defaultTarget(nether) == net.minecraft.world.level.Level.OVERWORLD
                && com.reiko.signaltower.radio.CrossDimension.defaultTarget(end) == net.minecraft.world.level.Level.OVERWORLD,
                "To start with: the Nether from the Overworld, the Overworld from anywhere else");
        var afterNether = com.reiko.signaltower.radio.CrossDimension.next(level, net.minecraft.world.level.Level.NETHER);
        check(afterNether == net.minecraft.world.level.Level.END
                && com.reiko.signaltower.radio.CrossDimension.next(level, afterNether) == net.minecraft.world.level.Level.NETHER,
                "Another one moves it on, round the other dimensions");

        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos relay = new BlockPos(10, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4521);
        place(helper, relay, ModRegistry.RADIO_REPEATER.get(), 4522);
        BlockPos abs = helper.absolutePos(tx);
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        ItemStack ender = new ItemStack(ModRegistry.ENDER_ANTENNA.get());
        BlockPos inNether = com.reiko.signaltower.radio.CrossDimension.arrival(level, nether, abs);
        BlockPos inEnd = com.reiko.signaltower.radio.CrossDimension.arrival(level, end, abs);
        long[] revision = new long[1];
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK))
                .thenIdle(5)
                .thenExecute(() -> {
                    Component refused = com.reiko.signaltower.item.AntennaItem.fit(dev, helper.absolutePos(relay), com.reiko.signaltower.radio.AntennaTier.ENDER, ender);
                    check(refused != null && keyOf(refused).endsWith("ender_transmitters_only") && ender.getCount() == 1
                            && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(relay)) == com.reiko.signaltower.radio.AntennaTier.NONE,
                            "A repeater can't take one");
                    Component fitted = com.reiko.signaltower.item.AntennaItem.fit(dev, abs, com.reiko.signaltower.radio.AntennaTier.ENDER, ender);
                    SignalNetwork here = SignalNetwork.get(level);
                    check(fitted != null && keyOf(fitted).endsWith("ender_fitted") && ender.isEmpty()
                            && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == com.reiko.signaltower.radio.AntennaTier.ENDER
                            && here.linkTarget(abs) == net.minecraft.world.level.Level.NETHER, "Fitted: it sends into the Nether too: " + fitted);
                    SignalNetwork there = SignalNetwork.get(nether);
                    int reach = here.reachNow(level, abs, here.towerReach(abs));
                    SignalNetwork.Reception heard = there.listen(nether, 4521, inNether, null);
                    check(heard.power() == 15 && heard.transmittersInReach() == 1 && there.listen(nether, 4521, inNether.east(reach), null).power() == 15
                            && there.listen(nether, 4521, inNether.east(reach + 1), null).power() == 0, "The Nether hears it where it arrives, with its reach");
                    check(there.edgeMargin(nether, 4521, inNether.east(10)) == reach - 10, "The meter's edge there too");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(info.getString("link_dimension").equals("minecraft:the_nether") && info.getLong("link_at") == inNether.asLong()
                            && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("antenna_ender"),
                            "Jade says where it goes: " + info);
                    revision[0] = there.revision(4521);
                    helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
                })
                .thenWaitUntil(() -> check(SignalNetwork.get(nether).listen(nether, 4521, inNether, null).power() == 0, "Off over there too"))
                .thenExecute(() -> {
                    check(SignalNetwork.get(nether).revision(4521) > revision[0], "The Nether knew at once");
                    helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
                    ItemStack another = new ItemStack(ModRegistry.ENDER_ANTENNA.get());
                    Component moved = com.reiko.signaltower.item.AntennaItem.fit(dev, abs, com.reiko.signaltower.radio.AntennaTier.ENDER, another);
                    check(moved != null && keyOf(moved).endsWith("ender_relinked") && another.getCount() == 1
                            && SignalNetwork.get(level).linkTarget(abs) == net.minecraft.world.level.Level.END, "Moved on to the End: " + moved);
                })
                .thenWaitUntil(() -> check(SignalNetwork.get(end).listen(end, 4521, inEnd, null).power() == 15, "The End hears it"))
                .thenExecute(() -> {
                    check(SignalNetwork.get(nether).listen(nether, 4521, inNether, null).power() == 0, "And the Nether doesn't any more");
                    SignalNetwork loaded = SignalNetwork.load(SignalNetwork.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                    check(loaded.linkTarget(abs) == net.minecraft.world.level.Level.END
                            && level.getBlockEntity(abs).saveWithoutMetadata(level.registryAccess()).getString("link_target").equals("minecraft:the_end"),
                            "Saved, in the network and the block");
                    helper.setBlock(tx, Blocks.AIR);
                    check(SignalNetwork.get(level).linkTarget(abs) == null && SignalNetwork.get(end).listen(end, 4521, inEnd, null).power() == 0,
                            "Gone with the tower");
                })
                .thenSucceed();
    }

    /**
     * The Solar Panel: 3 a second in full sun, by a Daylight Detector's reading; fractions add up
     * from one second to the next; with no battery under it, Jade says so; on a battery it charges
     * it by day (Jade: how fast, and on the battery "Solar Panel: +..."), and not in the dark.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void solarPanel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(com.reiko.signaltower.blockentity.SolarPanelBlockEntity.rateFor(15) == 3.0
                && com.reiko.signaltower.blockentity.SolarPanelBlockEntity.rateFor(0) == 0
                && com.reiko.signaltower.blockentity.SolarPanelBlockEntity.rateFor(5) == 1.0, "3 a second in full sun, a third at 5");
        BlockPos cell = new BlockPos(1, 1, 1);
        BlockPos panel = new BlockPos(1, 2, 1);
        BlockPos lone = new BlockPos(5, 1, 1);
        helper.setBlock(cell, ModRegistry.RADIO_BATTERY.get());
        helper.setBlock(panel, ModRegistry.SOLAR_PANEL.get());
        helper.setBlock(lone, ModRegistry.SOLAR_PANEL.get());
        BlockPos absCell = helper.absolutePos(cell);
        BlockPos absPanel = helper.absolutePos(panel);
        var battery = (com.reiko.signaltower.blockentity.RadioBatteryBlockEntity) level.getBlockEntity(absCell);
        var spare = (com.reiko.signaltower.blockentity.SolarPanelBlockEntity) level.getBlockEntity(helper.absolutePos(lone));
        int start = battery.getCharge();
        check(spare.collect(battery, 0.6) == 0 && spare.collect(battery, 0.6) == 1 && spare.collect(battery, 2.5) == 2
                && battery.getCharge() == start + 3, "Fractions add up: 0.6 + 0.6 + 2.5 is 3 whole");
        CompoundTag loneInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(lone));
        check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(lone), loneInfo)).contains("solar_no_battery"),
                "No battery under it: Jade says so");
        int lightBefore = com.reiko.signaltower.blockentity.SolarPanelBlockEntity.daylight(level, absPanel);
        int before = battery.getCharge();
        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    int lightAfter = com.reiko.signaltower.blockentity.SolarPanelBlockEntity.daylight(level, absPanel);
                    com.mojang.logging.LogUtils.getLogger().info("solarPanel: daylight {} then {}, charge {} -> {}", lightBefore, lightAfter, before, battery.getCharge());
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absPanel);
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(panel), info));
                    if (lightBefore > 0 && lightAfter > 0) {
                        CompoundTag cellInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absCell);
                        check(battery.getCharge() > before && keys.contains("solar_charging") && keys.contains("solar_light")
                                && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(cell), cellInfo)).contains("battery_solar"),
                                "Daylight " + lightAfter + ": charging " + before + " -> " + battery.getCharge() + " " + keys);
                    } else if (lightBefore == 0 && lightAfter == 0) {
                        check(battery.getCharge() == before && keys.contains("solar_dark"), "Dark: not charging " + keys);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.51.0: the mast builder and the Radio Telegraph

    /**
     * The mast builder: a mast used on a tower's mast goes in under the top, lifting it (with its
     * frequency and antenna) and a beacon standing on it; the network follows, the reach grows and
     * redstone still comes up the column; one mast is used up; using it on the top works too; a
     * bare column grows on top, a lightning rod riding up; no room above refuses, and so does
     * someone else's private frequency (nothing changes, nothing used up).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void mastBuilder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        BlockPos mast = new BlockPos(1, 1, 1);
        helper.setBlock(mast, ModRegistry.RADIO_MAST.get());
        place(helper, new BlockPos(1, 2, 1), ModRegistry.RADIO_TRANSMITTER.get(), 4531);
        helper.setBlock(new BlockPos(1, 2, 1), helper.getBlockState(new BlockPos(1, 2, 1))
                .setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY, com.reiko.signaltower.radio.AntennaTier.DIAMOND));
        helper.setBlock(new BlockPos(1, 3, 1), ModRegistry.AVIATION_BEACON.get());
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        List<Component> bar = new java.util.ArrayList<>();
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), UUID.randomUUID(), "Dev", false, bar, new java.util.ArrayList<>());
        ItemStack masts = new ItemStack(ModRegistry.RADIO_MAST_ITEM.get(), 4);
        int[] reach = new int[2];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    check(com.reiko.signaltower.radio.MastBuilder.builds(helper.getBlockState(mast))
                            && com.reiko.signaltower.radio.MastBuilder.builds(helper.getBlockState(new BlockPos(1, 2, 1)))
                            && !com.reiko.signaltower.radio.MastBuilder.builds(Blocks.STONE.defaultBlockState()), "Masts and tower tops build");
                    reach[0] = com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachNow(level, helper.absolutePos(new BlockPos(1, 2, 1)),
                            helper.getBlockState(new BlockPos(1, 2, 1)));
                    Component added = com.reiko.signaltower.radio.MastBuilder.build(dev, helper.absolutePos(mast), masts);
                    BlockPos top = new BlockPos(1, 3, 1);
                    check(added != null && keyOf(added).endsWith("mast_added") && masts.getCount() == 3
                            && helper.getBlockState(new BlockPos(1, 2, 1)).is(ModRegistry.RADIO_MAST.get())
                            && helper.getBlockState(top).is(ModRegistry.RADIO_TRANSMITTER.get())
                            && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(top)) == com.reiko.signaltower.radio.AntennaTier.DIAMOND
                            && frequencyAt(helper, top) == 4531
                            && helper.getBlockState(new BlockPos(1, 4, 1)).is(ModRegistry.AVIATION_BEACON.get()),
                            "Lifted, with its frequency, antenna and beacon: " + added);
                    SignalNetwork network = SignalNetwork.get(level);
                    check(network.transmitterFrequencyAt(helper.absolutePos(top)).orElse(-1) == 4531
                            && network.transmitterFrequencyAt(helper.absolutePos(new BlockPos(1, 2, 1))).isEmpty(), "The network moved with it");
                    reach[1] = com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachNow(level, helper.absolutePos(top), helper.getBlockState(top));
                    Object[] args = ((TranslatableContents) added.getContents()).getArgs();
                    check(reach[1] > reach[0] && args[1].equals(2) && args[2].equals(reach[0]) && args[3].equals(reach[1]),
                            "Two masts up now, reach " + reach[0] + " -> " + reach[1] + ": " + java.util.Arrays.toString(args));
                    Component again = com.reiko.signaltower.radio.MastBuilder.build(dev, helper.absolutePos(top), masts);
                    check(again != null && keyOf(again).endsWith("mast_added") && masts.getCount() == 2
                            && helper.getBlockState(new BlockPos(1, 4, 1)).is(ModRegistry.RADIO_TRANSMITTER.get())
                            && helper.getBlockState(new BlockPos(1, 5, 1)).is(ModRegistry.AVIATION_BEACON.get()), "Used on the top itself: " + again);
                })
                .thenWaitUntil(() -> check(SignalNetwork.get(level).listen(level, 4531, helper.absolutePos(new BlockPos(1, 4, 1))).power() == 15,
                        "Redstone still comes up the column"))
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(1, 6, 1), Blocks.STONE);
                    Component blocked = com.reiko.signaltower.radio.MastBuilder.build(dev, helper.absolutePos(mast), masts);
                    check(blocked != null && keyOf(blocked).endsWith("mast_no_room") && masts.getCount() == 2
                            && helper.getBlockState(new BlockPos(1, 4, 1)).is(ModRegistry.RADIO_TRANSMITTER.get()), "No room: " + blocked);

                    helper.setBlock(new BlockPos(5, 1, 1), ModRegistry.RADIO_MAST.get());
                    helper.setBlock(new BlockPos(5, 2, 1), ModRegistry.RADIO_MAST.get());
                    Component grown = com.reiko.signaltower.radio.MastBuilder.build(dev, helper.absolutePos(new BlockPos(5, 1, 1)), masts);
                    check(grown != null && keyOf(grown).endsWith("mast_grown") && masts.getCount() == 1
                            && helper.getBlockState(new BlockPos(5, 3, 1)).is(ModRegistry.RADIO_MAST.get()), "A bare column grows on top: " + grown);
                    helper.setBlock(new BlockPos(5, 4, 1), Blocks.LIGHTNING_ROD);
                    com.reiko.signaltower.radio.MastBuilder.build(dev, helper.absolutePos(new BlockPos(5, 2, 1)), masts);
                    check(masts.isEmpty() && helper.getBlockState(new BlockPos(5, 4, 1)).is(ModRegistry.RADIO_MAST.get())
                            && helper.getBlockState(new BlockPos(5, 5, 1)).is(Blocks.LIGHTNING_ROD), "A lightning rod rides up");

                    UUID owner = UUID.randomUUID();
                    claims.put(4532, owner, "Owner", java.util.Map.of(), List.of(), false);
                    try {
                        helper.setBlock(new BlockPos(9, 1, 1), ModRegistry.RADIO_MAST.get());
                        place(helper, new BlockPos(9, 2, 1), ModRegistry.RADIO_TRANSMITTER.get(), 4532);
                        RadioBlockEntity owned = helper.getBlockEntity(new BlockPos(9, 2, 1));
                        owned.setTunedBy(owner);
                        ItemStack one = new ItemStack(ModRegistry.RADIO_MAST_ITEM.get());
                        Component refused = com.reiko.signaltower.radio.MastBuilder.build(dev, helper.absolutePos(new BlockPos(9, 1, 1)), one);
                        check(refused != null && keyOf(refused).endsWith("private_refused_block") && one.getCount() == 1
                                && helper.getBlockState(new BlockPos(9, 2, 1)).is(ModRegistry.RADIO_TRANSMITTER.get()),
                                "Someone else's private tower: refused " + refused);
                    } finally {
                        claims.remove(4532);
                    }
                })
                .thenSucceed();
    }

    /**
     * The Radio Telegraph: a message (cleaned, at most 64 characters) goes out from where it
     * stands; another telegraph on the frequency nearby reads it (it clicks and pulses, and its
     * screen shows it), one on another frequency doesn't; too fast, empty, and from a non-member
     * on a private frequency are refused; pagers in reach get it in chat (and one back from being
     * away is told what it missed); a Status Board row would
     * show it; Jade shows the last one; it's saved; on an encrypted frequency a non-member's
     * telegraph can't read it, and the log keeps it; the screen re-tunes and round-trips.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void telegraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(4, 1, 1);
        BlockPos other = new BlockPos(7, 1, 1);
        BlockPos c = new BlockPos(10, 1, 1);
        BlockPos d = new BlockPos(13, 1, 1);
        place(helper, a, ModRegistry.RADIO_TELEGRAPH.get(), 4533);
        place(helper, b, ModRegistry.RADIO_TELEGRAPH.get(), 4533);
        place(helper, other, ModRegistry.RADIO_TELEGRAPH.get(), 4534);
        place(helper, c, ModRegistry.RADIO_TELEGRAPH.get(), 4535);
        place(helper, d, ModRegistry.RADIO_TELEGRAPH.get(), 4535);
        UUID owner = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        List<Component> bar = new java.util.ArrayList<>();
        List<Component> chat = new java.util.ArrayList<>();
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), UUID.randomUUID(), "Dev", false, bar, chat);
        Player boss = namedPlayer(helper, new BlockPos(13, 1, 0), owner, "Owner", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        ItemStack pager = new ItemStack(ModRegistry.PAGER.get());
        PagerItem.setFrequency(pager, 4533);
        pager.set(ModRegistry.PAGER_AWAY_SINCE.get(), 0L); // as if Dev had logged out before any message (the inventory gets a copy)
        dev.getInventory().add(pager);
        BlockPos absA = helper.absolutePos(a);
        BlockPos absB = helper.absolutePos(b);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(com.reiko.signaltower.radio.Telegraph.clean("x".repeat(80)).length() == 64
                            && com.reiko.signaltower.radio.Telegraph.clean("  Hello\u0007 there  ").equals("Hello there"), "Cleaned and cut to 64");
                    var sent = com.reiko.signaltower.radio.Telegraph.send(level, absA, dev, "  Hello\u0007 there  ");
                    check(sent != null && sent.sent() && sent.telegraphs() == 1 && keyOf(sent.message()).endsWith("remote_result"),
                            "Sent, read by the other telegraph on 4533: " + (sent == null ? null : sent.message()));
                    SignalNetwork network = SignalNetwork.get(level);
                    List<SignalNetwork.Message> kept = network.messages(4533);
                    check(!kept.isEmpty() && kept.get(kept.size() - 1).text().equals("Hello there") && kept.get(kept.size() - 1).name().equals("Dev"),
                            "Remembered: " + kept);
                    check(helper.getBlockState(b).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED)
                            && !helper.getBlockState(other).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED)
                            && !helper.getBlockState(a).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED),
                            "The one on 4533 pulses, not the one on 4534 (or the sender)");
                    List<com.reiko.signaltower.radio.Telegraph.Line> feed = com.reiko.signaltower.radio.Telegraph.feed(level, absB, 4533, null);
                    check(!feed.isEmpty() && feed.get(feed.size() - 1).text().equals("Hello there") && feed.get(feed.size() - 1).who().equals("Dev"),
                            "Its screen shows it: " + feed);
                    check(com.reiko.signaltower.radio.Telegraph.feed(level, helper.absolutePos(other), 4534, null).isEmpty(), "Nothing on 4534");
                    var fast = com.reiko.signaltower.radio.Telegraph.send(level, absA, dev, "again");
                    var empty = com.reiko.signaltower.radio.Telegraph.send(level, absB, dev, "   ");
                    check(!fast.sent() && keyOf(fast.message()).endsWith("telegraph_too_fast")
                            && !empty.sent() && keyOf(empty.message()).endsWith("telegraph_empty"), "Too fast, and empty: refused");

                    List<Component> caught = PagerItem.catchUp(level, dev, true);
                    check(caught.stream().anyMatch(line -> keyOf(line).endsWith("pager_catchup_message")), "Back from away: the pager says what it missed " + caught);
                    check(PagerItem.deliverMessage(level, dev, 4533, kept.get(kept.size() - 1)) == 1 && lastKey(bar).endsWith("pager_message")
                            && !chat.isEmpty() && keyOf(chat.get(chat.size() - 1)).endsWith("pager_log"), "A pager in reach gets it, in chat too");
                    check(PagerItem.deliverMessage(level, dev, 4534, kept.get(kept.size() - 1)) == 0, "Not a pager on another frequency");
                    check(network.lastMessageAt(4533, absB, level.getGameTime() - com.reiko.signaltower.radio.Telegraph.BOARD_TICKS) != null,
                            "A Status Board row would show it");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absB);
                    check(info.getString("telegraph_text").equals("Hello there") && info.getString("telegraph_from").equals("Dev")
                            && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(b), info)).contains("telegraph_last"),
                            "Jade: the last message " + info);
                    SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                    List<SignalNetwork.Message> saved = loaded.messages(4533);
                    check(!saved.isEmpty() && saved.get(saved.size() - 1).text().equals("Hello there")
                            && SignalNetwork.covers(saved.get(saved.size() - 1).coverage(), absB), "Saved");
                })
                .thenWaitUntil(() -> check(!helper.getBlockState(b).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED), "Its pulse ends"))
                .thenExecute(() -> {
                    claims.put(4535, owner, "Owner", java.util.Map.of(), List.of(), true);
                    try {
                        RadioBlockEntity theirs = helper.getBlockEntity(c);
                        theirs.setTunedBy(alex);
                        RadioBlockEntity bosses = helper.getBlockEntity(d);
                        bosses.setTunedBy(owner);
                        var refused = com.reiko.signaltower.radio.Telegraph.send(level, helper.absolutePos(d), dev, "nope");
                        check(!refused.sent() && keyOf(refused.message()).endsWith("private_refused_telegraph"), "A non-member can't send on it");
                        var secret = com.reiko.signaltower.radio.Telegraph.send(level, helper.absolutePos(d), boss, "secret");
                        check(secret.sent() && secret.telegraphs() == 0
                                && !helper.getBlockState(c).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED),
                                "Sent, but a non-member's telegraph can't read it");
                        var lines = com.reiko.signaltower.radio.Telegraph.feed(level, helper.absolutePos(c), 4535, alex);
                        check(!lines.isEmpty() && lines.get(lines.size() - 1).encrypted() && lines.get(lines.size() - 1).text().isEmpty(),
                                "Its screen shows it's encrypted, not the text");
                        CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(c));
                        check(!info.contains("telegraph_text") && info.contains("deaf_to"), "Jade: deaf, no text " + info);
                        var log = com.reiko.signaltower.radio.FrequencyLog.log(level.getServer(), 4535);
                        check(!log.isEmpty() && log.get(0).kind() == SignalNetwork.CauseKind.TELEGRAPH && log.get(0).detail().equals("secret")
                                && log.get(0).who().equals("Owner") && log.get(0).pos().isPresent(), "The log keeps it: " + log);
                    } finally {
                        claims.remove(4535);
                    }

                    var answer = com.reiko.signaltower.network.TelegraphActionPayload.apply(dev,
                            new com.reiko.signaltower.network.TelegraphActionPayload(absB, 4536, "", true));
                    RadioBlockEntity tuned = helper.getBlockEntity(b);
                    check(answer != null && tuned.getFrequency() == 4536 && dev.getUUID().equals(tuned.getTunedBy())
                            && answer.header().frequency() == 4536 && answer.notice().isPresent()
                            && keyOf(answer.notice().get()).endsWith("telegraph_tuned") && !answer.sent(), "The screen re-tunes it");
                    var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    com.reiko.signaltower.network.TelegraphFeedPayload.STREAM_CODEC.encode(buffer, answer);
                    var back = com.reiko.signaltower.network.TelegraphFeedPayload.STREAM_CODEC.decode(buffer);
                    check(back.header().equals(answer.header()) && back.lines().equals(answer.lines()) && back.notice().isPresent(),
                            "Screen packet round trip");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.52.0: Wall Redstone on ceilings and round corners, and the Dish Rotator

    /**
     * Wall Redstone on a ceiling: placed by clicking the underside of a block it faces down; it
     * carries power along the ceiling (15, 14) with its lines drawn; it drops off when the block
     * above goes. Round corners: to the next wall of the same block (14, both lines drawn), cut off
     * when the edge is filled in; from a wall to the ceiling under the block; and over the top edge
     * from dust lying on a glass block (which carries nothing itself).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void wallRedstoneCeilingsAndCorners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A ceiling, placed by hand on the underside of a block.
        helper.setBlock(new BlockPos(2, 3, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 3, 1), Blocks.STONE);
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        ItemStack dust = new ItemStack(ModRegistry.WALL_REDSTONE_ITEM.get(), 4);
        dev.setItemInHand(InteractionHand.MAIN_HAND, dust);
        BlockPos under = helper.absolutePos(new BlockPos(2, 3, 1));
        dust.useOn(new UseOnContext(dev, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(under).add(0, -0.5, 0), Direction.DOWN, under, false)));
        check(helper.getBlockState(new BlockPos(2, 2, 1)).is(ModRegistry.WALL_REDSTONE.get())
                && helper.getBlockState(new BlockPos(2, 2, 1)).getValue(WallRedstoneBlock.FACING) == Direction.DOWN,
                "Clicking the underside of a block puts it on the ceiling: " + helper.getBlockState(new BlockPos(2, 2, 1)));
        helper.setBlock(new BlockPos(3, 2, 1), wallRedstone(Direction.DOWN));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_BLOCK);

        // Round a corner to the next wall of the same block.
        helper.setBlock(new BlockPos(6, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 0), wallRedstone(Direction.NORTH));
        helper.setBlock(new BlockPos(7, 1, 1), wallRedstone(Direction.EAST));
        helper.setBlock(new BlockPos(5, 1, 0), Blocks.REDSTONE_BLOCK);

        // From a wall round the bottom edge to the ceiling under the block.
        helper.setBlock(new BlockPos(10, 3, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(10, 3, 0), wallRedstone(Direction.NORTH));
        helper.setBlock(new BlockPos(10, 2, 1), wallRedstone(Direction.DOWN));
        helper.setBlock(new BlockPos(9, 3, 0), Blocks.REDSTONE_BLOCK);

        // Over the top edge from dust lying on a glass block.
        helper.setBlock(new BlockPos(14, 1, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(14, 1, 0), wallRedstone(Direction.NORTH));
        helper.setBlock(new BlockPos(14, 2, 1), Blocks.REDSTONE_WIRE);
        helper.setBlock(new BlockPos(15, 2, 1), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    assertWallPower(helper, new BlockPos(2, 2, 1), 15);
                    assertWallPower(helper, new BlockPos(3, 2, 1), 14);
                    BlockState ceiling = helper.getBlockState(new BlockPos(2, 2, 1));
                    check(ceiling.getValue(WallRedstoneBlock.LEFT) && ceiling.getValue(WallRedstoneBlock.RIGHT)
                            && !ceiling.getValue(WallRedstoneBlock.UP) && !ceiling.getValue(WallRedstoneBlock.DOWN),
                            "On the ceiling: lines west (the redstone block) and east (the next dust): " + ceiling);
                    check(WallRedstoneBlock.armDirection(Direction.DOWN, WallRedstoneBlock.UP) == Direction.SOUTH
                            && WallRedstoneBlock.armDirection(Direction.NORTH, WallRedstoneBlock.LEFT) == Direction.EAST, "Arm directions");

                    assertWallPower(helper, new BlockPos(6, 1, 0), 15);
                    assertWallPower(helper, new BlockPos(7, 1, 1), 14);
                    check(helper.getBlockState(new BlockPos(6, 1, 0)).getValue(WallRedstoneBlock.LEFT)
                            && helper.getBlockState(new BlockPos(7, 1, 1)).getValue(WallRedstoneBlock.RIGHT), "Both draw a line to the corner");

                    assertWallPower(helper, new BlockPos(10, 3, 0), 15);
                    assertWallPower(helper, new BlockPos(10, 2, 1), 14);
                    check(helper.getBlockState(new BlockPos(10, 3, 0)).getValue(WallRedstoneBlock.DOWN)
                            && helper.getBlockState(new BlockPos(10, 2, 1)).getValue(WallRedstoneBlock.DOWN),
                            "Wall and ceiling draw lines round the bottom edge");

                    assertWallPower(helper, new BlockPos(14, 1, 0), 14);
                    check(helper.getBlockState(new BlockPos(14, 1, 0)).getValue(WallRedstoneBlock.UP), "A line over the top edge to the dust");

                    helper.setBlock(new BlockPos(7, 1, 0), Blocks.STONE);
                    helper.setBlock(new BlockPos(3, 3, 1), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    assertWallPower(helper, new BlockPos(7, 1, 1), 0);
                    check(!helper.getBlockState(new BlockPos(6, 1, 0)).getValue(WallRedstoneBlock.LEFT)
                            && !helper.getBlockState(new BlockPos(7, 1, 1)).getValue(WallRedstoneBlock.RIGHT), "The corner filled in: cut off");
                    helper.assertBlockNotPresent(ModRegistry.WALL_REDSTONE.get(), new BlockPos(3, 2, 1));
                    check(!helper.getBlockState(new BlockPos(2, 2, 1)).getValue(WallRedstoneBlock.RIGHT), "The ceiling went: it dropped off");
                })
                .thenSucceed();
    }

    /**
     * The Dish Rotator: it's a mast (the tower's column goes through it); a dish fitted on top
     * points the rotator's way at 0 (east here), whichever way the player looks; redstone 5 turns it
     * 5 steps clockwise (south-south-west) at once, in the network too, without the redstone going
     * up to the tower; off again, back east. Jade on both. Re-aiming the dish by hand is refused
     * while the rotator holds it; with two in a column the higher one turns the dish. The mast
     * builder puts one in under a tower's top, its arrow the way the player looks.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void dishRotator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = new BlockPos(1, 1, 1);
        BlockPos rotator = new BlockPos(1, 2, 1);
        BlockPos top = new BlockPos(1, 3, 1);
        helper.setBlock(lower, ModRegistry.DISH_ROTATOR.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.DishRotatorBlock.FACING, Direction.NORTH));
        helper.setBlock(rotator, ModRegistry.DISH_ROTATOR.get().defaultBlockState()
                .setValue(com.reiko.signaltower.block.DishRotatorBlock.FACING, Direction.EAST));
        place(helper, top, ModRegistry.RADIO_TRANSMITTER.get(), 4541);
        for (int x = 2; x <= 13; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
        }
        BlockPos absTop = helper.absolutePos(top);
        Player dev = namedPlayer(helper, new BlockPos(2, 3, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        dev.setYRot(180.0F); // looking north
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(com.reiko.signaltower.block.DishRotatorBlock.segment(Direction.NORTH) == 0
                            && com.reiko.signaltower.block.DishRotatorBlock.segment(Direction.EAST) == 4
                            && com.reiko.signaltower.block.DishRotatorBlock.segment(Direction.SOUTH) == 8
                            && com.reiko.signaltower.block.DishRotatorBlock.segment(Direction.WEST) == 12, "Its arrow as an aim");
                    check(RadioHelper.mastsBelow(level, absTop) == 2, "It's a mast: the column goes through it");
                    check(helper.absolutePos(rotator).equals(com.reiko.signaltower.block.DishRotatorBlock.rotatorUnder(level, absTop)),
                            "The one nearest the top turns the dish");
                    Component fitted = com.reiko.signaltower.item.AntennaItem.fit(dev, absTop, com.reiko.signaltower.radio.AntennaTier.DISH,
                            new ItemStack(ModRegistry.DISH_ANTENNA.get()));
                    check(fitted != null && com.reiko.signaltower.radio.AntennaTier.aimOf(helper.getBlockState(top)) == 4,
                            "A dish fitted on top points the rotator's way (east), not the player's: " + fitted);
                    // The mast builder puts one in under a tower's top, its arrow the way the player looks (south).
                    helper.setBlock(new BlockPos(20, 1, 1), ModRegistry.RADIO_MAST.get());
                    place(helper, new BlockPos(20, 2, 1), ModRegistry.RADIO_TRANSMITTER.get(), 4542);
                    helper.setBlock(new BlockPos(20, 2, 1), helper.getBlockState(new BlockPos(20, 2, 1))
                            .setValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY, com.reiko.signaltower.radio.AntennaTier.DISH));
                    Player alex = namedPlayer(helper, new BlockPos(20, 1, 0), UUID.randomUUID(), "Alex", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
                    alex.setYRot(0.0F); // looking south
                    Component added = com.reiko.signaltower.radio.MastBuilder.build(alex, helper.absolutePos(new BlockPos(20, 1, 1)),
                            new ItemStack(ModRegistry.DISH_ROTATOR_ITEM.get()));
                    check(added != null && keyOf(added).endsWith("rotator_added")
                            && helper.getBlockState(new BlockPos(20, 2, 1)).is(ModRegistry.DISH_ROTATOR.get())
                            && helper.getBlockState(new BlockPos(20, 2, 1)).getValue(com.reiko.signaltower.block.DishRotatorBlock.FACING) == Direction.SOUTH
                            && com.reiko.signaltower.radio.AntennaTier.aimOf(helper.getBlockState(new BlockPos(20, 3, 1))) == 8,
                            "Put in by the mast builder, its arrow south, the dish turned south: " + added);
                    for (int x = 2; x <= 12; x++) {
                        helper.setBlock(new BlockPos(x, 2, 1), Blocks.REDSTONE_WIRE);
                    }
                    helper.setBlock(new BlockPos(13, 2, 1), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(com.reiko.signaltower.radio.AntennaTier.aimOf(helper.getBlockState(top)) == 9,
                        "Redstone 5 turns it 5 steps clockwise: " + com.reiko.signaltower.radio.AntennaTier.aimOf(helper.getBlockState(top))))
                .thenExecute(() -> {
                    SignalNetwork network = SignalNetwork.get(level);
                    check(network.dishAim(absTop) != null && network.dishAim(absTop) == 9, "The network knows");
                    check(com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.inputOf(level, absTop, helper.getBlockState(top)) == 0,
                            "Its redstone doesn't go up to the tower");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(rotator));
                    List<String> keys = lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rotator), info));
                    check(info.getInt("rotator_power") == 5 && info.getInt("rotator_aim") == 9 && keys.contains("rotator_turns")
                            && keys.contains("mast_top"), "Jade on the rotator: " + keys);
                    CompoundTag lowerInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(lower));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(lower), lowerInfo)).contains("rotator_overruled"),
                            "Jade on the lower one: the higher one turns it");
                    CompoundTag topInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absTop);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(top), topInfo)).contains("dish_rotated"),
                            "Jade on the tower: turned by a rotator");
                    Component refused = com.reiko.signaltower.item.AntennaItem.fit(dev, absTop, com.reiko.signaltower.radio.AntennaTier.DISH,
                            new ItemStack(ModRegistry.DISH_ANTENNA.get()));
                    check(refused != null && keyOf(refused).endsWith("dish_rotated")
                            && com.reiko.signaltower.radio.AntennaTier.aimOf(helper.getBlockState(top)) == 9, "No re-aiming by hand: " + refused);
                    helper.setBlock(new BlockPos(13, 2, 1), Blocks.AIR);
                })
                .thenWaitUntil(() -> check(com.reiko.signaltower.radio.AntennaTier.aimOf(helper.getBlockState(top)) == 4, "Off: back east"))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.53.0: Radio Weather Station, Reiko's Weather

    private static com.reiko.signaltower.radio.StationReading.Value stationRead(ServerLevel level, BlockPos pos,
                                                                           com.reiko.signaltower.radio.StationReading reading) {
        return reading.read(level, pos);
    }

    /**
     * The Radio Weather Station: the arithmetic of its readings (time of day, temperature, storm
     * distance, moon names); set by Dev's packet to the moon, a receiver on its frequency outputs the
     * moon's number and the log names the station; Jade, the screen packet (round trip) and the item
     * know its reading; redstone into it pauses it; a reading that needs Reiko's Weather can't be
     * picked without it; a stranger can't change it on someone else's private frequency, nor anyone
     * out of reach; it's saved, and drops with its settings.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void weatherStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos pause = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, tx, ModRegistry.RADIO_WEATHER_STATION.get(), 4551);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4551);
        com.reiko.signaltower.blockentity.WeatherStationBlockEntity station = helper.getBlockEntity(tx);
        BlockPos abs = helper.absolutePos(tx);
        Player dev = namedPlayer(helper, new BlockPos(3, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Player far = namedPlayer(helper, new BlockPos(70, 1, 0), UUID.randomUUID(), "Far", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        var MOON = com.reiko.signaltower.radio.StationReading.MOON;
        int[] moon = new int[1];

        check(com.reiko.signaltower.radio.StationReading.timeStrength(0) == 0 && com.reiko.signaltower.radio.StationReading.timeStrength(6000) == 4
                && com.reiko.signaltower.radio.StationReading.timeStrength(12000) == 8 && com.reiko.signaltower.radio.StationReading.timeStrength(18000) == 12
                && com.reiko.signaltower.radio.StationReading.timeStrength(23999) == 15 && com.reiko.signaltower.radio.StationReading.timeStrength(25500) == 1
                && com.reiko.signaltower.radio.StationReading.timeStrength(-1) == 15, "Time of day: 16 steps from 06:00");
        check(com.reiko.signaltower.radio.StationReading.clock(0).equals("06:00") && com.reiko.signaltower.radio.StationReading.clock(18000).equals("00:00")
                && com.reiko.signaltower.radio.StationReading.clock(12750).equals("18:45"), "Clock");
        check(com.reiko.signaltower.radio.StationReading.temperatureStrength(-10) == 0 && com.reiko.signaltower.radio.StationReading.temperatureStrength(-30) == 0
                && com.reiko.signaltower.radio.StationReading.temperatureStrength(0) == 3 && com.reiko.signaltower.radio.StationReading.temperatureStrength(20) == 10
                && com.reiko.signaltower.radio.StationReading.temperatureStrength(35) == 15 && com.reiko.signaltower.radio.StationReading.temperatureStrength(60) == 15
                && com.reiko.signaltower.radio.StationReading.temperatureStrength(Float.NaN) == 0, "Temperature: 3 degrees a step from -10");
        check(com.reiko.signaltower.radio.StationReading.stormStrength(0) == 15 && com.reiko.signaltower.radio.StationReading.stormStrength(100) == 14
                && com.reiko.signaltower.radio.StationReading.stormStrength(149) == 14 && com.reiko.signaltower.radio.StationReading.stormStrength(151) == 13
                && com.reiko.signaltower.radio.StationReading.stormStrength(1500) == 0 && com.reiko.signaltower.radio.StationReading.stormStrength(5000) == 0,
                "Nearest storm: 1 less every 100 blocks, like Reiko's Weather Radar");
        check(keyOf(com.reiko.signaltower.radio.StationReading.moonName(0)).endsWith("moon.0")
                && keyOf(com.reiko.signaltower.radio.StationReading.moonName(12)).endsWith("moon.4"), "Moon names");
        check(com.reiko.signaltower.radio.StationReading.named("storm_near") == com.reiko.signaltower.radio.StationReading.STORM_NEAR
                && com.reiko.signaltower.radio.StationReading.named("nonsense") == com.reiko.signaltower.radio.StationReading.WEATHER, "Saved by name");

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(station.getMode() == com.reiko.signaltower.radio.StationReading.WEATHER && !station.isAnnouncing(), "Weather, not announcing, at first");
                    var weather = SignalNetwork.get(level).weatherAt(level, abs);
                    check(station.output(level) == (weather == com.reiko.signaltower.radio.Weather.THUNDER ? 15
                            : weather == com.reiko.signaltower.radio.Weather.RAIN ? 8 : 0), "Weather: " + weather);
                    Component set = com.reiko.signaltower.network.ConfigureStationPayload.apply(dev,
                            new com.reiko.signaltower.network.ConfigureStationPayload(abs, MOON.ordinal(), false));
                    check(set != null && keyOf(set).endsWith("station_set") && station.getMode() == MOON, "Set to the moon: " + set);
                    moon[0] = stationRead(level, abs, MOON).strength();
                    boolean blood = com.reiko.signaltower.compat.ReikosWeatherCompat.bloodMoon(level);
                    check(blood ? moon[0] == 15 : moon[0] == level.getMoonPhase() + 1, "The moon's number: " + moon[0]);
                    check(keyOf(stationRead(level, abs, MOON).label()).endsWith(blood ? "blood_moon" : "moon." + level.getMoonPhase()), "Its name");
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == moon[0], "The receiver hears the moon: " + outputAt(helper, rx)))
                .thenExecute(() -> {
                    var history = SignalNetwork.get(level).history(4551);
                    check(!history.isEmpty() && history.get(history.size() - 1).cause().kind() == SignalNetwork.CauseKind.WEATHER_STATION
                            && abs.equals(history.get(history.size() - 1).cause().pos()), "The log names the station");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    List<Component> lines = com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info);
                    List<String> keys = lineKeys(lines);
                    check(keys.contains("station_reading") && keys.contains("station_scale") && !keys.contains("station_announces"), "Jade: " + keys);
                    Component reading = lines.stream().filter(line -> keyOf(line).endsWith("station_reading")).findFirst().orElseThrow();
                    Object label = ((TranslatableContents) reading.getContents()).getArgs()[1];
                    check(label instanceof Component c && keyOf(c).contains("moon"), "Jade shows the moon's name, as the server worked it out: " + label);

                    var screen = com.reiko.signaltower.network.OpenStationScreenPayload.of(level, station);
                    check(screen.frequency() == 4551 && screen.mode() == MOON.ordinal() && !screen.announce()
                            && screen.status() == com.reiko.signaltower.network.OpenStationScreenPayload.SENDING
                            && (screen.offered() & 0b111) == 0b111 && screen.strengths().size() == com.reiko.signaltower.radio.StationReading.values().length
                            && screen.strengths().get(MOON.ordinal()) == moon[0]
                            && screen.reikos() == com.reiko.signaltower.compat.ReikosWeatherCompat.available(), "Screen: " + screen);
                    var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    com.reiko.signaltower.network.OpenStationScreenPayload.STREAM_CODEC.encode(buffer, screen);
                    check(com.reiko.signaltower.network.OpenStationScreenPayload.STREAM_CODEC.decode(buffer).equals(screen), "Screen packet round trip");
                    var settings = new com.reiko.signaltower.network.ConfigureStationPayload(abs, 3, true);
                    var plain = Unpooled.buffer();
                    com.reiko.signaltower.network.ConfigureStationPayload.STREAM_CODEC.encode(plain, settings);
                    check(com.reiko.signaltower.network.ConfigureStationPayload.STREAM_CODEC.decode(plain).equals(settings), "Settings packet round trip");

                    if (!com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
                        Component refused = com.reiko.signaltower.network.ConfigureStationPayload.apply(dev,
                                new com.reiko.signaltower.network.ConfigureStationPayload(abs, com.reiko.signaltower.radio.StationReading.CONDITIONS.ordinal(), false));
                        check(refused != null && keyOf(refused).endsWith("station_needs_reikos") && station.getMode() == MOON,
                                "Without Reiko's Weather its readings can't be picked");
                        check((screen.offered() & ~0b111) == 0, "Only three on offer");
                    }
                    check(com.reiko.signaltower.network.ConfigureStationPayload.apply(far,
                            new com.reiko.signaltower.network.ConfigureStationPayload(abs, 1, false)) == null && station.getMode() == MOON, "Out of reach");
                    helper.setBlock(pause, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0 && station.isPaused(level), "Redstone pauses it"))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("timer_paused"), "Jade says paused");
                    check(com.reiko.signaltower.network.OpenStationScreenPayload.of(level, station).status()
                            == com.reiko.signaltower.network.OpenStationScreenPayload.PAUSED, "The screen says paused");
                    helper.setBlock(pause, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == moon[0], "It starts again"))
                .thenExecute(() -> {
                    var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
                    claims.put(4551, UUID.randomUUID(), "Owner", java.util.Map.of());
                    try {
                        Component refused = com.reiko.signaltower.network.ConfigureStationPayload.apply(dev,
                                new com.reiko.signaltower.network.ConfigureStationPayload(abs, 1, true));
                        check(refused != null && keyOf(refused).endsWith("private_refused_block") && station.getMode() == MOON && !station.isAnnouncing(),
                                "A stranger can't change it on someone else's private frequency");
                    } finally {
                        claims.remove(4551);
                    }
                    station.setSettings(com.reiko.signaltower.radio.StationReading.TIME, true);
                    check(station.output(level) == com.reiko.signaltower.radio.StationReading.timeStrength(level.getDayTime()), "Time of day now");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info)).contains("station_announces"), "Jade: announces");
                    CompoundTag saved = station.saveWithoutMetadata(level.registryAccess());
                    var copy = new com.reiko.signaltower.blockentity.WeatherStationBlockEntity(abs, helper.getBlockState(tx));
                    copy.loadWithComponents(saved, level.registryAccess());
                    check(copy.getMode() == com.reiko.signaltower.radio.StationReading.TIME && copy.isAnnouncing() && copy.getFrequency() == 4551, "Saved");
                    level.destroyBlock(abs, true);
                })
                .thenWaitUntil(() -> check(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                        .anyMatch(stack -> stack.is(ModRegistry.RADIO_WEATHER_STATION_ITEM.get())
                                && "TIME".equals(stack.get(ModRegistry.STATION_MODE.get()))
                                && Boolean.TRUE.equals(stack.get(ModRegistry.STATION_ANNOUNCE.get()))
                                && Integer.valueOf(4551).equals(stack.get(ModRegistry.FREQUENCY.get()))), "Dropped with its settings"))
                .thenSucceed();
    }

    /**
     * The Radio Weather Station in the weather (a batch of its own, since the weather is the whole
     * dimension's): clear 0, rain 8, thunder 15 on a receiver. Announcing, it sends "Now: Rain" to a
     * telegraph on its frequency once rain has held for two looks (not after one, and not for a
     * flicker back), named "Weather Station"; not while it's paused.
     */
    @GameTest(template = PLATFORM, batch = STATION, timeoutTicks = 300)
    public static void weatherStationAnnounces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos pause = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(6, 1, 1);
        BlockPos tele = new BlockPos(9, 1, 1);
        place(helper, tx, ModRegistry.RADIO_WEATHER_STATION.get(), 4552);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4552);
        place(helper, tele, ModRegistry.RADIO_TELEGRAPH.get(), 4552);
        com.reiko.signaltower.blockentity.WeatherStationBlockEntity station = helper.getBlockEntity(tx);
        BlockPos abs = helper.absolutePos(tx);
        SignalNetwork network = SignalNetwork.get(level);
        var CLEAR = com.reiko.signaltower.radio.Weather.CLEAR;
        var RAIN = com.reiko.signaltower.radio.Weather.RAIN;
        var THUNDER = com.reiko.signaltower.radio.Weather.THUNDER;
        int[] count = new int[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    network.forceWeather(CLEAR);
                    station.setSettings(com.reiko.signaltower.radio.StationReading.WEATHER, true);
                    check(station.output(level) == 0 && station.checkWeather(level) == null, "Clear: 0, nothing to announce");
                    network.forceWeather(RAIN);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 8, "Rain: 8"))
                .thenExecute(() -> {
                    check(keyOf(station.reading(level).label()).endsWith(".rain"), "Its label: " + station.reading(level).label());
                    count[0] = network.messages(4552).size();
                    check(station.checkWeather(level) == null && network.messages(4552).size() == count[0], "Not after one look");
                    SignalNetwork.Message message = station.checkWeather(level);
                    check(message != null && message.text().equals("Now: Rain") && message.name().equals("Weather Station") && message.who() == null,
                            "Announced after two: " + message);
                    check(network.messages(4552).size() == count[0] + 1, "Remembered on its frequency");
                    check(helper.getBlockState(tele).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED), "The telegraph on it clicks");
                    List<com.reiko.signaltower.radio.Telegraph.Line> feed = com.reiko.signaltower.radio.Telegraph.feed(level, helper.absolutePos(tele), 4552, null);
                    check(!feed.isEmpty() && feed.get(feed.size() - 1).who().equals("Weather Station")
                            && feed.get(feed.size() - 1).text().equals("Now: Rain"), "Its screen shows it: " + feed);
                    check(station.checkWeather(level) == null, "Still raining: nothing new");
                    network.forceWeather(THUNDER);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "Thunder: 15"))
                .thenExecute(() -> {
                    check(station.checkWeather(level) == null, "Thunder, one look");
                    network.forceWeather(RAIN);
                    check(station.checkWeather(level) == null, "A flicker back to rain isn't announced");
                    network.forceWeather(THUNDER);
                    check(station.checkWeather(level) == null, "Thunder again: one look");
                    SignalNetwork.Message message = station.checkWeather(level);
                    check(message != null && message.text().equals("Now: Thunderstorm"), "Two looks: " + message);
                    count[0] = network.messages(4552).size();
                    helper.setBlock(pause, Blocks.REDSTONE_BLOCK);
                    network.forceWeather(CLEAR);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0 && station.isPaused(level), "Paused"))
                .thenExecute(() -> {
                    station.checkWeather(level);
                    check(station.checkWeather(level) == null && network.messages(4552).size() == count[0], "Nothing while it's paused");
                    helper.setBlock(pause, Blocks.AIR);
                    network.forceWeather(null);
                })
                .thenSucceed();
    }

    // Reiko's Weather through reflection, for the test below only.

    private static Object reikosState(ServerLevel level) throws ReflectiveOperationException {
        return Class.forName("com.reiko.weather.weather.WeatherManager").getMethod("activeState", net.minecraft.world.level.Level.class)
                .invoke(null, level);
    }

    private static List<Object> reikosCells(ServerLevel level) throws ReflectiveOperationException {
        Object state = reikosState(level);
        @SuppressWarnings("unchecked")
        List<Object> cells = state == null ? new java.util.ArrayList<>() : (List<Object>) state.getClass().getField("cells").get(state);
        return cells;
    }

    /** A storm cell of {@code type} centred on {@code x, z} that stays put at full strength. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object reikosCell(ServerLevel level, String type, double x, double z, float radius) throws ReflectiveOperationException {
        Class<?> manager = Class.forName("com.reiko.weather.weather.WeatherManager");
        Class<?> stateClass = Class.forName("com.reiko.weather.weather.WeatherState");
        Class typeClass = Class.forName("com.reiko.weather.weather.WeatherType");
        Object cell = manager.getMethod("spawnCell", ServerLevel.class, stateClass, typeClass, double.class, double.class, float.class,
                        double.class, double.class, int.class, int.class, boolean.class)
                .invoke(null, level, reikosState(level), Enum.valueOf(typeClass, type), x, z, radius, 0.0, 0.0, 100_000, 1, false);
        cell.getClass().getField("age").setInt(cell, 1); // built up already
        return cell;
    }

    /**
     * Reiko's Weather. Without it: only three readings, the others read 0 and say they need it, and
     * nothing is read from it. With it (./gradlew runGameTestServer -PwithWeather=...): in rain the
     * station's weather code is 4, storm danger 5, weather 8, named as Reiko's Weather names it, and
     * temperature, season and wind come from it; in a thunderstorm 6, 15, 15. A heavy rain cell over a
     * tower (with clear skies elsewhere) cuts its reach 15% there and not 5,000 blocks away, the
     * station reads it as rain (code 5) with the storm overhead (15), and the tower's frequency is
     * worked out again; a spot 300 blocks from the cell's edge reads 12 for the nearest storm. Switched
     * off, everything goes back to vanilla weather.
     */
    @GameTest(template = PLATFORM, batch = REIKOS, timeoutTicks = 400)
    public static void reikosWeather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(2, 1, 1);
        BlockPos rx = new BlockPos(6, 1, 1);
        place(helper, tx, ModRegistry.RADIO_WEATHER_STATION.get(), 4561);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4561);
        com.reiko.signaltower.blockentity.WeatherStationBlockEntity station = helper.getBlockEntity(tx);
        BlockPos abs = helper.absolutePos(tx);
        SignalNetwork network = SignalNetwork.get(level);
        boolean recipe = level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID,
                "radio_weather_station_from_reikos_weather")).isPresent();
        check(recipe == com.reiko.signaltower.compat.ReikosWeatherCompat.installed(),
                "The recipe from Reiko's Weather Station is there only with Reiko's Weather: " + recipe);
        if (!com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
            check(com.reiko.signaltower.radio.StationReading.offeredHere().size() == 3
                    && !com.reiko.signaltower.radio.StationReading.CONDITIONS.offered(), "Three readings without Reiko's Weather");
            var code = stationRead(level, abs, com.reiko.signaltower.radio.StationReading.CONDITIONS);
            check(code.strength() == 0 && keyOf(code.label()).endsWith("needs_reikos_weather"), "Its readings say they need it");
            check(com.reiko.signaltower.compat.ReikosWeatherCompat.local(level, abs) == null
                    && com.reiko.signaltower.compat.ReikosWeatherCompat.stormOver(level, abs) == null
                    && com.reiko.signaltower.compat.ReikosWeatherCompat.nearestStorm(level, abs) == null
                    && com.reiko.signaltower.compat.ReikosWeatherCompat.season() == null
                    && !com.reiko.signaltower.compat.ReikosWeatherCompat.bloodMoon(level)
                    && Float.isNaN(com.reiko.signaltower.compat.ReikosWeatherCompat.temperature(level, abs, null)), "Nothing read from it");
            helper.succeed();
            return;
        }
        Object[] cell = new Object[1];
        long[] before = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(com.reiko.signaltower.compat.ReikosWeatherCompat.runs(level), "Reiko's Weather runs the Overworld"))
                .thenExecute(() -> {
                    try {
                        reikosCells(level).clear();
                    } catch (ReflectiveOperationException e) {
                        throw new GameTestAssertException("Can't reach Reiko's Weather: " + e);
                    }
                    setWeatherNow(level, true, false); // Reiko's Weather follows /weather
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    var local = com.reiko.signaltower.compat.ReikosWeatherCompat.local(level, abs);
                    check(local != null && local.rain() && !local.thunder() && local.id().equals("rain") && local.code() == 4, "Rain: " + local);
                    check(stationRead(level, abs, com.reiko.signaltower.radio.StationReading.CONDITIONS).strength() == 4, "Weather code 4");
                    check(stationRead(level, abs, com.reiko.signaltower.radio.StationReading.STORM).strength() == 5, "Storm danger 5");
                    var weather = stationRead(level, abs, com.reiko.signaltower.radio.StationReading.WEATHER);
                    check(weather.strength() == 8 && keyOf(weather.label()).equals("weather.reikos_weather.rain"), "Weather 8, named by Reiko's Weather: " + weather);
                    check(stationRead(level, abs, com.reiko.signaltower.radio.StationReading.PRECIPITATION).strength() > 0, "It rains");
                    float celsius = com.reiko.signaltower.compat.ReikosWeatherCompat.temperature(level, abs, local);
                    check(!Float.isNaN(celsius) && stationRead(level, abs, com.reiko.signaltower.radio.StationReading.TEMPERATURE).strength()
                            == com.reiko.signaltower.radio.StationReading.temperatureStrength(celsius), "Temperature " + celsius);
                    var season = com.reiko.signaltower.compat.ReikosWeatherCompat.season();
                    check(season == null || season.number() >= 1 && season.number() <= 4
                            && (season.days() <= 0 || season.day() >= 1 && season.day() <= season.days())
                            && stationRead(level, abs, com.reiko.signaltower.radio.StationReading.SEASON).strength() == season.number(),
                            "Season, its day counted from 1: " + season);
                    var wind = stationRead(level, abs, com.reiko.signaltower.radio.StationReading.WIND);
                    check(keyOf(wind.label()).endsWith("station_wind"), "Wind: " + wind.label());
                    var near = stationRead(level, abs, com.reiko.signaltower.radio.StationReading.STORM_NEAR);
                    check(near.strength() == 0 && keyOf(near.label()).endsWith("no_storms"), "No storms: " + near.label());
                    check(com.reiko.signaltower.radio.StationReading.offeredHere().size() == com.reiko.signaltower.radio.StationReading.values().length,
                            "All ten on offer");
                    station.setSettings(com.reiko.signaltower.radio.StationReading.CONDITIONS, false);
                    setWeatherNow(level, true, true);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 6, "Thunderstorm: code 6 on the receiver"))
                .thenExecute(() -> {
                    check(stationRead(level, abs, com.reiko.signaltower.radio.StationReading.STORM).strength() == 15
                            && stationRead(level, abs, com.reiko.signaltower.radio.StationReading.WEATHER).strength() == 15, "Danger 15, weather 15");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
                    check("thunderstorm".equals(info.getString("weather_reikos")), "Jade names Reiko's thunderstorm: " + info);
                    setWeatherNow(level, false, false);
                    before[0] = network.revision(4561);
                    try {
                        cell[0] = reikosCell(level, "HEAVY_RAIN", abs.getX() + 0.5, abs.getZ() + 0.5, 64);
                    } catch (ReflectiveOperationException e) {
                        throw new GameTestAssertException("Can't make a storm cell: " + e);
                    }
                    station.setSettings(com.reiko.signaltower.radio.StationReading.WEATHER, false);
                })
                .thenIdle(SignalNetwork.STORM_CHECK_TICKS + 5)
                .thenExecute(() -> {
                    check(com.reiko.signaltower.compat.ReikosWeatherCompat.stormOver(level, abs) == com.reiko.signaltower.radio.Weather.RAIN, "A rain cell overhead");
                    check(network.weatherAt(level, abs) == com.reiko.signaltower.radio.Weather.RAIN && network.reachNow(level, abs, 100) == 85,
                            "It cuts the tower's reach 15%");
                    BlockPos away = abs.offset(5000, 0, 0);
                    check(network.weatherAt(level, away) == com.reiko.signaltower.radio.Weather.CLEAR && network.reachNow(level, away, 100) == 100,
                            "Not 5,000 blocks away, where it's clear");
                    check(network.revision(4561) > before[0], "The tower's frequency was worked out again");
                    check(station.output(level) == 8, "The station reads rain");
                    check(stationRead(level, abs, com.reiko.signaltower.radio.StationReading.CONDITIONS).strength() == 5, "Heavy rain: code 5");
                    var near = stationRead(level, abs, com.reiko.signaltower.radio.StationReading.STORM_NEAR);
                    check(near.strength() == 15 && keyOf(near.label()).endsWith("storm_overhead"), "The storm is overhead: " + near.label());
                    var storm = com.reiko.signaltower.compat.ReikosWeatherCompat.nearestStorm(level, abs.offset(64 + 300, 0, 0));
                    check(storm != null && storm.id().equals("heavy_rain") && Math.abs(storm.edge() - 300) <= 1
                            && com.reiko.signaltower.radio.StationReading.stormStrength(storm.edge()) == 12, "300 blocks from its edge: 12 (" + storm + ")");
                    com.reiko.signaltower.compat.ReikosWeatherCompat.forTests(false);
                    check(!com.reiko.signaltower.compat.ReikosWeatherCompat.available()
                            && network.weatherAt(level, abs) == com.reiko.signaltower.radio.Weather.CLEAR
                            && stationRead(level, abs, com.reiko.signaltower.radio.StationReading.CONDITIONS).strength() == 0,
                            "Switched off: back to vanilla weather");
                    com.reiko.signaltower.compat.ReikosWeatherCompat.forTests(null);
                    try {
                        reikosCells(level).remove(cell[0]);
                    } catch (ReflectiveOperationException e) {
                        throw new GameTestAssertException("Can't remove the storm cell: " + e);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.54.0: bundled cable, guy wires, storm damage

    private static BlockState tap(net.minecraft.world.item.DyeColor color, Direction facing, boolean output) {
        return ModRegistry.CABLE_TAP.get().defaultBlockState().setValue(com.reiko.signaltower.block.CableTapBlock.COLOR, color)
                .setValue(com.reiko.signaltower.block.CableTapBlock.FACING, facing).setValue(com.reiko.signaltower.block.CableTapBlock.OUTPUT, output);
    }

    private static int tapPower(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(com.reiko.signaltower.block.CableTapBlock.POWER);
    }

    /**
     * Bundled Cable: a line of 9 cables joins up (arms drawn); a red input tap fed by a redstone block
     * puts 15 on red, a blue one fed through 7 dust puts 8 on blue (two blue taps: the stronger
     * counts), and the output taps on red, blue and white give 15, 8 and 0 out (a lamp by the red
     * one lights); the scan finds 9 cables and 6 devices. Right-clicking a tap switches it out and
     * back in; a dye recolors it (red goes off, green comes on). Breaking a cable in the middle
     * cuts the far taps off; mending it brings them back. Jade shows what a cable carries.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void bundledCable(GameTestHelper helper) {
        var DOWN = Direction.DOWN;
        var RED = net.minecraft.world.item.DyeColor.RED;
        var BLUE = net.minecraft.world.item.DyeColor.BLUE;
        var WHITE = net.minecraft.world.item.DyeColor.WHITE;
        for (int x = 2; x <= 10; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), ModRegistry.BUNDLED_CABLE.get());
        }
        BlockPos redIn = new BlockPos(2, 2, 1);
        BlockPos blueIn = new BlockPos(3, 2, 1);
        BlockPos blueIn2 = new BlockPos(4, 2, 1);
        BlockPos whiteOut = new BlockPos(8, 2, 1);
        BlockPos blueOut = new BlockPos(9, 2, 1);
        BlockPos redOut = new BlockPos(10, 2, 1);
        helper.setBlock(redIn, tap(RED, DOWN, false));
        helper.setBlock(blueIn, tap(BLUE, DOWN, false));
        helper.setBlock(blueIn2, tap(BLUE, DOWN, false));
        helper.setBlock(whiteOut, tap(WHITE, DOWN, true));
        helper.setBlock(blueOut, tap(BLUE, DOWN, true));
        helper.setBlock(redOut, tap(RED, DOWN, true));
        helper.setBlock(redIn.above(), Blocks.REDSTONE_BLOCK);
        // blue: dust from a redstone block runs past both blue taps (10 at the second, 9 at the first)
        for (int x = 3; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 0), Blocks.REDSTONE_WIRE);
        }
        helper.setBlock(new BlockPos(10, 2, 0), Blocks.REDSTONE_BLOCK);
        helper.setBlock(redOut.above(), Blocks.REDSTONE_LAMP);
        BlockPos middle = new BlockPos(6, 1, 1);
        helper.startSequence()
                .thenWaitUntil(() -> check(tapPower(helper, redOut) == 15, "Red comes out at 15: " + tapPower(helper, redOut)))
                .thenExecute(() -> {
                    BlockState cable = helper.getBlockState(middle);
                    check(cable.getValue(net.minecraft.world.level.block.PipeBlock.EAST) && cable.getValue(net.minecraft.world.level.block.PipeBlock.WEST)
                            && !cable.getValue(net.minecraft.world.level.block.PipeBlock.NORTH), "The cable joins its neighbours");
                    check(helper.getBlockState(redIn.below()).getValue(net.minecraft.world.level.block.PipeBlock.UP), "And the tap on it");
                    var wired = com.reiko.signaltower.radio.BundledCables.scan(helper.getLevel(), helper.absolutePos(middle));
                    check(wired.cables() == 9 && wired.devices().size() == 6, "9 cables, 6 devices: " + wired.cables() + ", " + wired.devices().size());
                    check(wired.channels()[RED.getId()] == 15 && wired.channels()[WHITE.getId()] == 0, "Red 15, white 0");
                    int blue = wired.channels()[BLUE.getId()];
                    check(blue == 10 && tapPower(helper, blueIn) == 9 && tapPower(helper, blueIn2) == 10,
                            "Blue is the stronger of its two taps (9 and 10): " + blue);
                    check(tapPower(helper, blueOut) == blue && tapPower(helper, whiteOut) == 0, "Blue comes out as it went in, white not at all");
                    check(helper.getBlockState(redOut.above()).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "The lamp by red lights");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(helper.getLevel(), helper.absolutePos(middle));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(middle), info)).containsAll(List.of("cable_carries", "cable_size")),
                            "Jade on the cable");
                    // switch the red output tap to input and back
                    Player dev = namedPlayer(helper, new BlockPos(10, 1, 2), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
                    BlockState state = helper.getBlockState(redOut);
                    state.useWithoutItem(helper.getLevel(), dev, new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(redOut)),
                            Direction.UP, helper.absolutePos(redOut), false));
                    check(!helper.getBlockState(redOut).getValue(com.reiko.signaltower.block.CableTapBlock.OUTPUT) && tapPower(helper, redOut) == 0,
                            "Switched to in");
                    helper.getBlockState(redOut).useWithoutItem(helper.getLevel(), dev, new BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(redOut)), Direction.UP, helper.absolutePos(redOut), false));
                    check(helper.getBlockState(redOut).getValue(com.reiko.signaltower.block.CableTapBlock.OUTPUT) && tapPower(helper, redOut) == 15,
                            "And back out: 15 again");
                    // a green dye on the red input tap: red goes off, green comes on
                    dev.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GREEN_DYE));
                    helper.getLevel().getBlockState(helper.absolutePos(redIn)).useItemOn(dev.getMainHandItem(), helper.getLevel(), dev, InteractionHand.MAIN_HAND,
                            new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(redIn)), Direction.UP, helper.absolutePos(redIn), false));
                    check(helper.getBlockState(redIn).getValue(com.reiko.signaltower.block.CableTapBlock.COLOR) == net.minecraft.world.item.DyeColor.GREEN,
                            "Dyed green");
                })
                .thenWaitUntil(() -> {
                    var wired = com.reiko.signaltower.radio.BundledCables.scan(helper.getLevel(), helper.absolutePos(middle));
                    check(wired.channels()[net.minecraft.world.item.DyeColor.GREEN.getId()] == 15 && wired.channels()[RED.getId()] == 0
                            && tapPower(helper, redOut) == 0, "Green on, red off");
                })
                .thenExecute(() -> {
                    helper.setBlock(redIn, tap(RED, DOWN, false)); // red again (as placed: it reads its block next tick)
                })
                .thenWaitUntil(() -> check(tapPower(helper, redOut) == 15, "Red back"))
                .thenExecute(() -> helper.setBlock(middle, Blocks.AIR))
                .thenWaitUntil(() -> check(tapPower(helper, redOut) == 0 && tapPower(helper, blueOut) == 0, "Cut in the middle: the far taps go dark"))
                .thenExecute(() -> helper.setBlock(middle, ModRegistry.BUNDLED_CABLE.get()))
                .thenWaitUntil(() -> check(tapPower(helper, redOut) == 15, "Mended"))
                .thenExecute(() -> helper.setBlock(redIn.above(), Blocks.AIR))
                .thenWaitUntil(() -> check(tapPower(helper, redOut) == 0
                        && !helper.getBlockState(redOut.above()).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "Red off: the lamp goes out"))
                .thenSucceed();
    }

    /**
     * The bundled radio pair: a Bundled Radio Transmitter on 4571 fed by a cable with red (15) and
     * lime (15) taps sends red on 4585 and lime on 4576; a plain receiver on 4585 picks out red (15),
     * one on 4571 (white) hears nothing; a Bundled Radio Receiver on 4571 puts red and lime back on
     * its cable (output taps 15, 15, white 0). A transmitter on a mast is fed through the mast. The
     * log has red's turn-on on 4585; the bundle survives a save; Jade shows both ends. Broken, the
     * transmitter's colors go (the receivers and taps go dark).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void bundledRadio(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var DOWN = Direction.DOWN;
        var RED = net.minecraft.world.item.DyeColor.RED;
        var LIME = net.minecraft.world.item.DyeColor.LIME;
        var WHITE = net.minecraft.world.item.DyeColor.WHITE;
        BlockPos tx = new BlockPos(2, 1, 1);
        place(helper, tx, ModRegistry.BUNDLED_TRANSMITTER.get(), 4571);
        helper.setBlock(new BlockPos(3, 1, 1), ModRegistry.BUNDLED_CABLE.get());
        helper.setBlock(new BlockPos(4, 1, 1), ModRegistry.BUNDLED_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 1), tap(RED, DOWN, false));
        helper.setBlock(new BlockPos(4, 2, 1), tap(LIME, DOWN, false));
        helper.setBlock(new BlockPos(3, 3, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(4, 3, 1), Blocks.REDSTONE_BLOCK);
        BlockPos plainRed = new BlockPos(8, 1, 1);
        BlockPos plainWhite = new BlockPos(9, 1, 1);
        place(helper, plainRed, ModRegistry.RADIO_RECEIVER.get(), 4571 + RED.getId());
        place(helper, plainWhite, ModRegistry.RADIO_RECEIVER.get(), 4571);
        BlockPos rx = new BlockPos(14, 1, 1);
        place(helper, rx, ModRegistry.BUNDLED_RECEIVER.get(), 4571);
        for (int x = 15; x <= 17; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), ModRegistry.BUNDLED_CABLE.get());
        }
        BlockPos redOut = new BlockPos(15, 2, 1);
        BlockPos limeOut = new BlockPos(16, 2, 1);
        BlockPos whiteOut = new BlockPos(17, 2, 1);
        helper.setBlock(redOut, tap(RED, DOWN, true));
        helper.setBlock(limeOut, tap(LIME, DOWN, true));
        helper.setBlock(whiteOut, tap(WHITE, DOWN, true));
        // A second one on a mast, fed through the mast: green 15.
        BlockPos mast = new BlockPos(30, 1, 1);
        BlockPos tx2 = mast.above();
        helper.setBlock(mast, ModRegistry.RADIO_MAST.get());
        place(helper, tx2, ModRegistry.BUNDLED_TRANSMITTER.get(), 4591);
        helper.setBlock(new BlockPos(31, 1, 1), ModRegistry.BUNDLED_CABLE.get());
        helper.setBlock(new BlockPos(31, 2, 1), tap(net.minecraft.world.item.DyeColor.GREEN, DOWN, false));
        helper.setBlock(new BlockPos(31, 3, 1), Blocks.REDSTONE_BLOCK);
        SignalNetwork network = SignalNetwork.get(level);
        BlockPos absTx = helper.absolutePos(tx);
        helper.startSequence()
                .thenWaitUntil(() -> check(outputAt(helper, plainRed) == 15, "A plain receiver on 4585 picks out red: " + outputAt(helper, plainRed)))
                .thenWaitUntil(() -> check(tapPower(helper, redOut) == 15 && tapPower(helper, limeOut) == 15, "Red and lime come out at the far end"))
                .thenExecute(() -> {
                    check(outputAt(helper, plainWhite) == 0 && tapPower(helper, whiteOut) == 0, "White: nothing");
                    SignalNetwork.Bundle bundle = network.bundleAt(absTx);
                    check(bundle != null && bundle.base() == 4571 && bundle.channel(4571 + RED.getId()) == 15 && bundle.channel(4571 + LIME.getId()) == 15
                            && bundle.channel(4571) == 0 && bundle.covers(4586) && !bundle.covers(4587), "The bundle: " + bundle);
                    check(helper.getBlockState(tx).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Its light is on");
                    var history = network.history(4571 + RED.getId());
                    check(!history.isEmpty() && absTx.equals(history.get(history.size() - 1).cause().pos()), "Red's turn-on is in the log");
                    SignalNetwork.Bundle mastBundle = network.bundleAt(helper.absolutePos(tx2));
                    check(mastBundle != null && mastBundle.channel(4591 + net.minecraft.world.item.DyeColor.GREEN.getId()) == 15,
                            "The one on a mast is fed through it: " + mastBundle);
                    CompoundTag txInfo = com.reiko.signaltower.compat.RadioInfo.read(level, absTx);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), txInfo)).containsAll(List.of("bundle_range", "bundle_sending")),
                            "Jade on the transmitter");
                    CompoundTag rxInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(rx));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), rxInfo)).containsAll(List.of("bundle_range", "bundle_hears")),
                            "Jade on the receiver: " + rxInfo + " " + lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(rx), rxInfo)));
                    check(helper.getBlockState(rx).getValue(com.reiko.signaltower.block.BundledReceiverBlock.LIT), "The receiver's lit");
                    SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                    SignalNetwork.Bundle saved = loaded.bundleAt(absTx);
                    check(saved != null && saved.base() == 4571 && java.util.Arrays.equals(saved.channels(), bundle.channels()), "Saved");
                    helper.setBlock(new BlockPos(4, 3, 1), Blocks.AIR); // lime off
                })
                .thenWaitUntil(() -> check(tapPower(helper, limeOut) == 0 && tapPower(helper, redOut) == 15, "Lime off at the far end, red still on"))
                .thenExecute(() -> level.destroyBlock(absTx, false))
                .thenWaitUntil(() -> check(outputAt(helper, plainRed) == 0 && tapPower(helper, redOut) == 0 && network.bundleAt(absTx) == null,
                        "Broken: its colors go"))
                .thenSucceed();
    }

    /**
     * Guy wires and storm damage (without a storm): a tower 5 masts tall is at risk; three anchors
     * near it brace its mast (each finds it, the network counts 3), one taken away and it's at risk
     * again. Blown down, it goes quiet (its receiver dark, Jade says so) until an Iron Ingot on one
     * of its masts repairs it (the ingot used up; an ingot on a sound tower or a stick does nothing).
     * An iced-up antenna loses 30% of its reach until it has thawed. Damage, ice and anchors are saved.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void guyWiresAndStormDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = new BlockPos(2, 1, 1);
        for (int y = 0; y < 5; y++) {
            helper.setBlock(foot.above(y), ModRegistry.RADIO_MAST.get());
        }
        BlockPos top = foot.above(5);
        place(helper, top, ModRegistry.RADIO_TRANSMITTER.get(), 4592);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.REDSTONE_BLOCK);
        BlockPos rx = new BlockPos(12, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4592);
        BlockPos[] anchors = {new BlockPos(4, 2, 1), new BlockPos(4, 2, 0), new BlockPos(5, 2, 2)};
        for (BlockPos anchor : anchors) {
            helper.setBlock(anchor.below(), Blocks.STONE);
        }
        BlockPos absTop = helper.absolutePos(top);
        BlockPos absFoot = helper.absolutePos(foot);
        SignalNetwork network = SignalNetwork.get(level);
        Player dev = namedPlayer(helper, new BlockPos(3, 1, 2), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        helper.startSequence()
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "The tower sends"))
                .thenExecute(() -> {
                    var risk = com.reiko.signaltower.radio.StormDamage.assess(level, network, absTop);
                    check(risk.masts() == 5 && absFoot.equals(risk.base()) && risk.tall() && risk.braces() == 0 && risk.atRisk(), "Tall and unbraced: " + risk);
                    for (BlockPos anchor : anchors) {
                        helper.setBlock(anchor, ModRegistry.GUY_ANCHOR.get());
                    }
                })
                .thenWaitUntil(() -> check(network.bracesOf(absFoot) == 3, "Three anchors brace it: " + network.bracesOf(absFoot)))
                .thenExecute(() -> {
                    com.reiko.signaltower.blockentity.GuyAnchorBlockEntity anchor = helper.getBlockEntity(anchors[0]);
                    check(absFoot.equals(anchor.getBase()) && com.reiko.signaltower.blockentity.GuyAnchorBlockEntity.columnHeight(level, absFoot) == 6,
                            "It found the mast's foot (6 tall with the top)");
                    var braced = com.reiko.signaltower.radio.StormDamage.assess(level, network, absTop);
                    check(braced.braced() && !braced.atRisk(), "Braced: " + braced);
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTop);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(top), info)).contains("braced"), "Jade: braced");
                    CompoundTag anchorInfo = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(anchors[0]));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(anchors[0]), anchorInfo))
                            .containsAll(List.of("anchor_braces", "anchor_enough")), "Jade on an anchor");
                    helper.setBlock(anchors[2], Blocks.AIR);
                    check(network.bracesOf(absFoot) == 2 && com.reiko.signaltower.radio.StormDamage.assess(level, network, absTop).atRisk(),
                            "One taken away: at risk again");
                    com.reiko.signaltower.radio.StormDamage.blowDown(level, network, absTop, "derecho");
                    check("derecho".equals(network.damageCause(absTop)) && network.isDown(absTop), "Blown down by a derecho");
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 0, "Its receiver goes dark"))
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTop);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(top), info)).contains("storm_damaged"), "Jade: blown down");
                    SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                    check("derecho".equals(loaded.damageCause(absTop)) && loaded.bracesOf(absFoot) == 2, "Damage and anchors are saved");
                    check(com.reiko.signaltower.radio.StormDamage.repairWith(new ItemStack(Items.STICK), level, helper.absolutePos(foot), dev) == null,
                            "A stick doesn't repair it");
                    ItemStack ingots = new ItemStack(Items.IRON_INGOT, 3);
                    check(com.reiko.signaltower.radio.StormDamage.repairWith(ingots, level, helper.absolutePos(foot.above(2)), dev) != null
                            && network.damageCause(absTop) == null && ingots.getCount() == 2, "An Iron Ingot on a mast repairs it");
                    check(com.reiko.signaltower.radio.StormDamage.repairWith(ingots, level, absTop, dev) == null && ingots.getCount() == 2,
                            "Not again: there's nothing to repair");
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "Back up"))
                .thenExecute(() -> {
                    int clear = network.reachNow(level, absTop, 100);
                    network.ice(absTop);
                    check(network.isIced(absTop) && network.reachNow(level, absTop, 100) == clear * 70 / 100, "Iced up: 30% less reach");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTop);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(top), info)).contains("iced"), "Jade: iced");
                    SignalNetwork loaded = SignalNetwork.load(network.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                    check(loaded.isIced(absTop), "Ice is saved");
                    check(!network.thaw(absTop, 40, 2400) && network.thawed(absTop) == 40, "Thawing");
                    check(network.thaw(absTop, 2400, 2400) && !network.isIced(absTop) && network.reachNow(level, absTop, 100) == clear, "Thawed");
                    network.ice(absTop);
                    com.reiko.signaltower.radio.StormDamage.blowDown(level, network, absTop, "tornado");
                    level.destroyBlock(absTop, false);
                    check(network.damageCause(absTop) == null && !network.isIced(absTop), "A tower that's gone takes its damage and ice with it");
                })
                .thenSucceed();
    }

    /**
     * Storm damage with Reiko's Weather (a batch of its own). Without it: a storm check leaves a tall
     * tower alone and melts any ice. With it: under a derecho cell (chance 100% for the test), a tall
     * unbraced tower blows down, a braced one and a short one stand; under an ice storm cell a tower
     * ices up, and once the cell is gone (above freezing) it starts thawing. v0.56.0: the braced tower
     * standing through the derecho fires Weathered the Storm (for its tuner and whoever's near; value:
     * its masts), the others don't; without Reiko's Weather nothing fires.
     */
    @GameTest(template = PLATFORM, batch = STORMS, timeoutTicks = 400)
    public static void stormDamageWithReikos(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = SignalNetwork.get(level);
        BlockPos tallFoot = new BlockPos(2, 1, 1);
        BlockPos bracedFoot = new BlockPos(20, 1, 1);
        BlockPos shortFoot = new BlockPos(40, 1, 1);
        for (int y = 0; y < 5; y++) {
            helper.setBlock(tallFoot.above(y), ModRegistry.RADIO_MAST.get());
            helper.setBlock(bracedFoot.above(y), ModRegistry.RADIO_MAST.get());
        }
        helper.setBlock(shortFoot, ModRegistry.RADIO_MAST.get());
        place(helper, tallFoot.above(5), ModRegistry.RADIO_TRANSMITTER.get(), 4593);
        place(helper, bracedFoot.above(5), ModRegistry.RADIO_TRANSMITTER.get(), 4594);
        place(helper, shortFoot.above(1), ModRegistry.RADIO_TRANSMITTER.get(), 4595);
        for (BlockPos anchor : new BlockPos[]{new BlockPos(22, 2, 1), new BlockPos(22, 2, 0), new BlockPos(23, 2, 2)}) {
            helper.setBlock(anchor.below(), Blocks.STONE);
            helper.setBlock(anchor, ModRegistry.GUY_ANCHOR.get());
        }
        BlockPos tall = helper.absolutePos(tallFoot.above(5));
        BlockPos braced = helper.absolutePos(bracedFoot.above(5));
        BlockPos low = helper.absolutePos(shortFoot.above(1));
        UUID stormTuner = UUID.randomUUID();
        ((RadioBlockEntity) helper.getBlockEntity(bracedFoot.above(5))).setTunedBy(stormTuner);
        var fired = com.reiko.signaltower.advancement.TowerAdvancements.recordForTests();
        java.util.function.Function<BlockPos, List<com.reiko.signaltower.advancement.TowerAdvancements.Fired>> storms = at -> fired.stream()
                .filter(f -> f.pos().equals(at) && f.event().equals(com.reiko.signaltower.advancement.TowerAdvancements.STORM_SURVIVED)).toList();
        if (!com.reiko.signaltower.compat.ReikosWeatherCompat.available()) {
            helper.startSequence()
                    .thenIdle(3)
                    .thenExecute(() -> {
                        network.ice(tall);
                        com.reiko.signaltower.radio.StormDamage.check(level, network);
                        check(network.damageCause(tall) == null && !network.isIced(tall), "Without Reiko's Weather nothing blows down, and ice melts");
                        check(storms.apply(braced).isEmpty(), "Without Reiko's Weather, no Weathered the Storm");
                    })
                    .thenSucceed();
            return;
        }
        Object[] cell = new Object[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(network.bracesOf(helper.absolutePos(bracedFoot)) == 3, "The middle tower is braced"))
                .thenExecute(() -> {
                    com.reiko.signaltower.radio.StormDamage.chanceForTests(100);
                    try {
                        reikosCells(level).clear();
                        cell[0] = reikosCell(level, "DERECHO", (tall.getX() + low.getX()) / 2.0, tall.getZ() + 0.5, 90);
                    } catch (ReflectiveOperationException e) {
                        throw new GameTestAssertException("Can't make a storm cell: " + e);
                    }
                    check("derecho".equals(com.reiko.signaltower.compat.ReikosWeatherCompat.typeAt(level, tall))
                            && "derecho".equals(com.reiko.signaltower.compat.ReikosWeatherCompat.typeAt(level, low)), "A derecho over all three");
                })
                .thenIdle(SignalNetwork.STORM_CHECK_TICKS + 5)
                .thenExecute(() -> {
                    check("derecho".equals(network.damageCause(tall)), "The tall, unbraced one blew down");
                    check(network.damageCause(braced) == null, "The braced one stands");
                    check(network.damageCause(low) == null, "The short one stands");
                    var survived = storms.apply(braced);
                    check(!survived.isEmpty() && survived.get(0).value() == 5 && stormTuner.equals(survived.get(0).tuner())
                            && survived.get(0).radius() == com.reiko.signaltower.advancement.TowerAdvancements.STORM_RADIUS,
                            "The braced one: Weathered the Storm, for its tuner and whoever's near: " + survived);
                    check(storms.apply(tall).isEmpty() && storms.apply(low).isEmpty(), "Not for the one blown down, nor the short one");
                    network.repair(tall);
                    try {
                        reikosCells(level).remove(cell[0]);
                        cell[0] = reikosCell(level, "ICE_STORM", low.getX() + 0.5, low.getZ() + 0.5, 30);
                    } catch (ReflectiveOperationException e) {
                        throw new GameTestAssertException("Can't make a storm cell: " + e);
                    }
                })
                .thenIdle(SignalNetwork.STORM_CHECK_TICKS + 5)
                .thenExecute(() -> {
                    check(network.isIced(low) && !network.isIced(tall), "An ice storm ices up the tower under it");
                    // A tornado right at a tower touches it; one 30 blocks off doesn't (looked at at once, then sent away).
                    var type = net.minecraft.world.entity.EntityType.byString("reikos_weather:tornado").orElseThrow();
                    net.minecraft.world.entity.Entity twister = type.create(level);
                    twister.moveTo(braced.getX() + 0.5, braced.getY() - 5, braced.getZ() + 0.5);
                    level.addFreshEntity(twister);
                    boolean hits = com.reiko.signaltower.compat.ReikosWeatherCompat.tornadoHits(level, braced);
                    boolean far = com.reiko.signaltower.compat.ReikosWeatherCompat.tornadoHits(level, braced.east(30));
                    twister.discard();
                    check(hits && !far, "A tornado touches the tower it's at (" + hits + "), not one 30 blocks off (" + far + ")");
                    try {
                        reikosCells(level).remove(cell[0]);
                    } catch (ReflectiveOperationException e) {
                        throw new GameTestAssertException("Can't remove the storm cell: " + e);
                    }
                })
                .thenIdle(SignalNetwork.STORM_CHECK_TICKS * 2 + 5)
                .thenExecute(() -> {
                    float celsius = com.reiko.signaltower.compat.ReikosWeatherCompat.temperature(level, low, com.reiko.signaltower.compat.ReikosWeatherCompat.local(level, low));
                    check(celsius <= 0 || network.thawed(low) >= SignalNetwork.STORM_CHECK_TICKS, "Above freezing (" + celsius + " C), it thaws: "
                            + network.thawed(low));
                    network.unice(low);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.55.0: old radio towers, climbing, platforms

    /** v0.55.0: a 16-block cube, room for a whole old radio tower. */
    private static final String TOWER_SITE = "tower_site";

    private static net.minecraft.world.level.block.state.BlockState stateAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos);
    }

    /**
     * An old tower still on the air, built whole: the mast (6 masts), the transmitter on top with an
     * Iron Antenna, marked as found, on its frequency (4611), and on the air (a receiver nearby hears
     * 15); the beacon on top, the torch at its foot, a charged battery with a solar panel, three guy
     * wires that brace it, a platform round its mast just under the top, rails only on the outside, a chest of
     * parts with the tower loot table, the sign by the door with the frequency, and Jade's line.
     */
    @GameTest(template = TOWER_SITE, timeoutTicks = 200)
    public static void foundTowerOnTheAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var plan = new com.reiko.signaltower.world.RadioTowerPiece.Plan(6, true, 4611,
                com.reiko.signaltower.radio.AntennaTier.IRON, 3, true, true, 50_000);
        var piece = new com.reiko.signaltower.world.RadioTowerPiece(helper.absolutePos(new BlockPos(1, 0, 3)),
                net.minecraft.world.level.block.Rotation.NONE, plan);
        piece.buildAll(level, level.getChunkSource().getGenerator(), level.getRandom());
        BlockPos rx = new BlockPos(14, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4611);
        BlockPos foot = piece.mastFoot();
        BlockPos top = piece.top();
        for (int y = 0; y < 6; y++) {
            check(level.getBlockState(foot.above(y)).is(ModRegistry.RADIO_MAST.get()), "A mast at " + y);
        }
        check(level.getBlockState(top).is(ModRegistry.RADIO_TRANSMITTER.get())
                && level.getBlockState(top).getValue(com.reiko.signaltower.radio.AntennaTier.PROPERTY) == com.reiko.signaltower.radio.AntennaTier.IRON,
                "The transmitter on top, with an Iron Antenna");
        var transmitter = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(top);
        check(transmitter.isFoundTower() && transmitter.getFrequency() == 4611, "Marked as found, on 4611");
        check(level.getBlockState(top.above()).is(ModRegistry.AVIATION_BEACON.get()), "A beacon on top");
        check(level.getBlockState(piece.at(6, 1, 4)).is(Blocks.REDSTONE_TORCH), "A redstone torch at its foot");
        BlockPos batteryPos = piece.at(8, 1, 4);
        check(level.getBlockEntity(batteryPos) instanceof com.reiko.signaltower.blockentity.RadioBatteryBlockEntity battery
                && battery.getCharge() == 50_000 && level.getBlockState(batteryPos.above()).is(ModRegistry.SOLAR_PANEL.get()),
                "A charged battery with a solar panel");
        check(level.getBlockEntity(piece.chest()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                && com.reiko.signaltower.world.ModWorldgen.TOWER_CHEST.equals(chest.getLootTable()), "A chest of parts");
        check(level.getBlockEntity(piece.sign()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                && sign.getFrontText().getMessage(2, false).getString().equals("4611") && sign.isWaxed(), "The sign shows 4611");
        BlockPos mast = foot.above(4); // one below the top
        check(level.getBlockState(foot.above(5).north()).isAir(), "Nothing round the top mast");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    check(level.getBlockState(mast.offset(dx, 0, dz)).is(ModRegistry.TOWER_PLATFORM.get()), "A platform at " + dx + "," + dz);
                }
            }
        }
        var north = level.getBlockState(mast.north());
        check(north.getValue(com.reiko.signaltower.block.TowerPlatformBlock.NORTH) && !north.getValue(com.reiko.signaltower.block.TowerPlatformBlock.SOUTH)
                && !north.getValue(com.reiko.signaltower.block.TowerPlatformBlock.EAST) && !north.getValue(com.reiko.signaltower.block.TowerPlatformBlock.WEST),
                "The north platform: a rail on its outside only");
        var corner = level.getBlockState(mast.north().east());
        check(corner.getValue(com.reiko.signaltower.block.TowerPlatformBlock.NORTH) && corner.getValue(com.reiko.signaltower.block.TowerPlatformBlock.EAST)
                && !corner.getValue(com.reiko.signaltower.block.TowerPlatformBlock.SOUTH) && !corner.getValue(com.reiko.signaltower.block.TowerPlatformBlock.WEST),
                "The corner: rails on its two outsides");
        SignalNetwork network = SignalNetwork.get(level);
        helper.startSequence()
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "It's on the air: " + outputAt(helper, rx)))
                .thenWaitUntil(() -> check(network.bracesOf(foot) == 3, "Three guy wires brace it: " + network.bracesOf(foot)))
                .thenExecute(() -> {
                    check(transmitter.getFrequency() == 4611, "Its frequency is free, so it keeps it");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, top);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(level.getBlockState(top), info)).contains("found_tower"), "Jade: an old tower");
                    CompoundTag saved = transmitter.saveWithoutMetadata(level.registryAccess());
                    check(saved.getBoolean("found_tower") && saved.getBoolean("found_checked"), "Found and checked, saved");
                })
                .thenSucceed();
    }

    /**
     * An old tower off the air, built turned round (180 degrees): its blocks turn with it (the chest
     * faces west, the mast is where it should be); a lever (off) instead of a torch, no beacon or
     * platform, one guy wire and a flat battery. With power on (this batch), the lever alone doesn't
     * get it on the air; charging the battery does.
     */
    @GameTest(template = TOWER_SITE, batch = POWER, timeoutTicks = 200)
    public static void foundTowerOffTheAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var plan = new com.reiko.signaltower.world.RadioTowerPiece.Plan(5, false, 4612,
                com.reiko.signaltower.radio.AntennaTier.NONE, 1, false, true, 0);
        var piece = new com.reiko.signaltower.world.RadioTowerPiece(helper.absolutePos(new BlockPos(13, 0, 12)),
                net.minecraft.world.level.block.Rotation.CLOCKWISE_180, plan);
        piece.buildAll(level, level.getChunkSource().getGenerator(), level.getRandom());
        check(piece.getBoundingBox().getXSpan() == 12 && piece.getBoundingBox().getZSpan() == 9
                && piece.getBoundingBox().minX() == helper.absolutePos(new BlockPos(2, 0, 0)).getX(), "Turned round: " + piece.getBoundingBox());
        BlockPos rx = new BlockPos(1, 1, 1);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4612);
        BlockPos top = piece.top();
        BlockPos lever = piece.at(6, 1, 4);
        check(level.getBlockState(piece.mastFoot()).is(ModRegistry.RADIO_MAST.get()) && level.getBlockState(top).is(ModRegistry.RADIO_TRANSMITTER.get()),
                "The mast and transmitter, turned");
        check(level.getBlockState(piece.chest()).getValue(net.minecraft.world.level.block.ChestBlock.FACING) == Direction.WEST, "The chest turned to face west");
        check(level.getBlockState(lever).is(Blocks.LEVER) && !level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED),
                "A lever, off");
        check(level.getBlockState(top.above()).isAir() && level.getBlockState(top.below().north()).isAir(), "No beacon, no platform");
        check(level.getBlockState(piece.anchor(0)).is(ModRegistry.GUY_ANCHOR.get()) && piece.anchor(1) == null, "One guy wire");
        var battery = (com.reiko.signaltower.blockentity.RadioBatteryBlockEntity) level.getBlockEntity(piece.at(8, 1, 4));
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 0 && battery.getCharge() == 0, "Off the air");
                    level.setBlock(lever, level.getBlockState(lever).setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true), Block.UPDATE_ALL);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    check(outputAt(helper, rx) == 0, "The lever on, but the battery's flat");
                    battery.setCharge(20_000);
                })
                .thenWaitUntil(() -> check(outputAt(helper, rx) == 15, "Charged: on the air"))
                .thenSucceed();
    }

    /**
     * An old tower's frequency is checked the first time it ticks: one someone has made private (or
     * named) moves to a free one between 100,000 and 999,999, and its sign is changed to match; a free
     * one stays; the check is done once (saved), so the tower's finder can claim it later.
     */
    @GameTest(template = PLATFORM)
    public static void foundTowerFrequencyTaken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        var names = com.reiko.signaltower.radio.FrequencyNames.get(level.getServer());
        UUID owner = UUID.randomUUID();
        try {
            claims.put(4613, owner, "Owner", java.util.Map.of());
            names.set(4615, "Garage");
            BlockPos signPos = new BlockPos(3, 1, 1);
            helper.setBlock(signPos, Blocks.OAK_SIGN);
            BlockPos absSign = helper.absolutePos(signPos);
            int[] frequencies = {4613, 4614, 4615};
            for (int i = 0; i < frequencies.length; i++) {
                BlockPos at = new BlockPos(6 + i * 4, 1, 1);
                helper.setBlock(at, ModRegistry.RADIO_TRANSMITTER.get());
                var tx = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) helper.getBlockEntity(at);
                tx.markFound(frequencies[i], i == 0 ? absSign : null);
                tx.checkFound(level);
                int now = tx.getFrequency();
                if (i == 1) {
                    check(now == 4614, "A free frequency stays: " + now);
                } else {
                    check(now != frequencies[i] && now >= 100_000 && now < 1_000_000
                            && !com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.frequencyTaken(level, now),
                            "Taken (" + frequencies[i] + "): moved to a free one, " + now);
                }
                if (i == 0) {
                    var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) level.getBlockEntity(absSign);
                    check(sign.getFrontText().getMessage(2, false).getString().equals(Integer.toString(now)), "The sign follows it");
                    tx.setFrequency(4613);
                    tx.checkFound(level);
                    check(tx.getFrequency() == 4613, "Checked once: later on it can be tuned to anything");
                }
            }
        } finally {
            claims.remove(4613);
            names.set(4615, "");
        }
        helper.succeed();
    }

    /**
     * The structure is in the game: its type, the structure, its structure set and biome tag
     * (plains, desert; not the ocean). In the test world (flat plains) a tower would go at a chunk's
     * middle on the ground, in one piece 12 by 9 that saves and loads. Its chest's loot is 3 to 6
     * stacks of tower parts and materials.
     */
    @GameTest(template = PLATFORM)
    public static void radioTowerStructure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var access = level.registryAccess();
        var structure = access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(com.reiko.signaltower.world.ModWorldgen.RADIO_TOWER_STRUCTURE);
        check(structure instanceof com.reiko.signaltower.world.RadioTowerStructure, "The structure: " + structure);
        var set = access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).get(com.reiko.signaltower.world.ModWorldgen.RADIO_TOWERS);
        check(set != null && set.structures().stream().anyMatch(entry -> entry.structure().value() == structure), "Its structure set");
        var biomes = access.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        check(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).is(com.reiko.signaltower.world.ModWorldgen.HAS_RADIO_TOWER)
                && biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DESERT).is(com.reiko.signaltower.world.ModWorldgen.HAS_RADIO_TOWER)
                && !biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.OCEAN).is(com.reiko.signaltower.world.ModWorldgen.HAS_RADIO_TOWER),
                "Its biomes");
        var generator = level.getChunkSource().getGenerator();
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(access, generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level, structure.biomes()::contains);
        var stub = structure.findValidGenerationPoint(context);
        check(stub.isPresent(), "A tower can go here");
        var pieces = stub.get().getPiecesBuilder().build().pieces();
        check(pieces.size() == 1 && pieces.get(0) instanceof com.reiko.signaltower.world.RadioTowerPiece, "One piece: " + pieces);
        var piece = (com.reiko.signaltower.world.RadioTowerPiece) pieces.get(0);
        var box = piece.getBoundingBox();
        int ground = generator.getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
        check(Math.min(box.getXSpan(), box.getZSpan()) == 9 && Math.max(box.getXSpan(), box.getZSpan()) == 12
                && box.getYSpan() == piece.plan().height() && box.minY() == ground, "12 by 9, on the ground (" + ground + "): " + box);
        check(box.isInside(new BlockPos(chunk.getMiddleBlockX(), ground, chunk.getMiddleBlockZ())), "Round the chunk's middle");
        check(piece.plan().frequency() >= 100_000 && piece.plan().frequency() < 1_000_000 && piece.plan().masts() >= 5, "Its plan: " + piece.plan());
        CompoundTag tag = piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
        var loaded = new com.reiko.signaltower.world.RadioTowerPiece(tag);
        check(loaded.getBoundingBox().equals(box) && loaded.plan().equals(piece.plan()) && loaded.rotation() == piece.rotation(), "Saved and loaded");
        var table = level.getServer().reloadableRegistries().getLootTable(com.reiko.signaltower.world.ModWorldgen.TOWER_CHEST);
        java.util.Set<Item> parts = java.util.Set.of(ModRegistry.RADIO_MAST_ITEM.get(), Items.REDSTONE, Items.IRON_INGOT, Items.COPPER_INGOT,
                ModRegistry.GUY_ANCHOR_ITEM.get(), ModRegistry.TOWER_PLATFORM_ITEM.get(), ModRegistry.FREQUENCY_TUNER.get(),
                ModRegistry.RADIO_RECEIVER_ITEM.get(), ModRegistry.COMPACT_RADIO_RECEIVER_ITEM.get(), ModRegistry.IRON_ANTENNA.get(),
                ModRegistry.RADIO_BATTERY_ITEM.get(), ModRegistry.SOLAR_PANEL_ITEM.get(), ModRegistry.GOLD_ANTENNA.get(), ModRegistry.DIAMOND_ANTENNA.get());
        for (int i = 0; i < 20; i++) {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            List<ItemStack> loot = table.getRandomItems(params);
            check(loot.size() >= 3 && loot.stream().allMatch(stack -> parts.contains(stack.getItem())), "Tower parts in the chest: " + loot);
        }
        helper.succeed();
    }

    /**
     * Climbing a mast: walking into it you go up at a ladder's speed and the fall is forgotten;
     * sneaking you hold still; letting go you slide down no faster than on a ladder. Away from the
     * mast, standing by it without walking into it, flying, or above its top, nothing happens.
     */
    @GameTest(template = PLATFORM)
    public static void climbingMasts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 6; y++) {
            helper.setBlock(new BlockPos(2, y, 1), ModRegistry.RADIO_MAST.get());
        }
        BlockPos mast = helper.absolutePos(new BlockPos(2, 3, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double besideX = mast.getX() + 13 / 16.0 + 0.3; // its left side against the mast's east face
        player.moveTo(besideX, mast.getY() + 0.2, mast.getZ() + 0.5);
        check(com.reiko.signaltower.radio.MastClimbing.touchingMast(level, player.getBoundingBox()), "Touching the mast");
        player.setOnGround(false);
        player.horizontalCollision = true;
        player.setDeltaMovement(0, -0.5, 0);
        player.fallDistance = 4;
        check(com.reiko.signaltower.radio.MastClimbing.apply(player) && player.getDeltaMovement().y == com.reiko.signaltower.radio.MastClimbing.CLIMB_SPEED
                && player.fallDistance == 0, "Walking into it: up");
        player.horizontalCollision = false;
        player.setShiftKeyDown(true);
        player.setDeltaMovement(0, -0.3, 0);
        check(com.reiko.signaltower.radio.MastClimbing.apply(player) && player.getDeltaMovement().y == 0, "Sneaking: holding on");
        player.setShiftKeyDown(false);
        player.setDeltaMovement(0.4, -0.6, 0);
        check(com.reiko.signaltower.radio.MastClimbing.apply(player) && player.getDeltaMovement().y == -com.reiko.signaltower.radio.MastClimbing.SLIDE_SPEED
                && player.getDeltaMovement().x == com.reiko.signaltower.radio.MastClimbing.SLIDE_SPEED, "Letting go: sliding down slowly");
        player.setOnGround(true);
        player.setDeltaMovement(0, -0.08, 0);
        check(!com.reiko.signaltower.radio.MastClimbing.apply(player), "Standing by it: nothing");
        player.setOnGround(false);
        player.getAbilities().flying = true;
        check(!com.reiko.signaltower.radio.MastClimbing.apply(player), "Flying: nothing");
        player.getAbilities().flying = false;
        player.moveTo(besideX + 1.0, mast.getY() + 0.2, mast.getZ() + 0.5);
        check(!com.reiko.signaltower.radio.MastClimbing.touchingMast(level, player.getBoundingBox()) && !com.reiko.signaltower.radio.MastClimbing.apply(player),
                "A block away: nothing");
        player.moveTo(besideX, helper.absolutePos(new BlockPos(2, 7, 1)).getY(), mast.getZ() + 0.5);
        check(!com.reiko.signaltower.radio.MastClimbing.touchingMast(level, player.getBoundingBox()), "Above its top: nothing to hold");
        helper.succeed();
    }

    /**
     * Tower Platforms: right-clicking a mast with 8 puts in all 8 round it (the stack used up), with
     * rails only on the outside (none against a wall); the deck holds you from above, lets you through from below, and lets
     * you down beside the mast when you sneak. Taking a corner away puts rails on the two next to it.
     * With 3 in hand only 3 go in; with no room, none (and the click goes on to place one normally).
     */
    @GameTest(template = PLATFORM)
    public static void towerPlatforms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(5, y, 1), ModRegistry.RADIO_MAST.get());
        }
        Player player = namedPlayer(helper, new BlockPos(12, 1, 1), UUID.randomUUID(), "Climber", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        ItemStack platforms = new ItemStack(ModRegistry.TOWER_PLATFORM_ITEM.get(), 8);
        player.setItemInHand(InteractionHand.MAIN_HAND, platforms);
        BlockPos mast = helper.absolutePos(new BlockPos(5, 2, 1));
        var result = level.getBlockState(mast).useItemOn(platforms, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(mast), Direction.SOUTH, mast, false));
        check(result.consumesAction() && platforms.isEmpty(), "Used: " + result + ", " + platforms);
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockState(mast.offset(dx, 0, dz)).is(ModRegistry.TOWER_PLATFORM.get())) {
                    count++;
                }
            }
        }
        check(count == 8, "All 8 round the mast: " + count);
        // (North and south of the ring are the test's barrier walls: solid, so no rail there.)
        var west = level.getBlockState(mast.west());
        check(west.getValue(com.reiko.signaltower.block.TowerPlatformBlock.WEST) && !west.getValue(com.reiko.signaltower.block.TowerPlatformBlock.EAST)
                && !west.getValue(com.reiko.signaltower.block.TowerPlatformBlock.NORTH) && !west.getValue(com.reiko.signaltower.block.TowerPlatformBlock.SOUTH),
                "A rail on the outside only: " + west);
        check(!level.getBlockState(mast.north()).getValue(com.reiko.signaltower.block.TowerPlatformBlock.NORTH), "No rail against a wall");
        BlockPos cell = mast.west();
        player.moveTo(cell.getX() + 0.5, cell.getY() + 0.2, cell.getZ() + 0.5);
        check(com.reiko.signaltower.block.TowerPlatformBlock.deckSolidFor(level, cell, net.minecraft.world.phys.shapes.CollisionContext.of(player)),
                "Standing on it: solid");
        player.moveTo(cell.getX() + 0.5, cell.getY() - 1.0, cell.getZ() + 0.5);
        check(!com.reiko.signaltower.block.TowerPlatformBlock.deckSolidFor(level, cell, net.minecraft.world.phys.shapes.CollisionContext.of(player)),
                "From below: through it");
        // Beside the mast (against its west face), sneaking: down through it.
        player.moveTo(mast.getX() + 3 / 16.0 - 0.3, cell.getY() + 0.2, mast.getZ() + 0.5);
        player.setShiftKeyDown(true);
        check(!com.reiko.signaltower.block.TowerPlatformBlock.deckSolidFor(level, cell, net.minecraft.world.phys.shapes.CollisionContext.of(player)),
                "Sneaking by the mast: down through it");
        player.setShiftKeyDown(false);
        check(com.reiko.signaltower.block.TowerPlatformBlock.deckSolidFor(level, cell, net.minecraft.world.phys.shapes.CollisionContext.of(player)),
                "Not sneaking: it holds");
        player.moveTo(helper.absoluteVec(new Vec3(12.5, 1, 1.5)));
        level.setBlock(mast.north().east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check(level.getBlockState(mast.north()).getValue(com.reiko.signaltower.block.TowerPlatformBlock.EAST)
                && level.getBlockState(mast.east()).getValue(com.reiko.signaltower.block.TowerPlatformBlock.NORTH), "A corner gone: rails beside it");
        ItemStack three = new ItemStack(ModRegistry.TOWER_PLATFORM_ITEM.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, three);
        BlockPos higher = mast.above();
        level.getBlockState(higher).useItemOn(three, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(higher), Direction.SOUTH, higher, false));
        int up = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockState(higher.offset(dx, 0, dz)).is(ModRegistry.TOWER_PLATFORM.get())) {
                    up++;
                }
            }
        }
        check(up == 3 && three.isEmpty(), "Only 3 with 3 in hand: " + up);
        ItemStack more = new ItemStack(ModRegistry.TOWER_PLATFORM_ITEM.get(), 8);
        player.setItemInHand(InteractionHand.MAIN_HAND, more);
        level.getBlockState(mast).useItemOn(more, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(mast), Direction.SOUTH, mast, false));
        check(more.getCount() == 7 && level.getBlockState(mast.north().east()).is(ModRegistry.TOWER_PLATFORM.get()), "The gap filled: one used");
        var none = level.getBlockState(mast).useItemOn(more, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(mast), Direction.SOUTH, mast, false));
        check(none == net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION && more.getCount() == 7, "No room: none (" + none + ")");
        helper.succeed();
    }

    // ---------------------------------------------------------------- v0.56.0: advancements and Radio Operators

    private static net.minecraft.resources.ResourceLocation modId(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, path);
    }

    private static net.minecraft.advancements.Advancement advancement(ServerLevel level, String path) {
        var holder = level.getServer().getAdvancements().get(modId(path));
        check(holder != null, "Advancement " + path + " is loaded");
        return holder.value();
    }

    private static net.minecraft.world.level.storage.loot.LootContext entityContext(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, entity)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, entity.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ADVANCEMENT_ENTITY);
        return new net.minecraft.world.level.storage.loot.LootContext.Builder(params).create(java.util.Optional.empty());
    }

    private static boolean playerCriterion(net.minecraft.advancements.Advancement advancement, String criterion,
                                           net.minecraft.world.level.storage.loot.LootContext context) {
        var instance = (net.minecraft.advancements.critereon.PlayerTrigger.TriggerInstance) advancement.criteria().get(criterion).triggerInstance();
        return instance.player().orElseThrow().matches(context);
    }

    /**
     * The advancement tree is loaded, each under its parent: Reiko's Redstone Signal Tower (the root),
     * First Broadcast, Over the Horizon (a challenge), Head for Heights, Lost Signal, Finders Keepers
     * (a goal) and Call Sign; Weathered the Storm (a challenge, under Head for Heights) only with
     * Reiko's Weather. The tower trigger is registered; Over the Horizon wants a transmitter on the air
     * that reaches 500 blocks or more. Head for Heights wants you on a Tower Platform (not beside it);
     * Lost Signal wants you inside an old radio tower (not just near one).
     */
    @GameTest(template = PLATFORM)
    public static void advancementTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String[][] tree = {{"root", null}, {"first_broadcast", "root"}, {"over_the_horizon", "first_broadcast"},
                {"head_for_heights", "first_broadcast"}, {"lost_signal", "root"}, {"finders_keepers", "lost_signal"}, {"call_sign", "root"}};
        for (String[] entry : tree) {
            var advancement = advancement(level, entry[0]);
            check(entry[1] == null ? advancement.parent().isEmpty() : advancement.parent().equals(java.util.Optional.of(modId(entry[1]))),
                    entry[0] + " under " + entry[1] + ": " + advancement.parent());
            check(advancement.display().isPresent(), entry[0] + " is shown");
        }
        check(advancement(level, "over_the_horizon").display().orElseThrow().getType() == net.minecraft.advancements.AdvancementType.CHALLENGE
                && advancement(level, "finders_keepers").display().orElseThrow().getType() == net.minecraft.advancements.AdvancementType.GOAL
                && advancement(level, "first_broadcast").display().orElseThrow().getType() == net.minecraft.advancements.AdvancementType.TASK,
                "A challenge, a goal and tasks");
        var storm = level.getServer().getAdvancements().get(modId("weathered_the_storm"));
        boolean reikos = com.reiko.signaltower.compat.ReikosWeatherCompat.available();
        check((storm != null) == reikos, "Weathered the Storm only with Reiko's Weather (" + reikos + "): " + (storm != null));
        if (storm != null) {
            check(storm.value().parent().equals(java.util.Optional.of(modId("head_for_heights"))), "Weathered the Storm under Head for Heights");
        }
        check(net.minecraft.core.registries.BuiltInRegistries.TRIGGER_TYPES.get(modId("tower")) == com.reiko.signaltower.advancement.TowerAdvancements.TOWER.get(),
                "The tower trigger");
        var far = (com.reiko.signaltower.advancement.TowerTrigger.TriggerInstance) advancement(level, "over_the_horizon").criteria().get("far_reach").triggerInstance();
        check(far.matches("on_air", 500) && far.matches("on_air", 4000) && !far.matches("on_air", 499) && !far.matches("found_tower_revived", 800),
                "Over the Horizon: on the air, 500 blocks or more");
        var first = (com.reiko.signaltower.advancement.TowerTrigger.TriggerInstance) advancement(level, "first_broadcast").criteria().get("on_air").triggerInstance();
        check(first.matches("on_air", 16) && !first.matches("storm_survived", 16), "First Broadcast: any transmitter on the air");
        var keepers = (com.reiko.signaltower.advancement.TowerTrigger.TriggerInstance) advancement(level, "finders_keepers").criteria().get("brought_back").triggerInstance();
        check(keepers.matches("found_tower_revived", 40) && !keepers.matches("on_air", 40), "Finders Keepers: an old tower brought back");

        // Head for Heights: on a platform, not beside it.
        BlockPos deck = new BlockPos(4, 3, 1);
        helper.setBlock(deck, ModRegistry.TOWER_PLATFORM.get());
        Player climber = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absDeck = helper.absolutePos(deck);
        climber.moveTo(absDeck.getX() + 0.5, absDeck.getY() + 0.125, absDeck.getZ() + 0.5);
        check(playerCriterion(advancement(level, "head_for_heights"), "on_platform", entityContext(level, climber)), "Head for Heights: on a platform");
        climber.moveTo(absDeck.getX() + 2.5, absDeck.getY() + 0.125, absDeck.getZ() + 0.5);
        check(!playerCriterion(advancement(level, "head_for_heights"), "on_platform", entityContext(level, climber)), "Not beside it");

        // Lost Signal: inside an old tower's piece (a structure start put in this chunk for the test).
        var structure = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(com.reiko.signaltower.world.ModWorldgen.RADIO_TOWER_STRUCTURE);
        BlockPos origin = helper.absolutePos(new BlockPos(30, 1, 0));
        var piece = new com.reiko.signaltower.world.RadioTowerPiece(origin, net.minecraft.world.level.block.Rotation.NONE,
                new com.reiko.signaltower.world.RadioTowerPiece.Plan(6, true, 4616, com.reiko.signaltower.radio.AntennaTier.NONE, 0, false, false, 0));
        BlockPos inside = piece.at(2, 1, 4);
        var chunkPos = new net.minecraft.world.level.ChunkPos(inside);
        var chunk = level.getChunk(inside);
        var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure, chunkPos, 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece)));
        try {
            chunk.setStartForStructure(structure, start);
            chunk.addReferenceForStructure(structure, chunkPos.toLong());
            climber.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
            check(playerCriterion(advancement(level, "lost_signal"), "found_a_tower", entityContext(level, climber)), "Lost Signal: inside an old tower");
            BlockPos outside = new BlockPos(piece.getBoundingBox().maxX() + 2, inside.getY(), inside.getZ());
            climber.moveTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
            check(!playerCriterion(advancement(level, "lost_signal"), "found_a_tower", entityContext(level, climber)), "Not outside it");
        } finally {
            var starts = new java.util.HashMap<>(chunk.getAllStarts());
            starts.remove(structure);
            chunk.setAllStarts(starts);
            var references = new java.util.HashMap<>(chunk.getAllReferences());
            references.remove(structure);
            chunk.setAllReferences(references);
        }
        helper.succeed();
    }

    /**
     * The tower events. A transmitter on the air fires "on_air" for whoever tuned it, with its reach
     * (First Broadcast; Over the Horizon at 500 or more); one off the air doesn't, nor does a jammer. An
     * old tower off the air (with a lever) is quiet; switched on it fires "found_tower_revived" for
     * everyone within 24 blocks (Finders Keepers), once; quiet again and back on, again. Saved.
     */
    @GameTest(template = TOWER_SITE, timeoutTicks = 300)
    public static void towerAdvancementEvents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var fired = com.reiko.signaltower.advancement.TowerAdvancements.recordForTests();
        var plan = new com.reiko.signaltower.world.RadioTowerPiece.Plan(5, false, 4616,
                com.reiko.signaltower.radio.AntennaTier.NONE, 0, false, false, 0);
        var piece = new com.reiko.signaltower.world.RadioTowerPiece(helper.absolutePos(new BlockPos(1, 0, 3)),
                net.minecraft.world.level.block.Rotation.NONE, plan);
        piece.buildAll(level, level.getChunkSource().getGenerator(), level.getRandom());
        BlockPos top = piece.top();
        BlockPos lever = piece.at(6, 1, 4);
        var transmitter = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(top);
        BlockPos plain = new BlockPos(14, 1, 14);
        place(helper, plain, ModRegistry.RADIO_TRANSMITTER.get(), 4617);
        UUID owner = UUID.randomUUID();
        ((RadioBlockEntity) helper.getBlockEntity(plain)).setTunedBy(owner);
        BlockPos jammer = new BlockPos(14, 1, 1);
        place(helper, jammer, ModRegistry.RADIO_JAMMER.get(), 4618);
        ((RadioBlockEntity) helper.getBlockEntity(jammer)).setTunedBy(owner);
        helper.setBlock(jammer.east(), Blocks.REDSTONE_BLOCK);
        BlockPos absPlain = helper.absolutePos(plain);
        BlockPos absJammer = helper.absolutePos(jammer);
        String onAir = com.reiko.signaltower.advancement.TowerAdvancements.ON_AIR;
        String revived = com.reiko.signaltower.advancement.TowerAdvancements.FOUND_TOWER_REVIVED;
        java.util.function.BiFunction<BlockPos, String, List<com.reiko.signaltower.advancement.TowerAdvancements.Fired>> at =
                (pos, event) -> fired.stream().filter(f -> f.pos().equals(pos) && f.event().equals(event)).toList();
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    check(transmitter.isFoundTower() && transmitter.isFoundQuiet(), "The old tower is quiet");
                    check(at.apply(top, onAir).isEmpty() && at.apply(top, revived).isEmpty(), "Nothing from it yet");
                    check(at.apply(absPlain, onAir).isEmpty(), "Nothing from a transmitter off the air");
                    check(at.apply(absJammer, onAir).isEmpty(), "Nothing from a jammer, jamming");
                    level.setBlock(lever, level.getBlockState(lever).setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true), Block.UPDATE_ALL);
                    helper.setBlock(plain.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(at.apply(top, revived).size() == 1, "Switched on: Finders Keepers"))
                .thenWaitUntil(() -> check(!at.apply(absPlain, onAir).isEmpty(), "The other transmitter on the air"))
                .thenExecute(() -> {
                    var back = at.apply(top, revived).get(0);
                    check(back.radius() == com.reiko.signaltower.advancement.TowerAdvancements.REVIVED_RADIUS && back.tuner() == null,
                            "For everyone near it: " + back);
                    check(!transmitter.isFoundQuiet() && !at.apply(top, onAir).isEmpty(), "No longer quiet, and on the air");
                    var first = at.apply(absPlain, onAir).get(0);
                    int reach = com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.reachOf(level, absPlain, level.getBlockState(absPlain));
                    check(owner.equals(first.tuner()) && first.value() == reach && first.radius() == 0, "On the air: for its tuner, with its reach (" + reach + "): " + first);
                    check(at.apply(absPlain, revived).isEmpty(), "Finders Keepers only for an old tower");
                    CompoundTag saved = transmitter.saveWithoutMetadata(level.registryAccess());
                    check(saved.contains("found_quiet") && !saved.getBoolean("found_quiet"), "Saved");
                    level.setBlock(lever, level.getBlockState(lever).setValue(net.minecraft.world.level.block.LeverBlock.POWERED, false), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> check(transmitter.isFoundQuiet(), "Switched off: quiet again"))
                .thenExecute(() -> {
                    check(at.apply(top, revived).size() == 1, "Still only once");
                    level.setBlock(lever, level.getBlockState(lever).setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> check(at.apply(top, revived).size() == 2, "Back on: again"))
                .thenExecute(() -> check(at.apply(absJammer, onAir).isEmpty(), "Never from the jammer"))
                .thenSucceed();
    }

    /** v0.56.0: the Radio Operator test, in a batch of its own (villagers look 48 blocks round for a workstation). */
    private static final String OPERATORS = "radio_operators";

    /**
     * Radio Operators: every Radio Telegraph (any facing, keyed or not) is their workstation, one any
     * villager can take; the profession is registered. Their trades, by level: redstone and copper
     * for emeralds, masts and a tuner (Novice) ... platforms, an iron antenna and a battery
     * (Journeyman), a gold antenna and solar panel (Expert), a Diamond Antenna for 24 emeralds and a
     * diamond (Master); a new one has 2 Novice offers. Call Sign is for trading with one (not a
     * librarian). A villager with no job beside a Radio Telegraph walks over and becomes one.
     */
    @GameTest(template = PLATFORM, batch = OPERATORS, timeoutTicks = 1200)
    public static void radioOperators(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockState state : ModRegistry.RADIO_TELEGRAPH.get().getStateDefinition().getPossibleStates()) {
            var poi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state);
            check(poi.isPresent() && poi.get().is(com.reiko.signaltower.world.ModVillagers.RADIO_OPERATOR_POI_KEY), "A workstation: " + state);
        }
        var poi = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(com.reiko.signaltower.world.ModVillagers.RADIO_OPERATOR_POI_KEY);
        check(poi.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), "One any villager can take");
        var operator = com.reiko.signaltower.world.ModVillagers.RADIO_OPERATOR.get();
        check(modId("radio_operator").equals(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(operator))
                && operator.heldJobSite().test(poi) && operator.acquirableJobSite().test(poi), "The Radio Operator");
        var trades = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(operator);
        check(trades != null, "Its trades");
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        var trader = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 1, 1));
        trader.setNoAi(true);
        trader.setVillagerData(trader.getVillagerData().setProfession(operator).setLevel(1));
        java.util.Map<Integer, List<net.minecraft.world.item.trading.MerchantOffer>> byLevel = new java.util.HashMap<>();
        for (int villagerLevel = 1; villagerLevel <= 5; villagerLevel++) {
            var listings = trades.get(villagerLevel);
            check(listings != null && listings.length >= 2, "Trades at level " + villagerLevel);
            List<net.minecraft.world.item.trading.MerchantOffer> offers = new java.util.ArrayList<>();
            for (var listing : listings) {
                offers.add(listing.getOffer(trader, level.getRandom()));
            }
            byLevel.put(villagerLevel, offers);
        }
        java.util.function.BiPredicate<Integer, java.util.function.Predicate<net.minecraft.world.item.trading.MerchantOffer>> has =
                (lvl, test) -> byLevel.get(lvl).stream().anyMatch(test);
        check(has.test(1, o -> o.getCostA().is(Items.REDSTONE) && o.getCostA().getCount() == 12 && o.getResult().is(Items.EMERALD))
                && has.test(1, o -> o.getCostA().is(Items.COPPER_INGOT) && o.getResult().is(Items.EMERALD)), "Novice: buys redstone and copper");
        check(has.test(1, o -> o.getResult().is(ModRegistry.RADIO_MAST_ITEM.get()) && o.getResult().getCount() == 4 && o.getCostA().is(Items.EMERALD))
                && has.test(1, o -> o.getResult().is(ModRegistry.FREQUENCY_TUNER.get())), "Novice: sells masts and a tuner");
        check(has.test(2, o -> o.getResult().is(ModRegistry.RADIO_TRANSMITTER_ITEM.get())) && has.test(2, o -> o.getResult().is(ModRegistry.RADIO_RECEIVER_ITEM.get())),
                "Apprentice: transmitters and receivers");
        check(has.test(3, o -> o.getResult().is(ModRegistry.TOWER_PLATFORM_ITEM.get()) && o.getResult().getCount() == 8)
                && has.test(3, o -> o.getResult().is(ModRegistry.IRON_ANTENNA.get())) && has.test(3, o -> o.getResult().is(ModRegistry.RADIO_BATTERY_ITEM.get())),
                "Journeyman: platforms, an iron antenna, a battery");
        check(has.test(4, o -> o.getResult().is(ModRegistry.GOLD_ANTENNA.get())) && has.test(4, o -> o.getResult().is(ModRegistry.SOLAR_PANEL_ITEM.get())),
                "Expert: a gold antenna, a solar panel");
        check(has.test(5, o -> o.getResult().is(ModRegistry.DIAMOND_ANTENNA.get()) && o.getCostA().is(Items.EMERALD) && o.getCostA().getCount() == 24
                && o.getCostB().is(Items.DIAMOND)), "Master: a Diamond Antenna for 24 emeralds and a diamond");
        var offers = trader.getOffers();
        check(offers.size() == 2 && offers.stream().allMatch(o -> has.test(1, n -> ItemStack.isSameItemSameComponents(n.getResult(), o.getResult()))),
                "A new one: 2 Novice offers: " + offers.size());
        var callSign = (net.minecraft.advancements.critereon.TradeTrigger.TriggerInstance) advancement(level, "call_sign").criteria().get("traded").triggerInstance();
        check(callSign.villager().orElseThrow().matches(entityContext(level, trader)), "Call Sign: trading with a Radio Operator");
        var librarian = helper.spawn(EntityType.VILLAGER, new BlockPos(22, 1, 1));
        librarian.setNoAi(true);
        librarian.setVillagerData(librarian.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
        check(!callSign.villager().orElseThrow().matches(entityContext(level, librarian)), "Not with a librarian");
        librarian.discard();

        helper.setBlock(new BlockPos(9, 1, 1), ModRegistry.RADIO_TELEGRAPH.get());
        var jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));
        check(jobless.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE, "No job yet");
        helper.startSequence()
                .thenWaitUntil(() -> check(jobless.getVillagerData().getProfession() == operator,
                        "Took the telegraph: " + jobless.getVillagerData().getProfession()))
                .thenExecute(() -> {
                    var site = jobless.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
                    check(site.isPresent() && site.get().pos().equals(helper.absolutePos(new BlockPos(9, 1, 1))), "Its job site: the telegraph");
                    jobless.discard();
                    trader.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- v0.57.0: JEI / EMI, village radio shacks

    private static com.google.gson.JsonObject modLang() {
        try (var in = SignalTower.class.getResourceAsStream("/assets/" + SignalTower.MOD_ID + "/lang/en_us.json")) {
            check(in != null, "en_us.json on the classpath");
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Couldn't read en_us.json: " + e);
        }
    }

    /**
     * What JEI and EMI show: every one of the mod's items (all 50) has an information page, with its
     * text in en_us.json; the trades page has a title. The trades listed are the Radio Operator's own:
     * as many at each level as the villager has, with the same results.
     */
    @GameTest(template = PLATFORM)
    public static void recipeViewerInfo(GameTestHelper helper) {
        var lang = modLang();
        var items = com.reiko.signaltower.compat.RecipeViewerInfo.items();
        check(items.size() == 50, "All the mod's items: " + items.size());
        for (var item : items) {
            String key = com.reiko.signaltower.compat.RecipeViewerInfo.infoKey(item);
            check(lang.has(key) && lang.get(key).getAsString().length() > 40, "An information page for " + item + ": " + key);
        }
        check(lang.has(com.reiko.signaltower.compat.RecipeViewerInfo.TRADES_TITLE_KEY), "The trades page's title");
        var trades = com.reiko.signaltower.compat.RecipeViewerInfo.trades();
        check(trades.size() == 17, "17 trades: " + trades.size());
        var villager = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(com.reiko.signaltower.world.ModVillagers.RADIO_OPERATOR.get());
        var trader = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
        trader.setNoAi(true);
        for (int level = 1; level <= 5; level++) {
            int at = level;
            var listed = trades.stream().filter(t -> t.level() == at).toList();
            var listings = villager.get(level);
            check(listings.length == listed.size(), "Level " + level + ": " + listings.length + " trades, " + listed.size() + " shown");
            for (int i = 0; i < listings.length; i++) {
                var offer = listings[i].getOffer(trader, helper.getLevel().getRandom());
                var shown = listed.get(i);
                check(ItemStack.isSameItemSameComponents(offer.getResult(), shown.result()) && offer.getResult().getCount() == shown.result().getCount()
                        && offer.getCostA().is(shown.costA().getItem()) && offer.getCostA().getCount() == shown.costA().getCount()
                        && (shown.costB().isEmpty() ? offer.getCostB().isEmpty() : offer.getCostB().is(shown.costB().getItem())),
                        "Shown as traded: " + shown);
            }
        }
        trader.discard();
        helper.succeed();
    }

    /**
     * The five village types' house pools each hold the radio shack 3 times (the config's weight), and
     * adding again changes nothing. Each template loads: 8 x 7 x 7, a Radio Telegraph, a bed, 4 masts,
     * a transmitter marked as a village station, a charged battery, a "Radio Station" sign, a jigsaw
     * meeting the type's streets and one bringing a villager from its villagers pool (which exist).
     */
    @GameTest(template = PLATFORM)
    public static void villageShackPools(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var access = level.registryAccess();
        var pools = access.registryOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
        int weight = com.reiko.signaltower.Config.VILLAGE_RADIO_SHACKS.get();
        check(weight == 3, "Weight 3 by default: " + weight);
        check(com.reiko.signaltower.world.VillageRadioShacks.addToVillages(access, weight) == 0, "Adding again changes nothing");
        for (String type : com.reiko.signaltower.world.VillageRadioShacks.TYPES) {
            var pool = pools.get(com.reiko.signaltower.world.VillageRadioShacks.housesPool(type));
            check(pool != null, "The " + type + " houses pool");
            check(com.reiko.signaltower.world.VillageRadioShacks.count(pool, type) == weight, type + ": the shack " + weight + " times");
            check(pools.containsKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/" + type + "/streets"))
                    && pools.containsKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/" + type + "/villagers")), type + "'s streets and villagers");
            var template = level.getStructureManager().get(com.reiko.signaltower.world.VillageRadioShacks.template(type));
            check(template.isPresent(), type + "'s template");
            var size = template.get().getSize();
            check(size.getX() == 8 && size.getY() == 7 && size.getZ() == 7, type + " is 8 x 7 x 7: " + size);
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            java.util.Map<Block, Integer> counts = new java.util.HashMap<>();
            boolean station = false, street = false, villagerJigsaw = false, signed = false;
            var all = new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>();
            for (Block block : new Block[]{ModRegistry.RADIO_TELEGRAPH.get(), ModRegistry.RADIO_MAST.get(), ModRegistry.RADIO_TRANSMITTER.get(),
                    ModRegistry.RADIO_BATTERY.get(), Blocks.JIGSAW}) {
                var found = template.get().filterBlocks(BlockPos.ZERO, settings, block, false);
                counts.put(block, found.size());
                all.addAll(found);
            }
            for (var info : all) {
                if (info.state().is(ModRegistry.RADIO_TRANSMITTER.get())) {
                    station = info.nbt() != null && info.nbt().getBoolean("village_station") && info.nbt().getBoolean("found_tower");
                } else if (info.state().is(ModRegistry.RADIO_BATTERY.get())) {
                    check(info.nbt() != null && info.nbt().getInt("charge") == 80000, type + ": a charged battery");
                } else if (info.state().is(Blocks.JIGSAW) && info.nbt() != null) {
                    String target = info.nbt().getString("pool");
                    street |= info.nbt().getString("name").equals("minecraft:building_entrance") && target.equals("minecraft:village/" + type + "/streets");
                    villagerJigsaw |= info.nbt().getString("name").equals("minecraft:bottom") && target.equals("minecraft:village/" + type + "/villagers");
                }
            }
            check(counts.get(ModRegistry.RADIO_TELEGRAPH.get()) == 1 && counts.get(ModRegistry.RADIO_MAST.get()) == 4
                    && counts.get(ModRegistry.RADIO_TRANSMITTER.get()) == 1 && counts.get(ModRegistry.RADIO_BATTERY.get()) == 1
                    && counts.get(Blocks.JIGSAW) == 2, type + ": telegraph, 4 masts, transmitter, battery, 2 jigsaws: " + counts);
            check(station, type + ": the transmitter is a village station");
            check(street && villagerJigsaw, type + ": meets the street, brings a villager");
            for (Block sign : new Block[]{Blocks.OAK_WALL_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN}) {
                for (var info : template.get().filterBlocks(BlockPos.ZERO, settings, sign, false)) {
                    signed |= info.nbt() != null && info.nbt().getCompound("front_text").getList("messages", 8).getString(0)
                            .contains(com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.STATION_SIGN_KEY);
                }
            }
            check(signed, type + ": a Radio Station sign");
            boolean bed = false;
            for (Block b : new Block[]{Blocks.WHITE_BED, Blocks.CYAN_BED, Blocks.ORANGE_BED, Blocks.LIGHT_BLUE_BED, Blocks.BROWN_BED}) {
                bed |= template.get().filterBlocks(BlockPos.ZERO, settings, b, false).size() == 2;
            }
            check(bed, type + ": a bed");
        }
        helper.succeed();
    }

    /**
     * Two plains radio shacks placed side by side (one turned): within a second each transmitter has
     * picked its own frequency (100,000 to 999,999, not the template's 1, not each other's), found its
     * sign and written the frequency on it, and is on the air (lit, by its torch). Jade calls it a
     * village's radio station, not an old tower. Its torch taken away and put back, it fires no
     * Finders Keepers (that's for old towers).
     */
    @GameTest(template = TOWER_SITE, timeoutTicks = 200)
    public static void villageRadioShack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var fired = com.reiko.signaltower.advancement.TowerAdvancements.recordForTests();
        var template = level.getStructureManager().get(com.reiko.signaltower.world.VillageRadioShacks.template("plains")).orElseThrow();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos[] origins = {new BlockPos(0, 1, 0), new BlockPos(15, 1, 8)};
        net.minecraft.world.level.block.Rotation[] turns = {net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Rotation.CLOCKWISE_90};
        BlockPos[] tops = new BlockPos[2];
        for (int i = 0; i < 2; i++) {
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(turns[i])
                    .addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE)
                    .addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            BlockPos origin = helper.absolutePos(origins[i]);
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
            tops[i] = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.calculateRelativePosition(settings, new BlockPos(6, 5, 3)).offset(origin);
            check(level.getBlockState(tops[i]).is(ModRegistry.RADIO_TRANSMITTER.get()), "Transmitter " + i + " at " + tops[i]);
        }
        var first = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(tops[0]);
        var second = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(tops[1]);
        BlockPos torch = tops[0].below(4).north();
        helper.startSequence()
                .thenWaitUntil(() -> check(level.getBlockState(tops[0]).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED)
                        && level.getBlockState(tops[1]).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Both on the air"))
                .thenExecute(() -> {
                    for (var station : new com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity[]{first, second}) {
                        int f = station.getFrequency();
                        check(station.isVillageStation() && station.isFoundTower(), "A village station");
                        check(f >= 100_000 && f < 1_000_000, "Its own frequency: " + f);
                        CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, station.getBlockPos());
                        check(info.getBoolean("village_station") && !info.getBoolean("found_tower"), "Jade: a village's radio station");
                    }
                    check(first.getFrequency() != second.getFrequency(), "Not the same: " + first.getFrequency());
                    for (int i = 0; i < 2; i++) {
                        int f = ((com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(tops[i])).getFrequency();
                        boolean written = false;
                        for (BlockPos pos : BlockPos.betweenClosed(tops[i].offset(-8, -6, -8), tops[i].offset(8, 0, 8))) {
                            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                                written |= sign.getFrontText().getMessage(2, false).getString().equals(Integer.toString(f));
                            }
                        }
                        check(written, "Shack " + i + "'s sign says " + f);
                    }
                    check(level.getBlockState(torch).is(Blocks.REDSTONE_TORCH), "Its torch at " + torch);
                    level.setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> check(!level.getBlockState(tops[0]).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Off the air"))
                .thenExecute(() -> {
                    check(!first.isFoundQuiet(), "Not an old tower gone quiet");
                    level.setBlock(torch, Blocks.REDSTONE_TORCH.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> check(level.getBlockState(tops[0]).getValue(com.reiko.signaltower.block.RadioTransmitterBlock.POWERED), "Back on"))
                .thenIdle(5)
                .thenExecute(() -> check(fired.stream().noneMatch(f -> f.pos().equals(tops[0])
                        && f.event().equals(com.reiko.signaltower.advancement.TowerAdvancements.FOUND_TOWER_REVIVED)), "No Finders Keepers"))
                .thenSucceed();
    }

    // ---- v0.58.0: computers (CC: Tweaked) and raid alerts ----

    /** Records a computer's events as "rx signal 4619 9" or "tb message 4621 text|from". */
    private static com.reiko.signaltower.compat.RadioComputer.Listener recording(List<String> events, String name) {
        return new com.reiko.signaltower.compat.RadioComputer.Listener() {
            @Override
            public void signal(int frequency, int strength) {
                events.add(name + " signal " + frequency + " " + strength);
            }

            @Override
            public void message(int frequency, String text, String from) {
                events.add(name + " message " + frequency + " " + text + "|" + from);
            }
        };
    }

    /**
     * What a computer can do (CC: Tweaked's peripherals only call these): a transmitter sends what a
     * computer sets, and stops when the last computer goes; a receiver's computers get "radio_signal"
     * when what it hears changes, and it can listen on any frequency and scan; a transmitter can't be
     * tuned onto a private frequency its tuner isn't allowed on (a receiver can), or out of range; a
     * telegraph sends as "Computer 7", the other one's computers get "radio_message", and its
     * messages are listed; too fast and empty are refused.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void computerRadio(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(5, 1, 1);
        BlockPos ta = new BlockPos(8, 1, 1);
        BlockPos tb = new BlockPos(11, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4619);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4619);
        place(helper, ta, ModRegistry.RADIO_TELEGRAPH.get(), 4621);
        place(helper, tb, ModRegistry.RADIO_TELEGRAPH.get(), 4621);
        BlockPos absTx = helper.absolutePos(tx);
        BlockPos absRx = helper.absolutePos(rx);
        BlockPos absTb = helper.absolutePos(tb);
        com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity transmitter = helper.getBlockEntity(tx);
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity receiver = helper.getBlockEntity(rx);
        com.reiko.signaltower.blockentity.TelegraphBlockEntity telegraphA = helper.getBlockEntity(ta);
        com.reiko.signaltower.blockentity.TelegraphBlockEntity telegraphB = helper.getBlockEntity(tb);
        List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        var onTx = recording(events, "tx");
        var onRx = recording(events, "rx");
        var onTb = recording(events, "tb");
        com.reiko.signaltower.compat.RadioComputer.attach(level, absTx, onTx);
        com.reiko.signaltower.compat.RadioComputer.attach(level, absRx, onRx);
        com.reiko.signaltower.compat.RadioComputer.attach(level, absTb, onTb);
        claims.put(4620, UUID.randomUUID(), "Owner", java.util.Map.of());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(com.reiko.signaltower.compat.RadioComputer.status(transmitter).equals("quiet")
                            && com.reiko.signaltower.compat.RadioComputer.sending(transmitter) == 0, "Quiet at first");
                    transmitter.setComputerSignal(9);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 9, "The receiver hears what the computer sends"))
                .thenExecute(() -> {
                    check(com.reiko.signaltower.compat.RadioComputer.sending(transmitter) == 9
                            && com.reiko.signaltower.compat.RadioComputer.status(transmitter).equals("sending"), "Sending 9");
                    check(com.reiko.signaltower.compat.RadioComputer.reach(transmitter) > 0, "Its reach");
                    check(com.reiko.signaltower.compat.RadioComputer.listen(receiver, 4619) == 9
                            && com.reiko.signaltower.compat.RadioComputer.listen(receiver, 4622) == 0
                            && com.reiko.signaltower.compat.RadioComputer.output(receiver) == 9, "Listening on any frequency, and its output");
                    var scan = com.reiko.signaltower.compat.RadioComputer.scan(receiver);
                    check(scan.stream().anyMatch(row -> Integer.valueOf(4619).equals(row.get("frequency")) && Integer.valueOf(9).equals(row.get("strength"))),
                            "Scanned: " + scan);
                    check(events.contains("rx signal 4619 9"), "radio_signal: " + events);
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, absTx);
                    check(info.getInt("computer_signal") == 9 && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(tx), info))
                            .contains("computer_signal"), "Jade: what the computer sends " + info);
                    transmitter.setTunedBy(UUID.randomUUID());
                    check(com.reiko.signaltower.compat.RadioComputer.setFrequency(transmitter, 4620) != null && transmitter.getFrequency() == 4619,
                            "Not onto someone else's private frequency");
                    check(com.reiko.signaltower.compat.RadioComputer.setFrequency(transmitter, 0) != null
                            && com.reiko.signaltower.compat.RadioComputer.setFrequency(transmitter, 1_000_000_000L) != null, "Out of range");
                    check(com.reiko.signaltower.compat.RadioComputer.setFrequency(receiver, 4620) == null && receiver.getFrequency() == 4620
                            && com.reiko.signaltower.compat.RadioComputer.setFrequency(receiver, 4619) == null, "A receiver may listen to it");
                    com.reiko.signaltower.compat.RadioComputer.detach(level, absTx, onTx); // its last computer
                })
                .thenWaitUntil(() -> check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "Quiet when the computer goes"))
                .thenExecute(() -> {
                    check(transmitter.getComputerSignal() == 0 && events.contains("rx signal 4619 0"), "Back to 0: " + events);
                    check(com.reiko.signaltower.compat.RadioComputer.send(telegraphA, 7, "Hello from Lua") == null, "Sent");
                    check(events.contains("tb message 4621 Hello from Lua|Computer 7"), "radio_message: " + events);
                    var listed = com.reiko.signaltower.compat.RadioComputer.messages(telegraphB);
                    check(!listed.isEmpty() && "Hello from Lua".equals(listed.get(listed.size() - 1).get("text"))
                            && "Computer 7".equals(listed.get(listed.size() - 1).get("from")), "Listed: " + listed);
                    check(com.reiko.signaltower.compat.RadioComputer.send(telegraphA, 7, "again") != null
                            && com.reiko.signaltower.compat.RadioComputer.send(telegraphB, 7, "   ") != null, "Too fast, and empty: refused");
                    check(events.stream().noneMatch(e -> e.startsWith("tx ")), "A transmitter has no events");
                    com.reiko.signaltower.compat.RadioComputer.detach(level, absRx, onRx);
                    com.reiko.signaltower.compat.RadioComputer.detach(level, absTb, onTb);
                    check(!com.reiko.signaltower.compat.RadioComputer.watched(level, absRx), "Detached");
                    claims.remove(4620);
                })
                .thenSucceed();
    }

    /**
     * With CC: Tweaked loaded (the -PwithCct run), radio blocks are peripherals: transmitters
     * "radio_transmitter", every kind of receiver "radio_receiver", telegraphs "radio_telegraph"; a
     * mast isn't one. Without it, there's nothing to check.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void computerPeripherals(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded(com.reiko.signaltower.compat.cct.CctCompat.MOD_ID)) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos[] spots = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(7, 1, 1), new BlockPos(9, 1, 1), new BlockPos(11, 1, 1)};
        Block[] blocks = {ModRegistry.RADIO_TRANSMITTER.get(), ModRegistry.RADIO_RECEIVER.get(), ModRegistry.RADIO_TOGGLE_RECEIVER.get(),
                ModRegistry.RADIO_LAMP.get(), ModRegistry.RADIO_TELEGRAPH.get(), ModRegistry.RADIO_MAST.get()};
        String[] types = {"radio_transmitter", "radio_receiver", "radio_receiver", "radio_receiver", "radio_telegraph", null};
        for (int i = 0; i < spots.length; i++) {
            helper.setBlock(spots[i], blocks[i]);
        }
        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < spots.length; i++) {
                String type = com.reiko.signaltower.compat.cct.CctCompat.typeAt(level, helper.absolutePos(spots[i]));
                check(java.util.Objects.equals(type, types[i]), blocks[i] + ": " + type);
            }
            SignalTower.LOGGER.info("computerPeripherals: CC: Tweaked found the {} radio peripherals", spots.length - 1);
            helper.succeed();
        });
    }

    /** Places a plains radio shack at the tower site's corner (on stone) and returns its transmitter's position. */
    private static BlockPos placeShack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getStructureManager().get(com.reiko.signaltower.world.VillageRadioShacks.template("plains")).orElseThrow();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE)
                .addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
        return origin.offset(6, 5, 3);
    }

    /**
     * Raid alerts, step by step: a raid near a village station puts it on the air at 15 (its torch
     * gone) and it telegraphs "RAID!" as "Village Radio" (a telegraph on its frequency pulses); each
     * new wave "Raid: wave 2 of 5" (once); a won raid "the village held", and it goes quiet again; a
     * raid that just goes away "The raid has ended"; one already over when it's first seen, nothing;
     * a lost one "the village fell". Jade shows the raid; it's saved.
     */
    @GameTest(template = TOWER_SITE, timeoutTicks = 200)
    public static void raidAlertMessages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = placeShack(helper);
        BlockPos torch = top.below(4).north();
        BlockPos telegraphAt = new BlockPos(12, 1, 12);
        helper.setBlock(telegraphAt, ModRegistry.RADIO_TELEGRAPH.get());
        var poweredKey = com.reiko.signaltower.block.RadioTransmitterBlock.POWERED;
        helper.startSequence()
                .thenWaitUntil(() -> check(level.getBlockState(top).getValue(poweredKey), "On the air"))
                .thenExecute(() -> {
                    var station = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(top);
                    setFrequency(helper, telegraphAt, station.getFrequency());
                    level.setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> check(!level.getBlockState(top).getValue(poweredKey), "Off the air without its torch"))
                .thenIdle(2)
                .thenExecute(() -> {
                    var station = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(top);
                    int f = station.getFrequency();
                    SignalNetwork network = SignalNetwork.get(level);
                    int before = network.messages(f).size();
                    var ongoing = com.reiko.signaltower.radio.RaidAlerts.Status.ONGOING;
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_001, 0, 5, ongoing));
                    var last = network.messages(f).get(network.messages(f).size() - 1);
                    check(station.isRaidAlert() && level.getBlockState(top).getValue(poweredKey)
                            && com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.inputOf(level, top, level.getBlockState(top)) == 15,
                            "On the air at 15 for the raid");
                    check(last.text().equals("RAID! Pillagers are coming to the village") && last.name().equals("Village Radio"), "RAID!: " + last);
                    check(helper.getBlockState(telegraphAt).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED), "A telegraph on it pulses");
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, top);
                    check(info.getInt("raid_waves") == 5 && info.getInt("raid_wave") == 0
                            && lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(level.getBlockState(top), info)).contains("raid_coming"),
                            "Jade: the raid's coming " + info);
                    CompoundTag saved = station.saveWithoutMetadata(level.registryAccess());
                    check(saved.getInt("raid_id") == 900_001 && saved.getInt("raid_waves") == 5, "Saved: " + saved);
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_001, 1, 5, ongoing));
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_001, 1, 5, ongoing));
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_001, 2, 5, ongoing));
                    List<String> texts = network.messages(f).stream().map(SignalNetwork.Message::text).toList();
                    check(texts.subList(before, texts.size()).equals(List.of("RAID! Pillagers are coming to the village",
                            "Raid: wave 1 of 5", "Raid: wave 2 of 5")), "Each wave once: " + texts);
                    info = com.reiko.signaltower.compat.RadioInfo.read(level, top);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(level.getBlockState(top), info)).contains("raid_alert"), "Jade: wave 2 of 5");
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_001, 5, 5,
                            com.reiko.signaltower.radio.RaidAlerts.Status.VICTORY));
                    check(!station.isRaidAlert() && !level.getBlockState(top).getValue(poweredKey), "Quiet again after the raid");
                    station.updateRaid(level, null);
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_002, 0, 3, ongoing));
                    station.updateRaid(level, null);
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_003, 2, 3,
                            com.reiko.signaltower.radio.RaidAlerts.Status.LOSS));
                    check(!station.isRaidAlert(), "Not for a raid already over");
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_004, 0, 3, ongoing));
                    station.updateRaid(level, new com.reiko.signaltower.radio.RaidAlerts.Snapshot(900_004, 2, 3,
                            com.reiko.signaltower.radio.RaidAlerts.Status.LOSS));
                    texts = network.messages(f).stream().map(SignalNetwork.Message::text).toList();
                    check(texts.subList(before, texts.size()).equals(List.of("RAID! Pillagers are coming to the village",
                            "Raid: wave 1 of 5", "Raid: wave 2 of 5", "The raid is over: the village held",
                            "RAID! Pillagers are coming to the village", "The raid has ended",
                            "RAID! Pillagers are coming to the village", "The raid is over: the village fell")), "All the messages: " + texts);
                    check(!station.isRaidAlert() && !station.saveWithoutMetadata(level.registryAccess()).contains("raid_id"), "Nothing left of it");
                })
                .thenSucceed();
    }

    /** v0.58.0: the real raid test, in a batch of its own (a raid reaches 96 blocks, and its pillagers further). */
    private static final String RAIDS = "raid_alerts";

    @net.minecraft.gametest.framework.BeforeBatch(batch = RAIDS)
    public static void beforeRaids(ServerLevel level) {
        withoutFade(level);
    }

    /**
     * A real raid: started (as a player with a bad omen would) at a village radio shack whose bed
     * is someone's home, the station picks it up within 2 seconds and telegraphs "RAID!"; Jade
     * shows the raid coming (5 waves, Normal); the raid stopped, it telegraphs "The raid has ended".
     */
    @GameTest(template = TOWER_SITE, batch = RAIDS, timeoutTicks = 300)
    public static void raidNearVillageStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = placeShack(helper);
        var poweredKey = com.reiko.signaltower.block.RadioTransmitterBlock.POWERED;
        net.minecraft.world.entity.raid.Raid[] raid = new net.minecraft.world.entity.raid.Raid[1];
        BlockPos[] home = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(level.getBlockState(top).getValue(poweredKey), "On the air"))
                .thenExecute(() -> {
                    var taken = level.getPoiManager().take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                            (type, pos) -> true, top, 16);
                    check(taken.isPresent(), "The shack's bed is a home");
                    home[0] = taken.get();
                    var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
                    player.moveTo(home[0].getX() + 0.5, home[0].getY(), home[0].getZ() + 0.5);
                    raid[0] = level.getRaids().createOrExtendRaid(player, home[0]);
                    check(raid[0] != null, "A raid");
                })
                .thenWaitUntil(() -> {
                    var station = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(top);
                    var messages = SignalNetwork.get(level).messages(station.getFrequency());
                    check(station.isRaidAlert() && !messages.isEmpty()
                            && messages.get(messages.size() - 1).text().equals(com.reiko.signaltower.radio.RaidAlerts.startText()), "RAID! telegraphed");
                })
                .thenExecute(() -> {
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, top);
                    check(info.getInt("raid_waves") == 5 && info.getInt("raid_wave") == 0, "Jade: 5 waves coming " + info);
                    raid[0].stop();
                })
                .thenWaitUntil(() -> {
                    var station = (com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) level.getBlockEntity(top);
                    var messages = SignalNetwork.get(level).messages(station.getFrequency());
                    check(!station.isRaidAlert() && messages.get(messages.size() - 1).text().equals(com.reiko.signaltower.radio.RaidAlerts.endText(null)),
                            "The raid has ended");
                })
                .thenExecute(() -> level.getPoiManager().release(home[0]))
                .thenSucceed();
    }

    // ---- v0.59.0: the mod's own sounds, and the radio in turtles and pocket computers ----

    /** Reads a resource of the mod from the classpath (null if it isn't there). */
    private static byte[] modResource(String path) {
        try (var in = SignalTowerGameTests.class.getResourceAsStream("/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * The mod's own sounds: all ten are registered; sounds.json lists each with a subtitle that
     * en_us.json has, and each one's file is there, an Ogg Vorbis file, mono (so it's heard from
     * where it's played) at 44.1 kHz. The Speaker's three new sounds are the mod's, the Radio
     * Operator works its telegraph's key. A new Radio Transmitter coming on the air plays its "on"
     * sound; going off (2 seconds later), its "off" sound; again at once, nothing (too soon); a
     * compact one makes none.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void radioSounds(GameTestHelper helper) {
        com.google.gson.JsonObject lang = modLang();
        com.google.gson.JsonObject sounds = com.google.gson.JsonParser.parseString(new String(
                modResource("assets/reikos_signal_tower/sounds.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check(com.reiko.signaltower.registry.ModSounds.ALL.size() == 10 && sounds.size() == 10, "Ten sounds: " + sounds.keySet());
        for (var holder : com.reiko.signaltower.registry.ModSounds.ALL) {
            String event = holder.getId().getPath();
            check(holder.isBound() && holder.get().getLocation().equals(holder.getId()), "Registered: " + event);
            var entry = sounds.getAsJsonObject(event);
            check(entry != null && lang.has(entry.get("subtitle").getAsString()), "Listed, with a subtitle: " + event);
            for (var file : entry.getAsJsonArray("sounds")) {
                String name = file.getAsString().substring("reikos_signal_tower:".length());
                byte[] ogg = modResource("assets/reikos_signal_tower/sounds/" + name + ".ogg");
                check(ogg != null && ogg.length > 100 && new String(ogg, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("OggS"), "Its file: " + name);
                int packet = 27 + (ogg[26] & 0xFF); // the first page's header, then its segment table
                check(new String(ogg, packet + 1, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("vorbis"), "Vorbis: " + name);
                int channels = ogg[packet + 11] & 0xFF;
                int rate = (ogg[packet + 12] & 0xFF) | (ogg[packet + 13] & 0xFF) << 8 | (ogg[packet + 14] & 0xFF) << 16;
                check(channels == 1 && rate == 44100, name + ": " + channels + " channels, " + rate + " Hz");
            }
        }
        check(com.reiko.signaltower.radio.SpeakerSound.SIREN.sound() == com.reiko.signaltower.registry.ModSounds.STATION_SIREN.get()
                && com.reiko.signaltower.radio.SpeakerSound.PAGER.sound() == com.reiko.signaltower.registry.ModSounds.PAGER_BEEP.get()
                && com.reiko.signaltower.radio.SpeakerSound.STATIC.sound() == com.reiko.signaltower.registry.ModSounds.TUNER_TUNE.get(), "The Speaker's new sounds");
        for (var sound : List.of(com.reiko.signaltower.radio.SpeakerSound.PAGER, com.reiko.signaltower.radio.SpeakerSound.STATIC, com.reiko.signaltower.radio.SpeakerSound.SIREN)) {
            check(lang.has(((net.minecraft.network.chat.contents.TranslatableContents) sound.label().getContents()).getKey()), "Its name: " + sound);
        }
        check(com.reiko.signaltower.world.ModVillagers.RADIO_OPERATOR.get().workSound() == com.reiko.signaltower.registry.ModSounds.TELEGRAPH_KEY.get(),
                "A Radio Operator works its key");

        BlockPos tx = new BlockPos(2, 1, 2);
        BlockPos compact = new BlockPos(8, 1, 2);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4623);
        helper.setBlock(compact.below(), Blocks.STONE);
        helper.setBlock(compact, ModRegistry.COMPACT_RADIO_TRANSMITTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        setFrequency(helper, compact, 4623);
        com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity tower = helper.getBlockEntity(tx);
        com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity small = helper.getBlockEntity(compact);
        var powered = com.reiko.signaltower.block.RadioTransmitterBlock.POWERED;
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(tower.lastAirSoundForTests() == null, "No sound before it sends");
                    helper.setBlock(tx.east(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(compact.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(tx).getValue(powered) && helper.getBlockState(compact).getValue(powered), "On the air"))
                .thenExecute(() -> check(Boolean.TRUE.equals(tower.lastAirSoundForTests()) && small.lastAirSoundForTests() == null,
                        "The tower's \"on\" sound; none for the compact one"))
                .thenIdle(com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity.AIR_SOUND_GAP_TICKS + 2)
                .thenExecute(() -> helper.setBlock(tx.east(), Blocks.AIR))
                .thenWaitUntil(() -> check(!helper.getBlockState(tx).getValue(powered), "Off the air"))
                .thenExecute(() -> {
                    check(Boolean.FALSE.equals(tower.lastAirSoundForTests()), "Its \"off\" sound");
                    helper.setBlock(tx.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(tx).getValue(powered), "Back on"))
                .thenExecute(() -> check(Boolean.FALSE.equals(tower.lastAirSoundForTests()), "Too soon for another sound"))
                .thenSucceed();
    }

    /** A turtle or pocket computer's radio, for the tests: it can be moved, and keeps the messages it gets. */
    private static final class TestDevice implements com.reiko.signaltower.compat.MobileRadio.Device {
        private final ServerLevel level;
        private BlockPos pos;
        private final int frequency;
        private final UUID owner = UUID.randomUUID();
        private final List<String> got = new java.util.concurrent.CopyOnWriteArrayList<>();

        TestDevice(ServerLevel level, BlockPos pos, int frequency) {
            this.level = level;
            this.pos = pos;
            this.frequency = frequency;
        }

        public ServerLevel level() { return level; }
        public BlockPos position() { return pos; }
        public int frequency() { return frequency; }
        public UUID owner() { return owner; }
        public void message(int f, String text, String from) { got.add(f + " " + text + "|" + from); }
    }

    /**
     * A radio that moves (CC: Tweaked's turtle and pocket peripheral only calls these): it hears a
     * tower where it is, and scans; its pulse turns a receiver in reach on for the pulse and off
     * again, heard by one; it sends a telegraph message ("Turtle 3") that a telegraph on its frequency
     * reads (it doesn't get its own); a telegraph's message reaches it while it's in reach, not once
     * it's moved far away; on someone else's private frequency its pulse and messages are refused.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void mobileRadio(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var claims = com.reiko.signaltower.radio.FrequencyClaims.get(level.getServer());
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos rx = new BlockPos(8, 1, 1);
        BlockPos tg = new BlockPos(11, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4624);
        helper.setBlock(tx.east(), Blocks.REDSTONE_BLOCK);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4625);
        place(helper, tg, ModRegistry.RADIO_TELEGRAPH.get(), 4625);
        TestDevice turtle = new TestDevice(level, helper.absolutePos(new BlockPos(5, 1, 5)), 4625);
        com.reiko.signaltower.compat.MobileRadio.add(turtle);
        claims.put(4626, UUID.randomUUID(), "Owner", java.util.Map.of());
        int[] heardBy = new int[1];
        helper.startSequence()
                .thenIdle(2) // the receiver checks in with the network on its first tick
                .thenWaitUntil(() -> check(com.reiko.signaltower.compat.MobileRadio.heard(level, turtle.position(), 4624, turtle.owner) == 15,
                        "It hears the tower where it is"))
                .thenExecute(() -> {
                    var scan = com.reiko.signaltower.compat.MobileRadio.scan(level, turtle.position(), turtle.owner);
                    check(scan.stream().anyMatch(row -> Integer.valueOf(4624).equals(row.get("frequency"))), "Scanned: " + scan);
                    check(com.reiko.signaltower.compat.MobileRadio.reach(level, turtle.position()) > 0, "Its reach");
                    var pulse = com.reiko.signaltower.compat.MobileRadio.pulse(level, turtle.position(), 4625, turtle.owner, "Turtle 3", 10);
                    check(pulse.sent(), "Pulsed: " + pulse);
                    heardBy[0] = pulse.heardBy();
                })
                .thenWaitUntil(() -> check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "The receiver hears the pulse"))
                .thenWaitUntil(() -> check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "and it ends"))
                .thenExecute(() -> {
                    check(heardBy[0] == 1, "Heard by 1: " + heardBy[0]);
                    check(com.reiko.signaltower.compat.MobileRadio.send(level, turtle.position(), 4625, turtle.owner, "Turtle 3", "Hello from a turtle") == null,
                            "Message sent");
                    check(helper.getBlockState(tg).getValue(com.reiko.signaltower.block.RadioTelegraphBlock.POWERED), "The telegraph reads it");
                    var messages = com.reiko.signaltower.compat.MobileRadio.messages(level, turtle.position(), 4625, turtle.owner);
                    check(!messages.isEmpty() && "Hello from a turtle".equals(messages.get(messages.size() - 1).get("text"))
                            && "Turtle 3".equals(messages.get(messages.size() - 1).get("from")), "Listed: " + messages);
                    check(turtle.got.isEmpty(), "Not its own: " + turtle.got);
                    check(com.reiko.signaltower.compat.MobileRadio.send(level, turtle.position(), 4625, turtle.owner, "Turtle 3", "  ") != null, "Empty: refused");
                    check(!com.reiko.signaltower.compat.MobileRadio.pulse(level, turtle.position(), 4626, turtle.owner, "Turtle 3", 10).sent()
                            && com.reiko.signaltower.compat.MobileRadio.send(level, turtle.position(), 4626, turtle.owner, "Turtle 3", "hi") != null,
                            "Not on someone else's private frequency");
                })
                .thenIdle(com.reiko.signaltower.radio.Telegraph.COOLDOWN_TICKS)
                .thenExecute(() -> {
                    var sent = com.reiko.signaltower.radio.Telegraph.sendAs(level, helper.absolutePos(tg), null, "Computer 9", "Turtle, come home");
                    check(sent != null && sent.sent(), "The telegraph sends");
                    check(turtle.got.contains("4625 Turtle, come home|Computer 9"), "It reaches the turtle: " + turtle.got);
                    turtle.pos = turtle.pos.offset(5000, 0, 0);
                })
                .thenIdle(com.reiko.signaltower.radio.Telegraph.COOLDOWN_TICKS)
                .thenExecute(() -> {
                    com.reiko.signaltower.radio.Telegraph.sendAs(level, helper.absolutePos(tg), null, "Computer 9", "Are you there?");
                    check(turtle.got.size() == 1, "Not once it's far away: " + turtle.got);
                    com.reiko.signaltower.compat.MobileRadio.remove(turtle);
                    check(!com.reiko.signaltower.compat.MobileRadio.listed(turtle), "Gone");
                    claims.remove(4626);
                })
                .thenSucceed();
    }

    /**
     * With CC: Tweaked loaded: the Radio Turtle and Radio Pocket Computer upgrades are there from
     * their data files, take any Radio Pager and keep its frequency (giving it back on the pager),
     * are called "Radio", and make a "radio" peripheral whose frequency is the upgrade's (and can
     * be set). Without it, there's nothing to check.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void radioUpgrades(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded(com.reiko.signaltower.compat.cct.CctCompat.MOD_ID)) {
            helper.succeed();
            return;
        }
        List<String> problems = com.reiko.signaltower.compat.cct.CctCompat.checkRadioUpgrades(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        check(problems.isEmpty(), "Radio upgrades: " + problems);
        SignalTower.LOGGER.info("radioUpgrades: CC: Tweaked has the Radio Turtle and Radio Pocket Computer");
        helper.succeed();
    }

    /**
     * The Radio Jukebox: no disc, no signal; stone doesn't go in; a disc does and plays (15, the
     * network has its song, a receiver on its frequency hears it); one disc at a time, and a
     * hopper can't take it out while it plays. A speaker on its frequency plays the song in place
     * of its own sound (and tells clients), one on another frequency doesn't; Jade shows the song
     * on both. A redstone pulse plays it again from the start, and while the redstone stays it
     * repeats; when the redstone goes, the play going on is the last. At the end of the song it
     * goes quiet and the speaker stops; a hopper takes the disc out, another goes in and plays.
     * An empty hand takes it out; broken, it drops its disc.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void radioJukebox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos jb = new BlockPos(2, 1, 2);
        BlockPos speakerPos = new BlockPos(6, 1, 2);
        BlockPos otherPos = new BlockPos(6, 1, 5);
        BlockPos rx = new BlockPos(2, 1, 6);
        place(helper, jb, ModRegistry.RADIO_JUKEBOX.get(), 4629);
        place(helper, speakerPos, ModRegistry.RADIO_SPEAKER.get(), 4629);
        place(helper, otherPos, ModRegistry.RADIO_SPEAKER.get(), 4630);
        place(helper, rx, ModRegistry.RADIO_RECEIVER.get(), 4629);
        com.reiko.signaltower.blockentity.RadioJukeboxBlockEntity jukebox = helper.getBlockEntity(jb);
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity speaker = helper.getBlockEntity(speakerPos);
        com.reiko.signaltower.blockentity.RadioReceiverBlockEntity other = helper.getBlockEntity(otherPos);
        BlockPos at = helper.absolutePos(jb);
        SignalNetwork network = SignalNetwork.get(level);
        var cat = net.minecraft.world.item.JukeboxSongs.CAT;
        var thirteen = net.minecraft.world.item.JukeboxSongs.THIRTEEN;
        var powered = com.reiko.signaltower.block.RadioTransmitterBlock.POWERED;
        long[] started = new long[2];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    check(jukebox.getOutput() == 0 && !helper.getBlockState(jb).getValue(powered) && network.musicAt(at) == null, "Quiet with no disc");
                    check(!jukebox.insert(new ItemStack(Items.STONE)), "Stone isn't a disc");
                    check(jukebox.items().insertItem(0, new ItemStack(Items.STONE), true).getCount() == 1, "Nor from a hopper");
                    check(jukebox.insert(new ItemStack(Items.MUSIC_DISC_CAT)), "A disc goes in");
                    started[0] = level.getGameTime();
                    check(!jukebox.insert(new ItemStack(Items.MUSIC_DISC_13)), "One disc at a time");
                    check(jukebox.items().insertItem(0, new ItemStack(Items.MUSIC_DISC_13), false).getCount() == 1, "Even from a hopper");
                    check(jukebox.items().extractItem(0, 1, false).isEmpty() && jukebox.hasDisc(), "A hopper can't take it out while it plays");
                    check(jukebox.getOutput() == 15 && helper.getBlockState(jb).getValue(powered), "On the air");
                    SignalNetwork.Music music = network.musicAt(at);
                    check(music != null && music.song() == cat && music.frequency() == 4629 && !music.loop() && music.start() == started[0]
                            && music.length() == 185 * 20 + 20, "It broadcasts cat: " + music);
                    check(level.getBlockState(at).getAnalogOutputSignal(level, at) == 2, "A comparator reads cat's level");
                })
                .thenWaitUntil(() -> check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 15, "A receiver on it hears it"))
                .thenWaitUntil(() -> check(cat.equals(speaker.getMusicSong()) && speaker.getMusicStart() == started[0], "The speaker plays cat"))
                .thenExecute(() -> {
                    check(speaker.getNotesPlayed() == 0, "In place of its own sound: " + speaker.getNotesPlayed());
                    check(other.getMusicSong() == null, "Not on another frequency");
                    CompoundTag update = speaker.getUpdateTag(level.registryAccess());
                    check("minecraft:cat".equals(update.getString("music_song")) && update.getLong("music_start") == started[0],
                            "Clients are told: " + update);
                    CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, at);
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(level.getBlockState(at), info)).contains("jukebox_disc"), "Jade on the jukebox");
                    CompoundTag heard = com.reiko.signaltower.compat.RadioInfo.read(level, helper.absolutePos(speakerPos));
                    check(lineKeys(com.reiko.signaltower.compat.RadioInfo.lines(helper.getBlockState(speakerPos), heard)).contains("speaker_music"),
                            "Jade on the speaker: " + heard);
                    helper.setBlock(jb.east(), Blocks.REDSTONE_BLOCK); // a pulse, and it stays: play again, and repeat
                })
                .thenWaitUntil(() -> check(jukebox.isLooping(), "Redstone: it repeats"))
                .thenExecute(() -> {
                    started[1] = network.musicAt(at).start();
                    check(started[1] > started[0] && network.musicAt(at).loop(), "From the start again: " + started[1]);
                })
                .thenWaitUntil(() -> check(speaker.getMusicStart() == started[1], "The speaker starts it again"))
                .thenExecute(() -> {
                    jukebox.startedAtForTests(level.getGameTime() - jukebox.playLength() - 100); // a play and a bit ago
                    long now = level.getGameTime();
                    check(jukebox.isPlaying(now) && jukebox.playStart(now) > now - jukebox.playLength(), "Still playing: its second time round");
                    check(network.musicAt(at).startAt(now) == jukebox.playStart(now), "The network agrees");
                    helper.setBlock(jb.east(), Blocks.AIR);
                })
                .thenWaitUntil(() -> check(!jukebox.isLooping(), "No redstone: this play is the last"))
                .thenExecute(() -> {
                    long now = level.getGameTime();
                    check(jukebox.isPlaying(now) && jukebox.getOutput() == 15, "It finishes the song");
                    jukebox.startedAtForTests(now - jukebox.playLength() + 3); // nearly over
                })
                .thenWaitUntil(() -> check(jukebox.getOutput() == 0 && !helper.getBlockState(jb).getValue(powered), "Over: quiet"))
                .thenWaitUntil(() -> check(speaker.getMusicSong() == null && network.musicAt(at) == null, "The speaker stops"))
                .thenWaitUntil(() -> check(helper.getBlockState(rx).getValue(RadioReceiverBlock.POWER) == 0, "The receiver too"))
                .thenExecute(() -> {
                    ItemStack out = jukebox.items().extractItem(0, 1, false);
                    check(out.is(Items.MUSIC_DISC_CAT) && !jukebox.hasDisc(), "A hopper takes the disc out once it's over");
                    check(jukebox.items().insertItem(0, new ItemStack(Items.MUSIC_DISC_13), false).isEmpty(), "and another goes in");
                    check(network.musicAt(at) != null && network.musicAt(at).song() == thirteen, "It plays 13");
                })
                .thenWaitUntil(() -> check(thirteen.equals(speaker.getMusicSong()), "So does the speaker"))
                .thenExecute(() -> {
                    ItemStack out = jukebox.eject();
                    check(out.is(Items.MUSIC_DISC_13) && jukebox.getOutput() == 0 && network.musicAt(at) == null, "Taken out: quiet");
                    check(jukebox.insert(new ItemStack(Items.MUSIC_DISC_CAT)), "Back in");
                    level.destroyBlock(at, true);
                    check(network.musicAt(at) == null, "Broken: off the air");
                    check(helper.getEntities(EntityType.ITEM).stream().anyMatch(item -> item.getItem().is(Items.MUSIC_DISC_CAT)), "and its disc drops");
                })
                .thenWaitUntil(() -> check(speaker.getMusicSong() == null, "The speaker stops"))
                .thenSucceed();
    }

    /**
     * Music broadcasts in the network, with a jukebox far away (its chunk not loaded): a spot in its
     * reach hears its song, one outside doesn't, nor one on another frequency; out of power, it's
     * off. A repeating song tells the speakers each time it starts again; a song that's over is
     * taken off, and the jukebox goes quiet, without its chunk. A broadcast is saved with the network.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void musicBroadcasts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        network.forceWeather(com.reiko.signaltower.radio.Weather.CLEAR);
        BlockPos far = new BlockPos(8_300_000, 100, 8_300_000);
        BlockPos loopFar = far.offset(0, 0, 2000);
        long now = level.getGameTime();
        var cat = net.minecraft.world.item.JukeboxSongs.CAT;
        network.updateTransmitter(far, 4631, 15, 120, null);
        network.setMusic(far, new SignalNetwork.Music(4631, cat, now - 100, 400, false));
        SignalNetwork.MusicHeard heard = network.musicHeard(level, 4631, far.offset(80, 0, 0));
        check(heard != null && heard.from().equals(far) && heard.start() == now - 100 && heard.music().song() == cat, "Heard in its reach: " + heard);
        check(network.musicHeard(level, 4631, far.offset(400, 0, 0)) == null, "Not out of its reach");
        check(network.musicHeard(level, 4632, far.offset(80, 0, 0)) == null, "Not on another frequency");
        network.updateTransmitter(far, 4631, 0, 120, null);
        check(network.musicHeard(level, 4631, far.offset(80, 0, 0)) == null, "Not while it sends nothing");
        network.updateTransmitter(far, 4631, 15, 120, null);

        network.updateTransmitter(loopFar, 4632, 15, 120, null);
        network.setMusic(loopFar, new SignalNetwork.Music(4632, cat, now - 1000, 400, true));
        SignalNetwork.MusicHeard looped = network.musicHeard(level, 4632, loopFar);
        check(looped != null && looped.start() == now - 200, "Repeating: its latest start: " + looped);

        CompoundTag saved = network.save(new CompoundTag(), level.registryAccess());
        SignalNetwork loaded = SignalNetwork.load(saved, level.registryAccess()).attachTo(level);
        check(java.util.Objects.equals(loaded.musicAt(far), network.musicAt(far)) && java.util.Objects.equals(loaded.musicAt(loopFar), network.musicAt(loopFar)),
                "Saved with the network");

        long revision = network.revision(4632);
        network.tick(now + 200, 0); // the repeat starts again
        check(network.revision(4632) > revision, "Speakers are told when it starts again");
        network.tick(now + 300, 0); // cat (400 ticks here) started 100 ago is over
        check(network.musicAt(far) == null, "Over: taken off");
        check(network.transmitterAt(far) != null && network.transmitterAt(far).power() == 0, "and the jukebox is quiet, without its chunk");
        check(network.musicAt(loopFar) != null, "The repeating one goes on");
        helper.succeed();
    }


    // ---------------------------------------------------------------- v0.61.0: satellites, map waypoints

    /**
     * The satellite uplink, in the network (the tower's chunk not loaded): a Satellite Dish with no
     * satellite up changes nothing; with one up, the tower is heard at full strength 5,000 blocks
     * off and across the world, the meter says it's in reach, its music plays there, and it uses
     * four times the power. Satellites and uplinks are saved with the network; the oldest falls
     * out past 64. Each satellite's path is a great circle that rises and sets. Dish off, or no
     * satellites: its own reach again.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void satelliteUplink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = new SignalNetwork().attachTo(level);
        network.forceWeather(com.reiko.signaltower.radio.Weather.CLEAR);
        BlockPos tower = new BlockPos(8_400_000, 100, 8_400_000);
        BlockPos near = tower.offset(60, 0, 0);
        BlockPos farAway = tower.offset(5000, 0, 0);
        BlockPos acrossTheWorld = new BlockPos(-8_400_000, 64, -8_400_000);
        network.updateTransmitter(tower, 4633, 15, 120, null);
        check(network.listen(level, 4633, near).power() > 0 && network.listen(level, 4633, farAway).power() == 0,
                "Without a dish: heard near it, not 5000 blocks off");
        network.setUplink(tower, true);
        check(network.hasUplinkDish(tower) && !network.isUplinked(tower) && network.listen(level, 4633, farAway).power() == 0,
                "A dish with no satellite up: just as before");
        double rate = com.reiko.signaltower.radio.PowerCost.rateFor(network, tower);
        network.addSatellite(level.getGameTime(), 42L);
        check(network.isUplinked(tower) && network.satellites().size() == 1, "A satellite up: uplinked");
        check(network.listen(level, 4633, farAway).power() == 15 && network.listen(level, 4633, acrossTheWorld).power() == 15,
                "Heard at full strength 5000 blocks off and across the world");
        check(network.edgeMargin(level, 4633, acrossTheWorld) > 0, "The meter: in reach there");
        check(Math.abs(com.reiko.signaltower.radio.PowerCost.rateFor(network, tower) - rate * 4) < 1e-9, "Four times the power: "
                + com.reiko.signaltower.radio.PowerCost.rateFor(network, tower) + " for " + rate);
        network.setMusic(tower, new SignalNetwork.Music(4633, net.minecraft.world.item.JukeboxSongs.CAT, level.getGameTime(), 400, false));
        check(network.musicHeard(level, 4633, acrossTheWorld) != null, "Its music plays across the world too");
        UUID tuner = UUID.randomUUID();
        network.updateTransmitter(tower, 4633, 15, 120, tuner);
        check(network.towersTunedBy(tuner).equals(List.of(new SignalNetwork.OwnTower(tower, 0, 4633, -1))), "On its tuner's map: reach everywhere");

        CompoundTag saved = network.save(new CompoundTag(), level.registryAccess());
        SignalNetwork loaded = SignalNetwork.load(saved, level.registryAccess()).attachTo(level);
        check(loaded.satellites().equals(network.satellites()) && loaded.isUplinked(tower), "Saved with the network");

        com.reiko.signaltower.radio.SatelliteOrbit orbit = com.reiko.signaltower.radio.SatelliteOrbit.of(network.satellites().get(0));
        boolean up = false, down = false;
        for (int t = 0; t < orbit.period(); t += 50) {
            org.joml.Vector3f d = orbit.direction(level.getGameTime() + t);
            check(Math.abs(d.length() - 1) < 1e-3, "A direction: " + d);
            up |= d.y > 0.2F;
            down |= d.y < -0.2F;
        }
        check(up && down && orbit.period() >= 3600 && orbit.period() <= 8400, "It crosses the sky, and sets, every 3 to 7 minutes: " + orbit.period());
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var payload = new com.reiko.signaltower.network.SatellitesPayload(level.dimension().location(), network.satellites());
        com.reiko.signaltower.network.SatellitesPayload.STREAM_CODEC.encode(buffer, payload);
        check(com.reiko.signaltower.network.SatellitesPayload.STREAM_CODEC.decode(buffer).equals(payload), "Sent to the client as it is");

        network.setUplink(tower, false);
        check(!network.isUplinked(tower) && network.listen(level, 4633, farAway).power() == 0, "Dish off: just its own reach");
        network.setUplink(tower, true);
        network.clearSatellitesForTests();
        check(!network.isUplinked(tower) && network.listen(level, 4633, farAway).power() == 0, "No satellite: just its own reach");
        for (int i = 0; i < SignalNetwork.MAX_SATELLITES + 6; i++) {
            network.addSatellite(level.getGameTime() + i, i);
        }
        check(network.satellites().size() == SignalNetwork.MAX_SATELLITES && network.satellites().get(0).seed() == 6, "64 at most: the oldest fall out");
        network.removeTransmitter(tower);
        check(!network.hasUplinkDish(tower), "Gone with its tower");
        helper.succeed();
    }

    /**
     * The Satellite Dish on real blocks: a repeater can't take one; a transmitter under open sky
     * can (Jade says whether it's uplinked), roofed over it can't reach a satellite (under glass it
     * can), and taken off it's given back.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void satelliteDish(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tx = new BlockPos(1, 1, 1);
        BlockPos relay = new BlockPos(10, 1, 1);
        place(helper, tx, ModRegistry.RADIO_TRANSMITTER.get(), 4634);
        place(helper, relay, ModRegistry.RADIO_REPEATER.get(), 4635);
        BlockPos abs = helper.absolutePos(tx);
        Player dev = namedPlayer(helper, new BlockPos(2, 1, 0), UUID.randomUUID(), "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        ItemStack dish = new ItemStack(ModRegistry.SATELLITE_DISH.get(), 2);
        SignalNetwork network = SignalNetwork.get(level);
        Component refused = com.reiko.signaltower.item.AntennaItem.fit(dev, helper.absolutePos(relay), com.reiko.signaltower.radio.AntennaTier.SATELLITE, dish);
        check(refused != null && keyOf(refused).endsWith("satellite_transmitters_only") && dish.getCount() == 2
                && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(relay)) == com.reiko.signaltower.radio.AntennaTier.NONE,
                "A repeater can't take one: " + refused);
        Component fitted = com.reiko.signaltower.item.AntennaItem.fit(dev, abs, com.reiko.signaltower.radio.AntennaTier.SATELLITE, dish);
        check(fitted != null && (keyOf(fitted).endsWith("satellite_no_satellite") || keyOf(fitted).endsWith("satellite_fitted"))
                && dish.getCount() == 1 && com.reiko.signaltower.radio.AntennaTier.of(helper.getBlockState(tx)) == com.reiko.signaltower.radio.AntennaTier.SATELLITE
                && network.hasUplinkDish(abs), "Fitted under open sky: " + fitted);
        CompoundTag info = com.reiko.signaltower.compat.RadioInfo.read(level, abs);
        check(info != null && List.of("on", "no_satellite").contains(info.getString("uplink")), "Jade: " + info);
        check(com.reiko.signaltower.radio.AntennaTier.SATELLITE.label().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key
                && key.getKey().equals("item.reikos_signal_tower.satellite_dish"), "Its name");
        BlockPos roof = tx.above(4);
        helper.setBlock(roof, Blocks.STONE);
        helper.startSequence()
                .thenIdle(3) // the light catches up
                .thenExecute(() -> {
                    ((com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) helper.getBlockEntity(tx)).refresh();
                    check(!network.hasUplinkDish(abs) && "no_sky".equals(com.reiko.signaltower.compat.RadioInfo.read(level, abs).getString("uplink")),
                            "Roofed over: it can't reach a satellite");
                    Component again = com.reiko.signaltower.item.AntennaItem.satelliteMessage(level, abs, Component.literal("It"));
                    check(keyOf(again).endsWith("satellite_no_sky"), "and says so: " + again);
                    helper.setBlock(roof, Blocks.GLASS);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    ((com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity) helper.getBlockEntity(tx)).refresh();
                    check(network.hasUplinkDish(abs), "Under glass: it can again");
                    Component off = com.reiko.signaltower.item.AntennaItem.takeOff(dev, abs);
                    check(off != null && !network.hasUplinkDish(abs) && dev.getInventory().countItem(ModRegistry.SATELLITE_DISH.get()) == 1,
                            "Taken off: given back, and no uplink");
                })
                .thenSucceed();
    }

    /**
     * The Launch Pad: a pulse with nothing on it, or too few rockets, or a block over it, says why
     * and does nothing; hoppers load it (one satellite, eight rockets; a comparator reads 15 when
     * it's ready). A pulse then sends it up: the rockets are used, nothing comes out while it
     * climbs, and 6 seconds later a satellite is in orbit over the dimension (the advancement's
     * event fires). The Nether and the End have no orbit.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void launchPad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SignalNetwork network = SignalNetwork.get(level);
        var fired = com.reiko.signaltower.advancement.TowerAdvancements.recordForTests();
        BlockPos pad = new BlockPos(4, 1, 1);
        BlockPos power = new BlockPos(5, 1, 1);
        helper.setBlock(pad, ModRegistry.LAUNCH_PAD.get());
        com.reiko.signaltower.blockentity.LaunchPadBlockEntity be = helper.getBlockEntity(pad);
        var slots = be.items();
        int[] before = new int[1];
        check(com.reiko.signaltower.blockentity.LaunchPadBlockEntity.hasOrbit(level)
                && !com.reiko.signaltower.blockentity.LaunchPadBlockEntity.hasOrbit(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER))
                && !com.reiko.signaltower.blockentity.LaunchPadBlockEntity.hasOrbit(level.getServer().getLevel(net.minecraft.world.level.Level.END)),
                "An orbit over the Overworld, none over the Nether or the End");
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
                .thenExecute(() -> {
                    check("no_satellite".equals(be.refusal()) && !be.isLaunching(), "Nothing on it: no launch: " + be.refusal());
                    helper.setBlock(power, Blocks.AIR);
                    check(slots.insertItem(0, new ItemStack(ModRegistry.SATELLITE.get()), false).isEmpty()
                            && !slots.insertItem(0, new ItemStack(ModRegistry.SATELLITE.get()), true).isEmpty()
                            && slots.insertItem(1, new ItemStack(ModRegistry.SATELLITE.get()), true).getCount() == 1,
                            "A hopper puts in one satellite, in its slot");
                    check(slots.insertItem(1, new ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET, 5), false).isEmpty()
                            && be.fireworkCount() == 5 && be.comparatorOutput() == 12, "and rockets: 5, comparator " + be.comparatorOutput());
                })
                .thenIdle(2)
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
                .thenExecute(() -> {
                    check("fireworks".equals(be.refusal()) && !be.isLaunching(), "Too few rockets: no launch: " + be.refusal());
                    helper.setBlock(power, Blocks.AIR);
                    ItemStack rest = slots.insertItem(1, new ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET, 7), false);
                    check(rest.getCount() == 4 && be.fireworkCount() == 8 && be.comparatorOutput() == 15
                            && level.getBlockState(helper.absolutePos(pad)).getAnalogOutputSignal(level, helper.absolutePos(pad)) == 15,
                            "Eight rockets: ready, comparator 15; the rest stay in the hopper: " + rest.getCount());
                    helper.setBlock(pad.above(3), Blocks.STONE);
                })
                .thenIdle(3) // the light catches up
                .thenExecute(() -> {
                    check("no_sky".equals(be.launchProblem(level)), "A block over it: it can't go");
                    helper.setBlock(pad.above(3), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    check(be.launchProblem(level) == null, "Clear again: ready");
                    before[0] = network.satellites().size();
                })
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
                .thenExecute(() -> {
                    check(be.isLaunching() && be.fireworkCount() == 0 && !be.hasSatellite() && be.refusal() == null, "Launched: the rockets used up");
                    check(slots.extractItem(1, 1, false).isEmpty() && slots.insertItem(1, new ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET), true).getCount() == 1,
                            "Nothing in or out while it climbs");
                    check(be.height(level.getGameTime(), 0) < 5, "It starts slowly");
                })
                .thenIdle(com.reiko.signaltower.blockentity.LaunchPadBlockEntity.FLIGHT_TICKS / 2)
                .thenExecute(() -> check(be.isLaunching() && be.height(level.getGameTime(), 0) > 30, "Climbing: " + be.height(level.getGameTime(), 0)))
                .thenIdle(com.reiko.signaltower.blockentity.LaunchPadBlockEntity.FLIGHT_TICKS / 2 + 5)
                .thenExecute(() -> {
                    check(!be.isLaunching() && network.satellites().size() == before[0] + 1, "In orbit: " + network.satellites().size());
                    check(fired.stream().anyMatch(f -> f.event().equals("satellite_in_orbit") && f.pos().equals(helper.absolutePos(pad))),
                            "Its advancement's event");
                    network.clearSatellitesForTests(); // so other tests' dishes aren't uplinked
                })
                .thenSucceed();
    }

    /**
     * Map waypoints: a player's own full-size towers (not compact ones, nor anyone else's), with
     * their reach; an old tower or village station once they've been near it (kept, and updated
     * rather than doubled), with the frequency the network has for it now. The list goes to the
     * client as it is.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void mapWaypoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID dev = UUID.randomUUID();
        SignalNetwork network = SignalNetwork.get(level);
        BlockPos own = new BlockPos(8_500_000, 90, 8_500_000);
        BlockPos compact = own.offset(10, 0, 0);
        BlockPos theirs = own.offset(20, 0, 0);
        BlockPos old = own.offset(30, 0, 0);
        network.updateTransmitter(own, 4636, 15, 300, dev);
        network.updateTransmitter(compact, 4636, 15, 16, dev, true, null);
        network.updateTransmitter(theirs, 4636, 15, 300, UUID.randomUUID());
        network.updateTransmitter(old, 4637, 0, 200, null);
        try {
            Player player = namedPlayer(helper, new BlockPos(2, 1, 0), dev, "Dev", false, new java.util.ArrayList<>(), new java.util.ArrayList<>());
            var place = new com.reiko.signaltower.radio.MapTowers.Place(level.dimension(), old, com.reiko.signaltower.network.MapWaypointsPayload.OLD_TOWER, 4000);
            check(com.reiko.signaltower.radio.MapTowers.discover(player, place) && !com.reiko.signaltower.radio.MapTowers.discover(player, place),
                    "Found once, not twice");
            check(com.reiko.signaltower.radio.MapTowers.discover(player,
                    new com.reiko.signaltower.radio.MapTowers.Place(level.dimension(), old, com.reiko.signaltower.network.MapWaypointsPayload.OLD_TOWER, 4001))
                    && player.getData(com.reiko.signaltower.radio.MapTowers.DISCOVERED).places().size() == 1, "Updated, not doubled");
            var entries = com.reiko.signaltower.radio.MapTowers.entriesFor(level.getServer(), dev,
                    player.getData(com.reiko.signaltower.radio.MapTowers.DISCOVERED).places());
            var ownEntry = new com.reiko.signaltower.network.MapWaypointsPayload.Entry(level.dimension().location(), own,
                    com.reiko.signaltower.network.MapWaypointsPayload.TRANSMITTER, 4636, 300);
            var oldEntry = new com.reiko.signaltower.network.MapWaypointsPayload.Entry(level.dimension().location(), old,
                    com.reiko.signaltower.network.MapWaypointsPayload.OLD_TOWER, 4637, 0);
            check(entries.contains(ownEntry) && entries.contains(oldEntry) && entries.stream().noneMatch(e -> e.pos().equals(compact) || e.pos().equals(theirs)),
                    "Their own tower with its reach, and the old tower on its frequency now; not compact ones or anyone else's: " + entries);
            check(ownEntry.own() && !oldEntry.own(), "Own towers get a reach circle");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            var payload = new com.reiko.signaltower.network.MapWaypointsPayload(entries);
            com.reiko.signaltower.network.MapWaypointsPayload.STREAM_CODEC.encode(buffer, payload);
            check(com.reiko.signaltower.network.MapWaypointsPayload.STREAM_CODEC.decode(buffer).equals(payload), "Sent to the client as it is");
            var codec = com.reiko.signaltower.radio.MapTowers.Discovered.CODEC;
            var known = player.getData(com.reiko.signaltower.radio.MapTowers.DISCOVERED);
            var tag = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, known).getOrThrow();
            check(codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().equals(known), "Kept with the player");
        } finally {
            for (BlockPos pos : List.of(own, compact, theirs, old)) network.removeTransmitter(pos);
        }
        helper.succeed();
    }
}
