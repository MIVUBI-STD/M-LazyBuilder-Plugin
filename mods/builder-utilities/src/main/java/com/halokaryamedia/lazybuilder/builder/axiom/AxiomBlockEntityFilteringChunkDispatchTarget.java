package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchOutcome;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchTarget;

import java.io.IOException;
import java.util.Objects;

/** Keeps block-entity-owned coordinates out of Axiom block batches. */
public final class AxiomBlockEntityFilteringChunkDispatchTarget
        implements ChunkDispatchTarget {
    private final ChunkDispatchTarget delegate;
    private final AxiomBlockEntityPositionMask mask;

    public AxiomBlockEntityFilteringChunkDispatchTarget(
            ChunkDispatchTarget delegate,
            AxiomBlockEntityPositionMask mask
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.mask = Objects.requireNonNull(mask, "mask");
    }

    @Override
    public ChunkDispatchOutcome dispatch(ChunkChangeSet chunk) throws IOException {
        ChunkChangeSet filtered = mask.withoutOwnedPositions(chunk);
        if (filtered.size() == 0) {
            return ChunkDispatchOutcome.dispatched(0);
        }
        return delegate.dispatch(filtered);
    }
}
