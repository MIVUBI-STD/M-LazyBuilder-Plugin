package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.List;

/** Open Catmull-Rom spline with uniform or centripetal parameterization. */
public final class CatmullRomSpline {
    private static final double MIN_POINT_DISTANCE_SQUARED = 1.0e-12;
    private static final double MIN_KNOT_STEP = 1.0e-6;
    private static final double DERIVATIVE_STEP = 1.0e-5;

    private final List<SplineControlPoint> points;
    private final SplineParameterization parameterization;

    public CatmullRomSpline(List<SplineControlPoint> points) {
        this(points, SplineParameterization.UNIFORM);
    }

    public CatmullRomSpline(
            List<SplineControlPoint> points,
            SplineParameterization parameterization
    ) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("spline requires at least two control points");
        }
        if (parameterization == null) throw new NullPointerException("parameterization");
        this.points = List.copyOf(points);
        this.parameterization = parameterization;
        for (int i = 1; i < this.points.size(); i++) {
            if (this.points.get(i - 1).position()
                    .distanceSquared(this.points.get(i).position()) <= MIN_POINT_DISTANCE_SQUARED) {
                throw new IllegalArgumentException("adjacent spline control points must be distinct");
            }
        }
    }

    public int segmentCount() {
        return points.size() - 1;
    }

    public SplineParameterization parameterization() {
        return parameterization;
    }

    public SplineEvaluation evaluate(double t) {
        if (!Double.isFinite(t) || t < 0.0 || t > 1.0) {
            throw new IllegalArgumentException("t must be in [0,1]");
        }

        double scaled = t * segmentCount();
        int segment = t == 1.0 ? segmentCount() - 1 : (int) Math.floor(scaled);
        double local = t == 1.0 ? 1.0 : scaled - segment;

        SplineControlPoint p0 = points.get(Math.max(0, segment - 1));
        SplineControlPoint p1 = points.get(segment);
        SplineControlPoint p2 = points.get(segment + 1);
        SplineControlPoint p3 = points.get(Math.min(points.size() - 1, segment + 2));

        BuilderVec3 position = position(p0.position(), p1.position(), p2.position(), p3.position(), local);
        BuilderVec3 tangent = tangent(
                p0.position(), p1.position(), p2.position(), p3.position(), local);
        double radius = lerp(p1.radius(), p2.radius(), local);
        double roll = SplineAngles.lerpDegreesShortest(
                p1.rollDegrees(), p2.rollDegrees(), local);
        return new SplineEvaluation(position, tangent, radius, roll);
    }

    private BuilderVec3 position(
            BuilderVec3 p0,
            BuilderVec3 p1,
            BuilderVec3 p2,
            BuilderVec3 p3,
            double t
    ) {
        return parameterization == SplineParameterization.UNIFORM
                ? catmullUniform(p0, p1, p2, p3, t)
                : catmullCentripetal(p0, p1, p2, p3, t);
    }

    private BuilderVec3 tangent(
            BuilderVec3 p0,
            BuilderVec3 p1,
            BuilderVec3 p2,
            BuilderVec3 p3,
            double t
    ) {
        if (parameterization == SplineParameterization.UNIFORM) {
            return catmullUniformDerivative(p0, p1, p2, p3, t).normalized();
        }

        double from = Math.max(0.0, t - DERIVATIVE_STEP);
        double to = Math.min(1.0, t + DERIVATIVE_STEP);
        BuilderVec3 derivative = catmullCentripetal(p0, p1, p2, p3, to)
                .subtract(catmullCentripetal(p0, p1, p2, p3, from));
        if (derivative.lengthSquared() <= MIN_POINT_DISTANCE_SQUARED) {
            derivative = p2.subtract(p1);
        }
        return derivative.normalized();
    }

    private static BuilderVec3 catmullUniform(
            BuilderVec3 p0,
            BuilderVec3 p1,
            BuilderVec3 p2,
            BuilderVec3 p3,
            double t
    ) {
        double t2 = t * t;
        double t3 = t2 * t;
        return p1.multiply(2.0)
                .add(p2.subtract(p0).multiply(t))
                .add(p0.multiply(2.0).subtract(p1.multiply(5.0))
                        .add(p2.multiply(4.0)).subtract(p3).multiply(t2))
                .add(p0.multiply(-1.0).add(p1.multiply(3.0))
                        .subtract(p2.multiply(3.0)).add(p3).multiply(t3))
                .multiply(0.5);
    }

    private static BuilderVec3 catmullUniformDerivative(
            BuilderVec3 p0,
            BuilderVec3 p1,
            BuilderVec3 p2,
            BuilderVec3 p3,
            double t
    ) {
        double t2 = t * t;
        return p2.subtract(p0)
                .add(p0.multiply(2.0).subtract(p1.multiply(5.0))
                        .add(p2.multiply(4.0)).subtract(p3).multiply(2.0 * t))
                .add(p0.multiply(-1.0).add(p1.multiply(3.0))
                        .subtract(p2.multiply(3.0)).add(p3).multiply(3.0 * t2))
                .multiply(0.5);
    }

    private static BuilderVec3 catmullCentripetal(
            BuilderVec3 p0,
            BuilderVec3 p1,
            BuilderVec3 p2,
            BuilderVec3 p3,
            double local
    ) {
        double k0 = 0.0;
        double k1 = k0 + knotStep(p0, p1);
        double k2 = k1 + knotStep(p1, p2);
        double k3 = k2 + knotStep(p2, p3);
        double u = k1 + (k2 - k1) * local;

        BuilderVec3 a1 = interpolateKnot(p0, p1, k0, k1, u);
        BuilderVec3 a2 = interpolateKnot(p1, p2, k1, k2, u);
        BuilderVec3 a3 = interpolateKnot(p2, p3, k2, k3, u);
        BuilderVec3 b1 = interpolateKnot(a1, a2, k0, k2, u);
        BuilderVec3 b2 = interpolateKnot(a2, a3, k1, k3, u);
        return interpolateKnot(b1, b2, k1, k2, u);
    }

    private static double knotStep(BuilderVec3 a, BuilderVec3 b) {
        double distance = Math.sqrt(a.distanceSquared(b));
        return Math.max(MIN_KNOT_STEP, Math.sqrt(distance));
    }

    private static BuilderVec3 interpolateKnot(
            BuilderVec3 a,
            BuilderVec3 b,
            double ta,
            double tb,
            double t
    ) {
        double denominator = tb - ta;
        if (Math.abs(denominator) < MIN_KNOT_STEP) return a;
        double alpha = (t - ta) / denominator;
        return a.multiply(1.0 - alpha).add(b.multiply(alpha));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
