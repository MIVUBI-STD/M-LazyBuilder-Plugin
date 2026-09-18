package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.ReplayDirection;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionAwarePreparedMutationExecutorTest {
    @Test
    void redoAndUndoMixedHistoryUseDependencySafeOrdering() throws Exception {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("mixed")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 1, new byte[]{1}, new byte[]{2}));
            stored = writer.commit();
        }

        Target target = new Target();
        target.block = "minecraft:stone";
        target.ext = new byte[]{1};

        assertEquals(MutationExecutionState.COMPLETED,
                ExtensionAwarePreparedMutationExecutor.apply(
                        stored, ReplayDirection.REDO, target, target,
                        new CancellationSource().token()).state());
        assertEquals(List.of("block:air", "ext:2"), target.events);

        target.events.clear();
        assertEquals(MutationExecutionState.COMPLETED,
                ExtensionAwarePreparedMutationExecutor.apply(
                        stored, ReplayDirection.UNDO, target, target,
                        new CancellationSource().token()).state());
        assertEquals(List.of("ext:1", "block:stone"), target.events);
        stored.close();
    }

    private static final class Target implements WorldBlockMutationTarget, HistoryExtensionMutationTarget {
        String block;
        byte[] ext;
        final List<String> events = new java.util.ArrayList<>();

        @Override public String readBlockState(int x, int y, int z) { return block; }
        @Override public void writeBlockState(int x, int y, int z, String state) {
            block = state;
            events.add("block:" + state.substring(state.indexOf(':') + 1));
        }
        @Override public byte[] read(String typeId, int chunkX, int chunkZ, long localKey) {
            return ext.clone();
        }
        @Override public void write(String typeId, int chunkX, int chunkZ, long localKey, byte[] payload) {
            ext = payload.clone();
            events.add("ext:" + payload[0]);
        }
    }
}
