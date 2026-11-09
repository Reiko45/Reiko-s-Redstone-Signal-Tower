package com.reiko.signaltower.advancement;

import com.reiko.signaltower.SignalTower;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * v0.56.0: the mod's advancements. Most are plain data (finding things, standing on a platform,
 * trading with a Radio Operator); these events come from the towers themselves through the
 * {@link TowerTrigger}:
 * <ul>
 * <li>{@link #ON_AIR}: a transmitter is sending (value: its reach), for whoever tuned it.</li>
 * <li>{@link #FOUND_TOWER_REVIVED}: an old tower found in the world that had gone quiet sends again,
 * for everyone near it.</li>
 * <li>{@link #STORM_SURVIVED}: a tall tower braced with guy wires stands through a tornado, hurricane
 * or derecho (Reiko's Weather), for whoever tuned it and everyone near it.</li>
 * </ul>
 */
public final class TowerAdvancements {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, SignalTower.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, TowerTrigger> TOWER = TRIGGERS.register("tower", TowerTrigger::new);

    public static final String ON_AIR = "on_air";
    public static final String FOUND_TOWER_REVIVED = "found_tower_revived";
    public static final String STORM_SURVIVED = "storm_survived";

    /** How near an old tower you have to be when it comes back on the air. */
    public static final double REVIVED_RADIUS = 24;
    /** How near a tower you have to be to see it stand through a storm. */
    public static final double STORM_RADIUS = 32;

    /** One event, as the GameTests see it (test players can't earn advancements). */
    public record Fired(String event, int value, BlockPos pos, @Nullable UUID tuner, double radius) {
    }

    private static @Nullable List<Fired> firedForTests;

    private TowerAdvancements() {
    }

    public static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }

    /** GameTests: from now on, keep every event fired (on the server thread). */
    public static List<Fired> recordForTests() {
        if (firedForTests == null) {
            firedForTests = new ArrayList<>();
        }
        return firedForTests;
    }

    /**
     * Fires {@code event} at {@code pos}: for {@code tuner} if they're online (anywhere), and for every
     * player within {@code radius} blocks of it (0: nobody else). Each player once.
     */
    public static void fire(ServerLevel level, BlockPos pos, String event, int value, @Nullable UUID tuner, double radius) {
        if (firedForTests != null) {
            firedForTests.add(new Fired(event, value, pos.immutable(), tuner, radius));
        }
        List<ServerPlayer> players = new ArrayList<>(2);
        if (tuner != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(tuner);
            if (player != null) {
                players.add(player);
            }
        }
        if (radius > 0) {
            Vec3 centre = Vec3.atCenterOf(pos);
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.distanceToSqr(centre) <= radius * radius && !players.contains(player)) {
                    players.add(player);
                }
            }
        }
        for (ServerPlayer player : players) {
            TOWER.get().trigger(player, event, value);
        }
    }
}
