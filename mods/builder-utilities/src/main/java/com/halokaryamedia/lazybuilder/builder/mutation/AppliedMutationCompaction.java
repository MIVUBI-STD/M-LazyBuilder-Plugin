package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

/** Result of projecting a prepared mutation onto the subset actually present in world state. */
public record AppliedMutationCompaction(
        AppliedMutationCompactionState state,
        long appliedChanges,
        StoredChangeSet compactedChangeSet,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public AppliedMutationCompaction {
        if (state == null) throw new NullPointerException("state");
        if (appliedChanges < 0) throw new IllegalArgumentException("appliedChanges must be >= 0");
        if (state == AppliedMutationCompactionState.COMPACTED) {
            if (compactedChangeSet == null) throw new IllegalArgumentException("COMPACTED requires a changeset");
            if (appliedChanges == 0 || compactedChangeSet.changeCount() != appliedChanges) {
                throw new IllegalArgumentException("COMPACTED count must match a non-empty changeset");
            }
        } else if (compactedChangeSet != null) {
            throw new IllegalArgumentException("Only COMPACTED may own a changeset");
        }
        if (state == AppliedMutationCompactionState.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
    }

    public static AppliedMutationCompaction empty() {
        return new AppliedMutationCompaction(AppliedMutationCompactionState.EMPTY, 0, null, null, null, null);
    }

    public static AppliedMutationCompaction compacted(StoredChangeSet changes) {
        return new AppliedMutationCompaction(
                AppliedMutationCompactionState.COMPACTED,
                changes.changeCount(), changes, null, null, null);
    }

    public static AppliedMutationCompaction conflict(int x, int y, int z) {
        return new AppliedMutationCompaction(AppliedMutationCompactionState.CONFLICT, 0, null, x, y, z);
    }
}
