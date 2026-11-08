package com.reiko.signaltower.item;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBlockEntity;
import com.reiko.signaltower.blockentity.RadioRepeaterBlockEntity;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Coverage Map (v0.49.0): a map tuned to a frequency. Right-click to draw it where you stand;
 * while you hold it, it paints where that frequency can be heard: green where it's strong, yellow,
 * orange and red as it gets weaker (fade, hills, jamming), blank where it isn't heard at all, with
 * a red marker on each transmitter, a blue one on each repeater or converter sending on it and a
 * red X on each jammer. It's a real map underneath, so it shows in your hand and in item frames
 * like any other, lined up with vanilla maps of the same zoom. Sneak + right-click the air
 * changes its zoom; sneak + right-click a radio block copies that block's frequency.
 */
public class CoverageMapItem extends MapItem {
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";
    /** Rows painted each tick while it's held: the whole map every 32 ticks. */
    public static final int ROWS_PER_TICK = 4;
    public static final int DEFAULT_ZOOM = 2;
    private static final String MARKER = SignalTower.MOD_ID + ":tower_";

    /** Where each map is in its painting (the next row), and the tower markers on it: server-side only, not saved. */
    private static final Map<MapId, Integer> NEXT_ROW = new HashMap<>();
    private static final Map<MapId, Set<String>> MARKERS = new HashMap<>();

    public CoverageMapItem(Properties properties) {
        super(properties);
    }

