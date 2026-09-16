package com.halokaryamedia.lazybuilder.terraform;

/** Continuous path cliff with hierarchical crest, sparse wall events, and bounded breakup. */
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
        double rootDepth = spec.size() * (0.26 + 0.06 * spec.variation().macro());
        double rootBlend = localY >= 0.0 ? 1.0 : TerrainMath.smooth(TerrainMath.clamp01((localY + rootDepth) / Math.max(1.0, rootDepth)));

        double crestWave = SeededNoise.value1D(progress * 2.35 + 4.0, spec.seed() ^ 0x13198A2EL);
        double crestShoulder = event(progress, spec.seed() ^ 0xA4093822L, 1.65);
        double crestEnvelope = 1.0 + crestWave * 0.08 * spec.variation().macro() + crestShoulder * 0.055 * spec.variation().meso();

        double backDepth = spec.size() * (0.80 + 0.07 * macro + 0.04 * crestShoulder) * (0.58 + 0.42 * rootBlend);
        double frontDistance = p.frontDistance();
        double backProgress = TerrainMath.clamp01((frontDistance + spec.size() * 0.02) / Math.max(1.0, backDepth));
        double backSlope = Math.max(0.07, 1.0 - TerrainMath.smooth(backProgress));

        double topBreakup = SeededNoise.value2D(
                frame.position().x() / 18.0 + frontDistance / 23.0,
                frame.position().z() / 18.0 + progress * 1.35,
                spec.seed() ^ 0x3C6EF372L) * spec.height() * 0.020 * spec.variation().micro();
        double surfaceHeight = Math.max(spec.height() * 0.06,
                spec.height() * endpoint * crestEnvelope * (1.0 + macro * 0.105 + meso * 0.035) * backSlope + topBreakup);

        double vertical01 = TerrainMath.clamp01(localY / Math.max(1.0, surfaceHeight));
        double baseFlare = (1.0 - TerrainMath.smooth(TerrainMath.clamp01(vertical01 / 0.28))) * spec.size() * 0.17;
        double highShoulder = band(vertical01, 0.73, 0.18) * spec.size() * (0.055 + 0.04 * Math.max(0.0, macro));

        double ledgeEventA = event(progress + 0.05, spec.seed() ^ 0x243F6A88L, 2.15);
        double ledgeEventB = event(progress + 0.31, spec.seed() ^ 0xB7E15162L, 2.55);
        double ledges = (
                band(vertical01, 0.26, 0.060) * 0.78 * ledgeEventA
                + band(vertical01, 0.50, 0.052) * 0.58 * ledgeEventB
                + band(vertical01, 0.69, 0.045) * 0.40 * Math.max(ledgeEventA, ledgeEventB))
                * spec.size() * 0.155 * spec.variation().meso();

        double recessA = event(progress + 0.18, spec.seed() ^ 0x85A308D3L, 1.90);
        double recessB = event(progress + 0.59, spec.seed() ^ 0xC6EF3720L, 2.35);
        double recess = (band(vertical01, 0.58, 0.17) * recessA + band(vertical01, 0.36, 0.13) * recessB * 0.55)
                * spec.size() * 0.085 * spec.variation().meso();

        double frontBreakup = SeededNoise.value2D(
                frame.position().x() / 13.0 + localY / 19.0,
                frame.position().z() / 13.0 + progress,
                spec.seed() ^ 0xA54FF53AL) * spec.size() * 0.035 * spec.variation().micro();
        double frontDepth = spec.size() * (0.22 + 0.028 * meso) * (0.62 + 0.38 * rootBlend);
        double frontBoundary = -frontDepth - baseFlare * rootBlend - highShoulder - ledges + recess + frontBreakup;

        double pathWidth = spec.size() * (0.44 + 0.11 * rootBlend + 0.18 * endpoint + 0.03 * Math.max(0.0, macro));
        double outsidePath = p.alongDistance() - pathWidth;
        double outsideFront = frontBoundary - frontDistance;
        double outsideBack = frontDistance - backDepth;
        return Math.max(Math.max(outsidePath, outsideFront),
                Math.max(Math.max(outsideBack, -rootDepth - localY), localY - surfaceHeight));
    }

    private static double band(double value, double center, double width) {
        double d = Math.abs(value - center) / Math.max(0.0001, width);
        return 1.0 - TerrainMath.smooth(TerrainMath.clamp01(d));
    }

    private static double event(double progress, long seed, double frequency) {
        double n = SeededNoise.value1D(progress * frequency + 11.0, seed);
        return TerrainMath.smooth(TerrainMath.clamp01((n + 0.12) * 0.82));
    }

    @Override public ShapeBounds bounds() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double horizontal = spec.size() * 1.92 + 4.0;
        double rootDepth = spec.size() * (0.26 + 0.06 * spec.variation().macro());
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal); maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y() - rootDepth); maxY = Math.max(maxY, point.y() + spec.height() * 1.36 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal); maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
