package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.blockentity.TelegraphBlockEntity;
import com.reiko.signaltower.compat.RadioComputer;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;

import java.util.List;
import java.util.Map;

/** v0.58.0: a Radio Telegraph as a "radio_telegraph" peripheral, with "radio_message" events. */
public final class TelegraphPeripheral extends RadioPeripheral<TelegraphBlockEntity> {
    public TelegraphPeripheral(TelegraphBlockEntity block) {
        super(block);
    }

    @Override
    public String getType() {
        return "radio_telegraph";
    }

    /** Sends {@code text} (up to 64 characters) as "Computer N": true, or false and why not. */
    @LuaFunction(mainThread = true)
    public final MethodResult send(IComputerAccess computer, String text) {
        String refused = RadioComputer.send(block, computer.getID(), text);
        return refused == null ? MethodResult.of(true) : MethodResult.of(false, refused);
    }

    /** The messages that reached it, oldest first: a list of {text, from, secondsAgo}. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getMessages() {
        return RadioComputer.messages(block);
    }
}
