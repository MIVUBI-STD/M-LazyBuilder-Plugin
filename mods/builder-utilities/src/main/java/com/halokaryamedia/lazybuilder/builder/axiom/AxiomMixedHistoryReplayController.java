package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimelineLease;
import com.halokaryamedia.lazybuilder.builder.history.ReplayDirection;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedPreparedMutationDispatcher;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkChangeSetTransforms;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchTarget;
import com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/**
 * Async mixed-history replay for authoritative BLOCK_ENTITY/BIOME/ENTITY extensions.
 *
 * <p>Redo order: regular blocks → block entities → biomes → entities.
 * Undo order: entities → biomes → block entities → regular blocks.</p>
 */
public final class AxiomMixedHistoryReplayController implements AutoCloseable {
    private final BuilderRuntime runtime;
    private final ClientWorld world;
    private final HistoryTimelineLease lease;
    private final AxiomClientWorldStateSource blocks;
    private final ChunkDispatchTarget forwardTarget;
    private final ChunkDispatchTarget reverseTarget;
    private final AxiomMixedExtensionSupport.Plan extensionPlan;

    private BudgetedPreparedMutationDispatcher blockDispatcher;
    private AxiomBlockEntityBatchDispatcher blockEntityDispatcher;
    private AxiomBiomeBatchDispatcher biomeDispatcher;
    private AxiomEntityBatchDispatcher entityDispatcher;
    private Phase phase;
    private String status;
    private boolean finished;
    private boolean redoExtensionsCompleted;
    private long blockEntityObserved;
    private long biomeObserved;
    private long entityObserved;

    private AxiomMixedHistoryReplayController(
            AxiomClientServices services,
            BuilderRuntime runtime,
            ClientWorld world,
            HistoryTimelineLease lease
    ) throws IOException {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.world = Objects.requireNonNull(world, "world");
        this.lease = Objects.requireNonNull(lease, "lease");
        this.extensionPlan = AxiomMixedExtensionSupport.inspect(lease.changeSet());
        if (extensionPlan.total() == 0) {
            throw new IllegalArgumentException(
                    "Block-only history should use Axiom native undo/redo");
        }

        var capabilities =
                com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking.capabilities();
        if (extensionPlan.hasBlockEntities() && !capabilities.supportsBlockEntity()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder BLOCK_ENTITY authority");
        }
        if (extensionPlan.hasBiomes() && !capabilities.supportsBiome()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder BIOME authority");
        }
        if (extensionPlan.hasEntities() && !capabilities.supportsEntity()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder ENTITY authority");
        }

        this.blocks = new AxiomClientWorldStateSource(world);
        ChunkDispatchTarget baseForward = new AxiomBudgetedChunkDispatchTarget(
                new AxiomChunkMutationDispatcher(
                        Objects.requireNonNull(services, "services"), world));
        if (extensionPlan.hasBlockEntities()) {
            this.forwardTarget = new AxiomBlockEntityFilteringChunkDispatchTarget(
                    baseForward,
                    AxiomBlockEntityPositionMask.from(lease.changeSet()));
        } else {
            this.forwardTarget = baseForward;
        }
        this.reverseTarget = chunk -> forwardTarget.dispatch(
                ChunkChangeSetTransforms.reverse(chunk));

        if (lease.direction() == ReplayDirection.UNDO) {
            startUndoExtensions();
        } else if (lease.changeSet().changeCount() > 0) {
            blockDispatcher = new BudgetedPreparedMutationDispatcher(
                    lease.changeSet(), forwardTarget, () -> false);
            phase = Phase.BLOCKS;
            status = "Redoing blocks";
        } else {
            startRedoExtensions();
        }
    }

    public static AxiomMixedHistoryReplayController beginUndo(
            AxiomClientServices services,
            BuilderRuntime runtime,
            ClientWorld world
    ) throws IOException {
        HistoryTimelineLease lease = runtime.timeline().beginUndo()
                .orElseThrow(() -> new IllegalStateException("Nothing to undo"));
        try {
            return new AxiomMixedHistoryReplayController(services, runtime, world, lease);
        } catch (IOException | RuntimeException failure) {
            lease.abort();
            throw failure;
        }
    }

    public static AxiomMixedHistoryReplayController beginRedo(
            AxiomClientServices services,
            BuilderRuntime runtime,
            ClientWorld world
    ) throws IOException {
        HistoryTimelineLease lease = runtime.timeline().beginRedo()
                .orElseThrow(() -> new IllegalStateException("Nothing to redo"));
        try {
            return new AxiomMixedHistoryReplayController(services, runtime, world, lease);
        } catch (IOException | RuntimeException failure) {
            lease.abort();
            throw failure;
        }
    }

