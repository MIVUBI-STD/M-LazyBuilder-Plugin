package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Starts one child process for one request and never keeps an idle daemon. */
public final class OnDemandProcessRunner {
    private static final int MAX_CAPTURE_BYTES = 1024 * 1024;
    private static final int STREAM_BUFFER_BYTES = 64 * 1024;
    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(2);
    private static final Duration CAPTURE_JOIN_TIMEOUT = Duration.ofSeconds(2);

    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Path work = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath()
                .normalize();
        Objects.requireNonNull(timeout, "timeout");
        if (command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (!Files.isDirectory(work)) {
            throw new IOException("Process working directory does not exist: " + work);
        }

        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(false)
                .start();

        StreamCapture stdoutCapture = new StreamCapture(
                process.getInputStream(),
                new BoundedTailBuffer(MAX_CAPTURE_BYTES));
        StreamCapture stderrCapture = new StreamCapture(
                process.getErrorStream(),
                new BoundedTailBuffer(MAX_CAPTURE_BYTES));
        Thread stdoutThread = Thread.ofVirtual()
                .name("lazybuilder-conversion-stdout")
                .start(stdoutCapture);
        Thread stderrThread = Thread.ofVirtual()
                .name("lazybuilder-conversion-stderr")
                .start(stderrCapture);

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                boolean terminated = terminateProcessTree(process);
                awaitCapture(stdoutThread, stdoutCapture, "stdout");
                awaitCapture(stderrThread, stderrCapture, "stderr");
                String output = readCombined(stdoutCapture, stderrCapture);
                if (!terminated) {
                    throw new IOException(
                            "Conversion worker timed out and its process tree did not terminate: "
                                    + output);
                }
                throw new IOException(
                        "Conversion worker timed out after " + timeout + ": " + output);
            }

            awaitCapture(stdoutThread, stdoutCapture, "stdout");
            awaitCapture(stderrThread, stderrCapture, "stderr");
            return new ProcessResult(
                    process.exitValue(),
                    readCombined(stdoutCapture, stderrCapture));
        } catch (InterruptedException interrupted) {
            try {
                terminateProcessTree(process);
            } catch (InterruptedException cleanupInterrupted) {
                interrupted.addSuppressed(cleanupInterrupted);
            }
            stdoutThread.interrupt();
            stderrThread.interrupt();
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            if (process.isAlive()) {
                try {
                    terminateProcessTree(process);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            stdoutThread.interrupt();
            stderrThread.interrupt();
        }
    }

    private static void awaitCapture(
            Thread thread,
            StreamCapture capture,
            String stream
    ) throws IOException, InterruptedException {
        thread.join(CAPTURE_JOIN_TIMEOUT.toMillis());
        if (thread.isAlive()) {
            thread.interrupt();
            throw new IOException(
                    "Conversion worker " + stream + " capture did not terminate cleanly");
        }
        if (capture.failure() != null) {
            throw new IOException(
                    "Could not capture conversion worker " + stream,
                    capture.failure());
        }
    }

    private static String readCombined(StreamCapture stdoutCapture, StreamCapture stderrCapture) {
        String stdout = stdoutCapture.buffer().text("stdout");
        String stderr = stderrCapture.buffer().text("stderr");
        if (stderr.isBlank()) return stdout;
        if (stdout.isBlank()) return "[stderr]\n" + stderr;
        return "[stdout]\n" + stdout
                + (stdout.endsWith("\n") ? "" : "\n")
                + "[stderr]\n" + stderr;
    }

    private static boolean terminateProcessTree(Process process) throws InterruptedException {
        List<ProcessHandle> handles = new ArrayList<>(process.descendants().toList());
        handles.add(process.toHandle());

        destroy(handles, false);
        if (waitForExit(handles, TERMINATION_GRACE)) return true;

        destroy(handles, true);
        return waitForExit(handles, TERMINATION_GRACE);
    }

    private static void destroy(List<ProcessHandle> handles, boolean forcibly) {
        for (int index = handles.size() - 1; index >= 0; index--) {
            ProcessHandle handle = handles.get(index);
            if (!handle.isAlive()) continue;
            if (forcibly) handle.destroyForcibly();
            else handle.destroy();
        }
    }

    private static boolean waitForExit(List<ProcessHandle> handles, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (handles.stream().noneMatch(ProcessHandle::isAlive)) return true;
            Thread.sleep(25L);
        }
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }

    static final class BoundedTailBuffer {
        private final byte[] bytes;
        private long totalBytes;
        private int start;
        private int size;

        BoundedTailBuffer(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
            this.bytes = new byte[capacity];
        }

        synchronized void append(byte[] source, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, source.length);
            if (length == 0) return;
            totalBytes = Math.addExact(totalBytes, length);

            if (length >= bytes.length) {
                System.arraycopy(
                        source,
                        offset + length - bytes.length,
                        bytes,
                        0,
                        bytes.length);
                start = 0;
                size = bytes.length;
                return;
            }

            int overflow = Math.max(0, size + length - bytes.length);
            if (overflow > 0) {
                start = (start + overflow) % bytes.length;
                size -= overflow;
            }

            int end = (start + size) % bytes.length;
            int first = Math.min(length, bytes.length - end);
            System.arraycopy(source, offset, bytes, end, first);
            int remaining = length - first;
            if (remaining > 0) {
                System.arraycopy(source, offset + first, bytes, 0, remaining);
            }
            size += length;
        }

        synchronized byte[] snapshot() {
            byte[] result = new byte[size];
            if (size == 0) return result;
            int first = Math.min(size, bytes.length - start);
            System.arraycopy(bytes, start, result, 0, first);
            if (size > first) {
                System.arraycopy(bytes, 0, result, first, size - first);
            }
            return result;
        }

        synchronized boolean truncated() {
            return totalBytes > bytes.length;
        }

        synchronized String text(String stream) {
            String value = new String(snapshot(), StandardCharsets.UTF_8);
            if (!truncated()) return value;
            return "[" + stream + " truncated]\n" + value;
        }
    }

    private static final class StreamCapture implements Runnable {
        private final InputStream input;
        private final BoundedTailBuffer buffer;
        private volatile IOException failure;

        private StreamCapture(InputStream input, BoundedTailBuffer buffer) {
            this.input = Objects.requireNonNull(input, "input");
            this.buffer = Objects.requireNonNull(buffer, "buffer");
        }

        @Override
        public void run() {
            try (InputStream source = input) {
                byte[] chunk = new byte[STREAM_BUFFER_BYTES];
                for (int read; (read = source.read(chunk)) >= 0;) {
                    if (read > 0) buffer.append(chunk, 0, read);
                }
            } catch (IOException error) {
                failure = error;
            }
        }

        private BoundedTailBuffer buffer() {
            return buffer;
        }

        private IOException failure() {
            return failure;
        }
    }

    public record ProcessResult(int exitCode, String output) {}
}
