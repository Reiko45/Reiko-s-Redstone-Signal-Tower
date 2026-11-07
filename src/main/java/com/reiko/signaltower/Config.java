package com.reiko.signaltower;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side settings. Edit them in {@code <world>/serverconfig/reikos_signal_tower-server.toml}
 * or from the Mods menu in game.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BASE_REACH = BUILDER
            .comment("Reach, in blocks, of a transmitter at or below sea level.")
            .defineInRange("baseReach", 32, 0, 1_000_000);

    public static final ModConfigSpec.IntValue REACH_PER_BLOCK = BUILDER
            .comment("Extra reach, in blocks, for every block the transmitter sits above sea level.",
                    "Example with the defaults: a transmitter at Y=100 in the Overworld (37 above sea level)",
                    "reaches 32 + 37 x 8 = 328 blocks.")
            .defineInRange("reachPerBlockOfHeight", 8, 0, 100_000);

    public static final ModConfigSpec.IntValue MAX_REACH = BUILDER
            .comment("Upper limit on any transmitter's reach, in blocks. 0 means no limit.")
            .defineInRange("maxReach", 0, 0, 30_000_000);

    public static final ModConfigSpec.IntValue COMPACT_TRANSMITTER_REACH = BUILDER
            .comment("Reach, in blocks, of a Compact Radio Transmitter. It's the same at any height (still capped by maxReach).")
            .defineInRange("compactTransmitterReach", 16, 1, 1_000_000);

    public static final ModConfigSpec.BooleanValue TOWERS_RELAY_REMOTES = BUILDER
            .comment("If true, every Radio Transmitter and Radio Repeater within a Remote Control's reach relays",
                    "its presses: the signal goes out again from the tower with the tower's reach.",
                    "Works on any frequency. If false, a remote only reaches as far as its own reach",
                    "(repeaters on the press's own frequency still pass it on).")
            .define("towersRelayRemotes", true);

    public static final ModConfigSpec.IntValue PRIVATE_FREQUENCIES_PER_PLAYER = BUILDER
            .comment("How many frequencies each player may make private in the Frequency Tuner's screen.",
                    "On a private frequency only its owner and their friends can send (remotes, transmitters,",
                    "repeaters and converters) or rename it. 0 means players can't make frequencies private.",
                    "Server operators can always make any private frequency public again.")
            .defineInRange("privateFrequenciesPerPlayer", 32, 0, 100_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
