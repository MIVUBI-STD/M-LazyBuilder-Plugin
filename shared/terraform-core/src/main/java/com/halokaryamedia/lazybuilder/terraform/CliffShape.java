package com.halokaryamedia.lazybuilder.terraform;

/**
 * Backward-compatible straight-cliff field.
 *
 * <p>The legacy straight-drag contract is intentionally stricter than the path cliff:
 * the dragged segment is a hard longitudinal boundary, the front face stays steep,
 * and the solid recedes toward the back as elevation increases.</p>
 */
public final class CliffShape implements ShapeField {
    private final CliffSpec spec;

    CliffShape(CliffSpec spec) {
        this.spec = spec;
    }

    public CliffSpec spec() {
        return spec;
    }

    @Override
    public double sample(double x, double y, double z) {
        double dx = x - spec.originX();
        double dz = z - spec.originZ();
        double along = dx * spec.directionX() + dz * spec.directionZ();

        // Perpendicular to the drag: for a +X drag, negative Z is the exposed
        // front and positive Z is the receding back.
        double frontDistance = dz * spec.directionX() - dx * spec.directionZ();
        double localY = y - spec.originY();

        double rootDepth = spec.width() * 0.24;
        double vertical01 = TerrainMath.clamp01(localY / spec.height());
        double progress = TerrainMath.clamp01(along / spec.length());

        // Keep bounded deterministic variation without allowing noise to violate
        // the straight-drag geometry contract.
        double macro = SeededNoise.value1D(progress * 3.0 + 5.0, spec.seed() ^ 0x6A09E667L);
        double crest = SeededNoise.value1D(progress * 5.5 + 13.0, spec.seed() ^ 0xBB67AE85L);
        double surfaceHeight = spec.height() * (0.97 + 0.03 * crest);

        // The front remains almost vertical. The back loses depth as elevation
        // rises, producing a readable cliff instead of a symmetric blob.
        double frontDepth = spec.width() * (0.24 + 0.015 * macro);
        double backDepthAtBase = spec.width() * (0.86 + 0.035 * macro);
        double backRecession = 0.58 * TerrainMath.smooth(vertical01);
        double backDepth = Math.max(spec.width() * 0.22, backDepthAtBase * (1.0 - backRecession));

        double outsideStart = -along;
        double outsideEnd = along - spec.length();
        double outsideFront = -frontDepth - frontDistance;
        double outsideBack = frontDistance - backDepth;
        double outsideBottom = -rootDepth - localY;
        double outsideTop = localY - surfaceHeight;

        return Math.max(
                Math.max(outsideStart, outsideEnd),
                Math.max(Math.max(outsideFront, outsideBack), Math.max(outsideBottom, outsideTop)));
    }

    public ShapeBounds bounds() {
        double endX = spec.originX() + spec.directionX() * spec.length();
        double endZ = spec.originZ() + spec.directionZ() * spec.length();
        double horizontalMargin = spec.width();
        double rootDepth = spec.width() * 0.24;

        return new ShapeBounds(
                Math.min(spec.originX(), endX) - horizontalMargin,
                spec.originY() - rootDepth,
                Math.min(spec.originZ(), endZ) - horizontalMargin,
                Math.max(spec.originX(), endX) + horizontalMargin,
                spec.originY() + spec.height() * 1.03,
                Math.max(spec.originZ(), endZ) + horizontalMargin);
    }
}
