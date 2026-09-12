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
import java.util.UUID;

/** Request-bound Import World flow. Imported worlds publish only after validation succeeds. */
public final class WorldImportService {
    public static final String TARGET_FORMAT = WorldExportService.NATIVE_SERVER_FORMAT;

    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldFileRepository files;
    private final WorldImportArtifactStore imports;
    private final ConversionRuntimeStore conversionStore;
    private final ConversionUpdateService updateService;
    private final ConverterAdapter converter;
    private final ConversionJobCoordinator conversionJobs;
    private boolean importActive;

    public WorldImportService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeStateRegistry runtimeStates,
            WorldFileRepository files,
            WorldImportArtifactStore imports,
            ConversionRuntimeStore conversionStore,
            ConversionUpdateService updateService,
            ConverterAdapter converter,
            ConversionJobCoordinator conversionJobs
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeStates = Objects.requireNonNull(runtimeStates, "runtimeStates");
        this.files = Objects.requireNonNull(files, "files");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.conversionStore = Objects.requireNonNull(conversionStore, "conversionStore");
        this.updateService = Objects.requireNonNull(updateService, "updateService");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.conversionJobs = Objects.requireNonNull(conversionJobs, "conversionJobs");
    }

    public synchronized ImportTask prepare(String artifactName, String destinationFolder, String displayName) {
        if (importActive) throw new IllegalStateException("An import is already active");
        if (registry.findByFolderName(destinationFolder).isPresent()) {
            throw new IllegalArgumentException("Destination world folder is already managed: " + destinationFolder);
        }
        WorldRecord destination = new WorldRecord(
                WorldId.create(), destinationFolder, displayName, WorldKind.IMPORTED,
                WorldLifecycle.ACTIVE, false
        );
        importActive = true;
        return new ImportTask(UUID.randomUUID(), artifactName, destination);
    }

    public WorldRecord executeFilePhase(ImportTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path stagedInput = null;
        Path convertedWorkspace = null;
        Path publishSource = null;
        boolean published = false;
        boolean registered = false;
        boolean stateInitialized = false;
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
            runtimeStates.initialize(task.destination.id(), WorldRuntimeState.UNLOADED);
            stateInitialized = true;
            persistence.save(registry.all());
            task.completed = true;

            // The uploaded archive is an inbox artifact, not durable world state.
            // Consume it only after the managed-world publication and registry
            // persistence have committed. A cleanup failure must not roll back an
            // already committed world; the leftover artifact may be removed later.
            try {
                imports.deleteArtifact(task.artifactName);
            } catch (IOException cleanupFailure) {
                task.artifactCleanupFailure = cleanupFailure;
            }
            return task.destination;
        } catch (IOException | RuntimeException exception) {
            if (stateInitialized) runtimeStates.remove(task.destination.id());
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

    public synchronized void finish(ImportTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) return;
        task.closed = true;
        importActive = false;
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
        private boolean completed;
        private boolean closed;
        private IOException artifactCleanupFailure;

        private ImportTask(UUID operationId, String artifactName, WorldRecord destination) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
            this.destination = Objects.requireNonNull(destination, "destination");
        }

        public WorldRecord destination() { return destination; }
        public boolean completed() { return completed; }
        public IOException artifactCleanupFailure() { return artifactCleanupFailure; }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Import task is already closed");
        }
    }
}
