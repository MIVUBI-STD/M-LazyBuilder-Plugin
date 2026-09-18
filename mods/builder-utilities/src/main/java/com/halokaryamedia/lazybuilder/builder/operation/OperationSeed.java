package com.halokaryamedia.lazybuilder.builder.operation;

/**
 * Deterministic position/channel random source used so preview and commit resolve
 * procedural choices identically without sharing mutable RNG state.
 */
public record OperationSeed(long value) {
    public long sampleLong(int x, int y, int z, long channel) {
        long mixed = value;
        mixed ^= mix64(((long) x << 32) ^ Integer.toUnsignedLong(z));
        mixed ^= mix64(((long) y << 32) ^ channel);
        return mix64(mixed);
    }

    public double sampleUnit(int x, int y, int z, long channel) {
        long bits = sampleLong(x, y, z, channel) >>> 11;
        return bits * 0x1.0p-53;
    }

    private static long mix64(long input) {
        long z = input + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
