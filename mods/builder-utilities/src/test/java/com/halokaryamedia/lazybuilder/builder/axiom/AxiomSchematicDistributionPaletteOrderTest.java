package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.WeightedPlacementSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxiomSchematicDistributionPaletteOrderTest {
    @Test
    void sameSeedSelectsTheSameSourcesRegardlessOfCatalogIterationOrder() {
        var first = AxiomSchematicDistributionTool.sortedPaletteEntries(
                new LinkedHashSet<>(List.of("z.schem", "a.schem", "m.schem")));
        var reordered = AxiomSchematicDistributionTool.sortedPaletteEntries(
                new LinkedHashSet<>(List.of("m.schem", "z.schem", "a.schem")));

        assertEquals(List.of("a.schem", "m.schem", "z.schem"),
                first.stream().map(WeightedPlacementSource.Entry::sourceId).toList());
        assertEquals(first, reordered);

        var points = List.of(
                new PlacementPoint(0, 64, 0, 0),
                new PlacementPoint(8, 64, 0, 1),
                new PlacementPoint(16, 64, 0, 2),
                new PlacementPoint(24, 64, 0, 3)
        );
        var sourceA = new WeightedPlacementSource(first, 0x50414c455454454cL);
        var sourceB = new WeightedPlacementSource(reordered, 0x50414c455454454cL);
        var seed = new OperationSeed(424242);
        for (var point : points) {
            assertEquals(sourceA.resolve(point, seed), sourceB.resolve(point, seed));
        }
    }
}
