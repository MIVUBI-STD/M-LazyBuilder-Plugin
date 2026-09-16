package com.halokaryamedia.lazybuilder.terraform;

/** Continuous path cliff with macro mass, sparse ledges/recesses and bounded surface breakup. */
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

        double backDepth = spec.size() * (0.78 + 0.08 * macro) * (0.58 + 0.42 * rootBlend);
        double frontDistance = p.frontDistance();
        double backProgress = TerrainMath.clamp01((frontDistance + spec.size() * 0.02) / Math.max(1.0, backDepth));
        double backSlope = Math.max(0.08, 1.0 - TerrainMath.smooth(backProgress));

        double topBreakup = SeededNoise.value2D(
                frame.position().x() / 16.0 + frontDistance / 19.0,
                frame.position().z() / 16.0 + progress * 2.0,
                spec.seed() ^ 0x3C6EF372L) * spec.height() * 0.028 * spec.variation().micro();
        double surfaceHeight = Math.max(spec.height() * 0.06,
                spec.height() * endpoint * (1.0 + macro * 0.13) * (1.0 + meso * 0.05) * backSlope + topBreakup);

        double vertical01 = TerrainMath.clamp01(localY / Math.max(1.0, surfaceHeight));
        double baseFlare = (1.0 - TerrainMath.smooth(TerrainMath.clamp01(vertical01 / 0.30))) * spec.size() * 0.18;
        double shoulder = band(vertical01, 0.72, 0.16) * spec.size() * (0.05 + 0.05 * Math.max(0.0, macro));
        double ledges = (band(vertical01, 0.24, 0.055) * 0.70
                + band(vertical01, 0.48, 0.050) * 0.55
                + band(vertical01, 0.68, 0.045) * 0.42)
                * spec.size() * 0.16 * spec.variation().meso()
                * (0.55 + 0.45 * eventMask(progress, spec.seed() ^ 0x243F6A88L));
        double recess = band(vertical01, 0.56, 0.15) * spec.size() * 0.10 * spec.variation().meso()
                * eventMask(progress + 0.17, spec.seed() ^ 0x85A308D3L);

        double frontBreakup = SeededNoise.value2D(
                frame.position().x() / 11.0 + localY / 17.0,
                frame.position().z() / 11.0 + progress * 1.5,
                spec.seed() ^ 0xA54FF53AL) * spec.size() * 0.045 * spec.variation().micro();
        double frontDepth = spec.size() * (0.22 + 0.035 * meso) * (0.62 + 0.38 * rootBlend);
        double frontBoundary = -frontDepth - baseFlare * rootBlend - shoulder - ledges + recess + frontBreakup;

        double outsidePath = p.alongDistance() - spec.size() * (0.44 + 0.11 * rootBlend + 0.20 * endpoint);
        double outsideFront = frontBoundary - frontDistance;
        double outsideBack = frontDistance - backDepth;
        return Math.max(Math.max(outsidePath, outsideFront),
                Math.max(Math.max(outsideBack, -rootDepth - localY), localY - surfaceHeight));
    }

    private static double band(double value, double center, double width) {
        double d = Math.abs(value - center) / Math.max(0.0001, width);
        return 1.0 - TerrainMath.smooth(TerrainMath.clamp01(d));
    }

    private static double eventMask(double progress, long seed) {
        double n = SeededNoise.value2D(progress * 3.1, 0.0, seed);
        return TerrainMath.smooth(TerrainMath.clamp01((n + 0.20) * 0.90));
    }

    @Override public ShapeBounds bounds() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double horizontal = spec.size() * 1.90 + 4.0;
        double rootDepth = spec.size() * (0.26 + 0.06 * spec.variation().macro());
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal); maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y() - rootDepth); maxY = Math.max(maxY, point.y() + spec.height() * 1.35 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal); maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
