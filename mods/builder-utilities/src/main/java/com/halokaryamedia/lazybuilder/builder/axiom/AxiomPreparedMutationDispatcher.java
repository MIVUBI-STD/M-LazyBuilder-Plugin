package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.io.IOException;
import java.util.Objects;

/** Streams a committed block-only History v2 plan into Axiom's public region mutation API. */
public final class AxiomPreparedMutationDispatcher {
    private AxiomPreparedMutationDispatcher() {
    }

    public static AxiomPreparedDispatchResult dispatch(
            StoredChangeSet prepared,
            AxiomChunkMutationDispatcher dispatcher,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (prepared.extensionCount() != 0) {
            throw new IllegalArgumentException(
                    "Axiom block dispatcher cannot apply History extension frames");
        }

        MutableDispatch aggregate = new MutableDispatch();
        prepared.visitChunks(chunk -> {
            if (aggregate.state != null) return false;
            if (cancellationToken.isCancellationRequested()) {
                aggregate.state = AxiomPreparedDispatchResult.State.CANCELLED;
                return false;
            }

            AxiomChunkDispatchResult result = dispatcher.dispatch(chunk);
            aggregate.visitedChunks++;
            switch (result.state()) {
                case DISPATCHED -> aggregate.dispatchedBlocks += result.dispatchedBlocks();
                case ALREADY_APPLIED -> { }
                case CONFLICT -> {
                    aggregate.state = AxiomPreparedDispatchResult.State.CONFLICT;
                    aggregate.conflictX = result.conflictX();
                    aggregate.conflictY = result.conflictY();
                    aggregate.conflictZ = result.conflictZ();
                    return false;
                }
            }
            return true;
        });

        AxiomPreparedDispatchResult.State state = aggregate.state == null
                ? AxiomPreparedDispatchResult.State.DISPATCHED
                : aggregate.state;
        return new AxiomPreparedDispatchResult(
                state,
                aggregate.visitedChunks,
                aggregate.dispatchedBlocks,
                aggregate.conflictX,
                aggregate.conflictY,
                aggregate.conflictZ
        );
    }

    private static final class MutableDispatch {
        long visitedChunks;
        long dispatchedBlocks;
        AxiomPreparedDispatchResult.State state;
        Integer conflictX;
        Integer conflictY;
        Integer conflictZ;
    }
}
