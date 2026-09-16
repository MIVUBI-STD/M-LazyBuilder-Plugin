package com.halokaryamedia.lazybuilder.terraform;

/**
 * Deterministic asymmetric cliff field.
 *
 * <p>The primary shape is intentional: a steep front face, a high shelf, and a
 * receding back slope. Seeded noise only deforms those forms within bounded
 * amplitudes so the cliff never degenerates into a noise blob.</p>
 */
public final class CliffShape implements ShapeField {
    private final CliffSpec spec;
    private final double dirX;
    private final double dirZ;
    private final double sideX;
    private final double sideZ;

    CliffShape(CliffSpec spec) {
        this.spec = spec;
        this.dirX = spec.directionX();
        this.dirZ = spec.directionZ();
        this.sideX = -dirZ;
        this.sideZ = dirX;
    }

    public CliffSpec spec() {
        return spec;
    }

    @Override
    public double sample(double x, double y, double z) {
        double dx = x - spec.originX();
        double dz = z - spec.originZ();
        double along = dx * dirX + dz * dirZ;
        double side = dx * sideX + dz * sideZ;
        double localY = y - spec.originY();

        double t = clamp01(along / spec.length());
        double endEnvelope = 0.82 + 0.18 * Math.sin(Math.PI * t);
        double longitudinal = 1.0 + 0.075 * SeededNoise.value1D(along / 11.0, spec.seed() ^ 0x51A7E2D3L);

        double frontDepth = spec.width() * 0.30;
        double backDepth = spec.width() * 0.70;

        // Vertical breakup is sampled mostly by height so the front reads as
        // cliff columns/recesses instead of soft spherical noise.
        double frontBreakup = SeededNoise.value2D(
                along / 8.0,
                localY / 12.0,
                spec.seed() ^ 0x1C11FFL
        ) * spec.width() * 0.075;
        double backBreakup = SeededNoise.value1D(
                along / 13.0,
                spec.seed() ^ 0xBA5E5L
        ) * spec.width() * 0.045;

        double frontBoundary = -frontDepth + frontBreakup;
        double backBoundary = backDepth + backBreakup;

        double backProgress = clamp01((side - spec.width() * 0.05) / (spec.width() * 0.65));
        double backSlope = 1.0 - smoothstep(0.0, 1.0, backProgress);
        double minimumShoulder = 0.12;
        double crossSection = Math.max(minimumShoulder, backSlope);

        double topBreakup = SeededNoise.value2D(
                along / 15.0,
                side / 10.0,
                spec.seed() ^ 0x70F5EEDL
        ) * spec.height() * 0.045;
        double surfaceHeight = Math.max(
                spec.height() * 0.08,
                spec.height() * endEnvelope * longitudinal * crossSection + topBreakup
        );

        double outsideAlongStart = -along;
        double outsideAlongEnd = along - spec.length();
        double outsideFront = frontBoundary - side;
        double outsideBack = side - backBoundary;
        double outsideBottom = -localY;
        double outsideTop = localY - surfaceHeight;

        return max(
                outsideAlongStart,
                outsideAlongEnd,
                outsideFront,
                outsideBack,
                outsideBottom,
                outsideTop
        );
    }

    public ShapeBounds bounds() {
        double padding = spec.width() * 0.18 + 2.0;
        double endX = spec.originX() + dirX * spec.length();
        double endZ = spec.originZ() + dirZ * spec.length();
        double sideExtent = spec.width() + padding;

        double minX = Math.min(spec.originX(), endX) - Math.abs(sideX) * sideExtent - padding;
        double maxX = Math.max(spec.originX(), endX) + Math.abs(sideX) * sideExtent + padding;
        double minZ = Math.min(spec.originZ(), endZ) - Math.abs(sideZ) * sideExtent - padding;
        double maxZ = Math.max(spec.originZ(), endZ) + Math.abs(sideZ) * sideExtent + padding;

        return new ShapeBounds(
                minX,
                spec.originY(),
                minZ,
                maxX,
                spec.originY() + spec.height() * 1.18 + 2.0,
                maxZ
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double max(double... values) {
        double result = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }
}
