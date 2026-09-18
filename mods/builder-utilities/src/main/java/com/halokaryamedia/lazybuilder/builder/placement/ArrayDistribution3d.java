package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.ArrayList;
import java.util.List;

/** Deterministic integer-safe 3D linear array distribution. */
public record ArrayDistribution3d(
        int originX,
        int originY,
        int originZ,
        int count,
        int stepX,
        int stepY,
        int stepZ
) implements PlacementDistribution {
    public ArrayDistribution3d {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        if (count > 1 && stepX == 0 && stepY == 0 && stepZ == 0) {
            throw new IllegalArgumentException(
                    "multi-entry 3D array requires at least one non-zero step");
        }
    }

    @Override
    public List<PlacementPoint> generate(
            BlockBounds bounds,
            SurfaceHeightSource surface,
            OperationSeed seed
    ) {
        if (bounds == null) throw new NullPointerException("bounds");
        if (surface == null) throw new NullPointerException("surface");
        if (seed == null) throw new NullPointerException("seed");

        List<PlacementPoint> points = new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            long x = (long) originX + (long) stepX * i;
            long y = (long) originY + (long) stepY * i;
            long z = (long) originZ + (long) stepZ * i;
            if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                    || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                    || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
                break;
            }
            int ix = (int) x;
            int iy = (int) y;
            int iz = (int) z;
            if (bounds.contains(ix, iy, iz)) {
                points.add(new PlacementPoint(ix, iy, iz, i));
            }
        }
        return List.copyOf(points);
    }
}
