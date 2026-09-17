package com.halokaryamedia.lazybuilder.builder.material;

/** Deterministic smooth lattice value noise driven only by OperationSeed and position. */
public final class ValueNoiseField implements ScalarField {
    private final double frequency;
    private final long channel;

    public ValueNoiseField(double frequency, long channel) {
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
        int x0 = floorToLattice(sx);
        int y0 = floorToLattice(sy);
        int z0 = floorToLattice(sz);
        double tx = fade(sx - x0);
        double ty = fade(sy - y0);
        double tz = fade(sz - z0);

        double x00 = lerp(lattice(context, x0, y0, z0), lattice(context, x0 + 1, y0, z0), tx);
        double x10 = lerp(lattice(context, x0, y0 + 1, z0), lattice(context, x0 + 1, y0 + 1, z0), tx);
        double x01 = lerp(lattice(context, x0, y0, z0 + 1), lattice(context, x0 + 1, y0, z0 + 1), tx);
        double x11 = lerp(lattice(context, x0, y0 + 1, z0 + 1), lattice(context, x0 + 1, y0 + 1, z0 + 1), tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    private double lattice(MaterialContext context, int x, int y, int z) {
        return context.seed().sampleUnit(x, y, z, channel);
    }

    private static int floorToLattice(double value) {
        if (value < Integer.MIN_VALUE || value >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("scaled material coordinate exceeds safe lattice range");
        }
        return (int) Math.floor(value);
    }

    private static double fade(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
