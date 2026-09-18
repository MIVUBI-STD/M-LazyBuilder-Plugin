package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimelineLease;
import com.halokaryamedia.lazybuilder.builder.history.ReplayDirection;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedPreparedMutationDispatcher;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkChangeSetTransforms;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchTarget;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/** Async replay engine used only for timeline entries containing BIOME extensions. */
public final class AxiomMixedHistoryReplayController implements AutoCloseable {
    private final BuilderRuntime runtime;
    private final ClientWorld world;
    private final HistoryTimelineLease lease;
    private final AxiomClientWorldStateSource blocks;
    private final AxiomBudgetedChunkDispatchTarget forwardTarget;
    private final ChunkDispatchTarget reverseTarget;

    private BudgetedPreparedMutationDispatcher blockDispatcher;
    private AxiomBiomeBatchDispatcher biomeDispatcher;
    private Phase phase;
    private String status;
    private boolean finished;

    private AxiomMixedHistoryReplayController(
            AxiomClientServices services,
            BuilderRuntime runtime,
            ClientWorld world,
            HistoryTimelineLease lease
    ) throws IOException {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.world = Objects.requireNonNull(world, "world");
        this.lease = Objects.requireNonNull(lease, "lease");
        requireBiomeOnly(lease);
        this.blocks = new AxiomClientWorldStateSource(world);
        this.forwardTarget = new AxiomBudgetedChunkDispatchTarget(
                new AxiomChunkMutationDispatcher(
                        Objects.requireNonNull(services, "services"), world));
        this.reverseTarget = chunk -> forwardTarget.dispatch(
                ChunkChangeSetTransforms.reverse(chunk));

        if (lease.direction() == ReplayDirection.UNDO) {
            biomeDispatcher = new AxiomBiomeBatchDispatcher(
                    lease.changeSet(), world, () -> false, true);
            phase = Phase.BIOMES;
            status = "Undoing biomes";
        } else {
            blockDispatcher = new BudgetedPreparedMutationDispatcher(
                    lease.changeSet(), forwardTarget, () -> false);
            phase = Phase.BLOCKS;
            status = "Redoing blocks";
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
                case BIOMES -> pumpBiomes();
                case DONE -> { }
            }
        } catch (Exception failure) {
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
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " blocks: " + slice.totalDispatchedBlocks();
        switch (slice.state()) {
            case YIELDED -> { }
            case EXHAUSTED -> {
                blockDispatcher.close();
                blockDispatcher = null;
                phase = Phase.BLOCK_RECONCILE;
            }
            case CANCELLED -> throw new IllegalStateException("History replay cannot be cancelled internally");
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
            biomeDispatcher = new AxiomBiomeBatchDispatcher(
                    lease.changeSet(), world, () -> false, false);
            phase = Phase.BIOMES;
            status = "Redoing biomes";
        } else {
            complete();
        }
    }

    private void pumpBiomes() throws IOException {
        BiomeBatchDispatchProgress progress = biomeDispatcher.pump();
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo")
                + " biomes: " + progress.processedExtensions();
        switch (progress.state()) {
            case YIELDED, WAITING -> { }
            case EXHAUSTED -> {
                biomeDispatcher.close();
                biomeDispatcher = null;
                if (lease.direction() == ReplayDirection.UNDO) {
                    blockDispatcher = new BudgetedPreparedMutationDispatcher(
                            lease.changeSet(), reverseTarget, () -> false);
                    phase = Phase.BLOCKS;
                    status = "Undoing blocks";
                } else {
                    complete();
                }
            }
            case CANCELLED -> throw new IllegalStateException("History replay cannot be cancelled internally");
            case CONFLICT, FAILED -> throw new IllegalStateException(
                    "biome replay " + progress.state() + ": " + progress.detail());
        }
    }

    private void complete() {
        lease.complete();
        closeTransports();
        finished = true;
        phase = Phase.DONE;
        status = (lease.direction() == ReplayDirection.UNDO ? "Undo" : "Redo") + " completed";
    }

    private void closeTransports() {
        if (blockDispatcher != null) {
            blockDispatcher.close();
            blockDispatcher = null;
        }
        if (biomeDispatcher != null) {
            biomeDispatcher.close();
            biomeDispatcher = null;
        }
    }

    private static void requireBiomeOnly(HistoryTimelineLease lease) throws IOException {
        if (lease.changeSet().extensionCount() == 0) {
            throw new IllegalArgumentException(
                    "Block-only history should use Axiom native undo/redo");
        }
        lease.changeSet().visitExtensions(frame -> {
            if (!HistoryExtensionTypes.BIOME.equals(frame.typeId())) {
                throw new IOException(
                        "No mixed replay authority for extension type " + frame.typeId());
            }
            return true;
        });
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
        BIOMES,
        DONE
    }
}
