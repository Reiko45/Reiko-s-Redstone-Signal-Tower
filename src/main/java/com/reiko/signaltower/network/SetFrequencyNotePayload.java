package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.FrequencyNotes;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the note saved in the Frequency Tuner's Note screen for a frequency ("" removes
 * it), and whether it's kept to the frequency's members (only on a private frequency).
 */
public record SetFrequencyNotePayload(int frequency, String note, boolean membersOnly) implements CustomPacketPayload {
    public static final Type<SetFrequencyNotePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "set_frequency_note"));

    public static final StreamCodec<ByteBuf, SetFrequencyNotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFrequencyNotePayload::frequency,
            ByteBufCodecs.stringUtf8(FrequencyNotes.MAX_LENGTH * 4), SetFrequencyNotePayload::note,
            ByteBufCodecs.BOOL, SetFrequencyNotePayload::membersOnly,
            SetFrequencyNotePayload::new);

    /** Open to everyone. */
    public SetFrequencyNotePayload(int frequency, String note) {
        this(frequency, note, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(SetFrequencyNotePayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /**
     * Sets the frequency's note if the player is holding a Frequency Tuner (and, on a private
     * frequency, is its owner, a member or an operator, the same rule as for names), tells every
     * player on the server who may see it (and takes it away from those who may not, if it's now
     * kept to members), and tells this player what happened. Returns false if nothing changed.
     * Public for the GameTests.
     */
    public static boolean apply(Player player, SetFrequencyNotePayload payload) {
        MinecraftServer server = player.getServer();
        boolean holdingTuner = player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
        if (server == null || !holdingTuner) {
            return false;
        }
        int frequency = Frequency.clamp(payload.frequency());
        String key = "message." + SignalTower.MOD_ID + ".";
        FrequencyClaims.Claim claim = FrequencyClaims.get(server).get(frequency);
        if (claim != null && !claim.allows(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable(key + "private_refused_note",
                    claim.ownerName(), FrequencyNames.label(player.level(), frequency)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Only a private frequency's note can be kept to its members.
        boolean keep = payload.membersOnly() && claim != null;
        FrequencyNotes notes = FrequencyNotes.get(server);
        String before = notes.get(frequency);
        boolean keptBefore = notes.isMembersOnly(frequency);
        String stored = notes.set(frequency, payload.note(), keep);
        if (stored == null) {
            player.displayClientMessage(Component.translatable(key + "note_full", FrequencyNotes.MAX_NOTES)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (stored.equals(before == null ? "" : before) && notes.isMembersOnly(frequency) == keptBefore) {
            return false;
        }
        FrequencyNotes.sendToAll(server, frequency); // each player as they may see it
        Object label = FrequencyNames.label(player.level(), frequency);
        player.displayClientMessage(stored.isEmpty()
                ? Component.translatable(key + "note_removed", label).withStyle(ChatFormatting.GRAY)
                : Component.translatable(key + (keep ? "note_set_members" : "note_set"), label,
                        Component.literal(stored).withStyle(ChatFormatting.ITALIC)), true);
        return true;
    }
}
