package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.UUID;

/** Narrow runtime boundary for map-coordinate teleport resolution. */
public interface WorldLocationGateway {
    ResolvedLocation teleportToSafeSurface(UUID playerId, WorldRecord world, int blockX, int blockZ, WorldGameMode gameMode);

    record ResolvedLocation(double x, double y, double z) {}
}
