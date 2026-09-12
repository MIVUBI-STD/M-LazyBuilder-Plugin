package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Starts one child process for one request and never keeps an idle daemon. */
public final class OnDemandProcessRunner {
    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(timeout, "timeout");
        if (command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");

        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                throw new IOException("Conversion worker timed out after " + timeout);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.exitValue(), output);
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    public record ProcessResult(int exitCode, String output) {}
}
