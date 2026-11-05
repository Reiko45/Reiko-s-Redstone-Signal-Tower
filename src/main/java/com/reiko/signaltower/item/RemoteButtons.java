package com.reiko.signaltower.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.Frequency;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The settings stored on a Multi-Button Remote: always exactly {@link #COUNT} buttons, each with its
 * own frequency, mode, name and color, plus which one a plain right-click presses.
 */
public record RemoteButtons(List<Button> buttons, int selected) {
    public static final int COUNT = 4;

    /** Longest button name. */
    public static final int MAX_NAME_LENGTH = 24;

    /** Colors buttons get when none was chosen: red, green, blue and yellow, like the old item texture. */
    private static final DyeColor[] DEFAULT_COLORS = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE, DyeColor.YELLOW};

    /** The color button {@code index} (0-3) has until someone picks another. */
    public static DyeColor defaultColor(int index) {
        return DEFAULT_COLORS[Math.floorMod(index, COUNT)];
    }

    /**
     * One button: a frequency, whether it toggles (true) or pulses (false), how long its pulse lasts
     * in ticks, an optional name ("" means unnamed, shown as "Button 1" etc), and a dye color. The
     * color is null only on a button that isn't on a remote yet; {@link RemoteButtons} fills in its
     * slot's default.
     */
    public record Button(int frequency, boolean toggle, int pulseTicks, String name, @Nullable DyeColor color) {
        public static final Codec<Button> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(Frequency.MIN, Frequency.MAX).fieldOf("frequency").forGetter(Button::frequency),
                Codec.BOOL.optionalFieldOf("toggle", false).forGetter(Button::toggle),
                Codec.INT.optionalFieldOf("pulse_ticks", PulseLength.DEFAULT_TICKS).forGetter(Button::pulseTicks),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Button::name),
                DyeColor.CODEC.optionalFieldOf("color").forGetter(button -> Optional.ofNullable(button.color()))
        ).apply(instance, (frequency, toggle, pulseTicks, name, color) ->
                new Button(frequency, toggle, pulseTicks, name, color.orElse(null))));

        private static final StreamCodec<ByteBuf, DyeColor> OPTIONAL_COLOR =
                ByteBufCodecs.optional(DyeColor.STREAM_CODEC).map(color -> color.orElse(null), Optional::ofNullable);

        public static final StreamCodec<ByteBuf, Button> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Button::frequency,
                ByteBufCodecs.BOOL, Button::toggle,
                ByteBufCodecs.VAR_INT, Button::pulseTicks,
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), Button::name,
                OPTIONAL_COLOR, Button::color,
                Button::new);

        public Button {
            frequency = Frequency.clamp(frequency);
            pulseTicks = PulseLength.clamp(pulseTicks);
            name = cleanName(name);
        }

        /** A button with the default 1-second pulse, no name and its slot's default color. */
        public Button(int frequency, boolean toggle) {
            this(frequency, toggle, PulseLength.DEFAULT_TICKS, "", null);
        }

        /** An unnamed button with its slot's default color. */
        public Button(int frequency, boolean toggle, int pulseTicks) {
            this(frequency, toggle, pulseTicks, "", null);
        }

        /** A button with its slot's default color. */
        public Button(int frequency, boolean toggle, int pulseTicks, String name) {
            this(frequency, toggle, pulseTicks, name, null);
        }

        public Button withFrequency(int newFrequency) {
            return new Button(newFrequency, toggle, pulseTicks, name, color);
        }

        public Button withToggle(boolean newToggle) {
            return new Button(frequency, newToggle, pulseTicks, name, color);
        }

        public Button withPulseTicks(int newPulseTicks) {
            return new Button(frequency, toggle, newPulseTicks, name, color);
        }

        public Button withName(String newName) {
            return new Button(frequency, toggle, pulseTicks, newName, color);
        }

        public Button withColor(@Nullable DyeColor newColor) {
            return new Button(frequency, toggle, pulseTicks, name, newColor);
        }

        /** This button's color, or the default for slot {@code index} (0-3) if it has none. */
        public DyeColor colorOr(int index) {
            return color != null ? color : defaultColor(index);
        }

        /** The button's name, or "Button N" if it has none, in the button's color. {@code index} is 0-3. */
        public MutableComponent label(int index) {
            MutableComponent text = name.isEmpty()
                    ? Component.translatable("tooltip." + SignalTower.MOD_ID + ".remote_button_default_name", index + 1)
                    : Component.literal(name);
            return text.withStyle(style -> style.withColor(TextColor.fromRgb(textColor(colorOr(index)))));
        }
    }

    /**
     * A dye's color for text on Minecraft's dark tooltips and chat: the dye's own color, lightened
     * just enough that dark ones (black, gray, blue...) stay readable.
     */
    public static int textColor(DyeColor dye) {
        int rgb = dye.getTextureDiffuseColor() & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        double wanted = 0.4;
        if (brightness >= wanted) {
            return rgb;
        }
        double t = (wanted - brightness) / (1.0 - brightness); // how far toward white
        r = (int) Math.round(r + (255 - r) * t);
        g = (int) Math.round(g + (255 - g) * t);
        b = (int) Math.round(b + (255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** The next (or, {@code backwards}, previous) of the 16 dye colors, in creative-menu order. */
    public static DyeColor nextColor(DyeColor color, boolean backwards) {
        int count = DyeColor.values().length;
        return DyeColor.byId(Math.floorMod(color.getId() + (backwards ? -1 : 1), count));
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

    /**
     * Always keeps exactly COUNT buttons, each with a color (its slot's default if it had none), and
     * a valid selection, whatever the input.
     */
    public RemoteButtons {
        List<Button> fixed = new ArrayList<>(buttons.subList(0, Math.min(buttons.size(), COUNT)));
        while (fixed.size() < COUNT) {
            fixed.add(new Button(fixed.size() + 1, false));
        }
        for (int i = 0; i < COUNT; i++) {
            if (fixed.get(i).color() == null) {
                fixed.set(i, fixed.get(i).withColor(defaultColor(i)));
            }
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
