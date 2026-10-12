package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.util.Objects;
import java.util.UUID;

/** Canonical server-authoritative Teleport to Location use case. */
public final class WorldLocationTeleportService {
    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldLocationGateway locationGateway;
    private final WorldOperationCoordinator operations;

    public WorldLocationTeleportService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldLocationGateway locationGateway,
            WorldOperationCoordinator operations
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.locationGateway = Objects.requireNonNull(locationGateway, "locationGateway");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.runtimeService.attachOperations(this.operations);
    }

    /** Asynchronous variant; ownership lasts through actual destination arrival. */
    public java.util.concurrent.CompletableFuture<TeleportResult> teleportAsync(
            UUID playerId, WorldId worldId, int blockX, int blockZ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");
        var playerLease = runtimeService.acquirePlayerTeleport(playerId);
        try {
            var worldLease = operations.acquire(worldId, WorldOperationType.TELEPORT);
            try {
                WorldRecord world = registry.find(worldId)
                        .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
                if (world.lifecycle() != WorldLifecycle.ACTIVE) {
                    throw new IllegalStateException("Archived worlds cannot be used for map teleport: " + world.folderName());
                }
                runtimeService.loadDuringOperation(worldId);
                WorldRecord selected = registry.find(worldId)
                        .orElseThrow(() -> new IllegalStateException("World disappeared during teleport: " + worldId));
                if (selected.lifecycle() != WorldLifecycle.ACTIVE) {
                    throw new IllegalStateException("World lifecycle changed during teleport: " + selected.folderName());
                }
                var future = locationGateway.teleportToSafeSurfaceAsync(
                        playerId, selected, blockX, blockZ,
                        WorldGameMode.valueOf(selected.defaultGameMode()))
                        .thenApply(resolved -> new TeleportResult(selected, blockX, blockZ, resolved));
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

    public TeleportResult teleport(UUID playerId, WorldId worldId, int blockX, int blockZ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");

        try (WorldOperationCoordinator.Lease ignored =
                     operations.acquire(worldId, WorldOperationType.TELEPORT)) {
            WorldRecord world = registry.find(worldId)
                    .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
            if (world.lifecycle() != WorldLifecycle.ACTIVE) {
                throw new IllegalStateException("Archived worlds cannot be used for map teleport: " + world.folderName());
            }

            runtimeService.loadDuringOperation(worldId);
            world = registry.find(worldId)
                    .orElseThrow(() -> new IllegalStateException("World disappeared during teleport: " + worldId));
            if (world.lifecycle() != WorldLifecycle.ACTIVE) {
                throw new IllegalStateException("World lifecycle changed during teleport: " + world.folderName());
            }

            WorldLocationGateway.ResolvedLocation resolved = locationGateway.teleportToSafeSurface(
                    playerId,
                    world,
                    blockX,
                    blockZ,
                    WorldGameMode.valueOf(world.defaultGameMode())
            );
            return new TeleportResult(world, blockX, blockZ, resolved);
        }
    }

    public record TeleportResult(
            WorldRecord world,
            int requestedBlockX,
            int requestedBlockZ,
            WorldLocationGateway.ResolvedLocation resolved
    ) {
        public TeleportResult {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(resolved, "resolved");
        }
    }
}
