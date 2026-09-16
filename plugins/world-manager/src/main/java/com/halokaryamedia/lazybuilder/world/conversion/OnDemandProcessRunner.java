package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Starts one child process for one request and never keeps an idle daemon. */
public final class OnDemandProcessRunner {
    private static final long MAX_CAPTURE_BYTES = 1024L * 1024L;
    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(2);

    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Path work = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        Objects.requireNonNull(timeout, "timeout");
        if (command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (!Files.isDirectory(work)) throw new IOException("Process working directory does not exist: " + work);

        String token = UUID.randomUUID().toString();
        Path stdoutLog = work.resolve(".lazybuilder-process-" + token + ".out.log");
        Path stderrLog = work.resolve(".lazybuilder-process-" + token + ".err.log");
        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(false)
                .redirectOutput(stdoutLog.toFile())
                .redirectError(stderrLog.toFile())
                .start();
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminateProcessTree(process);
                String output = readCombined(stdoutLog, stderrLog);
                throw new IOException("Conversion worker timed out after " + timeout + ": " + output);
            }
            return new ProcessResult(process.exitValue(), readCombined(stdoutLog, stderrLog));
        } catch (InterruptedException interrupted) {
            try {
                terminateProcessTree(process);
            } catch (InterruptedException cleanupInterrupted) {
                interrupted.addSuppressed(cleanupInterrupted);
            }
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
            Files.deleteIfExists(stdoutLog);
            Files.deleteIfExists(stderrLog);
        }
    }

    private static String readCombined(Path stdoutLog, Path stderrLog) throws IOException {
        String stdout = readTail(stdoutLog);
        String stderr = readTail(stderrLog);
        if (stderr.isBlank()) return stdout;
        if (stdout.isBlank()) return stderr;
        return stdout + (stdout.endsWith("\n") ? "" : "\n") + "[stderr]\n" + stderr;
    }

    private static void terminateProcessTree(Process process) throws InterruptedException {
        List<ProcessHandle> handles = new ArrayList<>(process.descendants().toList());
        handles.add(process.toHandle());

        destroy(handles, false);
        if (waitForExit(handles, TERMINATION_GRACE)) return;

        destroy(handles, true);
        waitForExit(handles, TERMINATION_GRACE);
    }

    private static void destroy(List<ProcessHandle> handles, boolean forcibly) {
        for (int index = handles.size() - 1; index >= 0; index--) {
            ProcessHandle handle = handles.get(index);
            if (!handle.isAlive()) continue;
            if (forcibly) handle.destroyForcibly();
            else handle.destroy();
        }
    }

    private static boolean waitForExit(List<ProcessHandle> handles, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (handles.stream().noneMatch(ProcessHandle::isAlive)) return true;
            Thread.sleep(25L);
        }
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }

    private static String readTail(Path log) throws IOException {
        if (Files.notExists(log)) return "";
        long size = Files.size(log);
        long start = Math.max(0L, size - MAX_CAPTURE_BYTES);
        try (var channel = Files.newByteChannel(log)) {
            channel.position(start);
            byte[] bytes = new byte[(int) (size - start)];
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Read only the bounded tail.
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            return start == 0L ? text : "[output truncated to last 1 MiB]\n" + text;
        }
    }

    public record ProcessResult(int exitCode, String output) {}
}
