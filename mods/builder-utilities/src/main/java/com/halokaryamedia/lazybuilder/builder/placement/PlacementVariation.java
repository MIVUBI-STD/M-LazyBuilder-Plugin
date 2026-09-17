package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;

public record PlacementVariation(
        double minYawDegrees,
        double maxYawDegrees,
        double minScale,
        double maxScale,
        double mirrorProbability,
        long channel
) {
    public PlacementVariation {
        if (!Double.isFinite(minYawDegrees) || !Double.isFinite(maxYawDegrees) || maxYawDegrees < minYawDegrees) {
            throw new IllegalArgumentException("yaw range must be finite and ordered");
        }
        if (!Double.isFinite(minScale) || !Double.isFinite(maxScale) || minScale <= 0.0 || maxScale < minScale) {
            throw new IllegalArgumentException("scale range must be finite, positive and ordered");
        }
        if (!Double.isFinite(mirrorProbability) || mirrorProbability < 0.0 || mirrorProbability > 1.0) {
            throw new IllegalArgumentException("mirrorProbability must be in [0,1]");
        }
    }

    public PlacementTransform resolve(PlacementPoint point, OperationSeed seed) {
        double yawUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel);
        double scaleUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel + 1);
        double mirrorUnit = seed.sampleUnit(point.x(), point.y(), point.z(), channel + 2);
        return new PlacementTransform(
                minYawDegrees + (maxYawDegrees - minYawDegrees) * yawUnit,
                minScale + (maxScale - minScale) * scaleUnit,
                mirrorUnit < mirrorProbability
        );
    }
}
