package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldBackupStore;
import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Phased backup use case with request-bound filesystem work. */
public final class WorldBackupService {
    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;
    private final WorldBackupStore backups;

    public WorldBackupService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            WorldFileRepository files,
            WorldBackupStore backups
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.files = Objects.requireNonNull(files, "files");
        this.backups = Objects.requireNonNull(backups, "backups");
    }

    public BackupTask prepare(WorldId worldId) {
        Objects.requireNonNull(worldId, "worldId");
        WorldRecord world = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        if (world.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before backup: " + world.folderName());
        }

        WorldOperationCoordinator.Lease lease = operations.acquire(worldId, WorldOperationType.BACKUP);
        boolean wasLoaded = runtimeService.isLoaded(worldId);
        try {
            runtimeService.unloadDuringOperation(worldId);
            UUID operationId = UUID.randomUUID();
            String backupId = world.folderName() + "-" + operationId;
            return new BackupTask(operationId, backupId, world, wasLoaded, lease);
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }
    }

    public BackupResult executeFilePhase(BackupTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path staged = null;
        IllegalStateException primaryFailure = null;
        try {
            staged = files.stageCopy(task.world, task.operationId, WorldCopyProfile.SNAPSHOT);
            Path artifact = backups.createBackup(staged, task.backupId);
            task.committed = true;
            return new BackupResult(task.backupId, artifact.getFileName().toString());
        } catch (IOException | RuntimeException exception) {
            primaryFailure = new IllegalStateException(
                    "Failed to back up world " + task.world.folderName(), exception);
            throw primaryFailure;
        } finally {
            if (staged != null) {
                try {
                    files.deleteWorkspace(staged);
                } catch (IOException cleanupFailure) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                    else if (!task.committed) {
                        throw new IllegalStateException(
                                "Failed to clean backup workspace for " + task.world.folderName(), cleanupFailure);
                    } else task.cleanupFailure = cleanupFailure;
                }
            }
        }
    }

    public void finish(BackupTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) return;

        RuntimeException failure = null;
        if (task.wasLoaded) {
            try { runtimeService.loadDuringOperation(task.world.id()); }
            catch (RuntimeException exception) { failure = exception; }
        }
        task.close();
        if (failure != null) throw failure;
    }

    public record BackupResult(String backupId, String artifactName) {
        public BackupResult {
            Objects.requireNonNull(backupId, "backupId");
            Objects.requireNonNull(artifactName, "artifactName");
        }
    }

    public static final class BackupTask {
        private final UUID operationId;
        private final String backupId;
        private final WorldRecord world;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private boolean committed;
        private boolean closed;
        private IOException cleanupFailure;

        private BackupTask(UUID operationId, String backupId, WorldRecord world, boolean wasLoaded,
                           WorldOperationCoordinator.Lease lease) {
            this.operationId = operationId;
            this.backupId = backupId;
            this.world = world;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
        }

        public WorldRecord world() { return world; }
        public boolean committed() { return committed; }
        public IOException cleanupFailure() { return cleanupFailure; }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Backup task is already closed");
        }

        private void close() {
            if (!closed) {
                lease.close();
                closed = true;
            }
        }
    }
}
