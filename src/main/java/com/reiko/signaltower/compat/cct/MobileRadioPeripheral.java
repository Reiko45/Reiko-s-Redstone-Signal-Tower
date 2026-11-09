package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.compat.MobileRadio;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.Telegraph;
import com.reiko.signaltower.registry.ModRegistry;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * v0.59.0: the "radio" peripheral of a Radio Turtle or Radio Pocket Computer (a Radio Pager
 * fitted). Its frequency is kept in the upgrade's data (the pager's own frequency), so it comes
 * back out on the pager. The work is done by {@link MobileRadio}.
 */
public final class MobileRadioPeripheral implements IPeripheral, MobileRadio.Device {
    /** Where the radio is fitted: a turtle's side or a pocket computer. */
    public interface Host {
        @Nullable ServerLevel level();

        BlockPos position();

        @Nullable UUID owner();

        /** The upgrade's data (the pager's components), and setting it. */
        DataComponentPatch data();

        void setData(DataComponentPatch data);

        /** "computer.reikos_signal_tower.turtle" or "...pocket": what its messages are signed with ("Turtle %s"). */
        String nameKey();

        /** The same turtle side or pocket computer. */
        boolean sameAs(Host other);
    }

    /** How often (ticks) it checks what it hears, for "radio_signal". */
    private static final int CHECK_TICKS = 10;
    private final Host host;
    private final AttachedComputerSet computers = new AttachedComputerSet();
    private int lastHeard = -1;
    private int timer;
    private long lastSent = Long.MIN_VALUE;

    public MobileRadioPeripheral(Host host) {
        this.host = host;
    }

    @Override
    public String getType() {
        return "radio";
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
        MobileRadio.add(this);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        if (!computers.hasComputers()) {
            MobileRadio.remove(this);
        }
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MobileRadioPeripheral radio && radio.host.sameAs(host);
    }

    @Override
    public Object getTarget() {
        return host;
    }

    // ---- MobileRadio.Device ----

    @Override
    public @Nullable ServerLevel level() {
        return host.level();
    }

    @Override
    public BlockPos position() {
        return host.position();
    }

    @Override
    public int frequency() {
        return frequencyOf(host.data());
    }

    @Override
    public @Nullable UUID owner() {
        return host.owner();
    }

    @Override
    public void message(int frequency, String text, String from) {
        computers.forEach(computer -> computer.queueEvent("radio_message", computer.getAttachmentName(), frequency, text, from));
    }

    /** The frequency kept in the upgrade's data (a pager's), or 1. */
    public static int frequencyOf(DataComponentPatch data) {
        Optional<? extends Integer> stored = data.get(ModRegistry.FREQUENCY.get());
        return stored != null && stored.isPresent() ? Frequency.clamp(stored.get()) : Frequency.DEFAULT;
    }

    /** {@code data} with its frequency set to {@code frequency} (its other components kept). */
    public static DataComponentPatch withFrequency(DataComponentPatch data, int frequency) {
        ItemStack pager = new ItemStack(ModRegistry.PAGER.get());
        pager.applyComponents(data);
        pager.set(ModRegistry.FREQUENCY.get(), frequency);
        return pager.getComponentsPatch();
    }

    /**
     * Every tick from the upgrade: every half second, a "radio_signal" event if what it hears on
     * its frequency has changed. Returns what it last heard (for the pocket computer's light).
     */
    public int tick() {
        if (--timer > 0) {
            return Math.max(0, lastHeard);
        }
        timer = CHECK_TICKS;
        ServerLevel level = host.level();
        if (level == null) {
            return Math.max(0, lastHeard);
        }
        int frequency = frequency();
        int heard = MobileRadio.heard(level, host.position(), frequency, host.owner());
        if (heard != lastHeard) {
            boolean first = lastHeard < 0;
            lastHeard = heard;
            if (!first || heard > 0) {
                computers.forEach(computer -> computer.queueEvent("radio_signal", computer.getAttachmentName(), frequency, heard));
            }
        }
        return heard;
    }

