package com.halokaryamedia.lazybuilder.builder.placement;

/** Axis-aligned XZ footprint used for slope validation and deterministic collision filtering. */
public record PlacementFootprint(int halfWidthX, int halfDepthZ) {
    public PlacementFootprint {
        if (halfWidthX < 0 || halfDepthZ < 0) {
            throw new IllegalArgumentException("footprint half extents must be >= 0");
        }
    }

    public static PlacementFootprint point() {
        return new PlacementFootprint(0, 0);
    }

    public int minX(PlacementPoint point) {
        return Math.subtractExact(point.x(), halfWidthX);
    }

    public int maxX(PlacementPoint point) {
        return Math.addExact(point.x(), halfWidthX);
    }

    public int minZ(PlacementPoint point) {
        return Math.subtractExact(point.z(), halfDepthZ);
    }

    public int maxZ(PlacementPoint point) {
        return Math.addExact(point.z(), halfDepthZ);
    }
}
