package com.halokaryamedia.lazybuilder.utility.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactDebugCoordinateTextTest {
    @Test
    void displayKeepsExplicitXyzLabelsAndCanonicalOrder() {
        assertEquals(
                "X -11   Y 71   Z -481",
                CompactDebugCoordinateText.display(-11, 71, -481)
        );
    }

    @Test
    void clipboardKeepsRawXyzTripletWithoutLabels() {
        assertEquals(
                "-11 71 -481",
                CompactDebugCoordinateText.clipboard(-11, 71, -481)
        );
    }
}
