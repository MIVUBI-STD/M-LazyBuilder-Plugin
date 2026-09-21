package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameProofHistogramTest {
    @Test
    void keepsLongRunPercentilesBoundedWithoutFrameHistory() {
        FrameProofHistogram histogram = new FrameProofHistogram();
        for (int i = 0; i < 9_900; i++) histogram.record(10.0D);
        for (int i = 0; i < 90; i++) histogram.record(30.0D);
        for (int i = 0; i < 10; i++) histogram.record(80.0D);

        FrameProofHistogram.Snapshot snapshot = histogram.snapshot();

        assertEquals(10_000L, snapshot.samples());
        assertEquals(10.0D, snapshot.p50Ms(), 0.0001D);
        assertTrue(snapshot.p99Ms() >= 10.0D);
        assertTrue(snapshot.p999Ms() >= 33.33D);
        assertEquals(100L, snapshot.framesOver16_67Ms());
        assertEquals(100L, snapshot.framesOver25Ms());
        assertEquals(10L, snapshot.framesOver33_33Ms());
        assertEquals(10L, snapshot.framesOver50Ms());
    }

    @Test
    void resetDropsPriorSessionEvidence() {
        FrameProofHistogram histogram = new FrameProofHistogram();
        histogram.record(50.0D);

        histogram.reset();

        assertEquals(0L, histogram.snapshot().samples());
    }
}
