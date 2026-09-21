package com.halokaryamedia.lazybuilder.utility.capture;

import com.halokaryamedia.lazybuilder.utility.mixin.FramebufferAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One recording session with bounded frame queues and crash-resilient MKV -> MP4 finalization.
 *
 * FFmpeg and the writer thread exist only while recording. The render thread never waits for the
 * encoder; queue pressure drops capture frames instead.
 */
final class VideoCaptureSession {
    enum State {
        IDLE,
        STARTING,
        RECORDING,
        STOPPING,
        REMUXING,
        FINISHED,
        FAILED
    }

    static final class FramePacket {
        private ByteBuffer buffer;
        private int repeatCount;

        FramePacket(int capacity) {
            buffer = ByteBuffer.allocateDirect(capacity);
        }

        ByteBuffer buffer() { return buffer; }

        void ensureCapacity(int capacity) {
            if (buffer.capacity() >= capacity) return;
            buffer = ByteBuffer.allocateDirect(capacity);
        }

        int repeatCount() { return repeatCount; }
        void repeatCount(int value) { repeatCount = Math.max(1, value); }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Capture/Video");
    private static final int FRAME_POOL_SIZE = 3;
    private static final long MIN_FREE_DISK_BYTES = 1024L * 1024L * 1024L;
    private static final long DISK_CHECK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final ArrayBlockingQueue<FramePacket> freeFrames = new ArrayBlockingQueue<>(FRAME_POOL_SIZE);
    private final ArrayBlockingQueue<FramePacket> readyFrames = new ArrayBlockingQueue<>(FRAME_POOL_SIZE);

    private volatile State state = State.IDLE;
    private volatile String status = "Idle";
    private volatile boolean stopRequested;
    private volatile boolean readbackDrained;
    private volatile long submittedFrames;
    private volatile long encodedFrames;
    private volatile long droppedFrames;
    private volatile String activeEncoder = "";
    private volatile Path finalOutput;

    private Thread writerThread;
    private Process ffmpeg;
    private long nextCaptureNanos;
    private int sourceWidth;
    private int sourceHeight;
    private int fps;
    private long nextDiskCheckNanos;

    State state() { return state; }
    String status() { return status; }
    long submittedFrames() { return submittedFrames; }
    long encodedFrames() { return encodedFrames; }
    long droppedFrames() { return droppedFrames; }
    String activeEncoder() { return activeEncoder; }
    Path finalOutput() { return finalOutput; }

    synchronized boolean start(
            File gameDirectory,
            CapturePreferences preferences,
            boolean contextualNames,
            Consumer<Text> notifier,
            int width,
            int height
    ) {
        if (state == State.STARTING || state == State.RECORDING
                || state == State.STOPPING || state == State.REMUXING
                || (writerThread != null && writerThread.isAlive())) {
            return false;
        }
        if (gameDirectory == null || preferences == null || width <= 0 || height <= 0) return false;

        state = State.STARTING;
        status = "Starting…";
        stopRequested = false;
        readbackDrained = false;
        submittedFrames = 0L;
        encodedFrames = 0L;
        droppedFrames = 0L;
        activeEncoder = "";
        finalOutput = null;
        sourceWidth = width;
        sourceHeight = height;
        fps = preferences.videoFrameRate().fps();
        nextCaptureNanos = 0L;
        nextDiskCheckNanos = 0L;
        readyFrames.clear();
        freeFrames.clear();

        long capacityLong = (long) width * (long) height * 4L;
        if (capacityLong <= 0L || capacityLong > Integer.MAX_VALUE) {
            fail("Recording resolution is too large.");
            return false;
        }
        int capacity = (int) capacityLong;
        for (int i = 0; i < FRAME_POOL_SIZE; i++) freeFrames.add(new FramePacket(capacity));

        Path outputDirectory = gameDirectory.toPath().resolve("captures").resolve("videos");
        try {
            Files.createDirectories(outputDirectory);
            if (Files.getFileStore(outputDirectory).getUsableSpace() < MIN_FREE_DISK_BYTES) {
                fail("At least 1 GB of free disk space is required to start recording.");
                return false;
            }
        } catch (IOException error) {
            fail("Unable to prepare the recording folder: " + safeMessage(error));
            return false;
        }

        String stem = CaptureNaming.videoFileStem(contextualNames);
        Path working = outputDirectory.resolve(stem + ".mkv.partial");
        Path mkv = outputDirectory.resolve(stem + ".mkv");
        Path mp4 = outputDirectory.resolve(stem + ".mp4");
        CapturePreferences snapshot = preferences;

        writerThread = Thread.ofPlatform()
                .daemon(true)
                .name("LazyBuilder-Video-Encoder")
                .unstarted(() -> {
                    try {
                        runEncoder(gameDirectory.toPath(), snapshot, working, mkv, mp4, notifier);
                    } finally {
                        synchronized (VideoCaptureSession.this) {
                            writerThread = null;
                        }
                    }
                });
        writerThread.start();
        return true;
    }

