package com.halokaryamedia.lazybuilder.terraform;

final class TerrainMath {
    private TerrainMath() {}

    static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double smooth(double t) { t = clamp01(t); return t * t * (3.0 - 2.0 * t); }
    static double bell(double t) { return Math.sin(Math.PI * clamp01(t)); }

    static double signedDistanceToSegment2d(double x, double z, Vec3d a, Vec3d b) {
        double abx = b.x() - a.x();
        double abz = b.z() - a.z();
        double denominator = abx * abx + abz * abz;
        if (denominator < 1.0e-9) return Math.hypot(x - a.x(), z - a.z());
        double t = clamp01(((x - a.x()) * abx + (z - a.z()) * abz) / denominator);
        double px = a.x() + abx * t;
        double pz = a.z() + abz * t;
        return Math.hypot(x - px, z - pz);
    }
}
