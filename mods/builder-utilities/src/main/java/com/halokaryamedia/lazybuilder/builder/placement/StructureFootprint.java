package com.halokaryamedia.lazybuilder.builder.placement;

/**
 * Source-local rectangular structure footprint. Width/depth are full block extents
 * before placement scale/yaw are applied.
 */
public record StructureFootprint(double width, double depth) {
    public StructureFootprint {
        if (!Double.isFinite(width) || width <= 0.0
                || !Double.isFinite(depth) || depth <= 0.0) {
            throw new IllegalArgumentException("footprint dimensions must be finite and > 0");
        }
    }

    public Resolved resolve(PlacementPoint point, PlacementTransform transform) {
        double scaledWidth = width * transform.scale();
        double scaledDepth = depth * transform.scale();
        double radians = Math.toRadians(transform.yawDegrees());
        double cos = Math.abs(Math.cos(radians));
        double sin = Math.abs(Math.sin(radians));
        double aabbWidth = scaledWidth * cos + scaledDepth * sin;
        double aabbDepth = scaledWidth * sin + scaledDepth * cos;
        return new Resolved(
                point.x() - aabbWidth / 2.0,
                point.x() + aabbWidth / 2.0,
                point.z() - aabbDepth / 2.0,
                point.z() + aabbDepth / 2.0
        );
    }

    public record Resolved(double minX, double maxX, double minZ, double maxZ) {
        public boolean overlaps(Resolved other) {
            return minX < other.maxX && maxX > other.minX
                    && minZ < other.maxZ && maxZ > other.minZ;
        }
    }
}
