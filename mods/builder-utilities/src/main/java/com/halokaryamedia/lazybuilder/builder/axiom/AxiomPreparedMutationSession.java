package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedPreparedMutationDispatcher;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationSession;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Axiom-facing orchestration wrapper for one durable prepared block mutation.
 *
 * <p>Dispatch is resumable and budgeted. Successful Axiom submission is not
 * completion: reconciliation must prove the committed plan is present in world
 * state before the core session publishes it to undo history.</p>
 */
public final class AxiomPreparedMutationSession implements AutoCloseable {
    private static final ExecutionBudget LEGACY_UNBOUNDED_BUDGET = new ExecutionBudget(
            Duration.ofSeconds(30), Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);

    private final PreparedMutationSession core;
    private final BudgetedPreparedMutationDispatcher budgetedDispatcher;
    private final AxiomClientWorldStateSource worldSource;
    private final CancellationToken cancellationToken;
    private boolean dispatchStarted;

    public AxiomPreparedMutationSession(
            AxiomClientServices services,
            ClientWorld world,
            PreparedMaterialMutation prepared,
            HistoryTimeline timeline,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(prepared, "prepared");
        this.core = new PreparedMutationSession(prepared, Objects.requireNonNull(timeline, "timeline"));
        AxiomChunkMutationDispatcher dispatcher = new AxiomChunkMutationDispatcher(services, world);
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
        this.budgetedDispatcher = new BudgetedPreparedMutationDispatcher(
                prepared.changeSet(),
                new AxiomBudgetedChunkDispatchTarget(dispatcher),
                cancellationToken
        );
        this.worldSource = new AxiomClientWorldStateSource(world);
    }

    public synchronized OperationLifecycle lifecycle() {
        return core.lifecycle();
    }

    /** Dispatches at most one execution-budget slice and preserves the cursor for the next call. */
    public synchronized BudgetedDispatchSlice dispatchSlice(ExecutionBudget budget) throws IOException {
        Objects.requireNonNull(budget, "budget");
        ensureDispatchStarted();
        BudgetedDispatchSlice result = budgetedDispatcher.dispatchSlice(budget);
        switch (result.state()) {
            case YIELDED, EXHAUSTED -> { }
            case CANCELLED -> core.noteCancellationRequested();
            case CONFLICT -> core.reconcile(worldSource);
            case BUDGET_EXCEEDED -> core.fail("Mutation dispatch budget exceeded: " + result.detail());
        }
        return result;
    }

    /**
     * Compatibility path for early callers. New tools should call {@link #dispatchSlice(ExecutionBudget)}
     * from their tick/frame scheduler instead of dispatching the whole plan in one call.
     */
    @Deprecated
    public synchronized AxiomPreparedDispatchResult dispatch() throws IOException {
        long chunks = 0;
        long blocks = 0;
        while (true) {
            BudgetedDispatchSlice slice = dispatchSlice(LEGACY_UNBOUNDED_BUDGET);
            chunks = slice.totalVisitedChunks();
            blocks = slice.totalDispatchedBlocks();
            switch (slice.state()) {
                case YIELDED -> { continue; }
                case EXHAUSTED -> {
                    return new AxiomPreparedDispatchResult(
                            AxiomPreparedDispatchResult.State.DISPATCHED,
                            chunks, blocks, null, null, null);
                }
                case CANCELLED -> {
                    return new AxiomPreparedDispatchResult(
                            AxiomPreparedDispatchResult.State.CANCELLED,
                            chunks, blocks, null, null, null);
                }
                case CONFLICT -> {
                    return new AxiomPreparedDispatchResult(
                            AxiomPreparedDispatchResult.State.CONFLICT,
                            chunks, blocks,
                            slice.conflictX(), slice.conflictY(), slice.conflictZ());
                }
                case BUDGET_EXCEEDED -> throw new IllegalStateException(slice.detail());
            }
        }
    }

    public synchronized PreparedReconciliationReport reconcile() throws IOException {
        if (!dispatchStarted) throw new IllegalStateException("Cannot reconcile before dispatch");
        if (cancellationToken.isCancellationRequested()
                && !core.lifecycle().state().isTerminal()) {
            core.noteCancellationRequested();
        }
        PreparedReconciliationReport report = core.reconcile(worldSource);
        if (core.lifecycle().state().isTerminal()) {
            budgetedDispatcher.close();
        }
        return report;
    }

    @Override
    public synchronized void close() throws IOException {
        budgetedDispatcher.close();
        core.close();
    }

    private void ensureDispatchStarted() {
        if (core.lifecycle().state().isTerminal()) {
            throw new IllegalStateException("Prepared mutation session is terminal: " + core.lifecycle().state());
        }
        if (!dispatchStarted) {
            core.startDispatch();
            dispatchStarted = true;
        }
    }
}
