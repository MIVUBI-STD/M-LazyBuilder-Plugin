package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.mutation.*;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/** Axiom-facing orchestration wrapper for one durable prepared block mutation. */
public final class AxiomPreparedMutationSession implements AutoCloseable {
    private final PreparedMutationSession core;
    private final AxiomBudgetedChunkDispatchTarget axiomTarget;
    private final BudgetedPreparedMutationDispatcher budgetedDispatcher;
    private final AxiomClientWorldStateSource worldSource;
    private final CancellationToken cancellationToken;
    private boolean dispatchStarted;
    private PreparedRollback rollback;
    private BudgetedPreparedMutationDispatcher rollbackDispatcher;

    public AxiomPreparedMutationSession(
            AxiomClientServices services,
            ClientWorld world,
            PreparedBlockMutation prepared,
            HistoryTimeline timeline,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.changeSet().extensionCount() != 0) {
            throw new IllegalArgumentException(
                    "Axiom public block mutation path cannot apply History extension frames");
        }
        // Native Axiom owns normal block-only undo/redo. The durable LazyBuilder
        // journal exists for crash safety only and is deleted after reconciliation.
        this.core = new PreparedMutationSession(
                prepared,
                Objects.requireNonNull(timeline, "timeline"),
                false
        );
        AxiomChunkMutationDispatcher dispatcher = new AxiomChunkMutationDispatcher(services, world);
        this.axiomTarget = new AxiomBudgetedChunkDispatchTarget(dispatcher);
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
        this.budgetedDispatcher = new BudgetedPreparedMutationDispatcher(
                prepared.changeSet(), axiomTarget, cancellationToken);
        this.worldSource = new AxiomClientWorldStateSource(world);
    }

    public synchronized OperationLifecycle lifecycle() { return core.lifecycle(); }

    public synchronized BudgetedDispatchSlice dispatchSlice(ExecutionBudget budget) throws IOException {
        if (rollback != null) throw new IllegalStateException("Forward dispatch is unavailable after rollback preparation");
        Objects.requireNonNull(budget, "budget");
        ensureDispatchStarted();
        BudgetedDispatchSlice result = budgetedDispatcher.dispatchSlice(budget);
        core.noteProcessedWork(Math.min(
                core.lifecycle().totalWork(),
                budgetedDispatcher.totalProcessedMutations()));
        switch (result.state()) {
            case YIELDED, EXHAUSTED -> { }
            case CANCELLED -> core.noteCancellationRequested();
            case CONFLICT -> core.reconcile(worldSource);
            case BUDGET_EXCEEDED -> core.fail("Mutation dispatch budget exceeded: " + result.detail());
        }
        return result;
    }

    public synchronized PreparedReconciliationReport reconcile() throws IOException {
        if (rollback != null) throw new IllegalStateException("Use reconcileRollback() after rollback preparation");
        if (!dispatchStarted) throw new IllegalStateException("Cannot reconcile before dispatch");
        if (cancellationToken.isCancellationRequested() && !core.lifecycle().state().isTerminal()) {
            core.noteCancellationRequested();
        }
        PreparedReconciliationReport report = core.reconcile(worldSource);
        if (core.lifecycle().state().isTerminal()) budgetedDispatcher.close();
        return report;
    }

    /** Prepares a durable reverse plan for the subset already applied. */
    public synchronized RollbackPreparationResult prepareRollback(
            HistoryStorageRouter history,
            long estimatedHistoryBytes
    ) throws IOException {
        Objects.requireNonNull(history, "history");
        ensureNotTerminal();
        if (rollback != null) throw new IllegalStateException("Rollback cancellation is already prepared");
        core.noteCancellationRequested();
        budgetedDispatcher.close();

        PreparedRollback preparedRollback = RollbackCancellationPreparer.prepare(
                core.prepared().changeSet(),
                worldSource,
                history,
                estimatedHistoryBytes,
                core.prepared().changeSet().operationId() + "-cancel"
        );
        switch (preparedRollback.state()) {
            case EMPTY -> {
                core.completeRollbackCancellation();
                preparedRollback.close();
            }
            case CONFLICT -> {
                core.fail("Rollback preparation conflicts with current world state");
                preparedRollback.close();
            }
            case READY -> {
                rollback = preparedRollback;
                rollbackDispatcher = new BudgetedPreparedMutationDispatcher(
                        rollback.rollbackPlan(), axiomTarget, () -> false);
            }
        }
        return RollbackPreparationResult.from(preparedRollback);
    }

    public synchronized BudgetedDispatchSlice dispatchRollbackSlice(ExecutionBudget budget) throws IOException {
        requireReadyRollback();
        BudgetedDispatchSlice result = rollbackDispatcher.dispatchSlice(Objects.requireNonNull(budget, "budget"));
        if (result.state() == BudgetedDispatchState.CONFLICT) {
            core.fail("Rollback dispatch conflicts with current world state");
        } else if (result.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            core.fail("Rollback dispatch budget exceeded: " + result.detail());
        }
        return result;
    }

    public synchronized PreparedReconciliationReport reconcileRollback() throws IOException {
        requireReadyRollback();
        PreparedReconciliationReport report = PreparedMutationReconciler.reconcile(rollback.rollbackPlan(), worldSource);
        switch (report.state()) {
            case FULLY_APPLIED, EMPTY -> finishRollbackCancellation();
            case CONFLICT -> core.fail("Rollback reconciliation conflicts with current world state");
            case NOT_APPLIED, PARTIALLY_APPLIED -> { }
        }
        return report;
    }

    private void finishRollbackCancellation() throws IOException {
        rollbackDispatcher.close();
        rollback.close();
        rollbackDispatcher = null;
        rollback = null;
        core.completeRollbackCancellation();
    }

    /**
     * Stops active cursors and leaves the original durable plan discoverable by
     * recovery. Temporary rollback plans are discarded because the original plan
     * is sufficient to classify the resulting world state.
     */
    public synchronized boolean preserveForRecovery() throws IOException {
        IOException failure = null;
        if (rollbackDispatcher != null) {
            rollbackDispatcher.close();
            rollbackDispatcher = null;
        }
        if (rollback != null) {
            try {
                rollback.close();
            } catch (IOException e) {
                failure = e;
            } finally {
                rollback = null;
            }
        }
        budgetedDispatcher.close();

        boolean preserved = false;
        try {
            preserved = core.preserveForRecovery();
        } catch (IOException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
        return preserved;
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        if (rollbackDispatcher != null) rollbackDispatcher.close();
        if (rollback != null) {
            try { rollback.close(); } catch (IOException e) { failure = e; }
        }
        budgetedDispatcher.close();
        try { core.close(); } catch (IOException e) {
            if (failure == null) failure = e; else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }

    private void ensureDispatchStarted() {
        ensureNotTerminal();
        if (!dispatchStarted) {
            core.startDispatch();
            dispatchStarted = true;
        }
    }

    private void ensureNotTerminal() {
        if (core.lifecycle().state().isTerminal()) {
            throw new IllegalStateException("Prepared mutation session is terminal: " + core.lifecycle().state());
        }
    }

    private void requireReadyRollback() {
        ensureNotTerminal();
        if (rollback == null || rollback.state() != RollbackPreparationState.READY || rollbackDispatcher == null) {
            throw new IllegalStateException("Rollback cancellation is not READY");
        }
    }
}
