package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SectionRingUpdatePolicyTest {
    @Test
    void oneSectionMoveChangesOnlyOneRingIndex() {
        assertEquals(1, SectionRingUpdatePolicy.changedIndexCount(0, 1, 2, 5));
        assertEquals(1, SectionRingUpdatePolicy.changedIndexCount(0, -1, 2, 5));
    }

    @Test
    void twoSectionMoveChangesTwoRingIndices() {
        assertEquals(2, SectionRingUpdatePolicy.changedIndexCount(0, 2, 2, 5));
    }

    @Test
    void unchangedCameraKeepsEveryMappingStable() {
        for (int index = 0; index < 5; index++) {
            assertFalse(SectionRingUpdatePolicy.mappingChanged(index, 3, 3, 2, 5));
        }
    }

    @Test
    void diagonalSingleSectionMoveTouchesOnlyTwoStrips() {
        assertEquals(9, SectionRingUpdatePolicy.uniqueChangedColumns(1, 1, 5, 5));
        assertTrue(SectionRingUpdatePolicy.mappingChanged(3, 0, 1, 2, 5)
                || SectionRingUpdatePolicy.mappingChanged(3, 0, -1, 2, 5));
    }
}
