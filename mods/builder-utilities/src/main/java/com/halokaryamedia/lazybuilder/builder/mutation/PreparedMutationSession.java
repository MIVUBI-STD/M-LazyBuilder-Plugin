package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;

import java.io.IOException;
import java.util.Objects;

/**
 * Lifecycle owner for one prepared durable mutation.
 *
 * <p>Dispatch and world transport remain adapter-owned. This session only permits
 * COMPLETED after reconciliation proves the whole committed plan is present in
 * world state. Partial/not-applied plans stay RUNNING (or CANCELLING), while
 * conflicts fail explicitly.</p>
 */
public final class PreparedMutationSession implements AutoCloseable {
    private final PreparedMaterialMutation prepared;
    private final HistoryTimeline timeline;
    private OperationLifecycle lifecycle;
    private boolean ownershipTransferred;
    private boolean disposed;

    public PreparedMutationSession(PreparedMaterialMutation prepared, HistoryTimeline timeline) {
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.lifecycle = OperationLifecycle.created(prepared.plannedChanges())
                .transitionTo(OperationState.VALIDATING)
                .transitionTo(OperationState.PLANNING)
                .transitionTo(OperationState.QUEUED);
    }

    public synchronized OperationLifecycle lifecycle() {
        return lifecycle;
    }

    public PreparedMaterialMutation prepared() {
        return prepared;
    }

    public synchronized void startDispatch() {
        ensureOwned();
        if (lifecycle.state() != OperationState.QUEUED) {
            throw new IllegalStateException("Dispatch can start only from QUEUED");
        }
        lifecycle = lifecycle.transitionTo(OperationState.RUNNING);
    }

    public synchronized void noteCancellationRequested() {
        ensureOwned();
        if (lifecycle.state() == OperationState.QUEUED || lifecycle.state() == OperationState.RUNNING) {
            lifecycle = lifecycle.transitionTo(OperationState.CANCELLING);
        }
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
            case NOT_APPLIED, PARTIALLY_APPLIED -> { /* remain active/recovery-required */ }
        }
        return report;
    }

    private void completeWithHistory() throws IOException {
        lifecycle = lifecycle.transitionTo(OperationState.COMMITTING);
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
        lifecycle = lifecycle.transitionTo(OperationState.COMMITTING);
        prepared.close();
        disposed = true;
        lifecycle = lifecycle.transitionTo(OperationState.COMPLETED);
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
