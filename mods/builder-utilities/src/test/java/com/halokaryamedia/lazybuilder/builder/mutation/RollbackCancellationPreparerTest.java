package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RollbackCancellationPreparerTest {
    @TempDir Path tempDir;
    @Test
    void preparesReversePlanForOnlyAppliedSubset() throws Exception {
        StoredChangeSet original = original();
        HistoryStorageRouter router = router();
        WorldBlockStateSource world = (x, y, z) -> x == 0 ? "minecraft:dirt" : "minecraft:stone";

        try (original; PreparedRollback rollback = RollbackCancellationPreparer.prepare(
                original, world, router, 256, "cancel-test")) {
            assertEquals(RollbackPreparationState.READY, rollback.state());
            assertEquals(1, rollback.appliedChanges());
            List<String> states = new ArrayList<>();
            rollback.rollbackPlan().replay(ReplayDirection.REDO,
                    (chunkX, chunkZ, localX, y, localZ, state) -> states.add(state));
            assertEquals(List.of("minecraft:stone"), states);
        }
    }

    @Test
    void emptyAndConflictAreExplicit() throws Exception {
        StoredChangeSet emptyOriginal = original();
        try (emptyOriginal; PreparedRollback rollback = RollbackCancellationPreparer.prepare(
                emptyOriginal, (x, y, z) -> "minecraft:stone", router(), 256, "empty")) {
            assertEquals(RollbackPreparationState.EMPTY, rollback.state());
        }

        StoredChangeSet conflictOriginal = original();
        try (conflictOriginal; PreparedRollback rollback = RollbackCancellationPreparer.prepare(
                conflictOriginal, (x, y, z) -> "minecraft:gold_block", router(), 256, "conflict")) {
            assertEquals(RollbackPreparationState.CONFLICT, rollback.state());
            assertEquals(0, rollback.conflictX());
        }
    }

    private HistoryStorageRouter router() {
        return new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048),
                new MemoryChangeSetStorage(),
                new DiskChangeSetStorage(tempDir));
    }

    private static StoredChangeSet original() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("original")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:dirt"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0), LocalBlockPosition.pack(1, 64, 0)},
                    new int[]{0, 0},
                    new int[]{1, 1}
            ));
            return writer.commit();
        }
    }
}
