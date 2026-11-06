package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.RemoteButtons;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.item.ScannerTarget;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.network.ConfigureMultiRemotePayload;
import com.reiko.signaltower.network.ConfigureRadioItemPayload;
import com.reiko.signaltower.network.ScannerRequestPayload;
import com.reiko.signaltower.network.ScannerResultPayload;
import com.reiko.signaltower.network.TrackTransmitterPayload;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The Signal Scanner's screen: a live list of what a receiver where the player stands would hear,
 * refreshed about once a second. Scroll for more rows. Each row's Tune button sets that frequency
 * on the radio item in the player's other hand (for a Multi-Button Remote, its selected button).
 * Clicking a row with a transmitter in it tracks that transmitter with an arrow on screen; clicking
 * the tracked row again stops.
 */
public class ScannerScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".scanner.";
    private static final String LOCATOR = "screen." + SignalTower.MOD_ID + ".locator.";
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 20;
    private static final int WIDTH = 388;
    private static final int SOURCE_WIDTH = 214;

    private final InteractionHand hand;
    private final Button[] tuneButtons = new Button[ROWS];
    private List<ScannerResultPayload.Entry> entries = List.of();
    private boolean scanned;
    private int scroll;
    private int requestTimer;
    private @Nullable Component tunedMessage;
    private int left;
    private int top;

    public ScannerScreen(InteractionHand hand, Component title) {
        super(title);
        this.hand = hand;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = height / 2 - 64;
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            tuneButtons[i] = addRenderableWidget(Button.builder(Component.translatable(KEY + "tune"), b -> tune(row))
                    .bounds(left + WIDTH - 44, top + i * ROW_HEIGHT + 1, 44, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, top + ROWS * ROW_HEIGHT + 24, 100, 20).build());
        refreshButtons();
    }

    /** The scanner itself, in the hand it was used from. */
    private ItemStack scanner() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getItemInHand(hand) : ItemStack.EMPTY;
    }

    /** The frequency being tracked, or -1. */
    private int trackedFrequency() {
        ScannerTarget target = SignalScannerItem.getTarget(scanner());
        return target == null ? -1 : target.frequency();
    }

    /** The row (0 to ROWS-1) under the mouse, left of the Tune buttons, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + WIDTH - 48 || mouseY < top) {
            return -1;
        }
        int row = (int) ((mouseY - top) / ROW_HEIGHT);
        return row < ROWS && scroll + row < entries.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0) {
            ScannerResultPayload.Entry entry = entries.get(scroll + row);
            if (entry.transmitters() > 0) {
                boolean stop = entry.frequency() == trackedFrequency();
                PacketDistributor.sendToServer(new TrackTransmitterPayload(hand == InteractionHand.OFF_HAND, entry.frequency(), stop));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                tunedMessage = null;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The other hand's item: what the Tune buttons set. */
    private ItemStack otherHand() {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return minecraft != null && minecraft.player != null ? minecraft.player.getItemInHand(other) : ItemStack.EMPTY;
    }

    private void refreshButtons() {
        ItemStack other = otherHand();
        boolean canTune = SignalScannerItem.canTune(other);
        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            tuneButtons[i].visible = index < entries.size();
            tuneButtons[i].active = canTune;
            tuneButtons[i].setTooltip(Tooltip.create(index < entries.size() && canTune
                    ? Component.translatable(KEY + "tune_tooltip", other.getHoverName(), format(entries.get(index).frequency()))
                    : Component.translatable(KEY + "tune_hint")));
        }
    }

    private void tune(int row) {
        int index = scroll + row;
        ItemStack other = otherHand();
        if (index >= entries.size() || !SignalScannerItem.canTune(other)) {
            return;
        }
        int frequency = entries.get(index).frequency();
        boolean offhand = hand == InteractionHand.MAIN_HAND; // the other hand
        if (other.getItem() instanceof MultiRemoteItem) {
            RemoteButtons buttons = SignalScannerItem.retuneSelected(MultiRemoteItem.getButtons(other), frequency);
            PacketDistributor.sendToServer(new ConfigureMultiRemotePayload(offhand, buttons, -1));
        } else {
            // A remote keeps its mode and pulse length; the tuner and pager ignore them.
            PacketDistributor.sendToServer(new ConfigureRadioItemPayload(offhand, frequency,
                    RemoteControlItem.isToggleMode(other), RemoteControlItem.getPulseTicks(other)));
        }
        tunedMessage = Component.translatable(KEY + "tuned", other.getHoverName(), format(frequency)).withStyle(ChatFormatting.GREEN);
    }

    /** A scan answer arrived. */
    public void onResult(ScannerResultPayload result) {
        entries = result.entries();
        scanned = true;
        scroll = Math.max(0, Math.min(scroll, entries.size() - ROWS));
        refreshButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (--requestTimer <= 0) {
            requestTimer = 20;
            PacketDistributor.sendToServer(ScannerRequestPayload.INSTANCE);
        }
        refreshButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (entries.size() > ROWS && scrollY != 0) {
            scroll = Math.max(0, Math.min(entries.size() - ROWS, scroll - (int) Math.signum(scrollY)));
            refreshButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String format(int frequency) {
        return String.format(Locale.ROOT, "%,d", frequency);
    }

    private static Component source(ScannerResultPayload.Entry entry) {
        return switch (entry.sourceType()) {
            case REMOTE_PULSE, REMOTE_TOGGLE -> {
                Component remote = Component.translatable(KEY + (entry.sourceType() == SignalNetwork.Source.REMOTE_PULSE
                        ? "remote_pulse" : "remote_toggle"));
                yield entry.transmitters() == 0 ? remote
                        : entry.transmitters() == 1 ? Component.translatable(KEY + "plus_one", remote)
                        : Component.translatable(KEY + "plus_many", remote, entry.transmitters());
            }
            case TRANSMITTER, REPEATER -> {
                Component where = entry.distance() <= 0 || entry.direction() < 0
                        ? Component.translatable(LOCATOR + "here")
                        : Component.translatable(LOCATOR + "distance", format(entry.distance()),
                                Component.translatable(LOCATOR + "dir_" + DIRECTIONS[entry.direction() & 7]));
                String kind = entry.sourceType() == SignalNetwork.Source.REPEATER ? "repeater" : "transmitter";
                yield entry.transmitters() <= 1 ? Component.translatable(KEY + kind, where)
                        : Component.translatable(KEY + kind + "s", where, entry.transmitters() - 1);
            }
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top - 38, 0xFFFFFF);
        Component summary = !scanned ? Component.translatable(KEY + "scanning").withStyle(ChatFormatting.DARK_GRAY)
                : entries.isEmpty() ? Component.translatable(KEY + "nothing").withStyle(ChatFormatting.GRAY)
                : entries.size() == 1 ? Component.translatable(KEY + "heard_one").withStyle(ChatFormatting.GRAY)
                : Component.translatable(KEY + "heard_many", entries.size()).withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, summary, centerX, top - 26, 0xFFFFFF);

        int headingY = top - 11;
        graphics.drawString(font, Component.translatable(KEY + "heading_frequency"), left + 2, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_signal"), left + 80, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_source"), left + 124, headingY, 0xC0C0C0);

        int tracked = trackedFrequency();
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && scroll + i < entries.size(); i++) {
            ScannerResultPayload.Entry entry = entries.get(scroll + i);
            int rowTop = top + i * ROW_HEIGHT;
            int background = entry.frequency() == tracked ? 0x6030A030
                    : i == hovered && entry.transmitters() > 0 ? 0x40FFFFFF
                    : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + WIDTH - 48, rowTop + ROW_HEIGHT - 1, background);
            int textY = rowTop + 6;
            graphics.drawString(font, (entry.frequency() == tracked ? "» " : "") + format(entry.frequency()), left + 4, textY,
                    entry.frequency() == tracked ? 0x80FF80 : 0x55FFFF);
            Component power = entry.power() > 0
                    ? Component.translatable(KEY + "on", entry.power()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "off").withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(font, power, left + 80, textY, 0xFFFFFF);
            FormattedText fitted = font.substrByWidth(source(entry), SOURCE_WIDTH);
            graphics.drawString(font, Language.getInstance().getVisualOrder(fitted), left + 124, textY, 0xFFFFFF);
        }

        int below = top + ROWS * ROW_HEIGHT;
        if (entries.size() > ROWS) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "more", scroll + 1, scroll + ROWS, entries.size())
                    .withStyle(ChatFormatting.DARK_GRAY), centerX, below + 2, 0xFFFFFF);
        }
        Component status = tunedMessage != null ? tunedMessage
                : tracked > 0 ? Component.translatable(KEY + "tracking", format(tracked)).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "track_hint").withStyle(ChatFormatting.GRAY); // the Tune buttons explain themselves
        graphics.drawCenteredString(font, status, centerX, below + 12, 0xFFFFFF);

        if (hovered >= 0) {
            ScannerResultPayload.Entry entry = entries.get(scroll + hovered);
            String tip = entry.transmitters() == 0 ? "row_remote" : entry.frequency() == tracked ? "row_stop" : "row_track";
            graphics.renderTooltip(font, Component.translatable(KEY + tip), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
