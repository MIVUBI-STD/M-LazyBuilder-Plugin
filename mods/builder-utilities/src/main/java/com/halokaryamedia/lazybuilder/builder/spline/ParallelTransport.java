package com.halokaryamedia.lazybuilder.builder.spline;

/** Stable frame transport for structure chains, pipes and cross-sections along splines. */
public final class ParallelTransport {
    private static final double EPSILON = 1.0e-10;

    private ParallelTransport() {
    }

    public static SplineFrame initial(BuilderVec3 tangent) {
        BuilderVec3 t = tangent.normalized();
        BuilderVec3 reference = Math.abs(t.y()) < 0.9 ? new BuilderVec3(0, 1, 0) : new BuilderVec3(1, 0, 0);
        BuilderVec3 normal = reference.subtract(t.multiply(reference.dot(t))).normalized();
        return new SplineFrame(t, normal, t.cross(normal).normalized());
    }

    public static SplineFrame transport(SplineFrame previous, BuilderVec3 nextTangent) {
        BuilderVec3 from = previous.tangent().normalized();
        BuilderVec3 to = nextTangent.normalized();
        BuilderVec3 axis = from.cross(to);
        double sin = axis.length();
        double dot = clamp(from.dot(to), -1.0, 1.0);
        BuilderVec3 normal;
        if (sin <= EPSILON) {
            // For an exact/near 180-degree reversal, keep the previous normal as
            // the rotation axis. This maps tangent -> -tangent without resetting
            // the frame to an unrelated world-space reference.
            normal = previous.normal();
        } else {
            normal = rotate(previous.normal(), axis.multiply(1.0 / sin), Math.atan2(sin, dot));
        }
        normal = normal.subtract(to.multiply(normal.dot(to))).normalized();
        return new SplineFrame(to, normal, to.cross(normal).normalized());
    }

    public static SplineFrame applyRoll(SplineFrame frame, double rollDegrees) {
        double radians = Math.toRadians(rollDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        BuilderVec3 normal = frame.normal().multiply(cos).add(frame.binormal().multiply(sin)).normalized();
        return new SplineFrame(frame.tangent(), normal, frame.tangent().cross(normal).normalized());
    }

    private static BuilderVec3 rotate(BuilderVec3 vector, BuilderVec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.multiply(cos)
                .add(axis.cross(vector).multiply(sin))
                .add(axis.multiply(axis.dot(vector) * (1.0 - cos)));
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
