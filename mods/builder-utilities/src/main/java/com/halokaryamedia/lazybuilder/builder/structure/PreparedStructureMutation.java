package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedBlockMutation;

import java.util.Objects;

/**
 * Durable prepared structure mutation. Extension frames are preserved here even
 * when the current Axiom public mutation adapter can only execute block-only plans.
 */
public record PreparedStructureMutation(
        StoredChangeSet changeSet,
        long plannedChanges,
        long extensionChanges
) implements PreparedBlockMutation {
    public PreparedStructureMutation(StoredChangeSet changeSet, long plannedChanges) {
        this(changeSet, plannedChanges, Objects.requireNonNull(changeSet, "changeSet").extensionCount());
    }

    public PreparedStructureMutation {
        Objects.requireNonNull(changeSet, "changeSet");
        if (plannedChanges < 0) throw new IllegalArgumentException("plannedChanges must be >= 0");
        if (extensionChanges < 0) throw new IllegalArgumentException("extensionChanges must be >= 0");
        if (plannedChanges > changeSet.changeCount()) {
            throw new IllegalArgumentException(
                    "plannedChanges cannot exceed stored block history entries");
        }
        if (changeSet.extensionCount() != extensionChanges) {
            throw new IllegalArgumentException("Stored extension count does not match extensionChanges");
        }
    }

    public boolean blockOnly() {
        return extensionChanges == 0;
    }
}