    void requestStop() {
        State current = state;
        if (current != State.STARTING && current != State.RECORDING) return;
        stopRequested = true;
        state = State.STOPPING;
        status = "Stopping…";
    }

    boolean shouldCapture(long nowNanos) {
        if (state != State.RECORDING || stopRequested) return false;
        if (nextCaptureNanos == 0L) nextCaptureNanos = nowNanos;
        return nowNanos >= nextCaptureNanos;
    }

    int consumeRepeatCount(long nowNanos) {
        long interval = 1_000_000_000L / Math.max(1, fps);
        if (nextCaptureNanos == 0L) nextCaptureNanos = nowNanos;
        long behind = Math.max(0L, nowNanos - nextCaptureNanos);
        long intervals = 1L + behind / interval;
        nextCaptureNanos += intervals * interval;

        int repeat = (int) Math.min(120L, intervals);
        if (intervals > repeat) droppedFrames += intervals - repeat;
        return repeat;
    }

    boolean sourceMatches(Framebuffer framebuffer) {
        if (framebuffer == null) return false;
        FramebufferAccessor access = (FramebufferAccessor) (Object) framebuffer;
        return access.lazybuilder$getTextureWidth() == sourceWidth
                && access.lazybuilder$getTextureHeight() == sourceHeight;
    }

    FramePacket acquireFramePacket(int capacity) {
        FramePacket packet = freeFrames.poll();
        if (packet == null) return null;
        packet.ensureCapacity(capacity);
        return packet;
    }

    void recycleFramePacket(FramePacket packet) {
        if (packet == null) return;
        packet.repeatCount(1);
        freeFrames.offer(packet);
    }

    void submitFramePacket(FramePacket packet) {
        if (packet == null) return;
        if (!readyFrames.offer(packet)) {
            recordDroppedFrame();
            recycleFramePacket(packet);
            return;
        }
        submittedFrames += packet.repeatCount();
    }

    void recordDroppedFrame() {
        droppedFrames++;
    }

    void markReadbackDrained() {
        readbackDrained = true;
    }

    void fail(String reason) {
        status = reason == null || reason.isBlank() ? "Recording failed" : reason;
        state = State.FAILED;
        stopRequested = true;
        readbackDrained = true;
    }

