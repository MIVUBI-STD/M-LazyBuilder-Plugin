package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedPreparedMutationDispatcher;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import com.halokaryamedia.lazybuilder.builder.mutation.StoredChangeSetReverser;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/**
 * Mixed block + BIOME lifecycle owner.
 *
 * <p>Forward dependency order is blocks then biomes. Cancellation rollback is
 * biomes then blocks. Paper batch responses are authoritative for biome writes;
 * block completion is reconciled against the client world before timeline publish.</p>
 */
public final class AxiomBiomePreparedMutationSession implements AutoCloseable {
    private final PreparedBlockMutation prepared;
    private final HistoryTimeline timeline;
    private final HistoryStorageRouter history;
    private final long estimatedHistoryBytes;
    private final ClientWorld world;
    private final AxiomClientWorldStateSource worldBlocks;
    private final CancellationToken cancellation;
    private final AxiomBudgetedChunkDispatchTarget blockTarget;

    private BudgetedPreparedMutationDispatcher blockDispatcher;
    private AxiomBiomeBatchDispatcher biomeDispatcher;
    private StoredChangeSet rollbackBlocks;
    private BudgetedPreparedMutationDispatcher rollbackBlockDispatcher;
    private OperationLifecycle lifecycle;
    private Phase phase = Phase.FORWARD_BLOCKS;
    private boolean ownershipTransferred;
    private boolean disposed;

    public AxiomBiomePreparedMutationSession(
            AxiomClientServices services,
            ClientWorld world,
            PreparedBlockMutation prepared,
            HistoryTimeline timeline,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(services, "services");
        this.world = Objects.requireNonNull(world, "world");
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.history = Objects.requireNonNull(history, "history");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        if (prepared.changeSet().extensionCount() <= 0) {
            throw new IllegalArgumentException("Mixed BIOME session requires extension frames");
        }
        if (estimatedHistoryBytes <= 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be > 0");
        }
        this.estimatedHistoryBytes = estimatedHistoryBytes;
        requireBiomeOnly(prepared.changeSet());

        this.worldBlocks = new AxiomClientWorldStateSource(world);
        this.blockTarget = new AxiomBudgetedChunkDispatchTarget(
                new AxiomChunkMutationDispatcher(services, world));
        this.blockDispatcher = new BudgetedPreparedMutationDispatcher(
                prepared.changeSet(), blockTarget, cancellation);

        long total = Math.addExact(
                prepared.plannedChanges(), prepared.changeSet().extensionCount());
        this.lifecycle = OperationLifecycle.created(total)
                .transitionTo(OperationState.VALIDATING)
                .transitionTo(OperationState.PLANNING)
                .transitionTo(OperationState.QUEUED)
                .transitionTo(OperationState.RUNNING);
    }

    public synchronized OperationLifecycle lifecycle() {
        return lifecycle;
    }

    public synchronized AxiomMixedPumpResult pump(ExecutionBudget budget) throws IOException {
        ensureOwned();
        Objects.requireNonNull(budget, "budget");

        if (cancellation.isCancellationRequested()
                && phase != Phase.ROLLBACK_BIOMES
                && phase != Phase.ROLLBACK_BLOCKS
                && phase != Phase.ROLLBACK_RECONCILE) {
            beginRollback();
        }

        return switch (phase) {
            case FORWARD_BLOCKS -> pumpForwardBlocks(budget);
            case FORWARD_BIOMES -> pumpForwardBiomes();
            case FORWARD_RECONCILE -> pumpForwardReconcile();
            case ROLLBACK_BIOMES -> pumpRollbackBiomes();
            case ROLLBACK_BLOCKS -> pumpRollbackBlocks(budget);
            case ROLLBACK_RECONCILE -> pumpRollbackReconcile();
            case TERMINAL -> terminalResult();
        };
    }

    public synchronized boolean preserveForRecovery() throws IOException {
        ensureOwned();
        closeTransient();
        boolean preserved = prepared.changeSet().preserveForRecovery();
        if (preserved) ownershipTransferred = true;
        return preserved;
    }

