package com.halokaryamedia.lazybuilder.terraform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CliffSpecTest {
    @Test
    void normalizesHorizontalDirection() {
        CliffSpec spec = new CliffSpec(0, 64, 0, 3, 4, 40, 30, 18, 7L);
        assertEquals(0.6, spec.directionX(), 1.0e-12);
        assertEquals(0.8, spec.directionZ(), 1.0e-12);
    }

    @Test
    void rejectsZeroDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> new CliffSpec(0, 64, 0, 0, 0, 40, 30, 18, 7L));
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new CliffSpec(0, 64, 0, 1, 0, 0, 30, 18, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> new CliffSpec(0, 64, 0, 1, 0, 40, -1, 18, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> new CliffSpec(0, 64, 0, 1, 0, 40, 30, Double.NaN, 7L));
    }
}
