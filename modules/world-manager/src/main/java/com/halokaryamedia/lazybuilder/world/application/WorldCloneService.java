package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Phased Duplicate World use case; heavy filesystem work stays request-bound. */
public final class WorldCloneService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;

    public WorldCloneService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            WorldFileRepository files
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Migration bridge only; legacy runtime-state registry is intentionally ignored. */
    public WorldCloneService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldRuntimeStateRegistry ignoredLegacyStates,
            WorldOperationCoordinator operations,
            WorldFileRepository files
    ) {
        this(registry, persistence, runtimeService, operations, files);
    }

    public CloneTask prepare(WorldId sourceId, String destinationFolder, String displayName) {
        Objects.requireNonNull(sourceId, "sourceId");
        WorldRecord source = registry.find(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + sourceId));
        if (source.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before duplicating: " + source.folderName());
        }

        WorldRecord destination = new WorldRecord(
                WorldId.create(), destinationFolder, displayName, source.kind(),
                WorldLifecycle.ACTIVE, false, source.defaultGameMode()
        );

        WorldRegistry.FolderReservation destinationReservation = registry.reserveFolder(destination.folderName());
        WorldOperationCoordinator.Lease lease = null;
        try {
            lease = operations.acquire(sourceId, WorldOperationType.CLONE);
            boolean wasLoaded = runtimeService.isLoaded(sourceId);
            runtimeService.unloadDuringOperation(sourceId);
            return new CloneTask(UUID.randomUUID(), source, destination, wasLoaded, lease, destinationReservation);
        } catch (RuntimeException exception) {
            if (lease != null) lease.close();
            destinationReservation.close();
            throw exception;
        }
    }

    public WorldRecord executeFilePhase(CloneTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path staged = null;
        boolean published = false;
        boolean registered = false;
        try {
            staged = files.stageCopy(task.source, task.operationId, WorldCopyProfile.CLONE);
            files.publishStagedWorld(staged, task.destination.folderName());
            staged = null;
            published = true;

            registry.register(task.destination);
            registered = true;
            persistence.save(registry.all());
            task.committed = true;
            return task.destination;
        } catch (IOException | RuntimeException exception) {
            if (registered) registry.remove(task.destination.id());
            if (published) {
                try { files.deleteWorld(task.destination); }
                catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            if (staged != null) {
                try { files.deleteWorkspace(staged); }
                catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            throw new IllegalStateException("Failed to duplicate world " + task.source.folderName()
                    + " to " + task.destination.folderName(), exception);
        }
    }

    public void finish(CloneTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) return;

        RuntimeException failure = null;
        if (task.wasLoaded) {
            try {
                runtimeService.loadDuringOperation(task.source.id());
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        task.close();
        if (failure != null) throw failure;
    }

    public static final class CloneTask {
        private final UUID operationId;
        private final WorldRecord source;
        private final WorldRecord destination;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private final WorldRegistry.FolderReservation destinationReservation;
        private boolean committed;
        private boolean closed;

        private CloneTask(UUID operationId, WorldRecord source, WorldRecord destination, boolean wasLoaded,
                          WorldOperationCoordinator.Lease lease,
                          WorldRegistry.FolderReservation destinationReservation) {
            this.operationId = operationId;
            this.source = source;
            this.destination = destination;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
            this.destinationReservation = destinationReservation;
        }

        public WorldRecord source() { return source; }
        public WorldRecord destination() { return destination; }
        public boolean committed() { return committed; }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Duplicate task is already closed");
        }

        private void close() {
            if (closed) return;
            RuntimeException failure = null;
            try { lease.close(); } catch (RuntimeException exception) { failure = exception; }
            try { destinationReservation.close(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
            closed = true;
            if (failure != null) throw failure;
        }
    }
}
