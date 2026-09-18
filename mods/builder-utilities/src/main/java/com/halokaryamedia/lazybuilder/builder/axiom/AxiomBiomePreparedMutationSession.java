package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntimeMetrics;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedPreparedMutationDispatcher;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;
import com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionReconciler;
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
 * Mixed block + BIOME + ENTITY lifecycle owner.
 *
 * <p>Forward dependency order is blocks → biomes → entities. Cancellation rollback
 * runs entities → biomes → blocks. Biome state is reconciled from the client world;
 * entity batches are authoritative compare-and-set operations with stable Builder markers.</p>
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
    private final AxiomMixedExtensionSupport.Plan extensionPlan;
    private final BuilderRuntimeMetrics metrics;

    private long forwardBiomeObserved;
    private long forwardEntityObserved;
    private long rollbackBiomeObserved;
    private long rollbackEntityObserved;

    private BudgetedPreparedMutationDispatcher blockDispatcher;
    private AxiomBiomeBatchDispatcher biomeDispatcher;
    private AxiomEntityBatchDispatcher entityDispatcher;
    private StoredChangeSet rollbackBlocks;
    private BudgetedPreparedMutationDispatcher rollbackBlockDispatcher;
    private OperationLifecycle lifecycle;
    private Phase phase;
    private boolean ownershipTransferred;
    private boolean disposed;

    public AxiomBiomePreparedMutationSession(
            AxiomClientServices services,
            ClientWorld world,
            PreparedBlockMutation prepared,
            HistoryTimeline timeline,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            CancellationToken cancellation,
            BuilderRuntimeMetrics metrics
    ) throws IOException {
        Objects.requireNonNull(services, "services");
        this.world = Objects.requireNonNull(world, "world");
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.history = Objects.requireNonNull(history, "history");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (prepared.changeSet().extensionCount() <= 0) {
            throw new IllegalArgumentException("Mixed session requires extension frames");
        }
        if (estimatedHistoryBytes <= 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be > 0");
        }
        this.estimatedHistoryBytes = estimatedHistoryBytes;
        this.extensionPlan = AxiomMixedExtensionSupport.inspect(prepared.changeSet());

        var capabilities = com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking.capabilities();
        if (extensionPlan.hasBiomes() && !capabilities.supportsBiome()) {
            throw new IllegalStateException("Server does not advertise Builder BIOME authority");
        }
        if (extensionPlan.hasEntities() && !capabilities.supportsEntity()) {
            throw new IllegalStateException("Server does not advertise Builder ENTITY authority");
        }

        this.worldBlocks = new AxiomClientWorldStateSource(world);
        this.blockTarget = new AxiomBudgetedChunkDispatchTarget(
                new AxiomChunkMutationDispatcher(services, world));
        if (prepared.plannedChanges() > 0) {
            this.blockDispatcher = new BudgetedPreparedMutationDispatcher(
                    prepared.changeSet(), blockTarget, cancellation);
            this.phase = Phase.FORWARD_BLOCKS;
        } else {
            startNextForwardExtension();
        }

        long total = Math.addExact(prepared.plannedChanges(), extensionPlan.total());
        this.lifecycle = OperationLifecycle.created(total)
                .transitionTo(OperationState.VALIDATING)
                .transitionTo(OperationState.PLANNING)
                .transitionTo(OperationState.QUEUED)
                .transitionTo(OperationState.RUNNING);
    }

    public synchronized OperationLifecycle lifecycle() { return lifecycle; }

    public synchronized AxiomMixedPumpResult pump(ExecutionBudget budget) throws IOException {
        ensureOwned();
        Objects.requireNonNull(budget, "budget");
        if (cancellation.isCancellationRequested()
                && phase != Phase.ROLLBACK_ENTITIES
                && phase != Phase.ROLLBACK_BIOMES
                && phase != Phase.ROLLBACK_BLOCKS
                && phase != Phase.ROLLBACK_RECONCILE) {
            beginRollback();
        }
        return switch (phase) {
            case FORWARD_BLOCKS -> pumpForwardBlocks(budget);
            case FORWARD_BIOMES -> pumpForwardBiomes();
            case FORWARD_ENTITIES -> pumpForwardEntities();
            case FORWARD_RECONCILE -> pumpForwardReconcile();
            case ROLLBACK_ENTITIES -> pumpRollbackEntities();
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
        try { closeTransient(); } catch (IOException e) { failure = e; }
        try { prepared.close(); disposed = true; } catch (IOException e) {
            if (failure == null) failure = e; else failure.addSuppressed(e);
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
                startNextForwardExtension();
                yield running("blocks dispatched");
            }
            case CANCELLED -> {
                beginRollback();
                yield running("cancellation requested");
            }
            case CONFLICT -> {
                metrics.conflict();
                yield fail("block dispatch conflict");
            }
            case BUDGET_EXCEEDED -> {
                metrics.budgetExceeded();
                yield fail("block dispatch budget exceeded: " + slice.detail());
            }
        };
    }

    private AxiomMixedPumpResult pumpForwardBiomes() throws IOException {
        BiomeBatchDispatchProgress progress = biomeDispatcher.pump();
        forwardBiomeObserved = recordDelta(
                progress.processedExtensions(),
                forwardBiomeObserved,
                metrics::recordForwardBiomeExtensions);
        setProcessed(Math.min(
                lifecycle.totalWork(),
                prepared.plannedChanges() + progress.processedExtensions()));
        return switch (progress.state()) {
            case YIELDED -> running("biome batch sent");
            case WAITING -> waiting("waiting for authoritative biome response");
            case EXHAUSTED -> {
                biomeDispatcher.close();
                biomeDispatcher = null;
                if (extensionPlan.hasEntities()) {
                    entityDispatcher = new AxiomEntityBatchDispatcher(
                            prepared.changeSet(), world, cancellation, false);
                    phase = Phase.FORWARD_ENTITIES;
                    yield running("biomes applied; starting entity batches");
                }
                phase = Phase.FORWARD_RECONCILE;
                yield running("biomes applied; reconciling");
            }
            case CANCELLED -> {
                beginRollback();
                yield running("cancellation requested");
            }
            case CONFLICT -> {
                metrics.extensionConflict();
                yield fail("biome dispatch " + progress.state() + ": " + progress.detail());
            }
            case FAILED -> {
                metrics.extensionFailure();
                yield fail("biome dispatch " + progress.state() + ": " + progress.detail());
            }
        };
    }

    private AxiomMixedPumpResult pumpForwardEntities() throws IOException {
        EntityBatchDispatchProgress progress = entityDispatcher.pump();
        forwardEntityObserved = recordDelta(
                progress.processedExtensions(),
                forwardEntityObserved,
                metrics::recordForwardEntityExtensions);
        long base = Math.addExact(prepared.plannedChanges(), extensionPlan.biomes());
        setProcessed(Math.min(lifecycle.totalWork(), base + progress.processedExtensions()));
        return switch (progress.state()) {
            case YIELDED -> running("entity batch sent");
            case WAITING -> waiting("waiting for authoritative entity response");
            case EXHAUSTED -> {
                entityDispatcher.close();
                entityDispatcher = null;
                phase = Phase.FORWARD_RECONCILE;
                yield running("entities applied; reconciling");
            }
            case CANCELLED -> {
                beginRollback();
                yield running("cancellation requested");
            }
            case CONFLICT -> {
                metrics.extensionConflict();
                yield fail("entity dispatch " + progress.state() + ": " + progress.detail());
            }
            case FAILED -> {
                metrics.extensionFailure();
                yield fail("entity dispatch " + progress.state() + ": " + progress.detail());
            }
        };
    }

    private AxiomMixedPumpResult pumpForwardReconcile() throws IOException {
        ReconciliationState blocks = PreparedMutationReconciler
                .reconcile(prepared.changeSet(), worldBlocks).state();
        if (blocks == ReconciliationState.CONFLICT) {
            metrics.conflict();
            return fail("block reconciliation conflict");
        }
        if (blocks != ReconciliationState.FULLY_APPLIED && blocks != ReconciliationState.EMPTY) {
            return waiting("waiting for Axiom block application");
        }

        if (extensionPlan.hasBiomes()) {
            ReconciliationState biomes = HistoryExtensionReconciler.reconcile(
                    prepared.changeSet(), new AxiomBiomeExtensionReadTarget(world)).state();
            if (biomes == ReconciliationState.CONFLICT) {
                metrics.extensionConflict();
                return fail("biome reconciliation conflict");
            }
            if (biomes != ReconciliationState.FULLY_APPLIED && biomes != ReconciliationState.EMPTY) {
                return waiting("waiting for biome reconciliation");
            }
        }

        setProcessed(lifecycle.totalWork());
        lifecycle = lifecycle.transitionTo(OperationState.COMMITTING);
        timeline.record(prepared.changeSet());
        ownershipTransferred = true;
        lifecycle = lifecycle.transitionTo(OperationState.COMPLETED);
        phase = Phase.TERMINAL;
        return new AxiomMixedPumpResult(AxiomMixedPumpResult.State.COMPLETED, "mixed mutation completed");
    }

    private void startNextForwardExtension() throws IOException {
        if (extensionPlan.hasBiomes()) {
            biomeDispatcher = new AxiomBiomeBatchDispatcher(
                    prepared.changeSet(), world, cancellation, false);
            phase = Phase.FORWARD_BIOMES;
        } else if (extensionPlan.hasEntities()) {
            entityDispatcher = new AxiomEntityBatchDispatcher(
                    prepared.changeSet(), world, cancellation, false);
            phase = Phase.FORWARD_ENTITIES;
        } else {
            phase = Phase.FORWARD_RECONCILE;
        }
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
            biomeDispatcher = null;
        }
        if (entityDispatcher != null) {
            entityDispatcher.close();
            entityDispatcher = null;
        }

        if (extensionPlan.hasEntities()) {
            entityDispatcher = new AxiomEntityBatchDispatcher(
                    prepared.changeSet(), world, () -> false, true);
            phase = Phase.ROLLBACK_ENTITIES;
        } else if (extensionPlan.hasBiomes()) {
            biomeDispatcher = new AxiomBiomeBatchDispatcher(
                    prepared.changeSet(), world, () -> false, true);
            phase = Phase.ROLLBACK_BIOMES;
        } else {
            startRollbackBlocksOrReconcile();
        }
    }

    private AxiomMixedPumpResult pumpRollbackEntities() throws IOException {
        EntityBatchDispatchProgress progress = entityDispatcher.pump();
        rollbackEntityObserved = recordDelta(
                progress.processedExtensions(),
                rollbackEntityObserved,
                metrics::recordRollbackEntityExtensions);
        return switch (progress.state()) {
            case YIELDED -> running("rollback entity batch sent");
            case WAITING -> waiting("waiting for entity rollback response");
            case EXHAUSTED -> {
                entityDispatcher.close();
                entityDispatcher = null;
                if (extensionPlan.hasBiomes()) {
                    biomeDispatcher = new AxiomBiomeBatchDispatcher(
                            prepared.changeSet(), world, () -> false, true);
                    phase = Phase.ROLLBACK_BIOMES;
                } else {
                    startRollbackBlocksOrReconcile();
                }
                yield running("entities rolled back");
            }
            case CANCELLED -> throw new IllegalStateException("rollback entity dispatcher cannot be cancelled");
            case CONFLICT -> {
                metrics.extensionConflict();
                yield fail("entity rollback " + progress.state() + ": " + progress.detail());
            }
            case FAILED -> {
                metrics.extensionFailure();
                yield fail("entity rollback " + progress.state() + ": " + progress.detail());
            }
        };
    }

    private AxiomMixedPumpResult pumpRollbackBiomes() throws IOException {
        BiomeBatchDispatchProgress progress = biomeDispatcher.pump();
        rollbackBiomeObserved = recordDelta(
                progress.processedExtensions(),
                rollbackBiomeObserved,
                metrics::recordRollbackBiomeExtensions);
        return switch (progress.state()) {
            case YIELDED -> running("rollback biome batch sent");
            case WAITING -> waiting("waiting for biome rollback response");
            case EXHAUSTED -> {
                biomeDispatcher.close();
                biomeDispatcher = null;
                startRollbackBlocksOrReconcile();
                yield running("biomes rolled back");
            }
            case CANCELLED -> throw new IllegalStateException("rollback biome dispatcher cannot be cancelled");
            case CONFLICT -> {
                metrics.extensionConflict();
                yield fail("biome rollback " + progress.state() + ": " + progress.detail());
            }
            case FAILED -> {
                metrics.extensionFailure();
                yield fail("biome rollback " + progress.state() + ": " + progress.detail());
            }
        };
    }

    private void startRollbackBlocksOrReconcile() throws IOException {
        if (prepared.plannedChanges() == 0) {
            phase = Phase.ROLLBACK_RECONCILE;
            return;
        }
        rollbackBlocks = StoredChangeSetReverser.reverseBlocks(
                prepared.changeSet(),
                history,
                estimatedHistoryBytes,
                prepared.changeSet().operationId() + "-mixed-cancel");
        rollbackBlockDispatcher = new BudgetedPreparedMutationDispatcher(
                rollbackBlocks, blockTarget, () -> false);
        phase = Phase.ROLLBACK_BLOCKS;
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
            case CANCELLED -> throw new IllegalStateException("rollback block dispatcher cannot be cancelled");
            case CONFLICT -> {
                metrics.conflict();
                yield fail("block rollback conflict");
            }
            case BUDGET_EXCEEDED -> {
                metrics.budgetExceeded();
                yield fail("block rollback budget exceeded: " + slice.detail());
            }
        };
    }

    private AxiomMixedPumpResult pumpRollbackReconcile() throws IOException {
        if (rollbackBlocks != null) {
            ReconciliationState blocks = PreparedMutationReconciler
                    .reconcile(rollbackBlocks, worldBlocks).state();
            if (blocks == ReconciliationState.CONFLICT) {
                metrics.conflict();
                return fail("block rollback reconciliation conflict");
            }
            if (blocks != ReconciliationState.FULLY_APPLIED && blocks != ReconciliationState.EMPTY) {
                return waiting("waiting for block rollback");
            }
            rollbackBlocks.close();
            rollbackBlocks = null;
        }

        if (extensionPlan.hasBiomes()) {
            ReconciliationState biomes = HistoryExtensionReconciler.reconcile(
                    prepared.changeSet(), new AxiomBiomeExtensionReadTarget(world)).state();
            if (biomes == ReconciliationState.CONFLICT) {
                metrics.extensionConflict();
                return fail("biome rollback reconciliation conflict");
            }
            if (biomes != ReconciliationState.NOT_APPLIED && biomes != ReconciliationState.EMPTY) {
                return waiting("waiting for biome rollback reconciliation");
            }
        }

        prepared.close();
        disposed = true;
        lifecycle = lifecycle.transitionTo(OperationState.CANCELLED);
        phase = Phase.TERMINAL;
        return new AxiomMixedPumpResult(AxiomMixedPumpResult.State.CANCELLED, "mixed mutation rolled back");
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
            case COMPLETED -> new AxiomMixedPumpResult(AxiomMixedPumpResult.State.COMPLETED, "completed");
            case CANCELLED -> new AxiomMixedPumpResult(AxiomMixedPumpResult.State.CANCELLED, "cancelled");
            case FAILED -> new AxiomMixedPumpResult(AxiomMixedPumpResult.State.FAILED, lifecycle.failureMessage());
            default -> throw new IllegalStateException("TERMINAL phase has non-terminal lifecycle " + lifecycle.state());
        };
    }

    private void setProcessed(long processed) {
        if (lifecycle.state() != OperationState.RUNNING) return;
        lifecycle = new OperationLifecycle(
                lifecycle.state(),
                Math.max(lifecycle.processedWork(), processed),
                lifecycle.totalWork(),
                null);
    }

    private void closeTransient() throws IOException {
        if (blockDispatcher != null) { blockDispatcher.close(); blockDispatcher = null; }
        if (biomeDispatcher != null) { biomeDispatcher.close(); biomeDispatcher = null; }
        if (entityDispatcher != null) { entityDispatcher.close(); entityDispatcher = null; }
        if (rollbackBlockDispatcher != null) { rollbackBlockDispatcher.close(); rollbackBlockDispatcher = null; }
        if (rollbackBlocks != null) { rollbackBlocks.close(); rollbackBlocks = null; }
    }

    private static long recordDelta(
            long current,
            long previous,
            java.util.function.LongConsumer recorder
    ) {
        if (current < previous) {
            throw new IllegalStateException(
                    "extension progress regressed from " + previous + " to " + current);
        }
        long delta = current - previous;
        if (delta > 0) recorder.accept(delta);
        return current;
    }

    private void ensureOwned() {
        if (ownershipTransferred || disposed) {
            throw new IllegalStateException("Mixed mutation ownership was released");
        }
    }

    private enum Phase {
        FORWARD_BLOCKS,
        FORWARD_BIOMES,
        FORWARD_ENTITIES,
        FORWARD_RECONCILE,
        ROLLBACK_ENTITIES,
        ROLLBACK_BIOMES,
        ROLLBACK_BLOCKS,
        ROLLBACK_RECONCILE,
        TERMINAL
    }
}
