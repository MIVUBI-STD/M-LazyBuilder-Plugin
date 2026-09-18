package com.halokaryamedia.lazybuilder.builder.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stable chunk-local grouping for structure/scatter scheduling. */
public final class PlacementChunkGroups {
    private PlacementChunkGroups() {}

    public static Map<ChunkKey, List<PlacementPlanEntry>> group(List<PlacementPlanEntry> plan) {
        Objects.requireNonNull(plan, "plan");
        LinkedHashMap<ChunkKey, List<PlacementPlanEntry>> mutable = new LinkedHashMap<>();
        for (PlacementPlanEntry entry : plan) {
            Objects.requireNonNull(entry, "entry");
            ChunkKey key = new ChunkKey(Math.floorDiv(entry.point().x(), 16), Math.floorDiv(entry.point().z(), 16));
            mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        LinkedHashMap<ChunkKey, List<PlacementPlanEntry>> result = new LinkedHashMap<>();
        mutable.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    public record ChunkKey(int chunkX, int chunkZ) {}
}
