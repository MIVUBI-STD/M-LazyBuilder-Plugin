package com.halokaryamedia.lazybuilder.terraform;

/** Stable local frame transported along a terrain path. */
public record PathFrame(Vec3d position, Vec3d tangent, Vec3d front, double progress) {
    public PathFrame {
        tangent = tangent.horizontalNormalized();
        front = front.horizontalNormalized();
        progress = TerrainMath.clamp01(progress);
    }

    public Vec3d side() { return new Vec3d(-tangent.z(), 0.0, tangent.x()); }

    public static PathFrame[] build(TerrainPath path, Vec3d preferredFront) {
        Vec3d frontSeed = preferredFront.horizontalNormalized();
        PathFrame[] frames = new PathFrame[path.points().size()];
        double traversed = 0.0;
        for (int i = 0; i < frames.length; i++) {
            if (i > 0) traversed += path.points().get(i - 1).distanceTo(path.points().get(i));
            Vec3d tangent = path.tangentAt(i);
            Vec3d perpendicular = new Vec3d(-tangent.z(), 0.0, tangent.x());
            if (perpendicular.dot(frontSeed) < 0.0) perpendicular = perpendicular.multiply(-1.0);
            Vec3d front = Vec3d.lerp(frontSeed, perpendicular, 0.72).horizontalNormalized();
            frontSeed = front;
            frames[i] = new PathFrame(path.points().get(i), tangent, front, traversed / path.length());
        }
        return frames;
    }
}
