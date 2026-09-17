package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;

/** Owned rollback preparation. READY owns a durable reverse plan until closed/transferred. */
public record PreparedRollback(
        RollbackPreparationState state,
        long appliedChanges,
        StoredChangeSet rollbackPlan,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) implements AutoCloseable {
    public PreparedRollback {
        if (state == null) throw new NullPointerException("state");
        if (appliedChanges < 0) throw new IllegalArgumentException("appliedChanges must be >= 0");
        if (state == RollbackPreparationState.READY) {
            if (rollbackPlan == null || appliedChanges == 0 || rollbackPlan.changeCount() != appliedChanges) {
                throw new IllegalArgumentException("READY requires a matching non-empty rollback plan");
            }
        } else if (rollbackPlan != null) {
            throw new IllegalArgumentException("Only READY may own a rollback plan");
        }
        if (state == RollbackPreparationState.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
    }

    public static PreparedRollback empty() {
        return new PreparedRollback(RollbackPreparationState.EMPTY, 0, null, null, null, null);
    }

    public static PreparedRollback ready(long appliedChanges, StoredChangeSet rollbackPlan) {
        return new PreparedRollback(RollbackPreparationState.READY, appliedChanges, rollbackPlan, null, null, null);
    }

    public static PreparedRollback conflict(int x, int y, int z) {
        return new PreparedRollback(RollbackPreparationState.CONFLICT, 0, null, x, y, z);
    }

    @Override
    public void close() throws IOException {
        if (rollbackPlan != null) rollbackPlan.close();
    }
}
