package com.halokaryamedia.lazybuilder.performance.culling;

import com.halokaryamedia.lazybuilder.performance.FramePressure;

/**
 * Pure adaptive policy for occlusion refinement cost.
 *
 * WorldRenderer already applies vanilla distance/frustum rejection before LazyBuilder's
 * render hook. This policy therefore spends raycasts only on the remaining ambiguous
 * occlusion cases and scales the refinement effort with pressure, distance and target size.
 */
public final class CullingPolicy {
    private static final double FAR_DISTANCE_SQ = 64.0D * 64.0D;
    private static final double MEDIUM_DISTANCE_SQ = 32.0D * 32.0D;
    private static final double LARGE_TARGET_DIAGONAL_SQ = 2.5D * 2.5D;

    private CullingPolicy() {
    }

    public static int entitySampleLimit(
            FramePressure pressure,
            double distanceSquared,
            double targetDiagonalSquared
    ) {
        if (pressure == FramePressure.HEAVY) return 1;
        if (distanceSquared >= FAR_DISTANCE_SQ) return 1;
        if (pressure == FramePressure.ELEVATED || distanceSquared >= MEDIUM_DISTANCE_SQ) return 3;
        return targetDiagonalSquared >= LARGE_TARGET_DIAGONAL_SQ ? 7 : 3;
    }

    public static int blockEntitySampleLimit(FramePressure pressure, double distanceSquared) {
        if (pressure == FramePressure.HEAVY || distanceSquared >= FAR_DISTANCE_SQ) return 1;
        if (pressure == FramePressure.ELEVATED || distanceSquared >= MEDIUM_DISTANCE_SQ) return 3;
        return 5;
    }

    public static int transparentPassLimit(FramePressure pressure, double distanceSquared) {
        if (pressure == FramePressure.HEAVY || distanceSquared >= FAR_DISTANCE_SQ) return 2;
        if (pressure == FramePressure.ELEVATED || distanceSquared >= MEDIUM_DISTANCE_SQ) return 4;
        return 8;
    }

    public static long cacheTtlNanos(VisibilityDecision decision, FramePressure pressure) {
        if (decision == VisibilityDecision.OCCLUDED) {
            if (pressure == FramePressure.HEAVY) return 400_000_000L;
            if (pressure == FramePressure.ELEVATED) return 325_000_000L;
            return 275_000_000L;
        }
        if (pressure == FramePressure.HEAVY) return 125_000_000L;
        return 200_000_000L;
    }
}
