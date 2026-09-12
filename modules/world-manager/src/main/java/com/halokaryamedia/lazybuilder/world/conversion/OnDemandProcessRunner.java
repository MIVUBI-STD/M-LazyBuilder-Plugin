package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Starts one child process for one request and never keeps an idle daemon. */
public final class OnDemandProcessRunner {
    private static final long MAX_CAPTURE_BYTES = 1024L * 1024L;

    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Path work = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        Objects.requireNonNull(timeout, "timeout");
        if (command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        if (!Files.isDirectory(work)) throw new IOException("Process working directory does not exist: " + work);

        Path log = work.resolve(".lazybuilder-process-" + UUID.randomUUID() + ".log");
        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                throw new IOException("Conversion worker timed out after " + timeout + ": " + readTail(log));
            }
            return new ProcessResult(process.exitValue(), readTail(log));
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
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
