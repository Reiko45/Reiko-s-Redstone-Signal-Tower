package com.reiko.signaltower.client;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioReceiverBlockEntity;
import com.reiko.signaltower.network.ConfigureSpeakerPayload;
import com.reiko.signaltower.network.OpenSpeakerScreenPayload;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SpeakerSound;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * A Radio Speaker's screen (v0.43.0): Sound, Pitch (a note block's 25 notes), Volume and Repeat
 * (once when its frequency turns on, or every second while it's on), a Test button that plays the
 * sound just for you, and what it hears. Shift-click or scroll goes back through a button's values.
 * Done or Enter saves, Escape cancels.
 */
public class SpeakerScreen extends Screen {
    private static final String KEY = "screen." + SignalTower.MOD_ID + ".speaker.";

    private final OpenSpeakerScreenPayload state;
    private SpeakerSound sound;
    private int note;
    private SpeakerSound.Volume volume;
    private boolean repeat;
    private int top;
    /** Ticks until the test doorbell's second note (-1: none). */
    private int testSecondNote = -1;

    public SpeakerScreen(OpenSpeakerScreenPayload state) {
        super(Component.translatable("block." + SignalTower.MOD_ID + ".radio_speaker"));
        this.state = state;
        int packed = state.speaker();
        this.sound = SpeakerSound.byId(packed & 15);
        this.volume = SpeakerSound.Volume.byId(packed >> 4 & 3);
        this.repeat = (packed >> 6 & 1) == 1;
        this.note = SpeakerSound.validNote(packed >> 7);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        top = height / 2 - 82;
        addRenderableWidget(CycleButton.<SpeakerSound>builder(SpeakerSound::label)
                .withValues(SpeakerSound.values())
                .withInitialValue(sound)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "sound_tip")))
                .create(centerX - 100, top + 30, 200, 20, Component.translatable(KEY + "sound"), (button, value) -> sound = value));
        addRenderableWidget(CycleButton.<Integer>builder(SpeakerSound::noteLabel)
                .withValues(IntStream.rangeClosed(0, 24).boxed().toList())
                .withInitialValue(note)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "pitch_tip")))
                .create(centerX - 100, top + 54, 98, 20, Component.translatable(KEY + "pitch"), (button, value) -> note = value));
        addRenderableWidget(CycleButton.<SpeakerSound.Volume>builder(SpeakerSound.Volume::label)
                .withValues(Arrays.asList(SpeakerSound.Volume.values()))
                .withInitialValue(volume)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "volume_tip")))
                .create(centerX + 2, top + 54, 98, 20, Component.translatable(KEY + "volume"), (button, value) -> volume = value));
        addRenderableWidget(CycleButton.<Boolean>builder(value -> Component.translatable(KEY + (value ? "repeat_on" : "repeat_off")))
                .withValues(false, true)
                .withInitialValue(repeat)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "repeat_tip")))
                .create(centerX - 100, top + 78, 200, 20, Component.translatable(KEY + "repeat"), (button, value) -> repeat = value));
        addRenderableWidget(Button.builder(Component.translatable(KEY + "test"), b -> test())
                .tooltip(Tooltip.create(Component.translatable(KEY + "test_tip")))
                .bounds(centerX - 100, top + 102, 200, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 102, top + 146, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 2, top + 146, 100, 20).build());
    }

    /** Plays the picked sound for this player only (the doorbell's second note follows on a later tick). */
    private void test() {
        playLocally(sound == SpeakerSound.DOORBELL);
        testSecondNote = sound == SpeakerSound.DOORBELL ? SpeakerSound.DOORBELL_GAP : -1;
    }

    private void playLocally(boolean high) {
        if (minecraft != null) {
            float pitch = SpeakerSound.pitch(note) * (high ? 1.26F : 1.0F);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.sound(), pitch, Math.min(1.0F, volume.volume())));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (testSecondNote > 0 && --testSecondNote == 0) {
            testSecondNote = -1;
            playLocally(false);
        }
    }

    private void save() {
        PacketDistributor.sendToServer(new ConfigureSpeakerPayload(state.pos(), RadioReceiverBlockEntity.packSpeaker(sound, note, volume, repeat)));
        onClose();
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
        Object frequency = FrequencyNames.label(null, state.frequency());
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "frequency", frequency), centerX, top + 14, 0xA0A0A0);
        Component status;
        if (state.deaf()) {
            FrequencyClaims.ClaimInfo claim = FrequencyClaims.clientInfo(state.frequency());
            status = Component.translatable(KEY + "deaf", frequency, claim != null ? claim.ownerName() : "?").withStyle(ChatFormatting.RED);
        } else {
            status = state.heard() > 0 ? Component.translatable(KEY + "hearing", state.heard()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(KEY + "hearing_nothing").withStyle(ChatFormatting.GRAY);
        }
        graphics.drawCenteredString(font, status, centerX, top + 128, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
