package com.reiko.signaltower.compat.wthit;

import com.reiko.signaltower.block.RadioBlock;
import com.reiko.signaltower.block.RadioMastBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.63.0: the blocks the WTHIT plugin covers (the same ones as the Jade plugin), and what it has
 * registered with WTHIT so far. No WTHIT classes here, so the rest of the mod (and the tests) can
 * use it whether or not WTHIT is installed.
 */
public final class WthitBlocks {
    /** Every {@link RadioBlock} (transmitters, receivers, repeaters, telegraphs, the Morse Key, the printer ...), masts, and the other tower parts. */
    public static final List<Class<?>> COVERED = List.of(RadioBlock.class, RadioMastBlock.class,
            com.reiko.signaltower.block.StatusBoardBlock.class, com.reiko.signaltower.block.AviationBeaconBlock.class,
            com.reiko.signaltower.block.RadioBatteryBlock.class, com.reiko.signaltower.block.SolarPanelBlock.class,
            com.reiko.signaltower.block.BundledCableBlock.class, com.reiko.signaltower.block.CableTapBlock.class,
            com.reiko.signaltower.block.GuyAnchorBlock.class, com.reiko.signaltower.block.LaunchPadBlock.class);

    private static final List<String> REGISTERED = Collections.synchronizedList(new ArrayList<>());

    private WthitBlocks() {
    }

    /** Called by the plugins: "common:RadioBlock", "client:RadioBlock" ... */
    static void registered(String side, Class<?> block) {
        REGISTERED.add(side + ":" + block.getSimpleName());
    }

    /** What the plugins have registered with WTHIT (empty without it). */
    public static List<String> registered() {
        return List.copyOf(REGISTERED);
    }
}
