package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.SignalMeterItem;
import com.reiko.signaltower.network.MeterReadingPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Signal Meter's display (v0.40.0): while a meter is in either hand (the main hand's first) and
 * its latest reading is for that meter's frequency and fresh, a small panel to the right of the
 * hotbar shows the frequency, a 15-segment bar with the strength, and the towers in reach with the
 * blocks left to the edge. It also ticks like a Geiger counter (unless the meter's sound is off):
 * every 20 ticks at strength 1, every 2 at 15, higher-pitched the stronger it is. The item's needle
 * ({@link #needle}) follows the same reading.
 */
public final class SignalMeterHud {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "signal_meter");
    private static final String KEY = "hud." + SignalTower.MOD_ID + ".meter_";
    /** A reading older than this (in ticks) isn't shown: the server stopped sending, or the meter changed hands. */
    private static final int FRESH_TICKS = 20;
    /** The panel's smallest width; it grows to fit its longest line. */
    private static final int MIN_WIDTH = 132;
    private static final int HEIGHT = 34;

    private static @Nullable MeterReadingPayload reading;
    private static long readAt;
    private static int tickCountdown;

    private SignalMeterHud() {}

    public static void onReading(MeterReadingPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        reading = payload;
        readAt = level != null ? level.getGameTime() : 0;
    }

    public static void clear() {
        reading = null;
    }

    /** The meter in the main hand, else in the off-hand; empty if neither holds one. */
    private static ItemStack heldMeter(Player player) {
        if (player.getMainHandItem().getItem() instanceof SignalMeterItem) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof SignalMeterItem ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    /** The latest reading if it's fresh and for {@code meter}'s frequency, else null. */
    private static @Nullable MeterReadingPayload readingFor(ItemStack meter) {
        Minecraft minecraft = Minecraft.getInstance();
        MeterReadingPayload current = reading;
        if (current == null || meter.isEmpty() || minecraft.level == null
                || minecraft.level.getGameTime() - readAt > FRESH_TICKS
                || current.frequency() != FrequencyTunerItem.getFrequency(meter)) {
            return null;
        }
        return current;
    }

    /**
     * The item model's "meter_level" property: the needle's position, 0 (resting) for anything but a
     * meter the local player holds with a fresh reading; else 0.33 / 0.66 / 1.0 for strength 1-5,
     * 6-10 and 11-15.
     */
    public static float needle(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null || entity != minecraft.player) {
            return 0.0F;
        }
        ItemStack held = heldMeter(minecraft.player);
        if (held.isEmpty() || !ItemStack.isSameItemSameComponents(held, stack)) {
            return 0.0F;
        }
        MeterReadingPayload current = readingFor(stack);
        if (current == null || current.strength() <= 0) {
            return 0.0F;
        }
        return current.strength() <= 5 ? 0.33F : current.strength() <= 10 ? 0.66F : 1.0F;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.isPaused()) {
            return;
        }
        ItemStack meter = heldMeter(player);
        MeterReadingPayload current = readingFor(meter);
        if (current == null || current.strength() <= 0 || SignalMeterItem.isSilent(meter)) {
            tickCountdown = 0;
            return;
        }
        if (--tickCountdown <= 0) {
            tickCountdown = Math.max(2, 21 - current.strength() * 19 / 15);
            float pitch = 0.7F + current.strength() * 0.06F;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(com.reiko.signaltower.registry.ModSounds.METER_TICK.get(), pitch, 0.4F));
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        ItemStack meter = heldMeter(player);
        MeterReadingPayload current = readingFor(meter);
        if (current == null) {
            return;
        }
        Font font = minecraft.font;
        Component label = Component.translatable(KEY + "frequency", FrequencyNames.label(null, current.frequency()));
        Component middle;
        Component detail;
        if (current.deaf()) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(current.frequency());
            middle = Component.translatable(KEY + "encrypted", claim != null ? claim.ownerName() : "?").withStyle(ChatFormatting.RED);
            detail = Component.translatable(KEY + "encrypted_hint").withStyle(ChatFormatting.DARK_GRAY);
        } else {
            middle = null;
            if (current.margin() < 0) {
                detail = Component.translatable(KEY + "out_of_reach").withStyle(ChatFormatting.DARK_GRAY);
            } else {
                // v0.61.0: through a satellite there's no edge in the dimension.
                Component edge = current.margin() >= com.reiko.signaltower.radio.SignalNetwork.UPLINK_REACH / 2 ? Component.translatable(KEY + "edge_satellite")
                        : current.margin() == 0 ? Component.translatable(KEY + "edge_at")
                        : current.margin() == 1 ? Component.translatable(KEY + "edge_block")
                        : Component.translatable(KEY + "edge_blocks", current.margin());
                detail = (current.towers() > 0
                        ? Component.translatable(KEY + "detail", current.towers() == 1 ? Component.translatable(KEY + "towers_one")
                                : Component.translatable(KEY + "towers", current.towers()), edge)
                        : edge.copy()).withStyle(ChatFormatting.GRAY);
            }
        }
        // v0.46.0: what hills and a jammer take off, on a line of its own; v0.48.0: and fading.
        Component loss = null;
        if (!current.deaf()) {
            for (Component part : new Component[] {
                    current.faded() > 0 ? Component.translatable(KEY + "faded", current.faded()).withStyle(ChatFormatting.GRAY) : null,
                    current.hills() > 0 ? Component.translatable(KEY + "hills", current.hills()).withStyle(ChatFormatting.GOLD) : null,
                    current.jammed() > 0 ? Component.translatable(KEY + "jammed", current.jammed()).withStyle(ChatFormatting.LIGHT_PURPLE) : null}) {
                if (part != null) {
                    loss = loss == null ? part : Component.translatable(KEY + "loss_both", loss, part);
                }
            }
        }
        int height = HEIGHT + (loss != null ? 10 : 0);
        int width = Math.max(MIN_WIDTH, Math.max(Math.max(font.width(detail), loss == null ? 0 : font.width(loss)),
                middle == null ? 0 : font.width(middle)) + 8);
        if (!current.deaf() && current.strength() <= 0 && current.jammed() > 0) {
            width = Math.max(width, 4 + 15 * 6 + 4 + font.width(Component.translatable(KEY + "jammed_short")) + 4);
        }
        width = Math.max(MIN_WIDTH, Math.min(width, graphics.guiWidth() - 4));
        int centerX = graphics.guiWidth() / 2;
        // To the right of the hotbar; above it on the right if the window is too narrow.
        int x = centerX + 98;
        int y = graphics.guiHeight() - height - 2;
        if (x + width > graphics.guiWidth() - 2) {
            x = graphics.guiWidth() - width - 2;
            y = graphics.guiHeight() - height - 60;
        }
        graphics.fill(x, y, x + width, y + height, 0xA0000000);
        graphics.fill(x, y, x + width, y + 1, 0x60FFFFFF);
        graphics.drawString(font, font.plainSubstrByWidth(label.getString(), width - 8), x + 4, y + 3, 0x55FFFF);

        if (middle != null) {
            graphics.drawString(font, middle, x + 4, y + 14, 0xFFFFFF);
            graphics.drawString(font, detail, x + 4, y + 24, 0xFFFFFF);
            return;
        }

        // 15 segments, 5 wide with a 1-pixel gap; lit ones go from yellow-green to green.
        int barX = x + 4;
        int barY = y + 14;
        for (int i = 0; i < 15; i++) {
            int segX = barX + i * 6;
            int color = i < current.strength() ? (i < 5 ? 0xFFB8E04A : i < 10 ? 0xFF7CE04A : 0xFF3CF060) : 0xFF303030;
            graphics.fill(segX, barY, segX + 5, barY + 7, color);
        }
        Component strength = current.strength() > 0
                ? Component.literal(Integer.toString(current.strength())).withStyle(ChatFormatting.GREEN)
                : current.jammed() > 0 ? Component.translatable(KEY + "jammed_short").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable(KEY + "off").withStyle(ChatFormatting.GRAY);
        graphics.drawString(font, strength, barX + 15 * 6 + 4, barY, 0xFFFFFF);
        graphics.drawString(font, detail, x + 4, y + 24, 0xFFFFFF);
        if (loss != null) {
            graphics.drawString(font, loss, x + 4, y + 34, 0xFFFFFF);
        }
    }
}
