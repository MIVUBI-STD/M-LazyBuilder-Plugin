package com.halokaryamedia.lazybuilder.builder.material;

/**
 * Deterministic 3D cellular/Voronoi distance field.
 * Returns 0 near a feature point and approaches 1 near cell boundaries/farther regions.
 */
public final class CellNoiseField implements ScalarField {
    private final double frequency;
    private final long channel;

    public CellNoiseField(double frequency, long channel) {
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
        int cx = floorSafe(sx);
        int cy = floorSafe(sy);
        int cz = floorSafe(sz);
        double nearestSquared = Double.POSITIVE_INFINITY;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int gx = safeAdd(cx, dx);
                    int gy = safeAdd(cy, dy);
                    int gz = safeAdd(cz, dz);
                    double fx = gx + context.seed().sampleUnit(gx, gy, gz, channel);
                    double fy = gy + context.seed().sampleUnit(gx, gy, gz, channel + 1);
                    double fz = gz + context.seed().sampleUnit(gx, gy, gz, channel + 2);
                    double px = fx - sx;
                    double py = fy - sy;
                    double pz = fz - sz;
                    nearestSquared = Math.min(nearestSquared, px * px + py * py + pz * pz);
                }
            }
        }

        // sqrt(3) is the maximum useful neighborhood distance for this normalized field.
        return Math.min(1.0, Math.sqrt(nearestSquared) / Math.sqrt(3.0));
    }

    private static int floorSafe(double value) {
        if (value < Integer.MIN_VALUE + 1.0 || value > Integer.MAX_VALUE - 1.0) {
            throw new IllegalArgumentException("scaled cell coordinate exceeds safe range");
        }
        return (int) Math.floor(value);
    }

    private static int safeAdd(int value, int delta) {
        return Math.toIntExact((long) value + delta);
    }
}