    void shutdown() {
        requestStop();
        readbackDrained = true;

        Thread worker = writerThread;
        if (worker != null && worker != Thread.currentThread()) {
            try {
                worker.join(6_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        worker = writerThread;
        if (worker != null && worker.isAlive()) {
            Process process = ffmpeg;
            if (process != null && process.isAlive()) process.destroy();
            worker.interrupt();
        }
    }

    private void runEncoder(
            Path gameDirectory,
            CapturePreferences preferences,
            Path working,
            Path mkv,
            Path mp4,
            Consumer<Text> notifier
    ) {
        FfmpegCapabilities.Snapshot capabilities =
                FfmpegCapabilities.detect(gameDirectory, preferences.videoEncoderMode());
        if (!capabilities.available() || capabilities.encoder() == null) {
            fail(capabilities.status());
            publish(notifier, Text.literal("Recording unavailable: " + capabilities.status()));
            return;
        }

        activeEncoder = capabilities.encoder().label();
        List<String> command = encoderCommand(
                capabilities,
                preferences.videoQuality(),
                sourceWidth,
                sourceHeight,
                fps,
                working
        );

        try {
            ffmpeg = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.appendTo(
                            working.resolveSibling(working.getFileName() + ".log").toFile()
                    ))
                    .start();
        } catch (IOException error) {
            fail("Unable to start FFmpeg: " + safeMessage(error));
            publish(notifier, Text.literal(status));
            return;
        }

        state = stopRequested ? State.STOPPING : State.RECORDING;
        status = stopRequested ? "Stopping…" : "Recording · " + activeEncoder;
        publish(notifier, Text.literal("Recording started · " + activeEncoder));

        try (OutputStream output = new BufferedOutputStream(ffmpeg.getOutputStream(), 1024 * 1024);
             WritableByteChannel channel = Channels.newChannel(output)) {
            while (true) {
                if (!stopRequested && !diskSpaceHealthy(working.getParent())) {
                    stopRequested = true;
                    state = State.STOPPING;
                    status = "Stopping · low disk space";
                    publish(notifier, Text.literal("Recording stopped: disk space is running low."));
                }

                FramePacket packet = readyFrames.poll(100, TimeUnit.MILLISECONDS);
                if (packet != null) {
                    try {
                        for (int repeat = 0; repeat < packet.repeatCount(); repeat++) {
                            ByteBuffer duplicate = packet.buffer().duplicate();
                            while (duplicate.hasRemaining()) channel.write(duplicate);
                            encodedFrames++;
                        }
                    } finally {
                        recycleFramePacket(packet);
                    }
                }

                if (stopRequested && readbackDrained && readyFrames.isEmpty()) break;
                if (state == State.FAILED) break;
            }
        } catch (Exception error) {
            fail("Video encoder write failed: " + safeMessage(error));
        }

        Process process = ffmpeg;
        ffmpeg = null;
        if (process == null) return;

        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            fail("Recording finalization was interrupted.");
        }

        if (state == State.FAILED || process.exitValue() != 0) {
            if (state != State.FAILED) fail("FFmpeg exited with code " + process.exitValue());
            publish(notifier, Text.literal(status));
            return;
        }

        try {
            Files.move(working, mkv, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            fail("Unable to finalize MKV recording: " + safeMessage(error));
            publish(notifier, Text.literal(status));
            return;
        }

        state = State.REMUXING;
        status = "Finalizing MP4…";

        if (remux(capabilities.executable(), mkv, mp4)) {
            try {
                Files.deleteIfExists(mkv);
            } catch (IOException cleanupError) {
                LOGGER.debug("Unable to remove remux source {}", mkv, cleanupError);
            }
            finalOutput = mp4;
            state = State.FINISHED;
            status = "Saved · " + mp4.getFileName();
            publish(notifier, Text.literal("Recording saved: " + mp4.getFileName()));
        } else {
            finalOutput = mkv;
            state = State.FINISHED;
            status = "Saved MKV · MP4 remux unavailable";
            publish(notifier, Text.literal("Recording saved as MKV: " + mkv.getFileName()));
        }
    }

    private boolean diskSpaceHealthy(Path outputDirectory) {
        long now = System.nanoTime();
        if (nextDiskCheckNanos != 0L && now < nextDiskCheckNanos) return true;
        nextDiskCheckNanos = now + DISK_CHECK_INTERVAL_NANOS;

        try {
            return Files.getFileStore(outputDirectory).getUsableSpace() >= MIN_FREE_DISK_BYTES;
        } catch (IOException error) {
            LOGGER.debug("Unable to inspect capture disk space for {}", outputDirectory, error);
            return true;
        }
    }

    private static List<String> encoderCommand(
            FfmpegCapabilities.Snapshot capabilities,
            CapturePreferences.VideoQuality quality,
            int width,
            int height,
            int fps,
            Path output
    ) {
        ArrayList<String> command = new ArrayList<>();
        command.add(capabilities.executable());
        command.addAll(List.of(
                "-hide_banner", "-loglevel", "warning", "-y",
                "-f", "rawvideo",
                "-pixel_format", "bgra",
                "-video_size", width + "x" + height,
                "-framerate", Integer.toString(fps),
                "-i", "-",
                "-an",
                "-vf", "vflip"
        ));

        int q = quality.qualityValue();
        switch (capabilities.encoder()) {
            case NVENC -> command.addAll(List.of(
                    "-c:v", "h264_nvenc", "-preset", "p5",
                    "-rc:v", "vbr", "-cq:v", Integer.toString(q), "-b:v", "0"
            ));
            case AMF -> command.addAll(List.of(
                    "-c:v", "h264_amf", "-quality", "quality",
                    "-rc", "cqp", "-qp_i", Integer.toString(q), "-qp_p", Integer.toString(q)
            ));
            case QSV -> command.addAll(List.of(
                    "-c:v", "h264_qsv", "-preset", "medium",
                    "-global_quality", Integer.toString(q)
            ));
            case SOFTWARE -> command.addAll(List.of(
                    "-c:v", "libx264", "-preset", "veryfast",
                    "-crf", Integer.toString(q)
            ));
        }

        command.addAll(List.of("-pix_fmt", "yuv420p", "-f", "matroska", output.toString()));
        return List.copyOf(command);
    }

    private static boolean remux(String executable, Path mkv, Path mp4) {
        Process remux = null;
        try {
            remux = new ProcessBuilder(
                    executable,
                    "-hide_banner", "-loglevel", "warning", "-y",
                    "-i", mkv.toString(),
                    "-c", "copy",
                    "-movflags", "+faststart",
                    mp4.toString()
            ).redirectErrorStream(true).start();
            if (!remux.waitFor(30, TimeUnit.SECONDS)) {
                remux.destroyForcibly();
                return false;
            }
            return remux.exitValue() == 0 && Files.isRegularFile(mp4);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            if (remux != null && remux.isAlive()) remux.destroyForcibly();
        }
    }

    private static void publish(Consumer<Text> notifier, Text message) {
        if (notifier == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.execute(() -> notifier.accept(message));
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.isBlank()
                ? error == null ? "unknown error" : error.getClass().getSimpleName()
                : message;
    }
}
