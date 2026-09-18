package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;

/** Pure policy for suppressing only distant particles while the client is under pressure. */
public final class ParticlePressurePolicy {
    private static final double ELEVATED_DISTANCE_SQ = 96.0D * 96.0D;
    private static final double HEAVY_DISTANCE_SQ = 64.0D * 64.0D;

    private ParticlePressurePolicy() {
    }

    public static boolean shouldSuppress(FramePressure pressure, double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) return false;
        if (pressure == FramePressure.HEAVY) return distanceSquared > HEAVY_DISTANCE_SQ;
        if (pressure == FramePressure.ELEVATED) return distanceSquared > ELEVATED_DISTANCE_SQ;
        return false;
    }
}
