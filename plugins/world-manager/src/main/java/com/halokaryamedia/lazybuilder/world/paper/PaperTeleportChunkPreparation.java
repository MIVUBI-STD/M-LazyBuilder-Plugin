package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A single Paper-owned preparation boundary for the bounded landing search.
 * Load only the few destination chunks intersecting the search radius.
 * No blocking Future.get/join and no new executor.
 */
final class PaperTeleportChunkPreparation {
    private PaperTeleportChunkPreparation() { }

    static CompletableFuture<Void> prepare(World world, int x, int z, int blockRadius) {
        Objects.requireNonNull(world, "world");
        if (blockRadius < 0 || blockRadius > SafeSpawnSearch.HORIZONTAL_RADIUS) {
            throw new IllegalArgumentException("Unsupported teleport preparation radius");
        }
        int minChunkX = (x - blockRadius) >> 4;
        int maxChunkX = (x + blockRadius) >> 4;
        int minChunkZ = (z - blockRadius) >> 4;
        int maxChunkZ = (z + blockRadius) >> 4;

        var futures = new ArrayList<CompletableFuture<?>>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Generated and imported worlds can both have a missing chunk
                // at the edge of the landing search. Paper loads/generates it
                // asynchronously, not on the caller's main tick.
                futures.add(world.getChunkAtAsync(chunkX, chunkZ, true));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
}
