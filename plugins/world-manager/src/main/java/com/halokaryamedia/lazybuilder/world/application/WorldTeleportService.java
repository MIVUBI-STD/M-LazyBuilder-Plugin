package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.util.Objects;
import java.util.UUID;

/** Canonical Teleport to World use case. */
public final class WorldTeleportService {
    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeGateway runtime;
    private final WorldOperationCoordinator operations;

    public WorldTeleportService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldRuntimeGateway runtime,
            WorldOperationCoordinator operations
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.runtimeService.attachOperations(this.operations);
    }

    /**
     * Keeps the per-player and per-world operation leases until Paper confirms
     * actual movement. A queued destination chunk is not a successful teleport.
     */
    public java.util.concurrent.CompletableFuture<WorldRecord> teleportToWorldAsync(
            UUID playerId, WorldId worldId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");
        var playerLease = runtimeService.acquirePlayerTeleport(playerId);
        try {
            var worldLease = operations.acquire(worldId, WorldOperationType.TELEPORT);
            try {
                WorldRecord world = registry.find(worldId)
                        .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
                if (world.lifecycle() != WorldLifecycle.ACTIVE) {
                    throw new IllegalStateException("Archived worlds must be restored before teleporting: "
                            + world.displayName());
                }
                runtimeService.loadDuringOperation(worldId);
                WorldRecord selected = registry.find(worldId)
                        .orElseThrow(() -> new IllegalStateException("World disappeared during teleport: " + worldId));
                if (selected.lifecycle() != WorldLifecycle.ACTIVE) {
                    throw new IllegalStateException("World lifecycle changed during teleport: " + selected.displayName());
                }
                var future = runtime.teleportPlayerToSpawnAsync(
                        playerId, selected, WorldGameMode.valueOf(selected.defaultGameMode()))
                        .thenApply(ignored -> selected);
                return future.whenComplete((result, failure) -> {
                    try { worldLease.close(); }
                    finally { playerLease.close(); }
                });
            } catch (RuntimeException | Error failure) {
                worldLease.close();
                throw failure;
            }
        } catch (RuntimeException | Error failure) {
            playerLease.close();
            throw failure;
        }
    }

    public WorldRecord teleportToWorld(UUID playerId, WorldId worldId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");

        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.TELEPORT)) {
            WorldRecord world = registry.find(worldId)
                    .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
            if (world.lifecycle() != WorldLifecycle.ACTIVE) {
                throw new IllegalStateException("Archived worlds must be restored before teleporting: "
                        + world.displayName());
            }

            runtimeService.loadDuringOperation(worldId);
            // Re-read after load while the same lease is still held. Archive/Restore cannot
            // interleave between load and player movement, and metadata cannot become archived
            // underneath a teleport that has already started.
            world = registry.find(worldId)
                    .orElseThrow(() -> new IllegalStateException("World disappeared during teleport: " + worldId));
            if (world.lifecycle() != WorldLifecycle.ACTIVE) {
                throw new IllegalStateException("World lifecycle changed during teleport: " + world.displayName());
            }
            runtime.teleportPlayerToSpawn(playerId, world, WorldGameMode.valueOf(world.defaultGameMode()));
            return world;
        }
    }
}
