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
}
