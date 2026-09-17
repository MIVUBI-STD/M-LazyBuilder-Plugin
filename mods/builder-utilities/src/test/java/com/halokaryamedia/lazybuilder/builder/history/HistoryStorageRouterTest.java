package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HistoryStorageRouterTest {
    @TempDir
    Path tempDir;

    @Test
    void routesSizingPolicyToConfiguredBackends() throws IOException {
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(100, 1_000),
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                new DiskChangeSetStorage(tempDir)
        );

        assertTrue(router.begin(HistoryRequirement.NONE, 10_000, "none").isEmpty());

        try (ChangeSetWriter memory = router.begin(HistoryRequirement.REQUIRED, 100, "memory").orElseThrow()) {
            assertEquals(HistoryStorageTier.MEMORY, memory.commit().storageTier());
        }
        try (ChangeSetWriter compressed = router.begin(HistoryRequirement.REQUIRED, 101, "compressed").orElseThrow()) {
            assertEquals(HistoryStorageTier.COMPRESSED_MEMORY, compressed.commit().storageTier());
        }
        try (ChangeSetWriter disk = router.begin(HistoryRequirement.REQUIRED, 1_001, "disk").orElseThrow()) {
            StoredChangeSet stored = disk.commit();
            assertEquals(HistoryStorageTier.DISK, stored.storageTier());
            stored.close();
        }
    }
}
