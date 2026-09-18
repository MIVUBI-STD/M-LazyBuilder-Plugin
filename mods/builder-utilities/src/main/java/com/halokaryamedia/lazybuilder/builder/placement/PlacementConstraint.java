package com.halokaryamedia.lazybuilder.builder.placement;

@FunctionalInterface
public interface PlacementConstraint {
    boolean test(PlacementPoint point);

    static PlacementConstraint all() {
        return point -> true;
    }
}
