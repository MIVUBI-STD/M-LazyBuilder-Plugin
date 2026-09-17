package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppliedMutationCompactorTest {
    @Test
    void compactsOnlyBlocksWhoseAfterStateIsPresent() throws Exception {
        MemoryChangeSetStorage sourceStorage = new MemoryChangeSetStorage();
        StoredChangeSet prepared;
        try (ChangeSetWriter writer = sourceStorage.begin("prepared")) {
            writer.append(twoChangeChunk());
            prepared = writer.commit();
        }

        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048),
                new MemoryChangeSetStorage()
        );
        WorldBlockStateSource world = (x, y, z) -> x == 0 ? "minecraft:dirt" : "minecraft:stone";

        try (prepared) {
            AppliedMutationCompaction result = AppliedMutationCompactor.compact(
                    prepared, world, router, 128, "partial-cancel");
            assertEquals(AppliedMutationCompactionState.COMPACTED, result.state());
            assertEquals(1, result.appliedChanges());
            StoredChangeSet compacted = result.compactedChangeSet();
            List<String> undoStates = new ArrayList<>();
            try (compacted) {
                compacted.replay(ReplayDirection.UNDO,
                        (chunkX, chunkZ, localX, y, localZ, state) -> undoStates.add(state));
            }
            assertEquals(List.of("minecraft:stone"), undoStates);
        }
    }

    @Test
    void returnsEmptyWhenNothingWasApplied() throws Exception {
        StoredChangeSet prepared = prepared();
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048), new MemoryChangeSetStorage());
        try (prepared) {
            AppliedMutationCompaction result = AppliedMutationCompactor.compact(
                    prepared, (x, y, z) -> "minecraft:stone", router, 128, "empty-cancel");
            assertEquals(AppliedMutationCompactionState.EMPTY, result.state());
            assertNull(result.compactedChangeSet());
        }
    }

    @Test
    void conflictFailsClosedWithoutPublishingSubset() throws Exception {
        StoredChangeSet prepared = prepared();
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048), new MemoryChangeSetStorage());
        try (prepared) {
            AppliedMutationCompaction result = AppliedMutationCompactor.compact(
                    prepared, (x, y, z) -> "minecraft:gold_block", router, 128, "conflict-cancel");
            assertEquals(AppliedMutationCompactionState.CONFLICT, result.state());
            assertEquals(0, result.conflictX());
            assertNull(result.compactedChangeSet());
        }
    }

    private static StoredChangeSet prepared() throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("prepared-single")) {
            writer.append(twoChangeChunk());
            return writer.commit();
        }
    }

    private static ChunkChangeSet twoChangeChunk() {
        return new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:dirt"),
                new long[]{
                        LocalBlockPosition.pack(0, 64, 0),
                        LocalBlockPosition.pack(1, 64, 0)
                },
                new int[]{0, 0},
                new int[]{1, 1}
        );
    }
}
