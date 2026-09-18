package com.halokaryamedia.lazybuilder.builder.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Splits regions into stable Z-major, then X-major chunk work. */
public final class DeterministicRegionPlanner implements RegionPlanner {
    @Override
    public List<ChunkWorkUnit> plan(BuilderRegion region) {
        Objects.requireNonNull(region, "region");
        if (region instanceof ChunkPlannableRegion plannable) {
            return List.copyOf(plannable.workUnits());
        }
        BlockBounds bounds = Objects.requireNonNull(region.bounds(), "region.bounds()");
        List<ChunkWorkUnit> work = new ArrayList<>();
        for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
            for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
                BlockBounds clipped = bounds.intersectionWithChunk(chunkX, chunkZ)
                        .orElseThrow(() -> new IllegalStateException("planned chunk did not intersect region bounds"));
                work.add(new ChunkWorkUnit(chunkX, chunkZ, clipped));
            }
        }
        return List.copyOf(work);
    }
}
