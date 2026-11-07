package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.network.LogRequestPayload;
import com.reiko.signaltower.network.LogResultPayload;
import com.reiko.signaltower.radio.FrequencyAudit;
import com.reiko.signaltower.radio.FrequencyLog;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The frequency log (v0.35.0), opened with Log in the audit of one of your private frequencies
 * (an operator: anyone's): its last turn-ons, newest first, with when (in-game day and time), what
 * did it (a transmitter, a remote pulse or toggle, or one carried over from another frequency by
 * a converter), who, and where (for a transmitter that isn't another player's). Updates every
 * second. Clicking a row with a position puts a beam over it, as in the audit.
 */
public class LogScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".log.";
    private static final String AUDIT_KEY = "screen." + SignalTower.MOD_ID + ".audit.";
    private static final int WIDTH = 380;
    private static final int ROWS = 9;
    private static final int ROW_HEIGHT = 14;
    private static final int WHAT_X = 92;
    private static final int WHO_X = 210;
    private static final int WHERE_X = 286;
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private final Screen parent;
    private final int frequency;
    private List<FrequencyLog.Entry> entries = List.of();
    private long serverTime;
    private boolean answered;
    private boolean allowed = true;
    private int requestTimer;
    private int scroll;
    private int left;
    private int top;
    private int listTop;
    private @Nullable Component notice;
    private int noticeTicks;

    public LogScreen(Screen parent, int frequency) {
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
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 104, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(AUDIT_KEY + "close"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
        }).tooltip(Tooltip.create(Component.translatable(AUDIT_KEY + "close_tooltip")))
                .bounds(width / 2 + 4, buttonsY, 100, 20).build());
        requestTimer = 0;
    }

    @Override
    public void tick() {
        super.tick();
        serverTime++;
        if (noticeTicks > 0 && --noticeTicks == 0) {
            notice = null;
        }
        if (--requestTimer <= 0) {
            requestTimer = 20;
            PacketDistributor.sendToServer(new LogRequestPayload(frequency));
        }
    }

    /** An answer arrived. */
    public void onResult(LogResultPayload result) {
        if (result.frequency() != frequency) {
            return;
        }
        answered = true;
        allowed = result.allowed();
        entries = result.entries();
        serverTime = result.gameTime();
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
            FrequencyLog.Entry entry = entries.get(scroll + row);
            BlockPos pos = entry.pos().get();
            boolean on = AuditBeams.toggle(entry.dimension(), pos);
            Component what = Component.translatable(KEY + "kind_transmitter");
            notice = on
                    ? (sameDimension(entry)
                    ? Component.translatable(AUDIT_KEY + "beam_on", what, pos.getX(), pos.getY(), pos.getZ())
                    : Component.translatable(AUDIT_KEY + "beam_on_dimension", what, pos.getX(), pos.getY(), pos.getZ(),
                    dimensionName(entry.dimension()))).withStyle(ChatFormatting.GOLD)
                    : Component.translatable(AUDIT_KEY + "beam_off", what, pos.getX(), pos.getY(), pos.getZ())
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

    private static Component when(FrequencyLog.Entry entry) {
        Object[] dayAndTime = PagerItem.dayAndTime(entry.dayTime());
        return Component.translatable(KEY + "when", dayAndTime[0], dayAndTime[1]);
    }

    private static Component what(FrequencyLog.Entry entry) {
        Component kind = Component.translatable(KEY + "kind_" + entry.kind().name().toLowerCase(java.util.Locale.ROOT));
        return entry.from() != 0 ? Component.translatable(KEY + "kind_from", kind, entry.from()).withStyle(ChatFormatting.AQUA) : kind;
    }

    private static Component who(FrequencyLog.Entry entry) {
        if (entry.kind() == SignalNetwork.CauseKind.UNKNOWN) {
            return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
        }
        return switch (entry.whoKind()) {
            case OWNER -> Component.literal(entry.who()).withStyle(ChatFormatting.GOLD);
            case MEMBER -> Component.literal(entry.who()).withStyle(ChatFormatting.YELLOW);
            case UNKNOWN -> Component.translatable(AUDIT_KEY + "no_tuner").withStyle(ChatFormatting.LIGHT_PURPLE);
            case OTHER -> Component.literal(entry.who()).withStyle(ChatFormatting.RED);
        };
    }

    private Component where(FrequencyLog.Entry entry) {
        if (entry.pos().isEmpty()) {
            // Another player's transmitter isn't located, and where a remote was pressed isn't kept.
            return (entry.kind() == SignalNetwork.CauseKind.TRANSMITTER
                    ? Component.translatable(AUDIT_KEY + "hidden") : Component.literal("-")).withStyle(ChatFormatting.DARK_GRAY);
        }
        BlockPos pos = entry.pos().get();
        String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        return sameDimension(entry) ? Component.literal(coords).withStyle(ChatFormatting.WHITE)
                : Component.translatable(AUDIT_KEY + "other_dimension", dimensionName(entry.dimension()), coords).withStyle(ChatFormatting.GRAY);
    }

    private static Component dimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> Component.translatable(AUDIT_KEY + "dim_overworld");
            case "minecraft:the_nether" -> Component.translatable(AUDIT_KEY + "dim_nether");
            case "minecraft:the_end" -> Component.translatable(AUDIT_KEY + "dim_end");
            default -> Component.literal(dimension);
        };
    }

    private boolean sameDimension(FrequencyLog.Entry entry) {
        return minecraft != null && minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(entry.dimension());
    }

    /** "just now", "40 seconds ago", "3 minutes ago", "2 hours ago", "5 days ago" (while the world ran). */
    private Component ago(long gameTime) {
        long seconds = Math.max(0, (serverTime - gameTime) / 20);
        if (seconds < 5) {
            return Component.translatable(KEY + "ago_now");
        }
        if (seconds < 120) {
            return Component.translatable(KEY + "ago_seconds", seconds);
        }
        long minutes = seconds / 60;
        if (minutes < 120) {
            return Component.translatable(KEY + "ago_minutes", minutes);
        }
        long hours = minutes / 60;
        return hours < 48 ? Component.translatable(KEY + "ago_hours", hours) : Component.translatable(KEY + "ago_days", hours / 24);
    }

    private List<FormattedCharSequence> tooltip(FrequencyLog.Entry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "kind_" + entry.kind().name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.WHITE));
        Object[] dayAndTime = PagerItem.dayAndTime(entry.dayTime());
        lines.add(Component.translatable(KEY + "tip_when", dayAndTime[0], dayAndTime[1], ago(entry.gameTime())).withStyle(ChatFormatting.GRAY));
        if (entry.from() != 0) {
            lines.add(Component.translatable(KEY + "tip_from", FrequencyNames.label(null, entry.from())).withStyle(ChatFormatting.AQUA));
        }
        switch (entry.kind()) {
            case UNKNOWN -> lines.add(Component.translatable(KEY + "tip_unknown").withStyle(ChatFormatting.DARK_GRAY));
            case TRANSMITTER -> {
                lines.add(switch (entry.whoKind()) {
                    case OWNER -> Component.translatable(AUDIT_KEY + "tip_owner", entry.who()).withStyle(ChatFormatting.GOLD);
                    case MEMBER -> Component.translatable(AUDIT_KEY + "tip_member", entry.who()).withStyle(ChatFormatting.YELLOW);
                    case UNKNOWN -> Component.translatable(KEY + "tip_no_tuner").withStyle(ChatFormatting.LIGHT_PURPLE);
                    case OTHER -> Component.translatable(KEY + "tip_other_transmitter", entry.who()).withStyle(ChatFormatting.RED);
                });
                entry.pos().ifPresent(pos -> lines.add(place(entry, pos)));
            }
            case REMOTE_PULSE, REMOTE_TOGGLE -> {
                lines.add((entry.whoKind() == FrequencyAudit.Who.OTHER
                        ? Component.translatable(KEY + "tip_other_remote", entry.who()).withStyle(ChatFormatting.RED)
                        : Component.translatable(KEY + "tip_remote", entry.who()).withStyle(ChatFormatting.YELLOW)));
                lines.add(Component.translatable(KEY + "tip_remote_where").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (entry.pos().isPresent()) {
            int secondsLeft = AuditBeams.secondsLeft(entry.dimension(), entry.pos().get());
            lines.add(secondsLeft >= 0
                    ? Component.translatable(AUDIT_KEY + "tip_beam_on", secondsLeft).withStyle(ChatFormatting.GOLD)
                    : Component.translatable(AUDIT_KEY + "tip_beam").withStyle(ChatFormatting.DARK_AQUA));
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, 240));
        }
        return wrapped;
    }

    private Component place(FrequencyLog.Entry entry, BlockPos pos) {
        if (sameDimension(entry) && minecraft != null && minecraft.player != null) {
            long dx = pos.getX() - minecraft.player.getBlockX();
            long dz = pos.getZ() - minecraft.player.getBlockZ();
            int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            int direction = SignalNetwork.compassDirection(dx, dz);
            return (direction < 0
                    ? Component.translatable(AUDIT_KEY + "tip_here", pos.getX(), pos.getY(), pos.getZ())
                    : Component.translatable(AUDIT_KEY + "tip_where", pos.getX(), pos.getY(), pos.getZ(), distance,
                    Component.translatable("screen." + SignalTower.MOD_ID + ".locator.dir_" + DIRECTIONS[direction])))
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable(AUDIT_KEY + "tip_where_dimension", pos.getX(), pos.getY(), pos.getZ(),
                dimensionName(entry.dimension())).withStyle(ChatFormatting.GRAY);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY), centerX, top + 12, 0xFFFFFF);

        int headingY = listTop - 11;
        graphics.drawString(font, Component.translatable(KEY + "heading_when"), left + 4, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_what"), left + WHAT_X, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_who"), left + WHO_X, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_where"), left + WHERE_X, headingY, 0xC0C0C0);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS; i++) {
            int rowTop = listTop + i * ROW_HEIGHT;
            int background = i == hovered ? 0x40FFFFFF : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + WIDTH, rowTop + ROW_HEIGHT - 1, background);
            if (scroll + i >= entries.size()) {
                continue;
            }
            FrequencyLog.Entry entry = entries.get(scroll + i);
            if (entry.pos().isPresent() && AuditBeams.isOn(entry.dimension(), entry.pos().get())) {
                graphics.fill(left, rowTop, left + 2, rowTop + ROW_HEIGHT - 1, 0xFFFFC21A);
            }
            int textY = rowTop + 3;
            drawFitted(graphics, when(entry), left + 4, textY, WHAT_X - 8);
            drawFitted(graphics, what(entry), left + WHAT_X, textY, WHO_X - WHAT_X - 6);
            drawFitted(graphics, who(entry), left + WHO_X, textY, WHERE_X - WHO_X - 6);
            drawFitted(graphics, where(entry), left + WHERE_X, textY, WIDTH - WHERE_X - 4);
        }
        if (entries.size() > ROWS) {
            int barHeight = Math.max(8, ROWS * ROW_HEIGHT * ROWS / entries.size());
            int barTop = listTop + (ROWS * ROW_HEIGHT - barHeight) * scroll / (entries.size() - ROWS);
            graphics.fill(left + WIDTH - 2, barTop, left + WIDTH, barTop + barHeight, 0xC0A0A0A0);
        }

        int below = listTop + ROWS * ROW_HEIGHT + 3;
        if (!answered) {
            graphics.drawCenteredString(font, Component.translatable(AUDIT_KEY + "loading").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
        } else if (!allowed) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "not_allowed").withStyle(ChatFormatting.RED),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
        } else if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(font, Component.translatable(entries.size() == 1 ? KEY + "summary_one" : KEY + "summary",
                    entries.size(), ago(entries.get(0).gameTime())), centerX, below, 0xFFFFFF);
        }
        graphics.drawCenteredString(font, notice != null ? notice
                : Component.translatable(KEY + "kept", SignalNetwork.MAX_HISTORY).withStyle(ChatFormatting.DARK_GRAY), centerX, below + 11, 0xFFFFFF);

        if (hovered >= 0) {
            graphics.renderTooltip(font, tooltip(entries.get(scroll + hovered)), mouseX, mouseY);
        }
    }
}
