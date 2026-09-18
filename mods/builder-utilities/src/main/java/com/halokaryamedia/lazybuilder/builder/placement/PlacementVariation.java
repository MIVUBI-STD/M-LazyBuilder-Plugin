package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

public record PlacementVariation(
        double minYawDegrees,
        double maxYawDegrees,
        double minScale,
        double maxScale,
        double mirrorProbability,
        double mirrorZProbability,
        long channel
) {
    /** Backward-compatible constructor: original mirror probability controls X only. */
    public PlacementVariation(
            double minYawDegrees,
            double maxYawDegrees,
            double minScale,
            double maxScale,
            double mirrorProbability,
            long channel
    ) {
        this(
                minYawDegrees,
                maxYawDegrees,
                minScale,
                maxScale,
                mirrorProbability,
                0.0,
                channel
        );
    }

    public PlacementVariation {
        if (!Double.isFinite(minYawDegrees) || !Double.isFinite(maxYawDegrees)
                || maxYawDegrees < minYawDegrees) {
            throw new IllegalArgumentException("yaw range must be finite and ordered");
        }
        if (!Double.isFinite(minScale) || !Double.isFinite(maxScale)
                || minScale <= 0.0 || maxScale < minScale) {
            throw new IllegalArgumentException(
                    "scale range must be finite, positive and ordered");
        }
        validateProbability(mirrorProbability, "mirrorProbability");
        validateProbability(mirrorZProbability, "mirrorZProbability");
    }

    public PlacementTransform resolve(PlacementPoint point, OperationSeed seed) {
        double yawUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel);
        double scaleUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel + 1);
        double mirrorXUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel + 2);
        double mirrorZUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel + 3);
        return new PlacementTransform(
                minYawDegrees + (maxYawDegrees - minYawDegrees) * yawUnit,
                minScale + (maxScale - minScale) * scaleUnit,
                mirrorXUnit < mirrorProbability,
                mirrorZUnit < mirrorZProbability
        );
    }

    private static void validateProbability(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }
}
