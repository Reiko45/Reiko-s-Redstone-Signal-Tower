package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.compat.RadioComputer;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.List;
import java.util.Map;

/** v0.58.0: any Radio Receiver (toggle, lamp, speaker...) as a "radio_receiver" peripheral, with "radio_signal" events. */
public final class ReceiverPeripheral extends RadioPeripheral<RadioReceiverBlockEntity> {
    public ReceiverPeripheral(RadioReceiverBlockEntity block) {
        super(block);
    }

    @Override
    public String getType() {
        return "radio_receiver";
    }

    /** The strength it hears on its frequency. */
    @LuaFunction(mainThread = true)
    public final int getSignal() {
        return RadioComputer.listen(block, block.getFrequency());
    }

    /** The redstone it puts out. */
    @LuaFunction(mainThread = true)
    public final int getOutput() {
        return RadioComputer.output(block);
    }

    /** Everything it could hear here: a list of {frequency, strength, name}, strongest first. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> scan() {
        return RadioComputer.scan(block);
    }
}
