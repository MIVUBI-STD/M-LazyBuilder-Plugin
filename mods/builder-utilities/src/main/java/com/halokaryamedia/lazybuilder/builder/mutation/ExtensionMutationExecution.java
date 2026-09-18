package com.halokaryamedia.lazybuilder.builder.mutation;

public record ExtensionMutationExecution(
        long plannedExtensions,
        long appliedExtensions,
        MutationExecutionState state,
        String conflictTypeId,
        Integer conflictChunkX,
        Integer conflictChunkZ,
        Long conflictLocalKey
) {
    public ExtensionMutationExecution {
        if (plannedExtensions < 0 || appliedExtensions < 0 || appliedExtensions > plannedExtensions) {
            throw new IllegalArgumentException("invalid extension execution counts");
        }
        if (state == null) throw new NullPointerException("state");
        boolean anyConflict = conflictTypeId != null || conflictChunkX != null || conflictChunkZ != null || conflictLocalKey != null;
        if (state == MutationExecutionState.CONFLICT) {
            if (conflictTypeId == null || conflictChunkX == null || conflictChunkZ == null || conflictLocalKey == null) {
                throw new IllegalArgumentException("CONFLICT requires complete extension key");
            }
        } else if (anyConflict) {
            throw new IllegalArgumentException("extension conflict key is valid only for CONFLICT");
        }
    }
}
