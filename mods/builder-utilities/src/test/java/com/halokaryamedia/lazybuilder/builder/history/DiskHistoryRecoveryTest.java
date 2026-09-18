package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskHistoryRecoveryTest {
    @TempDir
    Path tempDir;

    @Test
    void committedHistoryCanBeRediscoveredAfterRestart() throws Exception {
        DiskChangeSetStorage first = new DiskChangeSetStorage(tempDir);
        StoredChangeSet original;
        try (ChangeSetWriter writer = first.begin("recover-me")) {
            writer.append(sample());
            original = writer.commit();
        }

        assertEquals(1, first.listCommitted().size());

        // Simulate process loss by intentionally not closing the original owner.
        DiskChangeSetStorage restarted = new DiskChangeSetStorage(tempDir);
        List<StoredChangeSet> recovered = restarted.recoverCommitted();

        assertEquals(1, recovered.size());
        StoredChangeSet set = recovered.get(0);
        assertEquals("recover-me", set.operationId());
        assertEquals(1, set.changeCount());

        set.close();
        assertTrue(restarted.listCommitted().isEmpty());

        // Original refers to the same path; close is idempotent at filesystem level.
        original.close();
    }

    private static ChunkChangeSet sample() {
        return new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, 64, 0)},
                new int[]{0},
                new int[]{1}
        );
    }

    @Test
    void corruptCommittedFileIsNotRecoveredAsValidHistory() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet set;
        try (ChangeSetWriter writer = storage.begin("corrupt")) {
            writer.append(sample());
            set = writer.commit();
        }

        Path path = storage.listCommitted().get(0);
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        bytes[Math.max(12, bytes.length / 2)] ^= 1;
        java.nio.file.Files.write(path, bytes);

        assertThrows(IOException.class, storage::recoverCommitted);

        // Cleanup damaged file manually because it is deliberately invalid.
        java.nio.file.Files.deleteIfExists(path);
    }
}
