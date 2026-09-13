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

/**
 * Phased backup use case.
 *
 * <p>prepare/finish own Paper lifecycle work. executeFilePhase performs snapshot copy and
 * compression on a worker thread. Backup storage remains owned by World-Manager.</p>
 */
public final class WorldBackupService {
    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldOperationCoordinator operations;
    private final WorldFileRepository files;
    private final WorldBackupStore backups;

    public WorldBackupService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldRuntimeStateRegistry runtimeStates,
            WorldOperationCoordinator operations,
            WorldFileRepository files,
            WorldBackupStore backups
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtimeStates = Objects.requireNonNull(runtimeStates, "runtimeStates");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.files = Objects.requireNonNull(files, "files");
        this.backups = Objects.requireNonNull(backups, "backups");
    }

    /** Main-thread phase: validate, acquire lease, and quiesce the source world. */
    public BackupTask prepare(WorldId worldId) {
        Objects.requireNonNull(worldId, "worldId");
        WorldRecord world = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        if (world.lifecycle() != WorldLifecycle.ACTIVE) {
            throw new IllegalStateException("Archived worlds must be restored before backup: " + world.folderName());
        }

        WorldOperationCoordinator.Lease lease = operations.acquire(worldId, WorldOperationType.BACKUP);
        boolean wasLoaded = runtimeStates.get(worldId) == WorldRuntimeState.LOADED;
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

    /** Worker-thread phase: snapshot-copy, compress, then clean temporary workspace. */
    public BackupResult executeFilePhase(BackupTask task) {
        Objects.requireNonNull(task, "task");
        task.requireOpen();
        Path staged = null;
        try {
            staged = files.stageCopy(task.world, task.operationId, WorldCopyProfile.SNAPSHOT);
            Path artifact = backups.createBackup(staged, task.backupId);
            task.committed = true;
            return new BackupResult(task.backupId, artifact.getFileName().toString());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to back up world " + task.world.folderName(), exception);
        } finally {
            if (staged != null) {
                try {
                    files.deleteWorkspace(staged);
                } catch (IOException cleanupFailure) {
                    if (!task.committed) {
                        throw new IllegalStateException("Failed to clean backup workspace for " + task.world.folderName(), cleanupFailure);
                    }
                }
            }
        }
    }

    /** Main-thread phase: restore the source load state and release its operation lease. */
    public void finish(BackupTask task) {
        Objects.requireNonNull(task, "task");
        if (task.closed) return;

        RuntimeException failure = null;
        if (task.wasLoaded) {
            try {
                runtimeService.loadDuringOperation(task.world.id());
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        task.close();
        if (failure != null) throw failure;
    }

    public record BackupResult(String backupId, String fileName) {}

    public static final class BackupTask {
        private final UUID operationId;
        private final String backupId;
        private final WorldRecord world;
        private final boolean wasLoaded;
        private final WorldOperationCoordinator.Lease lease;
        private boolean committed;
        private boolean closed;

        private BackupTask(
                UUID operationId,
                String backupId,
                WorldRecord world,
                boolean wasLoaded,
                WorldOperationCoordinator.Lease lease
        ) {
            this.operationId = operationId;
            this.backupId = backupId;
            this.world = world;
            this.wasLoaded = wasLoaded;
            this.lease = lease;
        }

        public WorldRecord world() { return world; }
        public String backupId() { return backupId; }
        public boolean committed() { return committed; }

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
