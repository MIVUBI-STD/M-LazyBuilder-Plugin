package com.halokaryamedia.lazybuilder.builder.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationSeedTest {
    @Test
    void samplingIsDeterministicForPreviewCommitParity() {
        OperationSeed seed = new OperationSeed(42L);
        double first = seed.sampleUnit(12, 70, -8, 3L);
        double second = seed.sampleUnit(12, 70, -8, 3L);
        assertEquals(first, second);
    }

    @Test
    void coordinateAndChannelChangesProduceDifferentSamples() {
        OperationSeed seed = new OperationSeed(42L);
        long baseline = seed.sampleLong(1, 2, 3, 0L);
        assertNotEquals(baseline, seed.sampleLong(2, 2, 3, 0L));
        assertNotEquals(baseline, seed.sampleLong(1, 3, 3, 0L));
        assertNotEquals(baseline, seed.sampleLong(1, 2, 4, 0L));
        assertNotEquals(baseline, seed.sampleLong(1, 2, 3, 1L));
    }

    @Test
    void unitSamplesStayWithinHalfOpenUnitInterval() {
        OperationSeed seed = new OperationSeed(-9001L);
        for (int i = -100; i <= 100; i++) {
            double value = seed.sampleUnit(i, i * 2, -i, 7L);
            assertTrue(value >= 0.0);
            assertTrue(value < 1.0);
        }
    }
}
