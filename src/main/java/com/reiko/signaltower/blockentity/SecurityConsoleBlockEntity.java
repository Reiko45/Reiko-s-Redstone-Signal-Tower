package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.Config;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.RadioHelper;
import com.reiko.signaltower.radio.SecuritySetup;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.radio.Telegraph;
import com.reiko.signaltower.registry.ModRegistry;
import com.reiko.signaltower.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A Security Console (v0.68.0). Its own frequency is its control frequency: each time that turns on
 * (a Code Keypad's right code, a remote) it arms or disarms. Its zones ({@link SecuritySetup}) are
 * frequencies it listens on where it hangs, like a receiver: one turning on trips its zone.
 * <ul>
 *   <li>Disarmed → arming (the exit delay, a beep a second) → armed.</li>
 *   <li>Armed, a Delayed zone → entry (the delay, faster beeps) → alarm, unless it's disarmed first;
 *       an Instant zone → alarm at once (during entry too); an Always zone → alarm, armed or not.</li>
 *   <li>Alarm: full strength on the alarm frequency (reaching like a walkie-talkie and through the
 *       towers in reach) for {@link #SIREN_TICKS}, its own siren every 2 seconds, "ALARM: Vault (Rex)"
 *       to pagers and walkie-talkies there, and TV Monitors on the alarm frequency show the cameras on
 *       the tripped zone's frequency. It stays in alarm (the panel red) until it's disarmed.</li>
 * </ul>
 * Who did what comes from the turn-on that did it (a remote, keypad or sensor names its player). The
 * last {@link #LOG_SIZE} events are kept. On a private control frequency only the owner and
 * members (and operators) change, arm, disarm or read it.
 */
public class SecurityConsoleBlockEntity extends RadioBlockEntity {
    public static final int LOG_SIZE = 32;
    /** How long its alarm sends (and its siren sounds) before it goes quiet, still in alarm: 2 minutes. */
    public static final int SIREN_TICKS = 2400;
    /** How far from it a player may change or arm it. */
    public static final double USE_DISTANCE = 8.0;
    /** "who" for the night schedule. */
    public static final String NIGHT = "#night";
    public static final String MORNING = "#morning";
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    /** Saved and sent by ordinal: only add at the end. */
    public enum State {
        DISARMED, ARMING, ARMED, ENTRY, ALARM;

        public static State of(int ordinal) {
            State[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What happened. Saved by ordinal: only add at the end. */
    public enum Event {
        ARMING, ARMED, DISARMED, ENTRY, ALARM, TAMPER;

        public static Event of(int ordinal) {
            Event[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One line of its log: when, what, which zone (-1 for none) and who ("" if it can't tell). */
    public record LogEntry(long gameTime, long dayTime, Event event, int zone, String who) {
    }

    private SecuritySetup setup = SecuritySetup.EMPTY;
    private State state = State.DISARMED;
    /** The end of the exit or entry delay, or of the siren. */
    private long until;
    private int alarmZone = -1;
    private String alarmWho = "";
    private boolean autoArmed;
    private final List<LogEntry> log = new ArrayList<>();
    // Not saved.
    private final int[] power = new int[SecuritySetup.ZONES];
    private final long[] revisions = new long[SecuritySetup.ZONES];
    private boolean primed;
    private int controlPower;
    private long controlRevision = Long.MIN_VALUE;
    private boolean controlPrimed;
    private List<SignalNetwork.Coverage> alarmArea = List.of();
    private int alarmOn; // the frequency alarmArea is on
    private @Nullable Boolean night;
    private int openMask;
    private final int offset;

    public SecurityConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SECURITY_CONSOLE_BE.get(), pos, state);
        offset = Math.floorMod(pos.hashCode(), 10);
    }

    // ---- Settings ----

    public SecuritySetup getSetup() {
        return setup;
    }

    public State getState() {
        return state;
    }

    /** Game time the delay (or siren) ends. */
    public long getUntil() {
        return until;
    }

    public int getAlarmZone() {
        return alarmZone;
    }

    public String getAlarmWho() {
        return alarmWho;
    }

    /** Zones whose frequency it hears now (bit i for zone i). */
    public int getOpenMask() {
        return openMask;
    }

    public List<LogEntry> getLog() {
        return List.copyOf(log);
    }

    /** True if {@code player} may change, arm, disarm or read it: anyone, but on a private control frequency only members (and operators). */
    public boolean mayConfigure(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        FrequencyClaims.Claim claim = FrequencyClaims.get(serverLevel.getServer()).get(getFrequency());
        return claim == null || claim.allows(player.getUUID()) || player.hasPermissions(2);
    }

    /** True if {@code player} is near enough to use it from its screen. */
    public boolean inReach(Player player) {
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5)
                <= USE_DISTANCE * USE_DISTANCE;
    }

    /**
     * {@code player} sets its zones, alarm frequency, delay and schedule. Refused (a red line) if
     * they may not, or if the alarm would go out on someone else's private frequency (then the rest
     * is still set). Returns the action bar line.
     */
    public Component configure(ServerLevel level, Player player, SecuritySetup newSetup) {
        if (!mayConfigure(player)) {
            return FrequencyClaims.refusal(level, getFrequency(), player.getUUID(), "private_refused_security");
        }
        SecuritySetup clean = newSetup.normalized();
        Component refused = null;
        if (clean.alarmFrequency() > 0 && clean.alarmFrequency() != setup.alarmFrequency()) {
            refused = FrequencyClaims.refusal(level, clean.alarmFrequency(), player.getUUID(), "private_refused_block");
            if (refused != null) {
                clean = new SecuritySetup(clean.names(), clean.frequencies(), clean.modes(), setup.alarmFrequency(), clean.flags());
            }
        }
        if (clean.alarmFrequency() != setup.alarmFrequency()) {
            stopAlarmOutput(level);
        }
        setup = clean;
        changed();
        return refused != null ? refused : Component.translatable(KEY + "security_set", setup.zonesInUse(),
                setup.alarmFrequency() > 0 ? FrequencyNames.label(level, setup.alarmFrequency()) : Component.translatable(KEY + "security_no_alarm"))
                .withStyle(ChatFormatting.AQUA);
    }

    public void setSetup(SecuritySetup newSetup) {
        setup = newSetup.normalized();
        changed();
    }

    // ---- Arming ----

    private int delayTicks() {
        return setup.delaySeconds() * 20;
    }

    /** Starts arming (the exit delay). False if it isn't disarmed. */
    public boolean arm(ServerLevel level, String who) {
        if (state != State.DISARMED) {
            return false;
        }
        state = State.ARMING;
        until = level.getGameTime() + delayTicks();
        alarmZone = -1;
        alarmWho = "";
        autoArmed = NIGHT.equals(who);
        record(level, Event.ARMING, -1, who);
        beep(level, 1.5F);
        changed();
        return true;
    }

    /** Disarms it from any state: the alarm stops. False if it was disarmed already. */
    public boolean disarm(ServerLevel level, String who) {
        if (state == State.DISARMED) {
            return false;
        }
        boolean wasAlarm = state == State.ALARM;
        stopAlarmOutput(level);
        state = State.DISARMED;
        autoArmed = false;
        alarmZone = -1;
        alarmWho = "";
        record(level, Event.DISARMED, -1, who);
        beep(level, 0.8F);
        if (wasAlarm) {
            message(level, Component.translatable(KEY + "security_cleared", whoText(who)).getString());
        }
        changed();
        return true;
    }

    /** Arms it if it's disarmed, else disarms it. */
    public void toggle(ServerLevel level, String who) {
        if (state == State.DISARMED) {
            arm(level, who);
        } else {
            disarm(level, who);
        }
    }

    /** Zone {@code zone}'s frequency turned on, set off by {@code who}. Public for the GameTests. */
    public void trip(ServerLevel level, int zone, String who) {
        if (state == State.ALARM || setup.frequency(zone) <= 0) {
            return;
        }
        SecuritySetup.Mode mode = setup.mode(zone);
        if (mode == SecuritySetup.Mode.ALWAYS) {
            alarm(level, zone, who);
        } else if (state == State.ARMED) {
            if (mode == SecuritySetup.Mode.DELAYED) {
                state = State.ENTRY;
                until = level.getGameTime() + delayTicks();
                alarmZone = zone;
                alarmWho = who;
                record(level, Event.ENTRY, zone, who);
                changed();
            } else {
                alarm(level, zone, who);
            }
        } else if (state == State.ENTRY && mode == SecuritySetup.Mode.INSTANT) {
            alarm(level, zone, who);
        }
    }

    private void alarm(ServerLevel level, int zone, String who) {
        state = State.ALARM;
        alarmZone = zone;
        alarmWho = who;
        until = level.getGameTime() + SIREN_TICKS;
        record(level, Event.ALARM, zone, who);
        String detail = alarmText(zone, who);
        sendAlarm(level, SignalNetwork.Cause.security(worldPosition, getTunedBy(), detail));
        feed(level);
        message(level, Component.translatable(KEY + "security_alarm", detail).getString());
        siren(level);
        changed();
    }

    /** "Vault (Rex)", or "Vault" if it can't tell who. */
    public String alarmText(int zone, String who) {
        String name = zone >= 0 ? setup.label(zone) : "?";
        return who.isEmpty() || who.startsWith("#") ? name : name + " (" + who + ")";
    }

    /** The console was broken while armed: one last alarm message, and its alarm frequency on for 10 seconds. */
    public void tamper(ServerLevel level) {
        if (state == State.DISARMED || state == State.ARMING) {
            return;
        }
        String text = Component.translatable(KEY + "security_tamper").getString();
        int alarm = setup.alarmFrequency();
        if (alarm > 0 && FrequencyClaims.maySend(level, alarm, getTunedBy())) {
            SignalNetwork network = SignalNetwork.get(level);
            List<SignalNetwork.Coverage> area = coverage(level);
            network.beacon(alarm, alarmOn == alarm ? alarmArea : List.of(), area, level.getGameTime() + 200,
                    SignalNetwork.Cause.security(worldPosition, getTunedBy(), text));
            network.clearFeed(alarm);
            alarmArea = List.of(); // (so its removal doesn't end this one)
            alarmOn = 0;
        }
        message(level, Component.translatable(KEY + "security_alarm", text).getString());
    }

    // ---- The alarm's outputs ----

    private List<SignalNetwork.Coverage> coverage(ServerLevel level) {
        return SignalNetwork.get(level).remoteCoverage(level, worldPosition, RadioHelper.reachAt(level, worldPosition),
                Config.TOWERS_RELAY_REMOTES.get());
    }

    private void sendAlarm(ServerLevel level, @Nullable SignalNetwork.Cause cause) {
        int alarm = setup.alarmFrequency();
        if (alarm <= 0 || !FrequencyClaims.maySend(level, alarm, getTunedBy())) {
            return;
        }
        List<SignalNetwork.Coverage> area = coverage(level);
        SignalNetwork.get(level).beacon(alarm, alarmOn == alarm ? alarmArea : List.of(), area, level.getGameTime() + 15, cause);
        alarmArea = List.copyOf(area);
        alarmOn = alarm;
    }

    private void feed(ServerLevel level) {
        int alarm = setup.alarmFrequency();
        int zone = alarmZone >= 0 ? setup.frequency(alarmZone) : 0;
        if (alarm > 0 && zone > 0 && zone != alarm) {
            SignalNetwork.get(level).setFeed(alarm, zone, level.getGameTime() + 45);
        }
    }

    private void stopAlarmOutput(ServerLevel level) {
        SignalNetwork network = SignalNetwork.get(level);
        if (alarmOn > 0 && !alarmArea.isEmpty()) {
            network.endBeacon(alarmOn, alarmArea);
        }
        if (setup.alarmFrequency() > 0) {
            network.clearFeed(setup.alarmFrequency());
        }
        alarmArea = List.of();
        alarmOn = 0;
    }

    /** A message on the alarm frequency, from "Security Console", to pagers, walkie-talkies, telegraphs and Status Boards in reach. */
    private void message(ServerLevel level, String text) {
        int alarm = setup.alarmFrequency();
        if (alarm <= 0 || !FrequencyClaims.maySend(level, alarm, getTunedBy())) {
            return;
        }
        String name = Component.translatable("block." + SignalTower.MOD_ID + ".security_console").getString();
        Telegraph.broadcast(level, worldPosition, alarm, SignalNetwork.get(level).withRepeaters(level, alarm, coverage(level)), name,
                Telegraph.clean(text));
    }

    private void beep(ServerLevel level, float pitch) {
        level.playSound(null, worldPosition, ModSounds.SECURITY_BEEP.get(), SoundSource.BLOCKS, 0.6F, pitch);
    }

    private void siren(ServerLevel level) {
        level.playSound(null, worldPosition, ModSounds.STATION_SIREN.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
    }

    // ---- Every tick ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecurityConsoleBlockEntity console) {
        if (level instanceof ServerLevel serverLevel) {
            console.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        SignalNetwork network = SignalNetwork.get(level);
        boolean look = (now + offset) % 10 == 0;
        // Its control frequency: each turn-on arms or disarms it.
        int control = getFrequency();
        long revision = network.revision(control);
        if (look || revision != controlRevision) {
            controlRevision = revision;
            int heard = network.listen(level, control, worldPosition, getTunedBy()).power();
            if (controlPrimed && controlPower == 0 && heard > 0) {
                toggle(level, whoTurnedOn(level, network, control));
            }
            controlPower = heard;
            controlPrimed = true;
        }
        // Its zones: each turn-on trips one.
        int mask = 0;
        for (int i = 0; i < SecuritySetup.ZONES; i++) {
            int frequency = setup.frequency(i);
            if (frequency <= 0 || frequency == setup.alarmFrequency() || frequency == control) {
                power[i] = 0;
                continue;
            }
            long zoneRevision = network.revision(frequency);
            if (look || zoneRevision != revisions[i]) {
                revisions[i] = zoneRevision;
                int heard = network.listen(level, frequency, worldPosition, getTunedBy()).power();
                if (primed && power[i] == 0 && heard > 0) {
                    trip(level, i, whoTurnedOn(level, network, frequency));
                }
                power[i] = heard;
            }
            if (power[i] > 0) {
                mask |= 1 << i;
            }
        }
        primed = true;
        if (mask != openMask) {
            openMask = mask;
            changed();
        }
        // Delays, beeps and the alarm.
        switch (state) {
            case ARMING -> {
                if (now >= until) {
                    state = State.ARMED;
                    record(level, Event.ARMED, -1, "");
                    beep(level, 2.0F);
                    changed();
                } else if ((until - now) % 20 == 0) {
                    beep(level, until - now <= 60 ? 1.3F : 1.0F);
                }
            }
            case ENTRY -> {
                if (now >= until) {
                    alarm(level, alarmZone, alarmWho);
                } else if ((until - now) % 10 == 0) {
                    beep(level, 1.4F);
                }
            }
            case ALARM -> {
                if (now < until) {
                    if (now % 10 == 0) {
                        sendAlarm(level, null);
                    }
                    if ((until - now) % 40 == 0) {
                        siren(level);
                    }
                } else if (alarmOn > 0) {
                    stopAlarmOutput(level); // the siren's time is up; it stays in alarm until disarmed
                }
                if (now % 20 == 0) {
                    feed(level);
                }
            }
            default -> {
            }
        }
        if (setup.night() && (now + offset) % 20 == 0) {
            checkNight(level, level.getDayTime());
        }
    }

    /**
     * The night schedule, at {@code dayTime}: at dusk (13000) a disarmed console arms; at dawn (23000)
     * one it armed itself disarms. The first look only notes which it is. Public for the GameTests.
     */
    public void checkNight(ServerLevel level, long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        boolean isNight = time >= 13000 && time < 23000;
        if (night != null && isNight != night) {
            if (isNight && state == State.DISARMED) {
                arm(level, NIGHT);
            } else if (!isNight && autoArmed && (state == State.ARMING || state == State.ARMED)) {
                disarm(level, MORNING);
            }
        }
        night = isNight;
    }

    /** Who turned {@code frequency} on just now, if the turn-on names a player (a remote, keypad or sensor). */
    static String whoTurnedOn(ServerLevel level, SignalNetwork network, int frequency) {
        List<SignalNetwork.TurnOn> history = network.history(frequency);
        if (history.isEmpty()) {
            return "";
        }
        SignalNetwork.TurnOn last = history.get(history.size() - 1);
        if (level.getGameTime() - last.gameTime() > 40) {
            return "";
        }
        SignalNetwork.Cause cause = last.cause();
        boolean named = switch (cause.kind()) {
            case REMOTE_PULSE, REMOTE_TOGGLE, KEYPAD, SENSOR -> true;
            default -> false;
        };
        if (!named) {
            return "";
        }
        if (!cause.name().isEmpty()) {
            return cause.name();
        }
        UUID who = cause.who();
        if (who != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
            if (player != null) {
                return player.getGameProfile().getName();
            }
            return level.getServer().getProfileCache() == null ? ""
                    : level.getServer().getProfileCache().get(who).map(com.mojang.authlib.GameProfile::getName).orElse("");
        }
        return "";
    }

    private static Component whoText(String who) {
        return who.isEmpty() ? Component.translatable(KEY + "security_who_unknown")
                : who.startsWith("#") ? Component.translatable(KEY + "security_who_" + who.substring(1))
                : Component.literal(who);
    }

    private void record(ServerLevel level, Event event, int zone, String who) {
        log.add(new LogEntry(level.getGameTime(), level.getDayTime(), event, zone, who == null ? "" : who));
        while (log.size() > LOG_SIZE) {
            log.remove(0);
        }
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState blockState = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void onFrequencyChanged() {
        controlPrimed = false;
        controlRevision = Long.MIN_VALUE;
        changed();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            stopAlarmOutput(serverLevel);
        }
        super.setRemoved();
    }

    // ---- What its status line and Jade say ----

    /** "Armed", "Arming: 7 s", "Entry: 4 s (Front Door)", "ALARM: Vault (Rex)" or "Disarmed". */
    public Component stateText(long gameTime) {
        long left = Math.max(0, (until - gameTime + 19) / 20);
        return switch (state) {
            case DISARMED -> Component.translatable(KEY + "security_state_disarmed").withStyle(ChatFormatting.GREEN);
            case ARMING -> Component.translatable(KEY + "security_state_arming", left).withStyle(ChatFormatting.GOLD);
            case ARMED -> Component.translatable(KEY + "security_state_armed").withStyle(ChatFormatting.RED);
            case ENTRY -> Component.translatable(KEY + "security_state_entry", left, setup.label(Math.max(0, alarmZone)))
                    .withStyle(ChatFormatting.GOLD);
            case ALARM -> Component.translatable(KEY + "security_state_alarm", alarmText(alarmZone, alarmWho))
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        };
    }

    /** What its screen shows besides its setup: the state, the open zones, and the log (newest last). */
    public CompoundTag screenData(long gameTime) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", state.ordinal());
        tag.putLong("left", Math.max(0, until - gameTime));
        tag.putInt("alarm_zone", alarmZone);
        tag.putString("alarm_who", alarmWho);
        tag.putInt("open", openMask);
        ListTag entries = new ListTag();
        for (LogEntry entry : log) {
            CompoundTag line = new CompoundTag();
            line.putLong("ago", Math.max(0, gameTime - entry.gameTime()));
            line.putLong("day_time", entry.dayTime());
            line.putInt("event", entry.event().ordinal());
            line.putInt("zone", entry.zone());
            line.putString("who", entry.who());
            entries.add(line);
        }
        tag.put("log", entries);
        return tag;
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        SecuritySetup.CODEC.encodeStart(NbtOps.INSTANCE, setup).result().ifPresent(encoded -> tag.put("security", encoded));
        tag.putInt("security_state", state.ordinal());
        tag.putLong("security_until", until);
        tag.putInt("security_zone", alarmZone);
        tag.putString("security_who", alarmWho);
        tag.putBoolean("security_auto", autoArmed);
        ListTag entries = new ListTag();
        for (LogEntry entry : log) {
            CompoundTag line = new CompoundTag();
            line.putLong("t", entry.gameTime());
            line.putLong("d", entry.dayTime());
            line.putInt("e", entry.event().ordinal());
            line.putInt("z", entry.zone());
            line.putString("w", entry.who());
            entries.add(line);
        }
        tag.put("security_log", entries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setup = tag.contains("security") ? SecuritySetup.CODEC.parse(NbtOps.INSTANCE, tag.get("security")).result()
                .map(SecuritySetup::normalized).orElse(SecuritySetup.EMPTY) : SecuritySetup.EMPTY;
        state = State.of(tag.getInt("security_state"));
        until = tag.getLong("security_until");
        alarmZone = tag.contains("security_zone") ? tag.getInt("security_zone") : -1;
        alarmWho = tag.getString("security_who");
        autoArmed = tag.getBoolean("security_auto");
        if (tag.contains("security_log")) {
            log.clear();
            for (Tag item : tag.getList("security_log", Tag.TAG_COMPOUND)) {
                CompoundTag line = (CompoundTag) item;
                log.add(new LogEntry(line.getLong("t"), line.getLong("d"), Event.of(line.getInt("e")), line.getInt("z"), line.getString("w")));
            }
        }
        if (tag.contains("security_open")) {
            openMask = tag.getInt("security_open"); // the client's copy
        }
    }

    /** What clients get (for the panel's face): the setup, state, delay end, alarm zone and open zones; not the log or who did what. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("frequency", getFrequency());
        SecuritySetup.CODEC.encodeStart(NbtOps.INSTANCE, setup).result().ifPresent(encoded -> tag.put("security", encoded));
        tag.putInt("security_state", state.ordinal());
        tag.putLong("security_until", until);
        tag.putInt("security_zone", alarmZone);
        tag.putInt("security_open", openMask);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Item <-> block: it keeps its setup ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        SecuritySetup stored = componentInput.get(ModRegistry.SECURITY_SETUP.get());
        if (stored != null) {
            setup = stored.normalized();
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!setup.isEmpty()) {
            components.set(ModRegistry.SECURITY_SETUP.get(), setup);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("security");
    }
}
