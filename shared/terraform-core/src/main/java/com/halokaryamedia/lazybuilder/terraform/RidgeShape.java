package com.halokaryamedia.lazybuilder.terraform;

/** Path ridge with coherent crest rhythm, broad shoulders, and sparse erosion cuts. */
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
        double rootDepth = spec.size() * (0.22 + 0.05 * spec.variation().macro());
        double rootBlend = localY >= 0.0 ? 1.0 : TerrainMath.smooth(TerrainMath.clamp01((localY + rootDepth) / Math.max(1.0, rootDepth)));

        double crestWave = SeededNoise.value1D(progress * 2.1 + 3.0, spec.seed() ^ 0x6A09E667L);
        double crestWander = crestWave * spec.size() * 0.10 * spec.variation().meso();
        double lateralSigned = p.frontDistance() - crestWander;

        double broadPulse = SeededNoise.value1D(progress * 1.65 + 7.0, spec.seed() ^ 0x3C6EF372L);
        double halfWidth = spec.size() * (0.61 + 0.065 * macro + 0.045 * broadPulse) * (0.62 + 0.38 * rootBlend);
        double lateral = Math.abs(lateralSigned) / Math.max(1.0, halfWidth);

        double crestCore = Math.pow(Math.max(0.0, 1.0 - lateral), 1.38);
        double upperShoulder = Math.pow(Math.max(0.0, 1.0 - lateral * 0.74), 2.0) * 0.15;
        double lowerShoulder = Math.pow(Math.max(0.0, 1.0 - lateral * 0.50), 2.55) * 0.12;

        double sideBias = SeededNoise.value1D(progress * 1.7 + 19.0, spec.seed() ^ 0xA54FF53AL) * 0.055 * spec.variation().meso();
        double asymmetry = 1.0 + Math.copySign(sideBias, lateralSigned == 0.0 ? 1.0 : lateralSigned);

        double cutEvent = event(progress, spec.seed() ^ 0xBB67AE85L, 2.65);
        double cut = cutEvent * Math.max(0.0, 1.0 - lateral * 1.65) * 0.105 * spec.variation().meso();
        double breakup = SeededNoise.value2D(frame.position().x() / 22.0, frame.position().z() / 22.0,
                spec.seed() ^ 0x510E527FL) * 0.026 * spec.variation().micro();

        double crestRhythm = 1.0 + crestWave * 0.075 * spec.variation().macro() + macro * 0.085 + meso * 0.025;
        double surfaceHeight = spec.height() * endpoint
                * Math.max(0.0, crestCore + upperShoulder + lowerShoulder)
                * asymmetry
                * Math.max(0.56, crestRhythm + breakup - cut);

        double baseSkirt = spec.size() * (0.08 + 0.065 * rootBlend)
                * (1.0 - TerrainMath.smooth(TerrainMath.clamp01(Math.max(0.0, localY) / Math.max(1.0, spec.height() * 0.30))));
        double outsidePath = p.alongDistance() - spec.size() * (0.48 + 0.12 * rootBlend + 0.035 * Math.max(0.0, broadPulse));
        double outsideWidth = Math.abs(lateralSigned) - (halfWidth + baseSkirt);
        return Math.max(Math.max(outsidePath, outsideWidth),
                Math.max(-rootDepth - localY, localY - Math.max(spec.height() * 0.025, surfaceHeight)));
    }

    private static double event(double progress, long seed, double frequency) {
        double n = SeededNoise.value1D(progress * frequency + 5.0, seed);
        return TerrainMath.smooth(TerrainMath.clamp01((n + 0.10) * 0.78));
    }

    @Override public ShapeBounds bounds() {
        double horizontal = spec.size() * 1.50 + 4.0;
        double rootDepth = spec.size() * (0.22 + 0.05 * spec.variation().macro());
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal); maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y() - rootDepth); maxY = Math.max(maxY, point.y() + spec.height() * 1.34 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal); maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
