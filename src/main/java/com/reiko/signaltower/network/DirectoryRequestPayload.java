package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Sent about once a second by an open frequency directory: "which of the world's named and
 * private frequencies are on right now?" The server answers with {@link DirectoryResultPayload}.
 */
public record DirectoryRequestPayload() implements CustomPacketPayload {
    public static final DirectoryRequestPayload INSTANCE = new DirectoryRequestPayload();
    public static final Type<DirectoryRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "directory_request"));
    public static final StreamCodec<ByteBuf, DirectoryRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. Only answers players holding a radio item (the directory opens from their screens). */
    public static void handle(DirectoryRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && holdsRadioItem(player)) {
            PacketDistributor.sendToPlayer(player, new DirectoryResultPayload(entriesFor(player)));
        }
    }

    public static boolean holdsRadioItem(Player player) {
        return isRadioItem(player.getMainHandItem().getItem()) || isRadioItem(player.getOffhandItem().getItem());
    }

    private static boolean isRadioItem(Item item) {
        return item instanceof FrequencyTunerItem || item instanceof RemoteControlItem || item instanceof MultiRemoteItem
                || item instanceof PagerItem || item instanceof SignalScannerItem;
    }

    /**
     * Every named or private frequency, in order, with how strong it is anywhere in the player's
     * dimension and where they stand. Someone else's private frequency only says whether it's heard
     * where the player stands (anywhere = -1), so the directory can't be used to watch it from afar.
     * Public for the GameTests.
     */
    public static List<DirectoryResultPayload.Entry> entriesFor(Player player) {
        List<DirectoryResultPayload.Entry> entries = new ArrayList<>();
        MinecraftServer server = player.getServer();
        if (server == null || !(player.level() instanceof ServerLevel level)) {
            return entries;
        }
        FrequencyClaims claims = FrequencyClaims.get(server);
        TreeSet<Integer> frequencies = new TreeSet<>(FrequencyNames.get(server).all().keySet());
        frequencies.addAll(claims.allInfo().keySet());
        SignalNetwork network = SignalNetwork.get(level);
        for (int frequency : frequencies) {
            if (entries.size() >= DirectoryResultPayload.MAX_ENTRIES) {
                break;
            }
            FrequencyClaims.Claim claim = claims.get(frequency);
            boolean member = claim == null || claim.allows(player.getUUID());
            int anywhere = member ? network.strongestAnywhere(level, frequency) : -1;
            int here = network.listen(level, frequency, player.blockPosition()).power();
            entries.add(new DirectoryResultPayload.Entry(frequency, anywhere, here));
        }
        return entries;
    }
}
