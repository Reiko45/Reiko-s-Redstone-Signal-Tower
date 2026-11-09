package com.reiko.signaltower.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.CodeKeypadBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioRepeaterBlock;
import com.reiko.signaltower.block.RadioTransmitterBlock;
import com.reiko.signaltower.item.FrequencyTunerItem;
import com.reiko.signaltower.network.ReachPreviewPayload;
import com.reiko.signaltower.network.ReachPreviewRequestPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The reach preview ring (v0.37.0): while the player holds a Frequency Tuner, looking at a
 * transmitter, repeater, converter or Code Keypad (or a mast under one, up to 128 blocks away)
 * draws a dotted ring on the ground where its reach ends, plus two lines above the hotbar
 * saying how far it reaches and how far the player is from the edge. The ring stays on that
 * sender while the tuner is held, so you can walk out to the edge; looking at another one moves it.
 * <p>
 * The reach comes from the server ({@link ReachPreviewRequestPayload}), which knows the
 * dimension's real sea level and the config. Only this player sees the ring. The dots follow the
 * ground (the client's height map), and only those within render distance are drawn.
 */
public final class ReachRing {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "reach_ring");
    private static final String KEY = "hud." + SignalTower.MOD_ID + ".reach_ring";
    private static final int LOOK_DISTANCE = 128;
    /** How long to wait before asking about the same block again (no answer, or nothing to show). */
    private static final int RETRY_TICKS = 40;
    /** The ground under the dots is looked up again this often (blocks change, chunks load). */
    private static final int REBUILD_TICKS = 20;
    private static final int MAST_LIMIT = 512;

    private static @Nullable ReachPreviewPayload target;
    private static @Nullable ResourceKey<Level> targetDimension;
    private static @Nullable BlockPos pending;
    private static int pendingAge;
    private static int rebuildIn;
    /** Per dot: x, ground y (NaN: not drawn), z, and the ring's direction there (two floats). */
    private static float[] dots = new float[0];

    private ReachRing() {
    }

    /** The sender the ring is around, or null. */
    public static @Nullable ReachPreviewPayload target() {
        return target;
    }

    public static void clear() {
        target = null;
        targetDimension = null;
        pending = null;
        dots = new float[0];
    }

    private static boolean holdsTuner(Player player) {
        return player.getMainHandItem().getItem() instanceof FrequencyTunerItem
                || player.getOffhandItem().getItem() instanceof FrequencyTunerItem;
    }

    private static boolean isSender(Block block) {
        return block instanceof RadioTransmitterBlock || block instanceof RadioRepeaterBlock || block instanceof CodeKeypadBlock;
    }

    /** The sender at {@code pos}, or on top of the mast column {@code pos} is part of; null if there's none. */
    private static @Nullable BlockPos senderAt(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < MAST_LIMIT && level.getBlockState(cursor).getBlock() instanceof RadioMastBlock; i++) {
            cursor.move(0, 1, 0);
        }
        return isSender(level.getBlockState(cursor).getBlock()) ? cursor.immutable() : null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !holdsTuner(player)) {
            if (target != null || pending != null) {
                clear();
            }
            return;
        }
        if (target != null && (targetDimension != level.dimension()
                || level.isLoaded(target.pos()) && !isSender(level.getBlockState(target.pos()).getBlock()))) {
            clear(); // broken, or the player changed dimension
        }
        if (pending != null && ++pendingAge > RETRY_TICKS) {
            pending = null;
        }
        if (minecraft.screen == null) {
            HitResult hit = player.pick(LOOK_DISTANCE, 1.0F, false);
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos sender = senderAt(level, blockHit.getBlockPos());
                if (sender != null && (target == null || !sender.equals(target.pos())) && !sender.equals(pending)) {
                    pending = sender;
                    pendingAge = 0;
                    PacketDistributor.sendToServer(new ReachPreviewRequestPayload(sender));
                }
            }
        }
        if (target != null && --rebuildIn <= 0) {
            rebuild(minecraft, level, player);
            if (pending == null) {
                // v0.52.0: ask again once a second, so the ring follows a dish turned by a Dish Rotator (or the weather).
                pending = target.pos();
                pendingAge = 0;
                PacketDistributor.sendToServer(new ReachPreviewRequestPayload(target.pos()));
            }
        }
    }

    /** The server's answer. "Nothing to show" keeps the block pending, so it isn't asked about again every tick. */
    public static void onAnswer(ReachPreviewPayload answer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (answer.reach() < 0 || minecraft.level == null || minecraft.player == null || !holdsTuner(minecraft.player)
                || pending != null && !pending.equals(answer.pos())) {
            return;
        }
        target = answer;
        targetDimension = minecraft.level.dimension();
        pending = null;
        rebuild(minecraft, minecraft.level, minecraft.player);
    }

    private static double maxDistance(Minecraft minecraft) {
        return minecraft.options.getEffectiveRenderDistance() * 16.0 + 16.0;
    }

    /** Works out where each dot sits on the ground, for the dots within render distance. */
    private static void rebuild(Minecraft minecraft, ClientLevel level, Player player) {
        rebuildIn = REBUILD_TICKS;
        ReachPreviewPayload ring = target;
        if (ring == null) {
            return;
        }
        java.util.List<double[]> keyhole = ring.hasDish()
                ? ReachRingShape.keyhole(ring.reach(), ring.dishReach(), ring.dishAim(), ring.dishCone()) : null;
        int count = keyhole != null ? keyhole.size() : ReachRingShape.dotCount(ring.reach());
        float[] built = new float[count * 5];
        double centerX = ring.pos().getX() + 0.5;
        double centerZ = ring.pos().getZ() + 0.5;
        double max = maxDistance(minecraft);
        for (int i = 0; i < count; i++) {
            double[] offset = keyhole != null ? keyhole.get(i) : ReachRingShape.dot(ring.reach(), i, count);
            double x = centerX + offset[0];
            double z = centerZ + offset[1];
            double angle = 2 * Math.PI * i / count;
            float y = Float.NaN;
            double dx = x - player.getX();
            double dz = z - player.getZ();
            if (dx * dx + dz * dz <= max * max) {
                int blockX = (int) Math.floor(x);
                int blockZ = (int) Math.floor(z);
                if (level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    if (ground > level.getMinBuildHeight()) {
                        y = ground;
                    }
                }
            }
            built[i * 5] = (float) x;
            built[i * 5 + 1] = y;
            built[i * 5 + 2] = (float) z;
            built[i * 5 + 3] = (float) (keyhole != null ? offset[2] : -Math.sin(angle));
            built[i * 5 + 4] = (float) (keyhole != null ? offset[3] : Math.cos(angle));
        }
        dots = built;
    }

    /** The ring's color: the sender's light (transmitter red, repeater amber, converter teal, keypad green, motion sensor purple, jammer magenta). */
    private static int color(int kind) {
        return switch (kind) {
            case ReachPreviewPayload.REPEATER -> 0xFFB020;
            case ReachPreviewPayload.CONVERTER -> 0x2CDCE6;
            case ReachPreviewPayload.KEYPAD -> 0x50F050;
            case ReachPreviewPayload.MOTION_SENSOR -> 0xC060FF;
            case ReachPreviewPayload.JAMMER -> 0xFF50C8;
            default -> 0xFF4A3A;
        };
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || target == null || dots.length == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = pose.last().pose();
        int rgb = color(target.kind());
        int dim = (((rgb >> 16 & 0xFF) * 3 / 5) << 16) | (((rgb >> 8 & 0xFF) * 3 / 5) << 8) | ((rgb & 0xFF) * 3 / 5);
        double max = maxDistance(minecraft);
        // A bright stretch runs slowly around the ring, so it reads as a ring, not loose dots.
        long step = minecraft.level.getGameTime() / 3;
        int count = dots.length / 5;
        for (int i = 0; i < count; i++) {
            float y = dots[i * 5 + 1];
            if (Float.isNaN(y)) {
                continue;
            }
            float x = (float) (dots[i * 5] - camera.x);
            float z = (float) (dots[i * 5 + 2] - camera.z);
            if ((double) x * x + (double) z * z > max * max) {
                continue;
            }
            float ry = (float) (y - camera.y);
            int argb = 0xE0000000 | (Math.floorMod(i - step, 12) < 6 ? rgb : dim);
            float tx = dots[i * 5 + 3];
            float tz = dots[i * 5 + 4];
            // Far dots are drawn bigger, so the ring still reads from up high or across a valley.
            float grow = (float) Math.max(1.0, Math.min(3.0, Math.sqrt((double) x * x + (double) z * z + (double) ry * ry) / 32.0));
            // A flat square on the ground...
            float h = 0.22F * grow;
            float flat = ry + 0.02F;
            consumer.addVertex(matrix, x - h, flat, z - h).setColor(argb);
            consumer.addVertex(matrix, x - h, flat, z + h).setColor(argb);
            consumer.addVertex(matrix, x + h, flat, z + h).setColor(argb);
            consumer.addVertex(matrix, x + h, flat, z - h).setColor(argb);
            // ...and a low dash standing along the ring, so it shows over grass and from the side.
            float l = 0.4F * grow;
            float top = ry + 0.3F * grow;
            consumer.addVertex(matrix, x - tx * l, ry, z - tz * l).setColor(argb);
            consumer.addVertex(matrix, x + tx * l, ry, z + tz * l).setColor(argb);
            consumer.addVertex(matrix, x + tx * l, top, z + tz * l).setColor(argb);
            consumer.addVertex(matrix, x - tx * l, top, z - tz * l).setColor(argb);
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    /** The two lines above the hotbar: what the ring is around, and how far the player is from its edge. */
    public static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ReachPreviewPayload ring = target;
        if (player == null || ring == null || minecraft.options.hideGui) {
            return;
        }
        String block = switch (ring.kind()) {
            case ReachPreviewPayload.REPEATER -> "radio_repeater";
            case ReachPreviewPayload.CONVERTER -> "radio_converter";
            case ReachPreviewPayload.KEYPAD -> "code_keypad";
            case ReachPreviewPayload.COMPACT_TRANSMITTER -> "compact_radio_transmitter";
            case ReachPreviewPayload.MOTION_SENSOR -> "radio_motion_sensor";
            case ReachPreviewPayload.TIMER_TRANSMITTER -> "radio_timer_transmitter";
            case ReachPreviewPayload.WEATHER_STATION -> "radio_weather_station";
            case ReachPreviewPayload.BUNDLED_TRANSMITTER -> "bundled_transmitter";
            case ReachPreviewPayload.SIGNAL_RECORDER -> "radio_signal_recorder";
            case ReachPreviewPayload.JAMMER -> "radio_jammer";
            case ReachPreviewPayload.JUKEBOX -> "radio_jukebox";
            default -> "radio_transmitter";
        };
        Component name = Component.translatable("block." + SignalTower.MOD_ID + "." + block);
        Component first = ring.hasDish()
                ? Component.translatable(KEY + "_dish", name, FrequencyNames.label(null, ring.frequency()), ring.reach(), ring.dishReach(),
                        com.reiko.signaltower.radio.AntennaTier.aimLabel(ring.dishAim()))
                : Component.translatable(KEY, name, FrequencyNames.label(null, ring.frequency()), ring.reach());
        long dx = player.getBlockX() - ring.pos().getX();
        long dz = player.getBlockZ() - ring.pos().getZ();
        int edge = ring.hasDish() ? ReachRingShape.edgeDistance(dx, dz, ring.reach(), ring.dishReach(), ring.dishAim(), ring.dishCone())
                : ReachRingShape.edgeDistance(dx, dz, ring.reach());
        Component second = edge > 0 ? Component.translatable(KEY + "_inside", edge).withStyle(ChatFormatting.GREEN)
                : edge == 0 ? Component.translatable(KEY + "_edge").withStyle(ChatFormatting.YELLOW)
                : Component.translatable(KEY + "_outside", -edge).withStyle(ChatFormatting.GOLD);
        int centerX = graphics.guiWidth() / 2;
        // Above the action bar, clear of Jade's tooltip and the scanner's arrow at the top.
        int y = graphics.guiHeight() - 100;
        int width = Math.max(minecraft.font.width(first), minecraft.font.width(second)) + 8;
        graphics.fill(centerX - width / 2, y - 3, centerX + width / 2, y + 21, 0x70000000);
        graphics.drawCenteredString(minecraft.font, first, centerX, y, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, second, centerX, y + 11, 0xFFFFFF);
    }
}
