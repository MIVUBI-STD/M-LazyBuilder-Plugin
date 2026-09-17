package com.halokaryamedia.lazybuilder.builder.mutation;

public record ChunkMutationExecution(
        int chunkX,
        int chunkZ,
        long plannedChanges,
        long appliedChanges,
        MutationExecutionState state,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public ChunkMutationExecution {
        if (plannedChanges < 0 || appliedChanges < 0 || appliedChanges > plannedChanges) {
            throw new IllegalArgumentException("invalid mutation counts");
        }
        if (state == null) throw new NullPointerException("state");
        boolean hasConflictLocation = conflictX != null || conflictY != null || conflictZ != null;
        if (state == MutationExecutionState.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires a complete conflict location");
            }
        } else if (hasConflictLocation) {
            throw new IllegalArgumentException("conflict location is valid only for CONFLICT");
        }
    }

    public boolean isPartial() {
        return appliedChanges > 0 && appliedChanges < plannedChanges;
    }
}
