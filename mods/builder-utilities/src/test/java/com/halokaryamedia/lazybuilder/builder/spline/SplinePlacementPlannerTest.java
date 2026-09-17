package com.halokaryamedia.lazybuilder.builder.spline;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.placement.WeightedPlacementSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SplinePlacementPlannerTest {
    private static final SplineFrame FRAME = new SplineFrame(
            new BuilderVec3(1, 0, 0),
            new BuilderVec3(0, 1, 0),
            new BuilderVec3(0, 0, 1)
    );

    @Test
    void plansStableArcLengthSpacingOnStraightSpline() {
        List<SplineSample> samples = List.of(
                new SplineSample(0.0, new BuilderVec3(0, 64, 0), FRAME, 2.0, 0.0),
                new SplineSample(1.0, new BuilderVec3(10, 64, 0), FRAME, 4.0, 0.0)
        );
        PlacementSource source = (point, seed) -> "bridge";
        PlacementVariation variation = new PlacementVariation(0, 0, 1, 1, 0, 10);

        List<SplinePlacementPlanEntry> plan = SplinePlacementPlanner.plan(
                samples, 2.5, source, variation, new OperationSeed(42)
        );

        assertEquals(5, plan.size());
        assertEquals(0.0, plan.get(0).position().x(), 1.0e-9);
        assertEquals(2.5, plan.get(1).position().x(), 1.0e-9);
        assertEquals(5.0, plan.get(2).position().x(), 1.0e-9);
        assertEquals(7.5, plan.get(3).position().x(), 1.0e-9);
        assertEquals(10.0, plan.get(4).position().x(), 1.0e-9);
        assertEquals(3.0, plan.get(2).radius(), 1.0e-9);
    }

    @Test
    void structureChainPayloadIsDeterministicForSameSeed() {
        List<SplineSample> samples = List.of(
                new SplineSample(0.0, new BuilderVec3(0, 70, 0), FRAME, 1.0, 0.0),
                new SplineSample(1.0, new BuilderVec3(20, 70, 0), FRAME, 1.0, 0.0)
        );
        WeightedPlacementSource source = new WeightedPlacementSource(List.of(
                new WeightedPlacementSource.Entry("segment-a", 3),
                new WeightedPlacementSource.Entry("segment-b", 1)
        ), 100);
        PlacementVariation variation = new PlacementVariation(-5, 5, 0.9, 1.1, 0.25, 200);
        StructureChainSplinePayload payload = new StructureChainSplinePayload(4.0, source, variation);

        List<SplinePlacementPlanEntry> first = payload.plan(samples, new OperationSeed(99));
        List<SplinePlacementPlanEntry> second = payload.plan(samples, new OperationSeed(99));

        assertEquals(first, second);
        assertEquals(6, first.size());
    }

    @Test
    void rejectsDegenerateSamplePath() {
        List<SplineSample> samples = List.of(
                new SplineSample(0.0, new BuilderVec3(1, 2, 3), FRAME, 1.0, 0.0),
                new SplineSample(1.0, new BuilderVec3(1, 2, 3), FRAME, 1.0, 0.0)
        );
        PlacementVariation variation = new PlacementVariation(0, 0, 1, 1, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> SplinePlacementPlanner.plan(
                samples, 1.0, (point, seed) -> "segment", variation, new OperationSeed(1)
        ));
    }
}
