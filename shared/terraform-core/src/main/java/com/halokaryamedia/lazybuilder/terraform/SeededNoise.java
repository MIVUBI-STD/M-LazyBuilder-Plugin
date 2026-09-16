package com.halokaryamedia.lazybuilder.terraform;

final class SeededNoise {
    private SeededNoise() {
    }

    static double value1D(double x, long seed) {
        long x0 = fastFloor(x);
        long x1 = x0 + 1;
        double t = smooth(x - x0);
        return lerp(hashToUnit(x0, 0L, seed), hashToUnit(x1, 0L, seed), t);
    }

    static double value2D(double x, double y, long seed) {
        long x0 = fastFloor(x);
        long y0 = fastFloor(y);
        long x1 = x0 + 1;
        long y1 = y0 + 1;
        double tx = smooth(x - x0);
        double ty = smooth(y - y0);

        double a = lerp(hashToUnit(x0, y0, seed), hashToUnit(x1, y0, seed), tx);
        double b = lerp(hashToUnit(x0, y1, seed), hashToUnit(x1, y1, seed), tx);
        return lerp(a, b, ty);
    }

    private static long fastFloor(double value) {
        long truncated = (long) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hashToUnit(long x, long y, long seed) {
        long value = seed;
        value ^= x * 0x9E3779B97F4A7C15L;
        value ^= y * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        double zeroToOne = (value >>> 11) * 0x1.0p-53;
        return zeroToOne * 2.0 - 1.0;
    }
}
