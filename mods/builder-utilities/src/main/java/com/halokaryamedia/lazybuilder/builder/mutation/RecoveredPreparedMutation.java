package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.util.Objects;

/** Durable recovered mutation; may include supported extension frames. */
public record RecoveredPreparedMutation(
        StoredChangeSet changeSet,
        long plannedChanges
) implements PreparedBlockMutation {
    public RecoveredPreparedMutation {
        Objects.requireNonNull(changeSet, "changeSet");
        if (plannedChanges < 0 || plannedChanges > changeSet.changeCount()) {
            throw new IllegalArgumentException(
                    "plannedChanges must be within stored block history entry count");
        }
    }
}
