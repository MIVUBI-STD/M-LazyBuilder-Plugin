package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import java.util.ArrayList;
import java.util.List;

public record ArrayDistribution(int originX, int originZ, int count, int stepX, int stepZ)
        implements PlacementDistribution {
    public ArrayDistribution {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        if (count > 1 && stepX == 0 && stepZ == 0) {
            throw new IllegalArgumentException("multi-entry array requires non-zero step");
        }
    }

    @Override
    public List<PlacementPoint> generate(BlockBounds bounds, SurfaceHeightSource surface, OperationSeed seed) {
        List<PlacementPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long xLong = (long) originX + (long) stepX * i;
            long zLong = (long) originZ + (long) stepZ * i;
            if (xLong < Integer.MIN_VALUE || xLong > Integer.MAX_VALUE || zLong < Integer.MIN_VALUE || zLong > Integer.MAX_VALUE) {
                break;
            }
            int x = (int) xLong;
            int z = (int) zLong;
            int y = surface.yAt(x, z);
            if (bounds.contains(x, y, z)) {
                points.add(new PlacementPoint(x, y, z, i));
            }
        }
        return List.copyOf(points);
    }
}
