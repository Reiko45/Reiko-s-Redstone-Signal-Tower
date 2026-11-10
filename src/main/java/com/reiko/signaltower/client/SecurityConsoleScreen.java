package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.SecurityConsoleBlockEntity;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.network.ConfigureSecurityPayload;
import com.reiko.signaltower.network.OpenSecurityPayload;
import com.reiko.signaltower.network.SecurityCommandPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SecuritySetup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A Security Console's screen (v0.68.0), in three tabs: <b>Zones</b> (eight rows: a name, a
 * frequency and Instant / Delayed / Always, with a light for a zone heard now), <b>Settings</b> (the
 * alarm frequency, the delay, Arm at night, and Arm / Disarm) and <b>Log</b> (newest first; scroll
 * for more). Done or Enter saves the zones and settings, Escape cancels; Arm and Disarm act at once.
 */
public class SecurityConsoleScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".security.";
    private static final int ROW = 18;
    private static final int LOG_LINES = 11;

    private final OpenSecurityPayload state;
    private final String[] names = new String[SecuritySetup.ZONES];
    private final String[] frequencies = new String[SecuritySetup.ZONES];
    private final int[] modes = new int[SecuritySetup.ZONES];
    private String alarm;
    private int delay;
    private boolean night;
    private int tab;
    private int logScroll;
    private int top;
    private final List<AbstractWidget>[] tabs;
    private final List<Component> logLines = new ArrayList<>();
    private int ticksOpen;

    @SuppressWarnings("unchecked")
    public SecurityConsoleScreen(OpenSecurityPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".security_console"));
        this.state = state;
        SecuritySetup setup = state.setup();
        for (int i = 0; i < SecuritySetup.ZONES; i++) {
            names[i] = setup.name(i);
            frequencies[i] = setup.frequency(i) > 0 ? Integer.toString(setup.frequency(i)) : "";
            modes[i] = setup.mode(i).ordinal();
        }
        alarm = setup.alarmFrequency() > 0 ? Integer.toString(setup.alarmFrequency()) : "";
        delay = setup.delayIndex();
        night = setup.night();
        tabs = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
        buildLog();
    }

    private void buildLog() {
        SecuritySetup setup = state.setup();
        ListTag entries = state.data().getList("log", Tag.TAG_COMPOUND);
        for (int i = entries.size() - 1; i >= 0; i--) {
            CompoundTag line = entries.getCompound(i);
            Object[] when = PagerItem.dayAndTime(line.getLong("day_time"));
            SecurityConsoleBlockEntity.Event event = SecurityConsoleBlockEntity.Event.of(line.getInt("event"));
            int zone = line.getInt("zone");
            String who = line.getString("who");
            Component whoText = who.isEmpty() ? Component.translatable(KEY + "who_unknown")
                    : who.startsWith("#") ? Component.translatable(KEY + "who_" + who.substring(1)) : Component.literal(who);
            String zoneName = zone >= 0 && zone < SecuritySetup.ZONES ? setup.label(zone) : "?";
            Component what = switch (event) {
                case ARMING -> Component.translatable(KEY + "event_arming", whoText).withStyle(ChatFormatting.GOLD);
                case ARMED -> Component.translatable(KEY + "event_armed").withStyle(ChatFormatting.RED);
                case DISARMED -> Component.translatable(KEY + "event_disarmed", whoText).withStyle(ChatFormatting.GREEN);
                case ENTRY -> Component.translatable(KEY + "event_entry", zoneName, whoText).withStyle(ChatFormatting.GOLD);
                case ALARM -> Component.translatable(KEY + "event_alarm", zoneName, whoText).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                case TAMPER -> Component.translatable(KEY + "event_tamper").withStyle(ChatFormatting.RED);
            };
            logLines.add(Component.translatable(KEY + "log_line", when[0], when[1], what).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void init() {
        for (List<AbstractWidget> list : tabs) {
            list.clear();
        }
        int centerX = width / 2;
        top = height / 2 - 112;
        // The tabs.
        String[] tabKeys = {"tab_zones", "tab_settings", "tab_log"};
        for (int t = 0; t < 3; t++) {
            int index = t;
            addRenderableWidget(Button.builder(Component.translatable(KEY + tabKeys[t]), b -> showTab(index))
                    .bounds(centerX - 120 + t * 81, top + 20, 78, 16).build());
        }
        // Zones: eight rows.
        for (int i = 0; i < SecuritySetup.ZONES; i++) {
            int zone = i;
            int y = top + 52 + i * ROW;
            EditBox name = new EditBox(font, centerX - 104, y, 96, 14, Component.translatable(KEY + "zone_name", i + 1));
            name.setMaxLength(SecuritySetup.NAME_LENGTH);
            name.setValue(names[i]);
            name.setHint(Component.translatable(KEY + "zone_hint", i + 1).withStyle(ChatFormatting.DARK_GRAY));
            name.setResponder(text -> names[zone] = text);
            EditBox frequency = new EditBox(font, centerX - 4, y, 72, 14, Component.translatable(KEY + "zone_frequency", i + 1));
            frequency.setMaxLength(Frequency.MAX_DIGITS);
            frequency.setFilter(text -> text.chars().allMatch(Character::isDigit));
            frequency.setValue(frequencies[i]);
            frequency.setHint(Component.translatable(KEY + "frequency_hint").withStyle(ChatFormatting.DARK_GRAY));
            frequency.setResponder(text -> frequencies[zone] = text);
            CycleButton<SecuritySetup.Mode> mode = CycleButton.<SecuritySetup.Mode>builder(m -> Component.translatable(KEY + "mode_" + m.key()))
                    .withValues(SecuritySetup.Mode.values())
                    .withInitialValue(SecuritySetup.Mode.of(modes[i]))
                    .withTooltip(m -> Tooltip.create(Component.translatable(KEY + "mode_" + m.key() + "_tip")))
                    .displayOnlyValue()
                    .create(centerX + 72, y - 1, 64, 16, Component.translatable(KEY + "zone_mode"), (button, value) -> modes[zone] = value.ordinal());
            tabs[0].add(addRenderableWidget(name));
            tabs[0].add(addRenderableWidget(frequency));
            tabs[0].add(addRenderableWidget(mode));
        }
        // Settings.
        EditBox alarmBox = new EditBox(font, centerX + 10, top + 62, 90, 16, Component.translatable(KEY + "alarm"));
        alarmBox.setMaxLength(Frequency.MAX_DIGITS);
        alarmBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        alarmBox.setValue(alarm);
        alarmBox.setHint(Component.translatable(KEY + "frequency_hint").withStyle(ChatFormatting.DARK_GRAY));
        alarmBox.setResponder(text -> alarm = text);
        alarmBox.setTooltip(Tooltip.create(Component.translatable(KEY + "alarm_tip")));
        tabs[1].add(addRenderableWidget(alarmBox));
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < SecuritySetup.DELAYS.length; i++) {
            delays.add(i);
        }
        tabs[1].add(addRenderableWidget(CycleButton.<Integer>builder(i -> Component.translatable(KEY + "seconds", SecuritySetup.DELAYS[i]))
                .withValues(delays).withInitialValue(delay)
                .withTooltip(i -> Tooltip.create(Component.translatable(KEY + "delay_tip")))
                .create(centerX - 100, top + 84, 200, 20, Component.translatable(KEY + "delay"), (b, value) -> delay = value)));
        tabs[1].add(addRenderableWidget(CycleButton.onOffBuilder(night)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "night_tip")))
                .create(centerX - 100, top + 108, 200, 20, Component.translatable(KEY + "night"), (b, value) -> night = value)));
        tabs[1].add(addRenderableWidget(Button.builder(Component.translatable(KEY + "arm"), b -> command(SecurityCommandPayload.ARM))
                .tooltip(Tooltip.create(Component.translatable(KEY + "arm_tip")))
                .bounds(centerX - 100, top + 140, 98, 20).build()));
        tabs[1].add(addRenderableWidget(Button.builder(Component.translatable(KEY + "disarm"), b -> command(SecurityCommandPayload.DISARM))
                .bounds(centerX + 2, top + 140, 98, 20).build()));
        // Done / Cancel.
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(centerX - 102, top + 200, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(centerX + 2, top + 200, 100, 20).build());
        showTab(tab);
    }

    private void showTab(int index) {
        tab = index;
        for (int t = 0; t < tabs.length; t++) {
            for (AbstractWidget widget : tabs[t]) {
                widget.visible = t == index;
                widget.active = t == index;
            }
        }
        setFocused(null);
    }

    private SecuritySetup setup() {
        List<String> n = new ArrayList<>();
        List<Integer> f = new ArrayList<>();
        List<Integer> m = new ArrayList<>();
        for (int i = 0; i < SecuritySetup.ZONES; i++) {
            n.add(names[i]);
            f.add(parse(frequencies[i]));
            m.add(modes[i]);
        }
        return new SecuritySetup(n, f, m, parse(alarm), delay | (night ? SecuritySetup.NIGHT : 0)).normalized();
    }

    private static int parse(String text) {
        try {
            return text.isEmpty() ? 0 : Frequency.clamp(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureSecurityPayload(state.pos(), setup()));
        onClose();
    }

    private void command(int command) {
        // Save what's typed first, so arming uses it.
        PacketDistributor.sendToServer(new ConfigureSecurityPayload(state.pos(), setup()));
        PacketDistributor.sendToServer(new SecurityCommandPayload(state.pos(), command));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 2) {
            logScroll = Math.max(0, Math.min(Math.max(0, logLines.size() - LOG_LINES), logScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, stateLine(), centerX, top + 11, 0xFFFFFF);
        if (tab == 0) {
            int open = state.data().getInt("open");
            graphics.drawString(font, Component.translatable(KEY + "head_name"), centerX - 104, top + 41, 0xA0A0A0);
            graphics.drawString(font, Component.translatable(KEY + "head_frequency"), centerX - 4, top + 41, 0xA0A0A0);
            graphics.drawString(font, Component.translatable(KEY + "head_mode"), centerX + 72, top + 41, 0xA0A0A0);
            for (int i = 0; i < SecuritySetup.ZONES; i++) {
                int y = top + 52 + i * ROW;
                graphics.drawString(font, Integer.toString(i + 1), centerX - 116, y + 3, 0xA0A0A0);
                if (!frequencies[i].isEmpty()) {
                    boolean on = (open & 1 << i) != 0;
                    graphics.fill(centerX + 140, y + 3, centerX + 148, y + 11, on ? 0xFFFFC040 : 0xFF40C040);
                }
            }
        } else if (tab == 1) {
            graphics.drawString(font, Component.translatable(KEY + "control", FrequencyNames.label(null, state.frequency())),
                    centerX - 100, top + 46, 0xA0A0A0);
            graphics.drawString(font, Component.translatable(KEY + "alarm"), centerX - 100, top + 66, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.translatable(KEY + "control_hint"), centerX, top + 166, 0x808080);
            graphics.drawCenteredString(font, Component.translatable(KEY + "alarm_hint"), centerX, top + 177, 0x808080);
        } else {
            if (logLines.isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable(KEY + "log_empty"), centerX, top + 60, 0x808080);
            }
            for (int i = 0; i < LOG_LINES && logScroll + i < logLines.size(); i++) {
                Component line = logLines.get(logScroll + i);
                graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(line, 260)),
                        centerX - 130, top + 44 + i * 13, 0xC0C0C0);
            }
        }
    }

    /** "Armed", "Arming: 7 s" (counting down while the screen is open) ... */
    private Component stateLine() {
        SecurityConsoleBlockEntity.State current = SecurityConsoleBlockEntity.State.of(state.data().getInt("state"));
        long left = Math.max(0, (state.data().getLong("left") - ticksOpen + 19) / 20);
        int zone = state.data().getInt("alarm_zone");
        String zoneName = zone >= 0 && zone < SecuritySetup.ZONES ? state.setup().label(zone) : "?";
        String who = state.data().getString("alarm_who");
        String alarmText = who.isEmpty() || who.startsWith("#") ? zoneName : zoneName + " (" + who + ")";
        String key = "message." + SignalTower.MOD_ID + ".security_state_";
        return switch (current) {
            case DISARMED -> Component.translatable(key + "disarmed").withStyle(ChatFormatting.GREEN);
            case ARMING -> Component.translatable(key + "arming", left).withStyle(ChatFormatting.GOLD);
            case ARMED -> Component.translatable(key + "armed").withStyle(ChatFormatting.RED);
            case ENTRY -> Component.translatable(key + "entry", left, zoneName).withStyle(ChatFormatting.GOLD);
            case ALARM -> Component.translatable(key + "alarm", alarmText).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
