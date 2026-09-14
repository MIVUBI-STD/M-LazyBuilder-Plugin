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
