package com.halokaryamedia.lazybuilder.builder.material;

/** Deterministic multi-octave value noise normalized to the [0,1] range. */
public final class FractalNoiseField implements ScalarField {
    private final ValueNoiseField[] octaves;
    private final double[] amplitudes;
    private final double amplitudeSum;

    public FractalNoiseField(
            double baseFrequency,
            int octaveCount,
            double lacunarity,
            double persistence,
            long channel
    ) {
        if (!Double.isFinite(baseFrequency) || baseFrequency <= 0.0) {
            throw new IllegalArgumentException("baseFrequency must be finite and > 0");
        }
        if (octaveCount <= 0 || octaveCount > 16) {
            throw new IllegalArgumentException("octaveCount must be in 1..16");
        }
        if (!Double.isFinite(lacunarity) || lacunarity <= 1.0) {
            throw new IllegalArgumentException("lacunarity must be finite and > 1");
        }
        if (!Double.isFinite(persistence) || persistence <= 0.0 || persistence > 1.0) {
            throw new IllegalArgumentException("persistence must be in (0,1]");
        }

        this.octaves = new ValueNoiseField[octaveCount];
        this.amplitudes = new double[octaveCount];
        double frequency = baseFrequency;
        double amplitude = 1.0;
        double total = 0.0;
        for (int i = 0; i < octaveCount; i++) {
            octaves[i] = new ValueNoiseField(frequency, channel + i * 0x9E3779B97F4A7C15L);
            amplitudes[i] = amplitude;
            total += amplitude;
            frequency *= lacunarity;
            amplitude *= persistence;
            if (!Double.isFinite(frequency)) {
                throw new IllegalArgumentException("fractal frequency overflow");
            }
        }
        this.amplitudeSum = total;
    }

    @Override
    public double sample(MaterialContext context) {
        double value = 0.0;
        for (int i = 0; i < octaves.length; i++) {
            value += octaves[i].sample(context) * amplitudes[i];
        }
        return value / amplitudeSum;
    }
}
