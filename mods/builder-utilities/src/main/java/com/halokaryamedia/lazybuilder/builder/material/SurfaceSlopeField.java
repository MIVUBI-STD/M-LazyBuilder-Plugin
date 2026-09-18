package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/**
 * Surface slope in degrees derived from central differences of a height field.
 */
public final class SurfaceSlopeField implements ScalarField {
    private final SurfaceHeightFieldSource source;
    private final int sampleRadius;

    public SurfaceSlopeField(SurfaceHeightFieldSource source, int sampleRadius) {
        this.source = Objects.requireNonNull(source, "source");
        if (sampleRadius <= 0) {
            throw new IllegalArgumentException("sampleRadius must be > 0");
        }
        this.sampleRadius = sampleRadius;
    }

    @Override
    public double sample(MaterialContext context) {
        int x = context.x();
        int z = context.z();
        int r = sampleRadius;
        double dx = (source.heightAt(safeAdd(x, r), z) - source.heightAt(safeAdd(x, -r), z)) / (2.0 * r);
        double dz = (source.heightAt(x, safeAdd(z, r)) - source.heightAt(x, safeAdd(z, -r))) / (2.0 * r);
        return Math.toDegrees(Math.atan(Math.hypot(dx, dz)));
    }

    private static int safeAdd(int value, int delta) {
        long result = (long) value + delta;
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("surface sample coordinate overflow");
        }
        return (int) result;
    }
}
