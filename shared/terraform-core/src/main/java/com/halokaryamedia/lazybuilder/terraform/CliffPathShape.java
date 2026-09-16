package com.halokaryamedia.lazybuilder.terraform;

/**
 * Continuous path cliff: intentional macro wall/back-mass, sparse meso breakup,
 * and bounded micro deformation. Noise never owns the silhouette.
 */
public final class CliffPathShape implements ShapeField {
    private final CliffPathSpec spec;
    private final PathFrame[] frames;

    CliffPathShape(CliffPathSpec spec) {
        this.spec = spec;
        this.frames = PathFrame.build(spec.path(), spec.front());
    }

    @Override
    public double sample(double x, double y, double z) {
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
        double backMass = 1.0 - TerrainMath.smooth(backProgress);
        double crestFloor = 0.10;
        double crossSection = Math.max(crestFloor, backMass);

        double macroHeight = 1.0 + macro * 0.12;
        double mesoShoulder = 1.0 + meso * 0.055;
        double topBreakup = SeededNoise.value2D(
                frame.position().x() / 14.0 + frontDistance / 17.0,
                frame.position().z() / 14.0 + localY / 18.0,
                spec.seed() ^ 0x3C6EF372L) * spec.height() * 0.035 * spec.variation().micro();
        double surfaceHeight = Math.max(
                spec.height() * 0.06,
                spec.height() * endpoint * macroHeight * mesoShoulder * crossSection + topBreakup);

        double frontBreakup = SeededNoise.value2D(
                frame.position().x() / 10.0 + localY / 15.0,
                frame.position().z() / 10.0,
                spec.seed() ^ 0xA54FF53AL) * spec.size() * 0.055 * spec.variation().micro();
        double frontBoundary = -frontDepth + frontBreakup;
        double backBoundary = backDepth;

        double outsidePath = p.alongDistance() - spec.size() * (0.55 + 0.20 * endpoint);
        double outsideFront = frontBoundary - frontDistance;
        double outsideBack = frontDistance - backBoundary;
        double outsideBottom = -localY;
        double outsideTop = localY - surfaceHeight;
        return Math.max(Math.max(outsidePath, outsideFront), Math.max(Math.max(outsideBack, outsideBottom), outsideTop));
    }

    public ShapeBounds bounds() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double horizontal = spec.size() * 1.75 + 3.0;
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal);
            maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y() + spec.height() * 1.35 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal);
            maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
