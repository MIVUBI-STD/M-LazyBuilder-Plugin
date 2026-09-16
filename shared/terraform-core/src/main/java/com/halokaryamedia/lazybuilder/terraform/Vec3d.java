package com.halokaryamedia.lazybuilder.terraform;

/** Platform-neutral immutable vector used by the terrain geometry kernel. */
public record Vec3d(double x, double y, double z) {
    public Vec3d {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("vector components must be finite");
        }
    }

    public Vec3d add(Vec3d other) { return new Vec3d(x + other.x, y + other.y, z + other.z); }
    public Vec3d subtract(Vec3d other) { return new Vec3d(x - other.x, y - other.y, z - other.z); }
    public Vec3d multiply(double scalar) { return new Vec3d(x * scalar, y * scalar, z * scalar); }
    public double dot(Vec3d other) { return x * other.x + y * other.y + z * other.z; }
    public double length() { return Math.sqrt(dot(this)); }
    public double distanceTo(Vec3d other) { return subtract(other).length(); }

    public Vec3d normalize() {
        double length = length();
        if (length < 1.0e-9) throw new IllegalStateException("cannot normalize a zero vector");
        return multiply(1.0 / length);
    }

    public Vec3d horizontalNormalized() {
        double length = Math.hypot(x, z);
        if (length < 1.0e-9) throw new IllegalStateException("horizontal vector must not be zero");
        return new Vec3d(x / length, 0.0, z / length);
    }

    public static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        double s = TerrainMath.clamp01(t);
        return new Vec3d(
                TerrainMath.lerp(a.x, b.x, s),
                TerrainMath.lerp(a.y, b.y, s),
                TerrainMath.lerp(a.z, b.z, s));
    }
}
