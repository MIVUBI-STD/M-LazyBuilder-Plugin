package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.RollbackPreparationResult;
import com.halokaryamedia.lazybuilder.builder.mutation.RollbackPreparationState;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/**
 * Reusable lifecycle controller for Axiom block-only durable mutations.
 *
 * <p>Tools own preview/planning. This class owns bounded forward dispatch,
 * reconciliation, rollback-on-cancel and terminal cleanup.</p>
 */
public final class AxiomMutationController implements AutoCloseable {
    private final AxiomClientServices services;
    private final BuilderRuntime runtime;

    private AxiomPreparedMutationSession session;
    private CancellationSource cancellation;
    private Phase phase = Phase.IDLE;
    private String status = "Ready";
    private long estimatedHistoryBytes;
    private OperationState pendingOutcome;

    public AxiomMutationController(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public boolean isActive() {
        return session != null;
    }

    public OperationLifecycle lifecycle() {
        if (session == null) throw new IllegalStateException("No active mutation");
        return session.lifecycle();
    }

    public String status() {
        return status;
    }

    public void start(
            ClientWorld world,
            PreparedBlockMutation prepared,
            CancellationSource cancellation,
            long estimatedHistoryBytes
    ) throws IOException {
        if (session != null) throw new IllegalStateException("A mutation is already active");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(prepared, "prepared");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        if (estimatedHistoryBytes <= 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be > 0");
        }
        this.estimatedHistoryBytes = estimatedHistoryBytes;
        try {
            this.session = new AxiomPreparedMutationSession(
                    services, world, prepared, runtime.timeline(), cancellation.token());
        } catch (RuntimeException failure) {
            try {
                prepared.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            this.cancellation = null;
            throw failure;
        }
        this.phase = Phase.DISPATCHING;
        this.pendingOutcome = null;
        this.status = "Prepared " + prepared.plannedChanges() + " block changes";
    }

    public boolean requestRollbackCancellation() {
        if (session == null || session.lifecycle().state().isTerminal()) return false;
        boolean changed = cancellation.requestCancellation();
        status = "Cancellation requested";
        return changed;
    }

    /** Advances at most one budgeted execution/reconciliation slice. */
    public void pump() {
        if (session == null) return;
        try {
            if (cancellation.token().isCancellationRequested()
                    && phase != Phase.ROLLBACK_DISPATCH
                    && phase != Phase.ROLLBACK_RECONCILE) {
                beginRollback();
                return;
            }

            switch (phase) {
                case DISPATCHING -> pumpForwardDispatch();
                case RECONCILING -> pumpForwardReconcile();
                case ROLLBACK_DISPATCH -> pumpRollbackDispatch();
                case ROLLBACK_RECONCILE -> pumpRollbackReconcile();
                case IDLE -> { }
            }
        } catch (Exception e) {
            preserveFailedOperation(e);
        }
    }

    /**
     * Returns one terminal result once. Null means no new terminal result.
     */
    public OperationState pollOutcome() {
        OperationState result = pendingOutcome;
        pendingOutcome = null;
        return result;
    }

    private void pumpForwardDispatch() throws IOException {
        BudgetedDispatchSlice slice = session.dispatchSlice(runtime.dispatchBudget());
        status = "Dispatch " + slice.totalVisitedChunks() + " chunks / "
                + slice.totalDispatchedBlocks() + " blocks";

        if (slice.state() == BudgetedDispatchState.EXHAUSTED) {
            phase = Phase.RECONCILING;
        } else if (slice.state() == BudgetedDispatchState.CANCELLED) {
            beginRollback();
        } else if (slice.state() == BudgetedDispatchState.CONFLICT
                || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            finishIfTerminal();
        }
    }

    private void pumpForwardReconcile() throws IOException {
        PreparedReconciliationReport report = session.reconcile();
        status = "Reconcile: " + report.state();
        finishIfTerminal();
    }

    private void beginRollback() throws IOException {
        RollbackPreparationResult result = session.prepareRollback(
                runtime.history(), estimatedHistoryBytes);
        status = "Rollback preparation: " + result.state();
        if (result.state() == RollbackPreparationState.READY) {
            phase = Phase.ROLLBACK_DISPATCH;
        } else {
            finishIfTerminal();
        }
    }

    private void pumpRollbackDispatch() throws IOException {
        BudgetedDispatchSlice slice = session.dispatchRollbackSlice(runtime.dispatchBudget());
        status = "Rollback dispatch: " + slice.state();
        if (slice.state() == BudgetedDispatchState.EXHAUSTED) {
            phase = Phase.ROLLBACK_RECONCILE;
        } else if (slice.state() == BudgetedDispatchState.CONFLICT
                || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            finishIfTerminal();
        }
    }

    private void pumpRollbackReconcile() throws IOException {
        PreparedReconciliationReport report = session.reconcileRollback();
        status = "Rollback reconcile: " + report.state();
        finishIfTerminal();
    }

    private void finishIfTerminal() throws IOException {
        if (session == null || !session.lifecycle().state().isTerminal()) return;
        OperationState finalState = session.lifecycle().state();
        if (finalState == OperationState.FAILED) {
            boolean preserved = session.preserveForRecovery();
            status = preserved
                    ? "Last operation: FAILED (durable plan preserved for Recovery)"
                    : "Last operation: FAILED (plan could not be exposed to Recovery in this runtime)";
        } else {
            session.close();
            status = "Last operation: " + finalState;
        }
        session = null;
        cancellation = null;
        phase = Phase.IDLE;
        pendingOutcome = finalState;
    }

    @Override
    public void close() throws IOException {
        if (session == null) return;
        try {
            session.preserveForRecovery();
        } finally {
            session = null;
            cancellation = null;
            phase = Phase.IDLE;
        }
    }

    private void preserveFailedOperation(Exception failure) {
        String base = "Operation failed: " + safeMessage(failure);
        boolean preserved = false;
        Exception preserveFailure = null;
        if (session != null) {
            try {
                preserved = session.preserveForRecovery();
            } catch (Exception e) {
                preserveFailure = e;
            }
        }
        session = null;
        cancellation = null;
        phase = Phase.IDLE;
        pendingOutcome = OperationState.FAILED;
        if (preserved) {
            status = base + " | durable plan preserved for Recovery";
        } else if (preserveFailure != null) {
            status = base + " | recovery release failed: " + safeMessage(preserveFailure)
                    + " (journal remains on disk for restart recovery)";
        } else {
            status = base;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private enum Phase {
        IDLE,
        DISPATCHING,
        RECONCILING,
        ROLLBACK_DISPATCH,
        ROLLBACK_RECONCILE
    }
}
