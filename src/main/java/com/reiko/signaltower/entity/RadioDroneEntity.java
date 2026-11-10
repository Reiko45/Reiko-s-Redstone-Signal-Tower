package com.reiko.signaltower.entity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.RadioBatteryBlockEntity;
import com.reiko.signaltower.blockentity.SolarPanelBlockEntity;
import com.reiko.signaltower.item.DroneControllerItem;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.radio.DronePilots;
import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.IntruderAlerts;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.TvCamera;
import com.reiko.signaltower.radio.WalkieTalkie;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Radio Drone (v0.67.0): a small quadcopter flown from a Drone Controller over the radio.
 * <p>
 * <b>Flying.</b> While a player pilots it ({@link DronePilots}), their client sends what they press
 * every tick ({@link #steer}); the drone flies that way relative to where they look, with a little
 * inertia, colliding like any entity, and won't fly into unloaded chunks. Let go and it hovers
 * where it is. The pilot's signal reaches it like a walkie-talkie's (from where the pilot stands,
 * by height, carried by towers and repeaters on its frequency): past the edge it loses control and
 * sinks gently to the ground. While it's in the air a chunk ticket keeps its chunk ticking.
 * <p>
 * <b>Battery.</b> {@link #CAPACITY} charge: flying costs {@link #FLY_COST} a tick (about 4
 * minutes), hovering {@link #HOVER_COST} (plus {@link #RELAY_COST} as a relay); below
 * {@link #LOW_CHARGE} it lands itself, and flat it drops. On a Landing Pad it charges from a Radio
 * Battery or a Solar Panel next to the pad; Redstone Dust charges it by hand.
 * <p>
 * <b>Jobs.</b> It carries one item stack (grabbed from the ground under it). It carries a camera
 * like a TV Camera's, which TV Monitors on its frequency show. With Beacon on it turns its
 * frequency on within {@link #BEACON_REACH} blocks of itself while in the air; with Relay on, while
 * it hovers, it repeats its frequency like a Radio Repeater with the reach of its height.
 */
public class RadioDroneEntity extends Entity {
    public static final int CAPACITY = 9600;
    public static final int FLY_COST = 2;
    public static final int HOVER_COST = 1;
    public static final int RELAY_COST = 1;
    public static final int LOW_CHARGE = 300;
    public static final int BEACON_REACH = 16;
    public static final double MAX_SPEED = 0.45;
    public static final double MAX_CLIMB = 0.3;
    private static final double ACCELERATION = 0.25;
    /** How long the pilot's last input counts (ticks). */
    private static final int INPUT_TICKS = 5;
    public static final int PAD_CHARGE_PER_TICK = 10;

    // Settings bits.
    public static final int BEACON = 1;
    public static final int RELAY = 2;
    public static final int NIGHT_VISION = 4;
    public static final int SETTINGS_MASK = 7;

    /** What it's doing. */
    public enum State {
        LANDED, FLYING, HOVERING, SINKING;

        public static State of(int ordinal) {
            State[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        public boolean airborne() {
            return this != LANDED;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_FREQUENCY = SynchedEntityData.defineId(RadioDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(RadioDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(RadioDroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SETTINGS = SynchedEntityData.defineId(RadioDroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED = SynchedEntityData.defineId(RadioDroneEntity.class, EntityDataSerializers.ITEM_STACK);

    public static final TicketType<Integer> TICKET = TicketType.create(SignalTower.MOD_ID + ":drone", Integer::compare, 60);
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private @Nullable UUID tunedBy;
    private float health = 6.0F;
    // Server only (not saved).
    private @Nullable Player pilot;
    private float inputForward;
    private float inputStrafe;
    private float inputVertical;
    private long inputAt = Long.MIN_VALUE;
    private double signal = -1; // the pilot's edge fraction at the last look
    private boolean lostSignal;
    private int knockedTicks;
    private @Nullable BlockPos relayAt;
    private SignalNetwork.@Nullable Coverage beaconArea;
    private long beaconSince = Long.MIN_VALUE;
    private @Nullable ChunkPos ticketAt;
    // Client only: the rotors' angle.
    public float rotor;
    public float rotorO;

    public RadioDroneEntity(EntityType<? extends RadioDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FREQUENCY, Frequency.DEFAULT);
        builder.define(DATA_CHARGE, CAPACITY);
        builder.define(DATA_STATE, (byte) State.LANDED.ordinal());
        builder.define(DATA_SETTINGS, (byte) 0);
        builder.define(DATA_CARRIED, ItemStack.EMPTY);
    }

    // ---- What it is ----

    public int getFrequency() {
        return entityData.get(DATA_FREQUENCY);
    }

    public void setFrequency(int frequency) {
        int clamped = Frequency.clamp(frequency);
        if (clamped != getFrequency()) {
            entityData.set(DATA_FREQUENCY, clamped);
            if (level() instanceof ServerLevel serverLevel) {
                stopRelay(serverLevel);
                SignalNetwork.get(serverLevel).removeDrone(getUUID());
                publish(serverLevel);
            }
        }
    }

    public @Nullable UUID getTunedBy() {
        return tunedBy;
    }

    public void setTunedBy(@Nullable UUID player) {
        tunedBy = player;
    }

    public int getCharge() {
        return entityData.get(DATA_CHARGE);
    }

    public void setCharge(int charge) {
        entityData.set(DATA_CHARGE, Mth.clamp(charge, 0, CAPACITY));
    }

    /** Adds up to {@code amount}; returns how much went in. */
    public int addCharge(int amount) {
        int added = Math.max(0, Math.min(amount, CAPACITY - getCharge()));
        if (added > 0) {
            setCharge(getCharge() + added);
        }
        return added;
    }

    public State getState() {
        return State.of(entityData.get(DATA_STATE));
    }

    private void setState(State state) {
        if (state != getState()) {
            entityData.set(DATA_STATE, (byte) state.ordinal());
        }
    }

    public int getSettings() {
        return entityData.get(DATA_SETTINGS);
    }

    public void setSettings(int settings) {
        entityData.set(DATA_SETTINGS, (byte) (settings & SETTINGS_MASK));
        if ((settings & RELAY) == 0 && level() instanceof ServerLevel serverLevel) {
            stopRelay(serverLevel);
        }
    }

    public boolean has(int setting) {
        return (getSettings() & setting) != 0;
    }

    public ItemStack getCarried() {
        return entityData.get(DATA_CARRIED);
    }

    public void setCarried(ItemStack stack) {
        entityData.set(DATA_CARRIED, stack.copy());
    }

    /** Its name, for monitors and the HUD: an anvil or settings name, or "". */
    public String droneName() {
        Component name = getCustomName();
        return name == null ? "" : name.getString();
    }

    public void setDroneName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() > 24) {
            trimmed = trimmed.substring(0, 24);
        }
        setCustomName(trimmed.isEmpty() ? null : Component.literal(trimmed));
        if (level() instanceof ServerLevel serverLevel) {
            publish(serverLevel);
        }
    }

    /** The player flying it now (on the server), or null. */
    public @Nullable Player getPilot() {
        return pilot;
    }

    /** How far out in the pilot's reach it was at the last look (0 middle, 1 edge; more: out of reach), or -1 with no pilot. */
    public double getSignal() {
        return signal;
    }

    public boolean hasLostSignal() {
        return lostSignal;
    }

    /** True if it may be flown, set, picked up or tuned by {@code player}: anyone, but on a private frequency only members (and operators). */
    public boolean mayUse(Player player) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        FrequencyClaims.Claim claim = FrequencyClaims.get(serverLevel.getServer()).get(getFrequency());
        return claim == null || claim.allows(player.getUUID()) || player.hasPermissions(2);
    }

    // ---- Flying ----

    /** Where its camera is and looks: a TV Camera's pose (its yaw, the pilot's pitch). */
    public TvCamera.Pose cameraPose(float partialTick) {
        Vec3 at = getPosition(partialTick).add(0, 0.12, 0);
        float yaw = getViewYRot(partialTick);
        float pitch = Mth.clamp(getViewXRot(partialTick), -60.0F, 85.0F);
        Vec3 eye = at.add(Vec3.directionFromRotation(pitch, yaw).scale(0.38));
        return new TvCamera.Pose(eye, yaw, pitch, TvCamera.LENSES[1]);
    }

    /** The pilot's controls this tick: forward/back, left/right, up/down (each -1 to 1), and where they look. */
    public void steer(float forward, float strafe, float vertical, float yaw, float pitch) {
        inputForward = Float.isFinite(forward) ? Mth.clamp(forward, -1.0F, 1.0F) : 0.0F;
        inputStrafe = Float.isFinite(strafe) ? Mth.clamp(strafe, -1.0F, 1.0F) : 0.0F;
        inputVertical = Float.isFinite(vertical) ? Mth.clamp(vertical, -1.0F, 1.0F) : 0.0F;
        if (Float.isFinite(yaw)) {
            setYRot(Mth.wrapDegrees(yaw));
        }
        if (Float.isFinite(pitch)) {
            setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
        }
        inputAt = level().getGameTime();
    }

    /** {@code player} starts flying it (see {@link DronePilots#start}). */
    public void setPilot(@Nullable Player player) {
        pilot = player;
        inputAt = Long.MIN_VALUE;
        if (player != null) {
            lostSignal = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            rotorO = rotor;
            rotor += getState().airborne() ? 1.6F : 0.0F;
            if (getState().airborne()) {
                com.reiko.signaltower.client.ClientHooks.droneBuzz(this);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        checkPilot(level, now);
        State state = getState();
        int charge = getCharge();
        boolean piloted = pilot != null;
        boolean fresh = piloted && now - inputAt <= INPUT_TICKS && inputAt != Long.MIN_VALUE;
        if (knockedTicks > 0) {
            knockedTicks--;
        }
        // What it does, and what that costs.
        boolean canFly = charge >= LOW_CHARGE && knockedTicks == 0 && !lostSignal;
        boolean moving = Math.abs(inputForward) + Math.abs(inputStrafe) + Math.abs(inputVertical) > 0.01;
        Vec3 target;
        if (charge <= 0 && state.airborne()) {
            target = new Vec3(0, -0.6, 0); // flat: it drops
        } else if (state.airborne() && !canFly) {
            target = new Vec3(0, -0.08, 0); // sinks gently
        } else if (fresh && canFly && (state.airborne() || inputVertical > 0)) {
            Vec3 forward = Vec3.directionFromRotation(0, getYRot());
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            Vec3 flat = forward.scale(inputForward).add(right.scale(-inputStrafe));
            if (flat.lengthSqr() > 1) {
                flat = flat.normalize();
            }
            target = new Vec3(flat.x * MAX_SPEED, inputVertical * MAX_CLIMB, flat.z * MAX_SPEED);
        } else if (state.airborne()) {
            target = Vec3.ZERO; // hovers
        } else {
            target = null; // stays on the ground
        }
        if (target != null) {
            Vec3 motion = getDeltaMovement();
            double accel = target.y < -0.3 ? 0.08 : ACCELERATION;
            Vec3 next = keepToLoadedChunks(level, motion.add(target.subtract(motion).scale(accel)));
            setDeltaMovement(next);
            move(MoverType.SELF, next);
            if (onGround() && next.y <= 0.01 && !(fresh && canFly && inputVertical > 0)) {
                setDeltaMovement(Vec3.ZERO);
                land(level);
            } else {
                setState(!canFly || charge <= 0 ? State.SINKING : fresh && moving ? State.FLYING : State.HOVERING);
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
            if (charge >= LOW_CHARGE && level.noCollision(this, getBoundingBox().move(0, -0.05, 0))) {
                setState(State.HOVERING); // what it stood on is gone: it holds itself up
            } else if (level.noCollision(this, getBoundingBox().move(0, -0.05, 0))) {
                setState(State.SINKING);
            }
        }
        state = getState();
        // Battery.
        if (state.airborne() && charge > 0) {
            int cost = state == State.FLYING ? FLY_COST : HOVER_COST;
            if (relayAt != null) {
                cost += RELAY_COST;
            }
            setCharge(charge - cost);
        }
        if (state == State.LANDED) {
            chargeOnPad(level);
        }
        if (charge > 0 && getCharge() == 0 && pilot instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(KEY + "drone_flat").withStyle(ChatFormatting.RED), true);
        }
        // Jobs.
        beacon(level, now);
        relay(level, now);
        if (now % 20 == Math.floorMod(getId(), 20)) {
            publish(level);
        }
        keepTicking(level);
        if (piloted && now % 5 == 0) {
            DronePilots.sendStatus(this);
        }
    }

    /** Ends the flight if the pilot let go of the controller, left, or flew it out of reach (checked twice a second). */
    private void checkPilot(ServerLevel level, long now) {
        if (pilot == null) {
            signal = -1;
            return;
        }
        if (pilot.isRemoved() || pilot.level() != level || !DroneControllerItem.holdsLinked(pilot, getUUID())) {
            DronePilots.stop(pilot, null);
            return;
        }
        if (now % 10 == 0 || signal < 0) {
            signal = signalFrom(level, pilot.blockPosition());
            if (signal > 1.0) {
                lostSignal = true;
                Player was = pilot;
                DronePilots.stop(was, Component.translatable(KEY + "drone_lost").withStyle(ChatFormatting.RED));
            }
        }
    }

    /** How far out {@code from}'s signal is where the drone is (0 middle, 1 edge, more: out of reach), on its frequency. */
    public double signalFrom(ServerLevel level, BlockPos from) {
        SignalNetwork network = SignalNetwork.get(level);
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, from, RadioHelper.reachAt(level, from), Config.TOWERS_RELAY_REMOTES.get());
        List<SignalNetwork.Coverage> coverage = network.withRepeaters(level, getFrequency(), own);
        return WalkieTalkie.edgeFraction(coverage, blockPosition());
    }

    /** Doesn't fly into chunks that aren't loaded (it would end up inside their ground when they load). */
    private Vec3 keepToLoadedChunks(ServerLevel level, Vec3 motion) {
        Vec3 next = position().add(motion.x * 2, 0, motion.z * 2);
        if (!level.isLoaded(BlockPos.containing(next))) {
            return new Vec3(0, motion.y, 0);
        }
        return motion;
    }

    private void land(ServerLevel level) {
        if (getState() != State.LANDED) {
            setState(State.LANDED);
            lostSignal = false;
            stopRelay(level);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.3F, 1.6F);
        }
    }

    /** On a Landing Pad, it charges from a Radio Battery or a Solar Panel next to the pad. */
    private void chargeOnPad(ServerLevel level) {
        BlockPos pad = padUnder();
        if (pad == null || getCharge() >= CAPACITY) {
            return;
        }
        for (Direction side : Direction.values()) {
            BlockPos next = pad.relative(side);
            if (level.getBlockEntity(next) instanceof RadioBatteryBlockEntity battery && battery.getCharge() > 0) {
                int want = Math.min(PAD_CHARGE_PER_TICK, CAPACITY - getCharge());
                addCharge(battery.take(want));
                return;
            }
        }
        if (level.getGameTime() % 20 == 0) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pad.relative(side);
                if (level.getBlockState(next).is(ModRegistry.SOLAR_PANEL.get())) {
                    addCharge((int) Math.round(SolarPanelBlockEntity.rateFor(SolarPanelBlockEntity.daylight(level, next))));
                    return;
                }
            }
        }
    }

    /** The Landing Pad it's standing on, or null. */
    public @Nullable BlockPos padUnder() {
        BlockPos here = BlockPos.containing(getX(), getY() + 0.01, getZ());
        for (BlockPos pos : new BlockPos[]{here, here.below()}) {
            if (level().getBlockState(pos).is(ModRegistry.LANDING_PAD.get())) {
                return pos;
            }
        }
        return null;
    }

    private void beacon(ServerLevel level, long now) {
        if (!has(BEACON) || !getState().airborne() || getCharge() <= 0) {
            if (beaconArea != null && now - beaconSince > 20) {
                beaconArea = null;
            }
            return;
        }
        if (now % 10 != 0) {
            return;
        }
        if (!FrequencyClaims.maySend(level, getFrequency(), tunedBy)) {
            return;
        }
        SignalNetwork.Coverage area = new SignalNetwork.Coverage(blockPosition(), BEACON_REACH, false);
        boolean fresh = beaconArea == null;
        SignalNetwork.get(level).beacon(getFrequency(), beaconArea, area, now + 15,
                fresh ? SignalNetwork.Cause.drone(blockPosition(), tunedBy, droneName()) : null);
        beaconArea = area;
        beaconSince = now;
    }

    private void relay(ServerLevel level, long now) {
        boolean relaying = has(RELAY) && getState() == State.HOVERING && pilot == null && getCharge() > LOW_CHARGE;
        if (!relaying) {
            stopRelay(level);
            return;
        }
        BlockPos here = blockPosition();
        if (relayAt != null && !relayAt.equals(here)) {
            stopRelay(level);
        }
        if (relayAt == null || now % 20 == 0) {
            SignalNetwork.get(level).updateDroneRelay(here, getFrequency(), RadioHelper.reachAt(level, here), tunedBy);
            relayAt = SignalNetwork.get(level).isDroneRelay(here) ? here : null;
        }
    }

    private void stopRelay(ServerLevel level) {
        if (relayAt != null) {
            SignalNetwork.get(level).removeDroneRelay(relayAt);
            relayAt = null;
        }
    }

    /** True while it repeats its frequency as a relay. */
    public boolean isRelaying() {
        return relayAt != null;
    }

    /** Puts its camera in the network for monitors. Public for the GameTests. */
    public void publish(ServerLevel level) {
        SignalNetwork network = SignalNetwork.get(level);
        BlockPos here = blockPosition();
        List<SignalNetwork.Coverage> own = network.remoteCoverage(level, here, RadioHelper.reachAt(level, here), Config.TOWERS_RELAY_REMOTES.get());
        network.updateDrone(getUUID(), new SignalNetwork.DroneSpot(getFrequency(), tunedBy, List.copyOf(network.withRepeaters(level, getFrequency(), own)),
                droneName(), getId(), here));
    }

    /** In the air, a chunk ticket keeps its chunk ticking (and loaded) wherever it flies. */
    private void keepTicking(ServerLevel level) {
        if (!getState().airborne()) {
            ticketAt = null;
            return;
        }
        ChunkPos here = chunkPosition();
        if (!here.equals(ticketAt) || level.getGameTime() % 20 == 0) {
            level.getChunkSource().addRegionTicket(TICKET, here, 2, getId(), true);
            ticketAt = here;
        }
    }

    // ---- Jobs from the pilot ----

    /** Grabs the nearest dropped stack under it, or drops what it carries. Returns the action bar line. */
    public Component grabOrDrop(ServerLevel level) {
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            ItemEntity dropped = new ItemEntity(level, getX(), getY() - 0.3, getZ(), carried.copy());
            dropped.setDeltaMovement(getDeltaMovement().scale(0.5));
            level.addFreshEntity(dropped);
            setCarried(ItemStack.EMPTY);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 0.6F);
            return Component.translatable(KEY + "drone_dropped", carried.getHoverName(), carried.getCount());
        }
        AABB around = getBoundingBox().inflate(1.5, 0, 1.5).expandTowards(0, -3.0, 0);
        ItemEntity nearest = level.getEntitiesOfClass(ItemEntity.class, around, item -> item.isAlive() && !item.getItem().isEmpty())
                .stream().min(Comparator.comparingDouble(item -> item.distanceToSqr(this))).orElse(null);
        if (nearest == null) {
            return Component.translatable(KEY + "drone_nothing").withStyle(ChatFormatting.GRAY);
        }
        ItemStack stack = nearest.getItem().copy();
        nearest.discard();
        setCarried(stack);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
        return Component.translatable(KEY + "drone_grabbed", stack.getHoverName(), stack.getCount());
    }

    // ---- Players ----

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof FrequencyTunerItem) {
            int frequency = FrequencyTunerItem.getFrequency(stack);
            if (player.isSecondaryUseActive()) {
                FrequencyTunerItem.setFrequency(stack, getFrequency());
                player.displayClientMessage(Component.translatable(KEY + "frequency_copied", FrequencyNames.label(level, getFrequency())), true);
                return InteractionResult.CONSUME;
            }
            Component refused = !mayUse(player) ? FrequencyClaims.refusal(level, getFrequency(), player.getUUID(), "private_refused_drone")
                    : FrequencyClaims.refusal(level, frequency, player.getUUID(), "private_refused_block");
            if (refused != null) {
                IntruderAlerts.report(level.getServer(), mayUse(player) ? frequency : getFrequency(), player, IntruderAlerts.Kind.TUNE, null);
                player.displayClientMessage(refused, true);
                return InteractionResult.CONSUME;
            }
            tunedBy = player.getUUID();
            setFrequency(frequency);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TUNER_TUNE.get(), SoundSource.NEUTRAL, 0.7F, 1.15F);
            player.displayClientMessage(Component.translatable(KEY + "frequency_set", FrequencyNames.label(level, getFrequency())), true);
            return InteractionResult.CONSUME;
        }
        if (stack.getItem() instanceof DroneControllerItem) {
            if (!mayUse(player)) {
                player.displayClientMessage(FrequencyClaims.refusal(level, getFrequency(), player.getUUID(), "private_refused_drone"), true);
                return InteractionResult.CONSUME;
            }
            DroneControllerItem.link(stack, this);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TUNER_STEP.get(), SoundSource.NEUTRAL, 0.7F, 1.3F);
            player.displayClientMessage(Component.translatable(KEY + "drone_paired", label(), FrequencyNames.label(level, getFrequency()))
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }
        int value = RadioBatteryBlockEntity.valueOf(stack);
        if (value > 0) {
            int room = CAPACITY - getCharge();
            int count = Math.min(stack.getCount(), Math.max(room > 0 ? 1 : 0, room / value));
            if (count > 0) {
                addCharge(count * value);
                stack.consume(count, player);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.NEUTRAL, 0.3F, 1.6F);
            }
            player.displayClientMessage(Component.translatable(KEY + "drone_charge", percent()), true);
            return InteractionResult.CONSUME;
        }
        if (stack.isEmpty() && player.isSecondaryUseActive()) {
            if (!mayUse(player)) {
                player.displayClientMessage(FrequencyClaims.refusal(level, getFrequency(), player.getUUID(), "private_refused_drone"), true);
                return InteractionResult.CONSUME;
            }
            if (pilot != null && pilot != player) {
                player.displayClientMessage(Component.translatable(KEY + "drone_busy", pilot.getName()).withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            ItemStack item = toItem();
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
            dropCarried(level);
            discard();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.0F);
            return InteractionResult.CONSUME;
        }
        if (stack.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            if (!mayUse(player)) {
                player.displayClientMessage(FrequencyClaims.refusal(level, getFrequency(), player.getUUID(), "private_refused_drone"), true);
                return InteractionResult.CONSUME;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, com.reiko.signaltower.network.OpenDronePayload.of(this));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** "Front Gate drone", or "the drone". */
    public Component label() {
        String name = droneName();
        return name.isEmpty() ? Component.translatable(KEY + "drone_unnamed") : Component.literal(name);
    }

    public int percent() {
        return (int) Math.round(getCharge() * 100.0 / CAPACITY);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !(level() instanceof ServerLevel level) || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            breakApart(level, false);
            return true;
        }
        health -= amount;
        knockedTicks = 30;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.2F, 2.0F);
        if (health <= 0) {
            breakApart(level, true);
        }
        return true;
    }

    /** Broken: it falls apart, dropping itself (as an item, keeping its frequency and charge) and what it carries. */
    private void breakApart(ServerLevel level, boolean dropSelf) {
        if (dropSelf) {
            spawnAtLocation(toItem());
        }
        dropCarried(level);
        discard();
    }

    private void dropCarried(ServerLevel level) {
        if (!getCarried().isEmpty()) {
            spawnAtLocation(getCarried().copy());
            setCarried(ItemStack.EMPTY);
        }
    }

    /** The drone as an item: its frequency, charge, settings, name, and which drone it is (so controllers stay paired). */
    public ItemStack toItem() {
        ItemStack item = new ItemStack(ModRegistry.RADIO_DRONE.get());
        item.set(ModRegistry.FREQUENCY.get(), getFrequency());
        item.set(ModRegistry.DRONE_CHARGE.get(), getCharge());
        item.set(ModRegistry.DRONE_SETTINGS.get(), getSettings());
        item.set(ModRegistry.DRONE_ID.get(), getUUID());
        if (getCustomName() != null) {
            item.set(DataComponents.CUSTOM_NAME, getCustomName());
        }
        return item;
    }

    /** Takes what {@code item} keeps (see {@link #toItem}). */
    public void fromItem(ItemStack item) {
        Integer frequency = item.get(ModRegistry.FREQUENCY.get());
        if (frequency != null) {
            entityData.set(DATA_FREQUENCY, Frequency.clamp(frequency));
        }
        Integer charge = item.get(ModRegistry.DRONE_CHARGE.get());
        setCharge(charge == null ? CAPACITY : charge);
        Integer settings = item.get(ModRegistry.DRONE_SETTINGS.get());
        setSettings(settings == null ? 0 : settings);
        Component name = item.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            setDroneName(name.getString());
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModRegistry.RADIO_DRONE.get());
    }

    // ---- Gone ----

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level() instanceof ServerLevel level) {
            stopRelay(level);
            SignalNetwork.get(level).removeDrone(getUUID());
            if (pilot != null) {
                DronePilots.stop(pilot, null);
            }
        }
    }

    // ---- Saving ----

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_FREQUENCY, tag.contains("frequency") ? Frequency.clamp(tag.getInt("frequency")) : Frequency.DEFAULT);
        setCharge(tag.contains("charge") ? tag.getInt("charge") : CAPACITY);
        setSettings(tag.getInt("settings"));
        tunedBy = tag.hasUUID("tuned_by") ? tag.getUUID("tuned_by") : null;
        health = tag.contains("health") ? tag.getFloat("health") : 6.0F;
        entityData.set(DATA_STATE, (byte) State.of(tag.getInt("state")).ordinal());
        setCarried(tag.contains("carried") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("carried")) : ItemStack.EMPTY);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("frequency", getFrequency());
        tag.putInt("charge", getCharge());
        tag.putInt("settings", getSettings());
        if (tunedBy != null) {
            tag.putUUID("tuned_by", tunedBy);
        }
        tag.putFloat("health", health);
        tag.putInt("state", getState() == State.FLYING ? State.HOVERING.ordinal() : getState().ordinal());
        if (!getCarried().isEmpty()) {
            tag.put("carried", getCarried().save(registryAccess()));
        }
    }

    // ---- Client ----

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    /** On the pilot's client the pilot turns it themselves (see {@code client.DronePilot}): only its position comes from the server. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (level().isClientSide && com.reiko.signaltower.client.ClientHooks.pilotsDrone(getId())) {
            setPos(x, y, z);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    /** Test hook: how long it's knocked about for. */
    public int knockedTicksForTests() {
        return knockedTicks;
    }
}
