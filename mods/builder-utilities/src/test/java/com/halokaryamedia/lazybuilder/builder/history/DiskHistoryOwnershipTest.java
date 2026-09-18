package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskHistoryOwnershipTest {
    @TempDir Path tempDir;

    @Test
    void currentProcessOwnedHistoryIsNotRediscoveredButRestartCanRecoverIt() throws Exception {
        DiskChangeSetStorage current = new DiskChangeSetStorage(tempDir);
        StoredChangeSet owned;
        try (ChangeSetWriter writer = current.begin("owned")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            owned = writer.commit();
        }

        assertEquals(1, current.listCommitted().size());
        assertTrue(current.recoverCommitted().isEmpty());

        DiskChangeSetStorage restarted = new DiskChangeSetStorage(tempDir);
        List<StoredChangeSet> recovered = restarted.recoverCommitted();
        assertEquals(1, recovered.size());

        recovered.get(0).close();
        owned.close();
        assertTrue(restarted.listCommitted().isEmpty());
    }

    @Test
    void recoveredFileIsClaimedOnlyOncePerRuntime() throws Exception {
        DiskChangeSetStorage writerStorage = new DiskChangeSetStorage(tempDir);
        try (ChangeSetWriter writer = writerStorage.begin("orphan")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(1, 64, 0)},
                    new int[]{0}, new int[]{1}));
            writer.commit();
        }

        DiskChangeSetStorage restarted = new DiskChangeSetStorage(tempDir);
        List<StoredChangeSet> first = restarted.recoverCommitted();
        assertEquals(1, first.size());
        assertTrue(restarted.recoverCommitted().isEmpty());

        first.get(0).close();
    }
}
