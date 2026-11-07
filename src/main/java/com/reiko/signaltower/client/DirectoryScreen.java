package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.DirectoryRequestPayload;
import com.reiko.signaltower.network.DirectoryResultPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntConsumer;

/**
 * The frequency directory: every named and private frequency in the world, opened from the
 * tuner's, remote's and pager's screens. Each row shows the frequency, its name, who can send on
 * it and whether it's on right now; clicking one hands it back to the screen it came from. A
 * search box and a filter button narrow the list.
 * <p>
 * Names and owners come from the client's synced copies. Whether a frequency is on comes from the
 * server once a second ({@link DirectoryRequestPayload}); for someone else's private frequency the
 * server only says whether it's heard where the player stands.
 */
public class DirectoryScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".directory.";
    private static final int WIDTH = 380;
    private static final int ROWS = 9;
    private static final int ROW_HEIGHT = 14;
    private static final int NAME_X = 72;
    private static final int OWNER_X = 196;
    private static final int NOW_X = 300;

    /** What the filter button shows. */
    public enum Filter {
        ALL, NAMED, PRIVATE, MINE, ON;

        Filter step(boolean back) {
            Filter[] all = values();
            return all[(ordinal() + (back ? all.length - 1 : 1)) % all.length];
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One frequency in the list. */
    private record Row(int frequency, @Nullable String name, @Nullable String note, FrequencyClaims.@Nullable ClaimInfo claim) {}

    private final Screen parent;
    private final int current;
    private final IntConsumer onPick;
    /** The converter whose screen it was opened from, or null (opened from an item's screen). */
    private final @Nullable BlockPos converter;
    private EditBox search;
    private Button filterButton;
    private Filter filter = Filter.ALL;
    private List<Row> all = List.of();
    private List<Row> shown = List.of();
    private final Map<Integer, DirectoryResultPayload.Entry> status = new HashMap<>();
    private boolean answered;
    private int scroll;
    private int requestTimer;
    private int left;
    private int top;
    private int listTop;

    /**
     * @param current the frequency typed in the screen it was opened from (marked in the list)
     * @param onPick  gets the clicked frequency; the directory then goes back to {@code parent}
     */
    public DirectoryScreen(Screen parent, int current, IntConsumer onPick) {
        this(parent, current, onPick, null);
    }

    /** @param converter the converter whose screen it's opened from (so the server answers without a radio item in hand) */
    public DirectoryScreen(Screen parent, int current, IntConsumer onPick, @Nullable BlockPos converter) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
        this.converter = converter;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = Math.max(4, height / 2 - 107);
        listTop = top + 52;
        String typed = search != null ? search.getValue() : "";
        search = new EditBox(font, left, top + 16, 236, 18, Component.translatable(KEY + "search"));
        search.setMaxLength(32);
        search.setHint(Component.translatable(KEY + "search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(typed);
        search.setResponder(text -> {
            scroll = 0;
            refilter();
        });
        addRenderableWidget(search);
        filterButton = addRenderableWidget(Button.builder(filterLabel(), button -> {
            filter = filter.step(Screen.hasShiftDown());
            button.setMessage(filterLabel());
            scroll = 0;
            refilter();
        }).tooltip(Tooltip.create(Component.translatable(KEY + "filter_tooltip")))
                .bounds(left + WIDTH - 140, top + 15, 140, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 50, listTop + ROWS * ROW_HEIGHT + 16, 100, 20).build());
        setInitialFocus(search);
        reload();
    }

    private Component filterLabel() {
        return Component.translatable(KEY + "filter", Component.translatable(KEY + "filter_" + filter.key()));
    }

    /** Reads the world's names, notes and private frequencies from the client's copies. */
    private void reload() {
        TreeSet<Integer> frequencies = new TreeSet<>(FrequencyNames.clientFrequencies());
        frequencies.addAll(FrequencyNotes.clientFrequencies());
        frequencies.addAll(FrequencyClaims.clientFrequencies());
        List<Row> rows = new ArrayList<>(frequencies.size());
        for (int frequency : frequencies) {
            rows.add(new Row(frequency, FrequencyNames.nameOf(null, frequency), FrequencyNotes.noteOf(null, frequency),
                    FrequencyClaims.clientInfo(frequency)));
        }
        all = rows;
        refilter();
    }

    private void refilter() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        String digits = query.replace(",", "");
        List<Row> rows = new ArrayList<>();
        for (Row row : all) {
            if (matchesFilter(row) && (query.isEmpty()
                    || (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit) && Integer.toString(row.frequency()).contains(digits))
                    || (row.name() != null && row.name().toLowerCase(Locale.ROOT).contains(query))
                    || (row.note() != null && row.note().toLowerCase(Locale.ROOT).contains(query))
                    || (row.claim() != null && row.claim().ownerName().toLowerCase(Locale.ROOT).contains(query)))) {
                rows.add(row);
            }
        }
        shown = rows;
        scroll = Math.max(0, Math.min(scroll, shown.size() - ROWS));
    }

    private boolean matchesFilter(Row row) {
        return switch (filter) {
            case ALL -> true;
            case NAMED -> row.name() != null;
            case PRIVATE -> row.claim() != null;
            case MINE -> row.claim() != null && (mine(row.claim()) || friendOf(row.claim()));
            case ON -> {
                DirectoryResultPayload.Entry entry = status.get(row.frequency());
                yield entry != null && entry.on();
            }
        };
    }

    private boolean mine(FrequencyClaims.ClaimInfo claim) {
        return minecraft != null && minecraft.player != null && claim.owner().equals(minecraft.player.getUUID());
    }

    private boolean friendOf(FrequencyClaims.ClaimInfo claim) {
        return minecraft != null && minecraft.player != null
                && claim.hasMember(minecraft.player.getGameProfile().getName());
    }

    /** A status answer arrived. */
    public void onResult(DirectoryResultPayload result) {
        status.clear();
        for (DirectoryResultPayload.Entry entry : result.entries()) {
            status.put(entry.frequency(), entry);
        }
        answered = true;
        reload(); // names and claims may have changed too
    }

    @Override
    public void tick() {
        super.tick();
        if (--requestTimer <= 0) {
            requestTimer = 20;
            PacketDistributor.sendToServer(new DirectoryRequestPayload(java.util.Optional.ofNullable(converter)));
        }
    }

    /** The row (0 to ROWS-1) under the mouse, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop) {
            return -1;
        }
        int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        return row < ROWS && scroll + row < shown.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0 && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            onPick.accept(shown.get(scroll + row).frequency());
            minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (shown.size() > ROWS && scrollY != 0) {
            scroll = Math.max(0, Math.min(shown.size() - ROWS, scroll - (int) Math.signum(scrollY) * (Screen.hasShiftDown() ? ROWS : 1)));
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

    private static String format(int frequency) {
        return String.format(Locale.ROOT, "%,d", frequency);
    }

    /** The name column: the name, then its note in grey (cut off to fit); "-" with neither. */
    private static Component nameCell(Row row) {
        if (row.name() == null && row.note() == null) {
            return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
        }
        var cell = Component.empty();
        if (row.name() != null) {
            cell.append(Component.literal(row.name()).withStyle(ChatFormatting.AQUA));
        }
        if (row.note() != null) {
            cell.append(Component.literal((row.name() != null ? "  " : "") + row.note())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return cell;
    }

    /** Who can send on it, short enough for its column. */
    private Component owner(Row row) {
        FrequencyClaims.ClaimInfo claim = row.claim();
        if (claim == null) {
            return Component.translatable(KEY + "public").withStyle(ChatFormatting.GRAY);
        }
        if (mine(claim)) {
            return claim.members().isEmpty()
                    ? Component.translatable(KEY + "yours").withStyle(ChatFormatting.GOLD)
                    : Component.translatable(KEY + "yours_friends", claim.members().size()).withStyle(ChatFormatting.GOLD);
        }
        return friendOf(claim)
                ? Component.translatable(KEY + "friend", claim.ownerName()).withStyle(ChatFormatting.GOLD)
                : Component.literal(claim.ownerName()).withStyle(ChatFormatting.RED);
    }

    /** Whether it's on, short enough for its column. */
    private Component now(Row row) {
        DirectoryResultPayload.Entry entry = status.get(row.frequency());
        if (entry == null) {
            return Component.literal(answered ? "-" : "...").withStyle(ChatFormatting.DARK_GRAY);
        }
        if (entry.encrypted()) {
            return Component.translatable(KEY + "encrypted").withStyle(ChatFormatting.DARK_PURPLE);
        }
        if (entry.hidden()) {
            return entry.here() > 0
                    ? Component.translatable(KEY + "here", entry.here()).withStyle(ChatFormatting.GREEN)
                    : Component.literal("?").withStyle(ChatFormatting.DARK_GRAY);
        }
        if (entry.anywhere() > 0) {
            return entry.here() > 0
                    ? Component.translatable(KEY + "on_here", entry.anywhere()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "on", entry.anywhere()).withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable(KEY + "off").withStyle(ChatFormatting.DARK_GRAY);
    }

    /** The hover text for a row: who can send, whether it's on, and what clicking does. */
    private List<FormattedCharSequence> tooltip(Row row) {
        List<Component> lines = new ArrayList<>();
        Object label = row.name() == null ? format(row.frequency()) : FrequencyNames.named(row.frequency(), row.name());
        lines.add(Component.translatable(KEY + "tip_frequency", label));
        if (row.note() != null) {
            lines.add(FrequencyNotes.line(row.note()));
        }
        FrequencyClaims.ClaimInfo claim = row.claim();
        if (claim == null) {
            lines.add(Component.translatable(KEY + "tip_public").withStyle(ChatFormatting.GRAY));
        } else if (mine(claim) || friendOf(claim)) {
            lines.add(Component.translatable(mine(claim) ? KEY + "tip_yours" : KEY + "tip_friend", claim.ownerName())
                    .withStyle(ChatFormatting.GOLD));
            if (!claim.groups().isEmpty()) {
                lines.add(Component.translatable(KEY + "tip_groups", String.join(", ", claim.groups())).withStyle(ChatFormatting.GRAY));
            }
            if (!claim.members().isEmpty()) {
                lines.add(Component.translatable(KEY + "tip_friends", String.join(", ", claim.members())).withStyle(ChatFormatting.GRAY));
            }
        } else {
            lines.add(Component.translatable(KEY + "tip_other", claim.ownerName()).withStyle(ChatFormatting.RED));
        }
        if (claim != null && claim.encrypted()) {
            lines.add(Component.translatable(KEY + (mine(claim) || friendOf(claim) ? "tip_encrypted_member" : "tip_encrypted"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        DirectoryResultPayload.Entry entry = status.get(row.frequency());
        if (entry != null && !entry.encrypted()) {
            String key = entry.hidden()
                    ? (entry.here() > 0 ? "tip_hidden_here" : "tip_hidden")
                    : entry.anywhere() > 0 ? (entry.here() > 0 ? "tip_on_here" : "tip_on") : "tip_off";
            lines.add(Component.translatable(KEY + key, entry.anywhere(), entry.here()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(KEY + "tip_click").withStyle(ChatFormatting.DARK_GRAY));
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, 260));
        }
        return out;
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

        int headingY = listTop - 11;
        graphics.drawString(font, Component.translatable(KEY + "heading_frequency"), left + 4, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_name"), left + NAME_X, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_owner"), left + OWNER_X, headingY, 0xC0C0C0);
        graphics.drawString(font, Component.translatable(KEY + "heading_now"), left + NOW_X, headingY, 0xC0C0C0);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS; i++) {
            int rowTop = listTop + i * ROW_HEIGHT;
            int background = i == hovered ? 0x40FFFFFF : (i & 1) == 0 ? 0x50000000 : 0x30000000;
            graphics.fill(left, rowTop, left + WIDTH, rowTop + ROW_HEIGHT - 1, background);
            if (scroll + i >= shown.size()) {
                continue;
            }
            Row row = shown.get(scroll + i);
            int textY = rowTop + 3;
            boolean isCurrent = row.frequency() == current;
            graphics.drawString(font, (isCurrent ? "» " : "") + format(row.frequency()), left + 4, textY,
                    isCurrent ? 0x80FF80 : 0x55FFFF);
            drawFitted(graphics, nameCell(row), left + NAME_X, textY, OWNER_X - NAME_X - 6);
            drawFitted(graphics, owner(row), left + OWNER_X, textY, NOW_X - OWNER_X - 6);
            drawFitted(graphics, now(row), left + NOW_X, textY, WIDTH - NOW_X - 4);
        }
        if (shown.size() > ROWS) {
            // a thin scroll bar along the right edge
            int barHeight = Math.max(8, ROWS * ROW_HEIGHT * ROWS / shown.size());
            int travel = ROWS * ROW_HEIGHT - barHeight;
            int barTop = listTop + travel * scroll / (shown.size() - ROWS);
            graphics.fill(left + WIDTH - 2, barTop, left + WIDTH, barTop + barHeight, 0xC0A0A0A0);
        }

        int below = listTop + ROWS * ROW_HEIGHT + 3;
        Component footer;
        if (all.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty_hint").withStyle(ChatFormatting.DARK_GRAY),
                    centerX, listTop + 3 * ROW_HEIGHT + 3, 0xFFFFFF);
            footer = Component.empty();
        } else if (shown.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "no_match").withStyle(ChatFormatting.GRAY),
                    centerX, listTop + 2 * ROW_HEIGHT + 3, 0xFFFFFF);
            footer = Component.translatable(KEY + "count", all.size());
        } else if (shown.size() > ROWS) {
            footer = Component.translatable(KEY + "showing", scroll + 1, Math.min(shown.size(), scroll + ROWS), shown.size());
        } else {
            footer = shown.size() == all.size()
                    ? Component.translatable(all.size() == 1 ? KEY + "count_one" : KEY + "count", all.size())
                    : Component.translatable(KEY + "shown_of", shown.size(), all.size());
        }
        graphics.drawCenteredString(font, footer.copy().withStyle(ChatFormatting.DARK_GRAY), centerX, below, 0xFFFFFF);

        if (hovered >= 0) {
            graphics.renderTooltip(font, tooltip(shown.get(scroll + hovered)), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
