package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;

/** Canonical Archive / Restore lifecycle use cases. */
public final class WorldLifecycleService {
    private final WorldRegistry registry;
    private final WorldRegistryPersistence persistence;
    private final WorldRuntimeService runtimeService;
    private final WorldOperationCoordinator operations;
    private final Predicate<WorldRecord> protectedWorld;

    public WorldLifecycleService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations
    ) {
        this(registry, persistence, runtimeService, operations, ignored -> false);
    }

    public WorldLifecycleService(
            WorldRegistry registry,
            WorldRegistryPersistence persistence,
            WorldRuntimeService runtimeService,
            WorldOperationCoordinator operations,
            Predicate<WorldRecord> protectedWorld
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.protectedWorld = Objects.requireNonNull(protectedWorld, "protectedWorld");
        this.runtimeService.attachOperations(this.operations);
    }

    public synchronized WorldRecord archive(WorldId worldId) {
        WorldRecord current = requireWorld(worldId);
        if (current.lifecycle() == WorldLifecycle.ARCHIVED) return current;
        if (protectedWorld.test(current)) {
            throw new IllegalStateException("The active fallback/default world cannot be archived: "
                    + current.displayName());
        }
        if (runtimeService.hasPlayers(worldId)) {
            throw new IllegalStateException("Cannot archive " + current.displayName()
                    + " while builders are inside the world");
        }

        try (WorldOperationCoordinator.Lease ignored = operations.acquire(worldId, WorldOperationType.ARCHIVE)) {
            boolean wasLoaded = runtimeService.isLoaded(worldId);
            runtimeService.unloadDuringOperation(worldId);

            WorldRecord archived = current.withLifecycle(WorldLifecycle.ARCHIVED);
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
                        runtimeService.loadDuringOperation(worldId);
                    } catch (RuntimeException reloadFailure) {
                        exception.addSuppressed(reloadFailure);
                    }
                }
                throw new IllegalStateException("Failed to archive world: " + current.displayName(), exception);
            }
        }
    }

    public synchronized WorldRecord restore(WorldId worldId) {
        WorldRecord current = requireWorld(worldId);
        if (current.lifecycle() == WorldLifecycle.ACTIVE) return current;

        try (WorldOperationCoordinator.Lease ignored = operations.acquire(worldId, WorldOperationType.RESTORE)) {
            WorldRecord restored = current.withLifecycle(WorldLifecycle.ACTIVE);
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
                throw new IllegalStateException("Failed to restore archived world: " + current.displayName(), exception);
            }
        }
    }

    private WorldRecord requireWorld(WorldId worldId) {
        Objects.requireNonNull(worldId, "worldId");
        return registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
    }
}
