package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.util.Objects;

/** Canonical Archive / Restore lifecycle use cases. */
public final class WorldLifecycleService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldOperationCoordinator operations;

    public WorldLifecycleService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldRuntimeStateRegistry runtimeStates,
            WorldOperationCoordinator operations
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtimeStates = Objects.requireNonNull(runtimeStates, "runtimeStates");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public synchronized WorldRecord archive(WorldId worldId) {
        WorldRecord current = requireWorld(worldId);
        if (current.lifecycle() == WorldLifecycle.ARCHIVED) {
            return current;
        }

        try (WorldOperationCoordinator.Lease ignored = operations.acquire(worldId, WorldOperationType.ARCHIVE)) {
            boolean wasLoaded = runtimeStates.get(worldId) == WorldRuntimeState.LOADED;
            runtimeService.unload(worldId);

            WorldRecord archived = current
                    .withLifecycle(WorldLifecycle.ARCHIVED)
                    .withAutoLoad(false);
            registry.updateMetadata(archived);
            try {
                persistence.save(registry.all());
                return archived;
            } catch (IOException | RuntimeException exception) {
                registry.updateMetadata(current);
                try {
                    persistence.save(registry.all());
                } catch (IOException | RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                if (wasLoaded) {
                    try {
                        runtimeService.load(worldId);
                    } catch (RuntimeException reloadFailure) {
                        exception.addSuppressed(reloadFailure);
                    }
                }
                throw new IllegalStateException("Failed to archive world: " + current.folderName(), exception);
            }
        }
    }

    public synchronized WorldRecord restore(WorldId worldId) {
        WorldRecord current = requireWorld(worldId);
        if (current.lifecycle() == WorldLifecycle.ACTIVE) {
            return current;
        }

        try (WorldOperationCoordinator.Lease ignored = operations.acquire(worldId, WorldOperationType.RESTORE)) {
            WorldRecord restored = current
                    .withLifecycle(WorldLifecycle.ACTIVE)
                    .withAutoLoad(false);
            registry.updateMetadata(restored);
            try {
                persistence.save(registry.all());
                return restored;
            } catch (IOException | RuntimeException exception) {
                registry.updateMetadata(current);
                try {
                    persistence.save(registry.all());
                } catch (IOException | RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw new IllegalStateException("Failed to restore archived world: " + current.folderName(), exception);
            }
        }
    }

    private WorldRecord requireWorld(WorldId worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
    }
}
