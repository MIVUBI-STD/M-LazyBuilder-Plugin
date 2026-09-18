package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HistoryStorageRouterDurabilityTest {
    @TempDir Path tempDir;

    @Test
    void durableWriterAlwaysUsesDiskRegardlessOfEstimate() throws Exception {
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048),
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                new DiskChangeSetStorage(tempDir)
        );

        StoredChangeSet stored;
        try (ChangeSetWriter writer = router.beginDurable("production")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    java.util.List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            stored = writer.commit();
        }

        assertEquals(HistoryStorageTier.DISK, stored.storageTier());
        stored.close();
    }

    @Test
    void durableWriterFailsClosedWithoutDiskTier() {
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048),
                new MemoryChangeSetStorage()
        );
        assertThrows(IllegalStateException.class, () -> router.beginDurable("unsafe"));
    }
}
