package com.reiko.signaltower.network;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioConverterBlock;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.item.MultiRemoteItem;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.item.RemoteControlItem;
import com.reiko.signaltower.item.SignalScannerItem;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
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
import java.util.Optional;
import java.util.TreeSet;

/**
 * Sent about once a second by an open frequency directory: "which of the world's named and
 * private frequencies are on right now?" The server answers with {@link DirectoryResultPayload}.
 * {@code converter} is the converter whose screen the directory was opened from, if it was (the
 * player may have nothing in hand then).
 */
public record DirectoryRequestPayload(Optional<BlockPos> converter) implements CustomPacketPayload {
    public static final DirectoryRequestPayload FROM_ITEM = new DirectoryRequestPayload(Optional.empty());
    public static final Type<DirectoryRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "directory_request"));
    public static final StreamCodec<ByteBuf, DirectoryRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), DirectoryRequestPayload::converter,
            DirectoryRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server's main thread. Only answers players who may ask ({@link #mayAsk}). */
    public static void handle(DirectoryRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && mayAsk(player, payload)) {
            PacketDistributor.sendToPlayer(player, new DirectoryResultPayload(entriesFor(player)));
        }
    }

    /**
     * True if the player holds a radio item (the directory opens from their screens), or is within
     * reach of the converter whose screen they opened it from.
     */
    public static boolean mayAsk(Player player, DirectoryRequestPayload payload) {
        if (holdsRadioItem(player)) {
            return true;
        }
        return payload.converter().filter(pos -> player.canInteractWithBlock(pos, 1.0)
                && player.level().getBlockState(pos).getBlock() instanceof RadioConverterBlock).isPresent();
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
     * where the player stands (anywhere = -1), so the directory can't be used to watch it from afar;
     * someone else's encrypted frequency not even that (here = -1 too). Public for the GameTests.
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
            int here = claim == null || claim.hears(player.getUUID())
                    ? network.listen(level, frequency, player.blockPosition()).power() : -1;
            entries.add(new DirectoryResultPayload.Entry(frequency, anywhere, here));
        }
        return entries;
    }
}
