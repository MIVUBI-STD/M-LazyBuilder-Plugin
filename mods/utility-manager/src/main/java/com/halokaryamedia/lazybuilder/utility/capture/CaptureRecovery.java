package com.halokaryamedia.lazybuilder.utility.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * One-shot startup recovery for interrupted capture sessions.
 *
 * It is dormant when no partial recordings exist. Recovery never overwrites user files:
 * it attempts a stream-copy remux to MP4 and otherwise preserves the MKV payload under
 * a deterministic recovered filename.
 */
final class CaptureRecovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Capture/Recovery");

    private static volatile String status = "No recovery needed";
    private static volatile Thread worker;

    private CaptureRecovery() {}

    static synchronized void start(Path gameDirectory) {
        if (worker != null && worker.isAlive()) return;
        Path directory = gameDirectory == null
                ? null
                : gameDirectory.resolve("captures").resolve("videos");
        if (directory == null || !Files.isDirectory(directory) || !hasPartialRecording(directory)) {
            status = "No recovery needed";
            return;
        }

        Thread next = Thread.ofPlatform()
                .daemon(true)
                .name("LazyBuilder-Capture-Recovery")
                .unstarted(() -> recover(directory, gameDirectory));
        worker = next;
        status = "Checking interrupted recordings…";
        next.start();
    }

    static String status() {
        return status;
    }

    static synchronized void shutdown() {
        Thread current = worker;
        worker = null;
        if (current != null && current != Thread.currentThread() && current.isAlive()) {
            current.interrupt();
        }
    }

    private static boolean hasPartialRecording(Path directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.mkv.partial")) {
            return stream.iterator().hasNext();
        } catch (IOException error) {
            LOGGER.debug("Unable to inspect capture recovery directory {}", directory, error);
            return false;
        }
    }

    private static void recover(Path directory, Path gameDirectory) {
        int recovered = 0;
        int preserved = 0;
        int failed = 0;
        String ffmpeg = FfmpegCapabilities.findExecutable(gameDirectory);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.mkv.partial")) {
            for (Path partial : stream) {
                if (Thread.currentThread().isInterrupted()) return;

                String fileName = partial.getFileName().toString();
                String stem = fileName.substring(0, fileName.length() - ".mkv.partial".length());
                Path mp4 = uniqueTarget(directory, stem + "_recovered", ".mp4");

                if (!ffmpeg.isBlank() && remux(ffmpeg, partial, mp4)) {
                    try {
                        Files.deleteIfExists(partial);
                    } catch (IOException cleanupError) {
                        LOGGER.debug("Unable to remove recovered partial {}", partial, cleanupError);
                    }
                    recovered++;
                    continue;
                }

                Path mkv = uniqueTarget(directory, stem + "_recovered", ".mkv");
                try {
                    Files.move(partial, mkv, StandardCopyOption.REPLACE_EXISTING);
                    preserved++;
                } catch (IOException moveError) {
                    failed++;
                    LOGGER.warn("Unable to preserve interrupted recording {}", partial, moveError);
                }
            }
        } catch (IOException error) {
            LOGGER.warn("Unable to scan interrupted recordings in {}", directory, error);
            status = "Recovery check failed";
            return;
        } finally {
            synchronized (CaptureRecovery.class) {
                if (worker == Thread.currentThread()) worker = null;
            }
        }

        if (failed > 0) {
            status = "Recovery finished · " + failed + " failed";
        } else if (recovered > 0 || preserved > 0) {
            status = "Recovered " + recovered + " · preserved " + preserved;
        } else {
            status = "No recovery needed";
        }
    }

    private static boolean remux(String executable, Path input, Path output) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    executable,
                    "-hide_banner", "-loglevel", "warning", "-y",
                    "-i", input.toString(),
                    "-c", "copy",
                    "-movflags", "+faststart",
                    output.toString()
            ).redirectErrorStream(true).start();

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Files.deleteIfExists(output);
                return false;
            }
            boolean success = process.exitValue() == 0 && Files.isRegularFile(output)
                    && Files.size(output) > 0L;
            if (!success) Files.deleteIfExists(output);
            return success;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {
            }
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    static Path uniqueTarget(Path directory, String stem, String extension) {
        Path candidate = directory.resolve(stem + extension);
        if (!Files.exists(candidate)) return candidate;
        for (int index = 2; index < 10_000; index++) {
            Path next = directory.resolve(stem + "_" + index + extension);
            if (!Files.exists(next)) return next;
        }
        return directory.resolve(stem + "_" + System.nanoTime() + extension);
    }
}