    @Override
    public synchronized void close() throws IOException {
        if (ownershipTransferred || disposed) return;
        IOException failure = null;
        try { closeTransient(); }
        catch (IOException e) { failure = e; }
        try { prepared.close(); disposed = true; }
        catch (IOException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }

    private AxiomMixedPumpResult pumpForwardBlocks(ExecutionBudget budget) throws IOException {
        BudgetedDispatchSlice slice = blockDispatcher.dispatchSlice(budget);
        setProcessed(Math.min(prepared.plannedChanges(), blockDispatcher.totalProcessedBlocks()));
        return switch (slice.state()) {
            case YIELDED -> running("dispatching blocks");
            case EXHAUSTED -> {
                blockDispatcher.close();
                blockDispatcher = null;
                biomeDispatcher = new AxiomBiomeBatchDispatcher(
                        prepared.changeSet(), world, cancellation, false);
                phase = Phase.FORWARD_BIOMES;
                yield running("blocks dispatched; starting biome batches");
            }
            case CANCELLED -> {
                beginRollback();
                yield running("cancellation requested");
            }
            case CONFLICT -> fail("block dispatch conflict");
            case BUDGET_EXCEEDED -> fail(
                    "block dispatch budget exceeded: " + slice.detail());
        };
    }

    private AxiomMixedPumpResult pumpForwardBiomes() throws IOException {
        BiomeBatchDispatchProgress progress = biomeDispatcher.pump();
        setProcessed(Math.min(
                lifecycle.totalWork(),
                prepared.plannedChanges() + progress.processedExtensions()));
        return switch (progress.state()) {
            case YIELDED -> running("biome batch sent");
            case WAITING -> waiting("waiting for authoritative biome response");
            case EXHAUSTED -> {
                biomeDispatcher.close();
                biomeDispatcher = null;
                phase = Phase.FORWARD_RECONCILE;
                yield running("biomes applied; reconciling blocks");
            }
            case CANCELLED -> {
                beginRollback();
                yield running("cancellation requested");
            }
            case CONFLICT, FAILED -> fail(
                    "biome dispatch " + progress.state() + ": " + progress.detail());
        };
    }

    private AxiomMixedPumpResult pumpForwardReconcile() throws IOException {
        ReconciliationState blocks =
                PreparedMutationReconciler.reconcile(prepared.changeSet(), worldBlocks).state();
        if (blocks == ReconciliationState.CONFLICT) {
            return fail("block reconciliation conflict");
        }
        if (blocks != ReconciliationState.FULLY_APPLIED
                && blocks != ReconciliationState.EMPTY) {
            return waiting("waiting for Axiom block application");
        }

        setProcessed(lifecycle.totalWork());
        lifecycle = lifecycle.transitionTo(OperationState.COMMITTING);
        timeline.record(prepared.changeSet());
        ownershipTransferred = true;
        lifecycle = lifecycle.transitionTo(OperationState.COMPLETED);
        phase = Phase.TERMINAL;
        return new AxiomMixedPumpResult(
                AxiomMixedPumpResult.State.COMPLETED, "mixed mutation completed");
    }

    private void beginRollback() throws IOException {
        if (lifecycle.state() == OperationState.RUNNING) {
            lifecycle = lifecycle.transitionTo(OperationState.CANCELLING);
        }
        if (blockDispatcher != null) {
            blockDispatcher.close();
            blockDispatcher = null;
        }
        if (biomeDispatcher != null) {
            biomeDispatcher.close();
        }
        biomeDispatcher = new AxiomBiomeBatchDispatcher(
                prepared.changeSet(), world, () -> false, true);
        phase = Phase.ROLLBACK_BIOMES;
    }

    private AxiomMixedPumpResult pumpRollbackBiomes() throws IOException {
        BiomeBatchDispatchProgress progress = biomeDispatcher.pump();
        return switch (progress.state()) {
            case YIELDED -> running("rollback biome batch sent");
            case WAITING -> waiting("waiting for biome rollback response");
            case EXHAUSTED -> {
                biomeDispatcher.close();
                biomeDispatcher = null;
                if (prepared.plannedChanges() == 0) {
                    phase = Phase.ROLLBACK_RECONCILE;
                } else {
                    rollbackBlocks = StoredChangeSetReverser.reverseBlocks(
                            prepared.changeSet(),
                            history,
                            estimatedHistoryBytes,
                            prepared.changeSet().operationId() + "-mixed-cancel");
                    rollbackBlockDispatcher = new BudgetedPreparedMutationDispatcher(
                            rollbackBlocks, blockTarget, () -> false);
                    phase = Phase.ROLLBACK_BLOCKS;
                }
                yield running("biomes rolled back");
            }
            case CANCELLED -> throw new IllegalStateException(
                    "rollback biome dispatcher cannot be cancelled");
            case CONFLICT, FAILED -> fail(
                    "biome rollback " + progress.state() + ": " + progress.detail());
        };
    }

    private AxiomMixedPumpResult pumpRollbackBlocks(ExecutionBudget budget) throws IOException {
        BudgetedDispatchSlice slice = rollbackBlockDispatcher.dispatchSlice(budget);
        return switch (slice.state()) {
            case YIELDED -> running("rolling back blocks");
            case EXHAUSTED -> {
                rollbackBlockDispatcher.close();
                rollbackBlockDispatcher = null;
                phase = Phase.ROLLBACK_RECONCILE;
                yield running("block rollback dispatched");
            }
            case CANCELLED -> throw new IllegalStateException(
                    "rollback block dispatcher cannot be cancelled");
            case CONFLICT -> fail("block rollback conflict");
            case BUDGET_EXCEEDED -> fail(
                    "block rollback budget exceeded: " + slice.detail());
        };
    }

    private AxiomMixedPumpResult pumpRollbackReconcile() throws IOException {
        if (rollbackBlocks != null) {
            ReconciliationState blocks =
                    PreparedMutationReconciler.reconcile(rollbackBlocks, worldBlocks).state();
            if (blocks == ReconciliationState.CONFLICT) {
                return fail("block rollback reconciliation conflict");
            }
            if (blocks != ReconciliationState.FULLY_APPLIED
                    && blocks != ReconciliationState.EMPTY) {
                return waiting("waiting for block rollback");
            }
            rollbackBlocks.close();
            rollbackBlocks = null;
        }

        prepared.close();
        disposed = true;
        lifecycle = lifecycle.transitionTo(OperationState.CANCELLED);
        phase = Phase.TERMINAL;
        return new AxiomMixedPumpResult(
                AxiomMixedPumpResult.State.CANCELLED, "mixed mutation rolled back");
    }

    private AxiomMixedPumpResult fail(String detail) {
        if (!lifecycle.state().isTerminal()) {
            lifecycle = lifecycle.fail(detail == null ? "mixed mutation failed" : detail);
        }
        phase = Phase.TERMINAL;
        return new AxiomMixedPumpResult(AxiomMixedPumpResult.State.FAILED, detail);
    }

    private AxiomMixedPumpResult running(String detail) {
        return new AxiomMixedPumpResult(AxiomMixedPumpResult.State.RUNNING, detail);
    }

    private AxiomMixedPumpResult waiting(String detail) {
        return new AxiomMixedPumpResult(AxiomMixedPumpResult.State.WAITING, detail);
    }

    private AxiomMixedPumpResult terminalResult() {
        return switch (lifecycle.state()) {
            case COMPLETED -> new AxiomMixedPumpResult(
                    AxiomMixedPumpResult.State.COMPLETED, "completed");
            case CANCELLED -> new AxiomMixedPumpResult(
                    AxiomMixedPumpResult.State.CANCELLED, "cancelled");
            case FAILED -> new AxiomMixedPumpResult(
                    AxiomMixedPumpResult.State.FAILED, lifecycle.failureMessage());
            default -> throw new IllegalStateException(
                    "TERMINAL phase has non-terminal lifecycle " + lifecycle.state());
        };
    }

    private void setProcessed(long processed) {
        if (lifecycle.state() != OperationState.RUNNING) return;
        lifecycle = new OperationLifecycle(
                lifecycle.state(),
                Math.max(lifecycle.processedWork(), processed),
                lifecycle.totalWork(),
                null
        );
    }

    private void closeTransient() throws IOException {
        if (blockDispatcher != null) {
            blockDispatcher.close();
            blockDispatcher = null;
        }
        if (biomeDispatcher != null) {
            biomeDispatcher.close();
            biomeDispatcher = null;
        }
        if (rollbackBlockDispatcher != null) {
            rollbackBlockDispatcher.close();
            rollbackBlockDispatcher = null;
        }
        if (rollbackBlocks != null) {
            rollbackBlocks.close();
            rollbackBlocks = null;
        }
    }

    private void ensureOwned() {
        if (ownershipTransferred || disposed) {
            throw new IllegalStateException("Mixed mutation ownership was released");
        }
    }

    private static void requireBiomeOnly(StoredChangeSet set) throws IOException {
        set.visitExtensions(frame -> {
            if (!HistoryExtensionTypes.BIOME.equals(frame.typeId())) {
                throw new IOException(
                        "Mixed Axiom session does not support extension type "
                                + frame.typeId());
            }
            return true;
        });
    }

    private enum Phase {
        FORWARD_BLOCKS,
        FORWARD_BIOMES,
        FORWARD_RECONCILE,
        ROLLBACK_BIOMES,
        ROLLBACK_BLOCKS,
        ROLLBACK_RECONCILE,
        TERMINAL
    }
}
