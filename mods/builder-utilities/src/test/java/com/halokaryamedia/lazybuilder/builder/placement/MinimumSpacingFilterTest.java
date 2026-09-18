package com.halokaryamedia.lazybuilder.builder.placement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinimumSpacingFilterTest {
    @Test
    void filtersTransformedPointsGloballyAndReindexesOrdinals() {
        List<PlacementPoint> result = MinimumSpacingFilter.filter(
                List.of(
                        new PlacementPoint(0, 64, 0, 10),
                        new PlacementPoint(1, 64, 0, 11),
                        new PlacementPoint(5, 64, 0, 12)
                ),
                3.0
        );

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).ordinal());
        assertEquals(1, result.get(1).ordinal());
        assertEquals(5, result.get(1).x());
    }

    @Test
    void zeroSpacingPreservesAllCandidates() {
        assertEquals(2, MinimumSpacingFilter.filter(
                List.of(
                        new PlacementPoint(0, 64, 0, 0),
                        new PlacementPoint(0, 64, 0, 1)),
                0.0).size());
    }
}
