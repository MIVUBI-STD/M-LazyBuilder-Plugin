package com.halokaryamedia.lazybuilder.terraform;

/** Path ridge with a wandering crest, broad shoulders and sparse erosion-like cuts. */
public final class RidgeShape implements BoundedShapeField {
    private final RidgeSpec spec;
    private final PathFrame[] frames;

    RidgeShape(RidgeSpec spec) {
        this.spec = spec;
        Vec3d tangent = spec.path().tangentAt(0);
        this.frames = PathFrame.build(spec.path(), new Vec3d(-tangent.z(), 0.0, tangent.x()));
    }

    @Override public double sample(double x, double y, double z) {
        PathShapeSupport.Projection p = PathShapeSupport.nearest(frames, x, z);
        PathFrame frame = p.frame();
        double localY = y - frame.position().y();
        double progress = frame.progress();
        double endpoint = PathShapeSupport.endpointEnvelope(progress);
        double macro = PathShapeSupport.macro(spec.seed(), progress) * spec.variation().macro();
        double meso = PathShapeSupport.meso(spec.seed(), progress) * spec.variation().meso();

        double crestWander = SeededNoise.value2D(progress * 2.2, 0.0, spec.seed() ^ 0x6A09E667L)
                * spec.size() * 0.12 * spec.variation().meso();
        double lateralSigned = p.frontDistance() - crestWander;
        double halfWidth = spec.size() * (0.62 + 0.07 * macro);
        double lateral = Math.abs(lateralSigned) / Math.max(1.0, halfWidth);

        double core = Math.pow(Math.max(0.0, 1.0 - lateral), 1.28);
        double shoulder = Math.pow(Math.max(0.0, 1.0 - lateral * 0.62), 2.15) * 0.22;
        double asymmetry = 1.0 + Math.copySign(0.06 * meso, lateralSigned == 0.0 ? 1.0 : lateralSigned);
        double cut = Math.max(0.0, SeededNoise.value2D(progress * 4.0, 0.0, spec.seed() ^ 0xBB67AE85L))
                * 0.12 * spec.variation().meso();
        double breakup = SeededNoise.value2D(frame.position().x() / 20.0, frame.position().z() / 20.0,
                spec.seed() ^ 0x510E527FL) * 0.035 * spec.variation().micro();

        double surfaceHeight = spec.height() * endpoint
                * Math.max(0.0, core + shoulder)
                * asymmetry
                * (1.0 + macro * 0.12 + meso * 0.035 + breakup - cut);
        double baseSkirt = spec.size() * 0.10 * (1.0 - TerrainMath.smooth(TerrainMath.clamp01(localY / Math.max(1.0, spec.height() * 0.28))));
        double outsidePath = p.alongDistance() - spec.size() * 0.60;
        double outsideWidth = Math.abs(lateralSigned) - (halfWidth + baseSkirt);
        return Math.max(Math.max(outsidePath, outsideWidth),
                Math.max(-localY, localY - Math.max(spec.height() * 0.025, surfaceHeight)));
    }

    @Override public ShapeBounds bounds() {
        double horizontal = spec.size() * 1.48 + 4.0;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal); maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y() + spec.height() * 1.32 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal); maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
