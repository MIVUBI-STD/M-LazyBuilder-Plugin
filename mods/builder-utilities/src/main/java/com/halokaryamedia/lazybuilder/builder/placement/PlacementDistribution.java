package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import java.util.List;

@FunctionalInterface
public interface PlacementDistribution {
    List<PlacementPoint> generate(BlockBounds bounds, SurfaceHeightSource surface, OperationSeed seed);
}
