package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.block.RadioBatteryBlock;
import com.reiko.signaltower.radio.PowerCost;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Radio Battery's charge (v0.49.0). Towers next to it (or next to their mast) take from it
 * while they send ({@link PowerCost}). It's charged with Redstone Dust and Blocks of Redstone (by
 * hand, or from a hopper or pipe: they're used up as they go in) or with FE from other mods. Its
 * block state shows four bars; a comparator reads it. The item keeps its charge.
 */
public class RadioBatteryBlockEntity extends BlockEntity {
    private static final String CHARGE_TAG = "charge";
    private static final String FE_TAG = "fe";

    private int charge;
    /** FE that came in but doesn't make a whole charge yet. */
    private int feBuffer;
    /** The second (game time) towers last took from it, and how much a second they took (for Jade; not saved). */
    private long useTime = Long.MIN_VALUE;
    private double use;

    private final IItemHandler items = new RedstoneInput();
    private final IEnergyStorage energy = new EnergyInput();

    public RadioBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.RADIO_BATTERY_BE.get(), pos, state);
    }

    public int getCharge() {
        return charge;
    }

    public int getCapacity() {
        return PowerCost.capacity();
    }

    /** What one of {@code stack} puts in: Redstone Dust {@link PowerCost#chargePerRedstone()}, a Block of Redstone nine times that, else 0. */
    public static int valueOf(ItemStack stack) {
        if (stack.is(Items.REDSTONE)) return PowerCost.chargePerRedstone();
        if (stack.is(Items.REDSTONE_BLOCK)) return (int) Math.min(Integer.MAX_VALUE, 9L * PowerCost.chargePerRedstone());
        return 0;
    }

    /** How many of {@code stack} fit (all of them, or as many as there's room for); unless {@code simulate}, puts them in. */
    public int insert(ItemStack stack, boolean simulate) {
        int value = valueOf(stack);
        if (value <= 0) {
            return 0;
        }
        long room = Math.max(0L, (long) getCapacity() - charge);
        int count = (int) Math.min(stack.getCount(), room / value);
        if (count > 0 && !simulate) {
            add((int) Math.min(Integer.MAX_VALUE, (long) count * value));
        }
        return count;
    }

    /** Adds up to {@code amount} charge (as much as there's room for); returns how much went in. */
    public int add(int amount) {
        int room = Math.max(0, getCapacity() - charge);
        int added = Math.max(0, Math.min(amount, room));
        if (added > 0) {
            int before = charge;
            charge += added;
            changed(before);
        }
        return added;
    }

    /** Takes up to {@code amount} charge; returns how much it had to give. */
    public int take(int amount) {
        int taken = Math.max(0, Math.min(amount, charge));
        if (taken > 0) {
            int before = charge;
            charge -= taken;
            changed(before);
        }
        return taken;
    }

    /** For the GameTests and commands: sets the charge outright. */
    public void setCharge(int value) {
        int before = charge;
        charge = Math.max(0, value);
        changed(before);
    }

    /** Towers take {@code rate} a second from it, in the second at {@code gameTime}. */
    public void noteUse(long gameTime, double rate) {
        if (useTime != gameTime) {
            useTime = gameTime;
            use = 0;
        }
        use += rate;
    }

    /** The second (game time) Solar Panels last charged it, and how much a second (v0.50.0, for Jade; not saved). */
    private long gainTime = Long.MIN_VALUE;
    private double gain;

    /** A Solar Panel puts {@code rate} a second in, in the second at {@code gameTime}. */
    public void noteGain(long gameTime, double rate) {
        if (gainTime != gameTime) {
            gainTime = gameTime;
            gain = 0;
        }
        gain += rate;
    }

    /** How much a second Solar Panels put in lately (0 if none did in the last two seconds). */
    public double gainPerSecond(long gameTime) {
        return gameTime - gainTime <= 40 ? gain : 0;
    }

    /** How much a second towers took from it lately (0 if none did in the last two seconds). */
    public double usePerSecond(long gameTime) {
        return gameTime - useTime <= 40 ? use : 0;
    }

    /** 0 empty, 1-4 bars. */
    public static int bars(int charge, int capacity) {
        if (charge <= 0) return 0;
        return (int) Math.max(1, Math.min(4, Math.ceil(charge * 4.0 / Math.max(1, capacity))));
    }

    /** What a comparator reads: 0 empty, else 1-15 by how full it is. */
    public int comparatorLevel() {
        if (charge <= 0) return 0;
        return 1 + (int) Math.min(14, (long) charge * 14 / Math.max(1, getCapacity()));
    }

    private void changed(int before) {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        syncState();
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        if (before <= 0 && charge > 0) {
            // Something to send with again: towers next to it (or above its mast) look at once.
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** Makes the block's bars match the charge. */
    public void syncState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RadioBatteryBlock) {
            int bars = bars(charge, getCapacity());
            if (state.getValue(RadioBatteryBlock.CHARGE) != bars) {
                level.setBlock(worldPosition, state.setValue(RadioBatteryBlock.CHARGE, bars), Block.UPDATE_CLIENTS);
            }
        }
    }

    public IItemHandler items() {
        return items;
    }

    public IEnergyStorage energy() {
        return energy;
    }

    // ---- Saving, and the item keeping its charge ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(CHARGE_TAG, charge);
        if (feBuffer > 0) {
            tag.putInt(FE_TAG, feBuffer);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = Math.max(0, tag.getInt(CHARGE_TAG));
        feBuffer = Math.max(0, tag.getInt(FE_TAG));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(CHARGE_TAG, charge);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.BATTERY_CHARGE.get());
        if (stored != null) {
            charge = Math.max(0, stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (charge > 0) {
            components.set(ModRegistry.BATTERY_CHARGE.get(), charge);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(CHARGE_TAG);
    }

    // ---- Hoppers and pipes: redstone goes in and is used up ----

    private final class RedstoneInput implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int used = insert(stack, simulate);
            return used <= 0 ? stack : stack.copyWithCount(stack.getCount() - used);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return valueOf(stack) > 0;
        }
    }

    // ---- FE from other mods ----

    private final class EnergyInput implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int perCharge = PowerCost.fePerCharge();
            long room = Math.max(0L, ((long) getCapacity() - charge) * perCharge - feBuffer);
            int accepted = (int) Math.max(0, Math.min(toReceive, room));
            if (accepted > 0 && !simulate) {
                long total = (long) feBuffer + accepted;
                feBuffer = (int) (total % perCharge);
                add((int) Math.min(Integer.MAX_VALUE, total / perCharge));
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, (long) charge * PowerCost.fePerCharge() + feBuffer);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, (long) getCapacity() * PowerCost.fePerCharge());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
