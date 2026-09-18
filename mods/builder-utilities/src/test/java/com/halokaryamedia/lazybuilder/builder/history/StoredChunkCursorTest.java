package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoredChunkCursorTest {
    @Test
    void cursorResumesWithoutRescanningAndValidatesFooter() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        StoredChangeSet stored;
        try (ChangeSetWriter writer = storage.begin("cursor-test")) {
            writer.append(chunk(0, 0, 1));
            writer.append(chunk(1, 0, 2));
            writer.append(chunk(2, 0, 3));
            stored = writer.commit();
        }

        try (stored; StoredChunkCursor cursor = StoredChunkCursor.open(stored)) {
            assertEquals(0, cursor.nextChunk().chunkX());
            assertEquals(1, cursor.observedChanges());
            assertEquals(1, cursor.nextChunk().chunkX());
            assertEquals(3, cursor.observedChanges());
            assertEquals(2, cursor.nextChunk().chunkX());
            assertEquals(6, cursor.observedChanges());
            assertNull(cursor.nextChunk());
            assertTrue(cursor.exhausted());
            assertEquals(6, cursor.observedChanges());
        }
    }

    private static ChunkChangeSet chunk(int chunkX, int chunkZ, int changes) {
        long[] positions = new long[changes];
        int[] before = new int[changes];
        int[] after = new int[changes];
        for (int i = 0; i < changes; i++) {
            positions[i] = LocalBlockPosition.pack(i & 15, 64, 0);
            before[i] = 0;
            after[i] = 1;
        }
        return new ChunkChangeSet(
                chunkX, chunkZ,
                List.of("minecraft:stone", "minecraft:dirt"),
                positions, before, after
        );
    }
}