    public static int zoomOf(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.COVERAGE_ZOOM.get(), DEFAULT_ZOOM);
    }

    /** How many blocks across a map at {@code zoom} covers. */
    public static int blocksAcross(int zoom) {
        return 128 << zoom;
    }

    /** The map color for a signal of {@code strength}: 0 (blank) for nothing, else green (strong) through yellow and orange to red (weak). */
    public static byte colorFor(int strength) {
        if (strength <= 0) {
            return 0;
        }
        int s = Math.min(15, strength);
        MapColor color = s >= 13 ? MapColor.EMERALD : s >= 10 ? MapColor.COLOR_LIGHT_GREEN : s >= 7 ? MapColor.COLOR_YELLOW
                : s >= 4 ? MapColor.COLOR_ORANGE : MapColor.COLOR_RED;
        int step = (s - 1) % 3; // the top of each band brightest
        MapColor.Brightness brightness = step == 2 ? MapColor.Brightness.HIGH : step == 1 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
        return color.getPackedId(brightness);
    }

    /** Right-click the air: draw the map here (sneaking: zoom out, back to 1:1 after 1:16). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            if (player.isSecondaryUseActive()) {
                int zoom = (zoomOf(stack) + 1) % 5;
                stack.set(ModRegistry.COVERAGE_ZOOM.get(), zoom);
                player.displayClientMessage(Component.translatable(KEY + "coverage_zoom", 1 << zoom, blocksAcross(zoom)), true);
            }
            draw(serverLevel, player, stack);
            int frequency = FrequencyTunerItem.getFrequency(stack);
            Component deaf = FrequencyClaims.deafness(level, frequency, player.getUUID(), "coverage_deaf");
            if (deaf != null) {
                player.displayClientMessage(deaf, true);
            } else if (!player.isSecondaryUseActive()) {
                int zoom = zoomOf(stack);
                player.displayClientMessage(Component.translatable(KEY + "coverage_drawn", FrequencyNames.label(level, frequency),
                        1 << zoom, blocksAcross(zoom)), true);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Makes {@code stack} show the map square (lined up like a vanilla map's, at its zoom) that
     * {@code player} stands in, starting to paint it from the top: the same map if it already
     * shows that square, else a new one. Returns its id.
     */
    public static MapId draw(ServerLevel level, Player player, ItemStack stack) {
        int zoom = zoomOf(stack);
        MapItemSavedData fresh = MapItemSavedData.createFresh(player.getX(), player.getZ(), (byte) zoom, true, false, level.dimension());
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData current = id == null ? null : level.getMapData(id);
        if (current == null || current.centerX != fresh.centerX || current.centerZ != fresh.centerZ
                || current.scale != fresh.scale || current.dimension != fresh.dimension) {
            id = level.getFreeMapId();
            level.setMapData(id, fresh);
            stack.set(DataComponents.MAP_ID, id);
        }
        NEXT_ROW.put(id, 0);
        return id;
    }

    /** Sneak + right-click a radio block: map the frequency it sends on (a converter's output), or its own. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            if (!level.isClientSide) {
                int frequency = radio instanceof RadioRepeaterBlockEntity relay && relay.isConverter() ? relay.getOutputFrequency() : radio.getFrequency();
                ItemStack stack = context.getItemInHand();
                FrequencyTunerItem.setFrequency(stack, frequency);
                MapId id = stack.get(DataComponents.MAP_ID);
                if (id != null) {
                    NEXT_ROW.put(id, 0); // repaint for the new frequency
                }
                player.displayClientMessage(Component.translatable(KEY + "coverage_copied", FrequencyNames.label(level, frequency)), true);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Keeps the holder's arrow on the map, and paints a few rows each tick while it's in a hand (not the vanilla map's terrain). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : serverLevel.getMapData(id);
        if (data == null) {
            return;
        }
        data.tickCarriedBy(player, stack);
        boolean inHand = selected || player.getOffhandItem() == stack;
        if (!inHand || data.dimension != serverLevel.dimension()) {
            return;
        }
        int row = NEXT_ROW.getOrDefault(id, 0);
        int frequency = FrequencyTunerItem.getFrequency(stack);
        SignalNetwork network = SignalNetwork.get(serverLevel);
        if (row == 0) {
            mark(serverLevel, network, frequency, id, data);
        }
        paintRows(serverLevel, network, frequency, player.getUUID(), data, row, ROWS_PER_TICK, player.getBlockY());
        NEXT_ROW.put(id, (row + ROWS_PER_TICK) % 128);
    }

    /**
     * Paints rows {@code from} to {@code from + rows - 1} of {@code data} with the strength
     * {@code viewer} would hear {@code frequency} at in each pixel's middle: on the ground there if
     * its chunk is loaded, else at {@code fallbackY}.
     */
    public static void paintRows(ServerLevel level, SignalNetwork network, int frequency, @Nullable UUID viewer, MapItemSavedData data,
                                 int from, int rows, int fallbackY) {
        int perPixel = 1 << data.scale;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = from; z < Math.min(128, from + rows); z++) {
            for (int x = 0; x < 128; x++) {
                int worldX = (data.centerX / perPixel + x - 64) * perPixel + perPixel / 2;
                int worldZ = (data.centerZ / perPixel + z - 64) * perPixel + perPixel / 2;
                int y = level.hasChunk(worldX >> 4, worldZ >> 4)
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) : fallbackY;
                pos.set(worldX, y, worldZ);
                data.setColor(x, z, colorFor(network.listen(level, frequency, pos.immutable(), viewer).power()));
            }
        }
    }

    /** Puts a marker on each tower on {@code frequency} inside the map (and takes old ones off). */
    private static void mark(ServerLevel level, SignalNetwork network, int frequency, MapId id, MapItemSavedData data) {
        Set<String> old = MARKERS.getOrDefault(id, Set.of());
        Set<String> now = new HashSet<>();
        List<Map.Entry<BlockPos, Integer>> towers = network.towersOn(frequency);
        for (Map.Entry<BlockPos, Integer> tower : towers) {
            if (now.size() >= 32) break;
            BlockPos pos = tower.getKey();
            String key = MARKER + pos.asLong();
            Holder<MapDecorationType> type = tower.getValue() == 2 ? MapDecorationTypes.RED_X
                    : tower.getValue() == 1 ? MapDecorationTypes.BLUE_MARKER : MapDecorationTypes.RED_MARKER;
            data.addDecoration(type, level, key, pos.getX() + 0.5, pos.getZ() + 0.5, 180.0, null);
            now.add(key);
        }
        for (String key : old) {
            if (!now.contains(key)) data.removeDecoration(key);
        }
        MARKERS.put(id, now);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + SignalTower.MOD_ID + ".";
        int frequency = FrequencyTunerItem.getFrequency(stack);
        tooltip.add(Component.translatable(prefix + "frequency", FrequencyNames.label(null, frequency)).withStyle(ChatFormatting.AQUA));
        FrequencyNotes.addTooltipLine(frequency, tooltip);
        int zoom = zoomOf(stack);
        tooltip.add(Component.translatable(prefix + "coverage_zoom", 1 << zoom, blocksAcross(zoom)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + "coverage_legend").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable(prefix + "coverage_help").withStyle(ChatFormatting.GRAY));
    }
}