    public boolean finished() { return finished; }
    public String status() { return status; }
    public ReplayDirection direction() { return lease.direction(); }

    public void pump() {
        if (finished) return;
        try {
            switch (phase) {
                case BLOCKS -> pumpBlocks();
                case BLOCK_RECONCILE -> reconcileBlocks();
                case BLOCK_ENTITIES -> pumpBlockEntities();
                case BIOMES -> pumpBiomes();
                case ENTITIES -> pumpEntities();
                case DONE -> { }
            }
        } catch (Exception failure) {
            runtime.metrics().historyReplayFailure();
            status = "Replay failed: " + concise(failure);
            lease.abort();
            closeTransports();
            finished = true;
            phase = Phase.DONE;
        }
    }

    @Override
    public void close() {
        if (finished) return;
        lease.abort();
        closeTransports();
        finished = true;
        phase = Phase.DONE;
        status = "Replay aborted";
    }

    private void pumpBlocks() throws IOException {
        BudgetedDispatchSlice slice =
                blockDispatcher.dispatchSlice(runtime.dispatchBudget());
        if (lease.direction() == ReplayDirection.UNDO) {
            runtime.metrics().recordHistoryUndoBlocks(slice.sliceDispatchedBlocks());
        } else {
            runtime.metrics().recordHistoryRedoBlocks(slice.sliceDispatchedBlocks());
        }
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " blocks: " + slice.totalDispatchedBlocks();
        switch (slice.state()) {
            case YIELDED -> { }
            case EXHAUSTED -> {
                blockDispatcher.close();
                blockDispatcher = null;
                if (lease.direction() == ReplayDirection.REDO
                        && extensionPlan.hasBlockEntities()) {
                    startRedoExtensions();
                } else {
                    phase = Phase.BLOCK_RECONCILE;
                }
            }
            case CANCELLED -> throw new IllegalStateException(
                    "History replay cannot be cancelled internally");
            case CONFLICT -> throw new IllegalStateException(
                    "block replay conflict at "
                            + slice.conflictX() + "," + slice.conflictY() + "," + slice.conflictZ());
            case BUDGET_EXCEEDED -> throw new IllegalStateException(slice.detail());
        }
    }

    private void reconcileBlocks() throws IOException {
        ReconciliationState state =
                PreparedMutationReconciler.reconcile(lease.changeSet(), blocks).state();
        ReconciliationState desired = lease.direction() == ReplayDirection.REDO
                ? ReconciliationState.FULLY_APPLIED
                : ReconciliationState.NOT_APPLIED;
        if (state == ReconciliationState.CONFLICT) {
            throw new IllegalStateException("block replay reconciliation conflict");
        }
        if (state != desired && state != ReconciliationState.EMPTY) {
            status = "Waiting for Axiom block replay";
            return;
        }

        if (lease.direction() == ReplayDirection.REDO) {
            if (redoExtensionsCompleted) {
                complete();
            } else {
                startRedoExtensions();
            }
        } else {
            completeAfterUndoBlockReconcile();
        }
    }

    private void startRedoExtensions() throws IOException {
        if (extensionPlan.hasBlockEntities()) {
            blockEntityObserved = 0L;
            blockEntityDispatcher = new AxiomBlockEntityBatchDispatcher(
                    lease.changeSet(), world, () -> false, false);
            phase = Phase.BLOCK_ENTITIES;
            status = "Redoing block entities";
        } else if (extensionPlan.hasBiomes()) {
            biomeObserved = 0L;
            biomeDispatcher = new AxiomBiomeBatchDispatcher(
                    lease.changeSet(), world, () -> false, false);
            phase = Phase.BIOMES;
            status = "Redoing biomes";
        } else if (extensionPlan.hasEntities()) {
            entityObserved = 0L;
            entityDispatcher = new AxiomEntityBatchDispatcher(
                    lease.changeSet(), world, () -> false, false);
            phase = Phase.ENTITIES;
            status = "Redoing entities";
        } else {
            complete();
        }
    }

    private void startUndoExtensions() throws IOException {
        if (extensionPlan.hasEntities()) {
            entityObserved = 0L;
            entityDispatcher = new AxiomEntityBatchDispatcher(
                    lease.changeSet(), world, () -> false, true);
            phase = Phase.ENTITIES;
            status = "Undoing entities";
        } else if (extensionPlan.hasBiomes()) {
            biomeObserved = 0L;
            biomeDispatcher = new AxiomBiomeBatchDispatcher(
                    lease.changeSet(), world, () -> false, true);
            phase = Phase.BIOMES;
            status = "Undoing biomes";
        } else if (extensionPlan.hasBlockEntities()) {
            blockEntityObserved = 0L;
            blockEntityDispatcher = new AxiomBlockEntityBatchDispatcher(
                    lease.changeSet(), world, () -> false, true);
            phase = Phase.BLOCK_ENTITIES;
            status = "Undoing block entities";
        } else {
            startUndoBlocks();
        }
    }


