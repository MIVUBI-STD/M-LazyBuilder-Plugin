package com.halokaryamedia.lazybuilder.builder.axiom;

public record AxiomPreparedDispatchResult(
        State state,
        long visitedChunks,
        long dispatchedBlocks,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public enum State { DISPATCHED, CANCELLED, CONFLICT }

    public AxiomPreparedDispatchResult {
        if (state == null) throw new NullPointerException("state");
        if (visitedChunks < 0 || dispatchedBlocks < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        if (state == State.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
    }
}
