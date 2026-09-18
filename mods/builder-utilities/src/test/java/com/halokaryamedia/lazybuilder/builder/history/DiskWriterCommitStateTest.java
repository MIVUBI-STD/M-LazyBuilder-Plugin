package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskWriterCommitStateTest {
    @TempDir Path tempDir;

    @Test
    void successfulCommitPublishesAndLeavesNoStagingFile() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet stored;
        try (ChangeSetWriter writer = storage.begin("commit-state")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            stored = writer.commit();
        }

        assertTrue(storage.listIncomplete().isEmpty());
        assertEquals(1, storage.listCommitted().size());
        stored.close();
        assertTrue(storage.listCommitted().isEmpty());
    }

    @Test
    void abortDeletesActiveStagingAndReleasesOwnership() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        ChangeSetWriter writer = storage.begin("abort-state");
        writer.append(new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, 64, 0)},
                new int[]{0}, new int[]{1}));

        assertEquals(1, storage.listIncomplete().size());
        assertTrue(storage.listRecoverableIncomplete().isEmpty());

        writer.abort();
        assertTrue(storage.listIncomplete().isEmpty());
        assertTrue(storage.listRecoverableIncomplete().isEmpty());
    }
}
