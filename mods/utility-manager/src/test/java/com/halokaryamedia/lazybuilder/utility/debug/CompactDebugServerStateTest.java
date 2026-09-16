package com.halokaryamedia.lazybuilder.utility.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactDebugServerStateTest {
    @AfterEach
    void clearState() {
        CompactDebugServerState.clear();
    }

    @Test
    void freshTelemetryIsNormalizedAndAvailable() {
        long now = 100L;
        CompactDebugServerState.updateAt("  TanaSamawa  ", 125.0D, -1L, 8_000L, now);

        CompactDebugServerState.Snapshot snapshot = CompactDebugServerState.snapshotAt(now + 6_000_000_000L);

        assertTrue(snapshot.telemetryAvailable());
        assertEquals("TanaSamawa", snapshot.worldName());
        assertEquals(100.0D, snapshot.cpuPercent());
        assertEquals(0L, snapshot.usedMemoryBytes());
        assertEquals(8_000L, snapshot.maxMemoryBytes());
    }

    @Test
    void expiredTelemetryFailsClosed() {
        long now = 100L;
        CompactDebugServerState.updateAt("World", 25.0D, 1_000L, 2_000L, now);

        CompactDebugServerState.Snapshot snapshot = CompactDebugServerState.snapshotAt(now + 6_000_000_001L);

        assertFalse(snapshot.telemetryAvailable());
        assertTrue(snapshot.worldName().isBlank());
    }

    @Test
    void blankWorldNameRemainsBlankForClientFallback() {
        CompactDebugServerState.updateAt("   ", 25.0D, 1_000L, 2_000L, 100L);

        assertTrue(CompactDebugServerState.snapshotAt(100L).worldName().isBlank());
    }
}
