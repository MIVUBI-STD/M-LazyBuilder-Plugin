package com.halokaryamedia.lazybuilder.builder.mutation;

/** Public rollback-preparation summary that does not expose durable-plan ownership. */
public record RollbackPreparationResult(
        RollbackPreparationState state,
        long appliedChanges,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public static RollbackPreparationResult from(PreparedRollback prepared) {
        return new RollbackPreparationResult(
                prepared.state(), prepared.appliedChanges(),
                prepared.conflictX(), prepared.conflictY(), prepared.conflictZ());
    }
}
