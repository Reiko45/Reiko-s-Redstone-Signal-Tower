package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.CrossDimension;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Launch Pad (v0.61.0): holds a Satellite and up to eight Firework Rockets. A redstone pulse
 * (off to on) launches it, if it's loaded, has open sky above, and is in a dimension with a sky
 * (not the Nether or the End): the rockets are used up and the satellite rises for
 * {@link #FLIGHT_TICKS}, faster and faster, then reaches orbit over the dimension
 * ({@link SignalNetwork#addSatellite}). What it holds, and when it went up, are saved and sent to
 * the client, which draws the rocket and its exhaust.
 */
public class LaunchPadBlockEntity extends BlockEntity {
    public static final int FIREWORKS_NEEDED = 8;
    /** How long the climb to orbit takes. */
    public static final int FLIGHT_TICKS = 120;
    /** How high (in blocks above the pad) the rocket is when it reaches orbit. */
    public static final double FLIGHT_HEIGHT = 240.0;

    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private ItemStack satellite = ItemStack.EMPTY;
    private ItemStack fireworks = ItemStack.EMPTY;
    /** The game time the launch started, or -1 if it's not flying. */
    private long launchedAt = -1;
    /** The seed for its path across the sky (picked at launch). */
    private long seed;
    private boolean powered;
    /** Why the last pulse didn't launch it ("no_satellite", "fireworks", "no_sky", "no_space"), or null. Not saved. */
    private @Nullable String refusal;
    private final Slots slots = new Slots();

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LAUNCH_PAD_BE.get(), pos, state);
    }

    public static boolean isSatellite(ItemStack stack) {
        return stack.is(ModRegistry.SATELLITE.get());
    }

    public static boolean isFirework(ItemStack stack) {
        return stack.is(Items.FIREWORK_ROCKET);
    }

    public boolean hasSatellite() {
        return !satellite.isEmpty();
    }

    public ItemStack satellite() {
        return satellite;
    }

    public int fireworkCount() {
        return fireworks.getCount();
    }

    public boolean isLaunching() {
        return launchedAt >= 0;
    }

    public @Nullable String refusal() {
        return refusal;
    }

    /** How far up the rocket is, in blocks above the pad, {@code partialTick} after game time {@code time} (0 when it's not flying). */
    public double height(long time, float partialTick) {
        if (launchedAt < 0) {
            return 0;
        }
        double t = Math.max(0, Math.min(FLIGHT_TICKS, time - launchedAt + partialTick)) / FLIGHT_TICKS;
        return FLIGHT_HEIGHT * t * t;
    }

    /** Puts one satellite on the pad from {@code stack} (not used up here). False if it has one, or it's flying. */
    public boolean load(ItemStack stack) {
        if (!isSatellite(stack) || hasSatellite() || isLaunching()) {
            return false;
        }
        satellite = stack.copyWithCount(1);
        changed();
        return true;
    }

    /** Takes as many rockets from {@code stack} as it has room for (not used up here); how many. */
    public int loadFireworks(ItemStack stack) {
        if (!isFirework(stack) || isLaunching() || !fireworks.isEmpty() && !ItemStack.isSameItemSameComponents(fireworks, stack)) {
            return 0;
        }
        int taken = Math.min(stack.getCount(), FIREWORKS_NEEDED - fireworkCount());
        if (taken <= 0) {
            return 0;
        }
        if (fireworks.isEmpty()) {
            fireworks = stack.copyWithCount(taken);
        } else {
            fireworks.grow(taken);
        }
        changed();
        return taken;
    }

    /** Takes everything off the pad (nothing while it's flying). */
    public List<ItemStack> unload() {
        List<ItemStack> out = new ArrayList<>();
        if (isLaunching()) {
            return out;
        }
        if (hasSatellite()) out.add(satellite);
        if (!fireworks.isEmpty()) out.add(fireworks);
        satellite = ItemStack.EMPTY;
        fireworks = ItemStack.EMPTY;
        if (!out.isEmpty()) changed();
        return out;
    }

    /** What stops it launching now, or null if it can go. */
    public @Nullable String launchProblem(Level level) {
        if (isLaunching()) return "launching";
        if (!hasSatellite()) return "no_satellite";
        if (fireworkCount() < FIREWORKS_NEEDED) return "fireworks";
        if (!hasOrbit(level)) return "no_space";
        if (!level.canSeeSky(worldPosition.above())) return "no_sky";
        return null;
    }

    /** True if satellites can orbit over {@code level}: it has a sky (not the Nether, with its roof, or the End). */
    public static boolean hasOrbit(Level level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    /** The redstone it gets changed: off to on launches it (or says why not). */
    public void redstone(ServerLevel level, boolean now) {
        boolean rising = now && !powered;
        if (powered != now) {
            powered = now;
            setChanged();
        }
        if (rising) {
            tryLaunch(level);
        }
    }

    /** Launches it if it can; otherwise says why to players nearby (and Jade). True if it went. */
    public boolean tryLaunch(ServerLevel level) {
        String problem = launchProblem(level);
        if (problem != null) {
            if (!"launching".equals(problem)) {
                refusal = problem;
                level.playSound(null, worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0F, 1.2F);
                Component message = refusalMessage(level, problem);
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(worldPosition.getCenter()) < 24 * 24) {
                        player.displayClientMessage(message, true);
                    }
                }
                changed();
            }
            return false;
        }
        refusal = null;
        satellite = ItemStack.EMPTY;
        fireworks = ItemStack.EMPTY;
        launchedAt = level.getGameTime();
        seed = level.getRandom().nextLong();
        level.playSound(null, worldPosition, ModSounds.SATELLITE_LAUNCH.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        changed();
        return true;
    }

    public Component refusalMessage(Level level, String problem) {
        net.minecraft.network.chat.MutableComponent message = switch (problem) {
            case "fireworks" -> Component.translatable(KEY + "launch_fireworks", fireworkCount(), FIREWORKS_NEEDED);
            case "no_space" -> Component.translatable(KEY + "launch_no_space", CrossDimension.name(level.dimension()));
            default -> Component.translatable(KEY + "launch_" + problem);
        };
        return message.withStyle(ChatFormatting.RED);
    }

    /** The climb is over (or the pad went while it climbed): the satellite is in orbit. */
    public void reachOrbit(ServerLevel level) {
        if (launchedAt < 0) {
            return;
        }
        launchedAt = -1;
        SignalNetwork network = SignalNetwork.get(level);
        network.addSatellite(level.getGameTime(), seed);
        int count = network.satellites().size();
        Component message = Component.translatable(KEY + "satellite_in_orbit", CrossDimension.name(level.dimension()), count)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) < 256 * 256) {
                player.displayClientMessage(message, false);
            }
        }
        com.reiko.signaltower.advancement.TowerAdvancements.fire(level, worldPosition, "satellite_in_orbit", count, null, 64);
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaunchPadBlockEntity pad) {
        if (pad.launchedAt >= 0 && level instanceof ServerLevel serverLevel && level.getGameTime() - pad.launchedAt >= FLIGHT_TICKS) {
            pad.reachOrbit(serverLevel);
        }
    }

    /** The exhaust: flames and smoke under the rocket, and a cloud round the pad as it lifts off. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, LaunchPadBlockEntity pad) {
        if (pad.launchedAt < 0) {
            return;
        }
        long since = level.getGameTime() - pad.launchedAt;
        if (since < 0 || since > FLIGHT_TICKS) {
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double y = pos.getY() + 6.0 / 16.0 + pad.height(level.getGameTime(), 0);
        var random = level.getRandom();
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.FLAME, x + random.nextGaussian() * 0.06, y - 0.1, z + random.nextGaussian() * 0.06,
                    random.nextGaussian() * 0.02, -0.25 - random.nextDouble() * 0.2, random.nextGaussian() * 0.02);
        }
        level.addParticle(ParticleTypes.FIREWORK, x, y - 0.3, z, random.nextGaussian() * 0.05, -0.1, random.nextGaussian() * 0.05);
        if (since % 2 == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, y - 0.6, z,
                    random.nextGaussian() * 0.01, 0.01, random.nextGaussian() * 0.01);
        }
        if (since < 40) {
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.5, z, Math.cos(angle) * 0.35, 0.02, Math.sin(angle) * 0.35);
            }
        }
    }

    /** 0 empty; the rockets on it (up to 8), plus 7 with a satellite: 15 means ready. */
    public int comparatorOutput() {
        return isLaunching() ? 0 : Math.min(FIREWORKS_NEEDED, fireworkCount()) + (hasSatellite() ? 7 : 0);
    }

    public IItemHandler items() {
        return slots;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeShared(tag, registries);
        tag.putBoolean("powered", powered);
        tag.putLong("seed", seed);
    }

    private void writeShared(CompoundTag tag, HolderLookup.Provider registries) {
        if (hasSatellite()) tag.put("satellite", satellite.save(registries));
        if (!fireworks.isEmpty()) tag.put("fireworks", fireworks.save(registries));
        if (launchedAt >= 0) tag.putLong("launched_at", launchedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        satellite = tag.contains("satellite") ? ItemStack.parseOptional(registries, tag.getCompound("satellite")) : ItemStack.EMPTY;
        fireworks = tag.contains("fireworks") ? ItemStack.parseOptional(registries, tag.getCompound("fireworks")) : ItemStack.EMPTY;
        launchedAt = tag.contains("launched_at") ? tag.getLong("launched_at") : -1;
        if (tag.contains("powered")) powered = tag.getBoolean("powered");
        if (tag.contains("seed")) seed = tag.getLong("seed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeShared(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Slot 0: the satellite (1); slot 1: firework rockets (8). Nothing comes out while it's flying. */
    private final class Slots implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? satellite : slot == 1 ? fireworks : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (isLaunching() || stack.isEmpty()) {
                return stack;
            }
            if (slot == 0) {
                if (!isSatellite(stack) || hasSatellite()) return stack;
                if (!simulate) load(stack);
                return stack.copyWithCount(stack.getCount() - 1);
            }
            if (slot == 1) {
                if (!isFirework(stack) || !fireworks.isEmpty() && !ItemStack.isSameItemSameComponents(fireworks, stack)) return stack;
                int room = Math.min(stack.getCount(), FIREWORKS_NEEDED - fireworkCount());
                if (room <= 0) return stack;
                if (!simulate) loadFireworks(stack.copyWithCount(room));
                return stack.copyWithCount(stack.getCount() - room);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (isLaunching() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack in = getStackInSlot(slot);
            if (in.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, in.getCount());
            ItemStack out = in.copyWithCount(taken);
            if (!simulate) {
                if (slot == 0) {
                    satellite = ItemStack.EMPTY;
                } else {
                    fireworks.shrink(taken);
                    if (fireworks.isEmpty()) fireworks = ItemStack.EMPTY;
                }
                changed();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : FIREWORKS_NEEDED;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 ? isSatellite(stack) : slot == 1 && isFirework(stack);
        }
    }
}
