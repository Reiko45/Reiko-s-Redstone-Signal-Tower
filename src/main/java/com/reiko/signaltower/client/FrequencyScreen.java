package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import net.minecraft.ChatFormatting;
import com.reiko.signaltower.item.PulseLength;
import com.reiko.signaltower.item.TunerPresets;
import com.reiko.signaltower.network.ConfigureFavoritesPayload;
import com.reiko.signaltower.network.ConfigurePagerPayload;
import com.reiko.signaltower.network.ConfigureRadioItemPayload;
import com.reiko.signaltower.network.RemoteScanRequestPayload;
import com.reiko.signaltower.network.RemoteScanResultPayload;
import com.reiko.signaltower.network.SetFrequencyNamePayload;
import com.reiko.signaltower.network.SetFrequencyPrivacyPayload;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Type a frequency for the Frequency Tuner, Remote Control or Radio Pager (plus the remote's mode).
 * Enter or Done saves it, Escape cancels. All three get a row of favorites: click an empty one to
 * save the frequency above, click a saved one to use it, sneak-click to replace it, right-click to
 * clear it. Favorites are saved straight away, even if you then press Cancel.
 * <p>
 * Layout, from {@code top}: title, range hint, the frequency row; for the remote the locator and
 * reach lines; for the tuner the name box, the Private button and Friends box, and a line saying
 * who can send on the frequency; the favorites; the remote's Mode and Length buttons; Done and
 * Cancel.
 */
public class FrequencyScreen extends Screen implements RemoteScanListener {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".tuner";

    private final InteractionHand hand;
    private final int initialFrequency;
    /** Null for the tuner, which has no mode. */
    private @Nullable Boolean toggleMode;
    private int pulseTicks;
    private @Nullable Button lengthButton;
    /** Locator (remotes only): the latest answer, and ticks until the next question. */
    private @Nullable RemoteScanResultPayload.Entry scan;
    private int scanReach;
    private int scanTowers;
    private boolean scanned;
    private int scanTimer;
    private EditBox input;
    private Button doneButton;
    /** The favorites (all three items have them). */
    private @Nullable TunerPresets presets;
    /** Pager only: its Sound and Chat log settings (null for the tuner and remote). */
    private @Nullable Boolean pagerSound;
    private boolean pagerChatLog;
    private final Button[] presetButtons = new Button[TunerPresets.COUNT];
    /** Tuner only: the name of the typed frequency (shared by everyone in the world). */
    private @Nullable EditBox nameBox;
    /** True once the player typed in the name box, so changing the frequency doesn't overwrite it. */
    private boolean nameEdited;
    private boolean fillingName;
    /** Tuner only: whether the typed frequency is to be private, and the friends typed for it. */
    private @Nullable Button privateButton;
    private @Nullable EditBox friendsBox;
    private boolean privateOn;
    /** True once the player clicked Private or typed friends, so changing the frequency doesn't undo it. */
    private boolean privacyEdited;
    private boolean fillingPrivacy;
    /** A frequency picked in the directory, used when this screen is shown again. */
    private @Nullable Integer picked;
    /** Where things go, worked out in init(). */
    private int top;
    private int inputY;
    private int favoritesY;

    public FrequencyScreen(InteractionHand hand, int frequency, Component title,
                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks) {
        super(title);
        this.hand = hand;
        this.initialFrequency = frequency;
        this.toggleMode = toggleMode;
        this.pulseTicks = pulseTicks == null ? PulseLength.DEFAULT_TICKS : pulseTicks;
    }

    /** The screen with a row of favorites (all three items use this). */
    public FrequencyScreen(InteractionHand hand, int frequency, Component title,
                           @Nullable Boolean toggleMode, @Nullable Integer pulseTicks, @Nullable TunerPresets presets) {
        this(hand, frequency, title, toggleMode, pulseTicks);
        this.presets = presets;
    }

    /** A Radio Pager's screen: like the tuner's, plus Sound and Chat log buttons. */
    public FrequencyScreen(InteractionHand hand, int frequency, Component title, TunerPresets presets,
                           boolean sound, boolean chatLog) {
        this(hand, frequency, title, null, null, presets);
        this.pagerSound = sound;
        this.pagerChatLog = chatLog;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        boolean remote = toggleMode != null;
        boolean optionRow = remote || pagerSound != null;
        boolean tuner = !remote && pagerSound == null;
        int tunerRows = 26 + 24 + 16; // name box, Private and Friends, who can send
        int contentHeight = 34 + 20 + (remote ? 32 : 6) + (tuner ? tunerRows : 0) + (presets != null ? 37 : 0) + (optionRow ? 24 : 0) + 20;
        top = centerY - Math.max(44, contentHeight / 2);
        inputY = top + 34;
        // under the frequency row (and the remote's two locator lines, or the tuner's name and privacy rows)
        int y = inputY + 20 + (remote ? 32 : 6) + (tuner ? tunerRows : 0);

        // init() runs again when the window is resized or the directory hands back: keep what was typed.
        String keepFrequency = input != null ? input.getValue() : null;
        if (picked != null) {
            keepFrequency = Integer.toString(picked);
            picked = null;
            nameEdited = false;     // a new frequency: show its own name and privacy
            privacyEdited = false;
            scan = null;            // and ask the locator about it straight away
            scanTimer = 1;
        }
        String keepName = nameBox != null && nameEdited ? nameBox.getValue() : null;
        String keepFriends = friendsBox != null && privacyEdited ? friendsBox.getValue() : null;

        input = new EditBox(font, centerX - 50, inputY, 100, 20, Component.translatable(KEY + ".input"));
        input.setMaxLength(Frequency.MAX_DIGITS);
        input.setFilter(text -> text.chars().allMatch(Character::isDigit));
        input.setValue(keepFrequency != null ? keepFrequency : Integer.toString(initialFrequency));
        input.setResponder(text -> {
            updateDoneButton();
            updatePresetButtons();
            scan = null;     // show "checking" until the new frequency's answer arrives
            scanTimer = 5;
            fillName();
            fillPrivacy();
        });
        addRenderableWidget(input);

        if (tuner) {
            nameBox = new EditBox(font, centerX - 50, inputY + 26, 162, 20, Component.translatable(KEY + ".name"));
            nameBox.setMaxLength(FrequencyNames.MAX_LENGTH);
            nameBox.setHint(Component.translatable(KEY + ".name_hint").withStyle(ChatFormatting.DARK_GRAY));
            nameBox.setResponder(text -> {
                if (!fillingName) {
                    nameEdited = true;
                }
            });
            addRenderableWidget(nameBox);
            fillName();
            if (keepName != null) {
                fillingName = true;
                nameBox.setValue(keepName);
                fillingName = false;
            }

            privateButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                privateOn = !privateOn;
                privacyEdited = true;
                updatePrivacyWidgets();
            }).tooltip(Tooltip.create(Component.translatable(KEY + ".private_tooltip")))
                    .bounds(centerX - 112, inputY + 50, 76, 20).build());
            friendsBox = new EditBox(font, centerX - 32, inputY + 50, 144, 20, Component.translatable(KEY + ".friends"));
            friendsBox.setMaxLength(SetFrequencyPrivacyPayload.MAX_FRIENDS_TEXT);
            friendsBox.setHint(Component.translatable(KEY + ".friends_hint").withStyle(ChatFormatting.DARK_GRAY));
            friendsBox.setTooltip(Tooltip.create(Component.translatable(KEY + ".friends_tooltip", FrequencyClaims.MAX_FRIENDS)));
            friendsBox.setResponder(text -> {
                if (!fillingPrivacy) {
                    privacyEdited = true;
                }
            });
            addRenderableWidget(friendsBox);
            fillPrivacy();
            if (keepFriends != null) {
                fillingPrivacy = true;
                friendsBox.setValue(keepFriends);
                fillingPrivacy = false;
                updatePrivacyWidgets();
            }
        }

        addRenderableWidget(nudgeButton(-100, centerX - 112, 32));
        addRenderableWidget(nudgeButton(-1, centerX - 76, 22));
        addRenderableWidget(nudgeButton(1, centerX + 54, 22));
        addRenderableWidget(nudgeButton(100, centerX + 80, 32));

        if (presets != null) {
            // Five favorites, with a "Favorites" heading (drawn in render()) above them
            favoritesY = y - 1;
            int rowX = centerX - (TunerPresets.COUNT * 64 - 4) / 2;
            for (int i = 0; i < TunerPresets.COUNT; i++) {
                int slot = i;
                presetButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> pressPreset(slot))
                        .bounds(rowX + i * 64, y + 10, 60, 20).build());
            }
            updatePresetButtons();
            y += 37;
        }
        int buttonsY = y;
        if (toggleMode != null) {
            addRenderableWidget(Button.builder(modeLabel(), button -> {
                toggleMode = !toggleMode;
                button.setMessage(modeLabel());
                updateLengthButton();
            }).bounds(centerX - 102, buttonsY, 100, 20).build());
            // Click for a longer pulse, sneak-click for a shorter one. Only used in pulse mode.
            lengthButton = addRenderableWidget(Button.builder(lengthLabel(), button -> {
                pulseTicks = PulseLength.next(pulseTicks, Screen.hasShiftDown());
                updateLengthButton();
            }).bounds(centerX + 2, buttonsY, 100, 20).build());
            updateLengthButton();
            buttonsY += 24;
        }
        if (pagerSound != null) {
            addRenderableWidget(Button.builder(pagerSoundLabel(), button -> {
                pagerSound = !pagerSound;
                button.setMessage(pagerSoundLabel());
            }).bounds(centerX - 102, buttonsY, 100, 20).build());
            addRenderableWidget(Button.builder(pagerChatLabel(), button -> {
                pagerChatLog = !pagerChatLog;
                button.setMessage(pagerChatLabel());
            }).tooltip(Tooltip.create(Component.translatable(KEY + ".pager_chat_tooltip")))
                    .bounds(centerX + 2, buttonsY, 100, 20).build());
            buttonsY += 24;
        }
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(centerX - 112, buttonsY, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + ".directory"), button -> openDirectory())
                .tooltip(Tooltip.create(Component.translatable(KEY + ".directory_tooltip")))
                .bounds(centerX - 36, buttonsY, 72, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 40, buttonsY, 72, 20).build());

        setInitialFocus(input);
        updateDoneButton();
    }

    /** Shows the typed frequency's current name in the name box, until the player types their own. */
    private void fillName() {
        if (nameBox == null || nameEdited) {
            return;
        }
        String name = parsed().isPresent() ? FrequencyNames.nameOf(null, parsed().getAsInt()) : null;
        fillingName = true;
        nameBox.setValue(name == null ? "" : name);
        fillingName = false;
    }

    /** The typed frequency's claim as this client knows it, or null if it's public (or the box is empty). */
    private FrequencyClaims.@Nullable ClaimInfo claim() {
        return parsed().isPresent() ? FrequencyClaims.clientInfo(parsed().getAsInt()) : null;
    }

    private boolean mine(FrequencyClaims.@Nullable ClaimInfo claim) {
        return claim != null && minecraft != null && minecraft.player != null && claim.owner().equals(minecraft.player.getUUID());
    }

    /** Server operators can make anyone's frequency public. */
    private boolean isOperator() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    /**
     * Shows whether the typed frequency is private, and its friends, until the player changes
     * them. Someone else's private frequency can't be edited, so it's always shown as it is.
     */
    private void fillPrivacy() {
        if (friendsBox == null) {
            return;
        }
        FrequencyClaims.ClaimInfo claim = claim();
        boolean editable = claim == null || mine(claim);
        if (!privacyEdited || !editable) {
            privacyEdited = false;
            privateOn = claim != null;
            fillingPrivacy = true;
            friendsBox.setValue(claim == null ? "" : String.join(", ", claim.friends()));
            fillingPrivacy = false;
        }
        updatePrivacyWidgets();
    }

    private void updatePrivacyWidgets() {
        if (privateButton == null || friendsBox == null) {
            return;
        }
        FrequencyClaims.ClaimInfo claim = claim();
        boolean editable = claim == null || mine(claim);
        privateButton.setMessage(Component.translatable(KEY + (privateOn ? ".private_on" : ".private_off"))
                .withStyle(privateOn ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        privateButton.active = parsed().isPresent() && (editable || isOperator());
        friendsBox.setEditable(editable && privateOn);
        if (nameBox != null) {
            // A private frequency's name is up to its owner and friends (and operators).
            nameBox.setEditable(claim == null || mine(claim) || isFriend(claim) || isOperator());
        }
    }

    private boolean isFriend(FrequencyClaims.ClaimInfo claim) {
        return minecraft != null && minecraft.player != null
                && claim.friends().stream().anyMatch(n -> n.equalsIgnoreCase(minecraft.player.getGameProfile().getName()));
    }

    /** True if Done should send the Private button and Friends box: they differ from the frequency's claim. */
    private boolean privacyChanged(FrequencyClaims.@Nullable ClaimInfo claim) {
        if (friendsBox == null || privateOn != (claim != null)) {
            return friendsBox != null;
        }
        if (claim == null || !mine(claim)) {
            return false;
        }
        java.util.Set<String> typed = new java.util.HashSet<>();
        SetFrequencyPrivacyPayload.parseNames(friendsBox.getValue()).forEach(n -> typed.add(n.toLowerCase(java.util.Locale.ROOT)));
        java.util.Set<String> current = new java.util.HashSet<>();
        claim.friends().forEach(n -> current.add(n.toLowerCase(java.util.Locale.ROOT)));
        return !typed.equals(current);
    }

    /** The line under the Private button: who can send on the typed frequency, or what Done will change. */
    private Component privacyLine() {
        FrequencyClaims.ClaimInfo claim = claim();
        String key = KEY + ".privacy_";
        if (privacyChanged(claim)) {
            return Component.translatable(key + (privateOn ? (claim == null ? "will_claim" : "will_update") : "will_release"))
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (claim == null) {
            return Component.translatable(key + "public").withStyle(ChatFormatting.GRAY);
        }
        if (mine(claim)) {
            return claim.friends().isEmpty()
                    ? Component.translatable(key + "mine").withStyle(ChatFormatting.GOLD)
                    : claim.friends().size() == 1
                    ? Component.translatable(key + "mine_one_friend").withStyle(ChatFormatting.GOLD)
                    : Component.translatable(key + "mine_friends", claim.friends().size()).withStyle(ChatFormatting.GOLD);
        }
        return isFriend(claim)
                ? Component.translatable(key + "friend", claim.ownerName()).withStyle(ChatFormatting.GOLD)
                : Component.translatable(key + "other", claim.ownerName()).withStyle(ChatFormatting.RED);
    }

    /** Click: use a saved favorite, or save the frequency above into an empty one. Sneak-click: replace. */
    private void pressPreset(int slot) {
        if (presets == null) {
            return;
        }
        if (!presets.isEmpty(slot) && !Screen.hasShiftDown()) {
            input.setValue(Integer.toString(presets.get(slot)));
            return;
        }
        parsed().ifPresent(frequency -> setPresets(presets.with(slot, frequency)));
    }

    private void setPresets(TunerPresets newPresets) {
        presets = newPresets;
        PacketDistributor.sendToServer(new ConfigureFavoritesPayload(hand == InteractionHand.OFF_HAND, newPresets));
        updatePresetButtons();
    }

    private void updatePresetButtons() {
        if (presets == null || presetButtons[0] == null) {
            return;
        }
        int current = parsed().orElse(-1);
        for (int i = 0; i < TunerPresets.COUNT; i++) {
            Button button = presetButtons[i];
            if (presets.isEmpty(i)) {
                button.setMessage(Component.literal("+").withStyle(ChatFormatting.GRAY));
                button.setTooltip(Tooltip.create(Component.translatable(KEY + ".favorite_empty", i + 1)));
            } else {
                int frequency = presets.get(i);
                Component label = Component.literal(Integer.toString(frequency));
                button.setMessage(frequency == current ? label.copy().withStyle(ChatFormatting.GREEN) : label);
                button.setTooltip(Tooltip.create(Component.translatable(KEY + ".favorite", i + 1, FrequencyNames.label(null, frequency))));
            }
        }
    }

    /** Right-click a saved favorite to clear it. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && presets != null) {
            for (int i = 0; i < TunerPresets.COUNT; i++) {
                if (presetButtons[i] != null && presetButtons[i].isMouseOver(mouseX, mouseY) && !presets.isEmpty(i)) {
                    presetButtons[i].playDownSound(minecraft.getSoundManager());
                    setPresets(presets.cleared(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Component modeLabel() {
        String mode = Boolean.TRUE.equals(toggleMode) ? "remote_mode_toggle" : "remote_mode_pulse";
        return Component.translatable("tooltip." + SignalTower.MOD_ID + ".remote_mode",
                Component.translatable("tooltip." + SignalTower.MOD_ID + "." + mode));
    }

    private Component lengthLabel() {
        return Component.translatable(KEY + ".pulse_length", PulseLength.format(pulseTicks));
    }

    private void updateLengthButton() {
        if (lengthButton != null) {
            lengthButton.setMessage(lengthLabel());
            lengthButton.active = !Boolean.TRUE.equals(toggleMode);
        }
    }

    private Button nudgeButton(int amount, int x, int buttonWidth) {
        String label = (amount > 0 ? "+" : "") + amount;
        return Button.builder(Component.literal(label), button -> nudge(amount))
                .bounds(x, inputY, buttonWidth, 20).build();
    }

    private void nudge(int amount) {
        input.setValue(Integer.toString(Frequency.clamp((long) parsed().orElse(initialFrequency) + amount)));
    }

    private java.util.OptionalInt parsed() {
        String text = input.getValue();
        if (text.isEmpty()) {
            return java.util.OptionalInt.empty();
        }
        long value = Long.parseLong(text);
        return value >= Frequency.MIN && value <= Frequency.MAX
                ? java.util.OptionalInt.of((int) value)
                : java.util.OptionalInt.empty();
    }

    /** Opens the frequency directory; a frequency clicked there comes back into the box. */
    private void openDirectory() {
        if (minecraft != null) {
            minecraft.setScreen(new DirectoryScreen(this, parsed().orElse(-1), frequency -> picked = frequency));
        }
    }

    private void updateDoneButton() {
        if (doneButton != null) {
            doneButton.active = parsed().isPresent();
        }
    }

    private Component pagerSoundLabel() {
        return CommonComponents.optionNameValue(Component.translatable(KEY + ".pager_sound"),
                CommonComponents.optionStatus(Boolean.TRUE.equals(pagerSound)));
    }

    private Component pagerChatLabel() {
        return CommonComponents.optionNameValue(Component.translatable(KEY + ".pager_chat"),
                CommonComponents.optionStatus(pagerChatLog));
    }

    private void save() {
        parsed().ifPresent(frequency -> {
            boolean offhand = hand == InteractionHand.OFF_HAND;
            if (pagerSound != null) {
                PacketDistributor.sendToServer(new ConfigurePagerPayload(offhand, frequency, pagerSound, pagerChatLog));
            } else {
                PacketDistributor.sendToServer(new ConfigureRadioItemPayload(
                        offhand, frequency, Boolean.TRUE.equals(toggleMode), pulseTicks));
            }
            if (nameBox != null) {
                String typed = FrequencyNames.clean(nameBox.getValue());
                String current = FrequencyNames.nameOf(null, frequency);
                if (!typed.equals(current == null ? "" : current)) {
                    PacketDistributor.sendToServer(new SetFrequencyNamePayload(frequency, typed));
                }
            }
            if (friendsBox != null && privacyChanged(FrequencyClaims.clientInfo(frequency))) {
                PacketDistributor.sendToServer(new SetFrequencyPrivacyPayload(frequency, privateOn, friendsBox.getValue()));
            }
            onClose();
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (toggleMode != null && --scanTimer <= 0) {
            scanTimer = 20;
            PacketDistributor.sendToServer(new RemoteScanRequestPayload(
                    java.util.List.of(parsed().orElse(initialFrequency))));
        }
    }

    @Override
    public void onRemoteScan(RemoteScanResultPayload result) {
        if (!result.entries().isEmpty() && result.entries().get(0).frequency() == parsed().orElse(-1)) {
            scan = result.entries().get(0);
            scanReach = result.reach();
            scanTowers = result.towers();
            scanned = true;
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
        // The typed frequency's name if it has one (the tuner shows it in its name box instead), else the range.
        String name = nameBox == null && parsed().isPresent() ? FrequencyNames.nameOf(null, parsed().getAsInt()) : null;
        graphics.drawCenteredString(font, name != null
                        ? Component.literal(name).withStyle(ChatFormatting.AQUA)
                        : Component.translatable(KEY + ".range", Frequency.MIN, Frequency.MAX),
                centerX, top + 16, 0xA0A0A0);
        if (nameBox != null) {
            Component label = Component.translatable(KEY + ".name");
            graphics.drawString(font, label, centerX - 56 - font.width(label), inputY + 32, 0xFFFFFF);
            graphics.drawCenteredString(font, privacyLine(), centerX, inputY + 75, 0xFFFFFF);
        }
        if (toggleMode != null) {
            graphics.drawCenteredString(font, LocatorText.describe(scan, scanReach, scanTowers), centerX, inputY + 26, 0xFFFFFF);
            graphics.drawCenteredString(font, LocatorText.reachLine(scanned, scanReach, scanTowers), centerX, inputY + 38, 0xFFFFFF);
        }
        if (presets != null) {
            graphics.drawCenteredString(font, Component.translatable(KEY + ".favorites"), centerX, favoritesY, 0xA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
