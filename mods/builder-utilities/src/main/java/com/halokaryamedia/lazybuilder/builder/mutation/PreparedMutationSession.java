package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;

import java.io.IOException;
import java.util.Objects;

/** Lifecycle owner for one prepared durable mutation. */
public final class PreparedMutationSession implements AutoCloseable {
    private final PreparedBlockMutation prepared;
    private final HistoryTimeline timeline;
    private final boolean publishToTimeline;
    private OperationLifecycle lifecycle;
    private boolean ownershipTransferred;
    private boolean disposed;

    public PreparedMutationSession(PreparedBlockMutation prepared, HistoryTimeline timeline) {
        this(prepared, timeline, true);
    }

    public PreparedMutationSession(
            PreparedBlockMutation prepared,
            HistoryTimeline timeline,
            boolean publishToTimeline
    ) {
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        if (prepared.changeSet().extensionCount() != 0) {
            throw new IllegalArgumentException(
                    "PreparedMutationSession is block-only; extension frames require an extension-aware session");
        }
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.publishToTimeline = publishToTimeline;
        this.lifecycle = OperationLifecycle.created(prepared.plannedChanges())
                .transitionTo(OperationState.VALIDATING)
                .transitionTo(OperationState.PLANNING)
                .transitionTo(OperationState.QUEUED);
    }

    public synchronized OperationLifecycle lifecycle() { return lifecycle; }
    public PreparedBlockMutation prepared() { return prepared; }

    public synchronized void startDispatch() {
        ensureOwned();
        if (lifecycle.state() != OperationState.QUEUED) {
            throw new IllegalStateException("Dispatch can start only from QUEUED");
        }
        lifecycle = lifecycle.transitionTo(OperationState.RUNNING);
    }

    public synchronized void noteProcessedWork(long processedWork) {
        ensureOwned();
        if (lifecycle.state() != OperationState.RUNNING) {
            return;
        }
        if (processedWork < lifecycle.processedWork() || processedWork > lifecycle.totalWork()) {
            throw new IllegalArgumentException(
                    "processedWork must be monotonic and <= totalWork");
        }
        lifecycle = new OperationLifecycle(
                lifecycle.state(), processedWork, lifecycle.totalWork(), null);
    }

    public synchronized void noteCancellationRequested() {
        ensureOwned();
        if (lifecycle.state() == OperationState.QUEUED || lifecycle.state() == OperationState.RUNNING) {
            lifecycle = lifecycle.transitionTo(OperationState.CANCELLING);
        }
    }

    public synchronized void fail(String message) {
        ensureOwned();
        lifecycle = lifecycle.fail(message);
    }

    public synchronized PreparedReconciliationReport reconcile(WorldBlockStateSource world) throws IOException {
        ensureOwned();
        if (lifecycle.state() != OperationState.RUNNING && lifecycle.state() != OperationState.CANCELLING) {
            throw new IllegalStateException("Reconciliation requires RUNNING or CANCELLING lifecycle");
        }
        PreparedReconciliationReport report = PreparedMutationReconciler.reconcile(prepared.changeSet(), world);
        switch (report.state()) {
            case FULLY_APPLIED -> completeWithHistory();
            case EMPTY -> completeWithoutHistory();
            case CONFLICT -> lifecycle = lifecycle.fail("Prepared mutation conflicts with current world state");
            case NOT_APPLIED, PARTIALLY_APPLIED -> { }
        }
        return report;
    }

    /** Marks a successfully reconciled rollback cancellation terminal with no undo entry. */
    public synchronized void completeRollbackCancellation() throws IOException {
        ensureCancelling();
        cancelAfterDisposingOriginal(0);
    }

    private void cancelAfterDisposingOriginal(long processedWork) throws IOException {
        try {
            prepared.close();
            disposed = true;
        } catch (IOException e) {
            lifecycle = lifecycle.fail("Cancellation cleanup failed: " + e.getMessage());
            throw e;
        }
        setProcessedWork(processedWork);
        lifecycle = lifecycle.transitionTo(OperationState.CANCELLED);
    }

    private void completeWithHistory() throws IOException {
        setProcessedWork(lifecycle.totalWork());
        lifecycle = lifecycle.transitionTo(OperationState.COMMITTING);
        if (!publishToTimeline) {
            try {
                prepared.close();
                disposed = true;
                lifecycle = lifecycle.transitionTo(OperationState.COMPLETED);
            } catch (IOException | RuntimeException e) {
                lifecycle = lifecycle.fail(
                        "Failed to release completed mutation journal: " + e.getMessage());
                throw e;
            }
            return;
        }

        try {
            timeline.record(prepared.changeSet());
            ownershipTransferred = true;
            lifecycle = lifecycle.transitionTo(OperationState.COMPLETED);
        } catch (IOException | RuntimeException e) {
            lifecycle = lifecycle.fail("Failed to publish mutation into undo timeline: " + e.getMessage());
            throw e;
        }
    }

    private void completeWithoutHistory() throws IOException {
        setProcessedWork(lifecycle.totalWork());
        lifecycle = lifecycle.transitionTo(OperationState.COMMITTING);
        prepared.close();
        disposed = true;
        lifecycle = lifecycle.transitionTo(OperationState.COMPLETED);
    }

    private void setProcessedWork(long processedWork) {
        lifecycle = new OperationLifecycle(lifecycle.state(), processedWork, lifecycle.totalWork(), null);
    }

    /**
     * Detaches a durable prepared plan from this session without deleting it.
     * Used when execution fails after world mutation may already be partial.
     */
    public synchronized boolean preserveForRecovery() throws IOException {
        if (ownershipTransferred || disposed) {
            throw new IllegalStateException("Prepared mutation ownership has already been released");
        }
        boolean preserved = prepared.changeSet().preserveForRecovery();
        if (preserved) ownershipTransferred = true;
        return preserved;
    }

    private void ensureCancelling() {
        ensureOwned();
        if (lifecycle.state() != OperationState.CANCELLING) {
            throw new IllegalStateException("Cancellation finalization requires CANCELLING lifecycle");
        }
    }

    private void ensureOwned() {
        if (ownershipTransferred || disposed) {
            throw new IllegalStateException("Prepared mutation ownership has already been released");
        }
        if (lifecycle.state().isTerminal()) {
            throw new IllegalStateException("Prepared mutation session is terminal: " + lifecycle.state());
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (ownershipTransferred || disposed) return;
        prepared.close();
        disposed = true;
    }
}
