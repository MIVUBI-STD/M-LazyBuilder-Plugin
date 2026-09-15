package com.halokaryamedia.lazybuilder.world.transfer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Startup-only cleanup for interrupted transfer-owned partial upload files. */
public final class TransferCrashRecovery {
    private static final String UPLOAD_PART_SUFFIX = ".upload.part";

    private TransferCrashRecovery() { }

    public static int recover(Path tempRoot) throws IOException {
        Path root = Objects.requireNonNull(tempRoot, "tempRoot").toAbsolutePath().normalize();
        if (Files.notExists(root)) return 0;
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("Transfer temp root is unsafe");
        }

        int recovered = 0;
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                Path candidate = entry.toAbsolutePath().normalize();
                if (!root.equals(candidate.getParent())) continue;
                if (!isOwnedInterruptedUpload(candidate.getFileName().toString())) continue;

                if (Files.isSymbolicLink(candidate)) {
                    Files.deleteIfExists(candidate);
                    recovered++;
                    continue;
                }
                if (!Files.isRegularFile(candidate)) continue;

                Files.deleteIfExists(candidate);
                recovered++;
            }
        }
        return recovered;
    }

    static boolean isOwnedInterruptedUpload(String fileName) {
        if (fileName == null || !fileName.endsWith(UPLOAD_PART_SUFFIX)) return false;
        String session = fileName.substring(0, fileName.length() - UPLOAD_PART_SUFFIX.length());
        try {
            UUID.fromString(session);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
