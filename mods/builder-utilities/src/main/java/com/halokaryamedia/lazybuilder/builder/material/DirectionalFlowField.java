package com.halokaryamedia.lazybuilder.builder.material;

/**
 * Repeats a directional wave and distorts it with a secondary scalar field.
 * Useful for sediment streaks, strata and flow-aligned texturing.
 */
public final class DirectionalFlowField implements ScalarField {
    private final double nx;
    private final double ny;
    private final double nz;
    private final double frequency;
    private final double distortionStrength;
    private final ScalarField distortion;

    public DirectionalFlowField(
            double x,
            double y,
            double z,
            double frequency,
            double distortionStrength,
            ScalarField distortion
    ) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (!Double.isFinite(length) || length <= 1e-12) {
            throw new IllegalArgumentException("direction must be finite and non-zero");
        }
        if (!Double.isFinite(frequency) || frequency <= 0.0) {
            throw new IllegalArgumentException("frequency must be finite and > 0");
        }
        if (!Double.isFinite(distortionStrength)) {
            throw new IllegalArgumentException("distortionStrength must be finite");
        }
        if (distortion == null) throw new NullPointerException("distortion");
        nx = x / length;
        ny = y / length;
        nz = z / length;
        this.frequency = frequency;
        this.distortionStrength = distortionStrength;
        this.distortion = distortion;
    }

    @Override
    public double sample(MaterialContext context) {
        double phase = (context.x() * nx + context.y() * ny + context.z() * nz) * frequency;
        phase += (distortion.sample(context) - 0.5) * distortionStrength;
        double wrapped = phase - Math.floor(phase);
        return 0.5 - 0.5 * Math.cos(wrapped * Math.PI * 2.0);
    }
}
