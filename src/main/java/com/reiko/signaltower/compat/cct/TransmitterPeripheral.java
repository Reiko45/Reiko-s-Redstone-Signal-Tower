package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.compat.RadioComputer;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

/** v0.58.0: a Radio Transmitter (or Compact one) as a "radio_transmitter" peripheral. */
public final class TransmitterPeripheral extends RadioPeripheral<RadioTransmitterBlockEntity> {
    public TransmitterPeripheral(RadioTransmitterBlockEntity block) {
        super(block);
    }

    @Override
    public String getType() {
        return "radio_transmitter";
    }

    /** Sends {@code strength} (0 to 15), or its redstone if that's higher, until a computer changes it or none is attached. */
    @LuaFunction(mainThread = true)
    public final void setSignal(int strength) throws LuaException {
        if (strength < 0 || strength > 15) {
            throw new LuaException("Strength must be 0 to 15");
        }
        block.setComputerSignal(strength);
    }

    /** The strength a computer set. */
    @LuaFunction(mainThread = true)
    public final int getSignal() {
        return block.getComputerSignal();
    }

    /** The strength it's sending now (0 while silenced, struck by lightning or out of power). */
    @LuaFunction(mainThread = true)
    public final int getSending() {
        return RadioComputer.sending(block);
    }

    /** "sending", "quiet", "silenced", "struck" or "no_power". */
    @LuaFunction(mainThread = true)
    public final String getStatus() {
        return RadioComputer.status(block);
    }

    /** How far it reaches now, in blocks. */
    @LuaFunction(mainThread = true)
    public final int getReach() {
        return RadioComputer.reach(block);
    }
}
