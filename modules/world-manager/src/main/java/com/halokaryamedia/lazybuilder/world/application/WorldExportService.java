package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.ExportArtifactType;
import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldExportArtifactStore;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Phased Export World use case shared by whole-world and Export Area requests. */
public final class WorldExportService {
    public static final String NATIVE_SERVER_FORMAT = "JAVA_1_21_4";
    private static final String TRANSFER_MARKER = ".lazybuilder-transfer.properties";
    private static final String PRUNING_FILE = "lazybuilder-export-area.json";

    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;
    private final WorldExportArtifactStore artifacts;
    private final ConversionRuntimeStore conversionStore;
    private final ConversionUpdateService updateService;
    private final ConverterAdapter converter;
    private final ConversionJobCoordinator conversionJobs;

    public WorldExportService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            WorldFileRepository files,
            WorldExportArtifactStore artifacts,
            ConversionRuntimeStore conversionStore,
            ConversionUpdateService updateService,
            ConverterAdapter converter,
            ConversionJobCoordinator conversionJobs
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.files = Objects.requireNonNull(files, "files");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.conversionStore = Objects.requireNonNull(conversionStore, "conversionStore");
        this.updateService = Objects.requireNonNull(updateService, "updateService");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.conversionJobs = Objects.requireNonNull(conversionJobs, "conversionJobs");
    }

    public List<String> supportedFormats() {
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        formats.add(NATIVE_SERVER_FORMAT);
        try {
            conversionStore.current().ifPresent(runtime -> runtime.manifest().supportedFormats().stream()
                    .map(value -> value == null ? "" : value.strip().toUpperCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .forEach(formats::add));
        } catch (IOException ignored) { }
        return List.copyOf(formats);
    }

    public ExportTask prepare(WorldId worldId, String targetFormat, String artifactName) {
        return prepare(worldId, targetFormat, artifactName, null);
    }

    public ExportTask prepareArea(WorldId worldId, String targetFormat, String artifactName, WorldAreaSelection area) {
        return prepare(worldId, targetFormat, artifactName, Objects.requireNonNull(area, "area"));
    }

    private ExportTask prepare(WorldId worldId, String targetFormat, String artifactName, WorldAreaSelection area) {
        Objects.requireNonNull(worldId, "worldId");
        WorldRecord source = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        if (source.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Restore " + source.displayName() + " before exporting it");
        }
        if (runtimeService.hasPlayers(worldId)) {
            throw new IllegalStateException("Cannot export " + source.displayName()
                    + " while builders are inside the world");
        }

        String format = normalizeFormat(targetFormat);
        String safeArtifact = validateArtifactName(artifactName);
        WorldOperationCoordinator.Lease lease = operations.acquire(worldId, WorldOperationType.EXPORT);
        boolean wasLoaded = runtimeService.isLoaded(worldId);
        try {
            runtimeService.unloadDuringOperation(worldId);
            return new ExportTask(UUID.randomUUID(), source, format, safeArtifact, area, wasLoaded, lease);
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
    }

    public void captureSnapshot(ExportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        requireQuiescentSnapshotSource(task);
        Path snapshot = null;
        try {
            snapshot = files.stageCopy(task.source, task.operationId, WorldCopyProfile.SNAPSHOT);
            task.attachSnapshot(snapshot);
        } catch (IOException | RuntimeException exception) {
            cleanupWorkspace(snapshot);
            throw new IllegalStateException("Failed to prepare " + task.source.displayName() + " for export", exception);
        }
    }

    public void resumeSourceAfterSnapshot(ExportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        task.requireSnapshot();
        if (task.sourceRestoreResolved) return;
        task.sourceRestored = restoreSourceIfStillActive(task);
        task.sourceRestoreResolved = true;
    }

    public ExportResult processSnapshot(ExportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path snapshot = task.beginSnapshotProcessing();
        Path converted = null;
        Path pruning = null;
        try {
            if (task.area == null && NATIVE_SERVER_FORMAT.equals(task.targetFormat)) {
                writeNativeTransferMarker(snapshot);
                Path artifact = artifacts.packageDirectory(snapshot, task.artifactName, ExportArtifactType.JAVA_ZIP);
                task.completed = true;
                return new ExportResult(artifact, task.targetFormat, false, null);
            }

            ensureConversionRuntime();
            ConversionRuntimeStore.InstalledRuntime runtime = conversionStore.current()
                    .orElseThrow(() -> new IllegalStateException("Conversion support is not ready yet"));
            boolean supported = runtime.manifest().supportedFormats().stream()
                    .anyMatch(format -> format.equalsIgnoreCase(task.targetFormat));
            if (!supported) {
                throw new IllegalArgumentException("The selected export version is no longer supported by this server");
            }

            if (task.area != null) pruning = writeAreaPruning(task.area, snapshot);
            converted = files.reserveWorkspace(UUID.randomUUID());
            try (ConversionJobCoordinator.Lease ignored = conversionJobs.acquire()) {
                converter.convert(
                        runtime.artifact(),
                        new ConverterAdapter.ConversionRequest(snapshot, converted, task.targetFormat, pruning)
                );
            }

            if (NATIVE_SERVER_FORMAT.equals(task.targetFormat)) writeNativeTransferMarker(converted);
            ExportArtifactType type = task.targetFormat.startsWith("BEDROCK_")
                    ? ExportArtifactType.BEDROCK_WORLD
                    : ExportArtifactType.JAVA_ZIP;
            Path artifact = artifacts.packageDirectory(converted, task.artifactName, type);
            task.completed = true;
            return new ExportResult(artifact, task.targetFormat, true, task.area);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not export " + task.source.displayName(), exception);
        } finally {
            if (pruning != null) {
                try { Files.deleteIfExists(pruning); } catch (IOException ignored) { }
            }
            cleanupWorkspace(converted);
            cleanupWorkspace(snapshot);
            task.endSnapshotProcessing(snapshot);
        }
    }

    public ExportResult executeFilePhase(ExportTask task) {
        captureSnapshot(task);
        return processSnapshot(task);
    }

    public void finish(ExportTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) return;
        RuntimeException failure = null;
        if (!task.sourceRestoreResolved) {
            try {
                task.sourceRestored = restoreSourceIfStillActive(task);
                task.sourceRestoreResolved = true;
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        Path abandonedSnapshot = task.closeAndDetachIdleSnapshot();
        cleanupWorkspace(abandonedSnapshot);
        if (failure != null) throw failure;
    }

    /** Cancel uses the same guarded source restoration and cleanup contract as normal completion. */
    public void abandon(ExportTask task) {
        finish(Objects.requireNonNull(task, "task"));
    }

    private void requireQuiescentSnapshotSource(ExportTask task) {
        WorldId id = task.source.id();
        if (operations.activeOperation(id) != WorldOperationType.EXPORT) {
            throw new IllegalStateException("Export snapshot no longer owns the world operation: " + task.source.displayName());
        }
        WorldRecord current = registry.find(id)
                .orElseThrow(() -> new IllegalStateException("Export source is no longer managed: " + task.source.displayName()));
        if (current.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Export source is no longer active: " + current.displayName());
        }
        if (runtimeService.hasPlayers(id)) {
            throw new IllegalStateException("Cannot snapshot " + current.displayName()
                    + " while builders are inside the world");
        }
        if (runtimeService.isLoaded(id)) {
            throw new IllegalStateException("Cannot snapshot " + current.displayName()
                    + " because it became loaded after export preparation");
        }
    }

    private boolean restoreSourceIfStillActive(ExportTask task) {
        if (!task.wasLoaded) return false;
        WorldId id = task.source.id();
        WorldRecord current = registry.find(id).orElse(null);
        if (current == null || current.lifecycle() != WorldLifecycle.ACTIVE) return false;
        if (!runtimeService.isLoaded(id)) runtimeService.loadDuringOperation(id);
        return true;
    }

    static Path writeAreaPruning(WorldAreaSelection area, Path directory) throws IOException {
        Objects.requireNonNull(area, "area");
        Path root = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path file = root.resolve(PRUNING_FILE).normalize();
        if (!root.equals(file.getParent())) throw new IOException("Pruning path escaped workspace");

        String region = "{\"minChunkX\":" + area.minChunkX()
                + ",\"minChunkZ\":" + area.minChunkZ()
                + ",\"maxChunkX\":" + area.maxChunkX()
                + ",\"maxChunkZ\":" + area.maxChunkZ() + "}";
        String json = "{\"configs\":{" +
                "\"minecraft:overworld\":{\"include\":true,\"regions\":[" + region + "]}," +
                "\"minecraft:the_nether\":{\"include\":true,\"regions\":[" + region + "]}," +
                "\"minecraft:the_end\":{\"include\":true,\"regions\":[" + region + "]}" +
                "}}";
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static void writeNativeTransferMarker(Path snapshot) throws IOException {
        Files.writeString(snapshot.resolve(TRANSFER_MARKER),
                "format=" + NATIVE_SERVER_FORMAT + "\n", StandardCharsets.UTF_8);
    }

    private void ensureConversionRuntime() throws IOException {
        IOException updateFailure = null;
        try { updateService.checkIfDue(); }
        catch (IOException exception) { updateFailure = exception; }
        if (conversionStore.current().isPresent()) return;
        if (updateFailure != null) {
            throw new IOException("Conversion support could not be prepared and no verified runtime is available", updateFailure);
        }
        throw new IOException("Conversion support is not available yet");
    }

    private void cleanupWorkspace(Path workspace) {
        if (workspace == null) return;
        try { files.deleteWorkspace(workspace); }
        catch (IOException ignored) { }
    }

    private static String normalizeFormat(String value) {
        Objects.requireNonNull(value, "targetFormat");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("Selected export format is invalid");
        }
        return normalized;
    }

    private static String validateArtifactName(String value) {
        Objects.requireNonNull(value, "artifactName");
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("File name is invalid");
        }
        return value;
    }

    public record ExportResult(Path artifact, String targetFormat, boolean converted, WorldAreaSelection area) {
        public ExportResult {
            artifact = Objects.requireNonNull(artifact, "artifact").toAbsolutePath().normalize();
            targetFormat = Objects.requireNonNull(targetFormat, "targetFormat");
        }
    }

    public static final class ExportTask {
        private final UUID operationId;
        private final WorldRecord source;
        private final String targetFormat;
        private final String artifactName;
        private final WorldAreaSelection area;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private volatile boolean sourceRestoreResolved;
        private volatile boolean sourceRestored;
        private volatile boolean completed;
        private volatile boolean closed;
        private Path snapshot;
        private boolean snapshotProcessing;

        private ExportTask(UUID operationId, WorldRecord source, String targetFormat, String artifactName,
                           WorldAreaSelection area, boolean wasLoaded, WorldOperationCoordinator.Lease lease) {
            this.operationId = operationId;
            this.source = source;
            this.targetFormat = targetFormat;
            this.artifactName = artifactName;
            this.area = area;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
        }

        public WorldRecord source() { return source; }
        public String targetFormat() { return targetFormat; }
        public WorldAreaSelection area() { return area; }
        public boolean completed() { return completed; }
        public boolean sourceRestored() { return sourceRestored; }

        private synchronized void requireOpen() {
            if (closed) throw new IllegalStateException("Export task is already closed");
        }

        private synchronized void attachSnapshot(Path value) {
            if (closed) throw new IllegalStateException("Export task closed while snapshot was being captured");
            if (snapshot != null) throw new IllegalStateException("Export snapshot already exists");
            snapshot = Objects.requireNonNull(value, "snapshot");
        }

        private synchronized Path requireSnapshot() {
            if (snapshot == null) throw new IllegalStateException("Export snapshot has not been captured");
            return snapshot;
        }

        private synchronized Path beginSnapshotProcessing() {
            requireOpen();
            if (snapshotProcessing) throw new IllegalStateException("Export snapshot is already processing");
            Path value = requireSnapshot();
            snapshotProcessing = true;
            return value;
        }

        private synchronized void endSnapshotProcessing(Path processed) {
            if (snapshot == processed) snapshot = null;
            snapshotProcessing = false;
        }

        private synchronized Path closeAndDetachIdleSnapshot() {
            if (!closed) {
                lease.close();
                closed = true;
            }
            if (snapshotProcessing) return null;
            Path value = snapshot;
            snapshot = null;
            return value;
        }
    }
}
