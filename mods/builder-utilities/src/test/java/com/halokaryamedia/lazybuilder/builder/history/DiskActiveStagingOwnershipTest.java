package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiskActiveStagingOwnershipTest {
    @TempDir Path tempDir;

    @Test
    void activeWriterStagingIsNeverOfferedToRecoveryOrDiscard() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        ChangeSetWriter writer = storage.begin("active");
        try {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    java.util.List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));

            assertEquals(1, storage.listIncomplete().size());
            assertTrue(storage.listRecoverableIncomplete().isEmpty());

            HistoryRecoveryManager manager = new HistoryRecoveryManager(storage);
            assertTrue(manager.incompleteFiles().isEmpty());
            assertEquals(0, manager.discardIncompleteFiles());
            assertEquals(1, storage.listIncomplete().size());
        } finally {
            writer.abort();
            writer.close();
        }
        assertTrue(storage.listIncomplete().isEmpty());
    }
}
