package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.files.WorldImportArtifactStore;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.nio.file.Path;
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
    private final ConversionRuntimeStore conversionStore;
    private final ConversionUpdateService updateService;
    private final ConverterAdapter converter;
    private final ConversionJobCoordinator conversionJobs;
    /** Successful imports whose source upload could not yet be deleted. Retried only on explicit lifecycle events. */
    private final Set<String> pendingCommittedArtifactCleanup = ConcurrentHashMap.newKeySet();

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
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.files = Objects.requireNonNull(files, "files");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.conversionStore = Objects.requireNonNull(conversionStore, "conversionStore");
        this.updateService = Objects.requireNonNull(updateService, "updateService");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.conversionJobs = Objects.requireNonNull(conversionJobs, "conversionJobs");
    }

    public WorldImportArtifactStore.ImportInspection inspect(String artifactName) {
        retryPendingCommittedArtifactCleanup();
        try {
            return imports.inspectArtifact(artifactName);
        } catch (IOException | RuntimeException exception) {
            try { imports.deleteArtifact(artifactName); }
            catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            throw new IllegalStateException("Could not inspect uploaded world", exception);
        }
    }

    public void discard(String artifactName) {
        retryPendingCommittedArtifactCleanup();
        try {
            imports.deleteArtifact(artifactName);
            imports.clearCommittedCleanupPending(artifactName);
            pendingCommittedArtifactCleanup.remove(artifactName);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not discard uploaded world", exception);
        }
    }

    public ImportTask prepare(String artifactName, String destinationFolder, String displayName) {
        retryPendingCommittedArtifactCleanup();
        WorldRecord destination = new WorldRecord(
                WorldId.create(), destinationFolder, displayName, WorldKind.IMPORTED,
                WorldLifecycle.ACTIVE
        );
        WorldRegistry.FolderReservation destinationReservation = registry.reserveFolder(destination.folderName());
        return new ImportTask(UUID.randomUUID(), artifactName, destination, destinationReservation);
    }

    public WorldRecord executeFilePhase(ImportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path stagedInput = null;
        Path convertedWorkspace = null;
        Path publishSource = null;
        boolean published = false;
        boolean registered = false;
        try {
            stagedInput = files.reserveWorkspace(task.operationId);
            WorldImportArtifactStore.StagedImport staged = imports.stageArchive(task.artifactName, stagedInput);
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

            registry.register(task.destination);
            registered = true;
            persistence.save(registry.all());
            task.completed = true;

            try {
                files.markPublishedWorldCommitted(task.destination.folderName());
            } catch (IOException cleanupFailure) {
                task.publicationMarkerCleanupFailure = cleanupFailure;
            }

            pendingCommittedArtifactCleanup.add(task.artifactName);
            try {
                imports.markCommittedCleanupPending(task.artifactName);
            } catch (IOException markerFailure) {
                task.artifactCleanupFailure = markerFailure;
            }
            IOException cleanupFailure = retryCommittedArtifactCleanup(task.artifactName);
            if (cleanupFailure == null) {
                task.artifactCleanupFailure = null;
            } else if (task.artifactCleanupFailure == null) {
                task.artifactCleanupFailure = cleanupFailure;
            } else {
                task.artifactCleanupFailure.addSuppressed(cleanupFailure);
            }
            return task.destination;
        } catch (IOException | RuntimeException exception) {
            if (registered) registry.remove(task.destination.id());
            if (published) {
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
        if (task.completed && task.publicationMarkerCleanupFailure != null) {
            try {
                files.markPublishedWorldCommitted(task.destination.folderName());
                task.publicationMarkerCleanupFailure = null;
            } catch (IOException ignored) {
                // Startup publication reconciliation uses persisted registry truth.
            }
        }
        if (task.completed && task.artifactCleanupFailure != null) {
            IOException cleanupFailure = retryCommittedArtifactCleanup(task.artifactName);
            task.artifactCleanupFailure = cleanupFailure;
        }
        task.close();
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
            retryCommittedArtifactCleanup(artifactName);
        }
    }

    int pendingCommittedArtifactCleanupCount() {
        return pendingCommittedArtifactCleanup.size();
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
        private final WorldRecord destination;
        private final WorldRegistry.FolderReservation destinationReservation;
        private boolean completed;
        private boolean closed;
        private IOException artifactCleanupFailure;
        private IOException publicationMarkerCleanupFailure;

        private ImportTask(UUID operationId, String artifactName, WorldRecord destination,
                           WorldRegistry.FolderReservation destinationReservation) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
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
