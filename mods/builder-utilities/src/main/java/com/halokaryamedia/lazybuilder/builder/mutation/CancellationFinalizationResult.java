package com.halokaryamedia.lazybuilder.builder.mutation;

/** Public summary of KEEP_CHANGES cancellation finalization without leaking history ownership. */
public record CancellationFinalizationResult(
        AppliedMutationCompactionState state,
        long retainedChanges,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public CancellationFinalizationResult {
        if (state == null) throw new NullPointerException("state");
        if (retainedChanges < 0) throw new IllegalArgumentException("retainedChanges must be >= 0");
        if (state == AppliedMutationCompactionState.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
    }

    public static CancellationFinalizationResult from(AppliedMutationCompaction compaction) {
        return new CancellationFinalizationResult(
                compaction.state(),
                compaction.appliedChanges(),
                compaction.conflictX(),
                compaction.conflictY(),
                compaction.conflictZ()
        );
    }
}
