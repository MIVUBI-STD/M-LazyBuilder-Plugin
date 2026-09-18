package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded transport-owned persistence for one pending completion payload per player.
 *
 * <p>The wire payload remains the authority for what is delivered. This store only keeps
 * an offline completion durable across plugin/server restart and never owns export artifacts
 * or world-operation state.</p>
 */
final class PendingMapExportCompletionStore {
    private static final String PAYLOAD_SUFFIX = ".bin";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final int DEFAULT_MAX_PENDING = 2_048;

    private final Path directory;
    private final int maxPayloadBytes;
    private final int maxPending;

    PendingMapExportCompletionStore(Path directory) {
        this(directory, MapActionWireProtocol.MAX_MESSAGE_BYTES, DEFAULT_MAX_PENDING);
    }

    PendingMapExportCompletionStore(Path directory, int maxPending) {
        this(directory, MapActionWireProtocol.MAX_MESSAGE_BYTES, maxPending);
    }

    static PendingMapExportCompletionStore withPayloadLimit(Path directory, int maxPayloadBytes) {
        return new PendingMapExportCompletionStore(directory, maxPayloadBytes, DEFAULT_MAX_PENDING);
    }

    PendingMapExportCompletionStore(Path directory, int maxPayloadBytes, int maxPending) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (maxPayloadBytes < 1) throw new IllegalArgumentException("maxPayloadBytes must be positive");
        if (maxPending < 1) throw new IllegalArgumentException("maxPending must be positive");
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxPending = maxPending;
    }

    void put(UUID owner, byte[] payload) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0 || payload.length > maxPayloadBytes) {
            throw new IOException("Pending completion payload size is invalid: " + payload.length);
        }

        requireDirectory();
        Path target = payloadPath(owner);
        Path temp = tempPath(owner);
        if (Files.exists(temp)) requireRegularDirectFile(temp);
        try {
            Files.write(temp, payload,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        pruneOldestIfNeeded();
    }

    byte[] take(UUID owner) throws IOException {
        Objects.requireNonNull(owner, "owner");
        if (Files.notExists(directory)) return null;
        requireExistingDirectory();
        Path target = payloadPath(owner);
        if (Files.notExists(target)) return null;
        requireRegularDirectFile(target);

        long size = Files.size(target);
        if (size <= 0L || size > maxPayloadBytes) {
            Files.delete(target);
            throw new IOException("Discarded invalid pending completion payload for " + owner);
        }
        byte[] payload = Files.readAllBytes(target);
        Files.delete(target);
        return payload;
    }

    int recoverTemps() throws IOException {
        if (Files.notExists(directory)) return 0;
        requireExistingDirectory();
        int recovered = 0;
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(TEMP_SUFFIX)) continue;
                requireRegularDirectFile(path);
                Files.delete(path);
                recovered++;
            }
        }
        pruneOldestIfNeeded();
        return recovered;
    }

    private void pruneOldestIfNeeded() throws IOException {
        if (Files.notExists(directory)) return;
        requireExistingDirectory();
        List<Path> payloads;
        try (var files = Files.list(directory)) {
            payloads = files
                    .filter(path -> path.getFileName().toString().endsWith(PAYLOAD_SUFFIX))
                    .toList();
        }
        if (payloads.size() <= maxPending) return;

        List<PendingPayload> ordered = new ArrayList<>(payloads.size());
        for (Path path : payloads) {
            requireRegularDirectFile(path);
            ordered.add(new PendingPayload(path, Files.getLastModifiedTime(path).toMillis()));
        }
        ordered.sort(Comparator
                .comparingLong(PendingPayload::lastModifiedMillis)
                .thenComparing(payload -> payload.path().getFileName().toString()));

        int excess = ordered.size() - maxPending;
        for (int i = 0; i < excess; i++) Files.deleteIfExists(ordered.get(i).path());
    }

    private void requireDirectory() throws IOException {
        Files.createDirectories(directory);
        requireExistingDirectory();
    }

    private void requireExistingDirectory() throws IOException {
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) {
            throw new IOException("Pending completion directory is unsafe");
        }
    }

    private void requireRegularDirectFile(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!directory.equals(normalized.getParent())
                || !Files.isRegularFile(normalized)
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("Pending completion file is unsafe: " + path.getFileName());
        }
    }

    private Path payloadPath(UUID owner) {
        return direct(owner + PAYLOAD_SUFFIX);
    }

    private Path tempPath(UUID owner) {
        return direct(owner + TEMP_SUFFIX);
    }

    private Path direct(String name) {
        Path path = directory.resolve(name).normalize();
        if (!directory.equals(path.getParent())) {
            throw new IllegalArgumentException("Pending completion path escaped its directory");
        }
        return path;
    }

    private record PendingPayload(Path path, long lastModifiedMillis) { }
}
