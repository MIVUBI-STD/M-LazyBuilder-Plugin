package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.StoredChunkCursor;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;

import java.io.IOException;
import java.util.Objects;

/**
 * Resumable, budget-aware dispatcher over the block frames of a committed History v2 plan.
 * One instance owns one forward-only cursor and can be called repeatedly across
 * ticks/frames without rescanning earlier chunks.
 */
public final class BudgetedPreparedMutationDispatcher implements AutoCloseable {
    private final StoredChunkCursor cursor;
    private final ChunkDispatchTarget target;
    private final CancellationToken cancellationToken;
    private ChunkChangeSet pendingChunk;
    private long totalVisitedChunks;
    private long totalDispatchedBlocks;
    private long totalProcessedBlocks;
    private BudgetedDispatchState terminalState;
    private Integer conflictX;
    private Integer conflictY;
    private Integer conflictZ;
    private String terminalDetail;
    private boolean closed;

    public BudgetedPreparedMutationDispatcher(
            StoredChangeSet prepared,
            ChunkDispatchTarget target,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(prepared, "prepared");
        // Extension frames are intentionally ignored by this block dispatcher but
        // remain checksum/count validated by StoredChunkCursor. Callers that accept
        // mixed plans must orchestrate extension execution separately.
        this.cursor = StoredChunkCursor.open(prepared);
        this.target = Objects.requireNonNull(target, "target");
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
    }

    public synchronized BudgetedDispatchSlice dispatchSlice(ExecutionBudget budget) throws IOException {
        Objects.requireNonNull(budget, "budget");
        ensureOpen();
        if (terminalState != null) return result(terminalState, 0, 0, conflictX, conflictY, conflictZ, terminalDetail);
        if (cancellationToken.isCancellationRequested()) {
            terminalState = BudgetedDispatchState.CANCELLED;
            return result(terminalState, 0, 0, null, null, null, null);
        }

        long maxNanos;
        try {
            maxNanos = budget.maxSliceDuration().toNanos();
        } catch (ArithmeticException e) {
            maxNanos = Long.MAX_VALUE;
        }
        long started = System.nanoTime();
        long sliceChunks = 0;
        long sliceBlocks = 0;

        while (true) {
            if (cancellationToken.isCancellationRequested()) {
                terminalState = BudgetedDispatchState.CANCELLED;
                return result(terminalState, sliceChunks, sliceBlocks, null, null, null, null);
            }
            if (sliceChunks >= budget.maxChunksInFlight()
                    || sliceBlocks >= budget.maxPendingMutations()
                    || (sliceChunks > 0 && elapsedNanos(started) >= maxNanos)) {
                return result(BudgetedDispatchState.YIELDED, sliceChunks, sliceBlocks, null, null, null, null);
            }

            ChunkChangeSet chunk = takeNextChunk();
            if (chunk == null) {
                terminalState = BudgetedDispatchState.EXHAUSTED;
                return result(terminalState, sliceChunks, sliceBlocks, null, null, null, null);
            }

            long estimatedWorkingSet = estimateWorkingSetBytes(chunk);
            if (estimatedWorkingSet > budget.maxWorkingMemoryBytes()) {
                terminalState = BudgetedDispatchState.BUDGET_EXCEEDED;
                terminalDetail = "chunk " + chunk.chunkX() + "," + chunk.chunkZ()
                        + " estimated working set " + estimatedWorkingSet
                        + " exceeds budget " + budget.maxWorkingMemoryBytes();
                return result(terminalState, sliceChunks, sliceBlocks, null, null, null, terminalDetail);
            }
            if (chunk.size() > budget.maxPendingMutations()) {
                terminalState = BudgetedDispatchState.BUDGET_EXCEEDED;
                terminalDetail = "chunk " + chunk.chunkX() + "," + chunk.chunkZ()
                        + " contains " + chunk.size() + " mutations; budget allows "
                        + budget.maxPendingMutations();
                return result(terminalState, sliceChunks, sliceBlocks, null, null, null, terminalDetail);
            }
            if (sliceChunks > 0 && wouldExceed(sliceBlocks, chunk.size(), budget.maxPendingMutations())) {
                pendingChunk = chunk;
                return result(BudgetedDispatchState.YIELDED, sliceChunks, sliceBlocks, null, null, null, null);
            }

            ChunkDispatchOutcome outcome = target.dispatch(chunk);
            sliceChunks++;
            totalVisitedChunks++;
            if (outcome.state() == ChunkDispatchOutcome.State.DISPATCHED) {
                sliceBlocks = Math.addExact(sliceBlocks, outcome.dispatchedBlocks());
                totalDispatchedBlocks = Math.addExact(totalDispatchedBlocks, outcome.dispatchedBlocks());
                totalProcessedBlocks = Math.addExact(totalProcessedBlocks, chunk.size());
            } else if (outcome.state() == ChunkDispatchOutcome.State.ALREADY_APPLIED) {
                totalProcessedBlocks = Math.addExact(totalProcessedBlocks, chunk.size());
            } else if (outcome.state() == ChunkDispatchOutcome.State.CONFLICT) {
                terminalState = BudgetedDispatchState.CONFLICT;
                conflictX = outcome.conflictX();
                conflictY = outcome.conflictY();
                conflictZ = outcome.conflictZ();
                return result(terminalState, sliceChunks, sliceBlocks, conflictX, conflictY, conflictZ, null);
            }
        }
    }

    public synchronized long totalVisitedChunks() { return totalVisitedChunks; }
    public synchronized long totalDispatchedBlocks() { return totalDispatchedBlocks; }
    public synchronized long totalProcessedBlocks() { return totalProcessedBlocks; }
    public synchronized boolean terminal() { return terminalState != null; }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        cursor.close();
        pendingChunk = null;
    }

    private ChunkChangeSet takeNextChunk() throws IOException {
        if (pendingChunk != null) {
            ChunkChangeSet next = pendingChunk;
            pendingChunk = null;
            return next;
        }
        return cursor.nextChunk();
    }

    private BudgetedDispatchSlice result(
            BudgetedDispatchState state,
            long sliceChunks,
            long sliceBlocks,
            Integer x,
            Integer y,
            Integer z,
            String detail
    ) {
        return new BudgetedDispatchSlice(
                state,
                sliceChunks,
                sliceBlocks,
                totalVisitedChunks,
                totalDispatchedBlocks,
                x, y, z, detail
        );
    }

    private static long elapsedNanos(long started) {
        long elapsed = System.nanoTime() - started;
        return Math.max(0L, elapsed);
    }

    private static boolean wouldExceed(long current, long additional, long limit) {
        return additional > limit - current;
    }

    private static long estimateWorkingSetBytes(ChunkChangeSet chunk) {
        long bytes = 256L;
        bytes = saturatingAdd(bytes, saturatingMultiply(chunk.size(), 16L));
        for (String state : chunk.palette()) {
            bytes = saturatingAdd(bytes, 40L + saturatingMultiply(state.length(), 2L));
        }
        return bytes;
    }

    private static long saturatingAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }

    private static long saturatingMultiply(long a, long b) {
        if (a != 0 && b > Long.MAX_VALUE / a) return Long.MAX_VALUE;
        return a * b;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Budgeted dispatcher is closed");
    }
}
