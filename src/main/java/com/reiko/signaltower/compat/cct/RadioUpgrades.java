package com.reiko.signaltower.compat.cct;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.registry.ModRegistry;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.AbstractPocketUpgrade;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.turtle.AbstractTurtleUpgrade;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * v0.59.0: the radio upgrade for turtles and pocket computers. A Radio Pager crafted with a turtle
 * or pocket computer makes a "Radio Turtle" or "Radio Pocket Computer" with a "radio" peripheral;
 * the pager's frequency goes with it (and comes back out on the pager). The upgrades themselves
 * are data: {@code data/reikos_signal_tower/computercraft/turtle_upgrade/radio.json} and
 * {@code .../pocket_upgrade/radio.json}.
 */
public final class RadioUpgrades {
    public static final String ADJECTIVE = "upgrade." + SignalTower.MOD_ID + ".radio.adjective";

    private static final DeferredRegister<UpgradeType<? extends ITurtleUpgrade>> TURTLE_TYPES =
            DeferredRegister.create(ITurtleUpgrade.typeRegistry(), SignalTower.MOD_ID);
    private static final DeferredRegister<UpgradeType<? extends IPocketUpgrade>> POCKET_TYPES =
            DeferredRegister.create(IPocketUpgrade.typeRegistry(), SignalTower.MOD_ID);

    public static final DeferredHolder<UpgradeType<? extends ITurtleUpgrade>, UpgradeType<TurtleRadio>> TURTLE =
            TURTLE_TYPES.register("radio", () -> UpgradeType.simpleWithCustomItem(TurtleRadio::new));
    public static final DeferredHolder<UpgradeType<? extends IPocketUpgrade>, UpgradeType<PocketRadio>> POCKET =
            POCKET_TYPES.register("radio", () -> UpgradeType.simpleWithCustomItem(PocketRadio::new));

    private RadioUpgrades() {
    }

    static void register(IEventBus modEventBus) {
        TURTLE_TYPES.register(modEventBus);
        POCKET_TYPES.register(modEventBus);
    }

    /** Any Radio Pager fits (whatever its frequency); its frequency is what's kept. */
    static boolean suitable(ItemStack stack) {
        return stack.is(ModRegistry.PAGER.get());
    }

    static DataComponentPatch dataOf(ItemStack stack) {
        return MobileRadioPeripheral.withFrequency(DataComponentPatch.EMPTY, stack.getOrDefault(ModRegistry.FREQUENCY.get(),
                com.reiko.signaltower.radio.Frequency.DEFAULT));
    }

    static ItemStack pagerWith(DataComponentPatch data) {
        ItemStack pager = new ItemStack(ModRegistry.PAGER.get());
        pager.applyComponents(data);
        return pager;
    }

    /** The turtle upgrade (a peripheral on its left or right). */
    public static final class TurtleRadio extends AbstractTurtleUpgrade {
        public TurtleRadio(ItemStack item) {
            super(TurtleUpgradeType.PERIPHERAL, ADJECTIVE, item);
        }

        @Override
        public UpgradeType<TurtleRadio> getType() {
            return TURTLE.get();
        }

        @Override
        public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
            return new MobileRadioPeripheral(new TurtleHost(turtle, side));
        }

        @Override
        public void update(ITurtleAccess turtle, TurtleSide side) {
            if (turtle.getLevel() instanceof ServerLevel && turtle.getPeripheral(side) instanceof MobileRadioPeripheral radio) {
                radio.tick();
            }
        }

        @Override
        public boolean isItemSuitable(ItemStack stack) {
            return suitable(stack);
        }

        @Override
        public DataComponentPatch getUpgradeData(ItemStack stack) {
            return dataOf(stack);
        }

        @Override
        public ItemStack getUpgradeItem(DataComponentPatch data) {
            return pagerWith(data);
        }
    }

    /** The pocket computer upgrade (its "back" peripheral). Its light is green while it hears its frequency. */
    public static final class PocketRadio extends AbstractPocketUpgrade {
        /** The pocket computer's light while it hears its frequency (the pager's screen green). */
        public static final int LIGHT = 0x55E055;

        public PocketRadio(ItemStack item) {
            super(ADJECTIVE, item);
        }

        @Override
        public UpgradeType<PocketRadio> getType() {
            return POCKET.get();
        }

        @Override
        public IPeripheral createPeripheral(IPocketAccess access) {
            return new MobileRadioPeripheral(new PocketHost(access));
        }

        @Override
        public void update(IPocketAccess access, @Nullable IPeripheral peripheral) {
            if (peripheral instanceof MobileRadioPeripheral radio) {
                int heard = radio.tick();
                int light = heard > 0 ? LIGHT : -1;
                if (access.getLight() != light) {
                    access.setLight(light);
                }
            }
        }

        @Override
        public boolean isItemSuitable(ItemStack stack) {
            return suitable(stack);
        }

        @Override
        public DataComponentPatch getUpgradeData(ItemStack stack) {
            return dataOf(stack);
        }

        @Override
        public ItemStack getUpgradeItem(DataComponentPatch data) {
            return pagerWith(data);
        }
    }

    record TurtleHost(ITurtleAccess turtle, TurtleSide side) implements MobileRadioPeripheral.Host {
        @Override
        public @Nullable ServerLevel level() {
            return turtle.isRemoved() ? null : turtle.getLevel() instanceof ServerLevel level ? level : null;
        }

        @Override
        public BlockPos position() {
            return turtle.getPosition();
        }

        @Override
        public @Nullable UUID owner() {
            var profile = turtle.getOwningPlayer();
            return profile == null ? null : profile.getId();
        }

        @Override
        public DataComponentPatch data() {
            return turtle.getUpgradeData(side);
        }

        @Override
        public void setData(DataComponentPatch data) {
            turtle.setUpgradeData(side, data);
        }

        @Override
        public String nameKey() {
            return "computer." + SignalTower.MOD_ID + ".turtle";
        }

        @Override
        public boolean sameAs(MobileRadioPeripheral.Host other) {
            return other instanceof TurtleHost host && host.turtle == turtle && host.side == side;
        }
    }

    record PocketHost(IPocketAccess access) implements MobileRadioPeripheral.Host {
        @Override
        public @Nullable ServerLevel level() {
            return access.getLevel();
        }

        @Override
        public BlockPos position() {
            return BlockPos.containing(access.getPosition());
        }

        @Override
        public @Nullable UUID owner() {
            return access.getEntity() instanceof Player player ? player.getUUID() : null;
        }

        @Override
        public DataComponentPatch data() {
            return access.getUpgradeData();
        }

        @Override
        public void setData(DataComponentPatch data) {
            access.setUpgradeData(data);
        }

        @Override
        public String nameKey() {
            return "computer." + SignalTower.MOD_ID + ".pocket";
        }

        @Override
        public boolean sameAs(MobileRadioPeripheral.Host other) {
            return other instanceof PocketHost host && host.access == access;
        }
    }
}
