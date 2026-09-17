package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.*;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BudgetedPreparedMutationDispatcherTest {
    @Test
    void yieldsAndResumesAcrossChunkBudgetWithoutRescanning() throws Exception {
        StoredChangeSet stored = prepared(3, 1);
        CancellationSource cancellation = new CancellationSource();
        AtomicInteger calls = new AtomicInteger();
        ChunkDispatchTarget target = chunk -> {
            calls.incrementAndGet();
            return ChunkDispatchOutcome.dispatched(chunk.size());
        };
        ExecutionBudget budget = new ExecutionBudget(Duration.ofSeconds(1), 1, 16, 1_000_000);

        try (stored; BudgetedPreparedMutationDispatcher dispatcher =
                     new BudgetedPreparedMutationDispatcher(stored, target, cancellation.token())) {
            assertEquals(BudgetedDispatchState.YIELDED, dispatcher.dispatchSlice(budget).state());
            assertEquals(BudgetedDispatchState.YIELDED, dispatcher.dispatchSlice(budget).state());
            assertEquals(BudgetedDispatchState.YIELDED, dispatcher.dispatchSlice(budget).state());
            BudgetedDispatchSlice done = dispatcher.dispatchSlice(budget);
            assertEquals(BudgetedDispatchState.EXHAUSTED, done.state());
            assertEquals(3, done.totalVisitedChunks());
            assertEquals(3, done.totalDispatchedBlocks());
            assertEquals(3, calls.get());
        }
    }

    @Test
    void oversizedChunkFailsClosedBeforeDispatch() throws Exception {
        StoredChangeSet stored = prepared(1, 4);
        AtomicInteger calls = new AtomicInteger();
        ExecutionBudget budget = new ExecutionBudget(Duration.ofSeconds(1), 4, 2, 1_000_000);
        try (stored; BudgetedPreparedMutationDispatcher dispatcher = new BudgetedPreparedMutationDispatcher(
                stored,
                chunk -> { calls.incrementAndGet(); return ChunkDispatchOutcome.dispatched(chunk.size()); },
                () -> false
        )) {
            BudgetedDispatchSlice result = dispatcher.dispatchSlice(budget);
            assertEquals(BudgetedDispatchState.BUDGET_EXCEEDED, result.state());
            assertEquals(0, calls.get());
            assertNotNull(result.detail());
        }
    }

    @Test
    void cancellationStopsBeforeNextChunkAndConflictIsTerminal() throws Exception {
        StoredChangeSet stored = prepared(2, 1);
        CancellationSource cancellation = new CancellationSource();
        AtomicInteger calls = new AtomicInteger();
        try (stored; BudgetedPreparedMutationDispatcher dispatcher = new BudgetedPreparedMutationDispatcher(
                stored,
                chunk -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) return ChunkDispatchOutcome.conflict(5, 70, -2);
                    return ChunkDispatchOutcome.dispatched(chunk.size());
                },
                cancellation.token()
        )) {
            ExecutionBudget budget = new ExecutionBudget(Duration.ofSeconds(1), 8, 32, 1_000_000);
            BudgetedDispatchSlice conflict = dispatcher.dispatchSlice(budget);
            assertEquals(BudgetedDispatchState.CONFLICT, conflict.state());
            assertEquals(5, conflict.conflictX());
            assertEquals(1, calls.get());
        }

        StoredChangeSet cancelledStored = prepared(2, 1);
        CancellationSource preCancelled = new CancellationSource();
        preCancelled.requestCancellation();
        try (cancelledStored; BudgetedPreparedMutationDispatcher dispatcher = new BudgetedPreparedMutationDispatcher(
                cancelledStored,
                chunk -> fail("cancelled dispatcher must not call target"),
                preCancelled.token()
        )) {
            ExecutionBudget budget = new ExecutionBudget(Duration.ofSeconds(1), 8, 32, 1_000_000);
            assertEquals(BudgetedDispatchState.CANCELLED, dispatcher.dispatchSlice(budget).state());
        }
    }

    private static StoredChangeSet prepared(int chunks, int changesPerChunk) throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("budget-test-" + chunks + "-" + changesPerChunk)) {
            for (int chunkX = 0; chunkX < chunks; chunkX++) {
                long[] positions = new long[changesPerChunk];
                int[] before = new int[changesPerChunk];
                int[] after = new int[changesPerChunk];
                for (int i = 0; i < changesPerChunk; i++) {
                    positions[i] = LocalBlockPosition.pack(i & 15, 64, 0);
                    after[i] = 1;
                }
                writer.append(new ChunkChangeSet(
                        chunkX, 0,
                        List.of("minecraft:stone", "minecraft:dirt"),
                        positions, before, after
                ));
            }
            return writer.commit();
        }
    }
}
