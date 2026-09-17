package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.List;

/** Open Catmull-Rom spline with endpoint duplication and linear radius/roll profiles. */
public final class CatmullRomSpline {
    private static final double MIN_POINT_DISTANCE_SQUARED = 1.0e-12;
    private final List<SplineControlPoint> points;

    public CatmullRomSpline(List<SplineControlPoint> points) {
        if (points == null || points.size() < 2) throw new IllegalArgumentException("spline requires at least two control points");
        this.points = List.copyOf(points);
        for (int i = 1; i < this.points.size(); i++) {
            if (this.points.get(i - 1).position().distanceSquared(this.points.get(i).position()) <= MIN_POINT_DISTANCE_SQUARED) {
                throw new IllegalArgumentException("adjacent spline control points must be distinct");
            }
        }
    }

    public int segmentCount() { return points.size() - 1; }

    public SplineEvaluation evaluate(double t) {
        if (!Double.isFinite(t) || t < 0.0 || t > 1.0) throw new IllegalArgumentException("t must be in [0,1]");
        double scaled = t * segmentCount();
        int segment = t == 1.0 ? segmentCount() - 1 : (int) Math.floor(scaled);
        double local = t == 1.0 ? 1.0 : scaled - segment;

        SplineControlPoint p0 = points.get(Math.max(0, segment - 1));
        SplineControlPoint p1 = points.get(segment);
        SplineControlPoint p2 = points.get(segment + 1);
        SplineControlPoint p3 = points.get(Math.min(points.size() - 1, segment + 2));

        BuilderVec3 position = catmull(p0.position(), p1.position(), p2.position(), p3.position(), local);
        BuilderVec3 tangent = catmullDerivative(p0.position(), p1.position(), p2.position(), p3.position(), local).normalized();
        double radius = lerp(p1.radius(), p2.radius(), local);
        double roll = lerp(p1.rollDegrees(), p2.rollDegrees(), local);
        return new SplineEvaluation(position, tangent, radius, roll);
    }

    private static BuilderVec3 catmull(BuilderVec3 p0, BuilderVec3 p1, BuilderVec3 p2, BuilderVec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return p1.multiply(2.0)
                .add(p2.subtract(p0).multiply(t))
                .add(p0.multiply(2.0).subtract(p1.multiply(5.0)).add(p2.multiply(4.0)).subtract(p3).multiply(t2))
                .add(p0.multiply(-1.0).add(p1.multiply(3.0)).subtract(p2.multiply(3.0)).add(p3).multiply(t3))
                .multiply(0.5);
    }

    private static BuilderVec3 catmullDerivative(BuilderVec3 p0, BuilderVec3 p1, BuilderVec3 p2, BuilderVec3 p3, double t) {
        double t2 = t * t;
        return p2.subtract(p0)
                .add(p0.multiply(2.0).subtract(p1.multiply(5.0)).add(p2.multiply(4.0)).subtract(p3).multiply(2.0 * t))
                .add(p0.multiply(-1.0).add(p1.multiply(3.0)).subtract(p2.multiply(3.0)).add(p3).multiply(3.0 * t2))
                .multiply(0.5);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
