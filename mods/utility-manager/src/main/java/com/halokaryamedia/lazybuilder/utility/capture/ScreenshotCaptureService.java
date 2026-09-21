package com.halokaryamedia.lazybuilder.utility.capture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bounded screenshot encoder.
 *
 * GPU readback remains on Minecraft's render thread, while image compression and disk I/O are
 * moved to one daemon worker. Queue growth is bounded so repeated capture requests cannot create
 * unbounded memory pressure.
 */
public final class ScreenshotCaptureService {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Capture/Screenshot");
    private static final int MAX_QUEUED_CAPTURES = 2;

    private final ThreadPoolExecutor encoder = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_CAPTURES),
            runnable -> Thread.ofPlatform()
                    .daemon(true)
                    .name("LazyBuilder-Screenshot-Encoder")
                    .unstarted(runnable),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public boolean capture(
            File gameDirectory,
            String requestedFileName,
            Framebuffer framebuffer,
            Consumer<Text> messageReceiver,
            CapturePreferences.ScreenshotQuality quality,
            boolean contextualNames
    ) {
        if (gameDirectory == null || framebuffer == null || messageReceiver == null || quality == null) {
            return false;
        }
        if (encoder.getQueue().remainingCapacity() == 0 && encoder.getActiveCount() >= 1) {
            return false;
        }

        NativeImage nativeImage = ScreenshotRecorder.takeScreenshot(framebuffer);
        if (nativeImage == null) return false;

        final int width = nativeImage.getWidth();
        final int height = nativeImage.getHeight();
        final int[] argb;
        try {
            argb = nativeImage.copyPixelsArgb();
        } finally {
            nativeImage.close();
        }

        Path directory = gameDirectory.toPath().resolve(ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
        String fileName = requestedFileName == null || requestedFileName.isBlank()
                ? CaptureNaming.screenshotFileName(contextualNames, quality)
                : normalizeExtension(requestedFileName, quality.extension());
        Path target = uniqueTarget(directory, fileName);

        try {
            encoder.execute(() -> encodeAndPublish(argb, width, height, target, quality, messageReceiver));
            return true;
        } catch (RejectedExecutionException overloaded) {
            LOGGER.warn("Screenshot encoder queue is full; falling back to vanilla capture");
            return false;
        }
    }

    public void shutdown() {
        encoder.shutdown();
        try {
            if (!encoder.awaitTermination(3L, TimeUnit.SECONDS)) {
                encoder.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            encoder.shutdownNow();
        }
    }

    private static void encodeAndPublish(
            int[] argb,
            int width,
            int height,
            Path target,
            CapturePreferences.ScreenshotQuality quality,
            Consumer<Text> messageReceiver
    ) {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());

            BufferedImage image = new BufferedImage(
                    width,
                    height,
                    quality == CapturePreferences.ScreenshotQuality.MAXIMUM
                            ? BufferedImage.TYPE_INT_ARGB
                            : BufferedImage.TYPE_INT_RGB
            );
            image.setRGB(0, 0, width, height, argb, 0, width);

            if (quality == CapturePreferences.ScreenshotQuality.MAXIMUM) {
                if (!ImageIO.write(image, "png", temporary.toFile())) {
                    throw new IOException("PNG writer is unavailable");
                }
            } else {
                writeJpeg(image, temporary, quality.jpegQuality());
            }

            moveIntoPlace(temporary, target);
            publish(messageReceiver, Text.literal("Saved screenshot: " + target.getFileName()));
        } catch (Exception error) {
            LOGGER.warn("Unable to save screenshot {}", target, error);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                LOGGER.debug("Unable to remove partial screenshot {}", temporary, cleanupError);
            }
            publish(messageReceiver, Text.literal("Screenshot failed: " + safeMessage(error)));
        }
    }

    private static void writeJpeg(BufferedImage image, Path output, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG writer is unavailable");

        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(stream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(Math.max(0.0F, Math.min(1.0F, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path uniqueTarget(Path directory, String requested) {
        Path candidate = directory.resolve(requested);
        if (!Files.exists(candidate)) return candidate;

        String fileName = candidate.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int index = 2; index < 10_000; index++) {
            Path next = directory.resolve(stem + "_" + index + extension);
            if (!Files.exists(next)) return next;
        }
        return directory.resolve(stem + "_" + System.nanoTime() + extension);
    }

    private static String normalizeExtension(String fileName, String extension) {
        String safeName;
        try {
            Path parsed = Path.of(fileName).getFileName();
            safeName = parsed == null ? "minecraft" : parsed.toString();
        } catch (RuntimeException invalidPath) {
            safeName = "minecraft";
        }

        int dot = safeName.lastIndexOf('.');
        if (dot > 0) safeName = safeName.substring(0, dot);
        safeName = CaptureNaming.sanitize(safeName);
        return safeName + extension;
    }

    private static void publish(Consumer<Text> receiver, Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.execute(() -> receiver.accept(message));
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.isBlank()
                ? error == null ? "unknown error" : error.getClass().getSimpleName()
                : message;
    }
}
