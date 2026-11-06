package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.Set;

/**
 * Client to server, from the Groups screen: save a friend group ({@code oldName} empty: a new one;
 * otherwise the group being changed, which may be renamed to {@code newName}), or delete one.
 * {@code members} is the names typed, separated by commas or spaces.
 */
public record EditGroupPayload(String oldName, String newName, String members, boolean delete) implements CustomPacketPayload {
    public static final Type<EditGroupPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "edit_group"));

    public static final StreamCodec<ByteBuf, EditGroupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(FrequencyClaims.MAX_GROUP_NAME * 4), EditGroupPayload::oldName,
            ByteBufCodecs.stringUtf8(FrequencyClaims.MAX_GROUP_NAME * 4), EditGroupPayload::newName,
            ByteBufCodecs.stringUtf8(SetFrequencyPrivacyPayload.MAX_FRIENDS_TEXT * 4), EditGroupPayload::members,
            ByteBufCodecs.BOOL, EditGroupPayload::delete,
            EditGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. */
    public static void handle(EditGroupPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    /** A group name as it's kept: like a frequency name (no formatting codes, trimmed, 24 characters), without "#" or commas. */
    public static String cleanName(String raw) {
        return FrequencyNames.clean(raw.replace("#", "").replace(",", ""));
    }

    /**
     * Saves or deletes the player's group if they're holding a Frequency Tuner. Every private
     * frequency of theirs using the group changes with it, at once. Tells the player what
     * happened, and sends them their groups again. Returns false if nothing changed. Public for
     * the GameTests.
     */
    public static boolean apply(Player player, EditGroupPayload payload) {
        MinecraftServer server = player.getServer();
        boolean holdingTuner = player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
        if (server == null || !holdingTuner) {
            return false;
        }
        FrequencyClaims claims = FrequencyClaims.get(server);
        String key = "message." + SignalTower.MOD_ID + ".";
        FrequencyClaims.Group existing = payload.oldName().isBlank() ? null : claims.group(player.getUUID(), payload.oldName());
        if (!payload.oldName().isBlank() && existing == null) {
            return false; // it's gone already
        }

        if (payload.delete()) {
            if (existing == null) {
                return false;
            }
            Set<Integer> affected = claims.removeGroup(player.getUUID(), existing.name());
            affected.forEach(frequency -> SetFrequencyPrivacyPayload.changed(server, frequency, claims.get(frequency)));
            sendGroups(player, claims);
            tell(player, Component.translatable(key + "group_deleted", existing.name(), affected.size()).withStyle(ChatFormatting.GRAY));
            return true;
        }

        String name = cleanName(payload.newName());
        if (name.isEmpty()) {
            tell(player, Component.translatable(key + "group_name_needed").withStyle(ChatFormatting.RED));
            return false;
        }
        FrequencyClaims.Group sameName = claims.group(player.getUUID(), name);
        if (sameName != null && sameName != existing) {
            tell(player, Component.translatable(key + "group_name_taken", sameName.name()).withStyle(ChatFormatting.RED));
            return false;
        }
        if (existing == null && claims.groupsOf(player.getUUID()).size() >= FrequencyClaims.MAX_GROUPS) {
            tell(player, Component.translatable(key + "group_limit", FrequencyClaims.MAX_GROUPS).withStyle(ChatFormatting.RED));
            return false;
        }

        SetFrequencyPrivacyPayload.Resolved resolved = SetFrequencyPrivacyPayload.resolve(server, player,
                SetFrequencyPrivacyPayload.parseNames(payload.members()),
                existing == null ? Map.of() : existing.members(), FrequencyClaims.MAX_GROUP_MEMBERS);
        if (existing != null && existing.name().equals(name) && existing.members().equals(resolved.players())) {
            reportMembers(player, key, resolved);
            return false;
        }
        Set<Integer> affected = claims.putGroup(player.getUUID(), existing == null ? null : existing.name(), name, resolved.players());
        affected.forEach(frequency -> SetFrequencyPrivacyPayload.changed(server, frequency, claims.get(frequency)));
        sendGroups(player, claims);
        String message = (existing == null ? "group_created" : "group_saved") + (resolved.players().size() == 1 ? "_one" : "");
        tell(player, Component.translatable(key + message, name, resolved.players().size(), affected.size()).withStyle(ChatFormatting.GOLD));
        reportMembers(player, key, resolved);
        return true;
    }

    private static void sendGroups(Player player, FrequencyClaims claims) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new GroupsPayload(claims.groupInfo(player.getUUID())));
        }
    }

    /** In chat, so it stays after the action-bar message: names that weren't added, and why. */
    private static void reportMembers(Player player, String key, SetFrequencyPrivacyPayload.Resolved resolved) {
        if (!resolved.missing().isEmpty()) {
            player.displayClientMessage(Component.translatable(key + "group_members_missing",
                    String.join(", ", resolved.missing())).withStyle(ChatFormatting.YELLOW), false);
        }
        if (resolved.tooMany()) {
            player.displayClientMessage(Component.translatable(key + "group_members_too_many",
                    FrequencyClaims.MAX_GROUP_MEMBERS).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
