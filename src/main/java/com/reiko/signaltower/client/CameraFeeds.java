package com.reiko.signaltower.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.TvCameraBlock;
import com.reiko.signaltower.blockentity.TvCameraBlockEntity;
import com.reiko.signaltower.entity.RadioDroneEntity;
import com.reiko.signaltower.radio.TvCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pictures of the TV Cameras that monitors near the player show (v0.66.0). A monitor asks for
 * a camera's picture each frame it's drawn ({@link #picture}); before each frame a little time
 * ({@link #BUDGET_NANOS}) goes to drawing the next rows of the pictures asked for, in turn
 * ({@link CameraRaycaster}), and a finished picture replaces the old one on its texture, so the
 * pictures stay live at a few frames a second. A picture nobody has asked for in
 * {@link #DROP_NANOS} is dropped. At most {@link #MAX_FEEDS} at once. Also the snow texture.
 */
public final class CameraFeeds {
    public static final int MAX_FEEDS = 8;
    private static final long BUDGET_NANOS = 2_000_000L;
    private static final long DROP_NANOS = 3_000_000_000L;
    /** Rows drawn between looks at the clock. */
    private static final int ROW_STEP = 4;
    public static final ResourceLocation SNOW = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "tv_feed/snow");
    private static final int SNOW_WIDTH = 64;
    private static final int SNOW_HEIGHT = 36;

    /** Why a monitor shows no picture of a camera it was told to show. */
    public enum Status {
        LIVE, STARTING, TOO_FAR, NO_CAMERA
    }

    private static final class Feed {
        /** A TV Camera's BlockPos, or a Radio Drone's entity id (an Integer; v0.67.0). */
        final Object camera;
        final ResourceLocation id;
        final DynamicTexture texture;
        final int[] drawing = new int[CameraRaycaster.WIDTH * CameraRaycaster.HEIGHT];
        CameraRaycaster.Shot shot;
        int nextRow;
        boolean ready;
        long wantedAt;
        int frames;

        Feed(Object camera, int index) {
            this.camera = camera instanceof BlockPos pos ? pos.immutable() : camera;
            this.id = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "tv_feed/" + index);
            this.texture = new DynamicTexture(CameraRaycaster.WIDTH, CameraRaycaster.HEIGHT, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void release() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    private static final Map<Object, Feed> FEEDS = new LinkedHashMap<>();
    private static final List<Feed> ORDER = new ArrayList<>();
    private static int nextIndex;
    private static int turn;
    private static @Nullable DynamicTexture snow;
    private static long snowTick = Long.MIN_VALUE;
    private static final RandomSource RANDOM = RandomSource.create();
    private static @Nullable ClientLevel lastLevel;

    private CameraFeeds() {
    }

    /** What a monitor can show of the camera at {@code camera}: whether it's live, and the texture if so. */
    public record Picture(Status status, @Nullable ResourceLocation texture, int frames) {
    }

    /** Asks for the picture of the camera at {@code camera} (this frame). */
    public static Picture picture(ClientLevel level, BlockPos camera) {
        // (The client level's own hasChunk is always true: ask its chunk cache.)
        if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(camera.getX()), SectionPos.blockToSectionCoord(camera.getZ()))) {
            return new Picture(Status.TOO_FAR, null, 0);
        }
        if (!(level.getBlockEntity(camera) instanceof TvCameraBlockEntity)) {
            return new Picture(Status.NO_CAMERA, null, 0);
        }
        return feed(camera);
    }

    /**
     * Asks for the picture of the Radio Drone {@code droneId} (v0.67.0). A client only has the
     * drones near its player, so one farther away is {@link Status#TOO_FAR}.
     */
    public static Picture pictureOfDrone(ClientLevel level, int droneId) {
        if (!(level.getEntity(droneId) instanceof RadioDroneEntity)) {
            return new Picture(Status.TOO_FAR, null, 0);
        }
        return feed(droneId);
    }

    private static Picture feed(Object camera) {
        Feed feed = FEEDS.get(camera);
        if (feed == null) {
            if (FEEDS.size() >= MAX_FEEDS) {
                // The one wanted least recently goes.
                Feed oldest = null;
                for (Feed other : FEEDS.values()) {
                    if (oldest == null || other.wantedAt < oldest.wantedAt) {
                        oldest = other;
                    }
                }
                if (oldest != null) {
                    drop(oldest);
                }
            }
            feed = new Feed(camera, nextIndex++);
            FEEDS.put(feed.camera, feed);
            ORDER.add(feed);
        }
        feed.wantedAt = System.nanoTime();
        return feed.ready ? new Picture(Status.LIVE, feed.id, feed.frames) : new Picture(Status.STARTING, null, 0);
    }

    private static void drop(Feed feed) {
        FEEDS.remove(feed.camera);
        ORDER.remove(feed);
        feed.release();
    }

    /** All pictures go (leaving a world, or after a resource reload). */
    public static void clear() {
        for (Feed feed : new ArrayList<>(FEEDS.values())) {
            drop(feed);
        }
        CameraRaycaster.clearCaches();
    }

    /** Before each frame: drop pictures nobody wants, draw some rows of the rest, change the snow. */
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        if (level == null || FEEDS.isEmpty() || minecraft.isPaused()) {
            return;
        }
        long now = System.nanoTime();
        for (Iterator<Feed> it = ORDER.iterator(); it.hasNext(); ) {
            Feed feed = it.next();
            if (now - feed.wantedAt > DROP_NANOS) {
                it.remove();
                FEEDS.remove(feed.camera);
                feed.release();
            }
        }
        if (ORDER.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        long deadline = now + BUDGET_NANOS;
        int tries = 0;
        while (System.nanoTime() < deadline && tries < ORDER.size() * 2) {
            Feed feed = ORDER.get(Math.floorMod(turn, ORDER.size()));
            if (!draw(level, feed, partialTick, deadline)) {
                turn++; // finished a picture (or can't): the next camera's turn
            }
            tries++;
        }
    }

    /** Draws rows of {@code feed} until {@code deadline}. Returns true if there's more of this picture to draw. */
    private static boolean draw(ClientLevel level, Feed feed, float partialTick, long deadline) {
        if (feed.shot == null && feed.camera instanceof Integer droneId) {
            if (!(level.getEntity(droneId) instanceof RadioDroneEntity drone)) {
                return false;
            }
            feed.shot = CameraRaycaster.droneShot(level, droneId, drone.cameraPose(partialTick), drone.has(RadioDroneEntity.NIGHT_VISION), partialTick);
            feed.nextRow = 0;
        }
        if (feed.shot == null && feed.camera instanceof BlockPos cameraPos) {
            BlockState state = level.getBlockState(cameraPos);
            if (!(state.getBlock() instanceof TvCameraBlock) || !(level.getBlockEntity(cameraPos) instanceof TvCameraBlockEntity camera)) {
                return false;
            }
            int settings = camera.getSettings();
            TvCamera.Pose pose = TvCamera.pose(cameraPos, state, settings, level.getGameTime(), partialTick);
            feed.shot = CameraRaycaster.shot(level, cameraPos, pose, (settings & TvCamera.NIGHT_VISION) != 0, partialTick);
            feed.nextRow = 0;
        }
        while (feed.nextRow < CameraRaycaster.HEIGHT) {
            int to = Math.min(CameraRaycaster.HEIGHT, feed.nextRow + ROW_STEP);
            CameraRaycaster.rows(feed.shot, feed.drawing, feed.nextRow, to);
            feed.nextRow = to;
            if (System.nanoTime() >= deadline && feed.nextRow < CameraRaycaster.HEIGHT) {
                return true;
            }
        }
        NativeImage image = feed.texture.getPixels();
        if (image != null) {
            CameraRaycaster.copy(feed.drawing, image);
            feed.texture.upload();
        }
        feed.ready = true;
        feed.frames++;
        feed.shot = null;
        return false;
    }

    /** The snow texture, new noise every other tick. */
    public static ResourceLocation snow(ClientLevel level) {
        if (snow == null) {
            snow = new DynamicTexture(SNOW_WIDTH, SNOW_HEIGHT, true);
            Minecraft.getInstance().getTextureManager().register(SNOW, snow);
        }
        long tick = level.getGameTime() / 2;
        if (tick != snowTick && snow.getPixels() != null) {
            snowTick = tick;
            NativeImage image = snow.getPixels();
            for (int y = 0; y < SNOW_HEIGHT; y++) {
                for (int x = 0; x < SNOW_WIDTH; x++) {
                    int v = 40 + RANDOM.nextInt(216);
                    image.setPixelRGBA(x, y, 0xFF000000 | v << 16 | v << 8 | v);
                }
            }
            snow.upload();
        }
        return SNOW;
    }

    /** How many pictures there are now (for the GameTests' client check and the debug line). */
    public static int count() {
        return FEEDS.size();
    }
}
