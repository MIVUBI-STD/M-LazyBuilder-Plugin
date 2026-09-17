package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PerformanceSnapshotTest {
    @Test
    void memoryRatioRemainsBoundedWithChunkDiagnosticsPresent() {
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
                120,
                8.0D,
                9.0D,
                14.0D,
                512L,
                1024L,
                16,
                12,
                true,
                false,
                FramePressure.NORMAL,
                200,
                4,
                2,
                3,
                7L,
                5L,
                "chunks",
                "entities",
                "particles"
        );

        assertEquals(0.5D, snapshot.usedMemoryRatio());
        assertEquals(4, snapshot.chunkTasksToBatch());
        assertEquals(2, snapshot.chunksToUpload());
        assertEquals(3, snapshot.freeChunkBuffers());
        assertEquals(7L, snapshot.coalescedChunkRebuildRequests());
        assertEquals(5L, snapshot.chunkBufferAcquireMisses());
    }
}
