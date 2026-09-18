package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.ReplayDirection;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryExtensionMutationExecutorTest {
    @Test
    void redoAndUndoUsePreconditionsAndReverseOrder() throws Exception {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("extensions")) {
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 1, new byte[]{1}, new byte[]{11}));
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:entity", 0, 0, 2, new byte[]{2}, new byte[]{22}));
            stored = writer.commit();
        }

        MemoryTarget target = new MemoryTarget();
        target.put("lazybuilder:block_entity", 1, new byte[]{1});
        target.put("lazybuilder:entity", 2, new byte[]{2});

        ExtensionMutationExecution redo = HistoryExtensionMutationExecutor.apply(
                stored, ReplayDirection.REDO, target, new CancellationSource().token());
        assertEquals(MutationExecutionState.COMPLETED, redo.state());
        assertArrayEquals(new byte[]{11}, target.get("lazybuilder:block_entity", 1));
        assertArrayEquals(new byte[]{22}, target.get("lazybuilder:entity", 2));

        ExtensionMutationExecution undo = HistoryExtensionMutationExecutor.apply(
                stored, ReplayDirection.UNDO, target, new CancellationSource().token());
        assertEquals(MutationExecutionState.COMPLETED, undo.state());
        assertEquals(java.util.List.of("lazybuilder:entity:2", "lazybuilder:block_entity:1"),
                target.writeOrder.subList(2, 4));
        assertArrayEquals(new byte[]{1}, target.get("lazybuilder:block_entity", 1));
        stored.close();
    }

    private static final class MemoryTarget implements HistoryExtensionMutationTarget {
        private final Map<String, byte[]> values = new HashMap<>();
        private final java.util.List<String> writeOrder = new java.util.ArrayList<>();

        void put(String type, long key, byte[] value) { values.put(type + ":" + key, value.clone()); }
        byte[] get(String type, long key) { return values.get(type + ":" + key).clone(); }

        @Override
        public byte[] read(String typeId, int chunkX, int chunkZ, long localKey) {
            return get(typeId, localKey);
        }

        @Override
        public void write(String typeId, int chunkX, int chunkZ, long localKey, byte[] payload) {
            writeOrder.add(typeId + ":" + localKey);
            put(typeId, localKey, payload);
        }
    }
}
