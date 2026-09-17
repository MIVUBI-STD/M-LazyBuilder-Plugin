package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainGpuResidencyLedgerTest {
    @Test
    void aggregatesResidentCapacityByRegionAndKeepsPeak() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();

        ledger.associate(a, 0, 0, 0, 0);
        ledger.associate(b, 7, 3, 7, 1);
        ledger.associate(c, 8, 0, 0, 2);
        ledger.recordCapacity(a, 100, 20);
        ledger.recordCapacity(b, 200, 0);
        ledger.recordCapacity(c, 300, 30);

        TerrainGpuResidencyLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(650L, snapshot.residentBytes());
        assertEquals(650L, snapshot.peakResidentBytes());
        assertEquals(3, snapshot.residentBuffers());
        assertEquals(2, snapshot.residentRegions());
        assertEquals(330L, snapshot.largestRegionBytes());
        assertEquals(0L, snapshot.regionRelocations());

        ledger.release(c);
        TerrainGpuResidencyLedger.Snapshot afterRelease = ledger.snapshot();
        assertEquals(320L, afterRelease.residentBytes());
        assertEquals(650L, afterRelease.peakResidentBytes());
        assertEquals(2, afterRelease.residentBuffers());
        assertEquals(1, afterRelease.residentRegions());
    }

    @Test
    void movingResidentBufferAcrossAccountingRegionCountsRelocation() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object buffer = new Object();

        ledger.associate(buffer, 0, 0, 0, 3);
        ledger.recordCapacity(buffer, 512, 128);
        ledger.associate(buffer, 7, 3, 7, 3);
        assertEquals(0L, ledger.snapshot().regionRelocations());

        ledger.associate(buffer, 8, 0, 0, 3);
        TerrainGpuResidencyLedger.Snapshot moved = ledger.snapshot();
        assertEquals(1L, moved.regionRelocations());
        assertEquals(640L, moved.residentBytes());
    }

    @Test
    void capacityReplacementAndClearResetWorldScope() {
        TerrainGpuResidencyLedger<Object> ledger = new TerrainGpuResidencyLedger<>();
        Object buffer = new Object();

        ledger.associate(buffer, -1, -1, -1, 3);
        ledger.recordCapacity(buffer, 1024, 256);
        ledger.recordCapacity(buffer, 1024, 512);
        assertEquals(1536L, ledger.snapshot().residentBytes());

        ledger.clear();
        TerrainGpuResidencyLedger.Snapshot cleared = ledger.snapshot();
        assertEquals(0L, cleared.residentBytes());
        assertEquals(0L, cleared.peakResidentBytes());
        assertEquals(0, cleared.residentBuffers());
        assertEquals(0, cleared.residentRegions());
        assertEquals(0L, cleared.regionRelocations());
    }
}
