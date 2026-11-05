package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the frequency of a transmitter or receiver. The frequency is saved with the world, and is
 * copied onto the item when the block is broken, so a re-placed block keeps its tuning.
 */
public abstract class RadioBlockEntity extends BlockEntity {
    private static final String FREQUENCY_TAG = "frequency";

    private int frequency = Frequency.DEFAULT;

    protected RadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int newFrequency) {
        int clamped = Frequency.clamp(newFrequency);
        if (clamped != frequency) {
            frequency = clamped;
            setChanged();
            onFrequencyChanged();
        }
    }

    /** Called on the server after the frequency changes. */
    protected abstract void onFrequencyChanged();

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(FREQUENCY_TAG, frequency);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequency = tag.contains(FREQUENCY_TAG) ? Frequency.clamp(tag.getInt(FREQUENCY_TAG)) : Frequency.DEFAULT;
    }

    // ---- Item <-> block (keeps the frequency through break and re-place) ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        Integer stored = componentInput.get(ModRegistry.FREQUENCY.get());
        if (stored != null) {
            frequency = Frequency.clamp(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.FREQUENCY.get(), frequency);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(FREQUENCY_TAG);
    }
}