    private ServerLevel levelOrThrow() throws LuaException {
        ServerLevel level = host.level();
        if (level == null) {
            throw new LuaException("Not in a world");
        }
        return level;
    }

    // ---- Lua ----

    /** The frequency it's tuned to. */
    @LuaFunction(mainThread = true)
    public final int getFrequency() {
        return frequency();
    }

    /** Tunes it (1 to 999,999,999). */
    @LuaFunction(mainThread = true)
    public final void setFrequency(long frequency) throws LuaException {
        if (frequency < Frequency.MIN || frequency > Frequency.MAX) {
            throw new LuaException(Component.translatable("computer.reikos_signal_tower.frequency_range", Frequency.MIN, Frequency.MAX).getString());
        }
        host.setData(withFrequency(host.data(), (int) frequency));
        lastHeard = -1;
        timer = 0;
    }

    /** Its frequency's name, or nil. */
    @LuaFunction(mainThread = true)
    public final @Nullable String getFrequencyName() throws LuaException {
        return FrequencyNames.nameOf(levelOrThrow(), frequency());
    }

    /** The strength heard where it is, on its frequency. */
    @LuaFunction(mainThread = true)
    public final int getSignal() throws LuaException {
        return MobileRadio.heard(levelOrThrow(), host.position(), frequency(), host.owner());
    }

    /** The strength heard where it is, on any frequency. */
    @LuaFunction(mainThread = true)
    public final int listen(long frequency) throws LuaException {
        return MobileRadio.heard(levelOrThrow(), host.position(), frequency, host.owner());
    }

    /** Everything that could be heard where it is: a list of {frequency, strength, name}, strongest first. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> scan() throws LuaException {
        return MobileRadio.scan(levelOrThrow(), host.position(), host.owner());
    }

    /** How far it reaches from where it is, in blocks (by its height). */
    @LuaFunction(mainThread = true)
    public final int getReach() throws LuaException {
        return MobileRadio.reach(levelOrThrow(), host.position());
    }

    /**
     * Sends a full-strength pulse on its frequency, {@code seconds} long (0.1 to 10, default 0.5),
     * like a Remote Control: true and how many receivers and pagers heard it, or false and why not.
     */
    @LuaFunction(mainThread = true)
    public final MethodResult pulse(IComputerAccess computer, Optional<Double> seconds) throws LuaException {
        int ticks = (int) Math.round(seconds.orElse(0.5) * 20);
        MobileRadio.Pulse pulse = MobileRadio.pulse(levelOrThrow(), host.position(), frequency(), host.owner(),
                senderName(computer), Math.max(PulseLength.MIN_TICKS, ticks));
        return pulse.sent() ? MethodResult.of(true, pulse.heardBy()) : MethodResult.of(false, pulse.refusal());
    }

    /** Sends {@code text} (up to 64 characters) as a telegraph message, signed "Turtle 3": true, or false and why not. */
    @LuaFunction(mainThread = true)
    public final MethodResult send(IComputerAccess computer, String text) throws LuaException {
        ServerLevel level = levelOrThrow();
        long now = level.getGameTime();
        if (lastSent != Long.MIN_VALUE && now - lastSent < Telegraph.COOLDOWN_TICKS && now >= lastSent) {
            return MethodResult.of(false, Component.translatable("message.reikos_signal_tower.telegraph_too_fast").getString());
        }
        String refused = MobileRadio.send(level, host.position(), frequency(), host.owner(), senderName(computer), text);
        if (refused != null) {
            return MethodResult.of(false, refused);
        }
        lastSent = now;
        return MethodResult.of(true);
    }

    /** The messages on its frequency that could be read where it is, oldest first: a list of {text, from, secondsAgo}. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getMessages() throws LuaException {
        return MobileRadio.messages(levelOrThrow(), host.position(), frequency(), host.owner());
    }

    private String senderName(IComputerAccess computer) {
        return Component.translatable(host.nameKey(), computer.getID()).getString();
    }
}
