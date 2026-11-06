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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Holds the frequency of a transmitter or receiver. The frequency is saved with the world, and is
 * copied onto the item when the block is broken, so a re-placed block keeps its tuning.
 * <p>
 * It also remembers who last tuned or placed it ({@link #getTunedBy()}), which decides whether a
 * transmitter, repeater or converter may broadcast on a private frequency. That isn't copied onto
 * the item: whoever places the block next becomes its tuner.
 */
public abstract class RadioBlockEntity extends BlockEntity {
    private static final String FREQUENCY_TAG = "frequency";
    private static final String TUNED_BY_TAG = "tuned_by";

    private int frequency = Frequency.DEFAULT;
    private @Nullable UUID tunedBy;

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

    /** Who last tuned or placed this block, or null if nobody is known (tuned before v0.25, or by a command). */
    public @Nullable UUID getTunedBy() {
        return tunedBy;
    }

    public void setTunedBy(@Nullable UUID player) {
        if (!Objects.equals(player, tunedBy)) {
            tunedBy = player;
            setChanged();
            onTunedByChanged();
        }
    }

    /** Called on the server after {@link #setTunedBy} changes who tuned it. */
    protected void onTunedByChanged() {
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(FREQUENCY_TAG, frequency);
        if (tunedBy != null) {
            tag.putUUID(TUNED_BY_TAG, tunedBy);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequency = tag.contains(FREQUENCY_TAG) ? Frequency.clamp(tag.getInt(FREQUENCY_TAG)) : Frequency.DEFAULT;
        tunedBy = tag.hasUUID(TUNED_BY_TAG) ? tag.getUUID(TUNED_BY_TAG) : null;
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
