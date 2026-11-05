package com.reiko.signaltower.gametest;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CompactRadioReceiverBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.block.WallRedstoneBlock;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.item.RemoteLight;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
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
}
