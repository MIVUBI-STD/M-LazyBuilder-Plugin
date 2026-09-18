package com.halokaryamedia.lazybuilder.builder.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable first-wins collision filter for equal axis-aligned XZ footprints.
 * Spatial hashing keeps rejection close to O(n) for typical scatter distributions.
 */
public final class FootprintCollisionFilter {
    private FootprintCollisionFilter() {}

    public static List<PlacementPoint> filter(
            List<PlacementPoint> candidates,
            PlacementFootprint footprint
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(footprint, "footprint");

        int cellWidth = Math.max(1, Math.addExact(Math.multiplyExact(footprint.halfWidthX(), 2), 1));
        int cellDepth = Math.max(1, Math.addExact(Math.multiplyExact(footprint.halfDepthZ(), 2), 1));

        Map<Cell, List<PlacementPoint>> acceptedByCell = new HashMap<>();
        List<PlacementPoint> accepted = new ArrayList<>(candidates.size());

        for (PlacementPoint candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            Cell center = cell(candidate.x(), candidate.z(), cellWidth, cellDepth);
            if (collides(candidate, footprint, center, acceptedByCell)) continue;
            accepted.add(candidate);
            acceptedByCell.computeIfAbsent(center, ignored -> new ArrayList<>()).add(candidate);
        }

        return List.copyOf(accepted);
    }

    private static boolean collides(
            PlacementPoint candidate,
            PlacementFootprint footprint,
            Cell center,
            Map<Cell, List<PlacementPoint>> accepted
    ) {
        for (long dx = -1; dx <= 1; dx++) {
            for (long dz = -1; dz <= 1; dz++) {
                List<PlacementPoint> nearby = accepted.get(new Cell(center.x + dx, center.z + dz));
                if (nearby == null) continue;
                for (PlacementPoint existing : nearby) {
                    long distanceX = Math.abs((long) candidate.x() - existing.x());
                    long distanceZ = Math.abs((long) candidate.z() - existing.z());
                    if (distanceX <= (long) footprint.halfWidthX() * 2L
                            && distanceZ <= (long) footprint.halfDepthZ() * 2L) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Cell cell(int x, int z, int cellWidth, int cellDepth) {
        return new Cell(Math.floorDiv(x, cellWidth), Math.floorDiv(z, cellDepth));
    }

    private record Cell(long x, long z) {}
}
