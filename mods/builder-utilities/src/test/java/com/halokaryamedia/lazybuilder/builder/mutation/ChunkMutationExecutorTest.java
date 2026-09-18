package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkMutationExecutorTest {
    private static ChunkChangeSet changes() {
        return new ChunkChangeSet(
                0,
                0,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{LocalBlockPosition.pack(0, 64, 0), LocalBlockPosition.pack(1, 64, 0)},
                new int[]{0, 0},
                new int[]{1, 1}
        );
    }

    @Test
    void appliesAndVerifiesPlannedChanges() {
        InMemoryWorld world = new InMemoryWorld();
        world.put(0, 64, 0, "minecraft:stone");
        world.put(1, 64, 0, "minecraft:stone");

        ChunkMutationExecution result = ChunkMutationExecutor.apply(
                changes(), world, new CancellationSource().token());

        assertEquals(MutationExecutionState.COMPLETED, result.state());
        assertEquals(2, result.appliedChanges());
        assertEquals("minecraft:air", world.readBlockState(0, 64, 0));
        assertEquals("minecraft:air", world.readBlockState(1, 64, 0));
    }

    @Test
    void treatsAlreadyAppliedStateAsIdempotentResume() {
        InMemoryWorld world = new InMemoryWorld();
        world.put(0, 64, 0, "minecraft:air");
        world.put(1, 64, 0, "minecraft:stone");

        ChunkMutationExecution result = ChunkMutationExecutor.apply(
                changes(), world, new CancellationSource().token());

        assertEquals(MutationExecutionState.COMPLETED, result.state());
        assertEquals(2, result.appliedChanges());
    }

    @Test
    void stopsBeforeOverwritingUnexpectedWorldState() {
        InMemoryWorld world = new InMemoryWorld();
        world.put(0, 64, 0, "minecraft:dirt");
        world.put(1, 64, 0, "minecraft:stone");

        ChunkMutationExecution result = ChunkMutationExecutor.apply(
                changes(), world, new CancellationSource().token());

        assertEquals(MutationExecutionState.CONFLICT, result.state());
        assertEquals(0, result.appliedChanges());
        assertEquals(0, result.conflictX());
        assertEquals("minecraft:dirt", world.readBlockState(0, 64, 0));
    }

    @Test
    void cancellationCanLeaveExplicitlyClassifiablePartialState() {
        InMemoryWorld world = new InMemoryWorld();
        world.put(0, 64, 0, "minecraft:stone");
        world.put(1, 64, 0, "minecraft:stone");
        CancellationSource source = new CancellationSource();
        world.afterWrite = source::requestCancellation;

        ChunkMutationExecution result = ChunkMutationExecutor.apply(changes(), world, source.token());
        assertEquals(MutationExecutionState.CANCELLED, result.state());
        assertEquals(1, result.appliedChanges());
        assertTrue(result.isPartial());

        ChunkReconciliationReport report = ChunkMutationReconciler.reconcile(changes(), world);
        assertEquals(ReconciliationState.PARTIALLY_APPLIED, report.state());
    }

    private static final class InMemoryWorld implements WorldBlockMutationTarget {
        private final Map<String, String> states = new HashMap<>();
        private Runnable afterWrite = () -> {};

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
            afterWrite.run();
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }
}
