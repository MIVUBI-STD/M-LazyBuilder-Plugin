package com.halokaryamedia.lazybuilder.builder.spline;

public final class SplineAngles {
    private SplineAngles() {}

    public static double lerpDegreesShortest(double from, double to, double alpha) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || !Double.isFinite(alpha)) {
            throw new IllegalArgumentException("angles and alpha must be finite");
        }
        double delta = Math.IEEEremainder(to - from, 360.0);
        // Resolve the exact 180-degree ambiguity deterministically toward the raw delta sign.
        if (delta == -180.0 && to - from > 0.0) delta = 180.0;
        return from + delta * alpha;
    }
}
