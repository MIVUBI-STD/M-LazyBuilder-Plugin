package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.operation.OperationState;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Lock-free runtime counters used by Builder proof/diagnostic surfaces. */
public final class BuilderRuntimeMetrics {
    private final AtomicLong operationsStarted = new AtomicLong();
    private final AtomicLong operationsCompleted = new AtomicLong();
    private final AtomicLong operationsCancelled = new AtomicLong();
    private final AtomicLong operationsFailed = new AtomicLong();
    private final AtomicLong forwardChunksVisited = new AtomicLong();
    private final AtomicLong forwardBlocksDispatched = new AtomicLong();
    private final AtomicLong rollbackChunksVisited = new AtomicLong();
    private final AtomicLong rollbackBlocksDispatched = new AtomicLong();
    private final AtomicLong forwardBiomeExtensions = new AtomicLong();
    private final AtomicLong forwardEntityExtensions = new AtomicLong();
    private final AtomicLong rollbackBiomeExtensions = new AtomicLong();
    private final AtomicLong rollbackEntityExtensions = new AtomicLong();
    private final AtomicLong extensionConflicts = new AtomicLong();
    private final AtomicLong extensionFailures = new AtomicLong();
    private final AtomicLong conflicts = new AtomicLong();
    private final AtomicLong budgetExceeded = new AtomicLong();
    private final AtomicLong maxSliceNanos = new AtomicLong();
    private final AtomicReference<String> lastOutcome = new AtomicReference<>("none");

    public void operationStarted() {
        operationsStarted.incrementAndGet();
    }

    public void recordForwardSlice(long chunks, long blocks, long elapsedNanos) {
        requireNonNegative(chunks, "chunks");
        requireNonNegative(blocks, "blocks");
        requireNonNegative(elapsedNanos, "elapsedNanos");
        forwardChunksVisited.addAndGet(chunks);
        forwardBlocksDispatched.addAndGet(blocks);
        maxSliceNanos.accumulateAndGet(elapsedNanos, Math::max);
    }

    public void recordRollbackSlice(long chunks, long blocks, long elapsedNanos) {
        requireNonNegative(chunks, "chunks");
        requireNonNegative(blocks, "blocks");
        requireNonNegative(elapsedNanos, "elapsedNanos");
        rollbackChunksVisited.addAndGet(chunks);
        rollbackBlocksDispatched.addAndGet(blocks);
        maxSliceNanos.accumulateAndGet(elapsedNanos, Math::max);
    }

    public void recordForwardBiomeExtensions(long count) {
        addNonNegative(forwardBiomeExtensions, count, "forwardBiomeExtensions");
    }

    public void recordForwardEntityExtensions(long count) {
        addNonNegative(forwardEntityExtensions, count, "forwardEntityExtensions");
    }

    public void recordRollbackBiomeExtensions(long count) {
        addNonNegative(rollbackBiomeExtensions, count, "rollbackBiomeExtensions");
    }

    public void recordRollbackEntityExtensions(long count) {
        addNonNegative(rollbackEntityExtensions, count, "rollbackEntityExtensions");
    }

    public void extensionConflict() {
        extensionConflicts.incrementAndGet();
        conflicts.incrementAndGet();
    }

    public void extensionFailure() {
        extensionFailures.incrementAndGet();
    }

    public void conflict() {
        conflicts.incrementAndGet();
    }

    public void budgetExceeded() {
        budgetExceeded.incrementAndGet();
    }

    public void terminal(OperationState state) {
        if (state == null || !state.isTerminal()) {
            throw new IllegalArgumentException("terminal state required");
        }
        switch (state) {
            case COMPLETED -> operationsCompleted.incrementAndGet();
            case CANCELLED -> operationsCancelled.incrementAndGet();
            case FAILED -> operationsFailed.incrementAndGet();
            default -> throw new IllegalArgumentException("unexpected terminal state: " + state);
        }
        lastOutcome.set(state.name());
    }

    public Snapshot snapshot() {
        return new Snapshot(
                operationsStarted.get(),
                operationsCompleted.get(),
                operationsCancelled.get(),
                operationsFailed.get(),
                forwardChunksVisited.get(),
                forwardBlocksDispatched.get(),
                rollbackChunksVisited.get(),
                rollbackBlocksDispatched.get(),
                forwardBiomeExtensions.get(),
                forwardEntityExtensions.get(),
                rollbackBiomeExtensions.get(),
                rollbackEntityExtensions.get(),
                extensionConflicts.get(),
                extensionFailures.get(),
                conflicts.get(),
                budgetExceeded.get(),
                maxSliceNanos.get(),
                lastOutcome.get()
        );
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must be >= 0");
    }

    private static void addNonNegative(AtomicLong target, long value, String label) {
        requireNonNegative(value, label);
        target.addAndGet(value);
    }

    public record Snapshot(
            long operationsStarted,
            long operationsCompleted,
            long operationsCancelled,
            long operationsFailed,
            long forwardChunksVisited,
            long forwardBlocksDispatched,
            long rollbackChunksVisited,
            long rollbackBlocksDispatched,
            long forwardBiomeExtensions,
            long forwardEntityExtensions,
            long rollbackBiomeExtensions,
            long rollbackEntityExtensions,
            long extensionConflicts,
            long extensionFailures,
            long conflicts,
            long budgetExceeded,
            long maxSliceNanos,
            String lastOutcome
    ) {}
}
