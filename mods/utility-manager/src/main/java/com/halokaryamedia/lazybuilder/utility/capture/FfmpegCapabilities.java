package com.halokaryamedia.lazybuilder.utility.capture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Probes FFmpeg and chooses an H.264 encoder that can actually initialize on this machine. */
final class FfmpegCapabilities {
    enum Encoder {
        NVENC("NVIDIA NVENC", "h264_nvenc"),
        AMF("AMD AMF", "h264_amf"),
        QSV("Intel Quick Sync", "h264_qsv"),
        SOFTWARE("Software", "libx264");

        private final String label;
        private final String ffmpegName;

        Encoder(String label, String ffmpegName) {
            this.label = label;
            this.ffmpegName = ffmpegName;
        }

        String label() { return label; }
        String ffmpegName() { return ffmpegName; }
    }

    record Snapshot(String executable, Encoder encoder, boolean available, String status) {
        Snapshot {
            executable = executable == null ? "" : executable;
            status = status == null ? "" : status;
        }
    }

    private static volatile Snapshot automaticCache;
    private static volatile Snapshot softwareCache;

    private FfmpegCapabilities() {}

    static Snapshot detect(Path gameDirectory, CapturePreferences.VideoEncoderMode mode) {
        Snapshot cached = mode == CapturePreferences.VideoEncoderMode.SOFTWARE
                ? softwareCache
                : automaticCache;
        if (cached != null && cached.available()) return cached;

        for (String executable : candidates(gameDirectory)) {
            Snapshot snapshot = probe(executable, mode);
            if (!snapshot.available()) continue;
            if (mode == CapturePreferences.VideoEncoderMode.SOFTWARE) {
                softwareCache = snapshot;
            } else {
                automaticCache = snapshot;
            }
            return snapshot;
        }
        return new Snapshot("", null, false, "FFmpeg not found or no encoder could initialize");
    }

    private static Snapshot probe(String executable, CapturePreferences.VideoEncoderMode mode) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "-hide_banner", "-encoders")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(6, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Snapshot(executable, null, false, "FFmpeg probe timed out");
            }
            byte[] output = process.getInputStream().readAllBytes();
            if (process.exitValue() != 0) {
                return new Snapshot(executable, null, false, "FFmpeg probe failed");
            }

            String encoders = new String(output, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (Encoder encoder : candidates(encoders, mode)) {
                if (encoderInitializes(executable, encoder)) {
                    return new Snapshot(executable, encoder, true, "Ready · " + encoder.label());
                }
            }
            return new Snapshot(executable, null, false, "No supported H.264 encoder could initialize");
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Snapshot(executable, null, false, safeMessage(error));
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static List<Encoder> candidates(
            String encoders,
            CapturePreferences.VideoEncoderMode mode
    ) {
        ArrayList<Encoder> result = new ArrayList<>();
        if (mode == CapturePreferences.VideoEncoderMode.SOFTWARE) {
            if (encoders.contains(Encoder.SOFTWARE.ffmpegName())) result.add(Encoder.SOFTWARE);
            return List.copyOf(result);
        }

        for (Encoder encoder : List.of(Encoder.NVENC, Encoder.AMF, Encoder.QSV, Encoder.SOFTWARE)) {
            if (encoders.contains(encoder.ffmpegName())) result.add(encoder);
        }
        return List.copyOf(result);
    }

    private static boolean encoderInitializes(String executable, Encoder encoder) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    executable,
                    "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi",
                    "-i", "color=c=black:s=16x16:r=1",
                    "-frames:v", "1",
                    "-c:v", encoder.ffmpegName(),
                    "-f", "null",
                    "-"
            ).redirectErrorStream(true).start();

            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static List<String> candidates(Path gameDirectory) {
        ArrayList<String> candidates = new ArrayList<>();
        addIfPresent(candidates, System.getProperty("lazybuilder.capture.ffmpeg"));
        addIfPresent(candidates, System.getenv("LAZYBUILDER_FFMPEG"));

        if (gameDirectory != null) {
            Path bundledWindows = gameDirectory.resolve("lazybuilder").resolve("tools").resolve("ffmpeg.exe");
            Path bundledUnix = gameDirectory.resolve("lazybuilder").resolve("tools").resolve("ffmpeg");
            if (Files.isRegularFile(bundledWindows)) candidates.add(bundledWindows.toString());
            if (Files.isRegularFile(bundledUnix)) candidates.add(bundledUnix.toString());
        }

        candidates.add(isWindows() ? "ffmpeg.exe" : "ffmpeg");
        return List.copyOf(candidates);
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) values.add(value);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.isBlank()
                ? error == null ? "Unknown FFmpeg error" : error.getClass().getSimpleName()
                : message;
    }
}
