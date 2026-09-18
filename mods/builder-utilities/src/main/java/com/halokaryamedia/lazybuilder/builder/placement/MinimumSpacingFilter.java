package com.halokaryamedia.lazybuilder.builder.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable first-wins XZ minimum-spacing filter for already-generated points.
 * Used after symmetry/transform replication where earlier distribution guarantees
 * no longer imply global spacing.
 */
public final class MinimumSpacingFilter {
    private MinimumSpacingFilter() {}

    public static List<PlacementPoint> filter(
            List<PlacementPoint> candidates,
            double minimumSpacing
    ) {
        Objects.requireNonNull(candidates, "candidates");
        if (!Double.isFinite(minimumSpacing) || minimumSpacing < 0.0) {
            throw new IllegalArgumentException("minimumSpacing must be finite and >= 0");
        }
        if (minimumSpacing == 0.0) return List.copyOf(candidates);

        double cellSize = Math.max(1.0, minimumSpacing);
        double minSquared = minimumSpacing * minimumSpacing;
        Map<Cell, List<PlacementPoint>> acceptedByCell = new HashMap<>();
        List<PlacementPoint> accepted = new ArrayList<>(candidates.size());

        for (PlacementPoint point : candidates) {
            Objects.requireNonNull(point, "point");
            Cell center = cell(point.x(), point.z(), cellSize);
            if (tooClose(point, center, acceptedByCell, minSquared)) continue;

            PlacementPoint reindexed = new PlacementPoint(
                    point.x(), point.y(), point.z(), accepted.size());
            accepted.add(reindexed);
            acceptedByCell.computeIfAbsent(center, ignored -> new ArrayList<>())
                    .add(reindexed);
        }
        return List.copyOf(accepted);
    }

    private static boolean tooClose(
            PlacementPoint candidate,
            Cell center,
            Map<Cell, List<PlacementPoint>> accepted,
            double minSquared
    ) {
        for (long dx = -1; dx <= 1; dx++) {
            for (long dz = -1; dz <= 1; dz++) {
                List<PlacementPoint> nearby =
                        accepted.get(new Cell(center.x + dx, center.z + dz));
                if (nearby == null) continue;
                for (PlacementPoint point : nearby) {
                    double x = (double) point.x() - candidate.x();
                    double z = (double) point.z() - candidate.z();
                    if (x * x + z * z < minSquared) return true;
                }
            }
        }
        return false;
    }

    private static Cell cell(int x, int z, double cellSize) {
        return new Cell(
                (long) Math.floor(x / cellSize),
                (long) Math.floor(z / cellSize));
    }

    private record Cell(long x, long z) {}
}
