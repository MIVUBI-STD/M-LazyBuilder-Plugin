package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchOutcome;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchTarget;

import java.util.Objects;

/** Bridges the generic budgeted dispatcher to Axiom's public BlockRegion mutation path. */
public final class AxiomBudgetedChunkDispatchTarget implements ChunkDispatchTarget {
    private final AxiomChunkMutationDispatcher dispatcher;

    public AxiomBudgetedChunkDispatchTarget(AxiomChunkMutationDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public ChunkDispatchOutcome dispatch(ChunkChangeSet chunk) {
        AxiomChunkDispatchResult result = dispatcher.dispatch(chunk);
        return switch (result.state()) {
            case DISPATCHED -> ChunkDispatchOutcome.dispatched(result.dispatchedBlocks());
            case ALREADY_APPLIED -> ChunkDispatchOutcome.alreadyApplied();
            case CONFLICT -> ChunkDispatchOutcome.conflict(
                    result.conflictX(), result.conflictY(), result.conflictZ());
        };
    }
}
