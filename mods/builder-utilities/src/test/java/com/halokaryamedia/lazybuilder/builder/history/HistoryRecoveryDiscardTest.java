package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryRecoveryDiscardTest {
    @TempDir Path tempDir;

    @Test
    void discardIncompletePromotesValidJournalAndDeletesOnlyBrokenOne() throws Exception {
        Path valid = tempDir.resolve("valid.lbh2.incomplete");
        try (OutputStream output = Files.newOutputStream(valid)) {
            ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(output, "valid");
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            writer.commit();
        }
        Path broken = tempDir.resolve("broken.lbh2.incomplete");
        Files.write(broken, new byte[]{1, 2, 3});

        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        HistoryRecoveryManager manager = new HistoryRecoveryManager(storage);
        assertEquals(1, manager.discardIncompleteFiles());

        assertTrue(storage.listIncomplete().isEmpty());
        assertEquals(1, storage.listCommitted().size());
        for (StoredChangeSet set : storage.recoverCommitted()) set.close();
    }
}
