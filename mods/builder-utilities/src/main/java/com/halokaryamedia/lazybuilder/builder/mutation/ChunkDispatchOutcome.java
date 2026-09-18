package com.halokaryamedia.lazybuilder.builder.mutation;

/** Result returned by one concrete chunk dispatch target. */
public record ChunkDispatchOutcome(
        State state,
        long dispatchedBlocks,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public enum State { DISPATCHED, ALREADY_APPLIED, CONFLICT }

    public ChunkDispatchOutcome {
        if (state == null) throw new NullPointerException("state");
        if (dispatchedBlocks < 0) throw new IllegalArgumentException("dispatchedBlocks must be >= 0");
        if (state == State.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
            if (dispatchedBlocks != 0) {
                throw new IllegalArgumentException("CONFLICT cannot report dispatched blocks");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
    }

    public static ChunkDispatchOutcome dispatched(long blocks) {
        return new ChunkDispatchOutcome(State.DISPATCHED, blocks, null, null, null);
    }

    public static ChunkDispatchOutcome alreadyApplied() {
        return new ChunkDispatchOutcome(State.ALREADY_APPLIED, 0, null, null, null);
    }

    public static ChunkDispatchOutcome conflict(int x, int y, int z) {
        return new ChunkDispatchOutcome(State.CONFLICT, 0, x, y, z);
    }
}
