package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to one client: that player's friend groups (when they join, and whenever one changes). */
public record GroupsPayload(List<FrequencyClaims.GroupInfo> groups) implements CustomPacketPayload {
    public static final Type<GroupsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "groups"));

    public static final StreamCodec<ByteBuf, GroupsPayload> STREAM_CODEC = StreamCodec.composite(
            FrequencyClaims.GroupInfo.STREAM_CODEC.apply(ByteBufCodecs.list(FrequencyClaims.MAX_GROUPS)), GroupsPayload::groups,
            GroupsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client's main thread. */
    public static void handle(GroupsPayload payload, IPayloadContext context) {
        FrequencyClaims.clientSetGroups(payload.groups());
    }
}
