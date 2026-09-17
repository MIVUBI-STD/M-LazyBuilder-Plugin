package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationSession;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/**
 * Axiom-facing orchestration wrapper for one durable prepared block mutation.
 *
 * <p>A successful dispatch is intentionally not completion. The session remains
 * RUNNING until {@link #reconcile()} proves that the committed plan is present in
 * current world state, at which point the core session publishes it to undo history.</p>
 */
public final class AxiomPreparedMutationSession implements AutoCloseable {
    private final PreparedMutationSession core;
    private final AxiomPreparedMutationDispatcherFacade dispatchFacade;
    private final AxiomClientWorldStateSource worldSource;
    private final CancellationToken cancellationToken;
    private boolean dispatched;

    public AxiomPreparedMutationSession(
            AxiomClientServices services,
            ClientWorld world,
            PreparedMaterialMutation prepared,
            HistoryTimeline timeline,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(world, "world");
        this.core = new PreparedMutationSession(
                Objects.requireNonNull(prepared, "prepared"),
                Objects.requireNonNull(timeline, "timeline")
        );
        AxiomChunkMutationDispatcher dispatcher = new AxiomChunkMutationDispatcher(services, world);
        this.dispatchFacade = new AxiomPreparedMutationDispatcherFacade(dispatcher);
        this.worldSource = new AxiomClientWorldStateSource(world);
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
    }

    public synchronized OperationLifecycle lifecycle() {
        return core.lifecycle();
    }

    public synchronized AxiomPreparedDispatchResult dispatch() throws IOException {
        if (dispatched) throw new IllegalStateException("Prepared mutation was already dispatched");
        core.startDispatch();
        dispatched = true;

        AxiomPreparedDispatchResult result = dispatchFacade.dispatch(core.prepared(), cancellationToken);
        if (result.state() == AxiomPreparedDispatchResult.State.CANCELLED) {
            core.noteCancellationRequested();
        } else if (result.state() == AxiomPreparedDispatchResult.State.CONFLICT) {
            // The dispatch planner already observed an incompatible world state.
            // Reconcile immediately so the canonical lifecycle records FAILED.
            core.reconcile(worldSource);
        }
        return result;
    }

    public synchronized PreparedReconciliationReport reconcile() throws IOException {
        if (!dispatched) throw new IllegalStateException("Cannot reconcile before dispatch");
        if (cancellationToken.isCancellationRequested()) {
            core.noteCancellationRequested();
        }
        return core.reconcile(worldSource);
    }

    @Override
    public synchronized void close() throws IOException {
        core.close();
    }

    /** Small seam that keeps the public session easy to exercise without owning Axiom mutation semantics itself. */
    private record AxiomPreparedMutationDispatcherFacade(AxiomChunkMutationDispatcher dispatcher) {
        private AxiomPreparedMutationDispatcherFacade {
            Objects.requireNonNull(dispatcher, "dispatcher");
        }

        private AxiomPreparedDispatchResult dispatch(
                PreparedMaterialMutation prepared,
                CancellationToken cancellationToken
        ) throws IOException {
            return AxiomPreparedMutationDispatcher.dispatch(
                    prepared.changeSet(), dispatcher, cancellationToken);
        }
    }
}
