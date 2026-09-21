package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderMemoryBudgetTest {
    @Test
    void normal4kPipelineFitsDefaultBudget() {
        ShaderMemoryBudget.Estimate estimate = ShaderMemoryBudget.estimate(
                3840,
                2160,
                2,
                true,
                true,
                true,
                2048
        );
        assertTrue(estimate.allowed());
    }

    @Test
    void extremeFramebufferFailsBeforeUnboundedAllocation() {
        ShaderMemoryBudget.Estimate estimate = ShaderMemoryBudget.estimate(
                8192,
                8192,
                2,
                true,
                true,
                true,
                4096
        );
        assertFalse(estimate.allowed());
    }

    @Test
    void invalidDimensionsFailClosed() {
        assertFalse(ShaderMemoryBudget.estimate(
                0, 1080, 0, false, true, false, 0
        ).allowed());
    }
    @Test
    void adaptiveShadowResolutionDropsBeforeRejectingTheFrame() {
        ShaderMemoryBudget.ShadowPlan plan = ShaderMemoryBudget.planShadow(
                5120,
                2880,
                2,
                true,
                true,
                4096
        );

        assertTrue(plan.allowed());
        assertTrue(plan.resolution() < 4096, "shadow resolution should reduce before rejecting the frame");
        assertTrue(plan.resolution() >= 256);
    }

    @Test
    void impossibleFrameStillFailsAfterShadowIsReduced() {
        ShaderMemoryBudget.ShadowPlan plan = ShaderMemoryBudget.planShadow(
                8192,
                8192,
                2,
                true,
                true,
                4096
        );

        assertFalse(plan.allowed());
    }
    @Test
    void lowReportedVramReducesAuxiliaryBudgetConservatively() {
        long twoGiB = 2L * 1024L * 1024L * 1024L;
        long adaptive = ShaderMemoryBudget.adaptiveLimitBytes(twoGiB);

        assertTrue(adaptive >= 128L * 1024L * 1024L);
        assertTrue(adaptive <= ShaderMemoryBudget.adaptiveLimitBytes(0L));
    }
}
