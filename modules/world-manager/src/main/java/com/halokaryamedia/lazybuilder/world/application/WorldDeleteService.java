package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
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
 * Phased permanent Delete use case.
 *
 * <p>prepare/finish own Paper lifecycle work. executeFilePhase moves the world
 * into an owned workspace, commits registry removal, then removes that workspace.
 * Heavy filesystem work is therefore request-bound and can run off the Paper
 * main thread without creating an idle worker.</p>
 */
public final class WorldDeleteService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;

    public WorldDeleteService(
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

    /** Main-thread phase: exact confirmation, exclusive lease, and safe unload. */
    public DeleteTask prepare(WorldId worldId, String typedFolderName) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(typedFolderName, "typedFolderName");
        WorldRecord world = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        if (!world.folderName().equals(typedFolderName)) {
            throw new IllegalArgumentException("Delete confirmation must exactly match world folder name");
        }

        WorldOperationCoordinator.Lease lease = operations.acquire(worldId, WorldOperationType.DELETE);
        boolean wasLoaded = runtimeStates.get(worldId) == WorldRuntimeState.LOADED;
        try {
            runtimeService.unload(worldId);
            return new DeleteTask(UUID.randomUUID(), world, wasLoaded, lease);
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
    }

    /** Worker-thread phase: reversible staging, durable metadata commit, then physical cleanup. */
    public void executeFilePhase(DeleteTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path staged = null;
        try {
            staged = files.stageDelete(task.world, task.operationId);

            List<WorldRecord> remaining = new ArrayList<>(registry.all());
            remaining.removeIf(world -> world.id().equals(task.world.id()));
            persistence.save(List.copyOf(remaining));

            registry.remove(task.world.id());
            runtimeStates.remove(task.world.id());
            task.committed = true;

            try {
                files.deleteWorkspace(staged);
            } catch (IOException cleanupFailure) {
                task.cleanupFailure = cleanupFailure;
            }
        } catch (IOException | RuntimeException exception) {
            if (staged != null && !task.committed) {
                try {
                    files.publishStagedWorld(staged, task.world.folderName());
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw new IllegalStateException("Failed to delete world: " + task.world.folderName(), exception);
        }
    }

    /** Main-thread phase: restore runtime after failure if possible and release the lease. */
    public void finish(DeleteTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) {
            return;
        }

        RuntimeException failure = null;
        if (!task.committed && task.wasLoaded) {
            try {
                runtimeService.load(task.world.id());
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        task.close();

        if (failure != null) {
            throw failure;
        }
        if (task.cleanupFailure != null) {
            throw new IllegalStateException(
                    "World deletion committed but workspace cleanup failed: " + task.world.folderName(),
                    task.cleanupFailure
            );
        }
    }

    public static final class DeleteTask {
        private final UUID operationId;
        private final WorldRecord world;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private boolean committed;
        private boolean closed;
        private IOException cleanupFailure;

        private DeleteTask(
                UUID operationId,
                WorldRecord world,
                boolean wasLoaded,
                WorldOperationCoordinator.Lease lease
        ) {
            this.operationId = operationId;
            this.world = world;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
        }

        public WorldRecord world() {
            return world;
        }

        public boolean committed() {
            return committed;
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Delete task is already closed");
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
