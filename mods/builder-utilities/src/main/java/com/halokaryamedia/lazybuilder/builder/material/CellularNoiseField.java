package com.halokaryamedia.lazybuilder.builder.material;

/**
 * Deterministic Worley-style nearest-feature distance field.
 * Values are normalized approximately to [0,1] inside each lattice cell.
 */
public final class CellularNoiseField implements ScalarField {
    private final double frequency;
    private final long channel;

    public CellularNoiseField(double frequency, long channel) {
        if (!Double.isFinite(frequency) || frequency <= 0.0) {
            throw new IllegalArgumentException("frequency must be finite and > 0");
        }
        this.frequency = frequency;
        this.channel = channel;
    }

    @Override
    public double sample(MaterialContext context) {
        double sx = context.x() * frequency;
        double sy = context.y() * frequency;
        double sz = context.z() * frequency;
        int cx = safeFloor(sx);
        int cy = safeFloor(sy);
        int cz = safeFloor(sz);
        double best = Double.POSITIVE_INFINITY;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int gx = safeAdd(cx, dx);
                    int gy = safeAdd(cy, dy);
                    int gz = safeAdd(cz, dz);
                    double fx = gx + context.seed().sampleUnit(gx, gy, gz, channel);
                    double fy = gy + context.seed().sampleUnit(gx, gy, gz, channel + 1);
                    double fz = gz + context.seed().sampleUnit(gx, gy, gz, channel + 2);
                    double x = fx - sx;
                    double y = fy - sy;
                    double z = fz - sz;
                    best = Math.min(best, x * x + y * y + z * z);
                }
            }
        }
        return Math.min(1.0, Math.sqrt(best) / Math.sqrt(3.0));
    }

    private static int safeFloor(double value) {
        if (value < Integer.MIN_VALUE + 1.0 || value > Integer.MAX_VALUE - 1.0) {
            throw new IllegalArgumentException("scaled material coordinate exceeds safe cellular range");
        }
        return (int) Math.floor(value);
    }

    private static int safeAdd(int value, int delta) {
        return Math.addExact(value, delta);
    }
}
