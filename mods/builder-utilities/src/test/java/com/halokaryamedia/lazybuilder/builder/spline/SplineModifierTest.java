package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SplineModifierTest {
    private static List<SplineSample> samples() {
        SplineFrame frame = ParallelTransport.initial(new BuilderVec3(1, 0, 0));
        return List.of(
                new SplineSample(0, new BuilderVec3(0, 0, 0), frame, 2, 0),
                new SplineSample(0.5, new BuilderVec3(5, 0, 0), frame, 2, 0),
                new SplineSample(1, new BuilderVec3(10, 0, 0), frame, 2, 0)
        );
    }

    @Test
    void taperAndTwistApplyAcrossNormalizedLength() {
        List<SplineSample> modified = SplineModifierPipeline.apply(
                samples(),
                SplineModifiers.compose(
                        SplineModifiers.taper(1.0, 0.25),
                        SplineModifiers.twist(0.0, 180.0)
                ),
                new OperationSeed(1)
        );

        assertEquals(2.0, modified.get(0).radius(), 1e-9);
        assertEquals(0.5, modified.get(2).radius(), 1e-9);
        assertEquals(180.0, modified.get(2).rollDegrees(), 1e-9);
        assertEquals(-1.0, modified.get(2).frame().normal().dot(samples().get(2).frame().normal()), 1e-9);
    }

    @Test
    void deterministicJitterHasPreviewCommitParity() {
        SplineModifier jitter = SplineModifiers.jitter(1.0, 2.0, 0.5, 99L);
        OperationSeed seed = new OperationSeed(42L);
        List<SplineSample> first = SplineModifierPipeline.apply(samples(), jitter, seed);
        List<SplineSample> second = SplineModifierPipeline.apply(samples(), jitter, seed);
        assertEquals(first, second);
        for (SplineSample sample : first) {
            assertTrue(sample.radius() >= 0.0);
        }
    }
}
