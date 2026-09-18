package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic rejection scatter with a guaranteed XZ minimum spacing. */
public final class MinimumSpacingScatterDistribution implements PlacementDistribution {
    private final int requestedCount;
    private final double minimumSpacing;
    private final double cellSize;
    private final int attemptsPerPoint;
    private final long channel;

    public MinimumSpacingScatterDistribution(int requestedCount, double minimumSpacing, int attemptsPerPoint, long channel) {
        if (requestedCount < 0) {
            throw new IllegalArgumentException("requestedCount must be >= 0");
        }
        if (!Double.isFinite(minimumSpacing) || minimumSpacing < 0.0) {
            throw new IllegalArgumentException("minimumSpacing must be finite and >= 0");
        }
        if (attemptsPerPoint <= 0) {
            throw new IllegalArgumentException("attemptsPerPoint must be > 0");
        }
        this.requestedCount = requestedCount;
        this.minimumSpacing = minimumSpacing;
        this.cellSize = Math.max(1.0, minimumSpacing);
        this.attemptsPerPoint = attemptsPerPoint;
        this.channel = channel;
    }

    @Override
    public List<PlacementPoint> generate(BlockBounds bounds, SurfaceHeightSource surface, OperationSeed seed) {
        List<PlacementPoint> accepted = new ArrayList<>(Math.min(requestedCount, 4096));
        Map<Cell, List<PlacementPoint>> spatial = minimumSpacing == 0.0 ? Map.of() : new HashMap<>();
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long depth = (long) bounds.maxZ() - bounds.minZ() + 1L;
        double minDistanceSquared = minimumSpacing * minimumSpacing;

        for (int ordinal = 0; ordinal < requestedCount; ordinal++) {
            for (int attempt = 0; attempt < attemptsPerPoint; attempt++) {
                int x = coordinate(bounds.minX(), width, seed.sampleUnit(ordinal, attempt, 0, channel));
                int z = coordinate(bounds.minZ(), depth, seed.sampleUnit(ordinal, attempt, 1, channel));
                int y = surface.yAt(x, z);
                if (!bounds.contains(x, y, z) || tooClose(spatial, x, z, minDistanceSquared)) {
                    continue;
                }
                PlacementPoint point = new PlacementPoint(x, y, z, ordinal);
                accepted.add(point);
                if (minimumSpacing > 0.0) {
                    spatial.computeIfAbsent(cellFor(x, z), ignored -> new ArrayList<>()).add(point);
                }
                break;
            }
        }
        return List.copyOf(accepted);
    }

    private static int coordinate(int minimum, long size, double unit) {
        long offset = Math.min(size - 1L, (long) Math.floor(unit * size));
        return Math.toIntExact((long) minimum + offset);
    }

    private boolean tooClose(Map<Cell, List<PlacementPoint>> spatial, int x, int z, double minDistanceSquared) {
        if (minimumSpacing == 0.0) {
            return false;
        }
        Cell center = cellFor(x, z);
        for (long dx = -1; dx <= 1; dx++) {
            for (long dz = -1; dz <= 1; dz++) {
                List<PlacementPoint> nearby = spatial.get(new Cell(center.x() + dx, center.z() + dz));
                if (nearby == null) {
                    continue;
                }
                for (PlacementPoint point : nearby) {
                    double px = (double) point.x() - x;
                    double pz = (double) point.z() - z;
                    if (px * px + pz * pz < minDistanceSquared) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Cell cellFor(int x, int z) {
        return new Cell((long) Math.floor(x / cellSize), (long) Math.floor(z / cellSize));
    }

    private record Cell(long x, long z) {
    }
}
