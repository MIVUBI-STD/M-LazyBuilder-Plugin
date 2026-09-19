package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryTransactions;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Canonical Create World use case. Runtime load truth comes directly from Paper. */
public final class WorldCreationService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeGateway runtime;
    private final WorldFileRepository files;
    private final BuildReadyPolicy buildReadyPolicy;

    public WorldCreationService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeGateway runtime,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this(registry, persistence, runtime, new WorldFileRepository() {
            @Override public java.nio.file.Path stageCopy(WorldRecord source, UUID operationId, com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile profile) { throw new UnsupportedOperationException(); }
            @Override public java.nio.file.Path stageDelete(WorldRecord world, UUID operationId) { throw new UnsupportedOperationException(); }
            @Override public void publishStagedWorld(java.nio.file.Path stagedWorld, String destinationFolder) { throw new UnsupportedOperationException(); }
            @Override public void deleteWorld(WorldRecord world) { throw new UnsupportedOperationException(); }
            @Override public void deleteWorkspace(java.nio.file.Path workspace) { throw new UnsupportedOperationException(); }
        }, buildReadyPolicy);
    }

    public WorldCreationService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeGateway runtime,
            WorldFileRepository files,
            BuildReadyPolicy buildReadyPolicy
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.files = Objects.requireNonNull(files, "files");
        this.buildReadyPolicy = Objects.requireNonNull(buildReadyPolicy, "buildReadyPolicy");
    }

    public synchronized WorldRecord create(String folderName, String displayName, WorldKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind == WorldKind.IMPORTED) {
            throw new IllegalArgumentException("Create World only supports FLAT or VOID worlds");
        }

        WorldRecord record = new WorldRecord(
                WorldId.create(), folderName, displayName, kind,
                WorldLifecycle.ACTIVE, buildReadyPolicy.defaultGameMode().name()
        );
        UUID operationId = UUID.randomUUID();

        try (WorldRegistry.FolderReservation ignored = registry.reserveFolder(record.folderName())) {
            try {
                files.markCreatePending(operationId, record.folderName());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start Create World transaction: " + folderName, exception);
            }

            boolean runtimeCreated = false;
            try {
                runtime.createNewWorld(record, buildReadyPolicy);
                runtimeCreated = true;
                WorldRegistryTransactions.register(registry, persistence, record);
                try { files.clearCreatePending(operationId, record.folderName()); }
                catch (IOException ignoredCleanup) { }
                return record;
            } catch (IOException | RuntimeException exception) {
                 boolean rollbackCompleted = !runtimeCreated;
                if (runtimeCreated) {
                    try {
                        runtime.rollbackCreatedWorld(record);
                        rollbackCompleted = true;
                    } catch (RuntimeException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                }

                // The pending marker is recovery authority. If runtime rollback did not
                // complete, preserve it so startup reconciliation can remove an
                // unregistered world that may still exist on disk.
                if (rollbackCompleted) {
                    try { files.clearCreatePending(operationId, record.folderName()); }
                    catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
                }
                throw new IllegalStateException("Failed to publish newly created world: " + folderName, exception);
            }
        }
    }
}
