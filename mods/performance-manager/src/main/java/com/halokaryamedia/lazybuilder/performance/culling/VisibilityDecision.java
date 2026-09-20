package com.halokaryamedia.lazybuilder.performance.culling;

/** Cached visibility result. Absence from the cache represents an unknown decision. */
public enum VisibilityDecision {
    VISIBLE,
    OCCLUDED
}
