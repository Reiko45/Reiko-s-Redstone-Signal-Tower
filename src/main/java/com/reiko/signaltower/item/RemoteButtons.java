package com.reiko.signaltower.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings stored on a Multi-Button Remote: always exactly {@link #COUNT} buttons, each with its
 * own frequency and mode, plus which one a plain right-click presses.
 */
public record RemoteButtons(List<Button> buttons, int selected) {
    public static final int COUNT = 4;

    /** Longest button name. */
    public static final int MAX_NAME_LENGTH = 24;

    /**
     * One button: a frequency, whether it toggles (true) or pulses (false), how long its pulse lasts
     * in ticks, and an optional name ("" means unnamed, shown as "Button 1" etc).
     */
    public record Button(int frequency, boolean toggle, int pulseTicks, String name) {
        public static final Codec<Button> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(Frequency.MIN, Frequency.MAX).fieldOf("frequency").forGetter(Button::frequency),
                Codec.BOOL.optionalFieldOf("toggle", false).forGetter(Button::toggle),
                Codec.INT.optionalFieldOf("pulse_ticks", PulseLength.DEFAULT_TICKS).forGetter(Button::pulseTicks),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Button::name)
        ).apply(instance, Button::new));

        public static final StreamCodec<ByteBuf, Button> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Button::frequency,
                ByteBufCodecs.BOOL, Button::toggle,
                ByteBufCodecs.VAR_INT, Button::pulseTicks,
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), Button::name,
                Button::new);

        public Button {
            frequency = Frequency.clamp(frequency);
            pulseTicks = PulseLength.clamp(pulseTicks);
            name = cleanName(name);
        }

        /** A button with the default 1-second pulse and no name. */
        public Button(int frequency, boolean toggle) {
            this(frequency, toggle, PulseLength.DEFAULT_TICKS, "");
        }

        /** An unnamed button. */
        public Button(int frequency, boolean toggle, int pulseTicks) {
            this(frequency, toggle, pulseTicks, "");
        }

        public Button withFrequency(int newFrequency) {
            return new Button(newFrequency, toggle, pulseTicks, name);
        }

        public Button withToggle(boolean newToggle) {
            return new Button(frequency, newToggle, pulseTicks, name);
        }

        public Button withPulseTicks(int newPulseTicks) {
            return new Button(frequency, toggle, newPulseTicks, name);
        }

        public Button withName(String newName) {
            return new Button(frequency, toggle, pulseTicks, newName);
        }

        /** The button's name, or "Button N" if it has none. {@code index} is 0-3. */
        public Component label(int index) {
            return name.isEmpty()
                    ? Component.translatable("tooltip." + SignalTower.MOD_ID + ".remote_button_default_name", index + 1)
                    : Component.literal(name);
        }
    }

    /**
     * Keeps names safe to show anywhere: no colour codes or control characters, no leading or
     * trailing spaces, and at most {@link #MAX_NAME_LENGTH} characters.
     */
    public static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String clean = StringUtil.filterText(name).strip();
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH).strip() : clean;
    }

    public static final Codec<RemoteButtons> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Button.CODEC.listOf().fieldOf("buttons").forGetter(RemoteButtons::buttons),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(RemoteButtons::selected)
    ).apply(instance, RemoteButtons::new));

    public static final StreamCodec<ByteBuf, RemoteButtons> STREAM_CODEC = StreamCodec.composite(
            Button.STREAM_CODEC.apply(ByteBufCodecs.list(COUNT)), RemoteButtons::buttons,
            ByteBufCodecs.VAR_INT, RemoteButtons::selected,
            RemoteButtons::new);

    /** A new remote: buttons on frequencies 1-4, all pulse, button 1 selected. */
    public static final RemoteButtons DEFAULT = new RemoteButtons(
            List.of(new Button(1, false), new Button(2, false), new Button(3, false), new Button(4, false)), 0);

    /** Always keeps exactly COUNT buttons and a valid selection, whatever the input. */
    public RemoteButtons {
        List<Button> fixed = new ArrayList<>(buttons.subList(0, Math.min(buttons.size(), COUNT)));
        while (fixed.size() < COUNT) {
            fixed.add(new Button(fixed.size() + 1, false));
        }
        buttons = List.copyOf(fixed);
        selected = Math.floorMod(selected, COUNT);
    }

    public Button button(int index) {
        return buttons.get(Math.floorMod(index, COUNT));
    }

    public Button selectedButton() {
        return buttons.get(selected);
    }

    public RemoteButtons withSelected(int index) {
        return new RemoteButtons(buttons, index);
    }

    public RemoteButtons withButton(int index, Button button) {
        List<Button> copy = new ArrayList<>(buttons);
        copy.set(Math.floorMod(index, COUNT), button);
        return new RemoteButtons(copy, selected);
    }
}
