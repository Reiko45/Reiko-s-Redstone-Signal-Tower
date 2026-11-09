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

    public static final ModConfigSpec.IntValue SIGNAL_FADE_START_PERCENT = BUILDER
            .comment("Signals fade toward the edge of a tower's reach: full strength up to this percent of the way out,",
                    "then weaker the farther out (see signalFadeAtEdge). Repeaters and converters hear the signal unfaded",
                    "and send it on at full strength. Compact transmitters, motion sensors and remote presses don't fade.",
                    "100 means signals don't fade.")
            .defineInRange("signalFadeStartPercent", 50, 0, 100);

    public static final ModConfigSpec.IntValue SIGNAL_FADE_AT_EDGE = BUILDER
            .comment("How much of a full-strength (15) signal is lost at the very edge of reach: 14 leaves 1. Weaker",
                    "signals lose the same share. Inside reach a signal never fades below 1. 0 means no fade.")
            .defineInRange("signalFadeAtEdge", 14, 0, 15);

    public static final ModConfigSpec.DoubleValue DISH_REACH_MULTIPLIER = BUILDER
            .comment("How many times its normal reach a tower with a Dish Antenna reaches in the direction it's aimed",
                    "(still capped by maxReach). Elsewhere it reaches as far as without an antenna.")
            .defineInRange("dishReachMultiplier", 3.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue DISH_CONE_DEGREES = BUILDER
            .comment("How wide a Dish Antenna's beam is: this many degrees either side of where it's aimed.")
            .defineInRange("dishConeDegrees", 15, 1, 90);

    public static final ModConfigSpec.BooleanValue TOWERS_NEED_POWER = BUILDER
            .comment("Power cost: full-size transmitters (plain, timer, recorder), repeaters, converters and jammers use",
                    "charge from a Radio Battery next to them (or next to a mast in their column) while they send; with",
                    "none, or only empty ones, they go quiet. Compact transmitters, motion sensors, keypads and remotes",
                    "are free. False: towers send for nothing, as before.")
            .define("towersNeedPower", true);

    public static final ModConfigSpec.DoubleValue CHARGE_PER_SECOND_PER_64_BLOCKS = BUILDER
            .comment("How much charge a tower uses each second it sends, for every 64 blocks of its reach (with its",
                    "antenna; a Dish Antenna counts as none). A transmitter reaching 56 blocks uses 0.875 a second.")
            .defineInRange("chargePerSecondPer64Blocks", 1.0, 0.0, 10000.0);

    public static final ModConfigSpec.IntValue BATTERY_CAPACITY = BUILDER
            .comment("How much charge a Radio Battery holds.")
            .defineInRange("batteryCapacity", 100000, 100, 1000000000);

    public static final ModConfigSpec.IntValue CHARGE_PER_REDSTONE = BUILDER
            .comment("How much charge one Redstone Dust puts in a Radio Battery (a Block of Redstone gives nine times as much).")
            .defineInRange("chargePerRedstone", 1000, 1, 10000000);

    public static final ModConfigSpec.IntValue FE_PER_CHARGE = BUILDER
            .comment("How much energy (FE, from other mods' cables and generators) makes one charge in a Radio Battery.")
            .defineInRange("fePerCharge", 10, 1, 1000000);

    public static final ModConfigSpec.DoubleValue SOLAR_CHARGE_PER_SECOND = BUILDER
            .comment("How much charge a Solar Panel puts in the Radio Battery under it each second in full sun (noon, clear",
                    "sky, nothing over it). Less in rain or thunder and early or late in the day; none at night.")
            .defineInRange("solarChargePerSecond", 3.0, 0.0, 100000.0);

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

    // v0.54.0: storm damage (only with Reiko's Weather) and guy wires.
    public static final ModConfigSpec.BooleanValue STORM_DAMAGE = BUILDER
            .comment("With Reiko's Weather: if true, a tornado, hurricane or derecho can blow a tall tower down (it goes",
                    "quiet until someone repairs it with an Iron Ingot), unless enough Guy Wire Anchors brace it, and",
                    "an ice storm ices up antennas (less reach until it thaws). Without Reiko's Weather nothing happens.")
            .define("stormDamage", true);

    public static final ModConfigSpec.IntValue STORM_DAMAGE_MIN_MASTS = BUILDER
            .comment("How many masts a tower needs under its top before storms can blow it down (shorter ones stand).")
            .defineInRange("stormDamageMinMasts", 4, 0, 4096);

    public static final ModConfigSpec.IntValue GUY_WIRES_TO_BRACE = BUILDER
            .comment("How many Guy Wire Anchors must brace a tower's mast for storms not to blow it down.")
            .defineInRange("guyWiresToBrace", 3, 1, 64);

    public static final ModConfigSpec.IntValue STORM_DAMAGE_CHANCE_PERCENT = BUILDER
            .comment("While a hurricane or derecho blows over a tall, unbraced tower: the chance, every 2 seconds, that it",
                    "blows down. A tornado touching it always does.")
            .defineInRange("stormDamageChancePercent", 10, 0, 100);

    public static final ModConfigSpec.IntValue ICE_REACH_LOSS_PERCENT = BUILDER
            .comment("How much reach a tower's iced-up antenna loses after an ice storm, in percent (on top of the weather's).")
            .defineInRange("iceReachLossPercent", 30, 0, 100);

    public static final ModConfigSpec.IntValue ICE_THAW_SECONDS = BUILDER
            .comment("How long the ice takes to thaw once the ice storm has moved on, counting only time above freezing",
                    "where the tower's top is.")
            .defineInRange("iceThawSeconds", 120, 0, 86400);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
