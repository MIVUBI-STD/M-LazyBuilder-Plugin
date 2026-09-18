package com.halokaryamedia.lazybuilder.builder.spline;

public record BuilderVec3(double x, double y, double z) {
    private static final double EPSILON = 1.0e-12;

    public BuilderVec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("vector components must be finite");
        }
    }

    public BuilderVec3 add(BuilderVec3 other) { return new BuilderVec3(x + other.x, y + other.y, z + other.z); }
    public BuilderVec3 subtract(BuilderVec3 other) { return new BuilderVec3(x - other.x, y - other.y, z - other.z); }
    public BuilderVec3 multiply(double scalar) { return new BuilderVec3(x * scalar, y * scalar, z * scalar); }
    public double dot(BuilderVec3 other) { return x * other.x + y * other.y + z * other.z; }
    public BuilderVec3 cross(BuilderVec3 other) {
        return new BuilderVec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
    public BuilderVec3 normalized() {
        double length = length();
        if (length <= EPSILON) throw new IllegalStateException("cannot normalize near-zero vector");
        return multiply(1.0 / length);
    }
    public double distanceSquared(BuilderVec3 other) { return subtract(other).lengthSquared(); }
}
