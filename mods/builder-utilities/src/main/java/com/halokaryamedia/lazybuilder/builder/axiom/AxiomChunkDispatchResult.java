package com.halokaryamedia.lazybuilder.builder.axiom;

public record AxiomChunkDispatchResult(
        State state,
        int dispatchedBlocks,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public enum State { DISPATCHED, ALREADY_APPLIED, CONFLICT }

    public AxiomChunkDispatchResult {
        if (state == null) throw new NullPointerException("state");
        if (dispatchedBlocks < 0) throw new IllegalArgumentException("dispatchedBlocks must be >= 0");
        if (state == State.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
    }

    public static AxiomChunkDispatchResult dispatched(int count) {
        return new AxiomChunkDispatchResult(State.DISPATCHED, count, null, null, null);
    }

    public static AxiomChunkDispatchResult alreadyApplied() {
        return new AxiomChunkDispatchResult(State.ALREADY_APPLIED, 0, null, null, null);
    }

    public static AxiomChunkDispatchResult conflict(int x, int y, int z) {
        return new AxiomChunkDispatchResult(State.CONFLICT, 0, x, y, z);
    }
}
