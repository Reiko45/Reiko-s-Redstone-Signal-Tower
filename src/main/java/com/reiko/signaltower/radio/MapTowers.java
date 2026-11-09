package com.reiko.signaltower.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.network.MapWaypointsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Map waypoints (v0.61.0), the server's half: which towers each player's JourneyMap or Xaero's map
 * shows. Their own full-size towers (from every dimension's network, by who tuned them), and the old
 * towers and village radio stations they've been near ({@link #DISCOVERED_RADIUS} blocks), which
 * they keep (a player attachment, kept through death). Every {@link #CHECK_TICKS} the list is worked
 * out again and sent if it changed; the client does the drawing, only if one of those mods is there.
 */
public final class MapTowers {
    public static final int CHECK_TICKS = 100;
    /** How close a player has to come to an old tower or village station to put it on their map. */
    public static final int DISCOVERED_RADIUS = 96;
    /** The most places one player keeps. */
    public static final int MAX_DISCOVERED = 256;

    /** A place a player has been near: an old tower (3) or a village station (4), as it was then. */
    public record Place(ResourceKey<Level> dimension, BlockPos pos, int kind, int frequency) {
        public static final Codec<Place> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Place::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Place::pos),
                Codec.INT.fieldOf("kind").forGetter(Place::kind),
                Codec.INT.fieldOf("frequency").forGetter(Place::frequency)
        ).apply(instance, Place::new));
    }

    /** The places a player has been near (saved with the player). */
    public record Discovered(List<Place> places) {
        public static final Codec<Discovered> CODEC = Place.CODEC.listOf().xmap(Discovered::new, Discovered::places);
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SignalTower.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Discovered>> DISCOVERED = ATTACHMENTS.register("discovered_towers",
            () -> AttachmentType.builder(() -> new Discovered(List.of())).serialize(Discovered.CODEC).copyOnDeath().build());

    /** What each player was last sent (not saved: everyone gets theirs again after a restart). */
    private static final Map<UUID, List<MapWaypointsPayload.Entry>> SENT = new HashMap<>();

    private MapTowers() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** The old tower or village station at {@code pos} in {@code level} checks for players nearby (from its block entity). */
    public static void nearFoundTower(ServerLevel level, BlockPos pos, boolean village, int frequency) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(pos.getCenter()) <= (double) DISCOVERED_RADIUS * DISCOVERED_RADIUS) {
                discover(player, new Place(level.dimension(), pos.immutable(), village ? MapWaypointsPayload.VILLAGE_STATION
                        : MapWaypointsPayload.OLD_TOWER, frequency));
            }
        }
    }

    /** Adds {@code place} to what {@code player} has found (or updates its frequency). True if anything changed. */
    public static boolean discover(net.minecraft.world.entity.player.Player player, Place place) {
        List<Place> places = new ArrayList<>(player.getData(DISCOVERED).places());
        for (int i = 0; i < places.size(); i++) {
            Place known = places.get(i);
            if (known.dimension().equals(place.dimension()) && known.pos().equals(place.pos())) {
                if (known.equals(place)) {
                    return false;
                }
                places.set(i, place);
                player.setData(DISCOVERED, new Discovered(List.copyOf(places)));
                return true;
            }
        }
        places.add(place);
        while (places.size() > MAX_DISCOVERED) {
            places.remove(0);
        }
        player.setData(DISCOVERED, new Discovered(List.copyOf(places)));
        return true;
    }

    /** What {@code player}'s map should show now. */
    public static List<MapWaypointsPayload.Entry> entriesFor(ServerPlayer player) {
        return entriesFor(player.server, player.getUUID(), player.getData(DISCOVERED).places());
    }

    /** What the map of the player {@code uuid}, who has been near {@code places}, should show now. */
    public static List<MapWaypointsPayload.Entry> entriesFor(MinecraftServer server, UUID uuid, List<Place> places) {
        List<MapWaypointsPayload.Entry> entries = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            SignalNetwork network = SignalNetwork.get(level);
            ResourceLocation dimension = level.dimension().location();
            for (SignalNetwork.OwnTower tower : network.towersTunedBy(uuid)) {
                if (entries.size() >= MapWaypointsPayload.MAX_ENTRIES) break;
                entries.add(new MapWaypointsPayload.Entry(dimension, tower.pos(), tower.kind(), tower.frequency(), tower.reach()));
            }
        }
        for (Place place : places) {
            if (entries.size() >= MapWaypointsPayload.MAX_ENTRIES) break;
            ServerLevel level = server.getLevel(place.dimension());
            if (level == null) continue;
            // An old tower can be re-tuned; the network knows its frequency now if it's been loaded since.
            OptionalInt now = SignalNetwork.get(level).transmitterFrequency(place.pos());
            entries.add(new MapWaypointsPayload.Entry(place.dimension().location(), place.pos(), place.kind(),
                    now.orElse(place.frequency()), 0));
        }
        return entries;
    }

    /** Every {@link #CHECK_TICKS}: sends each player their list if it changed. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<MapWaypointsPayload.Entry> entries = entriesFor(player);
            if (!entries.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), entries);
                PacketDistributor.sendToPlayer(player, new MapWaypointsPayload(entries));
            }
        }
    }

    /** {@code player} logged in (or out): they get their list again at the next check. */
    public static void forget(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }
}
