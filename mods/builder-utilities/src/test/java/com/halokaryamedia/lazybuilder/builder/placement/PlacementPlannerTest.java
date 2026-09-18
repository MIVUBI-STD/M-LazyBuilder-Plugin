package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementPlannerTest {
    @Test
    void scatterIsDeterministicAndHonorsMinimumSpacing() {
        BlockBounds bounds = new BlockBounds(-50, 60, -50, 50, 80, 50);
        var distribution = new MinimumSpacingScatterDistribution(40, 8.0, 32, 9L);
        OperationSeed seed = new OperationSeed(123);
        List<PlacementPoint> first = distribution.generate(bounds, (x,z) -> 64, seed);
        List<PlacementPoint> second = distribution.generate(bounds, (x,z) -> 64, seed);
        assertEquals(first, second);
        for (int i = 0; i < first.size(); i++) {
            for (int j = i + 1; j < first.size(); j++) {
                double dx = first.get(i).x() - first.get(j).x();
                double dz = first.get(i).z() - first.get(j).z();
                assertTrue(dx * dx + dz * dz >= 64.0);
            }
        }
    }

    @Test
    void weightedSourcesAndVariationProduceStablePlan() {
        BlockBounds bounds = new BlockBounds(0, 0, 0, 32, 10, 32);
        PlacementSource source = new WeightedPlacementSource(List.of(
                new WeightedPlacementSource.Entry("tree-a", 3),
                new WeightedPlacementSource.Entry("tree-b", 1)
        ), 20L);
        PlacementVariation variation = new PlacementVariation(0, 360, 0.8, 1.2, 0.25, 0.0, 30L);
        var distribution = new ArrayDistribution(0, 0, 4, 8, 8);
        OperationSeed seed = new OperationSeed(42);
        var first = PlacementPlanner.plan(bounds, (x,z) -> 5, seed, distribution, source, variation);
        var second = PlacementPlanner.plan(bounds, (x,z) -> 5, seed, distribution, source, variation);
        assertEquals(first, second);
        assertEquals(4, first.size());
        assertTrue(first.stream().allMatch(entry -> entry.transform().scale() >= 0.8 && entry.transform().scale() <= 1.2));
    }
}
