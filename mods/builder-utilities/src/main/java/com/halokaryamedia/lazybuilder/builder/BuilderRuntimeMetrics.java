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
    private final AtomicLong forwardBlockEntityExtensions = new AtomicLong();
    private final AtomicLong forwardBiomeExtensions = new AtomicLong();
    private final AtomicLong forwardEntityExtensions = new AtomicLong();
    private final AtomicLong rollbackBlockEntityExtensions = new AtomicLong();
    private final AtomicLong rollbackBiomeExtensions = new AtomicLong();
    private final AtomicLong rollbackEntityExtensions = new AtomicLong();
    private final AtomicLong extensionConflicts = new AtomicLong();
    private final AtomicLong extensionFailures = new AtomicLong();
    private final AtomicLong historyUndoBlocks = new AtomicLong();
    private final AtomicLong historyRedoBlocks = new AtomicLong();
    private final AtomicLong historyUndoBlockEntityExtensions = new AtomicLong();
    private final AtomicLong historyUndoBiomeExtensions = new AtomicLong();
    private final AtomicLong historyRedoBlockEntityExtensions = new AtomicLong();
    private final AtomicLong historyRedoBiomeExtensions = new AtomicLong();
    private final AtomicLong historyUndoEntityExtensions = new AtomicLong();
    private final AtomicLong historyRedoEntityExtensions = new AtomicLong();
    private final AtomicLong historyReplayFailures = new AtomicLong();
    private final AtomicLong totalOperationNanos = new AtomicLong();
    private final AtomicLong maxOperationNanos = new AtomicLong();
    private final AtomicLong lastOperationNanos = new AtomicLong();
    private final AtomicLong lastOperationPlannedBlocks = new AtomicLong();
    private final AtomicLong lastOperationPlannedExtensions = new AtomicLong();
    private final AtomicLong maxCompletedPlannedBlocks = new AtomicLong();
    private final AtomicLong maxCompletedPlannedExtensions = new AtomicLong();
    private final AtomicReference<String> lastOperationId = new AtomicReference<>("none");
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

    public void recordForwardBlockEntityExtensions(long count) {
        addNonNegative(forwardBlockEntityExtensions, count, "forwardBlockEntityExtensions");
    }

    public void recordForwardBiomeExtensions(long count) {
        addNonNegative(forwardBiomeExtensions, count, "forwardBiomeExtensions");
    }

    public void recordForwardEntityExtensions(long count) {
        addNonNegative(forwardEntityExtensions, count, "forwardEntityExtensions");
    }

    public void recordRollbackBlockEntityExtensions(long count) {
        addNonNegative(rollbackBlockEntityExtensions, count, "rollbackBlockEntityExtensions");
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

    public void recordHistoryUndoBlocks(long count) {
        addNonNegative(historyUndoBlocks, count, "historyUndoBlocks");
    }

    public void recordHistoryRedoBlocks(long count) {
        addNonNegative(historyRedoBlocks, count, "historyRedoBlocks");
    }

    public void recordHistoryUndoBlockEntityExtensions(long count) {
        addNonNegative(historyUndoBlockEntityExtensions, count, "historyUndoBlockEntityExtensions");
    }

    public void recordHistoryUndoBiomeExtensions(long count) {
        addNonNegative(historyUndoBiomeExtensions, count, "historyUndoBiomeExtensions");
    }

    public void recordHistoryRedoBlockEntityExtensions(long count) {
        addNonNegative(historyRedoBlockEntityExtensions, count, "historyRedoBlockEntityExtensions");
    }

    public void recordHistoryRedoBiomeExtensions(long count) {
        addNonNegative(historyRedoBiomeExtensions, count, "historyRedoBiomeExtensions");
    }

    public void recordHistoryUndoEntityExtensions(long count) {
        addNonNegative(historyUndoEntityExtensions, count, "historyUndoEntityExtensions");
    }

    public void recordHistoryRedoEntityExtensions(long count) {
        addNonNegative(historyRedoEntityExtensions, count, "historyRedoEntityExtensions");
    }

    public void historyReplayFailure() {
        historyReplayFailures.incrementAndGet();
    }

    public void conflict() {
        conflicts.incrementAndGet();
    }

    public void budgetExceeded() {
        budgetExceeded.incrementAndGet();
    }

    public void terminal(OperationState state) {
        terminal(state, 0L, "unknown", 0L, 0L);
    }

    public void terminal(
            OperationState state,
            long elapsedNanos,
            String operationId,
            long plannedBlocks,
            long plannedExtensions
    ) {
        if (state == null || !state.isTerminal()) {
            throw new IllegalArgumentException("terminal state required");
        }
        requireNonNegative(elapsedNanos, "elapsedNanos");
        requireNonNegative(plannedBlocks, "plannedBlocks");
        requireNonNegative(plannedExtensions, "plannedExtensions");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        switch (state) {
            case COMPLETED -> {
                operationsCompleted.incrementAndGet();
                maxCompletedPlannedBlocks.accumulateAndGet(plannedBlocks, Math::max);
                maxCompletedPlannedExtensions.accumulateAndGet(plannedExtensions, Math::max);
            }
            case CANCELLED -> operationsCancelled.incrementAndGet();
            case FAILED -> operationsFailed.incrementAndGet();
            default -> throw new IllegalArgumentException("unexpected terminal state: " + state);
        }
        lastOutcome.set(state.name());
        totalOperationNanos.addAndGet(elapsedNanos);
        maxOperationNanos.accumulateAndGet(elapsedNanos, Math::max);
        lastOperationNanos.set(elapsedNanos);
        lastOperationPlannedBlocks.set(plannedBlocks);
        lastOperationPlannedExtensions.set(plannedExtensions);
        lastOperationId.set(operationId);
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
                forwardBlockEntityExtensions.get(),
                forwardBiomeExtensions.get(),
                forwardEntityExtensions.get(),
                rollbackBlockEntityExtensions.get(),
                rollbackBiomeExtensions.get(),
                rollbackEntityExtensions.get(),
                extensionConflicts.get(),
                extensionFailures.get(),
                historyUndoBlocks.get(),
                historyUndoBlockEntityExtensions.get(),
                historyRedoBlocks.get(),
                historyRedoBlockEntityExtensions.get(),
                historyUndoBiomeExtensions.get(),
                historyRedoBiomeExtensions.get(),
                historyUndoEntityExtensions.get(),
                historyRedoEntityExtensions.get(),
                historyReplayFailures.get(),
                totalOperationNanos.get(),
                maxOperationNanos.get(),
                lastOperationNanos.get(),
                lastOperationPlannedBlocks.get(),
                lastOperationPlannedExtensions.get(),
                maxCompletedPlannedBlocks.get(),
                maxCompletedPlannedExtensions.get(),
                lastOperationId.get(),
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
            long forwardBlockEntityExtensions,
            long forwardBiomeExtensions,
            long forwardEntityExtensions,
            long rollbackBlockEntityExtensions,
            long rollbackBiomeExtensions,
            long rollbackEntityExtensions,
            long extensionConflicts,
            long extensionFailures,
            long historyUndoBlocks,
            long historyUndoBlockEntityExtensions,
            long historyRedoBlocks,
            long historyRedoBlockEntityExtensions,
            long historyUndoBiomeExtensions,
            long historyRedoBiomeExtensions,
            long historyUndoEntityExtensions,
            long historyRedoEntityExtensions,
            long historyReplayFailures,
            long totalOperationNanos,
            long maxOperationNanos,
            long lastOperationNanos,
            long lastOperationPlannedBlocks,
            long lastOperationPlannedExtensions,
            long maxCompletedPlannedBlocks,
            long maxCompletedPlannedExtensions,
            String lastOperationId,
            long conflicts,
            long budgetExceeded,
            long maxSliceNanos,
            String lastOutcome
    ) {}
}
