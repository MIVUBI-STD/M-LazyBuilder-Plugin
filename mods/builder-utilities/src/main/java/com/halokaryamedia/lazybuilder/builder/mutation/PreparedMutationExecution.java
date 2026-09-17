package com.halokaryamedia.lazybuilder.builder.mutation;

public record PreparedMutationExecution(
        long plannedChanges,
        long appliedChanges,
        long completedChunks,
        MutationExecutionState state,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public PreparedMutationExecution {
        if (plannedChanges < 0 || appliedChanges < 0 || appliedChanges > plannedChanges || completedChunks < 0) {
            throw new IllegalArgumentException("invalid prepared mutation counts");
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
}
