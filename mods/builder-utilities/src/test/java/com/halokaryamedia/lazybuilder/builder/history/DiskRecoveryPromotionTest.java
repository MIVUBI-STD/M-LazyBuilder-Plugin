package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskRecoveryPromotionTest {
    @TempDir Path tempDir;

    @Test
    void promotesValidFsyncedStyleIncompleteJournal() throws Exception {
        Path staging = tempDir.resolve("recovery.lbh2.incomplete");
        try (OutputStream output = Files.newOutputStream(staging)) {
            ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(output, "promote-me");
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}
            ));
            writer.commit();
        }

        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        List<Path> promoted = storage.promoteRecoverableIncomplete();

        assertEquals(1, promoted.size());
        assertTrue(Files.exists(tempDir.resolve("recovery.lbh2")));
        assertTrue(storage.listIncomplete().isEmpty());
        assertEquals(1, storage.recoverCommitted().size());
        for (StoredChangeSet set : storage.recoverCommitted()) set.close();
    }

    @Test
    void leavesTruncatedIncompleteJournalQuarantined() throws Exception {
        Path staging = tempDir.resolve("broken.lbh2.incomplete");
        Files.write(staging, new byte[]{1, 2, 3, 4});

        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        assertTrue(storage.promoteRecoverableIncomplete().isEmpty());
        assertEquals(List.of(staging), storage.listIncomplete());
    }
}
