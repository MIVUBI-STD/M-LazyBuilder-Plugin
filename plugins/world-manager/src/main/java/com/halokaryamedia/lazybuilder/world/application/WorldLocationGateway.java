package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.UUID;

/** Narrow runtime boundary for map-coordinate teleport resolution. */
public interface WorldLocationGateway {
    ResolvedLocation teleportToSafeSurface(UUID playerId, WorldRecord world, int blockX, int blockZ, WorldGameMode gameMode);

    /**
     * Delivers a result only when the actual teleport has completed.
     * Paper overrides to prepare destination chunks without blocking.
     */
    default java.util.concurrent.CompletableFuture<ResolvedLocation> teleportToSafeSurfaceAsync(
            UUID playerId, WorldRecord world, int blockX, int blockZ, WorldGameMode gameMode) {
        return java.util.concurrent.CompletableFuture.completedFuture(
                teleportToSafeSurface(playerId, world, blockX, blockZ, gameMode));
    }

    record ResolvedLocation(double x, double y, double z) {}
}
