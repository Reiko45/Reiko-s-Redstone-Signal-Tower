package com.reiko.signaltower.radio;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The antenna on a tower top (v0.45.0): none, or an Iron, Gold, Diamond or Netherite Antenna, each
 * multiplying the tower's reach (by 1.5, 2, 3 and 4 with the default config). It's a block state
 * property ({@link #PROPERTY}, "antenna") of the full-size transmitters (plain, timer, recorder),
 * repeaters and converters, so the client draws it and the item keeps it. Saved by name: keep the names.
 */
public enum AntennaTier implements StringRepresentable {
    NONE("none"),
    IRON("iron"),
    GOLD("gold"),
    DIAMOND("diamond"),
    NETHERITE("netherite"),
    /** v0.48.0: aimed ({@link #AIM}); {@link #dishMultiplier} times the reach inside its beam, normal elsewhere. */
    DISH("dish"),
    /** v0.50.0: on a transmitter, also sends into another dimension ({@link CrossDimension}); reach as with none. */
    ENDER("ender");

    public static final EnumProperty<AntennaTier> PROPERTY = EnumProperty.create("antenna", AntennaTier.class);
    /**
     * Where a tower top's Dish Antenna points (v0.48.0): the vanilla 16-step rotation, 0 = north,
     * 4 = east, 8 = south, 12 = west ({@link RotationSegment}). Set from the player's facing on
     * placing and when a dish is fitted; it only matters with a dish.
     */
    public static final IntegerProperty AIM = BlockStateProperties.ROTATION_16;

    private final String name;

    AntennaTier(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** How many times its tower's reach this antenna gives (from the server config; 1 for none). */
    public double multiplier() {
        return switch (this) {
            case NONE -> 1.0;
            case IRON -> value(Config.IRON_ANTENNA_REACH);
            case GOLD -> value(Config.GOLD_ANTENNA_REACH);
            case DIAMOND -> value(Config.DIAMOND_ANTENNA_REACH);
            case NETHERITE -> value(Config.NETHERITE_ANTENNA_REACH);
            case DISH -> 1.0; // outside its beam; see dishMultiplier()
            case ENDER -> 1.0;
        };
    }

    // ---- Dish Antenna (v0.48.0) ----

    /** How many times its reach a dish tower reaches inside its beam (config). */
    public static double dishMultiplier() {
        return value(Config.DISH_REACH_MULTIPLIER);
    }

    /** Half the beam's width, in degrees (config). */
    public static int dishConeDegrees() {
        var setting = Config.DISH_CONE_DEGREES;
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    /** {@code reach} (the tower's reach without an antenna) inside a dish's beam: times the multiplier, capped by maxReach. */
    public static int dishReach(int reach) {
        double far = reach * dishMultiplier();
        int max = Config.SPEC.isLoaded() ? Config.MAX_REACH.get() : Config.MAX_REACH.getDefault();
        if (max > 0) {
            far = Math.min(far, Math.max(reach, max));
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(far));
    }

    /** True if a block {@code dx, dz} from a dish aimed {@code aim} is inside its beam (the tower's own column always is). */
    public static boolean inBeam(long dx, long dz, int aim) {
        return inBeam(dx, dz, aim, dishConeDegrees());
    }

    /** {@link #inBeam(long, long, int)} for a beam {@code cone} degrees either side of the aim (the reach ring's, from the server). */
    public static boolean inBeam(double dx, double dz, int aim, int cone) {
        if (dx == 0 && dz == 0) {
            return true;
        }
        double yaw = Math.toRadians(aim * 22.5 - 180.0);
        double ax = -Math.sin(yaw);
        double az = Math.cos(yaw);
        double length = Math.sqrt(dx * dx + dz * dz);
        return (ax * dx + az * dz) / length >= Math.cos(Math.toRadians(cone)) - 1e-9;
    }

    /** The aim for a player facing {@code yaw} (the way they look): 0 north, 4 east, 8 south, 12 west. */
    public static int aimFor(float yaw) {
        return RotationSegment.convertToSegment(yaw + 180.0F);
    }

    /** {@code state} aimed the way a player facing {@code yaw} looks (unchanged if it can't hold an aim). */
    public static BlockState aimed(BlockState state, float yaw) {
        return state.hasProperty(AIM) ? state.setValue(AIM, aimFor(yaw)) : state;
    }

    /** Where a block in {@code state} is aimed (0 if it can't be). */
    public static int aimOf(BlockState state) {
        return state.hasProperty(AIM) ? state.getValue(AIM) : 0;
    }

    private static final String[] AIM_KEYS = {"n", "nne", "ne", "ene", "e", "ese", "se", "sse", "s", "ssw", "sw", "wsw", "w", "wnw", "nw", "nnw"};

    /** "north-east", "north-north-east" ... for an aim. */
    public static Component aimLabel(int aim) {
        return Component.translatable("direction." + SignalTower.MOD_ID + "." + AIM_KEYS[aim & 15]);
    }

    /** The config's value, or its default before the config is loaded (an item tooltip in the main menu). */
    private static double value(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue setting) {
        return Config.SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    /** "×1.5", "×3" (a dish: inside its beam). */
    public String multiplierText() {
        double value = this == DISH ? dishMultiplier() : multiplier();
        String number = value == Math.floor(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
        return "×" + number;
    }

    /** "Diamond Antenna". */
    public Component label() {
        return Component.translatable("item." + SignalTower.MOD_ID + "." + name + "_antenna");
    }

    /** The antenna item for this tier (none for {@link #NONE}). */
    public @Nullable Item item() {
        return switch (this) {
            case NONE -> null;
            case IRON -> ModRegistry.IRON_ANTENNA.get();
            case GOLD -> ModRegistry.GOLD_ANTENNA.get();
            case DIAMOND -> ModRegistry.DIAMOND_ANTENNA.get();
            case NETHERITE -> ModRegistry.NETHERITE_ANTENNA.get();
            case DISH -> ModRegistry.DISH_ANTENNA.get();
            case ENDER -> ModRegistry.ENDER_ANTENNA.get();
        };
    }

    /** The antenna on a block in {@code state}: none for blocks that can't have one. */
    public static AntennaTier of(BlockState state) {
        return state.hasProperty(PROPERTY) ? state.getValue(PROPERTY) : NONE;
    }

    /** Where the Dish Antenna on a block in {@code state} points (0-15), or -1 if it has no dish (v0.48.0). */
    public static int dishAim(BlockState state) {
        return of(state) == DISH && state.hasProperty(AIM) ? state.getValue(AIM) : -1;
    }

    /**
     * True if a tower top in {@code state} can send into another dimension with an Ender Antenna
     * (v0.50.0): a full-size transmitter (plain, timer, recorder), not a repeater, converter or jammer.
     */
    public static boolean sendsAcross(BlockState state) {
        return fits(state) && state.getBlock() instanceof com.reiko.signaltower.block.RadioTransmitterBlock
                && !(state.getBlock() instanceof com.reiko.signaltower.block.RadioJammerBlock);
    }

    /** True if a block in {@code state} takes an antenna (a full-size tower top). */
    public static boolean fits(BlockState state) {
        return state.hasProperty(PROPERTY);
    }

    /** The antenna a tower top's item carries (in its block state component), or none. */
    public static AntennaTier of(ItemStack stack) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        AntennaTier tier = properties == null ? null : properties.get(PROPERTY);
        return tier == null ? NONE : tier;
    }

    /** Adds "Antenna: Diamond Antenna (×3 reach)" to a tower top item's tooltip, if it has one. */
    public static void addTooltipLine(ItemStack stack, List<Component> tooltip) {
        AntennaTier tier = of(stack);
        if (tier != NONE) {
            tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + (tier == DISH ? ".antenna_dish" : tier == ENDER ? ".antenna_ender" : ".antenna"),
                    tier.label(), tier.multiplierText()).withStyle(ChatFormatting.GOLD));
        }
    }
}
