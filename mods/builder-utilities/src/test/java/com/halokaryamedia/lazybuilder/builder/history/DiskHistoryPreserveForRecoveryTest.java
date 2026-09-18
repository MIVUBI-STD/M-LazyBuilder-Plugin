package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskHistoryPreserveForRecoveryTest {
    @TempDir Path tempDir;

    @Test
    void preservedOwnedPlanBecomesRecoverableWithoutDeletion() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet owned;
        try (ChangeSetWriter writer = storage.begin("failed-operation")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            owned = writer.commit();
        }

        assertTrue(storage.recoverCommitted().isEmpty());
        assertTrue(owned.preserveForRecovery());

        List<StoredChangeSet> recovered = storage.recoverCommitted();
        assertEquals(1, recovered.size());
        assertEquals("failed-operation", recovered.get(0).operationId());
        recovered.get(0).close();
    }
}
