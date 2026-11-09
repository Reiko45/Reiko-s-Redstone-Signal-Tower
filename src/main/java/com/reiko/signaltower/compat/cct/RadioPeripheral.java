package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.compat.RadioComputer;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.Nullable;

/**
 * v0.58.0: what every radio peripheral has (only loaded with CC: Tweaked): the frequency, and the
 * computers attached, which get this block's "radio_signal" and "radio_message" events. The work
 * is done by {@link RadioComputer}.
 */
public abstract class RadioPeripheral<T extends RadioBlockEntity> implements IPeripheral, RadioComputer.Listener {
    protected final T block;
    private final AttachedComputerSet computers = new AttachedComputerSet();

    protected RadioPeripheral(T block) {
        this.block = block;
    }

    @Override
    public final void attach(IComputerAccess computer) {
        computers.add(computer);
        if (block.getLevel() != null) {
            RadioComputer.attach(block.getLevel(), block.getBlockPos(), this);
        }
    }

    @Override
    public final void detach(IComputerAccess computer) {
        computers.remove(computer);
        if (!computers.hasComputers() && block.getLevel() != null) {
            RadioComputer.detach(block.getLevel(), block.getBlockPos(), this);
        }
    }

    @Override
    public final void signal(int frequency, int strength) {
        computers.forEach(computer -> computer.queueEvent("radio_signal", computer.getAttachmentName(), frequency, strength));
    }

    @Override
    public final void message(int frequency, String text, String from) {
        computers.forEach(computer -> computer.queueEvent("radio_message", computer.getAttachmentName(), frequency, text, from));
    }

    @Override
    public final Object getTarget() {
        return block;
    }

    @Override
    public final boolean equals(@Nullable IPeripheral other) {
        return other instanceof RadioPeripheral<?> radio && radio.getClass() == getClass() && radio.block == block;
    }

    /** The frequency it's tuned to. */
    @LuaFunction(mainThread = true)
    public final int getFrequency() {
        return block.getFrequency();
    }

    /** Tunes it (1 to 999,999,999); an error if it can't be (a transmitter onto someone else's private frequency). */
    @LuaFunction(mainThread = true)
    public final void setFrequency(long frequency) throws LuaException {
        String refused = RadioComputer.setFrequency(block, frequency);
        if (refused != null) {
            throw new LuaException(refused);
        }
    }

    /** Its frequency's name, or nil. */
    @LuaFunction(mainThread = true)
    public final @Nullable String getFrequencyName() {
        return RadioComputer.frequencyName(block);
    }

    /** The strength heard where it stands on {@code frequency} (0 on someone else's encrypted frequency). */
    @LuaFunction(mainThread = true)
    public final int listen(long frequency) {
        return RadioComputer.listen(block, frequency);
    }
}
