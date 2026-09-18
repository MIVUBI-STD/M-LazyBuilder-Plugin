package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskChangeSetStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void publishesOnlyCommittedHistoryAndDeletesOnClose() throws IOException {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet stored;
        try (ChangeSetWriter writer = storage.begin("disk-op")) {
            writer.append(sampleChunk());
            stored = writer.commit();
        }

        assertEquals(HistoryStorageTier.DISK, stored.storageTier());
        assertEquals(1, stored.changeCount());
        assertTrue(storage.listIncomplete().isEmpty());
        try (var files = Files.list(tempDir)) {
            assertEquals(1, files.count());
        }

        List<String> states = new ArrayList<>();
        stored.replay(ReplayDirection.REDO, (cx, cz, x, y, z, state) -> states.add(state));
        assertEquals(List.of("minecraft:air"), states);

        stored.close();
        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void abortRemovesStagingFile() throws IOException {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        ChangeSetWriter writer = storage.begin("aborted-op");
        writer.append(sampleChunk());
        assertEquals(1, storage.listIncomplete().size());
        writer.abort();
        assertTrue(storage.listIncomplete().isEmpty());
    }

    @Test
    void detectsIncompleteFilesLeftByInterruptedProcess() throws IOException {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        Files.writeString(tempDir.resolve("orphan.lbh2.incomplete"), "partial");
        assertEquals(List.of(tempDir.resolve("orphan.lbh2.incomplete").toAbsolutePath().normalize()),
                storage.listIncomplete());
    }

    private static ChunkChangeSet sampleChunk() {
        return new ChunkChangeSet(0, 0, List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, 64, 0)}, new int[]{0}, new int[]{1});
    }
}
