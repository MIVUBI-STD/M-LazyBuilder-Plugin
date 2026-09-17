package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.*;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreparedMutationExecutorTest {
    @Test
    void executesCommittedPlanAcrossMultipleChunks() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("prepared")) {
            writer.append(chunk(0, 0, 64));
            writer.append(chunk(1, 0, 64));
            try (StoredChangeSet stored = writer.commit()) {
                InMemoryWorld world = new InMemoryWorld();
                world.put(0, 64, 0, "minecraft:stone");
                world.put(16, 64, 0, "minecraft:stone");

                PreparedMutationExecution result = PreparedMutationExecutor.execute(
                        stored, world, new CancellationSource().token());

                assertEquals(MutationExecutionState.COMPLETED, result.state());
                assertEquals(2, result.appliedChanges());
                assertEquals(2, result.completedChunks());
                assertEquals("minecraft:air", world.readBlockState(16, 64, 0));
            }
        }
    }

    @Test
    void conflictStopsFurtherChunkCallbacksButStillValidatesStoredStream() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("conflict")) {
            writer.append(chunk(0, 0, 64));
            writer.append(chunk(1, 0, 64));
            try (StoredChangeSet stored = writer.commit()) {
                InMemoryWorld world = new InMemoryWorld();
                world.put(0, 64, 0, "minecraft:dirt");
                world.put(16, 64, 0, "minecraft:stone");

                PreparedMutationExecution result = PreparedMutationExecutor.execute(
                        stored, world, new CancellationSource().token());

                assertEquals(MutationExecutionState.CONFLICT, result.state());
                assertEquals(0, result.appliedChanges());
                assertEquals("minecraft:stone", world.readBlockState(16, 64, 0));
            }
        }
    }

    @Test
    void refusesOpaqueExtensionFramesUntilTheirMutationAdapterExists() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("extension")) {
            writer.append(chunk(0, 0, 64));
            writer.appendExtension(new HistoryExtensionFrame(
                    "lazybuilder:block_entity", 0, 0, 0L, new byte[]{1}, new byte[]{2}));
            try (StoredChangeSet stored = writer.commit()) {
                assertThrows(IllegalArgumentException.class, () -> PreparedMutationExecutor.execute(
                        stored, new InMemoryWorld(), new CancellationSource().token()));
            }
        }
    }

    private static ChunkChangeSet chunk(int chunkX, int chunkZ, int y) {
        return new ChunkChangeSet(
                chunkX,
                chunkZ,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, y, 0)},
                new int[]{0},
                new int[]{1}
        );
    }

    private static final class InMemoryWorld implements WorldBlockMutationTarget {
        private final Map<String, String> states = new HashMap<>();

        void put(int x, int y, int z, String state) {
            states.put(key(x, y, z), state);
        }

        @Override
        public String readBlockState(int worldX, int y, int worldZ) {
            return states.get(key(worldX, y, worldZ));
        }

        @Override
        public void writeBlockState(int worldX, int y, int worldZ, String blockState) {
            states.put(key(worldX, y, worldZ), blockState);
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }
}
