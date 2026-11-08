package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.network.TelegraphActionPayload;
import com.reiko.signaltower.network.TelegraphFeedPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.Telegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.OptionalInt;

/**
 * A Radio Telegraph's screen (v0.51.0): its frequency (with "..." for the directory) and how far
 * it reaches, the last messages that reached it (newest at the bottom, checked once a second), and
 * a box to type one. Send or Enter sends it (re-tuning first if the frequency changed); Done or
 * Escape closes.
 */
public class TelegraphScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".telegraph.";
    private static final int FEED_WIDTH = 260;
    private static final int LINE_HEIGHT = 12;

    private TelegraphFeedPayload state;
    private EditBox frequencyBox;
    private EditBox messageBox;
    private Button sendButton;
    private Component notice;
    private int noticeTicks;
    private int ticks;
    private int top;
    private int picked = -1;

    public TelegraphScreen(TelegraphFeedPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_telegraph"));
        this.state = state;
        state.notice().ifPresent(this::show);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 98;
        String keepFrequency = frequencyBox != null ? frequencyBox.getValue() : Integer.toString(state.header().frequency());
        String keepMessage = messageBox != null ? messageBox.getValue() : "";
        if (picked > 0) {
            keepFrequency = Integer.toString(picked);
            picked = -1;
        }
        frequencyBox = addRenderableWidget(new EditBox(font, centerX - 60, top + 16, 80, 20, Component.translatable(KEY + "frequency")));
        frequencyBox.setMaxLength(Frequency.MAX_DIGITS);
        frequencyBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        frequencyBox.setValue(keepFrequency);
        frequencyBox.setResponder(text -> updateSend());
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory())
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory")))
                .bounds(centerX + 24, top + 16, 16, 20).build());

        int feedBottom = top + 44 + Telegraph.FEED_LINES * LINE_HEIGHT + 6;
        messageBox = addRenderableWidget(new EditBox(font, centerX - FEED_WIDTH / 2, feedBottom + 6, FEED_WIDTH - 62, 20,
                Component.translatable(KEY + "message")));
        messageBox.setMaxLength(Telegraph.MAX_LENGTH);
        messageBox.setValue(keepMessage);
        messageBox.setHint(Component.translatable(KEY + "hint").withStyle(ChatFormatting.DARK_GRAY));
        messageBox.setResponder(text -> updateSend());
        sendButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "send"), b -> send())
                .bounds(centerX + FEED_WIDTH / 2 - 58, feedBottom + 6, 58, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(centerX - 50, feedBottom + 54, 100, 20).build());
        setInitialFocus(messageBox);
        updateSend();
    }

    private void openDirectory() {
        if (minecraft != null) {
            minecraft.setScreen(new DirectoryScreen(this, parse().orElse(-1), frequency -> picked = frequency, state.header().pos()));
        }
    }

    private OptionalInt parse() {
        String text = frequencyBox.getValue();
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateSend() {
        if (sendButton == null || messageBox == null) {
            return;
        }
        OptionalInt frequency = parse();
        boolean retune = frequency.isPresent() && frequency.getAsInt() != state.header().frequency();
        sendButton.active = frequency.isPresent() && (retune || !Telegraph.clean(messageBox.getValue()).isEmpty());
        sendButton.setMessage(Component.translatable(KEY + (retune && Telegraph.clean(messageBox.getValue()).isEmpty() ? "tune" : "send")));
    }

    private void send() {
        OptionalInt frequency = parse();
        if (sendButton.active && frequency.isPresent()) {
            PacketDistributor.sendToServer(new TelegraphActionPayload(state.header().pos(), frequency.getAsInt(), messageBox.getValue(), true));
        }
    }

    /** The server's answer, or its once-a-second update. */
    public void onFeed(TelegraphFeedPayload feed) {
        if (!feed.header().pos().equals(state.header().pos())) {
            return;
        }
        int before = state.header().frequency();
        state = feed;
        feed.notice().ifPresent(this::show);
        if (feed.sent()) {
            messageBox.setValue("");
        }
        if (before != feed.header().frequency()) {
            frequencyBox.setValue(Integer.toString(feed.header().frequency()));
        }
        updateSend();
    }

    private void show(Component message) {
        notice = message;
        noticeTicks = 120;
    }

    @Override
    public void tick() {
        super.tick();
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        if (++ticks % 20 == 0) {
            PacketDistributor.sendToServer(new TelegraphActionPayload(state.header().pos(), state.header().frequency(), "", false));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && sendButton.active) {
            send();
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int left = centerX - FEED_WIDTH / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        Component label = Component.translatable(KEY + "frequency");
        graphics.drawString(font, label, centerX - 66 - font.width(label), top + 22, 0xFFFFFF);
        TelegraphFeedPayload.Header header = state.header();
        Component reach = RemoteControlItem.describeReach(header.reach(), header.towers(), header.repeaters());
        String name = FrequencyNames.nameOf(null, header.frequency());
        graphics.drawString(font, Component.translatable(KEY + "reach", reach), centerX + 46, top + 18, 0xA0A0A0);
        if (name != null) {
            graphics.drawString(font, font.plainSubstrByWidth(name, Math.max(0, width - centerX - 50)), centerX + 46, top + 28, 0x55FFFF);
        }

        int feedTop = top + 44;
        int feedHeight = Telegraph.FEED_LINES * LINE_HEIGHT + 6;
        graphics.fill(left - 1, feedTop - 1, left + FEED_WIDTH + 1, feedTop + feedHeight + 1, 0xFF505050);
        graphics.fill(left, feedTop, left + FEED_WIDTH, feedTop + feedHeight, 0xFF101010);
        List<com.reiko.signaltower.radio.Telegraph.Line> lines = state.lines();
        if (header.deaf()) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(header.frequency());
            graphics.drawCenteredString(font, Component.translatable(KEY + "deaf", claim == null ? "?" : claim.ownerName())
                    .withStyle(ChatFormatting.RED), centerX, feedTop + feedHeight - 14, 0xFFFFFF);
        }
        if (lines.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty").withStyle(ChatFormatting.DARK_GRAY),
                    centerX, feedTop + feedHeight / 2 - 4, 0xFFFFFF);
        }
        for (int i = 0; i < lines.size(); i++) {
            Telegraph.Line line = lines.get(i);
            int y = feedTop + 4 + i * LINE_HEIGHT;
            Object[] when = PagerItem.dayAndTime(line.dayTime());
            MutableComponent text = Component.literal("[" + when[1] + "] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(line.who() + ": ").withStyle(ChatFormatting.YELLOW))
                    .append(line.encrypted() ? Component.translatable(KEY + "encrypted").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)
                            : Component.literal(line.text()).withStyle(ChatFormatting.WHITE));
            List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(text, FEED_WIDTH - 8);
            if (!wrapped.isEmpty()) {
                graphics.drawString(font, wrapped.get(0), left + 4, y, 0xFFFFFF); // the rest is in the tooltip
            }
            if (mouseX >= left && mouseX < left + FEED_WIDTH && mouseY >= y - 1 && mouseY < y + LINE_HEIGHT - 1) {
                graphics.renderComponentTooltip(font, List.of(
                        Component.literal(line.who()).withStyle(ChatFormatting.YELLOW),
                        line.encrypted() ? Component.translatable(KEY + "encrypted").withStyle(ChatFormatting.RED)
                                : Component.literal(line.text()).withStyle(ChatFormatting.WHITE),
                        Component.translatable(KEY + "when", when[0], when[1], Telegraph.ago(line.secondsAgo())).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
            }
        }
        int feedBottom = feedTop + feedHeight;
        String count = messageBox.getValue().length() + " / " + Telegraph.MAX_LENGTH;
        graphics.drawString(font, count, left + FEED_WIDTH - 62 - font.width(count), feedBottom + 29, 0x808080);
        if (notice != null && noticeTicks > 0) {
            graphics.drawCenteredString(font, notice, centerX, feedBottom + 41, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
