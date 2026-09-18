package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Objects;

/**
 * Measures how strongly the local downhill direction aligns with a configured XZ flow vector.
 * 0 means opposite/flat, 1 means strongly aligned.
 */
public final class SurfaceFlowField implements ScalarField {
    private final SurfaceHeightFieldSource source;
    private final int sampleRadius;
    private final double flowX;
    private final double flowZ;

    public SurfaceFlowField(
            SurfaceHeightFieldSource source,
            int sampleRadius,
            double flowX,
            double flowZ
    ) {
        this.source = Objects.requireNonNull(source, "source");
        if (sampleRadius <= 0) throw new IllegalArgumentException("sampleRadius must be > 0");
        if (!Double.isFinite(flowX) || !Double.isFinite(flowZ)) {
            throw new IllegalArgumentException("flow vector must be finite");
        }
        double length = Math.hypot(flowX, flowZ);
        if (length == 0.0) throw new IllegalArgumentException("flow vector must be non-zero");
        this.sampleRadius = sampleRadius;
        this.flowX = flowX / length;
        this.flowZ = flowZ / length;
    }

    @Override
    public double sample(MaterialContext context) {
        int x = context.x();
        int z = context.z();
        int r = sampleRadius;
        double dx = (source.heightAt(safeAdd(x, r), z) - source.heightAt(safeAdd(x, -r), z)) / (2.0 * r);
        double dz = (source.heightAt(x, safeAdd(z, r)) - source.heightAt(x, safeAdd(z, -r))) / (2.0 * r);
        double gradientLength = Math.hypot(dx, dz);
        if (gradientLength == 0.0) return 0.0;
        double downhillX = -dx / gradientLength;
        double downhillZ = -dz / gradientLength;
        return Math.max(0.0, downhillX * flowX + downhillZ * flowZ);
    }

    private static int safeAdd(int value, int delta) {
        return Math.toIntExact((long) value + delta);
    }
}
