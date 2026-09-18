package com.halokaryamedia.lazybuilder.performance.culling;

/** Cached visibility result. UNKNOWN always falls back to rendering. */
public enum VisibilityDecision {
    UNKNOWN,
    VISIBLE,
    OCCLUDED
}
