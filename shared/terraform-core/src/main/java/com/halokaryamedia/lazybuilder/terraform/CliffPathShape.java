package com.halokaryamedia.lazybuilder.terraform;

/** Continuous path cliff with intentional macro/meso structure and bounded deformation. */
public final class CliffPathShape implements BoundedShapeField {
    private final CliffPathSpec spec;
    private final PathFrame[] frames;

    CliffPathShape(CliffPathSpec spec) {
        this.spec = spec;
        this.frames = PathFrame.build(spec.path(), spec.front());
    }

    @Override public double sample(double x, double y, double z) {
        PathShapeSupport.Projection p = PathShapeSupport.nearest(frames, x, z);
        PathFrame frame = p.frame();
        double localY = y - frame.position().y();
        double progress = frame.progress();
        double endpoint = PathShapeSupport.endpointEnvelope(progress);
        double macro = PathShapeSupport.macro(spec.seed(), progress) * spec.variation().macro();
        double meso = PathShapeSupport.meso(spec.seed(), progress) * spec.variation().meso();
        double frontDepth = spec.size() * (0.22 + 0.035 * meso);
        double backDepth = spec.size() * (0.78 + 0.08 * macro);
        double frontDistance = p.frontDistance();
        double backProgress = TerrainMath.clamp01((frontDistance + spec.size() * 0.02) / Math.max(1.0, backDepth));
        double crossSection = Math.max(0.10, 1.0 - TerrainMath.smooth(backProgress));
        double topBreakup = SeededNoise.value2D(
                frame.position().x() / 14.0 + frontDistance / 17.0,
                frame.position().z() / 14.0 + localY / 18.0,
                spec.seed() ^ 0x3C6EF372L) * spec.height() * 0.035 * spec.variation().micro();
        double surfaceHeight = Math.max(spec.height() * 0.06,
                spec.height() * endpoint * (1.0 + macro * 0.12) * (1.0 + meso * 0.055) * crossSection + topBreakup);
        double frontBreakup = SeededNoise.value2D(
                frame.position().x() / 10.0 + localY / 15.0,
                frame.position().z() / 10.0,
                spec.seed() ^ 0xA54FF53AL) * spec.size() * 0.055 * spec.variation().micro();
        double outsidePath = p.alongDistance() - spec.size() * (0.55 + 0.20 * endpoint);
        double outsideFront = (-frontDepth + frontBreakup) - frontDistance;
        double outsideBack = frontDistance - backDepth;
        return Math.max(Math.max(outsidePath, outsideFront),
                Math.max(Math.max(outsideBack, -localY), localY - surfaceHeight));
    }

    @Override public ShapeBounds bounds() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double horizontal = spec.size() * 1.75 + 3.0;
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal); maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y() + spec.height() * 1.35 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal); maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
