package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.SignalRecorderBlockEntity;
import com.reiko.signaltower.network.ConfigureRecorderPayload;
import com.reiko.signaltower.network.OpenRecorderScreenPayload;
import com.reiko.signaltower.network.RecorderStatus;
import com.reiko.signaltower.network.RecorderStatusPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.OptionalInt;

/**
 * A Radio Signal Recorder's screen (v0.44.0): Plays on and Records from (with the directory), Plays
 * (once on a redstone pulse, or on a loop while powered), the recording drawn as a strip (each
 * stretch as tall as its strength, with a line where it's playing), what it's doing, and Record,
 * Stop, Play and Clear. Those four save the boxes too and keep the screen open; Done or Enter
 * saves, Escape cancels. It asks the recorder what it's doing twice a second.
 */
public class RecorderScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".recorder.";
    private static final int STRIP_WIDTH = 240;
    private static final int STRIP_HEIGHT = 18;

    private final OpenRecorderScreenPayload state;
    private RecorderStatus status;
    /** The client's game time when {@link #status} came, so times can count on between answers. */
    private long statusAt;
    private boolean loop;
    private EditBox sendBox;
    private EditBox listenBox;
    private OptionalInt pickedSend = OptionalInt.empty();
    private OptionalInt pickedListen = OptionalInt.empty();
    private Button recordButton;
    private Button stopButton;
    private Button playButton;
    private Button clearButton;
    private Button doneButton;
    private int top;
    private int ticks;

    public RecorderScreen(OpenRecorderScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_signal_recorder"));
        this.state = state;
        this.status = state.status();
        this.loop = state.loop();
        this.statusAt = now();
    }

    private long now() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null ? level.getGameTime() : 0;
    }

    /** {@link RecorderStatus#elapsed()}, counted on since it came. */
    private int elapsed() {
        return (int) Math.min(Integer.MAX_VALUE, status.elapsed() + Math.max(0, now() - statusAt));
    }

    public void onStatus(RecorderStatus status) {
        if (status.pos().equals(state.status().pos())) {
            this.status = status;
            this.statusAt = now();
            updateButtons();
        }
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 104;
        String keepSend = sendBox != null ? sendBox.getValue() : Integer.toString(state.frequency());
        String keepListen = listenBox != null ? listenBox.getValue() : state.listen() > 0 ? Integer.toString(state.listen()) : "";
        if (pickedSend.isPresent()) {
            keepSend = Integer.toString(pickedSend.getAsInt());
            pickedSend = OptionalInt.empty();
        }
        if (pickedListen.isPresent()) {
            keepListen = Integer.toString(pickedListen.getAsInt());
            pickedListen = OptionalInt.empty();
        }
        listenBox = box(centerX - 4, top + 26, keepListen, "listen");
        listenBox.setHint(Component.translatable(KEY + "listen_none").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(listenBox, true))
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory")))
                .bounds(centerX + 102, top + 26, 16, 20).build());
        sendBox = box(centerX - 4, top + 50, keepSend, "send");
        addRenderableWidget(Button.builder(Component.literal("..."), b -> openDirectory(sendBox, false))
                .tooltip(Tooltip.create(Component.translatable(KEY + "directory")))
                .bounds(centerX + 102, top + 50, 16, 20).build());
        addRenderableWidget(CycleButton.<Boolean>builder(value -> SignalRecorderBlockEntity.modeText(value))
                .withValues(false, true)
                .withInitialValue(loop)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + (value ? "loop_tip" : "once_tip"))))
                .create(centerX - 120, top + 76, 240, 20, Component.translatable(KEY + "mode"), (button, value) -> loop = value));
        int buttonY = top + 166;
        recordButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "record"), b -> send(ConfigureRecorderPayload.RECORD))
                .tooltip(Tooltip.create(Component.translatable(KEY + "record_tip")))
                .bounds(centerX - 120, buttonY, 58, 20).build());
        stopButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "stop"), b -> send(ConfigureRecorderPayload.STOP))
                .bounds(centerX - 59, buttonY, 58, 20).build());
        playButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "play"), b -> send(ConfigureRecorderPayload.PLAY))
                .tooltip(Tooltip.create(Component.translatable(KEY + "play_tip")))
                .bounds(centerX + 2, buttonY, 58, 20).build());
        clearButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "clear"), b -> send(ConfigureRecorderPayload.CLEAR))
                .bounds(centerX + 62, buttonY, 58, 20).build());
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, buttonY + 26, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, buttonY + 26, 100, 20).build());
        updateButtons();
    }

    private EditBox box(int x, int y, String value, String name) {
        EditBox box = new EditBox(font, x, y, 104, 20, Component.translatable(KEY + name));
        box.setMaxLength(Frequency.MAX_DIGITS);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.translatable(KEY + name + "_tip")));
        box.setResponder(text -> updateButtons());
        addRenderableWidget(box);
        return box;
    }

    private void openDirectory(EditBox box, boolean listen) {
        if (minecraft != null) {
            OptionalInt current = parse(box, listen);
            minecraft.setScreen(new DirectoryScreen(this, current.orElse(-1), frequency -> {
                if (listen) {
                    pickedListen = OptionalInt.of(frequency);
                } else {
                    pickedSend = OptionalInt.of(frequency);
                }
            }, state.status().pos()));
        }
    }

    /** The frequency typed (empty allowed for Records from: 0), or an empty optional if it isn't one. */
    private static OptionalInt parse(EditBox box, boolean emptyAllowed) {
        String text = box.getValue();
        if (text.isEmpty()) {
            return emptyAllowed ? OptionalInt.of(0) : OptionalInt.empty();
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX ? OptionalInt.of((int) value) : OptionalInt.empty();
    }

    private void updateButtons() {
        if (doneButton == null) {
            return;
        }
        OptionalInt send = parse(sendBox, false);
        OptionalInt listen = parse(listenBox, true);
        boolean valid = send.isPresent() && listen.isPresent() && send.getAsInt() != listen.getAsInt();
        SignalRecorderBlockEntity.Phase phase = status.phaseValue();
        boolean taping = phase == SignalRecorderBlockEntity.Phase.WAITING || phase == SignalRecorderBlockEntity.Phase.RECORDING;
        doneButton.active = valid;
        recordButton.active = valid && listen.getAsInt() > 0 && !taping;
        stopButton.active = valid && phase != SignalRecorderBlockEntity.Phase.IDLE;
        playButton.active = valid && !taping && status.length() > 0;
        clearButton.active = valid && !taping && status.length() > 0;
    }

    private void send(int action) {
        OptionalInt send = parse(sendBox, false);
        OptionalInt listen = parse(listenBox, true);
        if (send.isPresent() && listen.isPresent()) {
            PacketDistributor.sendToServer(new ConfigureRecorderPayload(state.status().pos(), send.getAsInt(), listen.getAsInt(), loop, action));
        }
    }

    private void save() {
        if (doneButton.active) {
            send(ConfigureRecorderPayload.SAVE);
            onClose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 10 == 0) {
            PacketDistributor.sendToServer(new RecorderStatusPayload.Request(state.status().pos()));
        }
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        label(graphics, Component.translatable(KEY + "listen"), listenBox, true);
        label(graphics, Component.translatable(KEY + "send"), sendBox, false);
        drawStrip(graphics, centerX - STRIP_WIDTH / 2, top + 104);
        graphics.drawCenteredString(font, statusLine(), centerX, top + 140, 0xFFFFFF);
        graphics.drawCenteredString(font, detailLine(), centerX, top + 152, 0xFFFFFF);
    }

    /** The box's label on its left, and the frequency's name (if it has one) on the right of its "...". */
    private void label(GuiGraphics graphics, Component label, EditBox box, boolean emptyAllowed) {
        graphics.drawString(font, label, box.getX() - 8 - font.width(label), box.getY() + 6, 0xFFFFFF);
        OptionalInt typed = parse(box, emptyAllowed);
        String name = typed.isPresent() && typed.getAsInt() > 0 ? FrequencyNames.nameOf(null, typed.getAsInt()) : null;
        int x = box.getX() + box.getWidth() + 24;
        if (name != null && width - x > 12) {
            graphics.drawString(font, font.plainSubstrByWidth(name, width - x - 4), x, box.getY() + 6, 0x55FFFF);
        }
    }

    /**
     * The recording as a strip: dark where it sends nothing, red bars as tall as the strength
     * elsewhere, a white line where it's playing; while recording it fills in from the left over 30 seconds.
     */
    private void drawStrip(GuiGraphics graphics, int x, int y) {
        SignalRecorderBlockEntity.Phase phase = status.phaseValue();
        boolean recording = phase == SignalRecorderBlockEntity.Phase.RECORDING;
        int length = recording ? Math.min(SignalRecorderBlockEntity.MAX_TICKS, elapsed()) : status.length();
        int scale = recording || length <= 0 ? SignalRecorderBlockEntity.MAX_TICKS : length;
        graphics.fill(x - 1, y - 1, x + STRIP_WIDTH + 1, y + STRIP_HEIGHT + 1, 0xFF000000);
        graphics.fill(x, y, x + STRIP_WIDTH, y + STRIP_HEIGHT, 0xFF1C1C22);
        if (recording && length > 0) {
            graphics.fill(x, y, x + Math.min(STRIP_WIDTH, length * STRIP_WIDTH / scale), y + STRIP_HEIGHT, 0xFF2A1C1C);
        }
        List<Integer> changes = status.changes();
        for (int i = 0; i < changes.size(); i++) {
            int strength = changes.get(i) & 15;
            int from = changes.get(i) >> 4;
            int to = i + 1 < changes.size() ? changes.get(i + 1) >> 4 : length;
            if (strength <= 0 || to <= from) {
                continue;
            }
            int left = x + from * STRIP_WIDTH / scale;
            int right = Math.max(left + 1, x + Math.min(to, scale) * STRIP_WIDTH / scale);
            int height = Math.max(3, strength * (STRIP_HEIGHT - 2) / 15);
            graphics.fill(left, y + STRIP_HEIGHT - 1 - height, right, y + STRIP_HEIGHT - 1, strength >= 15 ? 0xFFFF4A3A : 0xFFC0382C);
        }
        if (phase == SignalRecorderBlockEntity.Phase.PLAYING && length > 0) {
            int at = loop ? elapsed() % length : Math.min(length, elapsed());
            int head = x + at * STRIP_WIDTH / scale;
            graphics.fill(head, y - 2, head + 1, y + STRIP_HEIGHT + 2, 0xFFFFFFFF);
        }
        String end = SignalRecorderBlockEntity.seconds(scale) + "s";
        graphics.drawString(font, "0s", x, y + STRIP_HEIGHT + 3, 0x808080);
        graphics.drawString(font, end, x + STRIP_WIDTH - font.width(end), y + STRIP_HEIGHT + 3, 0x808080);
    }

    /** What it's doing: waiting, recording, playing, or what it has. */
    private Component statusLine() {
        int listen = parse(listenBox, true).orElse(0);
        return switch (status.phaseValue()) {
            case WAITING -> Component.translatable(KEY + "waiting", FrequencyNames.label(null, Math.max(1, listen))).withStyle(ChatFormatting.YELLOW);
            case RECORDING -> Component.translatable(KEY + "recording",
                    SignalRecorderBlockEntity.seconds(Math.min(SignalRecorderBlockEntity.MAX_TICKS, elapsed())), status.changes().size())
                    .withStyle(ChatFormatting.RED);
            case PLAYING -> Component.translatable(KEY + (loop ? "playing_loop" : "playing"),
                    SignalRecorderBlockEntity.seconds(status.length() > 0 ? (loop ? elapsed() % status.length() : Math.min(status.length(), elapsed())) : 0),
                    SignalRecorderBlockEntity.seconds(status.length())).withStyle(ChatFormatting.GREEN);
            case IDLE -> status.length() > 0
                    ? Component.translatable(KEY + "recorded", SignalRecorderBlockEntity.tapeText(status.length(), status.changes().size()))
                    : Component.translatable(KEY + "empty").withStyle(ChatFormatting.GRAY);
        };
    }

    /** What it hears and whether redstone reaches it, or why it can't hear or send. */
    private Component detailLine() {
        int listen = parse(listenBox, true).orElse(0);
        if (status.deaf() && listen > 0) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(listen);
            return Component.translatable(KEY + "deaf", FrequencyNames.label(null, listen), claim != null ? claim.ownerName() : "?")
                    .withStyle(ChatFormatting.RED);
        }
        int send = parse(sendBox, false).orElse(state.frequency());
        if (status.silenced()) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(send);
            return Component.translatable(KEY + "silenced", FrequencyNames.label(null, send), claim != null ? claim.ownerName() : "?")
                    .withStyle(ChatFormatting.RED);
        }
        Component hearing = listen <= 0 ? Component.translatable(KEY + "nothing_set")
                : status.heard() > 0 ? Component.literal(Integer.toString(status.heard())).withStyle(ChatFormatting.GREEN)
                : Component.translatable(KEY + "nothing");
        Component redstone = Component.translatable(KEY + (status.powered() ? "redstone_on" : "redstone_off"));
        return Component.translatable(KEY + "detail", hearing, redstone).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
