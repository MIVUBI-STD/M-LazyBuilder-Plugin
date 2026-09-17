package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/**
 * Normalized discrete surface curvature. Positive values are locally convex,
 * negative values locally concave, and flat planes approach zero.
 */
public final class SurfaceCurvatureField implements ScalarField {
    private final SurfaceHeightFieldSource source;
    private final int sampleRadius;
    private final double normalization;

    public SurfaceCurvatureField(SurfaceHeightFieldSource source, int sampleRadius, double normalization) {
        this.source = Objects.requireNonNull(source, "source");
        if (sampleRadius <= 0) {
            throw new IllegalArgumentException("sampleRadius must be > 0");
        }
        if (!Double.isFinite(normalization) || normalization <= 0.0) {
            throw new IllegalArgumentException("normalization must be finite and > 0");
        }
        this.sampleRadius = sampleRadius;
        this.normalization = normalization;
    }

    @Override
    public double sample(MaterialContext context) {
        int x = context.x();
        int z = context.z();
        int r = sampleRadius;
        double center = source.heightAt(x, z);
        double neighbors = source.heightAt(safeAdd(x, r), z)
                + source.heightAt(safeAdd(x, -r), z)
                + source.heightAt(x, safeAdd(z, r))
                + source.heightAt(x, safeAdd(z, -r));
        double laplacian = (4.0 * center - neighbors) / ((double) r * r);
        return clamp(laplacian / normalization, -1.0, 1.0);
    }

    private static int safeAdd(int value, int delta) {
        long result = (long) value + delta;
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("surface sample coordinate overflow");
        }
        return (int) result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
