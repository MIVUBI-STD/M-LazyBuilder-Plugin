package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TranslucentSortPolicyTest {
    @Test
    void noTranslucentLayerAlwaysSkips() {
        assertTrue(TranslucentSortPolicy.shouldSkip(false, false, false));
        assertTrue(TranslucentSortPolicy.shouldSkip(false, true, true));
    }

    @Test
    void unchangedRelativePositionSkipsAwayFromCameraAxis() {
        assertTrue(TranslucentSortPolicy.shouldSkip(true, true, false));
    }

    @Test
    void cameraAxisPreservesVanillaResort() {
        assertFalse(TranslucentSortPolicy.shouldSkip(true, true, true));
    }

    @Test
    void changedRelativePositionStillSorts() {
        assertFalse(TranslucentSortPolicy.shouldSkip(true, false, false));
        assertFalse(TranslucentSortPolicy.shouldSkip(true, false, true));
    }
}
