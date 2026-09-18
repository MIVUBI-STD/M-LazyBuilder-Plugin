package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoryExtensionFrameTest {
    @Test
    void payloadsAreDefensivelyCopiedAndDirectionAware() {
        byte[] before = {1, 2};
        byte[] after = {3, 4};
        HistoryExtensionFrame frame = new HistoryExtensionFrame(
                "lazybuilder:block_entity", 1, -2, 7L, before, after);
        before[0] = 9;
        after[0] = 9;

        assertArrayEquals(new byte[]{1, 2}, frame.payload(ReplayDirection.UNDO));
        assertArrayEquals(new byte[]{3, 4}, frame.payload(ReplayDirection.REDO));
    }
    @Test
    void undoReplaysExtensionsInGlobalReverseOrderBeforeBlocks() throws IOException {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("ordered-undo")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    java.util.List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0},
                    new int[]{1}
            ));
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 1L, new byte[]{11}, new byte[]{21}));
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:entity", 0, 0, 2L, new byte[]{12}, new byte[]{22}));
            stored = writer.commit();
        }

        java.util.List<String> events = new java.util.ArrayList<>();
        stored.replayAll(ReplayDirection.UNDO, new HistoryReplayConsumer() {
            @Override
            public void acceptBlock(int chunkX, int chunkZ, int localX, int y, int localZ, String state) {
                events.add("block:" + state);
            }

            @Override
            public void acceptExtension(HistoryExtensionFrame frame, byte[] payload) {
                events.add(frame.typeId() + ":" + payload[0]);
            }
        });

        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(
                "lazybuilder:entity:12",
                "lazybuilder:block_entity:11",
                "block:minecraft:stone"
        ), events);
    }
}
