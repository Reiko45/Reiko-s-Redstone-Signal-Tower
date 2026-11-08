package com.reiko.signaltower.radio;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.AviationBeaconBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.item.AntennaItem;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The mast builder (v0.51.0): right-click any Radio Mast of a tower, or its top, with a Radio Mast
 * in hand and a new mast goes in under the top, lifting the top one block (with its frequency,
 * antenna and everything else it keeps) and whatever rides on it: an Aviation Beacon standing on
 * it, or a Lightning Rod. A column of masts with nothing on top grows from the top instead. So a
 * tower can be raised from the ground. Sneak to place a mast the normal way.
 * <p>
 * It needs room above (air, or something that can be built over, like grass), stays inside the
 * world, and on someone else's private frequency only its owner and members may raise the tower
 * (like fitting an antenna).
 */
public final class MastBuilder {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    private static final int SCAN_LIMIT = 4096;

    private MastBuilder() {
    }

    /** True if right-clicking a block in {@code state} with a mast builds: a mast, or a full-size tower top. */
    public static boolean builds(BlockState state) {
        return state.getBlock() instanceof RadioMastBlock || AntennaTier.fits(state);
    }

    /** True if a block in {@code state} goes up with the tower top under it: a beacon standing on it, or a lightning rod. */
    public static boolean rides(BlockState state) {
        return state.getBlock() instanceof AviationBeaconBlock && state.getValue(AviationBeaconBlock.FACE) == AttachFace.FLOOR
                || state.getBlock() instanceof LightningRodBlock;
    }

    /**
     * Adds a mast under the top of the tower {@code player} clicked at {@code clicked} (or on top of
     * a bare mast column), using up one of {@code stack} (not in creative). Tells them and returns
     * the message; null if there's nothing to build on there or they may not build. Public for the
     * GameTests.
     */
    public static @Nullable Component build(Player player, BlockPos clicked, ItemStack stack) {
        Level level = player.level();
        if (!builds(level.getBlockState(clicked)) || !player.mayBuild() || !level.mayInteract(player, clicked)) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = clicked.mutable();
        for (int i = 0; i < SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof RadioMastBlock; i++) {
            cursor.move(Direction.UP);
        }
        BlockPos base = cursor.immutable(); // where the new mast goes
        BlockState topState = level.getBlockState(base);
        boolean tower = AntennaTier.fits(topState);
        List<BlockPos> lifted = new ArrayList<>();
        if (tower) {
            lifted.add(base);
            cursor.move(Direction.UP);
        }
        if (rides(level.getBlockState(cursor))) {
            lifted.add(cursor.immutable());
            cursor.move(Direction.UP);
        }
        BlockPos free = cursor.immutable();
        if (level.isOutsideBuildHeight(free)) {
            return tell(player, Component.translatable(KEY + "mast_too_high").withStyle(ChatFormatting.RED));
        }
        BlockState inTheWay = level.getBlockState(free);
        if (!inTheWay.canBeReplaced()) {
            return tell(player, Component.translatable(KEY + "mast_no_room", inTheWay.getBlock().getName(),
                    free.getX() + ", " + free.getY() + ", " + free.getZ()).withStyle(ChatFormatting.RED));
        }
        if (tower) {
            Component refused = AntennaItem.towerRefusal(player, base, topState);
            if (refused != null) {
                return tell(player, refused);
            }
        }
        int before = tower ? AntennaItem.reachShown(level, base, topState) : 0;
        for (int i = lifted.size() - 1; i >= 0; i--) {
            move(level, lifted.get(i), lifted.get(i).above());
        }
        BlockState mast = ModRegistry.RADIO_MAST.get().defaultBlockState();
        level.setBlock(base, mast, Block.UPDATE_ALL);
        SoundType sound = mast.getSoundType(level, base, player);
        level.playSound(null, base, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (!tower) {
            return tell(player, Component.translatable(KEY + "mast_grown", RadioHelper.mastsBelow(level, base.above()))
                    .withStyle(ChatFormatting.AQUA));
        }
        BlockPos top = base.above();
        refresh(level, top);
        BlockState raised = level.getBlockState(top);
        return tell(player, Component.translatable(KEY + "mast_added", raised.getBlock().getName(), RadioHelper.mastsBelow(level, top),
                before, AntennaItem.reachShown(level, top, raised)).withStyle(ChatFormatting.AQUA));
    }

    /** Moves the block at {@code from} (and what its block entity keeps) to {@code to}, which is free. */
    private static void move(Level level, BlockPos from, BlockPos to) {
        BlockState state = level.getBlockState(from);
        BlockEntity entity = level.getBlockEntity(from);
        CompoundTag data = entity == null ? null : entity.saveWithoutMetadata(level.registryAccess());
        level.setBlock(to, state, Block.UPDATE_ALL);
        BlockEntity moved = level.getBlockEntity(to);
        if (data != null && moved != null) {
            moved.loadWithComponents(data, level.registryAccess());
            moved.setChanged();
        }
    }

    /** Tells the network where the tower top is now, and its new reach. */
    private static void refresh(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.refresh();
        } else if (level.getBlockEntity(pos) instanceof RadioRepeaterBlockEntity relay) {
            relay.refresh();
        }
    }

    private static Component tell(Player player, Component message) {
        player.displayClientMessage(message, true);
        return message;
    }
}
