package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.util.Objects;

/** Block-only durable mutation reopened from disk after restart. */
public record RecoveredBlockMutation(StoredChangeSet changeSet, long plannedChanges)
        implements PreparedBlockMutation {
    public RecoveredBlockMutation(StoredChangeSet changeSet) {
        this(changeSet, Objects.requireNonNull(changeSet, "changeSet").changeCount());
    }

    public RecoveredBlockMutation {
        Objects.requireNonNull(changeSet, "changeSet");
        if (changeSet.extensionCount() != 0) {
            throw new IllegalArgumentException(
                    "RecoveredBlockMutation cannot own extension-aware history");
        }
        if (plannedChanges < 0 || plannedChanges != changeSet.changeCount()) {
            throw new IllegalArgumentException("plannedChanges must match stored change count");
        }
    }
}
