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

    public static final ModConfigSpec.DoubleValue IRON_ANTENNA_REACH = BUILDER
            .comment("How many times its normal reach a transmitter, repeater or converter with an Iron Antenna reaches",
                    "(still capped by maxReach).")
            .defineInRange("ironAntennaReach", 1.5, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue GOLD_ANTENNA_REACH = BUILDER
            .comment("The same for a Gold Antenna.")
            .defineInRange("goldAntennaReach", 2.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue DIAMOND_ANTENNA_REACH = BUILDER
            .comment("The same for a Diamond Antenna.")
            .defineInRange("diamondAntennaReach", 3.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue NETHERITE_ANTENNA_REACH = BUILDER
            .comment("The same for a Netherite Antenna.")
            .defineInRange("netheriteAntennaReach", 4.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue RAIN_REACH_LOSS = BUILDER
            .comment("How much shorter, in percent, the reach of transmitters, repeaters, converters and jammers is while",
                    "it rains or snows where they stand (towers in deserts, savannas and badlands, where it never rains,",
                    "aren't affected). 0 means rain doesn't matter.")
            .defineInRange("rainReachLoss", 15, 0, 90);

    public static final ModConfigSpec.IntValue THUNDER_REACH_LOSS = BUILDER
            .comment("The same during a thunderstorm.")
            .defineInRange("thunderReachLoss", 30, 0, 90);

    public static final ModConfigSpec.IntValue TERRAIN_BLOCKS_PER_STRENGTH = BUILDER
            .comment("Hills and other ground between a tower and whatever hears it weaken the signal: every this many",
                    "blocks of solid ground (or liquid) the straight line from the tower's top runs through take 1 off",
                    "its strength (15 x this many block it completely). Leaves, caves and roofs above the line don't count;",
                    "nor do the first and last 8 blocks, so the building around a receiver doesn't. Only loaded chunks",
                    "count. 0 means terrain doesn't matter.")
            .defineInRange("terrainBlocksPerStrength", 8, 0, 100_000);

    public static final ModConfigSpec.IntValue RECEIVER_HEIGHT_PERCENT = BUILDER
            .comment("Receivers (and repeaters, converters, pagers, meters and scanners) standing higher than a tower hear it",
                    "from farther: as far as this percent of what a transmitter at the receiver's height would reach",
                    "(if that's more than the tower's own reach). Compact transmitters and motion sensors keep their",
                    "short reach. 0 means a receiver's height doesn't matter.")
            .defineInRange("receiverHeightPercent", 100, 0, 1000);

    public static final ModConfigSpec.DoubleValue LIGHTNING_STRIKES_PER_HOUR = BUILDER
            .comment("How often, on average, lightning strikes a tower 64 blocks above sea level while a thunderstorm",
                    "rages where it stands, in strikes per hour (a tower half as high, half as often). Only towers open",
                    "to the sky can be hit. A Lightning Rod within 2 blocks of the tower's top takes the hit instead.",
                    "Bolts that land on a tower some other way (vanilla lightning, a channeling trident, /summon) count",
                    "too. 0 means lightning never picks towers.")
            .defineInRange("lightningStrikesPerHour", 6.0, 0.0, 3600.0);

    public static final ModConfigSpec.IntValue LIGHTNING_OUTAGE_SECONDS = BUILDER
            .comment("How long, in seconds, a tower struck by lightning stays quiet (sends, relays and jams nothing).")
            .defineInRange("lightningOutageSeconds", 5, 1, 600);

    public static final ModConfigSpec.IntValue JAMMER_REACH_PERCENT = BUILDER
            .comment("How far a Radio Jammer reaches, in percent of what a transmitter at the same height",
                    "(with the same antenna) would.")
            .defineInRange("jammerReachPercent", 50, 1, 1000);

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
