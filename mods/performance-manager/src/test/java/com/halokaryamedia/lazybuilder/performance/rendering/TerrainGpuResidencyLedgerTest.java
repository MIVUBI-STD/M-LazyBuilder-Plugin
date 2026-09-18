package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainGpuResidencyLedgerTest {
    @Test
    void aggregatesCapacityPayloadHeadroomAndArenaProjectionByRegion() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();

        ledger.associate(a, 0, 0, 0, 0);
        ledger.associate(b, 7, 3, 7, 1);
        ledger.associate(c, 8, 0, 0, 2);
        ledger.recordCapacity(a, 100, 20);
        ledger.recordPayload(a, 80, 20);
        ledger.recordCapacity(b, 200, 0);
        ledger.recordPayload(b, 150, 0);
        ledger.recordCapacity(c, 300, 30);
        ledger.recordPayload(c, 200, 30);

        TerrainGpuResidencyLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(650L, snapshot.residentBytes());
        assertEquals(480L, snapshot.payloadBytes());
        assertEquals(170L, snapshot.headroomBytes());
        assertEquals(650L, snapshot.peakResidentBytes());
        assertEquals(3, snapshot.residentBuffers());
        assertEquals(2, snapshot.residentRegions());
        assertEquals(330L, snapshot.largestRegionBytes());
        assertEquals(100L, snapshot.largestRegionHeadroomBytes());
        assertEquals(0L, snapshot.regionRelocations());
        assertEquals(2L << 20, snapshot.projectedArenaBytes());
        assertEquals((2L << 20) - 480L, snapshot.projectedArenaSlackBytes());
        assertEquals(0, snapshot.arenaCompactionCandidateRegions());
        assertEquals(0L, snapshot.potentialArenaReclaimBytes());
        assertEquals(330L, ledger.capacityBytes(c));

        ledger.release(c);
        TerrainGpuResidencyLedger.Snapshot afterRelease = ledger.snapshot();
        assertEquals(320L, afterRelease.residentBytes());
        assertEquals(250L, afterRelease.payloadBytes());
        assertEquals(70L, afterRelease.headroomBytes());
        assertEquals(650L, afterRelease.peakResidentBytes());
        assertEquals(2, afterRelease.residentBuffers());
        assertEquals(1, afterRelease.residentRegions());
        assertEquals(1L << 20, afterRelease.projectedArenaBytes());
    }

    @Test
    void movingResidentBufferAcrossAccountingRegionCountsRelocation() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object buffer = new Object();

        ledger.associate(buffer, 0, 0, 0, 3);
        ledger.recordCapacity(buffer, 512, 128);
        ledger.recordPayload(buffer, 400, 100);
        ledger.associate(buffer, 7, 3, 7, 3);
        assertEquals(0L, ledger.snapshot().regionRelocations());

        ledger.associate(buffer, 8, 0, 0, 3);
        TerrainGpuResidencyLedger.Snapshot moved = ledger.snapshot();
        assertEquals(1L, moved.regionRelocations());
        assertEquals(640L, moved.residentBytes());
        assertEquals(500L, moved.payloadBytes());
    }

    @Test
    void flagsRegionWhenSharedArenaCouldRecoverMeaningfulCapacity() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object buffer = new Object();

        ledger.associate(buffer, 0, 0, 0, 0);
        ledger.recordCapacity(buffer, 8 << 20, 0);
        ledger.recordPayload(buffer, 2 << 20, 0);

        TerrainGpuResidencyLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(3L << 20, snapshot.projectedArenaBytes());
        assertEquals(1, snapshot.arenaCompactionCandidateRegions());
        assertEquals(5L << 20, snapshot.potentialArenaReclaimBytes());
    }

    @Test
    void indexPayloadReplacementPreservesVertexPayload() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object buffer = new Object();

        ledger.associate(buffer, 0, 0, 0, 3);
        ledger.recordCapacity(buffer, 1024, 512);
        ledger.recordPayload(buffer, 900, 200);
        ledger.recordIndexPayload(buffer, 300);

        TerrainGpuResidencyLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(1536L, snapshot.residentBytes());
        assertEquals(1200L, snapshot.payloadBytes());
        assertEquals(336L, snapshot.headroomBytes());
    }

    @Test
    void clearResetsWorldScope() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object buffer = new Object();

        ledger.associate(buffer, -1, -1, -1, 3);
        ledger.recordCapacity(buffer, 1024, 512);
        ledger.recordPayload(buffer, 700, 200);
        ledger.clear();

        TerrainGpuResidencyLedger.Snapshot cleared = ledger.snapshot();
        assertEquals(0L, cleared.residentBytes());
        assertEquals(0L, cleared.payloadBytes());
        assertEquals(0L, cleared.headroomBytes());
        assertEquals(0L, cleared.peakResidentBytes());
        assertEquals(0, cleared.residentBuffers());
        assertEquals(0, cleared.residentRegions());
        assertEquals(0L, cleared.regionRelocations());
        assertEquals(0L, cleared.projectedArenaBytes());
        assertEquals(0L, cleared.projectedArenaSlackBytes());
        assertEquals(0, cleared.arenaCompactionCandidateRegions());
        assertEquals(0L, cleared.potentialArenaReclaimBytes());
    }
}
