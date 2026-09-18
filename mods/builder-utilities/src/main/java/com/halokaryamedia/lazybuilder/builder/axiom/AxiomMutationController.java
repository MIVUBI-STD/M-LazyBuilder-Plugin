package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
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
            PreparedMaterialMutation prepared,
            CancellationSource cancellation,
            long estimatedHistoryBytes
    ) {
        if (session != null) throw new IllegalStateException("A mutation is already active");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(prepared, "prepared");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        if (estimatedHistoryBytes <= 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be > 0");
        }
        this.estimatedHistoryBytes = estimatedHistoryBytes;
        this.session = new AxiomPreparedMutationSession(
                services, world, prepared, runtime.timeline(), cancellation.token());
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
            status = "Operation failed: " + safeMessage(e);
            pendingOutcome = OperationState.FAILED;
            closeActiveQuietly();
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
        session.close();
        session = null;
        cancellation = null;
        phase = Phase.IDLE;
        pendingOutcome = finalState;
        status = "Last operation: " + finalState;
    }

    @Override
    public void close() throws IOException {
        if (session == null) return;
        session.close();
        session = null;
        cancellation = null;
        phase = Phase.IDLE;
    }

    private void closeActiveQuietly() {
        if (session != null) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
        session = null;
        cancellation = null;
        phase = Phase.IDLE;
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
