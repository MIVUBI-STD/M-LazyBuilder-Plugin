package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.files.WorldBackupStore;
import com.halokaryamedia.lazybuilder.world.files.WorldImportArtifactStore;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryTransactions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Request-bound Import World flow. Imported worlds publish only after validation succeeds. */
public final class WorldImportService {
    public static final String TARGET_FORMAT = WorldExportService.NATIVE_SERVER_FORMAT;

    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldFileRepository files;
    private final WorldImportArtifactStore imports;
    private final WorldBackupStore backups;
    private final ConversionRuntimeStore conversionStore;
    private final ConversionUpdateService updateService;
    private final ConverterAdapter converter;
    private final ConversionJobCoordinator conversionJobs;
    /** Successful imports whose source upload could not yet be deleted. Retried only on explicit lifecycle events. */
    private final Set<String> pendingCommittedArtifactCleanup = ConcurrentHashMap.newKeySet();
    /** Single in-flight owner per source archive across Desktop and Fabric requests.
     * This is request-scoped admission state, not a second filesystem registry. */
    private final Map<String, UUID> activeArtifactOperations = new ConcurrentHashMap<>();

    public WorldImportService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldFileRepository files,
            WorldImportArtifactStore imports,
            ConversionRuntimeStore conversionStore,
            ConversionUpdateService updateService,
            ConverterAdapter converter,
            ConversionJobCoordinator conversionJobs
    ) {
        this(registry, persistence, files, imports, conversionStore,
                updateService, converter, conversionJobs, null);
    }

    public WorldImportService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldFileRepository files,
            WorldImportArtifactStore imports,
            ConversionRuntimeStore conversionStore,
            ConversionUpdateService updateService,
            ConverterAdapter converter,
            ConversionJobCoordinator conversionJobs,
            WorldBackupStore backups
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.files = Objects.requireNonNull(files, "files");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.backups = backups; // Optional for legacy test fixtures; direct restore fails closed without it.
        this.conversionStore = Objects.requireNonNull(conversionStore, "conversionStore");
        this.updateService = Objects.requireNonNull(updateService, "updateService");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.conversionJobs = Objects.requireNonNull(conversionJobs, "conversionJobs");
    }

    public WorldImportArtifactStore.ImportInspection inspect(String artifactName) {
        UUID inspectionId = UUID.randomUUID();
        reserveArtifact(artifactName, inspectionId);
        try {
            retryPendingCommittedArtifactCleanup();
            try {
                return imports.inspectArtifact(artifactName);
            } catch (IOException | RuntimeException exception) {
                // Inspection is read-only. Invalid metadata or a transient read
                // failure cannot authorize deletion of a user's uploaded archive.
                // The explicit Discard action owns any destructive cleanup.
                throw new IllegalStateException("Could not inspect uploaded world; uploaded file was preserved", exception);
            }
        } finally {
            releaseArtifact(artifactName, inspectionId);
        }
    }

    public void discard(String artifactName) {
        UUID discardId = UUID.randomUUID();
        reserveArtifact(artifactName, discardId);
        try {
            retryPendingCommittedArtifactCleanup();
            try {
                imports.deleteArtifact(artifactName);
                imports.clearCommittedCleanupPending(artifactName);
                pendingCommittedArtifactCleanup.remove(artifactName);
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not discard uploaded world", exception);
            }
        } finally {
            releaseArtifact(artifactName, discardId);
        }
    }

    public ImportTask prepare(String artifactName, String destinationFolder, String displayName) {
        return prepare(artifactName, destinationFolder, displayName, null);
    }

    public ImportTask prepare(String artifactName, String destinationFolder, String displayName, UUID requestId) {
        return prepare(artifactName, destinationFolder, displayName, requestId, null);
    }

    /** The digest is verified by the existing Desktop transfer owner before this call. */
    public ImportTask prepare(
            String artifactName, String destinationFolder, String displayName, UUID requestId, String artifactSha256
    ) {
        if (requestId == null && artifactSha256 != null) {
            throw new IllegalArgumentException("Import source checksum requires an authoritative request ID");
        }
        UUID operationId = UUID.randomUUID();
        reserveArtifact(artifactName, operationId);
        try {
            retryPendingCommittedArtifactCleanup();
            // The persisted cleanup marker is authoritative after a restart,
            // even if startup recovery has not yet populated the in-memory set.
            try {
                pendingCommittedArtifactCleanup.addAll(imports.pendingCommittedCleanupArtifacts());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not verify pending committed import cleanup", exception);
            }
            if (pendingCommittedArtifactCleanup.contains(artifactName)) {
                throw new IllegalStateException("Import upload has pending committed cleanup: " + artifactName);
            }
            if (requestId != null && registry.findByImportRequestId(requestId).isPresent()) {
                throw new IllegalStateException("Import request already committed: " + requestId);
            }
            WorldRecord destination = new WorldRecord(
                    WorldId.create(), destinationFolder, displayName, WorldKind.IMPORTED,
                    WorldLifecycle.ACTIVE, WorldRecord.DEFAULT_GAME_MODE,
                    requestId == null ? null : new WorldRecord.ImportProvenance(
                            requestId, artifactName, displayName, artifactSha256)
            );
            WorldRegistry.FolderReservation destinationReservation =
                    registry.reserveFolder(destination.folderName());
            return new ImportTask(operationId, artifactName, destination, destinationReservation);
        } catch (RuntimeException | Error failure) {
            // A rejected folder/identity must never strand the source archive.
            releaseArtifact(artifactName, operationId);
            throw failure;
        }
    }

    /**
     * Prepare a backup-origin import without a loopback upload or a second
     * world publication path. The original backup ZIP stays read-only.
     */
    public ImportTask prepareBackup(
            String backupFileName, String destinationFolder, String displayName, UUID requestId
    ) {
        Objects.requireNonNull(requestId, "requestId");
        if (backups == null) throw new IllegalStateException("Direct backup restore is not configured");
        if (registry.findByImportRequestId(requestId).isPresent()) {
            throw new IllegalStateException("Backup restore request already committed: " + requestId);
        }
        Path source;
        String digest;
        try {
            source = backups.requireExistingBackup(backupFileName);
            digest = sourceDigest(source);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not safely open world backup: " + backupFileName, failure);
        }
        WorldRecord destination = new WorldRecord(
                WorldId.create(), destinationFolder, displayName, WorldKind.IMPORTED,
                WorldLifecycle.ACTIVE, WorldRecord.DEFAULT_GAME_MODE,
                new WorldRecord.ImportProvenance(requestId, backupFileName, displayName, digest));
        WorldRegistry.FolderReservation reservation = registry.reserveFolder(destination.folderName());
        return new ImportTask(UUID.randomUUID(), backupFileName, destination, reservation, source);
    }

    private static String sourceDigest(Path source) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
        try (InputStream in = Files.newInputStream(source)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public WorldRecord executeFilePhase(ImportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path stagedInput = null;
        Path convertedWorkspace = null;
        Path publishSource = null;
        boolean published = false;
        try {
            stagedInput = files.reserveWorkspace(task.operationId);
            String expectedSha256 = task.destination.importProvenance() == null
                    ? null : task.destination.importProvenance().artifactSha256();
            WorldImportArtifactStore.StagedImport staged = task.backupSource == null
                    ? imports.stageArchive(task.artifactName, stagedInput, expectedSha256)
                    : imports.stageReadOnlyBackup(task.backupSource, stagedInput, expectedSha256);
            publishSource = staged.worldDirectory();

            boolean nativeTrusted = staged.edition() == WorldImportArtifactStore.DetectedEdition.JAVA
                    && TARGET_FORMAT.equalsIgnoreCase(staged.trustedFormat());
            if (!nativeTrusted) {
                ensureConversionRuntime();
                ConversionRuntimeStore.InstalledRuntime runtime = conversionStore.current()
                        .orElseThrow(() -> new IllegalStateException("No verified conversion runtime is installed"));
                if (runtime.manifest().supportedFormats().stream().noneMatch(TARGET_FORMAT::equalsIgnoreCase)) {
                    throw new IllegalStateException("Active conversion runtime cannot write " + TARGET_FORMAT);
                }
                convertedWorkspace = files.reserveWorkspace(UUID.randomUUID());
                try (ConversionJobCoordinator.Lease ignored = conversionJobs.acquire()) {
                    converter.convert(runtime.artifact(), new ConverterAdapter.ConversionRequest(
                            staged.worldDirectory(), convertedWorkspace, TARGET_FORMAT, null
                    ));
                }
                imports.sanitizeConvertedWorld(convertedWorkspace);
                publishSource = convertedWorkspace;
            }

            files.publishStagedWorld(publishSource, task.destination.folderName());
            if (publishSource.equals(stagedInput)) stagedInput = null;
            if (publishSource.equals(convertedWorkspace)) convertedWorkspace = null;
            publishSource = null;
            published = true;

            WorldRegistryTransactions.register(registry, persistence, task.destination);
            task.completed = true;

            try {
                files.markPublishedWorldCommitted(task.destination.folderName());
            } catch (IOException | RuntimeException cleanupFailure) {
                // The registry has already committed. Even an unchecked cleanup
                // failure cannot turn this successful import into a failed task.
                task.publicationMarkerCleanupFailure = cleanupFailure instanceof IOException io
                        ? io : new IOException("Could not finalize committed world publication marker", cleanupFailure);
            }

            if (task.backupSource == null) {
            pendingCommittedArtifactCleanup.add(task.artifactName);
            try {
                imports.markCommittedCleanupPending(task.artifactName);
            } catch (IOException | RuntimeException markerFailure) {
                // Publication already committed: cleanup is a warning, not a
                // failed import. Retain pending cleanup for a later attempt.
                task.artifactCleanupFailure = markerFailure instanceof IOException io
                        ? io : new IOException("Could not persist committed import cleanup marker", markerFailure);
            }
            IOException cleanupFailure = retryCommittedArtifactCleanup(task.artifactName);
            if (cleanupFailure == null && task.artifactCleanupFailure == null) {
                task.artifactCleanupFailure = null;
            } else if (cleanupFailure != null && task.artifactCleanupFailure == null) {
                task.artifactCleanupFailure = cleanupFailure;
            } else if (cleanupFailure != null) {
                task.artifactCleanupFailure.addSuppressed(cleanupFailure);
            }
            }
            return task.destination;
        } catch (IOException | RuntimeException exception) {
            // A durable registry commit is the point of no return. Post-commit
            // cleanup failure must not delete the world referenced by registry.
            if (published && !task.completed) {
                try { files.deleteWorld(task.destination); }
                catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            throw new IllegalStateException("Failed to import world as " + task.destination.folderName(), exception);
        } finally {
            cleanupWorkspace(publishSource);
            cleanupWorkspace(convertedWorkspace);
            cleanupWorkspace(stagedInput);
        }
    }

    public void finish(ImportTask task) {
        Objects.requireNonNull(task, "task");
        synchronized (task) {
            if (task.closed) return;
            try {
                if (task.completed && task.publicationMarkerCleanupFailure != null) {
                    try {
                        files.markPublishedWorldCommitted(task.destination.folderName());
                        task.publicationMarkerCleanupFailure = null;
                    } catch (IOException | RuntimeException ignored) {
                        // A retry fault cannot invalidate the durable registry commit.
                        // Startup publication reconciliation uses persisted registry truth.
                    }
                }
                if (task.backupSource == null && task.completed && task.artifactCleanupFailure != null) {
                    IOException cleanupFailure = retryCommittedArtifactCleanup(task.artifactName);
                    task.artifactCleanupFailure = cleanupFailure;
                }
            } finally {
                try {
                    task.close();
                } finally {
                    // Compare operation IDs: a repeated finish from an older
                    // task must never release a newer claim on the same source.
                    if (task.backupSource == null) releaseArtifact(task.artifactName, task.operationId);
                }
            }
        }
    }

    public void recoverPendingCommittedArtifactCleanup() {
        try {
            pendingCommittedArtifactCleanup.addAll(imports.pendingCommittedCleanupArtifacts());
        } catch (IOException | RuntimeException ignored) {
            return;
        }
        retryPendingCommittedArtifactCleanup();
    }

    public void retryPendingCommittedArtifactCleanup() {
        for (String artifactName : Set.copyOf(pendingCommittedArtifactCleanup)) {
            // The cleanup pass must not delete an active import/inspection source.
            // Use the same operation-token map rather than a second lock owner.
            UUID cleanupId = UUID.randomUUID();
            String key = artifactKey(artifactName);
            if (activeArtifactOperations.putIfAbsent(key, cleanupId) != null) continue;
            try {
                retryCommittedArtifactCleanup(artifactName);
            } finally {
                activeArtifactOperations.remove(key, cleanupId);
            }
        }
    }

    int pendingCommittedArtifactCleanupCount() {
        return pendingCommittedArtifactCleanup.size();
    }

    private void reserveArtifact(String artifactName, UUID operationId) {
        String key = artifactKey(artifactName);
        Objects.requireNonNull(operationId, "operationId");
        if (activeArtifactOperations.putIfAbsent(key, operationId) != null) {
            throw new IllegalStateException("Import artifact is already in use: " + artifactName);
        }
    }

    private void releaseArtifact(String artifactName, UUID operationId) {
        // Compare-and-remove prevents a stale operation from unlocking its successor.
        activeArtifactOperations.remove(artifactKey(artifactName), operationId);
    }

    private static String artifactKey(String artifactName) {
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Import artifact name must not be blank");
        }
        // Reject portability aliases on case-insensitive Windows filesystems.
        // The actual path validation still belongs to WorldImportArtifactStore.
        return artifactName.toLowerCase(Locale.ROOT);
    }

    private IOException retryCommittedArtifactCleanup(String artifactName) {
        if (!pendingCommittedArtifactCleanup.contains(artifactName)) return null;
        try {
            imports.deleteArtifact(artifactName);
            imports.clearCommittedCleanupPending(artifactName);
            pendingCommittedArtifactCleanup.remove(artifactName);
            return null;
        } catch (IOException | RuntimeException exception) {
            return exception instanceof IOException io
                    ? io
                    : new IOException("Could not clean committed import artifact " + artifactName, exception);
        }
    }

    private void ensureConversionRuntime() throws IOException {
        IOException updateFailure = null;
        try { updateService.checkIfDue(); }
        catch (IOException exception) { updateFailure = exception; }
        if (conversionStore.current().isPresent()) return;
        if (updateFailure != null) {
            throw new IOException("Conversion runtime update failed and no verified runtime is installed", updateFailure);
        }
        throw new IOException("No verified conversion runtime is installed");
    }

    private void cleanupWorkspace(Path workspace) {
        if (workspace == null) return;
        try { files.deleteWorkspace(workspace); }
        catch (IOException ignored) { }
    }

    public static final class ImportTask {
        private final UUID operationId;
        private final String artifactName;
        private final Path backupSource;
        private final WorldRecord destination;
        private final WorldRegistry.FolderReservation destinationReservation;
        private boolean completed;
        private boolean closed;
        private IOException artifactCleanupFailure;
        private IOException publicationMarkerCleanupFailure;

        private ImportTask(UUID operationId, String artifactName, WorldRecord destination,
                           WorldRegistry.FolderReservation destinationReservation) {
            this(operationId, artifactName, destination, destinationReservation, null);
        }

        private ImportTask(UUID operationId, String artifactName, WorldRecord destination,
                           WorldRegistry.FolderReservation destinationReservation, Path backupSource) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
            this.backupSource = backupSource;
            this.destination = Objects.requireNonNull(destination, "destination");
            this.destinationReservation = Objects.requireNonNull(destinationReservation, "destinationReservation");
        }

        public WorldRecord destination() { return destination; }
        public boolean completed() { return completed; }
        public IOException artifactCleanupFailure() { return artifactCleanupFailure; }
        public IOException publicationMarkerCleanupFailure() { return publicationMarkerCleanupFailure; }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Import task is already closed");
        }

        private void close() {
            if (closed) return;
            destinationReservation.close();
            closed = true;
        }
    }
}
