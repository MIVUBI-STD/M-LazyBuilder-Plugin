package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeSetStorageTest {
    private static ChunkChangeSet sampleChunk() {
        return new ChunkChangeSet(
                -2,
                3,
                List.of("minecraft:stone", "minecraft:air", "minecraft:dirt"),
                new long[]{
                        LocalBlockPosition.pack(1, -64, 2),
                        LocalBlockPosition.pack(15, 319, 0)
                },
                new int[]{0, 2},
                new int[]{1, 0}
        );
    }

    @Test
    void memoryStorageReplaysRedoAndUndoDeterministically() throws IOException {
        assertRoundTrip(new MemoryChangeSetStorage(), HistoryStorageTier.MEMORY);
    }

    @Test
    void compressedStorageUsesSameReplayContract() throws IOException {
        assertRoundTrip(new CompressedMemoryChangeSetStorage(), HistoryStorageTier.COMPRESSED_MEMORY);
    }

    @Test
    void codecRejectsStreamWithoutCommitFooter() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(output, "op-truncated");
        writer.append(sampleChunk());
        writer.abort();

        IOException error = assertThrows(IOException.class,
                () -> ChangeSetCodec.inspect(new ByteArrayInputStream(output.toByteArray())));
        assertTrue(error.getMessage().contains("Incomplete History v2"));
    }

    @Test
    void abortedWriterCannotProduceHistory() throws IOException {
        ChangeSetWriter writer = new MemoryChangeSetStorage().begin("op-abort");
        writer.append(sampleChunk());
        writer.abort();
        assertThrows(IllegalStateException.class, writer::commit);
    }

    private static void assertRoundTrip(ChangeSetStorage storage, HistoryStorageTier expectedTier) throws IOException {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = storage.begin("op-1")) {
            writer.append(sampleChunk());
            stored = writer.commit();
        }
        assertEquals("op-1", stored.operationId());
        assertEquals(expectedTier, stored.storageTier());
        assertEquals(2, stored.changeCount());

        List<String> redo = new ArrayList<>();
        stored.replay(ReplayDirection.REDO, (cx, cz, x, y, z, state) ->
                redo.add(cx + ":" + cz + ":" + x + ":" + y + ":" + z + "=" + state));
        assertEquals(List.of(
                "-2:3:1:-64:2=minecraft:air",
                "-2:3:15:319:0=minecraft:stone"
        ), redo);

        List<String> undo = new ArrayList<>();
        stored.replay(ReplayDirection.UNDO, (cx, cz, x, y, z, state) ->
                undo.add(cx + ":" + cz + ":" + x + ":" + y + ":" + z + "=" + state));
        assertEquals(List.of(
                "-2:3:15:319:0=minecraft:dirt",
                "-2:3:1:-64:2=minecraft:stone"
        ), undo);
    }
}
