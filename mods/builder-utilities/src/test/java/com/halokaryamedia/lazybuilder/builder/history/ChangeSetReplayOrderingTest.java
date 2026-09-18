package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeSetReplayOrderingTest {
    @Test
    void directReplayUsesDependencySafeRedoAndUndoOrder() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ChangeSetCodec.StreamWriter writer = ChangeSetCodec.openWriter(bytes, "ordered");
        writer.appendExtension(new HistoryExtensionFrame(
                "lazybuilder:block_entity", 0, 0, 1, new byte[]{1}, new byte[]{2}));
        writer.append(new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, 64, 0)},
                new int[]{0}, new int[]{1}));
        writer.commit();

        List<String> redo = new ArrayList<>();
        ChangeSetCodec.replay(
                new ByteArrayInputStream(bytes.toByteArray()),
                ReplayDirection.REDO,
                consumer(redo));
        assertEquals(List.of("block:minecraft:air", "ext:2"), redo);

        List<String> undo = new ArrayList<>();
        ChangeSetCodec.replay(
                new ByteArrayInputStream(bytes.toByteArray()),
                ReplayDirection.UNDO,
                consumer(undo));
        assertEquals(List.of("ext:1", "block:minecraft:stone"), undo);
    }

    private static HistoryReplayConsumer consumer(List<String> events) {
        return new HistoryReplayConsumer() {
            @Override
            public void acceptBlock(
                    int chunkX, int chunkZ, int localX, int y, int localZ, String state
            ) {
                events.add("block:" + state);
            }

            @Override
            public void acceptExtension(HistoryExtensionFrame frame, byte[] payload) {
                events.add("ext:" + payload[0]);
            }
        };
    }
}