    private void pumpBlockEntities() throws IOException {
        BlockEntityBatchDispatchProgress progress = blockEntityDispatcher.pump();
        long delta = progressDelta(progress.processedExtensions(), blockEntityObserved);
        blockEntityObserved = progress.processedExtensions();
        if (lease.direction() == ReplayDirection.UNDO) {
            runtime.metrics().recordHistoryUndoBlockEntityExtensions(delta);
        } else {
            runtime.metrics().recordHistoryRedoBlockEntityExtensions(delta);
        }
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " block entities: " + progress.processedExtensions();

        switch (progress.state()) {
            case YIELDED, WAITING -> { }
            case EXHAUSTED -> {
                blockEntityDispatcher.close();
                blockEntityDispatcher = null;
                verifyBlockEntityState(
                        lease.direction() == ReplayDirection.REDO
                                ? ReconciliationState.FULLY_APPLIED
                                : ReconciliationState.NOT_APPLIED);
                if (lease.direction() == ReplayDirection.REDO) {
                    if (extensionPlan.hasBiomes()) {
                        biomeObserved = 0L;
                        biomeDispatcher = new AxiomBiomeBatchDispatcher(
                                lease.changeSet(), world, () -> false, false);
                        phase = Phase.BIOMES;
                        status = "Redoing biomes";
                    } else if (extensionPlan.hasEntities()) {
                        entityObserved = 0L;
                        entityDispatcher = new AxiomEntityBatchDispatcher(
                                lease.changeSet(), world, () -> false, false);
                        phase = Phase.ENTITIES;
                        status = "Redoing entities";
                    } else {
                        finishRedoExtensions();
                    }
                } else {
                    startUndoBlocks();
                }
            }
            case CANCELLED -> throw new IllegalStateException(
                    "History replay cannot be cancelled internally");
            case CONFLICT, FAILED -> throw new IllegalStateException(
                    "block-entity replay " + progress.state() + ": " + progress.detail());
        }
    }

    private void pumpBiomes() throws IOException {
        BiomeBatchDispatchProgress progress = biomeDispatcher.pump();
        long delta = progressDelta(progress.processedExtensions(), biomeObserved);
        biomeObserved = progress.processedExtensions();
        if (lease.direction() == ReplayDirection.UNDO) {
            runtime.metrics().recordHistoryUndoBiomeExtensions(delta);
        } else {
            runtime.metrics().recordHistoryRedoBiomeExtensions(delta);
        }
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " biomes: " + progress.processedExtensions();
        switch (progress.state()) {
            case YIELDED, WAITING -> { }
            case EXHAUSTED -> {
                biomeDispatcher.close();
                biomeDispatcher = null;
                verifyBiomeState(
                        lease.direction() == ReplayDirection.REDO
                                ? ReconciliationState.FULLY_APPLIED
                                : ReconciliationState.NOT_APPLIED);
                if (lease.direction() == ReplayDirection.REDO) {
                    if (extensionPlan.hasEntities()) {
                        entityObserved = 0L;
                        entityDispatcher = new AxiomEntityBatchDispatcher(
                                lease.changeSet(), world, () -> false, false);
                        phase = Phase.ENTITIES;
                        status = "Redoing entities";
                    } else {
                        finishRedoExtensions();
                    }
                } else if (lease.direction() == ReplayDirection.UNDO
                        && extensionPlan.hasBlockEntities()) {
                    blockEntityObserved = 0L;
                    blockEntityDispatcher = new AxiomBlockEntityBatchDispatcher(
                            lease.changeSet(), world, () -> false, true);
                    phase = Phase.BLOCK_ENTITIES;
                    status = "Undoing block entities";
                } else {
                    startUndoBlocks();
                }
            }
            case CANCELLED -> throw new IllegalStateException(
                    "History replay cannot be cancelled internally");
            case CONFLICT, FAILED -> throw new IllegalStateException(
                    "biome replay " + progress.state() + ": " + progress.detail());
        }
    }

