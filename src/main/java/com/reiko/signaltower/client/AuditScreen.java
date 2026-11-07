package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.AuditRequestPayload;
import com.reiko.signaltower.network.AuditResultPayload;
import com.reiko.signaltower.radio.FrequencyAudit;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The frequency audit, opened by sneak-clicking one of your private frequencies in the frequency
 * directory: every transmitter, repeater, converter and (loaded) receiver on it, in every
 * dimension, with who tuned it and whether it works, is silent or is deaf. Your own, your
 * friends' and old (no recorded tuner) blocks show where they are; other players' are only
 * counted. Updates every second.
 * <p>
 * Clicking a row that shows a position puts a gold beam of light over that block for a minute
 * ({@link AuditBeams}, v0.33.0); clicking it again turns it off. Rows with a beam get a gold edge.
 */
public class AuditScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".audit.";
    private static final int WIDTH = 380;
    private static final int ROWS = 9;
    private static final int ROW_HEIGHT = 14;
    private static final int WHERE_X = 122;
    private static final int TUNER_X = 250;
    private static final int STATUS_X = 324;
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private final Screen parent;
    private final int frequency;
    private List<FrequencyAudit.Entry> entries = List.of();
    private boolean answered;
    private boolean allowed = true;
    private int requestTimer;
    private int scroll;
    private int left;
    private int top;
    private int listTop;
    /** What the last click did, shown under the list for a few seconds. */
    private @Nullable Component notice;
    private int noticeTicks;

    public AuditScreen(Screen parent, int frequency) {
        super(Component.translatable(KEY + "title", FrequencyNames.label(null, frequency)));
        this.parent = parent;
        this.frequency = frequency;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = Math.max(4, height / 2 - 96);
        listTop = top + 38;
        int buttonsY = listTop + ROWS * ROW_HEIGHT + 28;
        // The owner also gets Hand over (v0.34.0); an operator auditing someone else's doesn't.
        FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(frequency);
        boolean owner = claim != null && minecraft != null && minecraft.player != null
                && claim.owner().equals(minecraft.player.getUUID());
        // Back, Close and Log (v0.35.0); the owner also gets Guests (v0.36.0) and Hand over.
        int buttonWidth = owner ? 72 : 100;
        int step = buttonWidth + 4;
        int x = width / 2 - ((owner ? 5 : 3) * step - 4) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(x, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "close"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
        }).tooltip(Tooltip.create(Component.translatable(KEY + "close_tooltip")))
                .bounds(x + step, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "log"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(new LogScreen(this, frequency));
            }
        }).tooltip(Tooltip.create(Component.translatable(KEY + "log_tooltip")))
                .bounds(x + 2 * step, buttonsY, buttonWidth, 20).build());
        if (owner) {
            addRenderableWidget(Button.builder(Component.translatable(KEY + "guests"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new GuestScreen(this, frequency));
                }
            }).tooltip(Tooltip.create(Component.translatable(KEY + "guests_tooltip")))
                    .bounds(x + 3 * step, buttonsY, buttonWidth, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(KEY + "hand_over"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new HandOverScreen(this, parent, frequency));
                }
            }).tooltip(Tooltip.create(Component.translatable(KEY + "hand_over_tooltip")))
                    .bounds(x + 4 * step, buttonsY, buttonWidth, 20).build());
        }
        requestTimer = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (noticeTicks > 0 && --noticeTicks == 0) {
            notice = null;
        }
        if (--requestTimer <= 0) {
            requestTimer = 20;
            PacketDistributor.sendToServer(new AuditRequestPayload(frequency));
        }
    }

    /** An answer arrived. */
    public void onResult(AuditResultPayload result) {
        if (result.frequency() != frequency) {
            return;
        }
        answered = true;
        allowed = result.allowed();
        entries = result.entries();
        scroll = Math.max(0, Math.min(scroll, entries.size() - ROWS));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop) {
            return -1;
        }
        int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        return row < ROWS && scroll + row < entries.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0 && entries.get(scroll + row).pos().isPresent()) {
            FrequencyAudit.Entry entry = entries.get(scroll + row);
            BlockPos pos = entry.pos().get();
            boolean on = AuditBeams.toggle(entry.dimension(), pos);
            notice = on
                    ? (sameDimension(entry)
                    ? Component.translatable(KEY + "beam_on", kind(entry), pos.getX(), pos.getY(), pos.getZ())
                    : Component.translatable(KEY + "beam_on_dimension", kind(entry), pos.getX(), pos.getY(), pos.getZ(),
                    dimensionName(entry.dimension()))).withStyle(ChatFormatting.GOLD)
                    : Component.translatable(KEY + "beam_off", kind(entry), pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY);
            noticeTicks = 100;
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, on ? 1.2F : 0.8F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (entries.size() > ROWS && scrollY != 0) {
            scroll = Math.max(0, Math.min(entries.size() - ROWS, scroll - (int) Math.signum(scrollY) * (Screen.hasShiftDown() ? ROWS : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Cells ----

    private static Component kind(FrequencyAudit.Entry entry) {
        MutableComponent name = Component.translatable(KEY + "kind_" + entry.kind().name().toLowerCase(java.util.Locale.ROOT));
        return entry.count() > 1 ? Component.translatable(KEY + "kind_count", name, entry.count()) : name;
    }

    /** "Overworld", "Nether", "End", or the dimension's id. */
    private static Component dimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> Component.translatable(KEY + "dim_overworld");
            case "minecraft:the_nether" -> Component.translatable(KEY + "dim_nether");
            case "minecraft:the_end" -> Component.translatable(KEY + "dim_end");
            default -> Component.literal(dimension);
        };
    }

    private boolean sameDimension(FrequencyAudit.Entry entry) {
        return minecraft != null && minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(entry.dimension());
    }

    private Component where(FrequencyAudit.Entry entry) {
        if (entry.pos().isEmpty()) {
            return Component.translatable(KEY + "hidden").withStyle(ChatFormatting.DARK_GRAY);
        }
        BlockPos pos = entry.pos().get();
        Component coords = Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        return sameDimension(entry) ? coords.copy().withStyle(ChatFormatting.WHITE)
                : Component.translatable(KEY + "other_dimension", dimensionName(entry.dimension()), coords).withStyle(ChatFormatting.GRAY);
    }

    private static Component tuner(FrequencyAudit.Entry entry) {
        return switch (entry.who()) {
            case OWNER -> Component.literal(entry.tuner()).withStyle(ChatFormatting.GOLD);
            case MEMBER -> Component.literal(entry.tuner()).withStyle(ChatFormatting.YELLOW);
            case UNKNOWN -> Component.translatable(KEY + "no_tuner").withStyle(ChatFormatting.LIGHT_PURPLE);
            case OTHER -> Component.literal(entry.tuner()).withStyle(ChatFormatting.RED);
        };
    }

    private static Component status(FrequencyAudit.Entry entry) {
        return switch (entry.status()) {
            case SILENT -> Component.translatable(KEY + "silent").withStyle(ChatFormatting.RED);
            case DEAF -> Component.translatable(KEY + "deaf").withStyle(ChatFormatting.RED);
            case WORKING -> entry.power() > 0
                    ? Component.translatable(KEY + "on", entry.power()).withStyle(ChatFormatting.GREEN)
                    : entry.power() == 0 ? Component.translatable(KEY + "off").withStyle(ChatFormatting.GRAY)
                    : Component.translatable(KEY + "working").withStyle(ChatFormatting.GREEN);
        };
    }

    /** The hover text: the block, where it is (and how far), who tuned it, and what its status means. */
    private List<FormattedCharSequence> tooltip(FrequencyAudit.Entry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(kind(entry).copy().withStyle(ChatFormatting.WHITE));
        if (entry.pos().isPresent()) {
            BlockPos pos = entry.pos().get();
            if (sameDimension(entry) && minecraft != null && minecraft.player != null) {
                long dx = pos.getX() - minecraft.player.getBlockX();
                long dz = pos.getZ() - minecraft.player.getBlockZ();
                int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                int direction = SignalNetwork.compassDirection(dx, dz);
                lines.add(direction < 0
                        ? Component.translatable(KEY + "tip_here", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY)
                        : Component.translatable(KEY + "tip_where", pos.getX(), pos.getY(), pos.getZ(), distance,
                                Component.translatable("screen." + SignalTower.MOD_ID + ".locator.dir_" + DIRECTIONS[direction]))
                                .withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable(KEY + "tip_where_dimension", pos.getX(), pos.getY(), pos.getZ(),
                        dimensionName(entry.dimension())).withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(switch (entry.who()) {
            case OWNER -> Component.translatable(KEY + "tip_owner", entry.tuner()).withStyle(ChatFormatting.GOLD);
            case MEMBER -> Component.translatable(KEY + "tip_member", entry.tuner()).withStyle(ChatFormatting.YELLOW);
            case UNKNOWN -> Component.translatable(KEY + "tip_unknown").withStyle(ChatFormatting.LIGHT_PURPLE);
            case OTHER -> Component.translatable(entry.count() > 1 ? KEY + "tip_other_many" : KEY + "tip_other", entry.tuner())
                    .withStyle(ChatFormatting.RED);
        });
        boolean sends = entry.kind() == FrequencyAudit.Kind.TRANSMITTER || entry.kind() == FrequencyAudit.Kind.REPEATER
                || entry.kind() == FrequencyAudit.Kind.CONVERTER_SENDS || entry.kind() == FrequencyAudit.Kind.COMPACT_TRANSMITTER;
        lines.add(switch (entry.status()) {
            case SILENT -> Component.translatable(KEY + "tip_silent").withStyle(ChatFormatting.RED);
            case DEAF -> Component.translatable(KEY + "tip_deaf").withStyle(ChatFormatting.RED);
            case WORKING -> Component.translatable(KEY + (sends ? "tip_sends" : "tip_hears")).withStyle(ChatFormatting.GREEN);
        });
        if (entry.pos().isPresent()) {
            int secondsLeft = AuditBeams.secondsLeft(entry.dimension(), entry.pos().get());
            lines.add(secondsLeft >= 0
                    ? Component.translatable(KEY + "tip_beam_on", secondsLeft).withStyle(ChatFormatting.GOLD)
                    : sameDimension(entry)
                    ? Component.translatable(KEY + "tip_beam").withStyle(ChatFormatting.DARK_AQUA)
                    : Component.translatable(KEY + "tip_beam_dimension", dimensionName(entry.dimension())).withStyle(ChatFormatting.DARK_AQUA));
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, 240));
        }
        return wrapped;
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, 0xFFFFFF);
            return;
        }
        FormattedText cut = font.substrByWidth(text, maxWidth - font.width("..."));
        graphics.drawString(font, Language.getInstance().getVisualOrder(FormattedText.composite(cut, Component.literal("..."))),
                x, y, 0xFFFFFF);
    }

    /** "7 blocks: 1 silent, 1 deaf, 2 with no recorded tuner" (only the parts that apply). */
    private Component summary() {
        int blocks = 0;
        int silent = 0;
        int deaf = 0;
        int unknown = 0;
        for (FrequencyAudit.Entry entry : entries) {
            blocks += entry.count();
            switch (entry.status()) {
                case SILENT -> silent += entry.count();
                case DEAF -> deaf += entry.count();
                default -> {
                }
            }
            if (entry.who() == FrequencyAudit.Who.UNKNOWN) {
                unknown += entry.count();
            }
        }
        MutableComponent line = Component.translatable(blocks == 1 ? KEY + "summary_one" : KEY + "summary", blocks);
        List<Component> parts = new ArrayList<>();
        if (silent > 0) {
            parts.add(Component.translatable(KEY + "summary_silent", silent).withStyle(ChatFormatting.RED));
        }
        if (deaf > 0) {
            parts.add(Component.translatable(KEY + "summary_deaf", deaf).withStyle(ChatFormatting.RED));
        }
        if (unknown > 0) {
            parts.add(Component.translatable(KEY + "summary_unknown", unknown).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (!parts.isEmpty()) {
            line.append(": ");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append(parts.get(i));
            }
        }
        return line;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY), centerX, top + 12, 0xFFFFFF);

        int headingY = listTop - 11;
        graphics.drawString(font, Component.translatable(KEY + "heading_block"), left + 4, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_where"), left + WHERE_X, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_tuner"), left + TUNER_X, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_status"), left + STATUS_X, headingY, 0xC0C0C0);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS; i++) {
            int rowTop = listTop + i * ROW_HEIGHT;
            int background = i == hovered ? 0x40FFFFFF : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + WIDTH, rowTop + ROW_HEIGHT - 1, background);
            if (scroll + i >= entries.size()) {
                continue;
            }
            FrequencyAudit.Entry entry = entries.get(scroll + i);
            if (entry.pos().isPresent() && AuditBeams.isOn(entry.dimension(), entry.pos().get())) {
                graphics.fill(left, rowTop, left + 2, rowTop + ROW_HEIGHT - 1, 0xFFFFC21A);
            }
            int textY = rowTop + 3;
            drawFitted(graphics, kind(entry), left + 4, textY, WHERE_X - 8);
            drawFitted(graphics, where(entry), left + WHERE_X, textY, TUNER_X - WHERE_X - 6);
            drawFitted(graphics, tuner(entry), left + TUNER_X, textY, STATUS_X - TUNER_X - 6);
            drawFitted(graphics, status(entry), left + STATUS_X, textY, WIDTH - STATUS_X - 4);
        }
        if (entries.size() > ROWS) {
            int barHeight = Math.max(8, ROWS * ROW_HEIGHT * ROWS / entries.size());
            int travel = ROWS * ROW_HEIGHT - barHeight;
            int barTop = listTop + travel * scroll / (entries.size() - ROWS);
            graphics.fill(left + WIDTH - 2, barTop, left + WIDTH, barTop + barHeight, 0xC0A0A0A0);
        }

        int below = listTop + ROWS * ROW_HEIGHT + 3;
        if (!answered) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "loading").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
        } else if (!allowed) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(frequency);
            graphics.drawCenteredString(font, (claim == null
                    ? Component.translatable(KEY + "not_private")
                    : Component.translatable(KEY + "not_yours", claim.ownerName())).withStyle(ChatFormatting.RED),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
        } else if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(font, summary(), centerX, below, 0xFFFFFF);
        }
        graphics.drawCenteredString(font, notice != null ? notice
                : Component.translatable(KEY + "receivers_loaded").withStyle(ChatFormatting.DARK_GRAY), centerX, below + 11, 0xFFFFFF);

        if (hovered >= 0) {
            graphics.renderTooltip(font, tooltip(entries.get(scroll + hovered)), mouseX, mouseY);
        }
    }
}
