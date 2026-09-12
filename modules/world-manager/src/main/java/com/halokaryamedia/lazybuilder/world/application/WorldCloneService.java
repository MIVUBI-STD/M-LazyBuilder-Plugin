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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phased Clone use case.
 *
 * <p>prepare/finish own Paper lifecycle work. executeFilePhase owns heavy
 * filesystem work and is intended for a request-scoped worker thread. No worker
 * is kept alive while World Manager is idle.</p>
 */
public final class WorldCloneService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;

    public WorldCloneService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldRuntimeStateRegistry runtimeStates,
            WorldOperationCoordinator operations,
            WorldFileRepository files
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtimeStates = Objects.requireNonNull(runtimeStates, "runtimeStates");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Main-thread phase: validate, acquire the world lease, and make the source quiescent. */
    public CloneTask prepare(WorldId sourceId, String destinationFolder, String displayName) {
        Objects.requireNonNull(sourceId, "sourceId");
        WorldRecord source = registry.find(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + sourceId));
        if (source.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before cloning: " + source.folderName());
        }
        if (registry.findByFolderName(destinationFolder).isPresent()) {
            throw new IllegalArgumentException("Destination world folder is already managed: " + destinationFolder);
        }

        WorldRecord destination = new WorldRecord(
                WorldId.create(),
                destinationFolder,
                displayName,
                source.kind(),
                WorldLifecycle.ACTIVE,
                false,
                source.defaultGameMode()
        );

        WorldOperationCoordinator.Lease lease = operations.acquire(sourceId, WorldOperationType.CLONE);
        boolean wasLoaded = runtimeStates.get(sourceId) == WorldRuntimeState.LOADED;
        try {
            // V1 prioritizes a consistent filesystem snapshot: quiesce the source
            // before the worker copies it, then restore its previous load state in finish().
            runtimeService.unload(sourceId);
            return new CloneTask(UUID.randomUUID(), source, destination, wasLoaded, lease);
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
    }

    /** Worker-thread phase: sanitized copy, publish, then durable registry publication. */
    public WorldRecord executeFilePhase(CloneTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path staged = null;
        boolean published = false;
        boolean registered = false;
        boolean stateInitialized = false;
        try {
            staged = files.stageCopy(task.source, task.operationId, WorldCopyProfile.CLONE);
            files.publishStagedWorld(staged, task.destination.folderName());
            staged = null;
            published = true;

            registry.register(task.destination);
            registered = true;
            runtimeStates.initialize(task.destination.id(), WorldRuntimeState.UNLOADED);
            stateInitialized = true;
            persistence.save(registry.all());
            task.committed = true;
            return task.destination;
        } catch (IOException | RuntimeException exception) {
            if (stateInitialized) {
                runtimeStates.remove(task.destination.id());
            }
            if (registered) {
                registry.remove(task.destination.id());
            }
            if (published) {
                try {
                    files.deleteWorld(task.destination);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            if (staged != null) {
                try {
                    files.deleteWorkspace(staged);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw new IllegalStateException("Failed to clone world " + task.source.folderName()
                    + " to " + task.destination.folderName(), exception);
        }
    }

    /** Main-thread phase: restore the source load state and release the operation lease. */
    public void finish(CloneTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) {
            return;
        }
        RuntimeException failure = null;
        if (task.wasLoaded) {
            try {
                runtimeService.load(task.source.id());
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        task.close();
        if (failure != null) {
            throw failure;
        }
    }

    public static final class CloneTask {
        private final UUID operationId;
        private final WorldRecord source;
        private final WorldRecord destination;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private boolean committed;
        private boolean closed;

        private CloneTask(
                UUID operationId,
                WorldRecord source,
                WorldRecord destination,
                boolean wasLoaded,
                WorldOperationCoordinator.Lease lease
        ) {
            this.operationId = operationId;
            this.source = source;
            this.destination = destination;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
        }

        public WorldRecord source() {
            return source;
        }

        public WorldRecord destination() {
            return destination;
        }

        public boolean committed() {
            return committed;
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Clone task is already closed");
            }
        }

        private void close() {
            if (!closed) {
                lease.close();
                closed = true;
            }
        }
    }
}
