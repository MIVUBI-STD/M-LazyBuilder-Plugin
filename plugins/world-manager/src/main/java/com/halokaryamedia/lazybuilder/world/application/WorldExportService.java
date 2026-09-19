package com.halokaryamedia.lazybuilder.world.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.AreaCopySelection;
import com.halokaryamedia.lazybuilder.world.files.ExportArtifactType;
import com.halokaryamedia.lazybuilder.world.files.SafeArtifactName;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Phased Export World use case shared by whole-world and Export Area requests. */
public final class WorldExportService {
    public static final String NATIVE_SERVER_FORMAT = "JAVA_1_21_4";
    private static final String TRANSFER_MARKER = ".lazybuilder-transfer.properties";
    private static final String PRUNING_FILE = "lazybuilder-export-area.json";
    private static final BooleanSupplier NEVER_CANCELLED = () -> false;
    private static final List<String> VANILLA_DIMENSIONS = List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

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
        return prepare(worldId, targetFormat, artifactName, null, WorldExportOptions.legacyDefaults());
    }

    public ExportTask prepare(
            WorldId worldId,
            String targetFormat,
            String artifactName,
            WorldExportOptions options
    ) {
        return prepare(worldId, targetFormat, artifactName, null, Objects.requireNonNull(options, "options"));
    }

    public ExportTask prepareArea(WorldId worldId, String targetFormat, String artifactName, WorldAreaSelection area) {
        return prepare(worldId, targetFormat, artifactName,
                Objects.requireNonNull(area, "area"), WorldExportOptions.legacyDefaults());
    }

    public ExportTask prepareArea(
            WorldId worldId,
            String targetFormat,
            String artifactName,
            WorldAreaSelection area,
            WorldExportOptions options
    ) {
        return prepare(worldId, targetFormat, artifactName,
                Objects.requireNonNull(area, "area"), Objects.requireNonNull(options, "options"));
    }

    private ExportTask prepare(
            WorldId worldId,
            String targetFormat,
            String artifactName,
            WorldAreaSelection area,
            WorldExportOptions options
    ) {
        Objects.requireNonNull(worldId, "worldId");
        WorldRecord source = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        if (source.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Restore " + source.displayName() + " before exporting it");
        }

        String format = normalizeFormat(targetFormat);
        String safeArtifact = validateArtifactName(artifactName);
        WorldOperationCoordinator.Lease lease = operations.acquire(worldId, WorldOperationType.EXPORT);
        boolean wasLoaded = runtimeService.isLoaded(worldId);
        boolean liveSnapshot = false;
        WorldRuntimeGateway.LiveSnapshotState snapshotState = null;
        try {
            if (wasLoaded && runtimeService.hasPlayers(worldId)) {
                snapshotState = runtimeService.beginLiveSnapshotDuringOperation(worldId);
                liveSnapshot = true;
            } else {
                runtimeService.unloadDuringOperation(worldId);
            }
            return new ExportTask(UUID.randomUUID(), source, format, safeArtifact,
                    area, options, wasLoaded, liveSnapshot, snapshotState, lease);
        } catch (RuntimeException exception) {
            if (liveSnapshot && snapshotState != null) {
                try {
                    runtimeService.endLiveSnapshotDuringOperation(worldId, snapshotState);
                } catch (RuntimeException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            lease.close();
            throw exception;
        }
    }

    /**
     * Validates Paper/runtime state before an asynchronous snapshot file copy begins.
     * Callers that split the export into main-thread and file phases must invoke this
     * while they still own the Paper main thread, immediately before dispatching the
     * filesystem copy. Validation grants a one-shot task permit consumed by the async phase.
     */
    public void validateSnapshotSourceForAsyncCapture(ExportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        requireSnapshotSource(task);
        task.authorizeAsyncCapture();
    }

    /** Existing synchronous/test path: validates runtime state and then copies files. */
    public void captureSnapshot(ExportTask task) {
        captureSnapshotFiles(task, true, NEVER_CANCELLED);
    }

    /**
     * Async file-phase path used only after validateSnapshotSourceForAsyncCapture()
     * has completed on the Paper main thread. This method performs filesystem work
     * only and must not touch Bukkit/Paper runtime state. The validation permit is
     * consumed before I/O begins so a retry must be validated again.
     */
    public void captureSnapshotAfterValidation(ExportTask task) {
        captureSnapshotAfterValidation(task, NEVER_CANCELLED);
    }

    /** Async capture path with an external cooperative cancellation signal. */
    public void captureSnapshotAfterValidation(ExportTask task, BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        task.consumeAsyncCaptureAuthorization();
        captureSnapshotFiles(task, false, cancellationRequested);
    }

    private void captureSnapshotFiles(
            ExportTask task,
            boolean validateRuntimeState,
            BooleanSupplier cancellationRequested
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        task.requireOpen();
        if (validateRuntimeState) {
            requireSnapshotSource(task);
            task.beginSynchronousCapture();
        }
        BooleanSupplier combinedCancellation =
                () -> task.captureCancellationRequested() || cancellationRequested.getAsBoolean();
        Path snapshot = null;
        try {
            if (task.area == null) {
                snapshot = files.stageCopy(
                        task.source,
                        task.operationId,
                        WorldCopyProfile.SNAPSHOT,
                        combinedCancellation);
            } else {
                snapshot = files.stageAreaCopy(
                        task.source,
                        task.operationId,
                        new AreaCopySelection(
                                task.area.dimensionId(),
                                task.area.minChunkX(),
                                task.area.minChunkZ(),
                                task.area.maxChunkX(),
                                task.area.maxChunkZ(),
                                combinedCancellation));
            }
            task.attachSnapshot(snapshot);
        } catch (IOException | RuntimeException exception) {
            task.failSnapshotCapture();
            cleanupWorkspace(snapshot);
            throw new IllegalStateException("Failed to prepare " + task.source.displayName() + " for export", exception);
        }
    }

    public void resumeSourceAfterSnapshot(ExportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        task.requireSnapshot();
        if (task.sourceRestoreResolved()) return;
        task.resolveSourceRestore(restoreSourceAfterSnapshot(task));
    }

    public ExportResult processSnapshot(ExportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path snapshot = task.beginSnapshotProcessing();
        Path converted = null;
        Path pruning = null;
        Path worldSettings = null;
        Path converterSettings = null;
        try {
            boolean nativeTarget = NATIVE_SERVER_FORMAT.equals(task.targetFormat);
            boolean nativeWholeFastPath = nativeTarget
                    && task.area == null
                    && !task.options.hasWorldOverrides();
            if (nativeWholeFastPath) {
                writeNativeTransferMarker(snapshot);
                Path artifact = artifacts.packageDirectory(snapshot, task.artifactName, ExportArtifactType.JAVA_ZIP);
                task.completed = true;
                return new ExportResult(artifact, task.targetFormat, false, null);
            }

            if (task.area != null) pruning = writeAreaPruning(task.area, snapshot);
            if (task.options.hasWorldOverrides()) worldSettings = writeWorldSettings(task.options);
            if (task.options.discardEmptyChunks()) converterSettings = writeConverterSettings();
            converted = files.reserveWorkspace(UUID.randomUUID());

            ConverterAdapter.ConversionRequest request = new ConverterAdapter.ConversionRequest(
                    snapshot,
                    converted,
                    task.targetFormat,
                    pruning,
                    worldSettings,
                    converterSettings,
                    nativeTarget
            );

            boolean usedExternalRuntime;
            try (ConversionJobCoordinator.Lease ignored = conversionJobs.acquire()) {
                if (converter.canConvertWithoutRuntime(request)) {
                    converter.convertWithoutRuntime(request);
                    usedExternalRuntime = false;
                } else {
                    ensureConversionRuntime();
                    ConversionRuntimeStore.InstalledRuntime runtime = conversionStore.current()
                            .orElseThrow(() -> new IllegalStateException("Conversion support is not ready yet"));
                    boolean supported = runtime.manifest().supportedFormats().stream()
                            .anyMatch(format -> format.equalsIgnoreCase(task.targetFormat));
                    if (!supported) {
                        throw new IllegalArgumentException(
                                "The selected export version is no longer supported by this server");
                    }
                    converter.convert(runtime.artifact(), request);
                    usedExternalRuntime = true;
                }
            }

            if (nativeTarget) writeNativeTransferMarker(converted);
            ExportArtifactType type = task.targetFormat.startsWith("BEDROCK_")
                    ? ExportArtifactType.BEDROCK_WORLD
                    : ExportArtifactType.JAVA_ZIP;
            Path artifact = artifacts.packageDirectory(converted, task.artifactName, type);
            task.completed = true;
            return new ExportResult(artifact, task.targetFormat, usedExternalRuntime, task.area);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not export " + task.source.displayName(), exception);
        } finally {
            deleteQuietly(pruning);
            deleteQuietly(worldSettings);
            deleteQuietly(converterSettings);
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
        if (task.closed()) return;
        task.requestCaptureCancellation();
        RuntimeException failure = null;
        if (!task.sourceRestoreResolved()) {
            try {
                task.resolveSourceRestore(restoreSourceAfterSnapshot(task));
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

    private void requireSnapshotSource(ExportTask task) {
        WorldId id = task.source.id();
        if (operations.activeOperation(id) != WorldOperationType.EXPORT) {
            throw new IllegalStateException("Export snapshot no longer owns the world operation: " + task.source.displayName());
        }
        WorldRecord current = registry.find(id)
                .orElseThrow(() -> new IllegalStateException("Export source is no longer managed: " + task.source.displayName()));
        if (current.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Export source is no longer active: " + current.displayName());
        }
        if (task.liveSnapshot) {
            if (!runtimeService.isLoaded(id)) {
                throw new IllegalStateException("Live export source is no longer loaded: " + current.displayName());
            }
            return;
        }
        if (runtimeService.hasPlayers(id)) {
            throw new IllegalStateException("Cannot snapshot " + current.displayName()
                    + " because builders entered the world during export preparation");
        }
        if (runtimeService.isLoaded(id)) {
            throw new IllegalStateException("Cannot snapshot " + current.displayName()
                    + " because it became loaded after export preparation");
        }
    }

    private boolean restoreSourceAfterSnapshot(ExportTask task) {
        WorldId id = task.source.id();
        WorldRecord current = registry.find(id).orElse(null);
        if (current == null || current.lifecycle() != WorldLifecycle.ACTIVE) return false;
        if (task.liveSnapshot) {
            if (!runtimeService.isLoaded(id)) {
                throw new IllegalStateException("Live export source is no longer loaded: " + current.displayName());
            }
            runtimeService.endLiveSnapshotDuringOperation(id, task.snapshotState);
            return true;
        }
        if (!task.wasLoaded) return false;
        if (!runtimeService.isLoaded(id)) runtimeService.loadDuringOperation(id);
        return true;
    }

    static Path writeAreaPruning(WorldAreaSelection area, Path snapshotDirectory) throws IOException {
        Objects.requireNonNull(area, "area");
        Path snapshot = Objects.requireNonNull(snapshotDirectory, "snapshotDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(snapshot)) throw new IOException("Export snapshot directory is missing");
        Path controlRoot = snapshot.getParent();
        if (controlRoot == null || !Files.isDirectory(controlRoot)) {
            throw new IOException("Export snapshot has no safe control directory");
        }
        Path file = controlRoot.resolve(snapshot.getFileName().toString() + "." + PRUNING_FILE).normalize();
        if (!controlRoot.equals(file.getParent())) throw new IOException("Pruning path escaped export control directory");
        if (Files.exists(file)) throw new IOException("Pruning control file already exists");

        String selectedRegion = "{\"minChunkX\":" + area.minChunkX()
                + ",\"minChunkZ\":" + area.minChunkZ()
                + ",\"maxChunkX\":" + area.maxChunkX()
                + ",\"maxChunkZ\":" + area.maxChunkZ() + "}";
        String fullExclusion = "{\"minChunkX\":" + Integer.MIN_VALUE
                + ",\"minChunkZ\":" + Integer.MIN_VALUE
                + ",\"maxChunkX\":" + Integer.MAX_VALUE
                + ",\"maxChunkZ\":" + Integer.MAX_VALUE + "}";
        StringBuilder json = new StringBuilder("{\"configs\":{");
        for (int i = 0; i < VANILLA_DIMENSIONS.size(); i++) {
            if (i > 0) json.append(',');
            String dimension = VANILLA_DIMENSIONS.get(i);
            json.append('\"').append(dimension).append("\":{");
            if (dimension.equals(area.dimensionId())) {
                json.append("\"include\":true,\"regions\":[").append(selectedRegion).append("]}");
            } else {
                json.append("\"include\":false,\"regions\":[").append(fullExclusion).append("]}");
            }
        }
        json.append("}}");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        return file;
    }

    static Path writeWorldSettings(WorldExportOptions options) throws IOException {
        Objects.requireNonNull(options, "options");
        if (!options.hasWorldOverrides()) return null;
        JsonObject json = new JsonObject();
        if (options.gameMode() != null) json.addProperty("GameType", gameModeId(options.gameMode()));
        if (options.difficulty() != null) json.addProperty("Difficulty", difficultyId(options.difficulty()));
        if (options.hasSpawnOverride()) {
            json.addProperty("SpawnX", options.spawnX());
            json.addProperty("SpawnY", options.spawnY());
            json.addProperty("SpawnZ", options.spawnZ());
        }
        if (options.timeOfDayTicks() != null) json.addProperty("Time", options.timeOfDayTicks());
        if (options.weather() != null) {
            boolean raining = options.weather() != WorldWeather.CLEAR;
            boolean thundering = options.weather() == WorldWeather.THUNDER;
            json.addProperty("raining", raining);
            json.addProperty("thundering", thundering);
        }
        for (Map.Entry<String, String> rule : options.gameRules().entrySet()) {
            json.add(rule.getKey(), gameRuleValue(rule.getValue()));
        }
        Path file = Files.createTempFile("lazybuilder-world-settings-", ".json");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        return file;
    }

    static Path writeConverterSettings() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("discardEmptyChunks", true);
        Path file = Files.createTempFile("lazybuilder-converter-settings-", ".json");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private static JsonPrimitive gameRuleValue(String raw) {
        if (raw.equalsIgnoreCase("true")) return new JsonPrimitive(true);
        if (raw.equalsIgnoreCase("false")) return new JsonPrimitive(false);
        try {
            return new JsonPrimitive(Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return new JsonPrimitive(raw);
        }
    }

    private static int gameModeId(WorldGameMode mode) {
        return switch (mode) {
            case SURVIVAL -> 0;
            case CREATIVE -> 1;
            case ADVENTURE -> 2;
            case SPECTATOR -> 3;
        };
    }

    private static int difficultyId(WorldDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> 1;
            case NORMAL -> 2;
            case HARD -> 3;
        };
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

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
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
        return SafeArtifactName.requirePortable(value, "artifactName");
    }

    public record ExportResult(Path artifact, String targetFormat, boolean converted, WorldAreaSelection area) {
        public ExportResult {
            artifact = Objects.requireNonNull(artifact, "artifact").toAbsolutePath().normalize();
            targetFormat = Objects.requireNonNull(targetFormat, "targetFormat");
        }
    }

    public static final class ExportTask {
        private enum Phase {
            PREPARED,
            CAPTURE_AUTHORIZED,
            CAPTURING,
            CAPTURED,
            PROCESSING,
            CONSUMED,
            CLOSED
        }

        private enum SourceRestoreState {
            UNRESOLVED,
            NOT_RESTORED,
            RESTORED
        }

        private final UUID operationId;
        private final WorldRecord source;
        private final String targetFormat;
        private final String artifactName;
        private final WorldAreaSelection area;
        private final WorldExportOptions options;
        private final boolean wasLoaded;
        private final boolean liveSnapshot;
        private final WorldRuntimeGateway.LiveSnapshotState snapshotState;
        private final WorldOperationCoordinator.Lease lease;
        private volatile SourceRestoreState sourceRestoreState = SourceRestoreState.UNRESOLVED;
        private volatile boolean completed;
        private volatile boolean captureCancellationRequested;
        private Phase phase = Phase.PREPARED;
        private Path snapshot;

        private ExportTask(
                UUID operationId,
                WorldRecord source,
                String targetFormat,
                String artifactName,
                WorldAreaSelection area,
                WorldExportOptions options,
                boolean wasLoaded,
                boolean liveSnapshot,
                WorldRuntimeGateway.LiveSnapshotState snapshotState,
                WorldOperationCoordinator.Lease lease
        ) {
            this.operationId = operationId;
            this.source = source;
            this.targetFormat = targetFormat;
            this.artifactName = artifactName;
            this.area = area;
            this.options = options;
            this.wasLoaded = wasLoaded;
            this.liveSnapshot = liveSnapshot;
            this.snapshotState = snapshotState;
            this.lease = lease;
        }

        public WorldRecord source() { return source; }
        public String targetFormat() { return targetFormat; }
        public WorldAreaSelection area() { return area; }
        public WorldExportOptions options() { return options; }
        public boolean completed() { return completed; }
        public boolean sourceRestored() { return sourceRestoreState == SourceRestoreState.RESTORED; }
        public boolean liveSnapshot() { return liveSnapshot; }

        private boolean sourceRestoreResolved() {
            return sourceRestoreState != SourceRestoreState.UNRESOLVED;
        }

        private void resolveSourceRestore(boolean restored) {
            sourceRestoreState = restored ? SourceRestoreState.RESTORED : SourceRestoreState.NOT_RESTORED;
        }

        private void requestCaptureCancellation() {
            captureCancellationRequested = true;
        }

        private boolean captureCancellationRequested() {
            return captureCancellationRequested;
        }

        public synchronized boolean closed() {
            return phase == Phase.CLOSED;
        }

        private synchronized void requireOpen() {
            if (phase == Phase.CLOSED) throw new IllegalStateException("Export task is already closed");
        }

        private synchronized void authorizeAsyncCapture() {
            requireOpen();
            if (phase == Phase.PREPARED || phase == Phase.CAPTURE_AUTHORIZED) {
                phase = Phase.CAPTURE_AUTHORIZED;
                return;
            }
            throw new IllegalStateException("Export snapshot capture has already started");
        }

        private synchronized void consumeAsyncCaptureAuthorization() {
            requireOpen();
            if (phase != Phase.CAPTURE_AUTHORIZED) {
                throw new IllegalStateException("Async export snapshot capture requires fresh runtime validation");
            }
            phase = Phase.CAPTURING;
        }

        private synchronized void beginSynchronousCapture() {
            requireOpen();
            if (phase != Phase.PREPARED && phase != Phase.CAPTURE_AUTHORIZED) {
                throw new IllegalStateException("Export snapshot capture has already started");
            }
            phase = Phase.CAPTURING;
        }

        private synchronized void failSnapshotCapture() {
            if (phase == Phase.CAPTURING) phase = Phase.PREPARED;
        }

        private synchronized void attachSnapshot(Path value) {
            if (phase == Phase.CLOSED) {
                throw new IllegalStateException("Export task closed while snapshot was being captured");
            }
            if (phase != Phase.CAPTURING) {
                throw new IllegalStateException("Export snapshot already exists");
            }
            snapshot = Objects.requireNonNull(value, "snapshot");
            phase = Phase.CAPTURED;
        }

        private synchronized Path requireSnapshot() {
            if (phase != Phase.CAPTURED && phase != Phase.PROCESSING) {
                throw new IllegalStateException("Export snapshot has not been captured");
            }
            if (snapshot == null) throw new IllegalStateException("Export snapshot has not been captured");
            return snapshot;
        }

        private synchronized Path beginSnapshotProcessing() {
            requireOpen();
            if (phase == Phase.PROCESSING) {
                throw new IllegalStateException("Export snapshot is already processing");
            }
            if (phase != Phase.CAPTURED) {
                throw new IllegalStateException("Export snapshot has not been captured");
            }
            Path value = requireSnapshot();
            phase = Phase.PROCESSING;
            return value;
        }

        private synchronized void endSnapshotProcessing(Path processed) {
            if (snapshot == processed) snapshot = null;
            if (phase == Phase.PROCESSING) phase = Phase.CONSUMED;
        }

        private synchronized Path closeAndDetachIdleSnapshot() {
            if (phase == Phase.CLOSED) return null;
            Phase previous = phase;
            lease.close();
            phase = Phase.CLOSED;
            if (previous == Phase.PROCESSING || previous == Phase.CAPTURING) return null;
            Path value = snapshot;
            snapshot = null;
            return value;
        }
    }
}
