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
}
