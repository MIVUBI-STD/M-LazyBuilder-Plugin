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
import com.halokaryamedia.lazybuilder.builder.operation.RecoverableActiveOperation;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/**
 * Reusable lifecycle controller for Axiom block-only durable mutations.
 *
 * <p>Tools own preview/planning. This class owns bounded forward dispatch,
 * reconciliation, rollback-on-cancel and terminal cleanup.</p>
 */
public final class AxiomMutationController implements AutoCloseable, RecoverableActiveOperation {
    private final AxiomClientServices services;
    private final BuilderRuntime runtime;

    private AxiomPreparedMutationSession session;
    private AxiomBiomePreparedMutationSession mixedSession;
    private CancellationSource cancellation;
    private Phase phase = Phase.IDLE;
    private String status = "Ready";
    private long estimatedHistoryBytes;
    private OperationState pendingOutcome;
    private boolean terminalMetricRecorded;
    private boolean recoveryTransferBlocked;
    private long operationStartedNanos;
    private String operationId = "unknown";
    private long operationPlannedBlocks;
    private long operationPlannedExtensions;

    public AxiomMutationController(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public boolean isActive() {
        return session != null || mixedSession != null;
    }

    public OperationLifecycle lifecycle() {
        if (mixedSession != null) return mixedSession.lifecycle();
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
        if (session != null || mixedSession != null) {
            throw new IllegalStateException("A mutation is already active");
        }
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(prepared, "prepared");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        if (estimatedHistoryBytes <= 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be > 0");
        }
        this.estimatedHistoryBytes = estimatedHistoryBytes;
        try {
            if (prepared.changeSet().extensionCount() == 0) {
                this.session = new AxiomPreparedMutationSession(
                        services, world, prepared, runtime.timeline(), cancellation.token());
            } else {
                this.mixedSession = new AxiomBiomePreparedMutationSession(
                        services,
                        world,
                        prepared,
                        runtime.timeline(),
                        runtime.history(),
                        estimatedHistoryBytes,
                        cancellation.token(),
                        runtime.metrics()
                );
            }
        } catch (RuntimeException | IOException failure) {
            try {
                prepared.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            this.cancellation = null;
            throw failure;
        }
        try {
            runtime.registerActiveOperation(this);
        } catch (RuntimeException registrationFailure) {
            IOException cleanupFailure = null;
            try {
                if (mixedSession != null) mixedSession.close();
                else if (session != null) session.close();
            } catch (IOException e) {
                cleanupFailure = e;
            } finally {
                session = null;
                mixedSession = null;
                this.cancellation = null;
            }
            if (cleanupFailure != null) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }

        this.phase = Phase.DISPATCHING;
        this.pendingOutcome = null;
        this.terminalMetricRecorded = false;
        this.recoveryTransferBlocked = false;
        this.operationStartedNanos = System.nanoTime();
        this.operationId = prepared.changeSet().operationId();
        this.operationPlannedBlocks = prepared.plannedChanges();
        this.operationPlannedExtensions = prepared.changeSet().extensionCount();
        runtime.metrics().operationStarted();
        this.status = "Prepared " + prepared.plannedChanges() + " block changes";
    }

    public boolean requestRollbackCancellation() {
        if (!isActive() || lifecycle().state().isTerminal()) return false;
        boolean changed = cancellation.requestCancellation();
        status = "Cancellation requested";
        return changed;
    }

    /** Advances at most one budgeted execution/reconciliation slice. */
    public void pump() {
        if (!isActive() || recoveryTransferBlocked) return;
        try {
            if (mixedSession != null) {
                pumpMixed();
                return;
            }
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

    private void pumpMixed() throws IOException {
        AxiomMixedPumpResult result = mixedSession.pump(runtime.dispatchBudget());
        status = result.detail() == null
                ? "Mixed mutation: " + result.state()
                : result.detail();
        if (result.state() == AxiomMixedPumpResult.State.COMPLETED
                || result.state() == AxiomMixedPumpResult.State.CANCELLED
                || result.state() == AxiomMixedPumpResult.State.FAILED) {
            OperationState finalState = mixedSession.lifecycle().state();
            if (finalState == OperationState.FAILED) {
                boolean preserved = mixedSession.preserveForRecovery();
                pendingOutcome = finalState;
                recordTerminalOnce(finalState);
                if (!preserved) {
                    recoveryTransferBlocked = true;
                    phase = Phase.IDLE;
                    status = "Last mixed operation: FAILED | durable plan retained for preservation retry";
                    return;
                }
                status = "Last mixed operation: FAILED (durable plan preserved for Recovery)";
            } else {
                mixedSession.close();
                status = "Last mixed operation: " + finalState;
            }
            mixedSession = null;
            cancellation = null;
            phase = Phase.IDLE;
            recoveryTransferBlocked = false;
            pendingOutcome = finalState;
            runtime.unregisterActiveOperation(this);
            recordTerminalOnce(finalState);
        }
    }

    private void pumpForwardDispatch() throws IOException {
        long started = System.nanoTime();
        BudgetedDispatchSlice slice = session.dispatchSlice(runtime.dispatchBudget());
        runtime.metrics().recordForwardSlice(
                slice.sliceVisitedChunks(),
                slice.sliceDispatchedBlocks(),
                Math.max(0L, System.nanoTime() - started));
        status = "Dispatch " + slice.totalVisitedChunks() + " chunks / "
                + slice.totalDispatchedBlocks() + " blocks";

        if (slice.state() == BudgetedDispatchState.EXHAUSTED) {
            phase = Phase.RECONCILING;
        } else if (slice.state() == BudgetedDispatchState.CANCELLED) {
            beginRollback();
        } else if (slice.state() == BudgetedDispatchState.CONFLICT
                || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            if (slice.state() == BudgetedDispatchState.CONFLICT) runtime.metrics().conflict();
            else runtime.metrics().budgetExceeded();
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
        long started = System.nanoTime();
        BudgetedDispatchSlice slice = session.dispatchRollbackSlice(runtime.dispatchBudget());
        runtime.metrics().recordRollbackSlice(
                slice.sliceVisitedChunks(),
                slice.sliceDispatchedBlocks(),
                Math.max(0L, System.nanoTime() - started));
        status = "Rollback dispatch: " + slice.state();
        if (slice.state() == BudgetedDispatchState.EXHAUSTED) {
            phase = Phase.ROLLBACK_RECONCILE;
        } else if (slice.state() == BudgetedDispatchState.CONFLICT
                || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            if (slice.state() == BudgetedDispatchState.CONFLICT) runtime.metrics().conflict();
            else runtime.metrics().budgetExceeded();
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
            pendingOutcome = finalState;
            recordTerminalOnce(finalState);
            if (!preserved) {
                recoveryTransferBlocked = true;
                phase = Phase.IDLE;
                status = "Last operation: FAILED | durable plan retained for preservation retry";
                return;
            }
            status = "Last operation: FAILED (durable plan preserved for Recovery)";
        } else {
            session.close();
            status = "Last operation: " + finalState;
        }
        session = null;
        cancellation = null;
        phase = Phase.IDLE;
        recoveryTransferBlocked = false;
        pendingOutcome = finalState;
        runtime.unregisterActiveOperation(this);
        recordTerminalOnce(finalState);
    }

    @Override
    public void preserveForWorldExit() throws IOException {
        if (!isActive()) {
            runtime.unregisterActiveOperation(this);
            return;
        }

        boolean preserved = mixedSession != null
                ? mixedSession.preserveForRecovery()
                : session.preserveForRecovery();
        if (!preserved) {
            recoveryTransferBlocked = true;
            phase = Phase.IDLE;
            status = "World-exit recovery transfer failed; operation retained for preservation retry";
            throw new IOException(
                    "Active Builder mutation could not transfer its durable plan to Recovery");
        }

        session = null;
        mixedSession = null;
        cancellation = null;
        phase = Phase.IDLE;
        recoveryTransferBlocked = false;
        runtime.unregisterActiveOperation(this);
    }

    @Override
    public void close() throws IOException {
        preserveForWorldExit();
    }

    private void preserveFailedOperation(Exception failure) {
        String base = "Operation failed: " + safeMessage(failure);
        boolean preserved = false;
        Exception preserveFailure = null;
        if (mixedSession != null) {
            try {
                preserved = mixedSession.preserveForRecovery();
            } catch (Exception e) {
                preserveFailure = e;
            }
        } else if (session != null) {
            try {
                preserved = session.preserveForRecovery();
            } catch (Exception e) {
                preserveFailure = e;
            }
        }

        phase = Phase.IDLE;
        pendingOutcome = OperationState.FAILED;
        recordTerminalOnce(OperationState.FAILED);

        if (preserved) {
            session = null;
            mixedSession = null;
            cancellation = null;
            recoveryTransferBlocked = false;
            runtime.unregisterActiveOperation(this);
            status = base + " | durable plan preserved for Recovery";
            return;
        }

        // Keep the session and runtime registration alive when durable ownership
        // could not be transferred. Pumping is blocked, but world-exit/close may
        // retry preservation instead of silently orphaning an owned journal.
        recoveryTransferBlocked = true;
        status = base + (preserveFailure != null
                ? " | recovery transfer failed: " + safeMessage(preserveFailure)
                : " | durable plan was not transferred to Recovery")
                + " | operation retained for preservation retry";
    }

    private void recordTerminalOnce(OperationState state) {
        if (terminalMetricRecorded) return;
        runtime.metrics().terminal(
                state,
                elapsedOperationNanos(),
                operationId,
                operationPlannedBlocks,
                operationPlannedExtensions);
        terminalMetricRecorded = true;
    }

    private long elapsedOperationNanos() {
        if (operationStartedNanos == 0L) return 0L;
        long elapsed = System.nanoTime() - operationStartedNanos;
        return Math.max(0L, elapsed);
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
