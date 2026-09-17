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
    void extensionFramesReplayDirectionSpecificPayload() throws IOException {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("extension-op")) {
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 12L, new byte[]{1}, new byte[]{2}));
            stored = writer.commit();
        }
        assertEquals(1, stored.extensionCount());

        List<Byte> undo = new ArrayList<>();
        stored.replayAll(ReplayDirection.UNDO, new HistoryReplayConsumer() {
            @Override
            public void acceptBlock(int chunkX, int chunkZ, int localX, int y, int localZ, String state) {
            }

            @Override
            public void acceptExtension(HistoryExtensionFrame frame, byte[] payload) {
                undo.add(payload[0]);
            }
        });
        assertEquals(List.of((byte) 1), undo);
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
    void codecRejectsUnknownFrameMarker() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(output, "corrupt");
        writer.append(sampleChunk());
        writer.commit();
        byte[] bytes = output.toByteArray();
        int firstFrameOffset = 4 + 4 + 2 + "corrupt".length();
        bytes[firstFrameOffset] = 99;
        IOException error = assertThrows(IOException.class,
                () -> ChangeSetCodec.inspect(new ByteArrayInputStream(bytes)));
        assertTrue(error.getMessage().contains("Unknown History v2 frame marker"));
    }

    @Test
    void abortedWriterCannotProduceHistory() throws IOException {
        ChangeSetWriter writer = new MemoryChangeSetStorage().begin("op-abort");
        writer.append(sampleChunk());
        writer.abort();
        assertThrows(IllegalStateException.class, writer::commit);
    }

    @Test
    void largeSyntheticStreamPreservesExactCountAndReplay() throws IOException {
        int chunks = 100;
        int changesPerChunk = 1_024;
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new CompressedMemoryChangeSetStorage().begin("large")) {
            for (int chunkX = 0; chunkX < chunks; chunkX++) {
                long[] positions = new long[changesPerChunk];
                int[] before = new int[changesPerChunk];
                int[] after = new int[changesPerChunk];
                for (int i = 0; i < changesPerChunk; i++) {
                    positions[i] = LocalBlockPosition.pack(i & 15, i / 256, (i >>> 4) & 15);
                    after[i] = 1;
                }
                writer.append(new ChunkChangeSet(chunkX, 0,
                        List.of("minecraft:stone", "minecraft:air"), positions, before, after));
            }
            stored = writer.commit();
        }

        assertEquals((long) chunks * changesPerChunk, stored.changeCount());
        long[] replayed = {0};
        stored.replay(ReplayDirection.REDO, (cx, cz, x, y, z, state) -> replayed[0]++);
        assertEquals(stored.changeCount(), replayed[0]);
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
        assertEquals(0, stored.extensionCount());

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
