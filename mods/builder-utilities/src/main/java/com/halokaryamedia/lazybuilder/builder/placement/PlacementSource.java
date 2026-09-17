package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

@FunctionalInterface
public interface PlacementSource {
    String resolve(PlacementPoint point, OperationSeed seed);
}
