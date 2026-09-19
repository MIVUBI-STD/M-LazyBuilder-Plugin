package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryTransactions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Phased permanent Delete use case with reversible publication before commit. */
public final class WorldDeleteService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;
    private final Predicate<WorldRecord> protectedWorld;

    public WorldDeleteService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            WorldFileRepository files
    ) {
        this(registry, persistence, runtimeService, operations, files, ignored -> false);
    }

    public WorldDeleteService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            WorldFileRepository files,
            Predicate<WorldRecord> protectedWorld
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.files = Objects.requireNonNull(files, "files");
        this.protectedWorld = Objects.requireNonNull(protectedWorld, "protectedWorld");
    }

    public DeleteTask prepare(WorldId worldId, String typedDisplayName) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(typedDisplayName, "typedDisplayName");
        WorldRecord world = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        if (!world.displayName().equals(typedDisplayName)) {
            throw new IllegalArgumentException("Delete confirmation must exactly match the world name");
        }
        if (protectedWorld.test(world)) {
            throw new IllegalStateException("The active fallback/default world cannot be deleted: " + world.displayName());
        }
        if (runtimeService.hasPlayers(worldId)) {
            throw new IllegalStateException("Cannot delete " + world.displayName()
                    + " while builders are inside the world");
        }

        WorldOperationCoordinator.Lease lease = operations.acquire(worldId, WorldOperationType.DELETE);
        boolean wasLoaded = runtimeService.isLoaded(worldId);
        try {
            runtimeService.unloadDuringOperation(worldId);
            return new DeleteTask(UUID.randomUUID(), world, wasLoaded, lease);
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
    }

    public void executeFilePhase(DeleteTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path staged = null;
        try {
            staged = files.stageDelete(task.world, task.operationId);

            WorldRegistryTransactions.remove(registry, persistence, task.world.id());
            task.committed = true;

            try {
                files.deleteWorkspace(staged);
            } catch (IOException cleanupFailure) {
                task.cleanupWorkspace = staged;
                task.cleanupFailure = cleanupFailure;
            }
        } catch (IOException | RuntimeException exception) {
            if (staged != null && !task.committed) {
                try { files.publishStagedWorld(staged, task.world.folderName()); }
                catch (IOException restoreFailure) { exception.addSuppressed(restoreFailure); }
            }
            throw new IllegalStateException("Failed to delete world: " + task.world.displayName(), exception);
        }
    }

    public void finish(DeleteTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) return;

        RuntimeException failure = null;
        if (!task.committed && task.wasLoaded) {
            try { runtimeService.loadDuringOperation(task.world.id()); }
            catch (RuntimeException exception) { failure = exception; }
        }

        if (task.committed && task.cleanupWorkspace != null) {
            try {
                files.deleteWorkspace(task.cleanupWorkspace);
                task.cleanupWorkspace = null;
                task.cleanupFailure = null;
            } catch (IOException retryFailure) {
                if (task.cleanupFailure != null) retryFailure.addSuppressed(task.cleanupFailure);
                task.cleanupFailure = retryFailure;
            }
        }

        task.close();
        if (failure != null) throw failure;
    }

    public static final class DeleteTask {
        private final UUID operationId;
        private final WorldRecord world;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private boolean committed;
        private boolean closed;
        private Path cleanupWorkspace;
        private IOException cleanupFailure;

        private DeleteTask(UUID operationId, WorldRecord world, boolean wasLoaded, WorldOperationCoordinator.Lease lease) {
            this.operationId = operationId;
            this.world = world;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
        }

        public WorldRecord world() { return world; }
        public boolean committed() { return committed; }
        public IOException cleanupFailure() { return cleanupFailure; }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Delete task is already closed");
        }

        private void close() {
            if (!closed) {
                lease.close();
                closed = true;
            }
        }
    }
}
