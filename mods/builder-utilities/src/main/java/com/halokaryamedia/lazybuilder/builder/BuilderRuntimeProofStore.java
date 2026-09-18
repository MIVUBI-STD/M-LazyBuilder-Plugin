package com.halokaryamedia.lazybuilder.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Atomic JSON proof snapshots for real Builder runtime sessions. */
public final class BuilderRuntimeProofStore {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);

    private final Path directory;
    private final long sessionStartedEpochMillis;
    private final String sessionId;

    public BuilderRuntimeProofStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        this.sessionStartedEpochMillis = System.currentTimeMillis();
        this.sessionId = UUID.randomUUID().toString();
    }

    public Path directory() {
        return directory;
    }

    public synchronized BuilderRuntimeProofEvidence aggregateEvidence()
            throws IOException {
        if (!Files.isDirectory(directory)) {
            return BuilderRuntimeProofEvidence.empty();
        }

        long snapshots = 0;
        long clean = 0;
        long rejected = 0;
        long completed = 0;
        long cancelled = 0;
        long maxBlocks = 0;
        long maxExtensions = 0;
        long rollbackBlocks = 0;
        long rollbackBiomes = 0;
        long rollbackEntities = 0;
        long forwardBiomes = 0;
        long forwardEntities = 0;

        try (var stream = Files.list(directory)) {
            for (Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                JsonObject json;
                try {
                    JsonElement parsed = JsonParser.parseString(
                            Files.readString(path, StandardCharsets.UTF_8));
                    if (!parsed.isJsonObject()) continue;
                    json = parsed.getAsJsonObject();
                } catch (RuntimeException malformed) {
                    continue;
                }

                snapshots++;
                boolean cleanSnapshot =
                        longValue(json, "operationsFailed") == 0
                        && longValue(json, "budgetExceeded") == 0
                        && longValue(json, "extensionFailures") == 0
                        && longValue(json, "historyReplayFailures") == 0;
                if (!cleanSnapshot) {
                    rejected++;
                    continue;
                }

                clean++;
                completed = Math.max(completed, longValue(json, "operationsCompleted"));
                cancelled = Math.max(cancelled, longValue(json, "operationsCancelled"));
                maxBlocks = Math.max(
                        maxBlocks,
                        longValue(json, "maxCompletedPlannedBlocks"));
                maxExtensions = Math.max(
                        maxExtensions,
                        longValue(json, "maxCompletedPlannedExtensions"));
                rollbackBlocks = Math.max(
                        rollbackBlocks,
                        longValue(json, "rollbackBlocksDispatched"));
                rollbackBiomes = Math.max(
                        rollbackBiomes,
                        longValue(json, "rollbackBiomeExtensions"));
                rollbackEntities = Math.max(
                        rollbackEntities,
                        longValue(json, "rollbackEntityExtensions"));
                forwardBiomes = Math.max(
                        forwardBiomes,
                        longValue(json, "forwardBiomeExtensions"));
                forwardEntities = Math.max(
                        forwardEntities,
                        longValue(json, "forwardEntityExtensions"));
            }
        }

        return new BuilderRuntimeProofEvidence(
                snapshots,
                clean,
                rejected,
                completed,
                cancelled,
                maxBlocks,
                maxExtensions,
                rollbackBlocks,
                rollbackBiomes,
                rollbackEntities,
                forwardBiomes,
                forwardEntities
        );
    }

    private static long longValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return 0L;
        try {
            long result = value.getAsLong();
            return Math.max(0L, result);
        } catch (RuntimeException invalid) {
            return 0L;
        }
    }

    public synchronized long snapshotCount() throws IOException {
        if (!Files.isDirectory(directory)) return 0L;
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .count();
        }
    }

    public synchronized Path writeSnapshot(
            BuilderRuntimeMetrics.Snapshot snapshot,
            String label
    ) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        String safeLabel = sanitizeLabel(label);
        long ended = System.currentTimeMillis();
        Files.createDirectories(directory);

        String stem = FILE_TIME.format(Instant.ofEpochMilli(ended))
                + "-" + safeLabel + "-" + sessionId.substring(0, 8);
        Path target = uniqueTarget(stem);
        Path staging = target.resolveSibling(target.getFileName() + ".tmp");

        String json = toJson(snapshot, ended, label);
        Files.writeString(
                staging,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.deleteIfExists(staging);
            throw new IOException(
                    "Runtime proof directory does not support atomic publication", e);
        }
        return target;
    }

    private Path uniqueTarget(String stem) throws IOException {
        Path target = directory.resolve(stem + ".json");
        if (!Files.exists(target)) return target;
        for (int i = 2; i <= 9999; i++) {
            target = directory.resolve(stem + "-" + i + ".json");
            if (!Files.exists(target)) return target;
        }
        throw new IOException("Could not allocate runtime proof filename");
    }

    private String toJson(
            BuilderRuntimeMetrics.Snapshot s,
            long endedEpochMillis,
            String label
    ) {
        return "{\n"
                + "  \"schema\": 4,\n"
                + "  \"sessionId\": \"" + escape(sessionId) + "\",\n"
                + "  \"label\": \"" + escape(label == null ? "snapshot" : label) + "\",\n"
                + "  \"startedEpochMillis\": " + sessionStartedEpochMillis + ",\n"
                + "  \"snapshotEpochMillis\": " + endedEpochMillis + ",\n"
                + "  \"operationsStarted\": " + s.operationsStarted() + ",\n"
                + "  \"operationsCompleted\": " + s.operationsCompleted() + ",\n"
                + "  \"operationsCancelled\": " + s.operationsCancelled() + ",\n"
                + "  \"operationsFailed\": " + s.operationsFailed() + ",\n"
                + "  \"forwardChunksVisited\": " + s.forwardChunksVisited() + ",\n"
                + "  \"forwardBlocksDispatched\": " + s.forwardBlocksDispatched() + ",\n"
                + "  \"rollbackChunksVisited\": " + s.rollbackChunksVisited() + ",\n"
                + "  \"rollbackBlocksDispatched\": " + s.rollbackBlocksDispatched() + ",\n"
                + "  \"forwardBiomeExtensions\": " + s.forwardBiomeExtensions() + ",\n"
                + "  \"forwardEntityExtensions\": " + s.forwardEntityExtensions() + ",\n"
                + "  \"rollbackBiomeExtensions\": " + s.rollbackBiomeExtensions() + ",\n"
                + "  \"rollbackEntityExtensions\": " + s.rollbackEntityExtensions() + ",\n"
                + "  \"extensionConflicts\": " + s.extensionConflicts() + ",\n"
                + "  \"extensionFailures\": " + s.extensionFailures() + ",\n"
                + "  \"historyUndoBlocks\": " + s.historyUndoBlocks() + ",\n"
                + "  \"historyRedoBlocks\": " + s.historyRedoBlocks() + ",\n"
                + "  \"historyUndoBiomeExtensions\": " + s.historyUndoBiomeExtensions() + ",\n"
                + "  \"historyRedoBiomeExtensions\": " + s.historyRedoBiomeExtensions() + ",\n"
                + "  \"historyUndoEntityExtensions\": " + s.historyUndoEntityExtensions() + ",\n"
                + "  \"historyRedoEntityExtensions\": " + s.historyRedoEntityExtensions() + ",\n"
                + "  \"historyReplayFailures\": " + s.historyReplayFailures() + ",\n"
                + "  \"totalOperationNanos\": " + s.totalOperationNanos() + ",\n"
                + "  \"maxOperationNanos\": " + s.maxOperationNanos() + ",\n"
                + "  \"lastOperationNanos\": " + s.lastOperationNanos() + ",\n"
                + "  \"lastOperationPlannedBlocks\": " + s.lastOperationPlannedBlocks() + ",\n"
                + "  \"lastOperationPlannedExtensions\": " + s.lastOperationPlannedExtensions() + ",\n"
                + "  \"maxCompletedPlannedBlocks\": " + s.maxCompletedPlannedBlocks() + ",\n"
                + "  \"maxCompletedPlannedExtensions\": " + s.maxCompletedPlannedExtensions() + ",\n"
                + "  \"lastOperationId\": \"" + escape(s.lastOperationId()) + "\",\n"
                + "  \"conflicts\": " + s.conflicts() + ",\n"
                + "  \"budgetExceeded\": " + s.budgetExceeded() + ",\n"
                + "  \"maxSliceNanos\": " + s.maxSliceNanos() + ",\n"
                + "  \"lastOutcome\": \"" + escape(s.lastOutcome()) + "\"\n"
                + "}\n";
    }

    private static String sanitizeLabel(String label) {
        String value = label == null || label.isBlank() ? "snapshot" : label;
        value = value.replaceAll("[^A-Za-z0-9._-]+", "-");
        value = value.replaceAll("^-+|-+$", "");
        if (value.isBlank()) value = "snapshot";
        return value.substring(0, Math.min(48, value.length()));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
