package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure policy mirroring vanilla's early translucent-sort cancellation conditions. */
public final class TranslucentSortPolicy {
    private TranslucentSortPolicy() {
    }

    public static boolean shouldSkip(
            boolean hasTranslucentLayer,
            boolean sameNormalizedRelativePosition,
            boolean onCameraAxis
    ) {
        if (!hasTranslucentLayer) return true;
        return sameNormalizedRelativePosition && !onCameraAxis;
    }
}