    private void pumpEntities() throws IOException {
        EntityBatchDispatchProgress progress = entityDispatcher.pump();
        long delta = progressDelta(progress.processedExtensions(), entityObserved);
        entityObserved = progress.processedExtensions();
        if (lease.direction() == ReplayDirection.UNDO) {
            runtime.metrics().recordHistoryUndoEntityExtensions(delta);
        } else {
            runtime.metrics().recordHistoryRedoEntityExtensions(delta);
        }
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " entities: " + progress.processedExtensions();
        switch (progress.state()) {
            case YIELDED, WAITING -> { }
            case EXHAUSTED -> {
                entityDispatcher.close();
                entityDispatcher = null;
                if (lease.direction() == ReplayDirection.REDO) {
                    finishRedoExtensions();
                } else if (extensionPlan.hasBiomes()) {
                    biomeObserved = 0L;
                    biomeDispatcher = new AxiomBiomeBatchDispatcher(
                            lease.changeSet(), world, () -> false, true);
                    phase = Phase.BIOMES;
                    status = "Undoing biomes";
                } else if (extensionPlan.hasBlockEntities()) {
                    blockEntityObserved = 0L;
                    blockEntityDispatcher = new AxiomBlockEntityBatchDispatcher(
                            lease.changeSet(), world, () -> false, true);
                    phase = Phase.BLOCK_ENTITIES;
                    status = "Undoing block entities";
                } else {
                    startUndoBlocks();
                }
            }
            case CANCELLED -> throw new IllegalStateException(
                    "History replay cannot be cancelled internally");
            case CONFLICT, FAILED -> throw new IllegalStateException(
                    "entity replay " + progress.state() + ": " + progress.detail());
        }
    }


    private void finishRedoExtensions() {
        redoExtensionsCompleted = true;
        if (lease.changeSet().changeCount() == 0) {
            complete();
        } else {
            phase = Phase.BLOCK_RECONCILE;
            status = "Reconciling final mixed block state";
        }
    }

    private void startUndoBlocks() {
        if (lease.changeSet().changeCount() == 0) {
            complete();
            return;
        }
        blockDispatcher = new BudgetedPreparedMutationDispatcher(
                lease.changeSet(), reverseTarget, () -> false);
        phase = Phase.BLOCKS;
        status = "Undoing blocks";
    }

    private void completeAfterUndoBlockReconcile() throws IOException {
        if (extensionPlan.hasBlockEntities()) {
            verifyBlockEntityState(ReconciliationState.NOT_APPLIED);
        }
        if (extensionPlan.hasBiomes()) {
            verifyBiomeState(ReconciliationState.NOT_APPLIED);
        }
        complete();
    }


    private void verifyBlockEntityState(ReconciliationState expected) throws IOException {
        if (!extensionPlan.hasBlockEntities()) return;
        ReconciliationState state = HistoryExtensionReconciler.reconcile(
                lease.changeSet(),
                new AxiomBlockEntityExtensionReadTarget(world)).state();
        if (state == ReconciliationState.CONFLICT) {
            throw new IllegalStateException("block-entity replay reconciliation conflict");
        }
        if (state != expected && state != ReconciliationState.EMPTY) {
            throw new IllegalStateException(
                    "block-entity replay expected " + expected + " but found " + state);
        }
    }

    private void verifyBiomeState(ReconciliationState expected) throws IOException {
        if (!extensionPlan.hasBiomes()) return;
        ReconciliationState state = HistoryExtensionReconciler.reconcile(
                lease.changeSet(), new AxiomBiomeExtensionReadTarget(world)).state();
        if (state == ReconciliationState.CONFLICT) {
            throw new IllegalStateException("biome replay reconciliation conflict");
        }
        if (state != expected && state != ReconciliationState.EMPTY) {
            throw new IllegalStateException(
                    "biome replay expected " + expected + " but found " + state);
        }
    }

    private void complete() {
        lease.complete();
        closeTransports();
        finished = true;
        phase = Phase.DONE;
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " completed";
    }

    private void closeTransports() {
        if (blockDispatcher != null) {
            blockDispatcher.close();
            blockDispatcher = null;
        }
        if (blockEntityDispatcher != null) {
            blockEntityDispatcher.close();
            blockEntityDispatcher = null;
        }
        if (biomeDispatcher != null) {
            biomeDispatcher.close();
            biomeDispatcher = null;
        }
        if (entityDispatcher != null) {
            entityDispatcher.close();
            entityDispatcher = null;
        }
    }

    private static long progressDelta(long current, long previous) {
        if (current < previous) {
            throw new IllegalStateException(
                    "history extension replay progress regressed from "
                            + previous + " to " + current);
        }
        return current - previous;
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private enum Phase {
        BLOCKS,
        BLOCK_RECONCILE,
        BLOCK_ENTITIES,
        BIOMES,
        ENTITIES,
        DONE
    }
}
