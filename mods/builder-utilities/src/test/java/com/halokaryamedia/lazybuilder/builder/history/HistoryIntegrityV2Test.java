package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryIntegrityV2Test {
    @Test
    void rejectsDuplicatePositionsInsideChunkFrame() {
        long packed = LocalBlockPosition.pack(1, 64, 1);
        assertThrows(IllegalArgumentException.class, () -> new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{packed, packed},
                new int[]{0, 0},
                new int[]{1, 1}
        ));
    }

    @Test
    void writerRejectsSecondFrameForSameChunk() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(bytes, "dup");
        writer.append(chunk(0, 0, 0));
        assertThrows(IOException.class, () -> writer.append(chunk(0, 0, 1)));
    }

    @Test
    void versionTwoChecksumRejectsParseableCorruption() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(bytes, "checksum");
        writer.append(chunk(0, 0, 0));
        writer.commit();

        byte[] corrupt = bytes.toByteArray();
        // Flip one byte in the first block state's UTF payload while preserving stream structure.
        for (int i = 12; i < corrupt.length - 24; i++) {
            if (corrupt[i] == 's') {
                corrupt[i] = 't';
                break;
            }
        }

        IOException error = assertThrows(IOException.class,
                () -> ChangeSetCodec.inspect(new ByteArrayInputStream(corrupt)));
        assertTrue(error.getMessage().contains("checksum"));
    }

    @Test
    void replayRunsBlocksBeforeExtensionsForBothDirections() throws Exception {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("phases")) {
            writer.append(chunk(0, 0, 0));
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 1L, new byte[]{1}, new byte[]{2}));
            stored = writer.commit();
        }

        for (ReplayDirection direction : ReplayDirection.values()) {
            List<String> order = new ArrayList<>();
            stored.replayAll(direction, new HistoryReplayConsumer() {
                @Override
                public void acceptBlock(int chunkX, int chunkZ, int localX, int y, int localZ, String state) {
                    order.add("block");
                }

                @Override
                public void acceptExtension(HistoryExtensionFrame frame, byte[] payload) {
                    order.add("extension");
                }
            });
            assertEquals(
                    direction == ReplayDirection.REDO
                            ? List.of("block", "extension")
                            : List.of("extension", "block"),
                    order);
        }
    }

    private static ChunkChangeSet chunk(int chunkX, int chunkZ, int localX) {
        return new ChunkChangeSet(
                chunkX, chunkZ,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(localX, 64, 0)},
                new int[]{0},
                new int[]{1}
        );
    }
}
