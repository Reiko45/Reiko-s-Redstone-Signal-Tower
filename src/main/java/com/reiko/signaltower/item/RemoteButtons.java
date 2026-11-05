package com.reiko.signaltower.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.signaltower.radio.Frequency;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings stored on a Multi-Button Remote: always exactly {@link #COUNT} buttons, each with its
 * own frequency and mode, plus which one a plain right-click presses.
 */
public record RemoteButtons(List<Button> buttons, int selected) {
    public static final int COUNT = 4;

    /** One button: a frequency, and whether it toggles (true) or pulses (false). */
    public record Button(int frequency, boolean toggle) {
        public static final Codec<Button> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(Frequency.MIN, Frequency.MAX).fieldOf("frequency").forGetter(Button::frequency),
                Codec.BOOL.optionalFieldOf("toggle", false).forGetter(Button::toggle)
        ).apply(instance, Button::new));

        public static final StreamCodec<ByteBuf, Button> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Button::frequency,
                ByteBufCodecs.BOOL, Button::toggle,
                Button::new);

        public Button {
            frequency = Frequency.clamp(frequency);
        }
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
