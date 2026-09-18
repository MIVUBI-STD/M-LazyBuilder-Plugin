package com.halokaryamedia.lazybuilder.builder.benchmark;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedPreparedMutationDispatcher;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchOutcome;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic Golden workload for the Builder dispatch core.
 *
 * <p>This is not Minecraft/Axiom runtime proof. It exists to compare exact-source
 * revisions under the same runner and to expose throughput/yield regressions in
 * the bounded dispatcher without pretending to measure client FPS.</p>
 */
@Tag("golden-benchmark")
class BuilderGoldenWorkloadBenchmarkTest {
    private static final int CHANGES_PER_CHUNK = 4096;
    private static final long MIB = 1024L * 1024L;

    @Test
    void dispatchesLargePreparedMutationWithBoundedSlices() throws Exception {
        long requestedChanges = Long.getLong("lazybuilder.golden.changes", 1_000_000L);
        if (requestedChanges <= 0 || requestedChanges > 20_000_000L) {
            throw new IllegalArgumentException(
                    "lazybuilder.golden.changes must be in 1..20,000,000");
        }

        StoredChangeSet stored = createPreparedHistory(requestedChanges);
        ExecutionBudget budget = new ExecutionBudget(
                Duration.ofMillis(4),
                4,
                65_536,
                16 * MIB
        );

        long started = System.nanoTime();
        long yields = 0L;
        long slices = 0L;
        BudgetedDispatchSlice terminal;

        try (stored;
             BudgetedPreparedMutationDispatcher dispatcher =
                     new BudgetedPreparedMutationDispatcher(
                             stored,
                             chunk -> ChunkDispatchOutcome.dispatched(chunk.size()),
                             () -> false)) {
            while (true) {
                BudgetedDispatchSlice slice = dispatcher.dispatchSlice(budget);
                slices++;
                if (slice.state() == BudgetedDispatchState.YIELDED) {
                    yields++;
                    continue;
                }
                terminal = slice;
                break;
            }

            assertEquals(BudgetedDispatchState.EXHAUSTED, terminal.state());
            assertEquals(requestedChanges, dispatcher.totalProcessedMutations());
            assertEquals(requestedChanges, dispatcher.totalDispatchedBlocks());
            assertTrue(slices > 0);
        }

        long elapsedNanos = Math.max(1L, System.nanoTime() - started);
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        System.out.printf(
                Locale.ROOT,
                "LAZYBUILDER_GOLDEN workload=prepared-dispatch changes=%d "
                        + "slices=%d yields=%d elapsed_ms=%.3f mchanges_s=%.3f%n",
                requestedChanges,
                slices,
                yields,
                elapsedNanos / 1_000_000.0,
                requestedChanges / elapsedSeconds / 1_000_000.0
        );
    }

    private static StoredChangeSet createPreparedHistory(long requestedChanges) throws Exception {
        MemoryChangeSetStorage storage = new MemoryChangeSetStorage();
        try (ChangeSetWriter writer = storage.begin("golden-prepared-dispatch")) {
            long written = 0L;
            int chunkOrdinal = 0;
            while (written < requestedChanges) {
                int count = (int) Math.min(CHANGES_PER_CHUNK, requestedChanges - written);
                writer.append(chunk(chunkOrdinal++, count));
                written += count;
            }
            StoredChangeSet stored = writer.commit();
            assertEquals(requestedChanges, stored.changeCount());
            return stored;
        }
    }

    private static ChunkChangeSet chunk(int ordinal, int count) {
        long[] positions = new long[count];
        int[] before = new int[count];
        int[] after = new int[count];

        for (int i = 0; i < count; i++) {
            int localIndex = i & 4095;
            int localX = localIndex & 15;
            int localZ = (localIndex >>> 4) & 15;
            int y = (localIndex >>> 8) + ((ordinal & 15) * 16);
            positions[i] = LocalBlockPosition.pack(localX, y, localZ);
            after[i] = 1;
        }

        return new ChunkChangeSet(
                ordinal,
                0,
                List.of("minecraft:stone", "minecraft:dirt"),
                positions,
                before,
                after
        );
    }
}
