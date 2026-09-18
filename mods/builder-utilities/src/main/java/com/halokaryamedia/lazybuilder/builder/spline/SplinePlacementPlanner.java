package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Arc-length based structure-chain planner for spline payloads.
 */
public final class SplinePlacementPlanner {
    public static final int MAX_PLACEMENTS = 100_000;
    private static final double EPSILON = 1.0e-9;

    private SplinePlacementPlanner() {
    }

    public static List<SplinePlacementPlanEntry> plan(
            List<SplineSample> samples,
            double spacing,
            PlacementSource source,
            PlacementVariation variation,
            OperationSeed seed
    ) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variation, "variation");
        Objects.requireNonNull(seed, "seed");
        if (samples.size() < 2) {
            throw new IllegalArgumentException("at least two spline samples are required");
        }
        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            throw new IllegalArgumentException("spacing must be finite and > 0");
        }

        List<SplinePlacementPlanEntry> result = new ArrayList<>();
        double traversed = 0.0;
        double nextDistance = 0.0;

        for (int segment = 1; segment < samples.size(); segment++) {
            SplineSample from = Objects.requireNonNull(samples.get(segment - 1), "sample");
            SplineSample to = Objects.requireNonNull(samples.get(segment), "sample");
            BuilderVec3 delta = to.position().subtract(from.position());
            double length = delta.length();
            if (length <= EPSILON) {
                continue;
            }
            double segmentEnd = traversed + length;
            while (nextDistance <= segmentEnd + EPSILON) {
                if (result.size() >= MAX_PLACEMENTS) {
                    throw new IllegalArgumentException("spline placement count exceeds " + MAX_PLACEMENTS);
                }
                double alpha = Math.max(0.0, Math.min(1.0, (nextDistance - traversed) / length));
                SplineSample interpolated = interpolate(from, to, alpha);
                int ordinal = result.size();
                PlacementPoint point = new PlacementPoint(
                        roundedCoordinate(interpolated.position().x()),
                        roundedCoordinate(interpolated.position().y()),
                        roundedCoordinate(interpolated.position().z()),
                        ordinal
                );
                String sourceId = source.resolve(point, seed);
                PlacementTransform transform = variation.resolve(point, seed);
                result.add(new SplinePlacementPlanEntry(
                        ordinal,
                        interpolated.position(),
                        interpolated.frame(),
                        interpolated.radius(),
                        sourceId,
                        transform
                ));
                nextDistance += spacing;
            }
            traversed = segmentEnd;
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("spline has no non-zero-length segments");
        }
        return List.copyOf(result);
    }

    private static SplineSample interpolate(SplineSample from, SplineSample to, double alpha) {
        BuilderVec3 position = lerp(from.position(), to.position(), alpha);
        BuilderVec3 tangent = lerp(from.frame().tangent(), to.frame().tangent(), alpha).normalized();
        BuilderVec3 normalCandidate = lerp(from.frame().normal(), to.frame().normal(), alpha);
        normalCandidate = normalCandidate.subtract(tangent.multiply(normalCandidate.dot(tangent)));
        if (normalCandidate.lengthSquared() <= EPSILON * EPSILON) {
            normalCandidate = from.frame().normal().subtract(tangent.multiply(from.frame().normal().dot(tangent)));
        }
        BuilderVec3 normal = normalCandidate.normalized();
        BuilderVec3 binormal = tangent.cross(normal).normalized();
        normal = binormal.cross(tangent).normalized();
        SplineFrame frame = new SplineFrame(tangent, normal, binormal);
        double radius = from.radius() + (to.radius() - from.radius()) * alpha;
        double roll = SplineAngles.lerpDegreesShortest(
                from.rollDegrees(), to.rollDegrees(), alpha);
        double t = from.t() + (to.t() - from.t()) * alpha;
        return new SplineSample(t, position, frame, radius, roll);
    }

    private static BuilderVec3 lerp(BuilderVec3 from, BuilderVec3 to, double alpha) {
        return from.multiply(1.0 - alpha).add(to.multiply(alpha));
    }

    private static int roundedCoordinate(double value) {
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("spline placement coordinate exceeds integer world range");
        }
        return (int) rounded;
    }
}
