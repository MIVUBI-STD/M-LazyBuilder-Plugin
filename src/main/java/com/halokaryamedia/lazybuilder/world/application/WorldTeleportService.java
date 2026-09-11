package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.util.Objects;
import java.util.UUID;

/** Canonical Teleport to World use case. */
public final class WorldTeleportService {
    private final WorldRegistry registry;
    private final WorldRuntimeService runtimeService;
    private final WorldRuntimeGateway runtime;

    public WorldTeleportService(
            WorldRegistry registry,
            WorldRuntimeService runtimeService,
            WorldRuntimeGateway runtime
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public WorldRecord teleportToWorld(UUID playerId, WorldId worldId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");

        WorldRecord world = registry.find(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World is not managed: " + worldId));
        runtimeService.load(worldId);
        runtime.teleportPlayerToSpawn(playerId, world);
        return world;
    }
}
