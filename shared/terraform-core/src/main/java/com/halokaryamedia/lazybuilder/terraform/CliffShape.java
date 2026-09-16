package com.halokaryamedia.lazybuilder.terraform;

/**
 * Backward-compatible straight-cliff adapter. New editor work should construct
 * {@link CliffPathSpec}; this class keeps the original deterministic contract.
 */
public final class CliffShape implements ShapeField {
    private final CliffSpec spec;
    private final CliffPathShape delegate;

    CliffShape(CliffSpec spec) {
        this.spec = spec;
        Vec3d start = new Vec3d(spec.originX(), spec.originY(), spec.originZ());
        Vec3d end = new Vec3d(
                spec.originX() + spec.directionX() * spec.length(),
                spec.originY(),
                spec.originZ() + spec.directionZ() * spec.length());
        Vec3d tangent = new Vec3d(spec.directionX(), 0.0, spec.directionZ());
        Vec3d front = new Vec3d(-tangent.z(), 0.0, tangent.x());
        this.delegate = new CliffPathSpec(
                TerrainPath.fromEndpoints(start, end),
                front,
                spec.width(),
                spec.height(),
                TerrainVariation.NATURAL,
                spec.seed()).createField();
    }

    public CliffSpec spec() { return spec; }
    @Override public double sample(double x, double y, double z) { return delegate.sample(x, y, z); }
    public ShapeBounds bounds() { return delegate.bounds(); }
}
