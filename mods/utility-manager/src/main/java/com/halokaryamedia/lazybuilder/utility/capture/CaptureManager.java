package com.halokaryamedia.lazybuilder.utility.capture;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.mixin.FramebufferAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Single runtime owner for screenshot and realtime video capture. */
public final class CaptureManager {
    private static final ScreenshotCaptureService SCREENSHOTS = new ScreenshotCaptureService();
    private static final VideoCaptureSession VIDEO = new VideoCaptureSession();

    private static CaptureConfigStore configStore;
    private static volatile CapturePreferences preferences = CapturePreferences.defaults();
    private static FrameReadbackRing readbackRing;

    private CaptureManager() {}

    public static void initialize(Path configDirectory) {
        configStore = new CaptureConfigStore(configDirectory);
        preferences = configStore.load();
    }

    public static CapturePreferences preferences() {
        return preferences;
    }

    public static void updatePreferences(CapturePreferences updated) {
        preferences = Objects.requireNonNull(updated, "updated");
        CaptureConfigStore store = configStore;
        if (store != null) store.save(updated);
    }

    public static boolean captureScreenshot(
            File gameDirectory,
            String requestedFileName,
            Framebuffer framebuffer,
            Consumer<Text> messageReceiver
    ) {
        CapturePreferences current = preferences;
        return SCREENSHOTS.capture(
                gameDirectory,
                requestedFileName,
                framebuffer,
                messageReceiver,
                current.screenshotQuality(),
                UtilityManagerClient.preferences().contextualScreenshotNames()
        );
    }

    public static void toggleVideo(MinecraftClient client) {
        if (client == null || client.getFramebuffer() == null) return;

        VideoCaptureSession.State state = VIDEO.state();
        if (state == VideoCaptureSession.State.STARTING
                || state == VideoCaptureSession.State.RECORDING) {
            VIDEO.requestStop();
            notify(client, Text.literal("Stopping recording…"));
            return;
        }
        if (state == VideoCaptureSession.State.STOPPING
                || state == VideoCaptureSession.State.REMUXING) {
            notify(client, Text.literal("Recording is already being finalized."));
            return;
        }

        if (RenderSystem.isOnRenderThread()) {
            if (readbackRing != null) readbackRing.close();
            readbackRing = new FrameReadbackRing();
        }

        Framebuffer framebuffer = client.getFramebuffer();
        boolean started = VIDEO.start(
                FabricLoader.getInstance().getGameDir().toFile(),
                preferences,
                UtilityManagerClient.preferences().contextualScreenshotNames(),
                text -> notify(client, text),
                ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureWidth(),
                ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureHeight()
        );
        if (!started) notify(client, Text.literal(VIDEO.status()));
    }

    /** Called once after a complete rendered frame. */
    public static void onFrameRendered(Framebuffer framebuffer, long nowNanos) {
        VideoCaptureSession.State state = VIDEO.state();

        if (state == VideoCaptureSession.State.RECORDING) {
            if (readbackRing == null) {
                if (!RenderSystem.isOnRenderThread()) return;
                readbackRing = new FrameReadbackRing();
            }
            readbackRing.drainReady(VIDEO);

            if (!VIDEO.sourceMatches(framebuffer)) {
                VIDEO.requestStop();
                notify(MinecraftClient.getInstance(), Text.literal(
                        "Recording stopped because the game resolution changed."
                ));
            } else if (VIDEO.shouldCapture(nowNanos)) {
                int repeatCount = VIDEO.consumeRepeatCount(nowNanos);
                readbackRing.capture(framebuffer, repeatCount, VIDEO);
            }
            return;
        }

        if (state == VideoCaptureSession.State.STOPPING) {
            FrameReadbackRing ring = readbackRing;
            if (ring == null) {
                VIDEO.markReadbackDrained();
                return;
            }
            ring.drainReady(VIDEO);
            if (!ring.hasPending()) VIDEO.markReadbackDrained();
            return;
        }

        if ((state == VideoCaptureSession.State.FINISHED
                || state == VideoCaptureSession.State.FAILED
                || state == VideoCaptureSession.State.IDLE)
                && RenderSystem.isOnRenderThread()) {
            FrameReadbackRing ring = readbackRing;
            if (ring != null) {
                ring.dropPending();
                ring.close();
                readbackRing = null;
            }
        }
    }

    public static String videoStatus() {
        return VIDEO.status();
    }

    public static String videoEncoderLabel() {
        String encoder = VIDEO.activeEncoder();
        return encoder.isBlank() ? preferences.videoEncoderMode().label() : encoder;
    }

    public static String effectiveVideoResolutionLabel() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
        int width = framebuffer == null ? 1920 : ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureWidth();
        int height = framebuffer == null ? 1080 : ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureHeight();
        return CaptureEstimates.effectiveVideoResolution(preferences, width, height);
    }

    public static String estimatedVideoSizeLabel() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
        int width = framebuffer == null ? 1920 : ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureWidth();
        int height = framebuffer == null ? 1080 : ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureHeight();
        return CaptureEstimates.videoApproximation(preferences, width, height);
    }

    public static String estimatedScreenshotSizeLabel() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
        int width = framebuffer == null ? 1920 : ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureWidth();
        int height = framebuffer == null ? 1080 : ((FramebufferAccessor) (Object) framebuffer).lazybuilder$getTextureHeight();
        return CaptureEstimates.screenshotApproximation(preferences.screenshotQuality(), width, height);
    }

    public static long videoDroppedFrames() {
        return VIDEO.droppedFrames();
    }

    public static void shutdown() {
        SCREENSHOTS.shutdown();
        if (RenderSystem.isOnRenderThread()) {
            FrameReadbackRing ring = readbackRing;
            if (ring != null) {
                ring.dropPending();
                ring.close();
                readbackRing = null;
            }
        }
        VIDEO.markReadbackDrained();
        VIDEO.shutdown();
    }

    private static void notify(MinecraftClient client, Text text) {
        if (client == null || text == null) return;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(text, false);
            }
        });
    }
}
