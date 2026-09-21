package com.halokaryamedia.lazybuilder.performance.culling;

import com.halokaryamedia.lazybuilder.performance.FramePressure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CullingPolicyTest {
    @Test
    void farOrHeavyTargetsUseSingleSample() {
        assertEquals(1, CullingPolicy.entitySampleLimit(
                FramePressure.NORMAL, 70.0D * 70.0D, 16.0D
        ));
        assertEquals(1, CullingPolicy.entitySampleLimit(
                FramePressure.HEAVY, 10.0D * 10.0D, 16.0D
        ));
        assertEquals(1, CullingPolicy.blockEntitySampleLimit(
                FramePressure.HEAVY, 10.0D * 10.0D
        ));
    }

    @Test
    void nearbyLargeEntitiesCanUseFullRefinement() {
        assertEquals(7, CullingPolicy.entitySampleLimit(
                FramePressure.NORMAL, 10.0D * 10.0D, 3.0D * 3.0D
        ));
    }

    @Test
    void elevatedPressureBoundsTransparentTraversal() {
        assertEquals(4, CullingPolicy.transparentPassLimit(
                FramePressure.ELEVATED, 10.0D * 10.0D
        ));
        assertEquals(2, CullingPolicy.transparentPassLimit(
                FramePressure.HEAVY, 10.0D * 10.0D
        ));
    }

    @Test
    void occludedResultsCanLiveLongerThanVisibleResults() {
        long occluded = CullingPolicy.cacheTtlNanos(
                VisibilityDecision.OCCLUDED, FramePressure.NORMAL
        );
        long visible = CullingPolicy.cacheTtlNanos(
                VisibilityDecision.VISIBLE, FramePressure.NORMAL
        );
        assertTrue(occluded > visible);
    }
}
